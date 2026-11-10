const MODEL = "minimax/h3-max-turbo/text-to-video";
const ENDPOINT = "https://queue.fal.run/" + MODEL;
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type", "Access-Control-Allow-Methods": "POST, OPTIONS" };
// Android sends { action: "submit" | "status" | "result", request_id } through SupabaseApi.
const reply = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "Method not allowed" }, 405);
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return reply({ error: "Требуется вход в аккаунт" }, 401);
  const check = await fetch(Deno.env.get("SUPABASE_URL") + "/auth/v1/user", { headers: { apikey: Deno.env.get("SUPABASE_ANON_KEY")!, Authorization: auth } });
  if (!check.ok) return reply({ error: "Сессия недействительна. Войди снова." }, 401);
  const key = Deno.env.get("FAL_KEY");
  if (!key) return reply({ error: "FAL_KEY не настроен в Supabase Secrets" }, 503);
  try {
    const body = await req.json(); const action = String(body.action ?? "submit");
    const headers = { Authorization: "Key " + key, "Content-Type": "application/json" };
    if (action === "submit") {
      const prompt = String(body.prompt ?? "").trim(); const duration = Number(body.duration ?? 5);
      if (prompt.length < 8 || prompt.length > 2500) return reply({ error: "Описание должно содержать 8–2500 символов" }, 400);
      if (![5, 10, 15].includes(duration)) return reply({ error: "Доступны фрагменты 5, 10 или 15 секунд" }, 400);
      const aspect = ["16:9", "9:16", "1:1"].includes(body.aspect_ratio) ? body.aspect_ratio : "16:9";
      const response = await fetch(ENDPOINT, { method: "POST", headers, body: JSON.stringify({ prompt, duration, prompt_expansion_mode: "balanced", resolution: "768P", aspect_ratio: aspect, enable_safety_checker: true }) });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) return reply({ error: data.detail ?? data.message ?? "fal не принял задачу" }, response.status);
      return reply({ request_id: data.request_id, status: data.status ?? "IN_QUEUE", model: MODEL });
    }
    if (action === "status" || action === "result") {
      const id = String(body.request_id ?? "");
      if (!/^[a-zA-Z0-9-]{8,}$/.test(id)) return reply({ error: "Некорректный ID задачи" }, 400);
      const suffix = action === "status" ? "/status" : "";
      const response = await fetch(ENDPOINT + "/requests/" + encodeURIComponent(id) + suffix, { headers });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) return reply({ error: data.detail ?? data.message ?? "Не удалось получить результат" }, response.status);
      return reply(data);
    }
    return reply({ error: "Неизвестное действие" }, 400);
  } catch (error) { return reply({ error: error instanceof Error ? error.message : "Внутренняя ошибка" }, 500); }
});