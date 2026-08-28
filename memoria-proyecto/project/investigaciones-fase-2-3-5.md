---
name: Investigaciones FASE 2/3/5 recibidas (055 jank, 028 login, 035 export video)
description: Estado 2026-08-27 — Tres investigaciones read-only completas devueltas por agentes: HALLAZGO-055 (jank global BETA-012), HALLAZGO-028 (login/biblioteca lenta), HALLAZGO-035 (export video no funciona). Causas raíz confirmadas + plan de fix + riesgos. GATEADAS al veredicto BETA-014; NO implementar hasta luz verde del dueño.
type: project
---

Tres investigaciones read-only (agentes de fondo, 2026-08-27) devolvieron
causas raíz confirmadas para tres frentes de la RONDA 3. Son material de
FASE 2 (055), FASE 3 (028) y FASE 5 (035). **NO se implementa nada todavía**:
están gateadas al veredicto del dueño sobre BETA-014 (faders 054 + portadas
video 057). Esto es el resumen durable para no re-investigar.

**Por qué:** el dueño reportó jank global, login con ritual y biblioteca lenta,
y export de video roto. Cada investigación costó ~150k tokens; perderlas
obligaría a repetirlas.
**Cómo aplicar:** cuando el dueño dé luz verde a la fase correspondiente,
arrancar desde el plan de fix de abajo (ya rankeado por riesgo), leyendo en
local los archivos marcados "ilegibles por tamaño" antes de editar.

---

## HALLAZGO-055 — jank global en BETA-012 (v2.0.8/vc963)

Ventana auditada 84647c0..HEAD (13 commits, BETA-008→012). `app/build.gradle.kts`
solo cambia versionCode/versionName → descartada regresión de dependencia.
`AuraPlayer.kt`/`Player.kt` NO están en la ventana (sospechosos LIVE_MESH/blur
fullscreen de FASE 2 quedan fuera del delta). **Conclusión central: ningún
escritor por-fotograma NUEVO en main thread; el único costo por-frame nuevo
es GPU (overdraw del bloom 049).**

Candidatos rankeados:
1. **Overdraw GPU bloom (049)** — `AxionEqScreen.kt:183-196`
   (`.auraScreenBackground(bloom, intensity=0.45f)`), `AutoEqScreen.kt:153-165`
   (0.40f), + AuraSearch/Stats/Migration. Impl `AuraBloom.kt:332-361`. Pinta 3
   gradientes radiales fullscreen por frame (durante dissolve 1s son 6). Fill-rate
   nuevo y permanente justo en pantallas donde el dueño prueba (EQ/AutoEq).
   FIX: renderizar lóbulos a Bitmap cacheado por track (1 blit vs 3 gradientes)
   o gatear por highPerf/tier.
2. **Churn de paleta por track (049 GAP-4)** — `MainActivity.kt:1606-1616`
   (`if (newUiShell) AuraPaletteSync()` en root), `AuraPalette.kt:561-626/137-149`.
   Por cambio de track recompone todo chrome que lee acento + dissolve 1s. Hitch
   puntual por canción, no jank continuo. FIX: mover `apply()` a LaunchedEffect.
3. **Loop reintento Visualizer (048)** — `AxionEqScreen.kt:435-445` watchdog,
   `AuraVisualizerHub.kt:137-145` throttle 10s. ≤1 attach fallido/10s en
   Dispatchers.IO → NO es jank directo; contribuidor TÉRMICO indirecto (impide
   deep-idle → calor → throttling). FIX: tope de intentos + backoff + log único.
4. **Snapshot FFT por frame** — PRE-VENTANA (no es el delta). `AxionEqScreen.kt:449-469`.
   La base era PEOR. Optimización opcional: short-circuit a `EqFftSnapshot.Zero`.

Descartados con evidencia: barrera 054, nestedScroll 051/052, WindowBlur,
FrostFill fallback, AuraRhythm, extractAuraBloom (todo event-driven o en IO).
El dispositivo del dueño tiene framework de audio roto para Visualizer (error -3)
y window blur deshabilitado (One UI 8.5/Android 16).
Validación sugerida con el dueño: ¿jank en todas las pantallas o peor en EQ?;
¿empeora al cambiar canción (#2) o al scrollear (#1)?; ¿inmediato o tras 10+ min (#3)?

## HALLAZGO-028 — login con ritual + biblioteca lenta (FASE 3)

NOTA: fork local SimpMusic inaccesible esa sesión; comparación contra upstream
público `maxrave-dev/SimpMusic` (dev) + core @ 7fa18125. Números de línea estimados.

Cadena causal del ritual "Accede"+atrás (verificada + log BETA-012):
1. Redirect aterriza en music.youtube.com → onPageFinished detecta SAPISID.
2. `account_menu` devuelve HTTP 500 backendError (log 07:35:51-53) — transitorio
   (propagación de sesión Google), NO auth malformada (eso sería 401/403).
3. `withRetry(maxAttempts=3)` solo reintenta IOException → 5xx NO reintenta.
4. onFailure → no persiste nada, `hasCompletedLogin=false` → usuario atrapado.
5. Al pulsar "Accede" otra vez → nuevo onPageFinished → 2º intento con sesión ya
   propagada → éxito → persiste + 4 syncs fire-and-forget + navigateUp (su "atrás").

Cuellos de botella por impacto:
1. Syncs fire-and-forget + UI vacía sin feedback (`LoginScreen ~70-81`
   `finishLoginInPlace` no-suspend; `LibraryArtistsScreen` EmptyPlaceholder sin
   indicador). Percepción: biblioteca vacía decenas de s a minutos.
2. Paginación completa en primer login: `completed()` hasta 50 requests/endpoint.
3. N+1 por artista: `getChannelId()` + account-live por cada no-local
   (`ArtistSyncPolicy.kt`); liga con HALLAZGO-056 (tormenta).
4. Validación account_menu sin retry 5xx + delay(500) corto.
5. Sync por pantalla en LaunchedEffect (duplicación).

Por qué SimpMusic se siente instantáneo: no hay espejo local; trigger es cambio
de cookie (`dataStoreManager.cookie.distinctUntilChanged().collect`); biblioteca
= llamadas vivas por sección con estado Loading. Fix de Aura debe conservar el
espejo (base de backup/AffinityEngine) pero hacerlo sentir instantáneo.

Plan de fix (riesgo): A1 extender withRetry a 5xx (SEGURO) → A2 delay 500→1500ms
(SEGURO) → B1 feedback "Sincronizando tu biblioteca…" (SEGURO) → B2 fast-path
primera página (MEDIO, toca código compartido) → E debounce sync por pantalla
(SEGURO) → C cap paginación (MEDIO) → D diferir getChannelId (ARRIESGADO,
coordinar con 056). Prioridad: A1+A2 → B1 → B2 → E → C → D.
Pendiente: cuerpo SyncUtils.kt (browseIds + patrón inserción), cuerpo ytClient()
(fórmula SAPISIDHASH).

## HALLAZGO-035 — export de video no funciona (FASE 5)

CAUSA RAÍZ CONFIRMADA: la exportación de video (sonda del chooser, servicio de
export y descarga offline) resuelve el stream SOLO por InnerTube directo con
clientes familia TVHTML5 (`videoStreamUrlDiag`/`videoStreamUrl`), QUEMADOS por
YouTube; la reproducción in-app usa `adaptiveVideoStreamNewPipe` (NewPipe vía
ANDROID_VR/ANDROID, viva), que la exportación NUNCA llama.

Evidencia (puntos verificados textualmente):
- `ExportFormatChooser.kt:~82` — sonda solo con `videoStreamUrlDiag`.
- `DownloadUtil.kt:~207` — fallback `videoStreamUrlDiag`→`videoStreamUrl`, sin NewPipe.
- `AudioExportService.kt:~328` — cita FASE 0 (archivo ilegible por tamaño).
- TVHTML5 quemado dicho por el dueño en commits 69d1ce1e/066722e1. Export
  congelado en 0.6.213 (5effd1a5, 08-16), ANTES del colapso 08-23 y reconstrucción
  NewPipe. MP3 sí funciona porque usa cascada de audio restaurada + NewPipe.

Fix (riesgo MEDIO): crear resolvedor compartido en YTPlayerUtils junto a
`adaptiveVideoStreamNewPipe` (`exportVideoStreams`) que llame
`NewPipeExtractor.newPipePlayer` (→ fallback BraveNewPipe) y seleccione
video-only 136/137 + audio 251/774, o muxed 22/18; fallback final
`videoStreamUrlDiag` (no borrar). Aplicar en los 3 puntos (sonda, servicio,
DownloadUtil conservando puente `videoUrlCache` primero). Mux ffmpeg maneja
dual-input `-c copy` y single-input. Subir timeout sonda 20s→30-35s. Logs siguen
patrón RESOLVE_CIPHER (solo videoId+itag, nunca URL). NO toca licencia/eq/ffmpeg-kit.
Characterization tests ANTES: contrato diag, resolvedor nuevo, builder ffmpeg,
sonda chooser, integridad archivo, + verificación manual en dispositivo (regla:
ruta de streaming solo se asume limpia con reproducción/export real).

De HALLAZGO-036 en el camino: `LocalMediaIntents.kt` `deleteExportedVideo()`
devuelve true siempre aunque falle el borrado físico (KDoc lo admite). La "opción
sin lógica" del export video es consecuencia directa de 035 (sonda falla siempre).
Pendiente local: AuraDownloadsScreen.kt:127-294 y SongMenu.kt:405-497,541-1084.

---

**Estado:** las tres son SOLO investigación (cero cambios de código). Gateadas al
veredicto BETA-014. Informes completos de los agentes en
`subagents/e6b2b00e-a19b-4981-84ec-e522704f4ea8/agent-general-purpose-call_*.jsonl`.
