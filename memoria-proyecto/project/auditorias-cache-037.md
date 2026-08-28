---
name: Auditorías caché/consumo datos HALLAZGO-037 (imágenes + audio/video)
description: Estado 2026-08-27 — Dos auditorías read-only completas del HALLAZGO-037 (FASE 6, consumo alto de datos): Auditoría A (imágenes/video visual, H1-H7) y Auditoría B (audio/video/ahorro, H0-H13). Causas confirmadas + fix + riesgo. H1 ya implementado. GATEADAS al veredicto BETA-014; NO implementar hasta luz verde.
type: project
---

Dos auditorías read-only de caché/consumo de datos (HALLAZGO-037, FASE 6)
devueltas por agentes de fondo el 2026-08-27. **Cero código tocado**; git
limpio. Gateadas al veredicto BETA-014. OJO: ambas usan numeración "H" propia
que se solapa — se citan como **A-Hn** (imágenes) y **B-Hn** (audio/video).

**Por qué:** el dueño reportó consumo alto (portadas re-descargadas, portadas
animadas, video con carga automática). Cada auditoría costó ~130k tokens.
**Cómo aplicar:** al dar luz verde a FASE 6, arrancar por el paquete de fixes
rankeado al final; leer en local los archivos marcados "ilegibles por tamaño".

---

## AUDITORÍA A — imágenes y video visual (A-H1..A-H7)

Raíz verificada LOCAL (código actual). Raíz: `app/src/main/kotlin/com/music/echo/`.

- **A-H1 ALTO ✅ YA IMPLEMENTADO**: MusicService borraba disk cache de Coil bajo
  presión de memoria. Original en `MusicService.kt:9627-9648` (`onTrimMemory`
  → `loader.diskCache?.clear()` :9647). Ya mudado: `App.kt:348-373` solo toca
  memoryCache (correcto). Config caché `App.kt:1843 DEFAULT_IMAGE_CACHE_SIZE_MB=2048`.
  Nota: verificar que el comentario obsoleto :9616-9626 quede coherente.
- **A-H2 ALTO**: hasta 6 URLs distintas por la misma portada lógica (cache keys
  duplicadas). `ui/utils/YouTubeUtils.kt` `String.resize(w,h)` reescribe
  `lh3.googleusercontent` con sufijo tamaño-dependiente. Tamaños: 1200 (player/
  MediaItemExt:62,70/Thumbnail:486,1038/menus), 640 (AuraHomeScreen:1568),
  **544 (Items.kt:1631 + PERSISTIDA en DB DatabaseDao.kt:1976)**, 384
  (CoilBitmapLoader:57 notificación), 256 (AuraContent:335), 128 (AuraQueue/
  AuraPlayer:2389 bloom). Agravante: `Song.toMediaMetadata()` usa variante 544
  de DB, `SongItem.toMediaMetadata()` produce 1200 → misma canción descarga 544
  en listas y 1200 en player. FIX: URL canónica única (2 buckets: listas/DB 544,
  player 1200) en un solo punto (mapper en `components{}` del ImageLoader
  App.kt:1794+ o normalizar en toMediaMetadata) + migración DB. Riesgo MEDIO.
- **A-H3 ALTO**: artist video sin caché disco, autoplay loop muteado, sin gating
  por red. `artistvideo/.../ArtistVideo.kt` — `DefaultMediaSourceFactory(
  DefaultDataSource(OkHttpDataSource))` SIN CacheDataSource; repeatMode ONE,
  volume 0. Gates `AuraArtistScreen.kt:197` = ShowArtistVideoKey(default true)
  && !deviceThrottle && appInForeground; SIN gate WiFi/datos. FIX: CacheDataSource
  sobre SimpleCache LRU 128-256MB (patrón CanvasVideoCache de
  CanvasArtworkPlayer.kt:60-71) + gate WiFi con `reco/GenreCache.kt:70 isWifi()`.
  Riesgo SEGURO-MEDIO. Mayor gasto de bytes por evento de la auditoría.
- **A-H4 MEDIO**: prewarm de cola descarga TODA la cola a 1200px en cada cambio.
  `Thumbnail.kt:486-493` LaunchedEffect(mediaItems) enqueue sin distinctBy.
  FIX: prewarm solo próximas K (3-5) al tamaño canónico A-H2. Riesgo SEGURO.
- **A-H5 MEDIO**: metadatos canvas/portadas animadas solo en RAM (TTL 24h/60s).
  módulo canvas/ TidalCanvasProvider:57,64, MonochromeApiCanvas:59,66, etc.
  Bytes de video SÍ tienen disco (256MB CanvasVideoCache). FIX: persistir mapa
  canción/álbum→canvasUrl en Room/DataStore con TTL largo. Riesgo SEGURO.
- **A-H6 MEDIO/BAJO**: fallback notificación sin caché. `CoilBitmapLoader.kt`
  fallback HttpURLConnection no lee/escribe caché. FIX: unificar a URL canónica
  A-H2 + cachear fallback. Riesgo SEGURO.
- **A-H7 BAJO informativo**: animadas estáticas pasan por disk cache (Coil 3
  default), sin fuga. Letras no fetchean imágenes. Regla 7 (nada de red por
  fotograma) verificada.

## AUDITORÍA B — audio, video y ahorro (B-H0..B-H13)

Caveat: auditó rama `main` del repo público `hck0n3/Aura-Hi-Res-v2` (solo tenía
web_fetch); líneas con `~` aproximadas. Cambios locales BETA-011/012 son EQ/faders,
no afectan capa de streaming.

- **B-H0 ALTO**: polling OwnerAnnouncements cada 30-60s, sin ETag, con cache-buster.
  `notices/OwnerAnnouncements.kt` — log :137 (cada línea = fetch completo);
  MIN_REFRESH_MS 60s :77 solo aplica a !force; `OwnerNoticesWarmup` (OwnerNoticePopup:22-27)
  refresh force=true en cada re-entrada en composición; fetchRemoteBody añade
  `?t=${currentTimeMillis()}` (cache-buster) + `useCaches=false` sin If-None-Match.
  ~0.6-3 MB/hora + despertar radio móvil. FIX: quitar `?t=`, enviar If-None-Match/ETag
  (304 ~100 bytes), throttle también a force (5-10 min), montar warmup 1 vez por
  Activity, no reescribir caché si body no cambió. Riesgo SEGURO.
- **B-H1 ALTO**: descargas automáticas sobre datos móviles sin gate. `DownloadUtil.kt`
  DownloadManager SIN setRequirements (default = cualquier red); `AutoDownloadOnLikeKey`
  TRUE por defecto → like encola álbum entero (audio+video companion) por datos.
  FIX: `Requirements(REQUIREMENT_NETWORK_UNMETERED)` o pref "solo WiFi" default ON.
  Riesgo SEGURO. No toca ruta crítica.
- **B-H2 ALTO condicional**: doble extracción por canción — PipePipe fallando baja
  base.js ~2MB y luego BravePipe otro. `innertube/pages/NewPipe.kt` newPipePlayer
  ~:262-275 fallback a BraveNewPipe. **ExtractorCircuitBreaker NO lo encontró en
  NewPipe.kt — cableado PENDIENTE** (404 en playback/ExtractorCircuitBreaker.kt).
  FIX: confirmar/activar circuit breaker + persistir estado. Riesgo MEDIO (ruta
  crítica indirecta — con characterization tests estilo 021).
- **B-H3 ALTO-MEDIO**: canvas fuerza bitrate más alto, sin gate datos/Data Saver.
  `CanvasArtworkPlayer.kt` — tier HIGH `setForceHighestSupportedBitrate(true)`;
  solo LOW/ULTRA limitan setMaxVideoSize(1280,1280). Caché disco 256MB bien.
  FIX: en datos móviles/Data Saver forzar path LOW/ULTRA. Riesgo SEGURO.
- **B-H4 MEDIO**: letras solo en memoria (LruCache 3) + preload puede duplicar fetch.
  `lyrics/LyricsHelper.kt:57` MAX_CACHE_SIZE=3; preload MusicService ~:11390 consulta
  `database.lyrics(mediaId)` primero. PENDIENTE: si preload persiste a BD. FIX:
  persistir letras a tabla Room `lyrics` + LyricsHelper lee BD antes que red. SEGURO.
- **B-H5 MEDIO**: export/sonda video usa 720p fijo, ignora cap 360p por datos que
  sí aplica al video mode. `ExportFormatChooser.kt:82` videoStreamUrlDiag(720);
  video mode in-app sí usa cap `isActiveNetworkMetered ? 360 : 720`. FIX: pasar cap
  por red también a export/sonda. Riesgo SEGURO. (Liga con HALLAZGO-035.)
- **B-H6 MEDIO**: no hay dedup de resolves paralelos del mismo videoId. songUrlCache
  TTL audio = expiry real (~6h); video TTL 5 min (corto). Sin `Map<String,Deferred>`.
  FIX: un solo ConcurrentHashMap<String,Deferred<ResolveResult>> compartido.
  Riesgo MEDIO — SÍ toca ruta crítica (con tests, sin cambiar refresh-ahead/403).
- **B-H7 MEDIO estructural**: Data Saver hoy solo cubre preload (`PreloadPlanning.kt`).
  FIX: Data Saver paraguas (preload 0-1, canvas LOW, descargas solo WiFi, calidad
  OPUS/low). SEGURO.
- **B-H8 LIMPIO**: loudness no re-consulta red (resolved-first/BD local).
- **B-H9 LIMPIO**: caché audio media3 activo y agresivo (playerCache ilimitado
  default, tope opcional MaxSongCacheSizeKey). Canción cacheada no se re-descarga.
- **B-H10 REGLA 7 VERIFICADA**: nada muestrea red por fotograma mientras suena
  (PlaybackKeepAlive WakeLock/WifiLock sin poll; DownloadUtil poller 500ms solo BD).
  Excepción: B-H0 anuncios despierta radio periódicamente.
- **B-H11 BAJO**: crossfade con dos ExoPlayer (intrínseco, impacto bajo).
- **B-H12 PENDIENTE**: "video de fondo de artista" no localizado en main v2
  (debe vivir en ArtistScreen.kt 76KB o AuraArtistScreen.kt 68KB, ilegibles).
  La auditoría A-H3 SÍ lo verificó local → usar A-H3 como fuente.
- **B-H13 NOTA**: ruta 100% desautenticada (NewPipe setTokens tokens="" anónimo).
  Para datos es eficiente (Opus invitado ~50-160kbps). Ajuste calidad existe como
  pref SIN UI (029 PlayerSettings.kt:102-106). Comentario YTPlayerUtils ~:86-93
  "opus first, then aac, then vorbis" — la afirmación 029 de itag 141 delante del
  Opus NO coincide; PENDIENTE re-verificación línea por línea.

---

## Paquete de fixes recomendado (por retorno, combinando ambas)

1. Anuncios con ETag + cadencia real [B-H0, SEGURO].
2. Descargas solo WiFi por defecto [B-H1, SEGURO].
3. URL canónica por portada (2 buckets + migración DB) [A-H2, MEDIO, mayor ahorro estructural].
4. Artist video con caché disco + gate WiFi [A-H3, SEGURO-MEDIO, muy visible en datos].
5. Extender Data Saver a descargas/canvas/preload-0 [B-H7, SEGURO].
6. Canvas video tier bajo con datos móviles [B-H3, SEGURO].
7. Persistir letras a BD [B-H4, SEGURO] + metadatos canvas [A-H5, SEGURO].
8. Prewarm solo próximas K [A-H4, SEGURO] + fallback notificación [A-H6, SEGURO].
9. Dedup resolves en vuelo [B-H6, MEDIO, toca ruta crítica — con tests].
10. Verificar/activar circuit breaker extracción [B-H2, MEDIO].
11. Exponer ajuste calidad ya existente [B-H13, MEDIO, toca resolve].

**Ruta crítica de reproducción (INTOCABLE):** solo la tocan B-H6 (dedup resolves)
y B-H2/B-H13 (capa de extracción). Todo lo demás se implementa sin tocar el
camino resolve→CacheDataSource→player.

**Estado:** SOLO investigación. Gateadas al veredicto BETA-014. Informes completos
en `subagents/e6b2b00e-a19b-4981-84ec-e522704f4ea8/agent-general-purpose-call_b4a0880bd0ab4ffda514fbc6.jsonl`
(imágenes) y `agent-Explore-call_3a80a1a3540540b8a24249c6.jsonl` (audio/video).
Las 2 corridas "build-013-cache-tests*.txt" fueron verificación huérfana de la
sesión de auditoría (exit 0); git limpio, suite vigente 911/911.
