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
- [x] L27 utils/YTPlayerUtils.kt L1-L1200
- [x] L28 utils/YTPlayerUtils.kt L1201-L1966 — sin hallazgos P0-P2 confirmados; S-004 y obs. abajo
- [x] L29 playback/ExactStyleGate.kt L1-L886 — F-008
- [x] L30 ui/newui/AuraShell.kt L1-L1200
- [x] L31 ui/newui/AuraShell.kt L1201-L1518 — sin hallazgos (descartado: el `pointerInput(Unit)` del deslizar lee `canSkipNext` por su delegado `by`, no queda congelado)
- [x] L32 ui/newui/AuraQueue.kt L1-L1200
- [x] L33 ui/newui/AuraQueue.kt L1201-L1995 — sin hallazgos P0-P2
- [x] L34 devicesync/RemotePlayback.kt L1-L306 — sin hallazgos propios (su `shouldPauseOther` depende del listener de F-007)
- [x] L35 ui/newui/AuraPalette.kt L1-L1192 — sin hallazgos confirmados; S-005
- [x] L36 utils/SyncUtils.kt L1-L1200
- [x] L37 utils/SyncUtils.kt L1201-L2156 — sin hallazgos P0-P2 confirmados; S-006
- [x] L38 playback/queues/Queue.kt L1-L194 — sin hallazgos P0-P2
- [x] L39 constants/PreferenceKeys.kt L1-L1354 — revisado con script (no lectura línea a línea: son 393 declaraciones): ningún nombre de clave repetido en todo `app/src`; 5 claves con valores por defecto distintos entre lectores, 1 con efecto (obs. abajo)
- [x] L40 db/DatabaseDao.kt L1-L1200
- [x] L41 db/DatabaseDao.kt L1201-L2618 — lectura dirigida, no línea a línea (son ~600 consultas): todas las sentencias UPDATE/DELETE, todos los `IN (:lista)` y quién los llama sin trocear, y los caminos de escritura L1900-L2260. F-009, F-010
- [x] L42 ui/newui/AuraContent.kt L1-L1200
- [x] L43 ui/newui/AuraContent.kt L1201-L1859 — leído L90-L730 y L1040-L1100; el resto (componentes de presentación) por búsqueda dirigida: escrituras, colas, gestos de deslizar (L1612-L1690 leído). Sin hallazgos P0-P2
- [x] L44 ui/newui/AuraBloom.kt L1-L692 + utils/DataStore.kt (ya leído) — sin hallazgos (caché acotada a 32, extracción deduplicada con `claim`/`release`, deriva ambiental con puerta térmica en `AuraAmbientMotion.kt:72-77`)
- [x] L45 ui/newui/AuraLibraryTabs.kt L1-L1200
- [x] L46 ui/newui/AuraLibraryTabs.kt L1201-L1767 — leído L380-L560, L915-L945, L1230-L1260, L1385-L1400; el resto por búsqueda dirigida (efectos, BD, navegación, colas). F-011
- [x] L47 ui/newui/AuraHomeScreen.kt L1-L1200
- [x] L48 ui/newui/AuraHomeScreen.kt L1201-L2309 — leído L170-L455; el resto (estanterías) por búsqueda dirigida: efectos, BD, colas, claves de la lista (por índice, sin claves repetidas). Sin hallazgos P0-P2
- [x] L49 ui/screens/equalizer/axion/AxionEqScreen.kt L1-L1200 (solo lectura)
- [x] L50 ui/screens/equalizer/axion/AxionEqScreen.kt L1201-L2400 (solo lectura)
- [x] L51 ui/screens/equalizer/axion/AxionEqScreen.kt L2401-L2708 (solo lectura) — zona del ecualizador: búsqueda dirigida (bucles, efectos, listeners, `!!`), leído L500-L600, L735-L760, L2395-L2440. Nada se toca (AGENTS regla 6); solo observaciones

Rutas relativas a `app/src/main/kotlin/com/music/echo/`.

## Fase 1 terminada (51/51 lotes)

## Ledger de hallazgos

| ID | Lente | Sev. | Marca | Archivo:Línea | Resumen | Estado | Commit |
|---|---|---|---|---|---|---|---|
| F-001 | PERF | P2 | NUEVO (fila #180 parcial) | `utils/DataStore.kt:85`, `:156` | `getNonBlocking`/`rememberEnumPreference` caen a `runBlocking` para TODA clave ausente del snapshot = todo ajuste nunca modificado (defaults). El snapshot es reemplazo completo, así que una clave en disco siempre está en él: el fallback solo bloquea los defaults. El test guardián `PrefsBridgeTest` caso 5 fija el comportamiento actual | [DECISIÓN REQUERIDA] | — |
| F-009 | BUG | P1 | NUEVO | `viewmodels/CachePlaylistViewModel.kt:103` → `db/DatabaseDao.kt:662` | «En caché» pide `getSongsByIds(pureCacheIds.toList())` con TODOS los ids de la caché de escucha de una vez (`WHERE id IN (:songIds)`, sin trocear; `SyncUtils` y `LocalSongScanner` sí trocean a 900). El límite de caché por defecto es ilimitado (`App.kt:2395` `DEFAULT_SONG_CACHE_SIZE_MB = -1`), así que tras unas semanas pasa de 999 canciones. En Android 8–11 (minSdk 26; SQLite < 3.32 limita a 999 variables) la consulta lanza «too many SQL variables» dentro de `viewModelScope.launch(Dispatchers.IO)` sin `try` (L70-156): cierre de la app al abrir «En caché» (`CachePlaylistScreen.kt:116`, `AuraCollectionScreens.kt:1170`) o la búsqueda sin conexión (`AuraOfflineSearch.kt:113-114`), y otra vez en cada intento. Arreglo mínimo: `chunked(900)` como en `SyncUtils.kt:434` | Para Fase 2 (1 archivo fuera de la lista compartida; no es cambio de riesgo). Sin probar en un dispositivo con Android ≤11 [NO VERIFICADO el límite exacto del SQLite del fabricante] | — |
| F-010 | BUG | P2 | CONOCIDO (código heredado del fork) | `db/DatabaseDao.kt:2199-2201` | Al refrescar un álbum (`AlbumViewModel.kt:162`, `:122`, `AlbumMenu.kt:635`, `LibraryViewModels.kt:293`, `:447`, `StatsViewModel.kt:240`), si el número de artistas guardados y el de la página difieren se BORRAN las filas `ArtistEntity` de esos artistas (`artists?.forEach(::delete)`), no sus vínculos con el álbum. Por `onDelete = CASCADE` (`SongArtistMap.kt:17-23`, `AlbumArtistMap.kt:17-23`) se van todos los vínculos canción↔artista de ese artista en TODA la biblioteca, y con la fila se pierden su seguimiento (`bookmarkedAt`, `followedByUserAt`), su foto y su `channelId`; L2228 lo reinserta vacío. Los vínculos del álbum ya se rehacen en L2220-2235, así que borrar esas tres líneas basta | Para Fase 2 (1 archivo, 3 líneas). Mecanismo leído; frecuencia real sin medir | — |
| F-011 | PERF | P2 | NUEVO (puerta de la fila #214 por la que se cuela) | `ui/newui/AuraLibraryTabs.kt:934-941`, `:1256-1258`, `:1392-1394` | Las pestañas Canciones, Álbumes y Artistas de la Biblioteca nueva lanzan en CADA entrada (`LaunchedEffect(Unit)`, con «sincronizar con la cuenta» activo) una pasada completa: `syncLikedSongs`/`syncLibrarySongs` (hasta 50 páginas + la reconciliación, que puede reenviar hasta 25 «me gusta»), `syncLikedAlbums` y `syncArtistsSubscriptions` (+ hasta 250 fotos de artista). No pasan por `tryAutoSync`, así que ignoran la cadencia que eligió el usuario, también «nunca». La fila #214 arregló eso para el arranque y el centro de la Biblioteca (`:429` sí usa `tryAutoSync`); las pestañas son otra puerta. Heredado de las pantallas clásicas (`LibrarySongsScreen.kt:124` y hermanas). Regla 7 (batería y red) | [DECISIÓN REQUERIDA] | — |
| F-008 | SEC/CON | P2 | NUEVO | `playback/ExactStyleGate.kt:612-636` → `edge/EdgeClient.kt:375-376`; `PRIVACY_POLICY.md:27-33`, `:200-202` | La segunda opinión de la IA (fila #606) manda al Worker `/v1/judge` los títulos y artistas de lo que suena y de los candidatos dudosos en cada continuación de la cola inteligente, y está ACTIVA POR DEFECTO (`EdgeEnabledKey ?: true`, `QueueAiJudgeKey` solo se apaga con `false`, ajuste con default `true` en `AuraServicesSettings.kt:76`). La política de privacidad (último cambio 2026-10-18, antes de `1312a0f` del 2026-10-31) dice que el servidor solo recibe licencia, prompts de IA «only if you use the AI playlist feature» y reconocimiento, que «We do not receive … your listening history» y que la IA está «off until you enable them». El dueño aceptó el envío (fila #606), pero la política publicada no lo cuenta | [DECISIÓN REQUERIDA] | — |
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

- S-004 `YTPlayerUtils.kt:1929-1934` si NewPipe no trae el itag elegido, `findUrlOrNull` devuelve la URL de OTRO itag (`AUDIO_ITAG_PREFERENCE`) pero `PlaybackData.format` sigue siendo el elegido. Consumidores que leen `format`: el registro de formato en Room y la calidad entregada (`StreamResolution.kt:912-943`, `MusicService.kt:11014-11022`) y la exportación, que corta el audio con `&range=0-${format.contentLength}` (`AudioExportService.kt:430`, `:682`). Si el itag servido es más grande, la exportación saldría truncada y la ficha mostraría un códec que no suena. Requiere que NewPipe e InnerTube discrepen en los itags; no se ha visto en un log [NO VERIFICADO].
- Obs (P3, placebo menor) `YTPlayerUtils.kt:1963` `forceRefreshForVideo` no hace nada (solo un log de depuración), pero `PlaybackErrorRecovery.kt:285-286` lo llama y loguea «Cleared decryption caches». La URL sí se invalida aparte (`PlaybackErrorRecovery.kt:443` `songUrlCache.remove`), así que no rompe la recuperación; el comentario engaña.
- Obs (P3) `YTPlayerUtils.kt:136-137` `hasShownLosslessToast`/`hasShownSaavnToast` y `:493` `showFallbackToast` (lee el DataStore en cada resolución) sin ningún uso tras retirar Qobuz/Saavn.
- Obs (privacidad, va con F-002/F-003) `YTPlayerUtils.kt:131` `RESOLVE_FAIL … ${error.message?.take(180)}` a WARN: con `expectSuccess = true` (`innertube/NetworkConfig.kt:34`) el mensaje de un `ClientRequestException` de Ktor es `Client request(POST <url>) invalid: 400 … Text: "<cuerpo>"`, así que entran la URL de `/player` y el principio del JSON de error de YouTube. No lleva datos del usuario, pero es «cuerpo de respuesta» según la regla 4. Además los logs INFO/WARN de este archivo llevan el videoId (L131, L434, L634, L639, L1026, L1264, L1283, L1311, L1498, L1875): misma decisión que F-002.
- Obs (P3) `YTPlayerUtils.kt:1820` `reportException` en cada fallo de la validación HEAD, también por red caída (en `getSignatureTimestampOrNull` ya se excluyen los fallos de red, L1881). Solo afecta al sabor GMS (Crashlytics).

- Obs (P3, cosmético) `AuraShell.kt:459-464` el rango del motor «líquido» de la barra es `0..items.size-1`, pero la celda Ajustes tiene índice `items.size`: `animateToValue` lo recorta (`DampedDragAnimation.kt:145`), así que ir a Ajustes desde la última pestaña no deforma la pastilla. Solo con cristal interactivo.

- Obs (P3) `AuraQueue.kt:1605` la clave de cada fila es `window.uid.hashCode()`: dos uids distintos con el mismo hash darían una clave repetida en la `LazyColumn`, que en Compose es un cierre. Con hashes de identidad la probabilidad es del orden de n²/2³² (≈0,02 % con 1.000 canciones en cola). Usar el `uid` mismo (o `firstPeriodIndex`+uid) lo evitaría; sin caso observado.

- Obs (P3, i18n) `RemotePlayback.kt:189` el aviso «… no responde: puede tener la pantalla bloqueada…» está escrito en español en el código, no en `strings.xml`; la app trae más de 40 idiomas (`res/values-*`). Mismo patrón en otros `DeviceSyncHub.notice("…")` del módulo.

- S-005 `AuraPalette.kt:854-999` `AuraPaletteSync` se compone en varios sitios a la vez (puertas de pantalla, barra, minirreproductor) y cada instancia guarda su propio `heldEntry` (L911). `apply` escribe estado global DURANTE la composición y lee ese mismo estado (`accent != next`, L198), así que cada instancia queda suscrita a lo que escriben las demás. Si una instancia nueva se compone mientras la portada nueva aún no está en caché (su `heldEntry` es null y la vieja conserva la anterior), las dos escriben valores distintos y se invalidan mutuamente, reiniciando la transición (`transition = 0f`) en cada escritura, hasta que llega la entrada de caché o vence `AURA_COVER_HOLD_MS`. La ventana es corta (la portada siguiente se resuelve por adelantado, L899-907); sin reproducir [NO VERIFICADO].

- S-006 `SyncUtils.kt:1991-2036` (fila #511) en la reconstrucción de una lista, toda fila local SIN `setVideoId` que no esté en la copia remota se trata como «añadida por el usuario y pendiente»: se conserva al final y se reenvía con `YouTube.addToPlaylist` en CADA sincronización. No mira `isEditable`. Si en una lista guardada de OTRO usuario las entradas llegan sin `playlistSetVideoId` (`PlaylistPage.kt:68`), una canción que su dueño quitó se quedaría para siempre en la copia local y cada sincronización mandaría una petición de añadir que YouTube rechaza. Depende de si YouTube Music envía ese id en listas ajenas [NO VERIFICADO]; un `playlistEntity.isEditable` en el filtro lo cerraría.
- Obs (P3) `SyncUtils.kt:301-313` `singleFlightOp` comprueba «hay una sincronización completa en curso» y luego añade su clave: entre las dos cosas puede arrancar una completa y solaparse. La ventana es de microsegundos y las pasadas internas de la completa también tienen su propia clave.

- Obs (P3) `Queue.kt:85-87` si el elemento ancla se filtra, el respaldo busca el siguiente superviviente con `indexOfFirst` por id: si esa canción aparece repetida ANTES en la cola, se cae en la copia anterior (rebobina). Solo con canciones duplicadas en la cola.
- Obs (P3) `reco/AutoRecoPlaylistWorker.kt:195-196` lee `OpenRouterModelKey ?: ""` mientras las pantallas usan `"google/gemini-2.5-flash-lite"` (`Lyrics.kt:348`, `AiPlaylistDialog.kt:66`…). Con clave de API propia y el modelo nunca tocado, `AiPlaylistService` no manda `model` (`AiPlaylistService.kt:304`) y la fase de IA de la lista automática falla en silencio; queda el relleno local. Sin clave propia (Worker) no afecta.

- Obs (P3, red/batería) `AuraContent.kt:405-417`, `:424`, `:435` sin miniatura, `AuraCover` fabrica `https://i.ytimg.com/vi/<seed>/hqdefault.jpg` con el `seed`, y el `seed` por defecto es `thumbnailUrl ?: "aura-cover"`: una tarjeta sin portada pide `…/vi/aura-cover/hqdefault.jpg`, y una con id de álbum o lista (`MPREb…`, `PL…`) pide un vídeo que no existe. Cada 404 baja la cadena hq→mq→default (`:547-553`): hasta 3 peticiones inútiles por tarjeta.
- Obs (P3, rendimiento) `AuraContent.kt:655` `downloadUtil.getDownload(songId)` devuelve un `Flow` nuevo en cada llamada (`DownloadUtil.kt:559` `downloads.map { … }`), así que `collectAsState` reinicia la suscripción en cada recomposición de la fila; mientras hay una descarga, `liveProgress` recompone todas las filas visibles en cada tic. Un `remember(songId)` lo evita.

- Obs (P3) `AuraHomeScreen.kt:278-288` → `HomeViewModel.kt:1374-1395` `loadMoreYouTubeItems` comprueba `_isLoadingMore` en Main y lo pone a `true` ya dentro del `launch(Dispatchers.IO)`: dos índices visibles seguidos antes de que arranque la corrutina piden la MISMA continuación dos veces y la página se añade repetida (estanterías duplicadas; las claves son por índice, así que no hay cierre). Además el `false` final no va en `finally`.

- Obs (P3, zona EQ, no se toca) `AxionEqScreen.kt:547-577` el medidor FFT corre un bucle `withFrameNanos` en cada fotograma mientras la pantalla del ecualizador está abierta, también en pausa con las barras ya en cero: publica el mismo `Zero` (no repinta), pero pide un fotograma por vsync.
- Obs (P3, zona EQ, misma clase que F-007) `AxionEqScreen.kt:752-…` el `DisposableEffect(playerConnection)` engancha su listener al `ExoPlayer` de ese momento; `PlayerConnection` sí se re-engancha cuando el crossfade cambia de instancia (`PlayerConnection.kt:339-343` `updateAttachedPlayer`), esta pantalla no: tras el primer crossfade con el ecualizador abierto, `audioSessionId`/`meterPlaying` quedan del reproductor viejo.

## [DECISIÓN REQUERIDA] abiertas

- D-002: `ui/player/Player.kt` — `BottomSheetPlayer` (L282-~L3236, ~2.950 líneas) y `MoreActionsButton` (L3584, 0 referencias) son inalcanzables: `SINGLE_UI = true` es constante (`NewUiAvailability.kt:44`) y `NewUiGate` siempre elige `AuraPlayer` (`NewUiHosts.kt:79-95`). Solo `InlineLyricsView` (L3499-L3580) sigue vivo. `UI_INVENTORY.md` aún lista elementos de ese reproductor (§ L418-L422) y el registro lo cita 48 veces. ¿Se conserva como respaldo (por si algún día `SINGLE_UI` vuelve a `false`) o se borra en su propio commit, moviendo `InlineLyricsView` a otro archivo?

- F-001: ¿cambiar `getNonBlocking` a `if (PrefsBridge.isReady) peek(key) ?: default else get(key, default)` (y la semilla de `rememberEnumPreference`) y actualizar el caso 5 de `PrefsBridgeTest`? Riesgo: una clave recién escrita por primera vez puede leerse como default durante los ms que tarda el colector en publicar (el mismo desfase que ya tienen hoy las claves presentes).

- F-008: la política de privacidad no cuenta que la cola inteligente envía títulos y artistas al Worker por defecto. ¿Se actualiza `PRIVACY_POLICY.md` (texto a decidir por el dueño) o se cambia el valor por defecto a apagado? No se toca nada sin respuesta: es texto legal de una app de pago.

- F-011: ¿las pestañas de la Biblioteca deben respetar la misma cadencia de sincronización que el arranque (pasar por `tryAutoSync` o un enfriamiento por pasada), o quieres que entrar a una pestaña siga trayendo los cambios de la cuenta al momento? Cambiarlo hace que un «me gusta» puesto en YouTube tarde hasta la siguiente sincronización programada (o el «Sincronizar» manual) en aparecer.
