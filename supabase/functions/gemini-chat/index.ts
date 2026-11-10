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

  const key = Deno.env.get("GEMINI_API_KEY");
  if (!key) return reply({ error: "GEMINI_API_KEY не настроен в Supabase Secrets" }, 503);
  try {
    const body = await req.json();
    const incoming = Array.isArray(body.messages) ? body.messages.slice(-16) : [];
    if (!incoming.length) return reply({ error: "Нет сообщений для ответа" }, 400);
    const contents = incoming.map((m: any) => ({
      role: m.role === "model" ? "model" : "user",
      parts: [{ text: String(m.text ?? "").slice(0, 12000) }],
    })).filter((m: any) => m.parts[0].text.trim().length > 0);
    if (!contents.length) return reply({ error: "Пустое сообщение" }, 400);

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + encodeURIComponent(key),
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          systemInstruction: { parts: [{ text: "Ты GORILLA AI — полезный, честный и дружелюбный ИИ-помощник. Отвечай на языке пользователя. Если используешь веб-поиск, указывай, что сведения найдены в интернете, и перечисляй источники, когда они доступны. Не выдумывай факты и ссылки." }] },
          contents,
          tools: [{ google_search: {} }],
          generationConfig: { temperature: 0.7, maxOutputTokens: 2048 },
        }),
      },
    );
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const message = data.error?.message ?? "Gemini не смог обработать запрос";
      return reply({ error: message }, response.status);
    }
    const parts = data.candidates?.[0]?.content?.parts ?? [];
    const answer = parts.map((p: any) => p.text ?? "").join("").trim();
    if (!answer) return reply({ error: "Gemini вернул пустой ответ" }, 502);
    const chunks = data.candidates?.[0]?.groundingMetadata?.groundingChunks ?? [];
    const sources = chunks.map((c: any) => c.web).filter((w: any) => w?.uri)
      .map((w: any) => ({ title: w.title ?? w.uri, url: w.uri }));
    return reply({ answer, sources });
  } catch (error) {
    return reply({ error: error instanceof Error ? error.message : "Внутренняя ошибка" }, 500);
  }
});
