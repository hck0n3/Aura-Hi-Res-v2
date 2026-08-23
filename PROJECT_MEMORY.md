# 🧠 MEMORIA DEL PROYECTO: AURA HI-RES PLAYER (PORT MODELO SIMPMUSIC)

> Este archivo lo mantiene la IA. El dueño solo ejecuta betas y da feedback.
> Plan maestro: ver `PLAN.md`. Reglas duras del repo: ver `AGENTS.md` y `docs/REGRESSION_REGISTRY.md`.

## 📍 Estado Actual

- **2026-08-23 (tarde): LA APP NUNCA REPRODUCE NADA — ESA ES LA FALLA RAÍZ.**
  El dueño reveló: el código viene de "Echo Music", app quemada/bloqueada y eliminada
  por YouTube. Evidencia de logcat (cascada completa de 12 clientes falla):
  ANDROID_VR → LOGIN_REQUIRED "confirm you're not a bot"; ANDROID → formatos sin URL;
  IOS → HTTP 403 CON poToken presente; WEB → UNPLAYABLE. El poToken BotGuard SÍ se
  genera (len=120) pero es rechazado: **los fingerprints de clientes heredados están
  quemados server-side**. Diagnóstico completo en la memoria qwen
  (`project/causa-raiz-bloqueo-streaming.md`).
- **Mandato del dueño:** reconstruir la capa de streaming basándose en SimpMusic,
  que ÉL CONFIRMÓ que reproduce bien HOY en el mismo celular y red (v1.7.0 instalada).
- **Ground truth extraída del APK de SimpMusic instalado (dex strings, no suposiciones):**
  - ANDROID_VR **1.65.10** UA `com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip`
    (SIN Cronet; Aura usa 1.43.32/1.61.48 "Quest 3" + Cronet)
  - IOS **19.45.4** UA `com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)`
    (Aura usa 21.03.1 iOS 18_2)
  - ANDROID **21.03.36** Android **15** (Aura usa 21.03.38 Android 14)
  - VISIONOS **1.02** UA `com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 ...)`
  - ANDROID_MUSIC **7.27.52** UA `com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip`
  - MWEB UA iPad `Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) ... Safari/604.1,gzip(gfe)`
  - WEB_REMIX **1.20260304.03.00** (+legacy 1.20250310.01.00); WEB **2.20250312.04.00**;
    TVHTML5 **7.20250312.16.00**
  - poToken vía `serviceIntegrityDimensions(poToken=...)` igual que Aura; visitorData presente.
  - Endpoint player: `music.youtube.com/youtubei/v1/player?prettyPrint=false` (igual que Aura).
- **EN CURSO:** reemplazar clientes en `innertube/.../models/YouTubeClient.kt` + cascada en
  `YTPlayerUtils.kt` con los valores de SimpMusic; subagente investigador leyendo el repo
  GitHub de SimpMusic para orden exacto y contexto (task general-purpose-call_b2fadb95784e44959b82d49a).
- Auditoría adversarial del port P1/P2/P4 volvió: **0🔴 1🟡 2🔵 2❓**. El 🟡 (race de
  refresh-ahead) YA corregido en `MusicService.refreshUrlIfNearExpiry` (revalida entrada,
  deriva calidad del NUEVO response con el mismo predicado del guard, descarta si drift).
  Pendiente recompilar con ese fix.
- Port P1/P2/P4 anterior queda CONSERVADO (chunking, refresh-ahead, StreamHealth): la
  reconstrucción es de la capa de RESOLUCIÓN InnerTube (clientes), no del pipeline local.
- Nota de entorno: en máquinas con 16 GB o menos, matar daemons viejos de Gradle/Kotlin y
  java de extensiones VS Code (SonarLint come 1.4 GB) antes de compilar; el daemon con
  `-Xmx10g` + kotlin 6g puede agotar la RAM y crashear en `mergeProjectDex` (OOM nativo).
- Paquete debug instalado en el celular: `iad1tya.aura.music.debug` (device e6f2c8eb).
  Logs: `adb logcat -d --pid=<PID>` (el app.log del dispositivo a veces no se escribe).

### ⚠️ Hallazgo clave (REVISADO 2026-08-23 tarde)

~~La capa de streaming actual ya implementa casi todo el modelo.~~ **FALSO en lo
esencial: la resolución InnerTube NO produce streams reproducibles** (clientes quemados,
herencia de Echo Music). El pipeline local (caché, chunking, crossfade) sí funciona;
lo que falla es la PRIMERA milla: conseguir una URL de googlevideo. Por eso la
reconstrucción se concentra en `YouTubeClient.kt` + cascada de `YTPlayerUtils.kt`,
copiando las constantes probadas de SimpMusic, conservando contratos (`PlaybackData`,
lambda de `ResolvingDataSource`) y los 131 fixes del pipeline local.

## 🔬 Diff Aura-vs-SimpMusic por pilar (resultado del análisis)

| Pilar PLAN.md | Aura (antes) | SimpMusic | Veredicto / acción |
|---|---|---|---|
| Extracción InnerTube | Cascada 6 clientes + cipher self-heal + NewPipe + poToken + config remota | InnerTube estándar | Aura SUPERA → sin cambios |
| Caché inteligente URL | `songUrlCache` en memoria + persistido DataStore, chequeo `expiresAt` | Caché de URL | Empate + **P2 portado**: refresh-ahead |
| Pipeline resiliente | Retry por canción (reset ya existe en 4 sitios), `handleExpiredUrlError` preserva posición, mapeo de errores | Reintento silencioso con posición | Empate; **P1 portado**: chunking anti-throttle en audio |
| Transiciones suaves | Crossfade dual-player, 9 curvas, fade manual | Fade simple | Aura SUPERA → sin cambios |
| **Chunking de red** | Solo descargas y video | Reabre el stream en sub-rangos (~5 MiB) | **GAP REAL → P1 implementado en streaming de audio** |
| Diagnóstico | RESOLVE_TIMING en YTPlayerUtils | N/D | **P4 implementado**: StreamHealth agregado |

## 🛠️ Cambios implementados (2026-08-23)

### P1 — Chunking anti-throttle en el streaming de AUDIO (el gap real)
- `MusicService.createCacheDataSource()`: la capa de red (OkHttp) ahora va envuelta en
  `ChunkingDataSourceFactory` (la misma clase endurecida de descargas, registro #91),
  DENTRO de ambas capas de caché: `Resolving → downloadCache → playerCache → Chunking → OkHttp`.
- Reabre la URL googlevideo resuelta cada 5 MB con headers Range → evita el throttle de
  googlevideo a conexiones largas durante buffering/precarga (menos microcortes, FLAC carga más rápido).
- El gate por host dentro de `ChunkingDataSource` deja Qobuz FLAC / Saavn / podcasts / URLs
  directas en passthrough byte-idéntico. La resolución sigue corriendo UNA vez por open
  (orden idéntico al de DownloadUtil, probado en producción).
- Doc de `ChunkingDataSource.kt` actualizado (ya no es "solo descargas").

### P2 — Refresh-ahead de URL (caché inteligente)
- Nuevo `refreshUrlIfNearExpiry(mediaId, cached)` en MusicService: si un cache-hit de
  `songUrlCache` tiene menos de `URL_REFRESH_AHEAD_MS` (5 min) de vida, se sirve la URL
  actual sin latencia y se renueva en background; guard `urlRefreshInFlight` (máx. 1 por canción).
- La renovación usa `cached.delivered` (NO la calidad global) para no romper el lock de
  contenedor a mitad de canción. Hooks en los 2 cache-hit paths del lambda.

### P3 — Contador de reintentos (YA cubierto, verificado)
- `resetRetryCount` ya se llama en 4 sitios (transición, reproducción, errores). Sin cambios.

### P4 — StreamHealth (harness de diagnóstico)
- Nuevo `playback/StreamHealth.kt`: contadores agregados (resolves, fallos, ms promedio,
  cache hits, rebuffers, recuperaciones 403, refresh-ahead). SIN datos de usuario
  (regla AGENTS.md #4) — seguro para el app.log compartido.
- Hooks: lambda del resolver (inicio/fin/fallo de resolve), cache hits, `onPlaybackStatsReady`
  (rebuffers de media3), `handleExpiredUrlError` (snapshot al recuperarse de un 403).

## 🗺️ Arquitectura actual (mapeada 2026-08-23, con file:line)

Raíz: `app/src/main/kotlin/com/music/echo/` salvo indicación. Líneas aproximadas tras los
cambios de hoy (el archivo creció ~115 líneas).

### Resolución de URL de stream
- Punto de costura único: lambda de `ResolvingDataSource` en `playback/MusicService.kt`
  (createDataSourceFactory, ~:8300). Corto-circuitos: mediaId local, podcast, offline,
  descarga completa, export, guard de container vs FormatEntity, hits de
  songUrlCache/playerCache (+refresh-ahead nuevo). Si no hay hit:
  `YTPlayerUtils.playerResponseForPlayback` (`utils/YTPlayerUtils.kt:~364`, cap 30 s).
- Orden de fuentes: LOSSLESS → Qobuz propia → vault Qobuz; Saavn (módulo `:jiosaavn`);
  YouTube: cascada InnerTube.
- Cascada de clientes: MAIN `ANDROID_VR_1_43_32`, VIDEO `TVHTML5`, METADATA `WEB`,
  fallbacks `[TVHTML5, WEB_REMIX, ANDROID_VR_NO_AUTH, MOBILE, IOS, WEB]`. `noLogin` por llamada.
- Extracción de URL `findUrlOrNull`: url directa → signatureCipher con `CipherDeobfuscator`
  → `NewPipeExtractor.getStreamUrl` → `StreamInfo.getInfo`. Último recurso: WebView embed.
- Cipher propia (`utils/cipher/`): PlayerJsFetcher (disco), FunctionNameExtractor (13 hashes
  hardcoded + remoto), CipherDeobfuscator (WebView JS, self-heal), RemotePlayerConfig
  (player_configs.json en raw.githubusercontent hck0n3/main, ETag, configEpoch).
- n-transform: `utils/sabr/EjsNTransformSolver.kt` + reintento CipherDeobfuscator.
- poToken: `utils/potoken/PoTokenGenerator.kt` (BotGuard WebView), prewarm en MusicService.
- Validación HEAD `validateStatus` con short-circuit al primer cliente bueno.
- Contrato de salida: `PlaybackData` (url, expiración, formato, loudness, headers fallback).
  MusicService también lee calidad entregada + FormatEntity de ahí.
- Fallo total: `StreamResolutionException`.

### Reproducción
- Media3 ExoPlayer 1.10.1 es el ÚNICO motor de transporte (builder ~:2867, DefaultLoadControl
  buffer 120 s / 64 MB, WAKE_MODE_NETWORK, offload por hint). Audio focus a nivel SERVICIO
  (`setAudioAttributes(attrs, false)` + AudioFocusRequest propio) — mismo patrón que SimpMusic.
- Superpowered NO es motor: es DSP en AudioProcessors del sink (EQ/silence/normalization/limiter,
  `createRenderersFactory`). Cadena: ExoPlayer decodifica → procesadores → AudioTrack.
- MediaSource: `createMediaSourceFactory` (progressive + sniff; video: MergingMediaSource).
- Cadena DataSource **(ACTUALIZADA hoy)**: Resolving → Default → CacheDataSource(downloadCache)
  → CacheDataSource(playerCache) → **ChunkingDataSource(5 MB)** → OkHttpDataSource.
- Video: `videoDataSourceFactory` aparte (su propio chunking + cliente OkHttp con UA por cliente).

### Caché
- URLs: `songUrlCache: ConcurrentHashMap<String, CachedStream>` EN MEMORIA + PERSISTIDO a
  DataStore (`SongUrlCacheBlobKey`, LRU). CachedStream = url + expiresAt + delivered + requested.
  **Nuevo: refresh-ahead renueva en background si quedan <5 min de vida.**
- Bytes: playerCache SimpleCache `filesDir/exoplayer` (AppModule.kt), downloadCache
  `filesDir/download`, anidados. Ghost cache: conserva bytes, re-resuelve URL.
- Descargas: `ChunkingDataSource` (`DownloadUtil`) — misma clase ahora en streaming de audio.

### Retry / errores
- Resolve: cap 30 s, cascada con HEAD fast-fail, reintento anónimo `noLogin` para LOGIN_REQUIRED,
  reintento n-transform, reintento cipher con JS fresco, backoff por renderer death.
- Errores del loader mapeados (NO_STREAM=1000001, IO_NETWORK_*, REMOTE_ERROR).
- `onPlayerError`: video→audio fallback, límite reintentos por canción, corrupción
  (borra bytes+formato), 403 URL expirada (`handleExpiredUrlError` preserva posición),
  network-like con drop de URL, AutoSkipNextOnErrorKey.
- Precarga: `preloadUpcomingItems` (default 2, slider hasta 10, gates de batería).

### Crossfade (YA existe, es extenso — INTACTO)
- Claves Crossfade{Enabled,Duration,Gapless,Curve}; `scheduleCrossfade`,
  `prepareSecondaryPlayer` (segundo ExoPlayer, volumen 0), `performCrossfadeSwap`,
  `cleanupCrossfade`; loop 40 ms con curvas de `playback/CrossfadeMath.kt` (9 curvas).
- Fade-in en cambio manual. Duck por audio focus.
- ⚠️ Regla del registro (filas #90/#96/#104): con crossfade ON, los avances esquivan los callbacks
  de media3; toda lógica de "empezó canción" debe vivir en helper compartido.

### Módulos y dependencias
- settings.gradle.kts: `:app :migration :canvas :innertube :kugou :lrclib :betterlyrics
  :simpmusic :youlyplus :shazamkit :artistvideo :applecanvas :echomusiccanvas :paxsenixlyrics
  :unison :jiosaavn`. `:simpmusic` es SOLO letras. Streaming real: `:app`, `:innertube`, `:jiosaavn`.
- media3 1.10.1 · ktor 3.4.0 (sin retrofit) · NewPipeExtractor v0.25.2 (jitpack) · jsoup 1.22.1 ·
  brotli · Kotlin 2.3.10 · AGP 9.0.0 · JDK 21. Superpowered nativo sin artefacto maven.

## 📋 Roadmap Inmediato

- [x] Mapa completo de la capa de streaming actual.
- [x] Investigación arquitectura SimpMusic + diff por pilar.
- [x] Implementación P1/P2/P4 (P3 ya cubierto) + build verificado.
- [x] Auditoría adversarial (0🔴 1🟡 corregido en código, pendiente recompilar).
- [x] Instalar en dispositivo vía adb → **la app NO reproduce: cascada InnerTube entera falla**.
- [x] Diagnóstico con logcat: clientes InnerTube quemados (herencia Echo Music).
- [x] Ground truth de constantes extraída del APK de SimpMusic 1.7.0 instalado.
- [ ] **Reconstruir clientes InnerTube con constantes de SimpMusic** (YouTubeClient.kt + cascada YTPlayerUtils.kt; esperando informe del investigador para el orden exacto).
- [ ] Rebuild + instalar + **verificar que reproduce DE VERDAD** (criterio del dueño: "SI O SI").
- [ ] Commit de los cambios (no pedido aún; NUNCA publicar tag/release sin permiso).
- [ ] Entregar beta + actualizar esta memoria.

## 📜 Historial de Cambios (Log)

- **2026-08-23 (1):** Sesión iniciada. Leídos PLAN.md, AGENTS.md, REGRESSION_REGISTRY.md (131 filas).
- **2026-08-23 (2):** Mapa completo de la capa actual (sección de arriba).
- **2026-08-23 (3):** Diff Aura-vs-SimpMusic terminado. Gap real único: chunking de red en el
  streaming de audio. Retry/posición/audio-focus/crossfade ya equivalentes o mejores en Aura.
- **2026-08-23 (4):** Implementados P1 (chunking audio), P2 (refresh-ahead), P4 (StreamHealth).
  Build en curso.
- **2026-08-23 (5):** Build verificado BUILD SUCCESSFUL + StreamHealth en classes18.dex.
  Auditoría adversarial: 0🔴 1🟡 2🔵 2❓; 🟡 corregido en MusicService.kt (sin recompilar).
- **2026-08-23 (6):** APK instalado (iad1tya.aura.music.debug). El dueño revela: la app
  NUNCA reproduce y el código viene de Echo Music (quemada por YouTube). Logcat confirma
  cascada entera fallando (LOGIN_REQUIRED bot-check / 403 / UNPLAYABLE). Cambio de plan:
  reconstruir la resolución InnerTube con constantes de SimpMusic.
- **2026-08-23 (7):** Ground truth extraída del APK de SimpMusic 1.7.0 instalado en el
  mismo celular (dex strings): clientes ANDROID_VR 1.65.10 eureka-user, IOS 19.45.4,
  ANDROID 21.03.36/Android15, VISIONOS 1.02, ANDROID_MUSIC 7.27.52, MWEB iPad-UA,
  WEB_REMIX 1.20260304.03.00. Subagente leyendo el repo GitHub para el orden de cascada.

## 🏛️ Decisiones Arquitectónicas Clave

- No reescribir desde cero: port comparativo conservando `PlaybackData` y el lambda de
  `ResolvingDataSource` (AGENTS.md reglas 1-3; 131 fixes viven en estos archivos).
- **Chunking DENTRO de las capas de caché (no fuera del resolver):** envolver el resolver
  con chunking re-ejecutaría el lambda completo (Room reads, guard #57, upserts) cada 5 MB —
  riesgo de regresión y gasto de batería (regla 7). El patrón elegido es el ya probado en
  DownloadUtil y en la fuente de video: resolver afuera, chunker junto a la red. La frescura
  de URL la cubren el refresh-ahead (P2) + `handleExpiredUrlError` existente.
- Refresh-ahead renueva con la calidad ENTREGADA (`delivered`), no la global, para respetar
  el lock de contenedor a mitad de canción (ídem filas #35/#40: estampar lo entregado).
- StreamHealth solo agrega contadores; cero datos de usuario (regla 4: el app.log se comparte).
