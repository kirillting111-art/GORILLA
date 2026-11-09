# GORILLA AI backend setup (Supabase)

This repository now contains the first database migration for approval-gated accounts.

## What the migration provides
- Each account gets a permanent numeric public ID (starting at 2471).
- New accounts start with `access_status = pending` and cannot be treated as approved.
- An owner/admin can list pending accounts and issue a unique six-digit code.
- Codes are tied to one account, stored as SHA-256 hashes, expire after 15 minutes and can be redeemed once.
- Redeeming the code approves that account permanently; later logins do not need another code.
- Ordinary accounts cannot issue codes or list pending registrations.

## Setup
1. Create a Supabase project.
2. Open **SQL Editor** in Supabase.
3. Run the complete file `supabase/migrations/001_approval_gated_accounts.sql`.
4. Create your own account through Supabase Auth.
5. In **Authentication > Users**, copy your auth user UUID.
6. Replace the placeholder in the final commented SQL statement with your UUID and run the owner-promotion update in SQL Editor. This is the one-time owner bootstrap.
7. Keep the Supabase service-role key private. Never put it in Android code, GitHub, or the APK.

## Important integration status
The SQL backend schema is committed, but it is not live until you create a Supabase project and run the migration. The Android app is not yet wired to Supabase Auth/RPC. Do not distribute this prototype as a functioning gated service until that connection is implemented and tested.

## Next integration values
The Android client will need the Supabase project URL and the public anon/publishable key. These are designed for client use when Row Level Security is correctly configured. Do not send or commit the service-role key.
