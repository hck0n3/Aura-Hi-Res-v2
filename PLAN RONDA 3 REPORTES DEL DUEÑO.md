# PLAN RONDA 3 — REPORTES DEL DUEÑO (Aura Hi-Res v2)

> Documento vivo de la RONDA 3, con el mismo formato que la SÚPER AUDITORÍA:
> tabla de hallazgos, fases con criterios de salida, bitácora y YAML de estado
> al final. Los hallazgos nuevos continúan la numeración de la auditoría (026+).

**Origen:** reporte directo del dueño el 2026-08-25 tras probar BETA-004
(v2.0.0-nosub/vc955) en un Samsung Galaxy S26 Ultra, comparando con un
Xiaomi 17 Ultra y con la experiencia de SimpMusic.

**Misión:** reparar TODOS los reportes sin romper la reproducción (que ya está
VERDE), por fases, con beta numerada entregada tras cada fase y memoria del
proyecto actualizada en cada checkpoint.

## Reglas duras (heredadas de AGENTS.md)

1. `docs/REGRESSION_REGISTRY.md` es lectura obligatoria antes de tocar archivos
   guardián (`MusicService.kt`, `Player.kt`, `App.kt`, `Thumbnail.kt`, etc.).
2. La reproducción es intocable: toda fase abre y cierra con reproducción
   verificada (suite de tests + build + beta en el celular del dueño).
3. Characterization tests antes de refactorizar; suite completa verde antes de
   cada beta (verificar contra XMLs frescos de test-results, no contra la consola).
4. Ecualizador/Superpowered (`eq/`, `app/src/main/cpp/`): solo para mejorarlos —
   el dueño lo pidió explícitamente en HALLAZGO-030. Paquete `license/` intacto.
5. Calentamiento y batería son criterio de calidad permanente: nada que
   muestree pantalla o red por fotograma mientras suena música.
6. Commits locales siempre; `git push` y tags solo con permiso explícito del dueño.
7. Betas vía `C:\Users\AURA\Desktop\BETA OFICIAL  DE AURA FENIX` con nombre
   `BETA-NNN_Aura_v<version>_vc<code>.apk` + `BETA-NNN_LEEME.txt`.
8. Nada de datos del usuario en logs (títulos, artistas, IDs, cookies).
9. Builds siempre con `set "JAVA_HOME=C:\Program Files\Amazon Corretto\jdk21.0.12_9"`
   delante (trampa conocida de esta PC).

## Hallazgos registrados (026–041)

| # | Reporte del dueño | Categoría | Fase |
|---|-------------------|-----------|------|
| HALLAZGO-026 | Los últimos botones del reproductor (letra, ajustes, volumen) salen ultra pequeños en el S26 Ultra; al cambiar la resolución del sistema (HD/FHD/4K) la interfaz no se redimensiona bien; en 4K la app reacciona lento y se traban animaciones y desplazamiento (el audio no). Meta del dueño: interfaz que se adapte a cualquier resolución, marca y capa de personalización, con botones acordes al tamaño de pantalla | UI/responsive | 2 |
| HALLAZGO-027 | Al dar atrás mientras suena música, el reproductor colapsa a mini-reproductor y las canciones se traban un poco | Reproducción/UI | 1 |
| HALLAZGO-028 | Al iniciar sesión se abre la sesión de YouTube/YouTube Music y no regresa a la app para confirmar el login; hay que volver manualmente; detectar la cuenta cuesta y la biblioteca del usuario tarda demasiado en cargar. Referencia del dueño: SimpMusic muestra la biblioteca al instante tras el login | Auth/biblioteca | 3 |
| HALLAZGO-029 | Calidad de audio percibida inferior a la de la Aura antigua, en cuentas no Premium e invitadas. Verificar el ajuste de calidad de audio (estilo SimpMusic, modificable por el usuario) y que quede siempre en lo más alto por defecto | Audio/calidad | 4 |
| HALLAZGO-030 | El S26 Ultra (Dolby Atmos en modo música, ecualizador plano) suena peor que el Xiaomi 17 Ultra (Dolby Atmos en modo plano). Meta del dueño: que en todo gama alta suene igual de bien; si la canción no tiene calidad, que Aura la aporte por puro software, sin importar marca/modelo | Audio/dispositivo | 4 |
| HALLAZGO-031 | Al dictar con el dictado por voz de Google el audio para (correcto), pero al terminar el dictado la canción NO continúa sola: hay que darle Play. Verificar también notificaciones y cualquier pérdida transitoria de foco | Audio/foco | 1 |
| HALLAZGO-032 | Los artistas que el dueño sigue con su cuenta de YouTube/YouTube Music no aparecen en la app | Biblioteca | 3 |
| HALLAZGO-033 | Hay apartados de "artistas favoritos" y "artistas de la biblioteca" pero la app no tiene función de marcar artista favorito (solo suscribirse). Incoherencia UI/función | UI/función | 3 |
| HALLAZGO-034 | Las transparencias con blur no funcionan en varios dispositivos (S26 Ultra y marcas de clientes del dueño): se ven totalmente transparentes, sin blur, dando mala experiencia. Solo funciona bien en Xiaomi | UI/render | 2 |
| HALLAZGO-035 | La app no exporta videos | Exportación | 5 |
| HALLAZGO-036 | Las opciones del apartado de MP3 exportados no tienen lógica; igual que las opciones del apartado de exportación de videos | Exportación/UX | 5 |
| HALLAZGO-037 | Consumo de datos alto: las portadas ya descargadas se vuelven a descargar; las portadas animadas igual; la portada de un álbum se descarga por cada canción en vez de una vez; los videos se cargan automáticamente sin decisión del usuario. Todo ahorro de datos posible sin perder la experiencia actual | Datos/red | 6 |
| HALLAZGO-038 | Verificar el manejo de errores de la app (barrido completo) | Salud | 7 |
| HALLAZGO-039 | Verificar que no haya problemas de temperatura | Salud | 7 |
| HALLAZGO-040 | Verificar que no haya consumos de batería excesivos | Salud | 7 |
| HALLAZGO-041 | Optimización general (paraguas de 038–040): que todo quede muy optimizado | Salud | 7 |

## RESULTADOS FASE 0 — causas raíz (2026-08-25)

Investigación en paralelo con 6 agentes de solo lectura; todos los claims
críticos re-verificados contra el código. Rutas relativas a
`app/src/main/kotlin/com/music/echo/` salvo indicación.

| Hallazgo | Causa raíz confirmada | Evidencia | Dirección de fix |
|---|---|---|---|
| 031 | (1) `abandonAudioFocus()` en la rama AUDIOFOCUS_LOSS: el dictado/llamada toma foco permanente, la app pausa Y se desregistra; al liberarse, el GAIN de retorno no llega a nadie y no reanuda. (2) Flag `wasPlayingBeforeAudioFocusLoss` capturado con `player.isPlaying` (false durante buffering) en vez de `playWhenReady`. (3) Duck 0.2x queda pegado si el LOSS llega duckado | playback/MusicService.kt:2994-3063 | No abandonar foco en LOSS (solo en stop/onDestroy); capturar con playWhenReady; el GAIN ya restaura volumen (:3015) |
| 027 | Trabajo síncrono en Main en los flips del BottomSheet: mini-player compuesto de golpe al inicio (Coil+Palette), disposal del árbol completo al final (release del Visualizer vía binder a AudioFlinger en plena reproducción = candidato al micro-corte), recomposición por frame por lecturas de `progress` en fase de composición | ui/component/BottomSheet.kt:115-198, MainActivity.kt:1798, ui/player/Player.kt:910, ui/newui/AuraRhythm.kt:91-97 | Lecturas de progress diferidas a graphicsLayer/offset; diferir releases a post-animación/IO; mini pre-compuesto |
| 026a | Botones finales con glyphs dp FIJOS (20-22dp) en cápsulas 42-48dp que no escalan con el tamaño de pantalla; precedente REGRESSION_REGISTRY #153: "subir dp fijos" ya se hizo y no resolvió de fondo | ui/newui/AuraQueue.kt:1158-1290, ui/newui/AuraPlayer.kt, ui/player/Queue.kt:316-535 | Escala relativa (BoxWithConstraints/WindowSizeClass), no otro aumento de dp fijos |
| 026b | Mecanismo propio de escala de densidad: `originalDensityDpi` se captura UNA vez y se re-aplica en cada resume con `updateConfiguration` (deprecada); tras cambiar la resolución del sistema re-escribe el dpi viejo escalado sobre la densidad nueva. `configChanges` incluye density\|screenSize (sin recreación) | com/dpi/DensityConfiguration.kt:17-66, AndroidManifest.xml:~95 | Recapturar el dpi original en cada cambio real; re-aplicar sobre la densidad vigente o retirar el mecanismo. Preguntar al dueño si usa escala != 1.0 en Ajustes > Apariencia |
| 026c | Costo GPU en 4K: blurs a pantalla completa sin downscale (34-150dp) + rotación infinita de la capa blurreada + `preferredDisplayModeId` que no se recalcula tras cambio de resolución (solo con el toggle de refresh) | ui/newui/AuraPlayer.kt:2415-2438, ui/player/Player.kt:973+, MainActivity.kt:663-680 | Downscale antes del blur (patrón glassResolutionScale ya existe en DrawBackdropModifier); recalcular display mode con Configuration; reducir blur/spin en 4K |
| 028 | Login: detección SOLO en onPageFinished con allowlist estrecha (URL exactamente music.youtube.com + cookie SAPISID), sin reintentos; fallo de validación sin feedback en UI. Biblioteca: 4 syncs SECUENCIALES (cola de 1 consumidor) con paginación completa (hasta 50 páginas) + 1 petición getChannelId POR artista (concurrencia 5) | ui/screens/LoginScreen.kt:204-259, utils/SyncUtils.kt:120-186,1100-1290 | Monitoreo continuo de cookies + overlay "verificando" + reintento; primera página directa de InnerTube (estilo SimpMusic) mientras el mirror sigue; syncs en paralelo; channelId desde el parser |
| 029 | Ruta de streaming 100% DESAUTENTICADA: cliente principal con loginSupported=false + InnerTube solo adjunta cookie si el cliente soporta login + NewPipe fuerza tokens="" (parche emergencia 2026-08-23) → YouTube sirve bitrates de invitado a todos, incluso Premium. Ajuste de calidad existe como pref pero sin UI (candado a OPUS). AUDIO_ITAG_PREFERENCE pone 141 (AAC 256k) detrás de Opus de ~50k | innertube/.../YouTubeClient.kt:167, InnerTube.kt:176, pages/NewPipe.kt:262-275, ui/screens/settings/PlayerSettings.kt:102-106, utils/YTPlayerUtils.kt:93 | Recuperar descubrimiento autenticado de formatos (con pruebas: el tokens="" se puso por fallos reales); renderizar selector de calidad; reordenar itags |
| 030 | Sin diferencias de código por marca (mismos AudioAttributes/float/offload). DSP default NO plano de la app: Simulador Tidal ON + Safe Volume ON + normalización -7dB a pistas sin metadatos, apilado bajo el Dolby "modo música" de Samsung (doble coloreado). Toggles de Normalización/Tidal existen cableados pero sin UI | ui/screens/settings/SoundSettings.kt:111, eq/audio/AudioGain.kt, playback/MusicService.kt:2495 | Exponer toggles faltantes + modo directo/bit-perfect; NO tocar AudioAttributes |
| 032 | Parser grid de biblioteca descarta EN SILENCIO todo artista sin íconos MUSIC_SHUFFLE y MIX (`?: return null`); la variante shelf del mismo archivo deja esos campos nulables (asimetría = bug). Agravado por 028: sin login detectado el sync ni corre | innertube/.../pages/LibraryPage.kt:64-76 | shuffleEndpoint/radioEndpoint nulables en el parser grid |
| 033 | La sección "Me gusta/Favoritos" de artistas filtra followedByUserAt IS NOT NULL = literalmente la lista de suscritos; no existe favorito independiente | db/DatabaseDao.kt:807-843, ui/screens/library/LibraryArtistsScreen.kt:113-122 | DECISIÓN DEL DUEÑO: favorito real de artistas o rebautizar a "Seguidos" |
| 034 | Blur de ventana (`setBackgroundBlurRadius`/`FLAG_BLUR_BEHIND`) es extensión OEM: Samsung lo ignora EN SILENCIO (sin excepción, el try/catch no detecta nada); Xiaomi sí lo implementa. La ventana se vuelve TRANSPARENTE antes de pedir blur y la placa frost es alpha real 0.34 que confía 100% en el blur → transparencia total sin blur | ui/newui/AuraFloatingChrome.kt:60-140 | Detectar soporte real (ro.surface_flinger.supports_background_blur + isCrossWindowBlurEnabled) ANTES de transparentar; fallback opaco (la rama no-premium ya existe) |
| 035 | Export de video resuelve SOLO con videoStreamUrlDiag (cliente TVHTML5 quemado por bot-check); el modo video in-app usa adaptiveVideoStreamNewPipe (vivo) pero el export nunca lo llama. Sonda del diálogo de export y descarga offline de video con el mismo agujero | playback/AudioExportService.kt:328, utils/YTPlayerUtils.kt:240,1520,1543, ui/utils/ExportFormatChooser.kt:82, playback/DownloadUtil.kt:207 | adaptiveVideoStreamNewPipe primero en los 3 puntos; manejar stream muxed (itag 18/22) en el mux ffmpeg |
| 036 | Menús genéricos de YouTube aplicados a archivos locales ya exportados: re-descargar, re-exportar, bulk "Descargar" que baja AUDIO en vez de video (id sin sufijo ::video); falta "Eliminar del dispositivo" para MP3; el borrado devuelve true aunque falle | ui/newui/AuraDownloadsScreen.kt:127-294, ui/menu/SongMenu.kt:405-497,541-1084, utils/LocalMediaIntents.kt:125-170 | Menús dedicados por tipo; extraer MP3 del MP4 local con ffmpeg; fix del borrado silenciado |
| 037 | (1) onTrimMemory borra el DISK cache de Coil ante presión rutinaria (TRIM_MEMORY_BACKGROUND = estado normal del reproductor) → re-descarga todo. (2) Misma portada en 3-6 tamaños = 3-6 cache keys distintas. (3) Cachés canvas solo memoria (TTL 24h y 60s) → búsquedas repetidas. (4) Video de fondo de artista autoplay y sin caché de disco. (5) Letras solo memoria | playback/MusicService.kt:9666-9675, ui/utils/YouTubeUtils.kt:5-48, canvas/...providers, artistvideo/ArtistVideo.kt, lyrics/LyricsHelper.kt:57 | Nunca borrar diskCache ahí; tamaños canónicos/cache key estable por mediaId; persistir canvas y letras a disco; CacheDataSource + gate por red medida en videos |
| 038-041 | Pendientes: barrido dedicado al inicio de FASE 7 | — | — |

## Fases

### FASE 0 — Triage y recolección (sin cambios de código)

Objetivo: confirmar cada hallazgo contra el código actual, localizar
archivo:línea, proponer causa raíz y fijar el orden definitivo de reparación.

- Investigación en paralelo por cluster con agentes de solo lectura:
  UI/responsive (026, 034), reproducción/foco (027, 031), auth/biblioteca
  (028, 032, 033), audio/calidad (029, 030), exportación (035, 036),
  datos (037). Salud (038–041) se investiga al inicio de la FASE 7.
- Salida: tabla de causas raíz con evidencia archivo:línea por hallazgo.
- Criterio de salida: cada hallazgo tiene causa raíz confirmada o marcado
  "no se reproduce en código / requiere prueba en dispositivo".

### FASE 1 — Reproducción y foco de audio (lo crítico primero)

Hallazgos: 031, 027.

- 031: auditar el manejo de AudioFocus en `MusicService.kt`
  (AUDIOFOCUS_LOSS_TRANSIENT, TRANSIENT_CAN_DUCK, ganancias/reanudación);
  el audio debe reanudarse solo tras dictado/notificaciones. Tests del
  listener de foco.
- 027: identificar el trabajo en el hilo principal al colapsar a
  mini-reproductor (recomposición, miniaturas, colas) y sacarlo del camino
  crítico. Characterization test antes de tocar.
- Cierre: suite verde + build + BETA-005 + veredicto del dueño.

### FASE 2 — UI responsive, fluidez y blur

Hallazgos: 026, 034.

- 026: inventario de tamaños fijos (px/dp hardcodeados) en las barras del
  reproductor y pantallas principales; comportamiento con las densidades del
  S26 Ultra (incluido el modo 4K); meta: botones y layout acordes a la
  pantalla en cualquier dispositivo y capa.
- 034: detectar el soporte real de blur del dispositivo (API/RenderEffect/
  limitaciones OEM) y añadir fallback elegante (opaco/degradado) donde no
  funcione, en vez de transparencia total.
- Revisar `docs/UI_INVENTORY.md` antes de cambiar la interfaz.
- Cierre: suite verde + build + BETA-006 + veredicto del dueño.

### FASE 3 — Login y biblioteca

Hallazgos: 028, 032, 033.

- 028: flujo de login completo — retorno a la app tras autenticarse,
  detección inmediata de la sesión y carga rápida de la biblioteca
  (paralelismo/paginación). Referencia de comportamiento: SimpMusic.
- 032: mostrar los artistas seguidos de la cuenta YouTube/YouTube Music.
- 033: decidir con el dueño: implementar favoritos de artistas reales o
  quitar/renombrar los apartados muertos (ocultar también es perder:
  revisar UI_INVENTORY).
- Cierre: suite verde + build + BETA-007 + veredicto del dueño.

### FASE 4 — Calidad de audio

Hallazgos: 029, 030.

- 029: auditar qué formatos/calidades resuelve la cadena actual
  (winner ANDROID_VR) para cuentas no Premium e invitadas; exponer/verificar
  el ajuste de calidad del usuario y fijar el máximo por defecto.
- 030: investigar la interacción con el procesamiento de audio del fabricante
  (Dolby Atmos por modo) y qué puede aportar la app por software
  (EQ Superpowered, loudness, ganancia — `eq/`, `AudioGain.kt`) para sonar
  bien en cualquier gama alta. Zona permitida: solo mejora, pedida por el dueño.
- Cierre: suite verde + build + BETA-008 + prueba A/B del dueño en ambos teléfonos.

### FASE 5 — Exportación

Hallazgos: 035, 036.

- 035: reproducir y corregir el fallo de exportación de video (pipeline
  ffmpeg-kit; ojo con HALLAZGO-010: el mirror aliyun es la única fuente).
- 036: inventario de las opciones de exportación MP3/video y corrección de la
  UX (con el dueño si hay decisiones de producto).
- Cierre: suite verde + build + BETA-009 + veredicto del dueño.

### FASE 6 — Consumo de datos

Hallazgos: 037.

- Caché persistente de portadas estáticas y animadas; portada de álbum única
  (no por canción); nada de re-descargas con caché válida.
- Videos: sin carga/reproducción automática salvo decisión explícita del usuario.
- Barrido de cualquier otro gasto de red evitable sin perder experiencia.
- Cierre: suite verde + build + BETA-010 + veredicto del dueño.

### FASE 7 — Salud y optimización final

Hallazgos: 038, 039, 040, 041.

- 038: barrido de manejo de errores (estados límite, crashes potenciales,
  reintentos, fallos de red).
- 039/040: wakelocks, workers, listeners, muestreos de pantalla/red; nada de
  trabajo por fotograma en reproducción; verificar consumo en segundo plano.
- 041: barrido final de optimización y limpieza.
- Cierre: suite verde + build + BETA-011 + veredicto del dueño. Si sale VERDE,
  la v2 queda candidata a estable (publicar sigue requiriendo pre-publish-check,
  CI verde y permiso explícito del dueño).

## Fuera de alcance (pistas paralelas, no mezclar con la RONDA 3)

- HALLAZGO-021 Fase B: decisión pendiente del dueño (cerrar aquí o extraer
  video mode/crossfade con colaboradores con estado).
- Publicar la estable v2: pre-publish-check + CI verde + permiso explícito.
- Dependabot del repo nuevo: nada se mergea sin revisión del dueño.
- HALLAZGO-010: plan de salida del mirror aliyun (reemplazar ffmpeg-kit y
  tinypinyin) sigue como riesgo aceptado; la FASE 5 lo toca sin agravarlo.

## Bitácora

| Fecha | Evento | Estado |
|---|---|---|
| 2026-08-25 | El dueño reporta 16 problemas de BETA-004 en el S26 Ultra; se registran HALLAZGO-026..041 y se abre la RONDA 3 por fases | EN_CURSO — FASE 0 |
| 2026-08-25 | FASE 0 completa: 6 agentes en paralelo + re-verificación principal; causas raíz confirmadas para 026-037 (tabla RESULTADOS FASE 0); 038-041 quedan para FASE 7; decisiones pendientes del dueño: 033 (favoritos) y 026b (¿usa escala de densidad?) | COMPLETADA |

```yaml
estado_actual:
  fecha: 2026-08-25
  fase_actual: RONDA_3_FASE_1_REPRODUCCION
  proxima_accion: FASE_1 — fix HALLAZGO-031 (no abandonar AudioFocus en LOSS + flag con playWhenReady) y HALLAZGO-027 (trabajo Main en flips del BottomSheet); characterization tests + suite + build + BETA-005
  bloqueos: []
  memoria: ACTIVA
```
