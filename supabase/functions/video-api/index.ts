const MODEL = "fal-ai/kling-video/v3/standard/text-to-video";
const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type", "Access-Control-Allow-Methods": "POST, OPTIONS" };
const reply = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "Method not allowed" }, 405);
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return reply({ error: "Требуется вход" }, 401);
  const userCheck = await fetch(Deno.env.get("SUPABASE_URL") + "/auth/v1/user", { headers: { apikey: Deno.env.get("SUPABASE_ANON_KEY")!, Authorization: auth } });
  if (!userCheck.ok) return reply({ error: "Сессия недействительна" }, 401);
  const key = Deno.env.get("FAL_KEY");
  if (!key) return reply({ error: "FAL_KEY не настроен в Supabase Secrets" }, 503);
  try {
    const body = await req.json(); const action = String(body.action ?? "submit"); const id = String(body.request_id ?? "");
    const headers = { Authorization: "Key " + key, "Content-Type": "application/json" };
    if (action === "submit") {
      const prompt = String(body.prompt ?? "").trim(); if (prompt.length < 8 || prompt.length > 2500) return reply({ error: "Описание должно содержать 8–2500 символов" }, 400);
      const duration = String(body.duration ?? "5"); if (!["5", "10", "15"].includes(duration)) return reply({ error: "Доступны фрагменты 5, 10 или 15 секунд" }, 400);
      const aspect = ["16:9", "9:16", "1:1"].includes(body.aspect_ratio) ? body.aspect_ratio : "16:9";
      const response = await fetch("https://queue.fal.run/" + MODEL, { method: "POST", headers, body: JSON.stringify({ prompt, duration, aspect_ratio: aspect, generate_audio: body.generate_audio !== false, enable_safety_checker: true }) });
      const data = await response.json(); if (!response.ok) return reply({ error: data.detail ?? data.message ?? "fal request failed" }, response.status);
      return reply({ request_id: data.request_id, status: data.status ?? "IN_QUEUE", model: MODEL });
    }
    if (!/^[a-f0-9-]{20,}$/i.test(id)) return reply({ error: "Некорректный ID задачи" }, 400);
    const suffix = action === "result" ? "" : "/status";
    const response = await fetch("https://queue.fal.run/" + MODEL + "/requests/" + id + suffix, { headers });
    const data = await response.json(); if (!response.ok) return reply({ error: data.detail ?? data.message ?? "Не удалось получить статус" }, response.status); return reply(data);
  } catch (e) { return reply({ error: e instanceof Error ? e.message : "Ошибка сервера" }, 500); }
});