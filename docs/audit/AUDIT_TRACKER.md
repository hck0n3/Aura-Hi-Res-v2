# AUDIT_TRACKER — Auditoría de la zona que más falla

> Fuente de verdad reanudable. Reglas: `docs/audit/PROMPT_AUDITORIA_V2.md` (manda `AGENTS.md` y la skill `cirujano`).
> Para reanudar: leer `AGENTS.md`, `cirujano`, este archivo; `git status` + `git log -1`; seguir en el primer lote `[ ]`.

## Estado inicial (Fase 0, 2026-11-22)

- Rama: `claude/compassionate-gates-1dotj5` · HEAD base: `68fd908` · `git status`: limpio.
- Orden del dueño: «empieza con la auditoría de zonas que más fallan hasta que finalices». Se avanza de fase sin
  parar en cada una; **se para** en P0, `[DECISIÓN REQUERIDA]` y cambios de riesgo (se acumulan y se presentan juntos).
- Alcance: los 24 archivos con más menciones en `REGRESSION_REGISTRY.md` + `CRASH_REPORTS.md` + `PROBLEMAS.md`.
  `AxionEqScreen.kt` es la pantalla del ecualizador → **solo lectura e informe** (AGENTS regla 6).

## Línea base

| Comprobación | Resultado |
|---|---|
| CI `unit-tests.yml` en `68fd908` | ✅ success — run 44988866771 |
| Build/tests/lint locales | SDK instalado en `/home/user/android-sdk` (fuera del repo). 1er intento: FALLO de red (Maven Central 429), no de código; reintento en curso |
| Nota | `app/build.gradle.kts` usa `compileSdk = 37`; `AGENTS.md` dice platform 36 (doc desfasado, no se toca) |

## Reconciliación de `PROBLEMAS.md` (ítems de la zona)

| ID | Estado real en el código |
|---|---|
| P1 `songUrlCache`/bypass | YA ARREGLADO — `ConcurrentHashMap` / `synchronizedSet` (`MusicService.kt:1359`, `:1414`) |
| P2 listener secundario sin release | YA ARREGLADO — release + remove EQ (`MusicService.kt:341-347`) |
| P12 clave duplicada en grid | por verificar al leer `Thumbnail.kt` |
| P24 doble unbind | YA ARREGLADO — `runCatching { unbindService }` + comentario (`MainActivity.kt:397`, `:480`, `:496`) |
| P32 `videoUrlCache` | YA ARREGLADO — `ConcurrentHashMap` (`MusicService.kt:10698`) |
| P33 `dataStore.get` runBlocking | PARCIAL — existe espejo no bloqueante (`DataStore.kt:45`, `getNonBlocking`); se revisa al leer |
| P39 índice `event.timestamp` | YA ARREGLADO — `Index(["timestamp"])` (`Event.kt:27`) |
| P40 `validateStatus` sin cerrar | YA ARREGLADO — `.use {}` (`YTPlayerUtils.kt:1798`) |
| P45 accesos directos invertidos | YA ARREGLADO — mapeo correcto (`MainActivity.kt:953-954`, `:2970-2971`) |
| P46 `newImageLoader` runBlocking | YA ARREGLADO — espejo de proceso (`App.kt:2277`) |

## Flujos críticos (prueba manual del dueño)

Reproducción YouTube (sig/n) · cola infinita/radio · crossfade y precarga · EQ · descargas · letras · backup/restore ·
Room · licencia/demo · login YouTube/Spotify · actualizador · Listen Together · sincronización entre dispositivos ·
widget · Cast (Gms).

## Lotes (51 · 53.209 líneas)

- [x] L01 MusicService.kt L1-L1200
- [x] L02 MusicService.kt L1201-L2400
- [x] L03 MusicService.kt L2401-L3600
- [ ] L04 MusicService.kt L3601-L4800
- [ ] L05 MusicService.kt L4801-L6000
- [ ] L06 MusicService.kt L6001-L7200
- [ ] L07 MusicService.kt L7201-L8400
- [ ] L08 MusicService.kt L8401-L9600
- [ ] L09 MusicService.kt L9601-L11089
- [ ] L10 MainActivity.kt L1-L1200
- [ ] L11 MainActivity.kt L1201-L2400
- [ ] L12 MainActivity.kt L2401-L3443
- [ ] L13 ui/newui/AuraPlayer.kt L1-L1200
- [ ] L14 ui/newui/AuraPlayer.kt L1201-L2400
- [ ] L15 ui/newui/AuraPlayer.kt L2401-L3324
- [ ] L16 App.kt L1-L1200
- [ ] L17 App.kt L1201-L2559
- [ ] L18 ui/player/Thumbnail.kt L1-L1200
- [ ] L19 ui/player/Thumbnail.kt L1201-L1661
- [ ] L20 devicesync/DeviceSyncEngine.kt L1-L900
- [ ] L21 ui/player/Player.kt L1-L1200
- [ ] L22 ui/player/Player.kt L1201-L2400
- [ ] L23 ui/player/Player.kt L2401-L3785
- [ ] L24 ui/component/Lyrics.kt L1-L1200
- [ ] L25 ui/component/Lyrics.kt L1201-L2400
- [ ] L26 ui/component/Lyrics.kt L2401-L2746
- [ ] L27 utils/YTPlayerUtils.kt L1-L1200
- [ ] L28 utils/YTPlayerUtils.kt L1201-L1966
- [ ] L29 playback/ExactStyleGate.kt L1-L886
- [ ] L30 ui/newui/AuraShell.kt L1-L1200
- [ ] L31 ui/newui/AuraShell.kt L1201-L1518
- [ ] L32 ui/newui/AuraQueue.kt L1-L1200
- [ ] L33 ui/newui/AuraQueue.kt L1201-L1995
- [ ] L34 devicesync/RemotePlayback.kt L1-L306
- [ ] L35 ui/newui/AuraPalette.kt L1-L1192
- [ ] L36 utils/SyncUtils.kt L1-L1200
- [ ] L37 utils/SyncUtils.kt L1201-L2156
- [ ] L38 playback/queues/Queue.kt L1-L194
- [ ] L39 constants/PreferenceKeys.kt L1-L1354
- [ ] L40 db/DatabaseDao.kt L1-L1200
- [ ] L41 db/DatabaseDao.kt L1201-L2618
- [ ] L42 ui/newui/AuraContent.kt L1-L1200
- [ ] L43 ui/newui/AuraContent.kt L1201-L1859
- [ ] L44 ui/newui/AuraBloom.kt L1-L692 + utils/DataStore.kt L1-L182 (DataStore.kt L1-L183 ya leído)
- [ ] L45 ui/newui/AuraLibraryTabs.kt L1-L1200
- [ ] L46 ui/newui/AuraLibraryTabs.kt L1201-L1767
- [ ] L47 ui/newui/AuraHomeScreen.kt L1-L1200
- [ ] L48 ui/newui/AuraHomeScreen.kt L1201-L2309
- [ ] L49 ui/screens/equalizer/axion/AxionEqScreen.kt L1-L1200 (solo lectura)
- [ ] L50 ui/screens/equalizer/axion/AxionEqScreen.kt L1201-L2400 (solo lectura)
- [ ] L51 ui/screens/equalizer/axion/AxionEqScreen.kt L2401-L2708 (solo lectura)

Rutas relativas a `app/src/main/kotlin/com/music/echo/`.

## Ledger de hallazgos

| ID | Lente | Sev. | Marca | Archivo:Línea | Resumen | Estado | Commit |
|---|---|---|---|---|---|---|---|
| F-001 | PERF | P2 | NUEVO (fila #180 parcial) | `utils/DataStore.kt:85`, `:156` | `getNonBlocking`/`rememberEnumPreference` caen a `runBlocking` para TODA clave ausente del snapshot = todo ajuste nunca modificado (defaults). El snapshot es reemplazo completo, así que una clave en disco siempre está en él: el fallback solo bloquea los defaults. El test guardián `PrefsBridgeTest` caso 5 fija el comportamiento actual | [DECISIÓN REQUERIDA] | — |

## Conexiones pendientes

## Sospechas (sin escenario completo, sin acción)

- S-001 `MusicService.kt:2499-2527` colector de versión de IP: su 1ª emisión no se salta si el colector de calidad emitió antes (`isFirstQualityEmit` compartido). Si la cola restaurada ya tuviera items, haría `stop()+prepare()` sobre la cola restaurada sin preparar (#27). Improbable por tiempos (la restauración pasa por IO); sin escenario reproducible.
- S-002 `MusicService.kt:589,1566,3795,3837` el scope revivido es `Job()` y no `SupervisorJob()` como el original (L354). Solo se cancela en `onDestroy` (L10406): revivir ocurre sobre un servicio ya destruido. Impacto real no demostrado.
- S-003 `MusicService.kt:3527-3531` `triggerRetry` dice «refresh stream URL» con `seekTo` a la misma posición; no se ve que invalide `songUrlCache`. Posible placebo; pendiente leer StreamResolution.
- Obs (P3) `MusicService.kt:3197`, `:3217` `dataStore.get(PersistentQueueKey)` bloqueante cada 10/30 s en Main aunque existe el espejo `persistentQueueHint` (L959).

## [DECISIÓN REQUERIDA] abiertas

- F-001: ¿cambiar `getNonBlocking` a `if (PrefsBridge.isReady) peek(key) ?: default else get(key, default)` (y la semilla de `rememberEnumPreference`) y actualizar el caso 5 de `PrefsBridgeTest`? Riesgo: una clave recién escrita por primera vez puede leerse como default durante los ms que tarda el colector en publicar (el mismo desfase que ya tienen hoy las claves presentes).
