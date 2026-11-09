-- GORILLA AI: owner-only prompt audit and role management.
-- Run this migration in Supabase SQL Editor. No service-role key is used by Android.

create table if not exists public.gorilla_prompt_logs (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  prompt text not null,
  created_at timestamptz not null default now()
);
create index if not exists gorilla_prompt_logs_created_idx
  on public.gorilla_prompt_logs(created_at desc);
create index if not exists gorilla_prompt_logs_user_idx
  on public.gorilla_prompt_logs(user_id, created_at desc);
alter table public.gorilla_prompt_logs enable row level security;

create or replace function public.gorilla_log_prompt(prompt text)
returns void
language plpgsql security definer
set search_path = public
as $$
begin
  if auth.uid() is null then raise exception 'not_authenticated'; end if;
  if length(trim(prompt)) = 0 or length(prompt) > 12000 then
    raise exception 'invalid_prompt';
  end if;
  if not exists (
    select 1 from public.gorilla_profiles
    where user_id = auth.uid() and access_status = 'approved'
  ) then raise exception 'account_not_approved'; end if;
  insert into public.gorilla_prompt_logs(user_id, prompt)
  values (auth.uid(), prompt);
end;
$$;

-- Only the account whose role is owner can read prompts.
create or replace function public.gorilla_owner_prompt_logs()
returns table(log_id bigint, user_id uuid, public_id bigint,
              display_name text, prompt text, created_at timestamptz)
language plpgsql security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.gorilla_profiles
                 where user_id = auth.uid() and role = 'owner') then
    raise exception 'owner_only';
  end if;
  return query
    select l.id, l.user_id, p.public_id, p.display_name, l.prompt, l.created_at
    from public.gorilla_prompt_logs l
    join public.gorilla_profiles p on p.user_id = l.user_id
    order by l.created_at desc
    limit 300;
end;
$$;

-- Only owner can see accounts and promote/demote admins.
create or replace function public.gorilla_owner_accounts()
returns table(user_id uuid, public_id bigint, display_name text,
              role text, access_status text, created_at timestamptz)
language plpgsql security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.gorilla_profiles
                 where user_id = auth.uid() and role = 'owner') then
    raise exception 'owner_only';
  end if;
  return query
    select p.user_id, p.public_id, p.display_name, p.role,
           p.access_status, p.created_at
    from public.gorilla_profiles p
    order by p.public_id;
end;
$$;

create or replace function public.gorilla_owner_set_role(target_user uuid, new_role text)
returns void
language plpgsql security definer
set search_path = public
as $$
begin
  if not exists (select 1 from public.gorilla_profiles
                 where user_id = auth.uid() and role = 'owner') then
    raise exception 'owner_only';
  end if;
  if new_role not in ('user','admin') then raise exception 'invalid_role'; end if;
  if target_user = auth.uid() then raise exception 'cannot_change_owner_role'; end if;
  update public.gorilla_profiles
  set role = new_role
  where user_id = target_user and role <> 'owner';
  if not found then raise exception 'account_not_found_or_owner'; end if;
end;
$$;

revoke all on function public.gorilla_log_prompt(text) from public;
revoke all on function public.gorilla_owner_prompt_logs() from public;
revoke all on function public.gorilla_owner_accounts() from public;
revoke all on function public.gorilla_owner_set_role(uuid,text) from public;
grant execute on function public.gorilla_log_prompt(text) to authenticated;
grant execute on function public.gorilla_owner_prompt_logs() to authenticated;
grant execute on function public.gorilla_owner_accounts() to authenticated;
grant execute on function public.gorilla_owner_set_role(uuid,text) to authenticated;
