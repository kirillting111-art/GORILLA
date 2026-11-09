-- GORILLA privacy cleanup: remove all stored prompt history and audit RPCs.
-- Run once in Supabase SQL Editor.
drop function if exists public.gorilla_owner_prompt_logs();
drop function if exists public.gorilla_log_prompt(text);
drop table if exists public.gorilla_prompt_logs cascade;
