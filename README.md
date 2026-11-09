# GORILLA AI

GORILLA AI is an Android companion app with Supabase-backed account registration and approval-gated access.

## Account flow
- Register in the app with a name, email and password.
- Sign in through Supabase Auth.
- New accounts receive a pending profile and wait for owner approval.
- The owner/admin opens **☰ → Панель владельца**, selects a pending account and issues a one-time six-digit code.
- The user enters that code in the app. It expires after 15 minutes and can be used once.
- Approved accounts retain their numeric public ID (starting at 2471).

## Owner account
The owner role is assigned once in Supabase SQL Editor after the first account is registered. The app displays the owner panel only to accounts whose profile role is `owner` or `admin`; server-side RPC functions also enforce this permission.

## Security
- Android uses only the Supabase project URL and publishable client key.
- Never place a Supabase service-role key in the app, repository, or APK.
- Approval codes are stored as SHA-256 hashes and are account-bound, single-use and time-limited.
- Row Level Security is enabled on the account tables.

## Build
Open the repository in Android Studio with JDK 17, sync Gradle, then choose **Build > Build APK(s)**.

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, target SDK 35 and minimum SDK 23.

## Current limitations
The account registration, login and owner approval-code flow are connected. The chat UI still uses a local placeholder response; a real AI model and cloud chat history are not connected yet.
