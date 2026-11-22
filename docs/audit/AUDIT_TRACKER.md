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
| Build/tests/lint locales | NO VIABLE en este contenedor: 2 intentos fallan resolviendo plugins de Gradle (Maven Central 429), igual que la fila #250 del registro. Verificación = CI `unit-tests.yml` |
| Nota | `app/build.gradle.kts` usa `compileSdk = 37`; `AGENTS.md` dice platform 36 (doc desfasado, no se toca) |

## Reconciliación de `PROBLEMAS.md` (ítems de la zona)

| ID | Estado real en el código |
|---|---|
| P1 `songUrlCache`/bypass | YA ARREGLADO — `ConcurrentHashMap` / `synchronizedSet` (`MusicService.kt:1359`, `:1414`) |
| P2 listener secundario sin release | YA ARREGLADO — release + remove EQ (`MusicService.kt:341-347`) |
| P12 clave duplicada en grid | YA ARREGLADO — `thumbnailItemKey(index, item)` (`Thumbnail.kt:333`) |
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
- [x] L04 MusicService.kt L3601-L4800
- [x] L05 MusicService.kt L4801-L6000
- [x] L06 MusicService.kt L6001-L7200
- [x] L07 MusicService.kt L7201-L8400
- [x] L08 MusicService.kt L8401-L9600
- [x] L09 MusicService.kt L9601-L11089
- [x] L10 MainActivity.kt L1-L1200
- [x] L11 MainActivity.kt L1201-L2400
- [x] L12 MainActivity.kt L2401-L3443
- [x] L13 ui/newui/AuraPlayer.kt L1-L1200
- [x] L14 ui/newui/AuraPlayer.kt L1201-L2400
- [x] L15 ui/newui/AuraPlayer.kt L2401-L3324
- [x] L16 App.kt L1-L1200
- [x] L17 App.kt L1201-L2559
- [x] L18 ui/player/Thumbnail.kt L1-L1200
- [x] L19 ui/player/Thumbnail.kt L1201-L1661
- [x] L20 devicesync/DeviceSyncEngine.kt L1-L900
- [x] L21 ui/player/Player.kt L1-L1200 — INALCANZABLE (ver D-002); no se audita a fondo código que no se ejecuta
- [x] L22 ui/player/Player.kt L1201-L2400
- [x] L23 ui/player/Player.kt L2401-L3785 — parte viva `InlineLyricsView` L3499-L3580 leída línea a línea (sin fallos); L3583-L3785 leída (código muerto)
- [x] L24 ui/component/Lyrics.kt L1-L1200
- [x] L25 ui/component/Lyrics.kt L1201-L2400
- [x] L26 ui/component/Lyrics.kt L2401-L2746 — sin hallazgos P0-P2; ver obs. de Lyrics en Sospechas
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
| F-007 | BUG | P2 | NUEVO | `devicesync/DeviceSyncEngine.kt:377-388` | `attachPlayerListener` engancha `onPlayWhenReadyChanged` UNA vez al `ExoPlayer` de ese momento (`playerListenerAttached = true`). El crossfade (activo por defecto) sustituye la instancia en cada canción (`CrossfadeEngine.kt:1085` `player = nextPlayer`; también el cambio instantáneo a vídeo, `MusicService.kt:8978`). Desde el primer crossfade el listener queda en un reproductor liberado: la reacción «empecé a sonar aquí → pausa el otro» (fila #594) pasa de inmediata a esperar el tick de 4 s, así que los dos dispositivos pueden sonar a la vez hasta 4 s; y el listener viejo nunca se quita | Plan pendiente (callbacks del reproductor = cambio de riesgo) | — |
| F-006 | BUG | P1 | REGRESIÓN de la fila #345(c) | `ui/newui/AuraPlayer.kt:629-633` | El arreglo #345(c) («en una colección el automix crudo de YouTube no se añade») vive solo en el reproductor clásico (`Player.kt:559`, guarda `!automixDefersToContinuation()`). La interfaz nueva es hoy la única (`NewUiGate.kt:27`, `SINGLE_UI`), y su efecto equivalente no tiene la guarda: al acabar un álbum o playlist sigue entrando el automix antes que el motor de continuación. Patrón «copiar a medias» (registro, regla 3) | Para Fase 2 (1 línea, no es cambio de riesgo) | — |
| F-005 | BUG/SEC | P1 | NUEVO (HALLAZGO-014 aplicado a medias) | `MainActivity.kt:3211`, `:3214` (y `navigateBrowseId` L3133-3146) | La actividad está exportada y acepta `music.youtube.com/browse/*` y `/channel/*` (manifiesto L110, L118). `lastPathSegment` llega DECODIFICADO, así que `/browse/MPREb%2Fx` da `navigate("album/MPREb/x")` → `IllegalArgumentException` (ninguna ruta casa) sin `runCatching` → cierre de la app desde un enlace. El validador `isRouteSafeId` (L3175) solo se aplica a la rama `playlist` (L3194). Arreglo mínimo: `?.takeIf(::isRouteSafeId)` en las dos ramas | Para Fase 2 (no es cambio de riesgo) | — |
| F-002 | SEC | P1 | NUEVO (choca con AGENTS regla 4) | `MusicService.kt:1906, 6129, 6912, 7525, 7949, 8025, 8066, 8113, 8126, 8170, 10481, 10528, 10532, 10537, 11033` | Logs INFO/WARN con el videoId (`mediaId`), el contexto `PL:<id>` o el id de playlist del widget. AppLogger persiste ≥INFO en `app.log`, que el usuario comparte; `LogRedaction` no los quita (sus comentarios los consideran «identificadores de diagnóstico»), pero AGENTS regla 4 y otros logs del mismo archivo («no title, artist or id», L1829/L5003) los prohíben. El dueño ya diagnosticó con estos ids (fila #305) | [DECISIÓN REQUERIDA] | — |
| F-003 | SEC | P2 | NUEVO | `MusicService.kt:8410-8415` | En un fetch de audio fallido se loguea a WARN `body=$peek` (160 primeros caracteres del cuerpo de la respuesta). El propio comentario (L8405-8406) dice «NEVER the query string or body contents (owner data rule)» y AGENTS regla 4 prohíbe cuerpos de respuesta | Plan pendiente (archivo compartido) | — |
| F-004 | CON | P2 | NUEVO | `MusicService.kt:10295` | `isPowerSaveModeActive()` privada sin ninguna llamada (grep en todo `app/src`); la misma comprobación se hace en línea en L6828, L8783, L10905 | Plan pendiente (archivo compartido) | — |

## Conexiones pendientes

## Sospechas (sin escenario completo, sin acción)

- S-001 `MusicService.kt:2499-2527` colector de versión de IP: su 1ª emisión no se salta si el colector de calidad emitió antes (`isFirstQualityEmit` compartido). Si la cola restaurada ya tuviera items, haría `stop()+prepare()` sobre la cola restaurada sin preparar (#27). Improbable por tiempos (la restauración pasa por IO); sin escenario reproducible.
- S-002 `MusicService.kt:589,1566,3795,3837` el scope revivido es `Job()` y no `SupervisorJob()` como el original (L354). Solo se cancela en `onDestroy` (L10406): revivir ocurre sobre un servicio ya destruido. Impacto real no demostrado.
- S-003 `MusicService.kt:3527-3531` `triggerRetry` dice «refresh stream URL» con `seekTo` a la misma posición; no se ve que invalide `songUrlCache`. Posible placebo; pendiente leer StreamResolution.
- Obs (P2 batería, sin medir) `DeviceSyncEngine.kt:695-706` mientras el dispositivo está emparejado, cada 4 s: dos saltos a Main, un JSON de hasta 66 elementos y `pushShuffle()` con dos consultas a Room (`enhancedPlayedSince` + `allEnhancedContexts` completa), también en pausa o con la pantalla apagada. Diseño de la fila #528; medirlo antes de tocarlo.
- Obs (P3) `App.kt:2136-2141` el encabezado de diagnóstico lee `crossfadeEnabled ?: true` y `safeVolume ?: false`, al revés que los valores por defecto reales (`MusicService.kt:2981` `?: false`, `:2736` `?: true`). Misma clase que la fila #585; hoy las migraciones escriben ambas claves, así que casi nunca se nota.
- Obs (P3) `AuraPlayer.kt:1437` el toque en el título navega con `navigate` y no con `navigateOnce` como el resto de navegaciones del reproductor (L1343-1346): puede apilar dos veces la misma ficha de álbum.
- Obs (diseño) `MainActivity.kt:3148-3156` un enlace `echomusic://listen/<código>` o `https://…onrender.com/listen/<código>` une a una sala de Escuchar juntos sin confirmación. Es la función de invitación; se anota por si el dueño quiere un diálogo de confirmación.
- Obs (P3) `MainActivity.kt:1616-1625` `deferred.await()` sin tope: si `checkForUpdate` nunca llama a ningún callback, el efecto queda suspendido (sin impacto visible).
- Obs (P3) `MusicService.kt:10233` `withCancellationSafe` relanza el bloque al cancelarse (doble efecto); hoy solo envuelve `job?.cancel()` (idempotente) dentro de un `runBlocking` inútil en `onDestroy` (L10371). Sin impacto actual.
- Obs (P3) `MusicService.kt:10413` `onTaskRemoved` solo llama a `super`.
- Obs (P3) `MusicService.kt:1812` `dataStore.get` bloqueante en el callback de dispositivo de audio (hilo principal), solo al conectar un dispositivo.
- Obs (P3) `MusicService.kt:3197`, `:3217` `dataStore.get(PersistentQueueKey)` bloqueante cada 10/30 s en Main aunque existe el espejo `persistentQueueHint` (L959).

- Obs (CON, registro desfasado) `Lyrics.kt:2041` APPLE_V2 sin timings por palabra pinta la línea entera (`!hasRealWordTimings -> wholeLineFill`); la fila #7 del registro dice lo contrario («fabricar timings char-weighted en el else»). El cambio es deliberado (comentario L2030-2040: el barrido fabricado iba «letra por letra desincronizado»), entró en `8b44a43` y ninguna fila lo recoge. No se toca el código: propuesta para Fase 4 = actualizar el guardián de la fila #7 (el dueño confirma qué comportamiento quiere).
- Obs (P3) `Lyrics.kt:2121-2132` con «brillo» activo, la línea actual corre un bucle de pulso infinito (recomposición por fotograma) mientras la pantalla de letra está visible. Solo con la letra en pantalla; no con la pantalla apagada. Sin medir.
- Obs (P3) `Lyrics.kt:2512` cada apertura del selector de colores crea un `ImageLoader(context)` nuevo (sin caché compartida) para sacar la paleta de la portada.

## [DECISIÓN REQUERIDA] abiertas

- D-002: `ui/player/Player.kt` — `BottomSheetPlayer` (L282-~L3236, ~2.950 líneas) y `MoreActionsButton` (L3584, 0 referencias) son inalcanzables: `SINGLE_UI = true` es constante (`NewUiAvailability.kt:44`) y `NewUiGate` siempre elige `AuraPlayer` (`NewUiHosts.kt:79-95`). Solo `InlineLyricsView` (L3499-L3580) sigue vivo. `UI_INVENTORY.md` aún lista elementos de ese reproductor (§ L418-L422) y el registro lo cita 48 veces. ¿Se conserva como respaldo (por si algún día `SINGLE_UI` vuelve a `false`) o se borra en su propio commit, moviendo `InlineLyricsView` a otro archivo?

- F-001: ¿cambiar `getNonBlocking` a `if (PrefsBridge.isReady) peek(key) ?: default else get(key, default)` (y la semilla de `rememberEnumPreference`) y actualizar el caso 5 de `PrefsBridgeTest`? Riesgo: una clave recién escrita por primera vez puede leerse como default durante los ms que tarda el colector en publicar (el mismo desfase que ya tienen hoy las claves presentes).
