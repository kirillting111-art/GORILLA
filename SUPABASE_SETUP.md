# GORILLA AI — Supabase setup

The Android app is connected to Supabase Auth and the approval-gated account RPC functions.

## Database migration
1. Open the Supabase project used by GORILLA AI.
2. Open **SQL Editor**.
3. Run `supabase/migrations/001_approval_gated_accounts.sql` once.
4. Register your own account from the GORILLA Android app.
5. In **Authentication → Users**, copy your auth user UUID.
6. In SQL Editor, replace the placeholder UUID in the owner bootstrap statement at the bottom of the migration and run that statement once.

## In-app registration and approval
1. A person registers from the GORILLA app with name, email and password.
2. Supabase Auth creates the auth user; the database trigger creates a profile with `pending` status.
3. The owner signs into the app and opens **☰ → Панель владельца**.
4. The owner selects the pending account. The app calls `gorilla_issue_access_code` and displays a one-time six-digit code.
5. The owner sends that code privately to the applicant. The applicant enters it in the app; `gorilla_redeem_access_code` approves the account.

Codes expire after 15 minutes, are tied to one user, and can only be redeemed once. Ordinary users cannot list pending accounts or issue codes; the database RPC functions check the caller's role.

## Client credentials and security
The Android client contains only the project URL and a publishable/anon client key. Those values are intended for client use when Row Level Security is correctly configured. **Never put the service-role key in Android, GitHub, or an APK.**

If the Supabase project is changed, update `PROJECT_URL` and `PUBLISHABLE_KEY` in `SupabaseApi.kt` with the new project's URL and publishable key. Do not paste a service-role or secret key there.

## Verification
After running the migration, register a test account in the app, confirm email if Supabase email confirmation is enabled, then sign in. It should show the pending-code prompt. Sign in as the owner on a separate device/session to issue the code.
