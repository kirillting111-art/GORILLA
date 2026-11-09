-- GORILLA AI: approval-gated accounts.
-- Run in Supabase SQL Editor. Never put the service-role key in the Android app.

create extension if not exists pgcrypto with schema extensions;

create sequence if not exists public.gorilla_user_id_seq start with 2471;

create table if not exists public.gorilla_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  public_id bigint unique not null default nextval('public.gorilla_user_id_seq'),
  display_name text,
  role text not null default 'user' check (role in ('user','admin','owner')),
  access_status text not null default 'pending' check (access_status in ('pending','approved','blocked')),
  approved_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.gorilla_access_codes (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  code_hash text not null unique,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_by uuid not null references auth.users(id),
  created_at timestamptz not null default now()
);

create index if not exists gorilla_access_codes_user_idx on public.gorilla_access_codes(user_id);
create index if not exists gorilla_profiles_status_idx on public.gorilla_profiles(access_status);

alter table public.gorilla_profiles enable row level security;
alter table public.gorilla_access_codes enable row level security;

drop policy if exists "Users read own profile" on public.gorilla_profiles;
create policy "Users read own profile" on public.gorilla_profiles
for select to authenticated using (auth.uid() = user_id);

-- Create a pending profile whenever Supabase Auth creates an account.
create or replace function public.gorilla_create_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.gorilla_profiles(user_id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data->>'name', split_part(coalesce(new.email, ''), '@', 1)))
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists gorilla_auth_user_created on auth.users;
create trigger gorilla_auth_user_created
after insert on auth.users
for each row execute procedure public.gorilla_create_profile();

-- Owner/admin issues a random, one-time six-digit code for a pending account.
-- Only the SHA-256 hash is stored; plaintext is returned once to the admin.
create or replace function public.gorilla_issue_access_code(target_user uuid)
returns text
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  actor_role text;
  new_code text;
  random_bytes bytea;
begin
  select p.role into actor_role
  from public.gorilla_profiles p
  where p.user_id = auth.uid();

  if coalesce(actor_role, '') not in ('admin', 'owner') then
    raise exception 'not_authorized';
  end if;

  if not exists (
    select 1 from public.gorilla_profiles p
    where p.user_id = target_user and p.access_status = 'pending'
  ) then
    raise exception 'user_not_pending';
  end if;

  update public.gorilla_access_codes
  set used_at = now()
  where user_id = target_user and used_at is null;

  random_bytes := gen_random_bytes(4);
  new_code := lpad((mod(
    get_byte(random_bytes, 0)::bigint * 16777216 +
    get_byte(random_bytes, 1)::bigint * 65536 +
    get_byte(random_bytes, 2)::bigint * 256 +
    get_byte(random_bytes, 3)::bigint,
    1000000
  ))::text, 6, '0');

  insert into public.gorilla_access_codes(user_id, code_hash, expires_at, created_by)
  values (
    target_user,
    encode(digest(new_code, 'sha256'), 'hex'),
    now() + interval '15 minutes',
    auth.uid()
  );

  return new_code;
end;
$$;

-- A pending user can redeem only a code issued for their own account.
create or replace function public.gorilla_redeem_access_code(code text)
returns bigint
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  current_status text;
  assigned_id bigint;
  matched_id uuid;
begin
  select p.access_status, p.public_id
  into current_status, assigned_id
  from public.gorilla_profiles p
  where p.user_id = auth.uid()
  for update;

  if current_status is null then
    raise exception 'profile_not_found';
  end if;

  if code !~ '^[0-9]{6}$' then
    raise exception 'invalid_or_expired_code';
  end if;

  if current_status = 'approved' then
    return assigned_id;
  end if;

  if current_status <> 'pending' then
    raise exception 'account_not_pending';
  end if;

  select c.id into matched_id
  from public.gorilla_access_codes c
  where c.user_id = auth.uid()
    and c.code_hash = encode(digest(code, 'sha256'), 'hex')
    and c.used_at is null
    and c.expires_at > now()
  order by c.created_at desc
  limit 1
  for update;

  if matched_id is null then
    raise exception 'invalid_or_expired_code';
  end if;

  update public.gorilla_access_codes set used_at = now() where id = matched_id;
  update public.gorilla_profiles
  set access_status = 'approved', approved_at = now()
  where user_id = auth.uid();

  return assigned_id;
end;
$$;

-- Admin/owner can list pending registrations through this guarded RPC.
create or replace function public.gorilla_pending_accounts()
returns table(user_id uuid, public_id bigint, display_name text, created_at timestamptz)
language plpgsql
security definer
set search_path = public
as $$
begin
  if not exists (
    select 1 from public.gorilla_profiles p
    where p.user_id = auth.uid() and p.role in ('admin', 'owner')
  ) then
    raise exception 'not_authorized';
  end if;

  return query
    select p.user_id, p.public_id, p.display_name, p.created_at
    from public.gorilla_profiles p
    where p.access_status = 'pending'
    order by p.created_at asc;
end;
$$;

revoke all on function public.gorilla_issue_access_code(uuid) from public;
revoke all on function public.gorilla_redeem_access_code(text) from public;
revoke all on function public.gorilla_pending_accounts() from public;
grant execute on function public.gorilla_issue_access_code(uuid) to authenticated;
grant execute on function public.gorilla_redeem_access_code(text) to authenticated;
grant execute on function public.gorilla_pending_accounts() to authenticated;

-- OWNER BOOTSTRAP:
-- 1. Sign up your own account in the app once the Auth screen is connected.
-- 2. Copy its UUID from Supabase Authentication > Users.
-- 3. Replace the UUID below and run this statement once in SQL Editor:
-- update public.gorilla_profiles
-- set role = 'owner', access_status = 'approved', approved_at = now()
-- where user_id = 'YOUR_AUTH_USER_UUID';
