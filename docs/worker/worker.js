// Aura Hi-Res Player — license + one-device-per-subscription Worker.
// Bindings: KV namespace "LICENSES". Env var: PRODUCT_ID (Gumroad product id).
// AÑADIDO: binding Workers AI "AI" para la ruta /ai (AI Playlists sin clave).
const GUMROAD_VERIFY = "https://api.gumroad.com/v2/licenses/verify";
const INACTIVITY_MS = 2 * 24 * 60 * 60 * 1000; // auto-release after 2 days idle
// Varios equipos con el MISMO correo de la compra (pedido del dueño 2026-10-30, fila 530). Solo cuenta cuando la app manda
// `email` y coincide con el de Gumroad; sin correo todo sigue como antes (UN equipo por clave). Se cambia con la variable
// MAX_DEVICES del Worker, sin tocar la app.
const DEFAULT_MAX_DEVICES = 3;
// Lista de modelos en orden de CALIDAD, no de disponibilidad a secas (reporte del dueño
// 2026-09-04: "la IA crea las playlists más RÁPIDO pero NADA QUE VER con lo que pido").
// Probes en vivo con el payload exacto de la app: cuando el 70B intermite (capacidad de
// Workers AI), el eslabón 2 era llama-3.2-3b — un modelo diminuto que IGNORA instrucciones
// complejas e INVENTA parejas título↔artista ("Ave Maria" de Einaudi, "Ondine" de Debussy...),
// respondiendo en 6-12s en vez de 17-25s: exactamente "más rápido pero nada que ver". El 3B
// queda FUERA de la cascada; si el 70B falla se cae a modelos que sí sostienen el contrato
// anti-invención (fila 198): scout-17b (multimodal, instrucciones complejas) → mistral-24b →
// 8b-fast como ÚLTIMO recurso. Se sigue usando el primero que RESPONDA.
const AI_MODELS = [
  "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
  "@cf/meta/llama-4-scout-17b-16e-instruct",
  "@cf/mistralai/mistral-small-3.1-24b-instruct",
  "@cf/meta/llama-3.1-8b-instruct-fast",
];
const AI_DAILY_LIMIT = 30;

// Refuerzo anti-invención SOLO para las peticiones de LISTAS IA de la app (Aura Hi-Res v2).
// La ruta /ai la comparten TRES clientes con system prompts propios: listas IA, Recomendado IA y
// TRADUCCIÓN DE LETRAS (LyricsTranslationHelper → OpenRouterService.translate, ver fila 192 del
// registro de regresiones). Inyectar la regla de playlists en una traducción la corrompería
// ("omítela si dudas" deja líneas sin traducir). Por eso el refuerzo es CONDICIONAL: se detecta la
// petición de playlist por el system prompt EXACTO que AiPlaylistPrompt.buildMessages emite.
// Marca de arranque del prompt de la app: "Eres un ejecutor EXACTO de peticiones musicales." — es
// literal en el primer system message; ninguna traducción ni recomendación empieza así.
const AI_PLAYLIST_SYSTEM_MARKER = "Eres un ejecutor EXACTO de peticiones musicales";
// Refuerzo worker-side (el dueño pidió: la IA "NO improvise — que sea lo más exacta posible").
// Llama 70B a veces INVENTA canciones; el resolver de la app las descarta y la lista sale corta.
// Este empujón adicional tras los mensajes de la app reduce las propuestas inventadas en origen.
const AI_ANTI_HALLUCINATION_BOOST = [
  {
    role: "system",
    content:
      "ANTI-HALLUCINATION ENFORCER (highest priority): ONLY include songs you are CERTAIN exist " +
      "and are REAL releases by the stated artist. NEVER invent or approximate titles. If unsure " +
      "about a song, SKIP it and suggest another well-known one. Prefer each artist's MOST " +
      "POPULAR/streamed tracks. Output ONLY from your certain knowledge. Do NOT pad the count " +
      "with uncertain songs: fewer correct tracks beat a full list of guesses.",
  },
];

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST") return json({ status: "invalid" }, 404);

    if (url.pathname === "/demo") return handleDemo(request, env);
    if (url.pathname === "/ai") return handleAi(request, env);
    if (url.pathname !== "/verify") return json({ status: "invalid" }, 404);

    let body;
    try { body = await request.json(); } catch (e) { return json({ status: "invalid" }, 400); }
    const licenseKey = body && body.license_key;
    const deviceId = body && body.device_id;
    if (!licenseKey || !deviceId) return json({ status: "invalid" }, 400);

    const masterKeys = (env.MASTER_KEYS || "").split(",").map((s) => s.trim()).filter(Boolean);
    if (masterKeys.includes(licenseKey)) {
      // Clave maestra: sin límite de equipos y sin Gumroad (no hay compra, así que no hay correo que comparar). Si el usuario
      // escribió SU correo, se acepta tal cual para que sus equipos se emparejen entre sí: cada correo distinto = grupo distinto.
      // OJO: ese correo no está comprobado; quien conozca la clave maestra y el correo de otro podría entrar en su grupo.
      // Para algo realmente privado, el usuario usa un código escrito (20 caracteres al azar) en vez del correo.
      const typed = normalizeEmail(body && body.email);
      return json(typed.includes("@") ? { status: "active", email_ok: true } : { status: "active" });
    }

    const gum = await verifyGumroad(env.PRODUCT_ID, licenseKey);
    if (gum.status === "invalid") return json({ status: "invalid" });
    if (gum.status === "ended") return json({ status: "ended" });
    if (gum.status === "error") return json({ status: "error" });

    // Correo opcional: lo manda la app nueva cuando el usuario lo escribe. Se COMPARA con el de la compra y no se guarda.
    const sentEmail = normalizeEmail(body && body.email);
    const emailOk = sentEmail !== "" && sentEmail === normalizeEmail(gum.email);
    if (sentEmail !== "" && !emailOk) return json({ status: "email_mismatch" });

    try {
      const now = Date.now();
      const raw = await env.LICENSES.get(licenseKey);
      const maxDevices = parseMaxDevices(env.MAX_DEVICES);
      const decision = decideBinding(parseBinding(raw), deviceId, now, emailOk ? maxDevices : 1);
      if (decision.allowed) {
        await env.LICENSES.put(licenseKey, JSON.stringify(decision.binding));
        // email_ok: la app solo empareja sola los equipos del comprador cuando ESTE Worker confirmó su correo contra Gumroad.
        // Las claves maestras salen antes (arriba), sin esta marca: nunca se emparejan solas ni tienen límite.
        return json(emailOk ? { status: "active", email_ok: true } : { status: "active" });
      }
      return json({ status: "device_mismatch" });
    } catch (e) {
      return json({ status: "error" });
    }
  },
};

function normalizeEmail(v) {
  return typeof v === "string" ? v.trim().toLowerCase() : "";
}

function parseMaxDevices(v) {
  const n = parseInt(v, 10);
  return Number.isFinite(n) && n >= 1 && n <= 10 ? n : DEFAULT_MAX_DEVICES;
}

// El registro de una clave en KV. Formato antiguo: {device_id, last_seen}. Formato nuevo: {devices:[{id,last_seen}]} y
// ADEMÁS device_id/last_seen del equipo más reciente, para que un Worker antiguo (si algún día se vuelve atrás) siga
// entendiéndolo: ese equipo seguiría entrando y los demás esperarían a que pasen los 2 días de inactividad.
function parseBinding(raw) {
  if (!raw) return [];
  try {
    const b = JSON.parse(raw);
    if (Array.isArray(b.devices)) {
      return b.devices.filter((d) => d && d.id && Number.isFinite(d.last_seen));
    }
    if (b.device_id && Number.isFinite(b.last_seen)) return [{ id: b.device_id, last_seen: b.last_seen }];
  } catch (e) { /* registro ilegible: se trata como vacío */ }
  return [];
}

// Pura (se prueba sola). Con limit = 1 es EXACTAMENTE la regla de siempre: entra el equipo que ya estaba, o cualquiera si
// el anterior lleva más de 2 días sin conectarse. Con limit > 1 caben hasta `limit` equipos activos a la vez.
function decideBinding(devices, deviceId, now, limit) {
  const live = devices.filter((d) => d.id === deviceId || now - d.last_seen <= INACTIVITY_MS);
  const mine = live.find((d) => d.id === deviceId);
  if (!mine && live.length >= limit) return { allowed: false };
  const next = live.filter((d) => d.id !== deviceId);
  next.push({ id: deviceId, last_seen: now });
  // Los equipos inactivos se sueltan; los activos se conservan (un equipo que entra SIN correo no echa a los demás).
  return { allowed: true, binding: { devices: next.slice(-10), device_id: deviceId, last_seen: now } };
}

async function handleAi(request, env) {
  // Rate limit por IP y día: clave KV `ai:<ip>:<yyyymmdd>` (no colisiona con licencias/demo).
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const day = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const rlKey = `ai:${ip}:${day}`;
  const used = parseInt((await env.LICENSES.get(rlKey)) || "0", 10);
  if (used >= AI_DAILY_LIMIT) return json({ error: { message: "Daily AI limit reached, try again tomorrow" } }, 429);

  let body;
  try { body = await request.json(); } catch (e) { return json({ error: { message: "Invalid JSON body" } }, 400); }
  const messages = body && body.messages;
  if (!Array.isArray(messages) || messages.length === 0) return json({ error: { message: "messages[] is required" } }, 400);

  // Prueba cada modelo en orden; usa el primero que responda. Un modelo deprecado (5028) o caído lanza
  // y se pasa al siguiente. Solo si TODOS fallan devolvemos 500 (y la app cae a su fallback keyless).
  // ANTI-INVENCION (condicional): si la petición viene del generador de LISTAS IA de la app, se
  // añade un system message extra que refuerza la regla "cero canciones inventadas". Solo para
  // playlists — ver AI_PLAYLIST_SYSTEM_MARKER arriba (las traducciones pasan IGUAL que antes).
  const isPlaylistRequest = messages.some(
    (m) => m && m.role === "system" && typeof m.content === "string" &&
      m.content.includes(AI_PLAYLIST_SYSTEM_MARKER)
  );
  const effectiveMessages = isPlaylistRequest ? messages.concat(AI_ANTI_HALLUCINATION_BOOST) : messages;
  let result = null;
  let usedModel = null;
  let lastErr = "no model available";
  for (const model of AI_MODELS) {
    try {
      const r = await env.AI.run(model, { messages: effectiveMessages, max_tokens: body.max_tokens || 2048 });
      if (r && typeof r.response === "string") { result = r; usedModel = model; break; }
    } catch (e) {
      lastErr = (e && e.message) ? e.message : String(e);
    }
  }
  if (!result) return json({ error: { message: "Workers AI error: " + lastErr } }, 500);

  await env.LICENSES.put(rlKey, String(used + 1), { expirationTtl: 172800 });
  // "model" expone QUÉ eslabón respondió: la cascada conmuta en silencio y desde fuera era
  // imposible distinguir un 70B correcto de un relleno inventando canciones (2026-09-04 — el
  // diagnóstico requirió inferir por velocidad/estilo). La app ignora el campo (solo Lee el
  // choices[].message.content), así que es aditivo y retrocompatible.
  return json({ model: usedModel, choices: [{ message: { role: "assistant", content: result.response || "" } }] });
}

async function handleDemo(request, env) {
  let body;
  try { body = await request.json(); } catch (e) { return json({ status: "invalid" }, 400); }
  const deviceId = body && body.device_id;
  if (!deviceId) return json({ status: "invalid" }, 400);
  const start = body.start === true;
  try {
    const now = Date.now();
    const k = "demo:" + deviceId;
    const existing = await env.LICENSES.get(k);
    let started;
    if (existing) {
      started = parseInt(existing, 10);
      if (!Number.isFinite(started)) started = now;
    } else if (start) {
      started = now;
      await env.LICENSES.put(k, String(started));
    } else {
      return json({ status: "none", server_time: now });
    }
    return json({ status: "ok", demo_started_at: started, server_time: now });
  } catch (e) {
    return json({ status: "error" });
  }
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json" },
  });
}

async function verifyGumroad(productId, licenseKey) {
  try {
    const form = new URLSearchParams();
    form.set("product_id", productId);
    form.set("license_key", licenseKey);
    form.set("increment_uses_count", "false");
    const res = await fetch(GUMROAD_VERIFY, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: form.toString(),
    });
    const data = await res.json();
    if (!data || data.success !== true) return { status: "invalid" };
    const p = data.purchase || {};

    // Access follows the PAID period, automatically:
    //  - A cancellation alone does NOT end access — the subscriber keeps the month they already paid for.
    //  - "ended" only once subscription_ended_at / subscription_failed_at is in the PAST. Then the app
    //    cuts on its next check and resumes automatically when Gumroad reports a fresh active charge.
    const now = Date.now();
    const isPast = (t) => {
      if (!t) return false;
      const ms = typeof t === "number" ? (t < 1e12 ? t * 1000 : t) : Date.parse(t);
      return Number.isFinite(ms) ? ms <= now : true;
    };
    const ended = isPast(p.subscription_ended_at) || isPast(p.subscription_failed_at);
    return { status: ended ? "ended" : "active", email: p.email };
  } catch (e) {
    return { status: "error" };
  }
}

