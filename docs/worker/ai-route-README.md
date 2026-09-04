# Aura Worker — ruta `/ai` (Workers AI relay para AI Playlists sin clave)

Este documento es para pegar **a mano** en el dashboard de Cloudflare del Worker existente
`https://round-math-d64e.toberto4000.workers.dev`. **NO tocar las rutas `/verify` ni `/demo`**: el flujo de
licencias/suscripción debe quedar exactamente igual.

La app (v con `feat/ai-nokey`) llama a `POST /ai` con un body OpenAI-compatible
(`{"model", "messages", "temperature", "max_tokens"}`) **sin** header `Authorization`, y espera la
respuesta en formato OpenAI: `{"choices":[{"message":{"content":"..."}}]}`.

---

## ⚠️ 2026-09-04 — Cascada REORDENADA por calidad + 3B FUERA (reporte del dueño: "más rápido pero NADA QUE VER")

**Qué cambió** (rate limit, refuerzo anti-invención condicional, reshape OpenAI y rutas
`/verify`+`/demo` quedan IGUAL):

1. `AI_MODELS` reordenado por CALIDAD y sin el 3B:
   ```js
   const AI_MODELS = [
     "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
     "@cf/meta/llama-4-scout-17b-16e-instruct",
     "@cf/mistralai/mistral-small-3.1-24b-instruct",
     "@cf/meta/llama-3.1-8b-instruct-fast",
   ];
   ```
2. La respuesta ahora incluye `"model"` con el eslabón que respondió (aditivo; la app solo lee
   `choices[].message.content`).

**Por qué:** probes en vivo (2026-09-04, payload exacto de la app) mostraron que cuando el 70B
intermite, el Worker conmutaba en silencio a `llama-3.2-3b-instruct` — un modelo diminuto que ignora
instrucciones complejas e inventa parejas título↔artista ("Ave Maria" de Einaudi, "Ondine" de
Debussy), respondiendo en 6-12s en vez de 17-25s. Ese failover es literalmente el síntoma "crea las
listas más rápido pero nada que ver con lo que pido". El 70B correcto cumplía el contrato de las
filas 198/200; el 3B lo violaba. Con el 3B fuera, un fallo del 70B cae a modelos que sí sostienen
el contrato.

**Cómo desplegarlo:** el archivo completo actualizado es `docs/worker/worker.js` — copiar el
contenido entero en *Edit code* del dashboard (reemplaza todo) y **Deploy**. Verificar con el curl
de la sección (b) paso 6: la respuesta debe traer `"model":"@cf/meta/llama-3.3-70b-instruct-fp8-fast"`
(la mayoría de las veces) y el JSON de playlist correcto.

---

## ⚠️ 2026-08-30 — Refuerzo ANTI-INVENCION (directiva del dueño: "la IA FUNCIONA pero NO improvise")

**Qué cambió** (solo el bloque de instrucciones; rate limit, modelos en cascada, reshape OpenAI y
rutas `/verify`+`/demo` quedan IGUAL):

1. Constantes nuevas arriba del handler `handleAi`:
   - `AI_PLAYLIST_SYSTEM_MARKER = "Eres un ejecutor EXACTO de peticiones musicales"` — el texto
     literal con el que arranca el system prompt del generador de listas de la app
     (`AiPlaylistPrompt.buildMessages`).
   - `AI_ANTI_HALLUCINATION_BOOST` — un system message adicional (en inglés, directo al modelo):
     *"ANTI-HALLUCINATION ENFORCER (highest priority): ONLY include songs you are CERTAIN exist and
     are REAL releases by the stated artist. NEVER invent or approximate titles. If unsure about a
     song, SKIP it and suggest another well-known one. Prefer each artist's MOST POPULAR/streamed
     tracks. Output ONLY from your certain knowledge. Do NOT pad the count with uncertain songs:
     fewer correct tracks beat a full list of guesses."*
2. Dentro de `handleAi`, antes de probar modelos:
   ```js
   const isPlaylistRequest = messages.some(
     (m) => m && m.role === "system" && typeof m.content === "string" &&
       m.content.includes(AI_PLAYLIST_SYSTEM_MARKER)
   );
   const effectiveMessages = isPlaylistRequest ? messages.concat(AI_ANTI_HALLUCINATION_BOOST) : messages;
   ```
   y `env.AI.run(model, { messages: effectiveMessages, … })` (antes recibía `messages` directo).

**Por qué CONDICIONAL y no global:** la ruta `/ai` la comparten TRES clientes con sus propios system
prompts — el generador de listas IA, el worker de "Recomendado para ti (IA)" y la **traducción de
letras** (`LyricsTranslationHelper` → `OpenRouterService.translate`, filas 192/193 del registro de
regresiones). Una regla "si dudas, omítela" inyectada en una petición de TRADUCCIÓN dejaría líneas
sin traducir (el traductor exige devolver exactamente N líneas). El marker detecta la petición de
playlist por el arranque literal de su prompt y solo entonces se añade el refuerzo. **Si un día se
reformula el system prompt de `AiPlaylistPrompt`, hay que actualizar el marker en el Worker o el
refuerzo deja de aplicar en silencio** (y SOLO eso: nada se rompe, vuelve al comportamiento actual).

**Qué NO cambia:** `/verify`, `/demo`, el rate limit por IP/día (30), la cascada de modelos con
failover 5028, el conteo de uso en KV, el reshape de respuesta. Las traducciones y recomendaciones
pasan por el Worker exactamente igual que antes.

**Cómo desplegarlo:** el archivo completo actualizado es `docs/worker/worker.js` — copiar el
contenido entero en *Edit code* del dashboard (reemplaza todo) y **Deploy**. Verificar con el curl
de abajo (sección b, paso 6): la respuesta de un prompt de playlist debe seguir siendo JSON de
playlist; una petición tipo traducción (system prompt distinto) debe responder igual que antes.

---

## (a) Código a añadir al Worker

Añade este bloque al `fetch` existente (ANTES o DESPUÉS del routing de `/verify`//`/demo`, sin
modificarlas) y la función `handleAi` al final del archivo:

```js
// ─────────────────────────────────────────────────────────────────────────────
// NUEVO: dentro del fetch handler existente, junto al routing actual.
// El resto del handler (rutas /verify y /demo) queda EXACTAMENTE igual.
// ─────────────────────────────────────────────────────────────────────────────
//   const url = new URL(request.url);
//   if (url.pathname === '/ai') {
//     return handleAi(request, env);
//   }
//   ... routing existente de /verify y /demo sin cambios ...

// ─────────────────────────────────────────────────────────────────────────────
// NUEVO: handler completo de /ai (pegar al final del worker.js).
// Requiere el binding de Workers AI llamado `AI` (ver pasos del dashboard).
// Rate limit: reutiliza el KV binding existente (`LICENSES`; si tu binding se
// llama distinto, renombra `env.LICENSES` abajo). ~30 peticiones/IP/día.
// ─────────────────────────────────────────────────────────────────────────────
const AI_MODEL = '@cf/meta/llama-3.1-8b-instruct';
const AI_DAILY_LIMIT = 30;

async function handleAi(request, env) {
  if (request.method !== 'POST') {
    return jsonError('Method Not Allowed', 405);
  }

  // Rate limit por IP y día natural: clave KV `ai:<ip>:<yyyymmdd>`.
  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  const day = new Date().toISOString().slice(0, 10).replace(/-/g, ''); // yyyymmdd
  const rlKey = `ai:${ip}:${day}`;
  const used = parseInt((await env.LICENSES.get(rlKey)) || '0', 10);
  if (used >= AI_DAILY_LIMIT) {
    return jsonError('Daily AI limit reached, try again tomorrow', 429);
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return jsonError('Invalid JSON body', 400);
  }
  const messages = body.messages;
  if (!Array.isArray(messages) || messages.length === 0) {
    return jsonError('messages[] is required', 400);
  }

  let result;
  try {
    // El campo `model` que manda la app se ignora a propósito: el modelo lo decide el Worker.
    result = await env.AI.run(AI_MODEL, {
      messages,
      max_tokens: body.max_tokens ?? 2048,
    });
  } catch (e) {
    // 500 → la app reintenta y, si persiste, cae sola a su fallback keyless.
    return jsonError(`Workers AI error: ${e.message}`, 500);
  }

  // Cuenta el uso solo en llamadas que llegaron al modelo. TTL 2 días: las claves se autolimpian.
  await env.LICENSES.put(rlKey, String(used + 1), { expirationTtl: 172800 });

  // Reshape a formato OpenAI, que es lo único que parsea la app.
  return new Response(
    JSON.stringify({
      choices: [{ message: { role: 'assistant', content: result.response ?? '' } }],
    }),
    { headers: { 'Content-Type': 'application/json' } },
  );
}

function jsonError(message, status) {
  return new Response(JSON.stringify({ error: { message } }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
```

Notas:
- **CORS no hace falta**: la app es nativa (OkHttp), no un navegador.
- El body que manda la app incluye `temperature`; Workers AI la acepta como opcional — se puede
  pasar (`temperature: body.temperature`) o ignorar, indiferente.
- `@cf/meta/llama-3.1-8b-instruct` entra en el free tier de Workers AI (10k neurons/día). Si un día
  se quiere un modelo mejor, basta cambiar `AI_MODEL` — la app no necesita update.

## (b) Pasos en el dashboard de Cloudflare

1. Dashboard → Workers & Pages → worker `round-math-d64e` → **Settings → Bindings**.
2. **Add binding → Workers AI**, con el nombre exacto `AI`. Guardar.
3. (El KV `LICENSES` ya está bindeado por el flujo de licencias; no hay que crear nada nuevo.
   Si el binding KV tiene otro nombre, ajustar `env.LICENSES` en el snippet.)
4. **Edit code**: pegar el bloque del punto (a) — el `if (url.pathname === '/ai')` dentro del
   `fetch` existente y `handleAi`/`jsonError`/constantes al final del archivo.
5. **Deploy**.
6. Probar:
   ```
   curl -s -X POST https://round-math-d64e.toberto4000.workers.dev/ai \
     -H "Content-Type: application/json" \
     -d '{"messages":[{"role":"user","content":"Say OK"}],"max_tokens":10}'
   ```
   Debe devolver `{"choices":[{"message":{"content":"..."}}]}`.

## (c) Mientras no esté desplegado

Nada se rompe: si `/ai` devuelve 404/405 o falla, la app cae **en silencio** al fallback keyless
(`text.pollinations.ai/openai`) y las AI Playlists siguen funcionando. Desplegar la ruta solo
mueve el tráfico al Worker propio (mejor control, rate limit y modelo elegible).
