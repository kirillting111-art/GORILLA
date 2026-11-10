const API = "https://api.magichour.ai";
const MODEL = "minimax-h3";
const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({ error: "Method not allowed" }, 405);

  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.startsWith("Bearer ")) return reply({ error: "Требуется вход в аккаунт" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  if (!supabaseUrl || !anonKey) return reply({ error: "Supabase auth не настроен" }, 503);
  const check = await fetch(supabaseUrl + "/auth/v1/user", {
    headers: { apikey: anonKey, Authorization: auth },
  });
  if (!check.ok) return reply({ error: "Сессия недействительна. Войди снова." }, 401);

  const key = Deno.env.get("MAGIC_HOUR_API_KEY");
  if (!key) return reply({ error: "MAGIC_HOUR_API_KEY не настроен в Supabase Secrets" }, 503);

  try {
    const body = await req.json();
    const action = String(body.action ?? "submit");
    const headers = { Authorization: "Bearer " + key, "Content-Type": "application/json" };

    if (action === "submit") {
      const prompt = String(body.prompt ?? "").trim();
      const duration = Number(body.duration ?? 5);
      if (prompt.length < 8 || prompt.length > 2500) {
        return reply({ error: "Описание должно содержать 8–2500 символов" }, 400);
      }
      if (![5, 10, 15].includes(duration)) {
        return reply({ error: "Доступны фрагменты 5, 10 или 15 секунд" }, 400);
      }
      const aspect = ["16:9", "9:16", "1:1"].includes(body.aspect_ratio) ? body.aspect_ratio : "16:9";
      const response = await fetch(API + "/v1/text-to-video", {
        method: "POST",
        headers,
        // Let Magic Hour choose the resolution available on the account's plan.
        body: JSON.stringify({
          name: "GORILLA video",
          model: MODEL,
          end_seconds: duration,
          aspect_ratio: aspect,
          audio: true,
          style: { prompt },
        }),
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = data.message ?? data.error?.message ?? data.error ?? "Magic Hour не принял задачу";
        return reply({ error: typeof message === "string" ? message : JSON.stringify(message) }, response.status);
      }
      if (!data.id) return reply({ error: "Magic Hour не вернул ID задачи" }, 502);
      return reply({ request_id: data.id, status: "IN_QUEUE", model: MODEL });
    }

    if (action === "status" || action === "result") {
      const id = String(body.request_id ?? "");
      if (!/^[a-zA-Z0-9_-]{8,}$/.test(id)) return reply({ error: "Некорректный ID задачи" }, 400);
      const response = await fetch(API + "/v1/video-projects/" + encodeURIComponent(id), { headers });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        const message = data.message ?? data.error?.message ?? data.error ?? "Не удалось получить результат";
        return reply({ error: typeof message === "string" ? message : JSON.stringify(message) }, response.status);
      }

      const raw = String(data.status ?? "").toLowerCase();
      const status = raw === "complete" ? "COMPLETED"
        : raw === "error" || raw === "canceled" ? "FAILED"
        : raw === "rendering" ? "IN_PROGRESS" : "IN_QUEUE";

      if (action === "status") {
        return reply({ status, error: data.error?.message ?? data.error ?? undefined });
      }

      const url = data.downloads?.[0]?.url ?? data.download?.url ?? "";
      return reply({
        status,
        video: url ? { url } : undefined,
        video_url: url || undefined,
        error: data.error?.message ?? data.error ?? undefined,
      });
    }

    return reply({ error: "Неизвестное действие" }, 400);
  } catch (error) {
    return reply({ error: error instanceof Error ? error.message : "Внутренняя ошибка" }, 500);
  }
});
