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

  const authKey = Deno.env.get("GIGACHAT_AUTH_KEY");
  if (!authKey) return reply({ error: "GIGACHAT_AUTH_KEY не настроен в Supabase Secrets" }, 503);

  try {
    const body = await req.json();
    const incoming = Array.isArray(body.messages) ? body.messages.slice(-16) : [];
    const messages = incoming.map((m: any) => ({
      role: m.role === "model" || m.role === "assistant" ? "assistant" : "user",
      content: String(m.text ?? m.content ?? "").slice(0, 12000),
    })).filter((m: any) => m.content.trim().length > 0);
    if (!messages.length) return reply({ error: "Нет сообщений для ответа" }, 400);

    // GigaChat TLS uses the Russian Trusted CA. Add its official root and issuing CA
    // instead of disabling certificate verification.
    const caUrls = [
      "https://gu-st.ru/content/lending/russian_trusted_root_ca_pem.crt",
      "https://gu-st.ru/content/lending/russian_trusted_sub_ca_pem.crt",
    ];
    const caCerts: string[] = [];
    for (const url of caUrls) {
      const caResponse = await fetch(url);
      if (!caResponse.ok) throw new Error("Не удалось загрузить официальный сертификат НУЦ Минцифры");
      caCerts.push(await caResponse.text());
    }
    const gigaHttp = Deno.createHttpClient({ caCerts });

    const tokenResponse = await fetch("https://ngw.devices.sberbank.ru:9443/api/v2/oauth", {
      method: "POST",
      client: gigaHttp,
      headers: {
        "Authorization": "Basic " + authKey,
        "RqUID": crypto.randomUUID(),
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "application/json",
      },
      body: "scope=GIGACHAT_API_PERS",
    });
    const tokenData = await tokenResponse.json().catch(() => ({}));
    if (!tokenResponse.ok || !tokenData.access_token) {
      gigaHttp.close();
      return reply({ error: "Не удалось получить токен GigaChat. Проверь Authorization Key и доступ API." }, tokenResponse.status || 502);
    }

    const response = await fetch("https://api.giga.chat/v1/chat/completions", {
      method: "POST",
      client: gigaHttp,
      headers: {
        "Authorization": "Bearer " + tokenData.access_token,
        "Content-Type": "application/json",
        "Accept": "application/json",
      },
      body: JSON.stringify({
        model: "GigaChat-2",
        messages: [
          { role: "system", content: "Ты GORILLA AI — полезный, честный и дружелюбный ИИ-помощник. Отвечай на языке пользователя. Не выдумывай факты и ссылки." },
          ...messages,
        ],
        temperature: 0.7,
        max_tokens: 2048,
      }),
    });
    const data = await response.json().catch(() => ({}));
    gigaHttp.close();
    if (!response.ok) {
      const message = data.message ?? data.error?.message ?? "GigaChat не смог обработать запрос";
      return reply({ error: String(message) }, response.status);
    }
    const answer = String(data.choices?.[0]?.message?.content ?? "").trim();
    if (!answer) return reply({ error: "GigaChat вернул пустой ответ" }, 502);
    return reply({ answer, sources: [], provider: "GigaChat-2" });
  } catch (error) {
    return reply({ error: error instanceof Error ? error.message : "Внутренняя ошибка" }, 500);
  }
});
