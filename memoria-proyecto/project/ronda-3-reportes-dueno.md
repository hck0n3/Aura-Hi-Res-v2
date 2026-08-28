---
name: RONDA 3 — Mac nueva: BETA-017 v2.0.14/vc970 (PrefsBridge, fila 180) en build; veredictos BETA-015/016 pendientes
description: Estado 2026-08-27 noche — proyecto migrado a macOS (Apple M4/32 GB; la carpeta NO tiene .git, los commits locales de Windows no migraron). BETA-015 (v2.0.12/vc968) y BETA-016 (v2.0.13/vc969, 062 crypto) entregadas en la carpeta de nube de Windows con veredictos del dueño PENDIENTES. BETA-017 (v2.0.14/vc970) = PrefsBridge (cierre del diferido 060 "semillas runBlocking", fila 180, el fix de los tiempos de reacción al tocar) implementado en la Mac (App.kt + DataStore.kt + PrefsBridgeTest 6 casos) con suite + assembleUniversalGmsRelease en curso. Keystores/secretos de repuesto en ~/Downloads/AuraHiResDevBackup y ~/Downloads/keystore. Próximo hallazgo 063, próxima fila del registro 181, próxima beta BETA-018. REGLA PERMANENTE - no publicar código Superpowered en GitHub (ya expuesto en origin/main — decisión ~09-02). GitHub bloqueado hasta ~2026-09-02
type: project
---

El 2026-08-25 el dueño probó BETA-004 (v2.0.0-nosub/vc955) en un Samsung
Galaxy S26 Ultra (antes probaba en un Xiaomi 17 Ultra) y reportó 16 problemas.
Se registraron como HALLAZGO-026..041 y se abrió la RONDA 3 con plan por
fases: documento vivo `PLAN RONDA 3 REPORTES DEL DUEÑO.md` en la raíz del repo
(commit 8adf4c8, local sin push).

Resumen de hallazgos por fase:
- FASE 1 (crítico, reproducción): 031 el audio no se reanuda tras dictado de
  Google/notificaciones (foco de audio); 027 las canciones se traban un poco
  al colapsar a mini-reproductor al dar atrás.
- FASE 2 (UI): 026 botones finales del reproductor ultra pequeños + sin
  re-layout al cambiar resolución del sistema (HD/FHD/4K) + app lenta en 4K
  (meta del dueño: interfaz responsive universal, cualquier marca/capa);
  034 blur/transparencias no funcionan fuera de Xiaomi (se ven transparentes
  sin blur, también en marcas de clientes del dueño).
- FASE 3 (auth/biblioteca): 028 el login abre la sesión de YouTube/YT Music y
  no regresa a la app; detectar la cuenta y cargar la biblioteca tarda mucho
  (referencia: SimpMusic es instantáneo); 032 artistas seguidos de la cuenta
  no aparecen; 033 hay apartados de artistas favoritos sin función de favoritos
  (solo suscribirse).
- FASE 4 (calidad de audio): 029 calidad percibida inferior a la Aura antigua
  en cuentas no Premium e invitadas + verificar ajuste de calidad estilo
  SimpMusic siempre en máximo; 030 el S26 Ultra (Dolby modo música, EQ plano)
  suena peor que el Xiaomi 17 Ultra (Dolby plano) — meta: misma buena
  experiencia en cualquier gama alta, y si la canción no tiene calidad que
  Aura la aporte por puro software (zona eq/Superpowered autorizada, solo mejora).
- FASE 5 (exportación): 035 no exporta videos; 036 opciones de los apartados
  MP3 exportados y exportación de video sin lógica.
- FASE 6 (datos): 037 consumo alto — portadas ya cacheadas se re-descargan,
  portadas animadas igual, portada de álbum por canción en vez de una vez,
  videos con carga automática sin decisión del usuario; todo ahorro posible
  sin perder experiencia.
- FASE 7 (salud): 038 manejo de errores, 039 temperatura, 040 batería,
  041 optimización general.

Reglas de la ronda: reproducción intocable (toda fase abre y cierra con
reproducción verificada), characterization tests antes de refactor, suite
verde + beta numerada (BETA-005 en adelante, carpeta de nube) tras cada fase,
memoria actualizada en cada checkpoint, commits locales sin push.

**FASE 0 COMPLETADA (2026-08-25):** 6 agentes en paralelo + re-verificación
contra el código. Causas raíz confirmadas (tabla completa en la sección
RESULTADOS FASE 0 del plan):
- 031: `abandonAudioFocus()` en rama AUDIOFOCUS_LOSS (MusicService.kt:~3034)
  desregistra la app y el GAIN de retorno nunca llega + flag capturado con
  isPlaying en vez de playWhenReady.
- 027: trabajo síncrono Main en flips del BottomSheet (composición mini de
  golpe, disposal del árbol con release del Visualizer, recomposición por frame).
- 026: (a) glyphs dp fijos que no escalan, (b) DensityConfiguration cachea
  originalDensityDpi una vez y re-escribe dpi viejo tras cambio de resolución,
  (c) blurs fullscreen sin downscale + display mode sin recalcular en 4K.
- 028: detección de login solo en onPageFinished con allowlist estrecha;
  syncs secuenciales con paginación completa + getChannelId por artista.
- 029: streaming 100% desautenticado (loginSupported=false + tokens="" de
  emergencia en NewPipe.kt:271) = bitrates de invitado para todos; selector
  de calidad sin UI (candado OPUS en PlayerSettings.kt:102).
- 030: sin diferencias por marca; DSP default no-plano (Tidal ON + Safe
  Volume ON) apilado con Dolby Samsung; toggles sin UI.
- 032: parser grid LibraryPage.kt:64-76 descarta artistas sin íconos
  MUSIC_SHUFFLE/MIX (`?: return null`).
- 033: "favoritos" de artistas = lista de suscritos; DECISIÓN DEL DUEÑO
  pendiente (favorito real o rebautizar).
- 034: blur de ventana OEM ignorado en silencio por Samsung; ventana
  transparente + FrostFill alpha 0.34 sin blur = transparencia total.
- 035: export de video usa solo videoStreamUrlDiag (TVHTML5 quemado); el
  in-app usa adaptiveVideoStreamNewPipe (vivo) que el export nunca llama.
- 036: menús genéricos YT sobre archivos locales; borrado devuelve true si falla.
- 037: onTrimMemory borra disk cache de Coil (MusicService.kt:9666-9675),
  portadas en 3-6 tamaños = 3-6 cache keys, canvas/letras solo memoria,
  artist video autoplay sin caché disco.
Preguntas RESUELTAS para el dueño (2026-08-26): 026b — NUNCA tocó la escala
de densidad, usa Native 100% (mecanismo interno inactivo con 1.0; su 026
viene de la resolución del sistema HD/FHD/4K); 033 — decidió REBAUTIZAR la
sección de favoritos ("solo cámbiale el nombre y que funcione de verdad"),
sin favoritos reales.

**FASE 1 COMPLETADA (2026-08-26):** TDD red-green con núcleos puros nuevos:
- 031 CERRADO (commit dbced69): núcleo puro `playback/AudioFocusPolicy.kt`
  + `AudioFocusPolicyTest` (20 tests). Reglas: ninguna pérdida (LOSS,
  LOSS_TRANSIENT, CAN_DUCK) abandona la petición de foco (abandonFocus solo en
  onCreate/onDestroy); la intención de reanudar se captura con
  `playWhenReady || wasPlayingBeforeLoss` (OR-sticky entre ráfagas de
  notificaciones; playWhenReady cubre buffering donde isPlaying=false);
  GAIN/GAIN_TRANSIENT reanudan con el delay de 300ms, guard reentrante y
  chequeo de cast preservados; duck 0.2f preservado; MAY_DUCK de retorno NO
  reanuda. MusicService.handleAudioFocusChange (~línea 2993) delega en el núcleo.
- 027 CERRADO (commit 5485aaa): núcleo puro `ui/component/BottomSheetVisuals.kt`
  + test (11 tests) con las curvas exactas (backgroundAlpha, contentAlpha,
  miniAlpha, canvasVisible, slideOffsetPx). Cambios: fades del BottomSheet y
  offset de navegación leídos en fase de dibujo (graphicsLayer) en vez de
  composición; gate del canvas → derivedStateOf; mini-player pre-compuesto
  (sin gate isExpanded) con clickable deshabilitado mientras está expandido;
  `Visualizer.release()` (binder a AudioFlinger) movido a scope IO de proceso
  en AuraRhythm.kt. Binding P13 de video verificado intacto.
- Verificación: suite 746/746 verde (60 XMLs frescos, --rerun), build verde.
  Commits de evidencia y bump v2.0.1/vc956: 8d3fc36, 27d927e (locales).
- BETA-005 (v2.0.1-nosub/vc956) entregada en la carpeta de nube con LEEME.
  VARIANTE CORRECTA: `assembleArm64FossRelease -Pnosub=true` (paquete
  iad1tya.aura.music.dev, arm64, firmada con CN=Aura Hi-Res v2) = actualiza
  ENCIMA de BETA-004 sin perder datos. CUIDADO: el build type debug añade el
  sufijo `.debug` al applicationId y firma con la debug keystore — un APK
  debug universal NO actualiza a BETA-004 (se instala al lado); eso se cazó
  a tiempo comparando badging (aapt2) contra el APK de BETA-004.
  VEREDICTO DEL DUEÑO PENDIENTE.

**VEREDICTO BETA-005 Y REAPERTURA FASE 1 (2026-08-26):** el dueño reportó que
el jank del flip PERSISTE (ahora en ambos sentidos del gesto: abrir a pantalla
completa y cerrar a mini) y que el streaming tarda en arrancar. Análisis del
log (C:\Users\AURA\Downloads\log de aurav2) + 2 agentes + re-verificación
principal. Causas nuevas (tabla REAPERTURA FASE 1 en el plan):
- 027: (a) el Visualizer se CONSTRUÍA en Main en cada flip (binder a
  AudioFlinger; FASE 1 solo había movido el release a IO) — inserción/quita
  del efecto en la cadena de audio en plena reproducción = candidato #1 al
  micro-corte; (b) `rhythmIntensity` leído en fase de composición →
  recomposición de todo AuraPlayer por frame mientras suena; (c) el gate
  `if (!state.isCollapsed)` compone/desecha el árbol completo en los bordes
  del flip, incluido un ExoPlayer dedicado (CanvasArtworkPlayer) creado Y
  liberado en Main.
- 042 (HALLAZGO NUEVO, inicio lento): resolve real 2.1-3.8s por canción —
  PipePipe falla SIEMPRE en este dispositivo/IP (muro anti-bot, ~1.7s
  perdidos) antes de caer a BraveNewPipe (~2s, el que funciona); preload de
  un solo disparo con bug de expiry (`containsKey` sin vencimiento vs data
  source que solo sirve entradas vigentes) y sin reintento; ventana de 117s
  sin preload con FETCHING STREAM en plena transición (~2.9s de silencio).
  DESCARTADO invertir prioridad de URLs directas del /player: el comentario
  SIMPMUSIC MODEL (YTPlayerUtils.kt:1781-1788) documenta que SimpMusic NO
  las reproduce (verificado contra su fuente 2026-08-23).
- Fixes segunda pasada (COMMIT 8414363 + bump 5396bdd, suite y build VERDES):
  keep-alive del Visualizer en AuraRhythm.kt (captureEnabled estable entre
  flips, `active` solo gatea el bucle por-frame) + núcleo puro
  `quantizeAuraRhythmIntensity` (+AuraRhythmTest, 10 tests) leído vía
  derivedStateOf en AuraPlayer.kt; CanvasArtworkPlayer difiere la creación
  del ExoPlayer 300ms post-flip (release NO se puede mover de thread:
  ExoPlayerImpl.release() llama verifyApplicationThread() en media3 1.10.1 —
  verificado en el sources jar); expiry en el lambda hasCachedUrl del preload
  (MusicService.kt:~11278); núcleo puro `ExtractorCircuitBreaker` en innertube
  (+8 tests) cableado en NewPipeExtractor.newPipePlayer: 3 fallos consecutivos
  de PipePipe → se salta 10 min y va directo a BraveNewPipe (~1.7s ahorrados
  por resolve). Suite final: app 756/756 + innertube 24/24 (XMLs frescos).
- BETA-006 ENTREGADA (2026-08-26): v2.0.2-nosub/vc957, arm64, verificada con
  aapt2 (iad1tya.aura.music.dev, arm64-v8a) + apksigner (v2 scheme true,
  CN=Aura Hi-Res v2, SHA-256 ba82c11d… = misma keystore que BETA-004/005).
  En la carpeta de nube como BETA-006_Aura_v2.0.2_vc957.apk + LEEME (pruebas:
  canción completa, flip ambos sentidos, arranque rápido desde la 3ra canción,
  REINTENTO del dictado para 031). Commits locales: 8414363, 5396bdd, bdeeab0,
  e34d971. VEREDICTO DEL DUEÑO PENDIENTE.
- Seguimientos registrados (no bloquean BETA-006): reintento del preload
  fallido; resolves duplicados en paralelo del mismo videoId; gap del
  crossfade cut-not-ready.

**HALLAZGO-043 (petición del dueño 2026-08-26):** interfaz adaptable a TV,
tablet, plegables y celulares, adaptada al estilo de cada dispositivo, en la
UI nueva Y la vieja. Registrado en la FASE 2 del plan (junto a 026 y 034).
DISPOSITIVOS DEL DUEÑO (confirmados 2026-08-26): TV TCL con GOOGLE TV
(confirmado por el dueño — la app puede correr ahí), Galaxy Fold 7 (plegable
+ pantalla interior ~8" cubre el caso tablet) y S25 Ultra (antes probó
también en S26 Ultra y Xiaomi 17 Ultra). Sin tablet dedicada.
**HALLAZGO-044 (2026-08-26):** los clientes se quejan del diseño de la app en
Android Auto (básico/feo). FASE 2. OJO: la UI de media en Auto es plantillada
por Google; el margen real es contenido del árbol (portadas, metadatos,
acciones) + evaluar Car App Library. El dueño CONFIRMÓ que tiene carro con
Android Auto instalado (2026-08-26) — se puede probar en real.
**HALLAZGO-045 (2026-08-26):** el cambio de color por portada casi no se nota
en el fondo de toda la app; hacerlo más notorio (bloom/wash/palette global),
sin romper legibilidad ni AMOLED. FASE 2.
**HALLAZGO-046 (petición del dueño 2026-08-26):** preamp del ecualizador POR
DEFECTO en +3 dB (pidió +1.2 primero, lo corrigió a +3 el mismo día; hoy 0.0
vía one-time AudioDefaultsV2 en App.kt:~973-1025; rango -20..+6). FASE 4.
Verificar interacción con safeVolumeGainWithEqPreamp (AudioGain.kt:116,
recorta ganancia con preamp positivo y Safe Volume ON). Zona eq autorizada
por petición explícita.
**HALLAZGO-047 (reporte del dueño 2026-08-26, durante prueba de BETA-006,
commit a4a0d9f):** "Las canciones cuando inician, inician cortadas".
INVESTIGACIÓN DE CÓDIGO COMPLETA (agente solo lectura, 2026-08-26). Cinco
causas candidatas por probabilidad: (1) cut-not-ready — el secundario no
llega a READY, la espera acotada se agota (≤800ms restantes del saliente) y
media3 auto-avanza con hard cut pagando resolve+buffer desde cero
(MusicService.kt:10495-10524; resolve 2.1-3.8s en logs BETA-005, peaje
PipePipe ~1.7s cuando el breaker está cerrado); (2) SponsorBlock salteando
el inicio — ON por defecto (MusicService.kt:2280 `?: true`, migración
0.6.127 lo forzó), categoría music_offtopic (SponsorBlockService.kt:24)
cubre los tramos "no musicales" que suelen arrancar en el segundo 0 de los
videos musicales; el watcher polea cada 1s y hace seekTo(fin) SIN loguear
(MusicService.kt:5924-5934) → la canción arranca y 1-2s después salta =
"inicia cortada"; toggle en UI: Ajustes ▸ Reproductor ▸ "Saltar segmentos
sin música" (PlayerSettings.kt:603-623); (3) blend tardío con curva 4 — si
la espera READY consume el lead, durOut se recorta a 600ms y el entrante
(curva 4: p=0.1→−24dB) queda casi inaudible 1-2s (CrossfadeMath.kt);
(4) underrun — reloj de fade-in congelado si el entrante cae en BUFFERING;
(5) lead corto de preload (Data Saver lo saltea, MusicService.kt:11237-11240).
DESCARTADO: arranque adelantado (seekTo siempre a 0, ~10720; intro memory
deshabilitada por el dueño 2026-08-18; leadSilenceHintMs solo se escribe),
regresión BETA-006 (expiry+breaker van en dirección contraria al corte),
primer item de cola (playQueue 3388-3390 posición 0), Visualizer keep-alive,
buffer de arranque (playbackStartBuffer 700ms). SIGUIENTE PASO (cero riesgo):
evidencia del dueño — ev=cut-not-ready en app.log (Ajustes ▸ Registros) +
A/B apagando "Saltar segmentos sin música". Fixes condicionales: Fix A si
SponsorBlock confirma (guardia de inicio ~2-3s + loguear skips, riesgo
bajo); Fix B si cut-not-ready confirma (latencia de las 3 primeras
resoluciones por proceso, riesgo bajo-medio). NO tocar curva/duración del
crossfade (curva 4 afinada por el dueño en 3 rondas).
La numeración de betas se corrió: reapertura FASE 1 = BETA-006,
FASE 2 = BETA-007, etc. (Próximo número de hallazgo nuevo: 050.)

**PRESET AR-SOUND (petición del dueño 2026-08-26, commit bd5e843):** añadido
a `FactoryPreset` (EqConstants.kt) con su curva y test que la fija
(`arSoundCurveMatchesOwnerSpec`). **CURVA DEFINITIVA v2 (revisión del dueño,
mismo día):** 31 Hz +9, 62 Hz 0, **125 Hz 0** (antes −4), 250 Hz −3, 500 Hz −3,
1 kHz −2, 2 kHz +1, 4 kHz +1, 8 kHz +2, 16 kHz +2 — constante y test
actualizados; OJO: la BETA-007 salió con la curva v1 (125 Hz −4), la v2 entra
en la próxima beta. Aparece solo en la grilla "PREAJUSTOS AUDIÓFILOS" de
AxionEqScreen (la grilla itera `FactoryPreset.entries`; el orden del enum no
afecta la exhibición porque se ordena por largo de nombre). OJO al añadir
presets: la detección del preset activo usa tolerancia de 0.5 dB por banda
sobre el orden canónico del enum.

**HALLAZGO-046 IMPLEMENTADO (2026-08-26, commit fbb7d25 + doc 179f927):**
preamp del EQ POR DEFECTO en +3 dB (adelantado de la FASE 4 por petición
explícita sin dependencias). Mecánica: (1) migración one-time de llave fresca
`EqPreampDefault3DbAppliedKey` → `migrateEqPreampDefaultV3` en App.kt, corre
JUSTO DESPUÉS de `migrateAudioDefaultsV2`; sube 0.0 → +3.0 en el perfil
efectivo del repo (unsaved ?: active, copy(preamp=3.0) + saveProfile +
setUnsavedProfile + setActiveProfile) Y el mirror echo_eq_prefs; SOLO si el
usuario sigue en el default (preamp ≠ 0.0 se respeta y solo estampa la
bandera); si no hay NINGÚN estado EQ todavía reintenta en el próximo arranque
(nunca compite con la siembra V2). (2) La siembra V2 ahora entrega +3.0
directo (perfil "JR Tuning" + mirror) — ambos escritores acuerdan. (3)
Fallbacks de lectura alineados a 3f: `MusicService.currentEqPreampDb` y
`AxionEqViewModel._preamp`. (4) `reset()` del EQ sigue yendo a 0f (acción
explícita de aplanar, intacta). Interacción Safe Volume VERIFICADA:
`safeVolumeGainWithEqPreamp` (AudioGain.kt) solo recorta cuando
baseGainLinear > 1 (o sea solo el boost de loudness-makeup en canciones
tranquilas, ~2.25 dB con preamp +3, por diseño contra saturación del
limiter); en masters normales el +3 pasa íntegro. headroomPreampDb +
eqMakeupDb preservan el preamp del usuario (makeup restaura el headroom
automático, cap 12 dB; con AR-SOUND maxBoost +9 el makeup queda en 10, dentro
del cap). Suite 757/757 + assembleUniversalFossDebug verde. FALTA:
confirmación de oído del dueño en la próxima beta (Safe Volume ON forzado).

**BETA-007 ENTREGADA (2026-08-26, commits 84647c0 bump + c6bea45 doc/evidencia):**
el dueño preguntó "¿ya está la beta 6?" y luego reportó que no veía el AR-SOUND
— la BETA-006 se compiló ANTES de los commits AR-SOUND/046, así que se cortó
la BETA-007 (v2.0.3-nosub/vc958) que consolida TODO: fixes 027/042/031 de la
006 + preset AR-SOUND + preamp +3 dB. Build assembleArm64FossRelease -Pnosub
BUILD SUCCESSFUL 4m02s; verificada aapt2 (iad1tya.aura.music.dev, arm64-v8a,
2.0.3-nosub/958) + apksigner (Verifies, v2 scheme true, CN=Aura Hi-Res v2,
SHA-256 ba82c11d… = misma keystore que 004/005/006 → actualiza encima sin
perder datos). En la carpeta de nube como BETA-007_Aura_v2.0.3_vc958.apk +
BETA-007_LEEME.txt (7 pruebas: canción completa, flip ambos sentidos, arranque
rápido, dictado, AR-SOUND en PREAJUSTOS AUDIÓFILOS, preamp marcando +3 dB,
y A/B del 047 apagando "Saltar segmentos sin música"). EL DUEÑO DEBE PROBAR
LA 007, NO LA 006. Veredicto pendiente.

**HALLAZGO-048 y 049 ARREGLADOS (2026-08-26, BETA-008):**
- 048 (cortes al entrar/salir del EQ) — CAUSA RAÍZ CONFIRMADA: el medidor
  FFT del EQ tenía su PROPIO `Visualizer` (binder a AudioFlinger): attach al
  entrar, release síncrono en Main al salir, re-attach del watchdog, y
  `visualizerGeneration++` por transición de pista = hasta 4 ciclos
  attach/release por ida y vuelta, cada uno un corte audible sobre la sesión
  activa. FIX: `AuraVisualizerHub` NUEVO (ui/newui/AuraVisualizerHub.kt) —
  DUEÑO ÚNICO del Visualizer para todo el proceso (scope IO vida-de-proceso +
  Mutex), consumidores RHYTHM/EQ_FFT, navegar solo voltea un booleano = CERO
  churn (reconcile retorna si ya existe), ambos callbacks siempre activos,
  FFT gateada por eqFftActive, fallback sesión→0, requestRebind throttle
  10s/4s silencio real. `rememberAuraRhythmLevel` (AuraRhythm.kt, reescrito:
  borrados AuraVisualizerExclusive + visualizerReleaseScope) y
  `rememberEqFftMeter` (AxionEqScreen.kt, reescrito como consumidor +
  watchdog de freshness 2.5s) ahora son consumidores; TODO el código viejo de
  attach/release/reintentos/generaciones borrado. Matemática del medidor
  VERBATIM + `AuraVisualizerHubTest` 12 casos (OJO con la matemática: barra =
  PROMEDIO de magnitud en su rango de bins /48 — bar 18 spans [287,320) = 33
  bins; pico = MÁX bin /48 sin promediar; sign-extension .toInt()). Filas 156
  del REGRESSION_REGISTRY.
- 049 (colores del EQ no siguen la carátula + paleta viva en TODAS las
  pantallas) — TRES agujeros: (a) AxionEqScreen pintaba fondo estático →
  bloom 0.45f gateado por rememberAuraPanelSkin (skin.enabled &&
  skin.darkGround) + semilla viva mediaMetadata?.id, TopAppBar transparente;
  (b) GAP-4: AuraPaletteSync() solo corría desde el gate del AuraShell
  (NewUiGate.kt:66 + AuraShell.kt:332) → sync en la RAÍZ de MainActivity
  (`if (newUiShell) AuraPaletteSync()` dentro de echomusicTheme, idempotente
  y con clave); (c) AutoEqScreen bloom 0.40f; AuraStatsScreen/AuraSearchScreen/
  AuraMigrationScreen(×3) usaban rememberAuraBloom(mediaId=null) estático →
  semilla viva vía collectAsState de mediaMetadata (patrón:
  `by playerConnection?.mediaMetadata?.collectAsState() ?: remember {
  mutableStateOf(null) }`). Superficies NUNCA siguen la carátula (regla
  intacta). Fila 157 del REGRESSION_REGISTRY.
- Suite 769/769 verde (757 + 12 del hub, XMLs frescos). Bump v2.0.4/vc959.
- LEEME BETA-008 incluye: curva AR-SOUND v2 DEFINITIVA (125 Hz 0) — verificado
  con git log que f79fdde se commiteó DESPUÉS del build de la 007 (c6bea45),
  así que la 007 salió con v1 (125 Hz −4) y la 008 trae la buena.
- Próximo número de hallazgo nuevo: 050.

**BETA-008 ENTREGADA (2026-08-26, commits c2e1182 fixes + df7ad5a bump +
18ac051 entrega/evidencia):** v2.0.4-nosub/vc959, arm64. Build
assembleArm64FossRelease -Pnosub BUILD SUCCESSFUL 7m48s
(build-008-release.txt); verificada aapt2 (iad1tya.aura.music.dev,
arm64-v8a, 2.0.4-nosub/959) + apksigner (Verifies, v2 scheme true,
CN=Aura Hi-Res v2, SHA-256 ba82c11d… = misma keystore que 004-007 →
actualiza encima sin perder datos; evidencia en build-008-sig.txt). En la
carpeta de nube como BETA-008_Aura_v2.0.4_vc959.apk + BETA-008_LEEME.txt
(pruebas: canción completa, entrar/salir del EQ sin cortes, colores del EQ
y del resto siguiendo la portada en vivo, re-checks flip/arranque/dictado/
AR-SOUND/preamp, A/B del 047). También trae la curva AR-SOUND v2 definitiva
(125 Hz 0). VEREDICTO DEL DUEÑO PENDIENTE.

**HALLAZGO-050 (CRÍTICO, 2026-08-26): la BETA-008 CRASHEA AL ABRIR —
reporte del dueño ("ME RARRUINASTE LA APP").** `IllegalStateException: No
PlayerConnection provided` en la primera composición (uptime 1s, SM-S948B
Android 16). CAUSA RAÍZ: el sync raíz del GAP-4 (`if (newUiShell)
AuraPaletteSync()` justo tras `echomusicTheme {`, MainActivity.kt:~826)
quedó ARRIBA del `CompositionLocalProvider` que provee
`LocalPlayerConnection` (MainActivity.kt:~1602); `AuraPaletteSync()` lee
`LocalPlayerConnection.current` (AuraPalette.kt:565) y el default del
`staticCompositionLocalOf` es `error("No PlayerConnection provided")`
(MainActivity.kt:2663) → la primera composición lanza. Compiló verde y la
suite 769/769 pasó porque NINGÚN test JVM compone la UI (no hay
Robolectric/createComposeRule en el proyecto). LECCIÓN (regla 2 AGENTS en
vivo): cambios en la capa de composición exigen verificación de
composición; la suite JVM no cubre el arranque de la UI. FIX: llamada
movida DENTRO del CompositionLocalProvider (tras los provides, antes del
Scaffold) — cobertura GAP-4 intacta porque el NavHost va dentro del
provider. Fila 158 del registro. Bump v2.0.5/vc960 → BETA-009.

**BETA-009 ENTREGADA (2026-08-26, commit cd77b4e):** v2.0.5-nosub/vc960,
arm64. Suite 769/769 XMLs frescos + build BUILD SUCCESSFUL 7m54s en una
sola corrida (build-009-release.txt); verificada aapt2 (iad1tya.aura.music.dev,
arm64-v8a, 2.0.5-nosub/960) + apksigner (Verifies, v2 scheme true,
CN=Aura Hi-Res v2, SHA-256 ba82c11d… = misma keystore que 004-008;
evidencia build-009-sig.txt). En la carpeta de nube como
BETA-009_Aura_v2.0.5_vc960.apk + LEEME (paso 0: la app abre; luego las
pruebas de la 008). EL DUEÑO DEBE PROBAR LA 009, NO LA 008. VEREDICTO
PENDIENTE.

**HALLAZGO-051 ARREGLADO (2026-08-26, BETA-010):** reporte del dueño: "el
ecualizador cuando desplazo las barras se mueven las barras y a la vez la
interfaz, eso no debe ser así". CAUSA RAÍZ verificada contra las fuentes
EXACTAS de compose foundation 1.11.0 (sources jar oficial descargado en
C:\Users\AURA\Desktop\fsources\ — NO memoria de entrenamiento, que corrigió
dos errores de análisis previos: onDragStart de detectVerticalDragGestures
dispara en el SLOP, no en el DOWN; y el slop vertical es 1D, no 2D): en un
empate de slop justo el hijo gana (pass Main hijo→raíz), PERO si la página
aún se asienta de un scroll/fling previo, `ScrollableNode.startDragImmediately()`
(isScrollInProgress || overscrollEffect.isInProgress) SALTA el slop y
arrastra la página 1:1 con el dedo hasta que el slop de la barra dispara;
el congelado viejo (`enabled` flip al slop) llegaba una recomposición tarde;
y el `blockParentScroll` de la barra era PLACEBO — las conexiones nestedScroll
DESCENDIENTES nunca reciben la oferta de pre-scroll del scrollable de la
página (`ScrollingLogic.performScroll` siempre despacha dispatchPreScroll
antes de scrollarse; solo conexiones ANCESTRAS en la cadena lo reciben).
FIX (AxionEqScreen.kt): (1) guardia `freezePageScroll` — NestedScrollConnection
ANTES del verticalScroll de la página (`.nestedScroll(guard).verticalScroll(...)`):
onPreScroll traga el delta vertical y onPreFling la velocidad residual
mientras contador >0; (2) señal levantada en el DOWN del dedo (síncrono,
cero carrera): overlay del fader reescrito con awaitEachGesture +
awaitFirstDown → notifica drag-activo → awaitVerticalPointerSlopOrCancellation
+ verticalDrag consumiendo cada cambio; try/finally libera aunque el
pointerInput se reinicie a mitad de gesto; (3) contador multi-dedo
`activeSliderDrags` (la página se libera al levantar el último dedo);
PreampCard edge-triggered (`preampDragging`) porque el Slider de Material
dispara onValueChange en cada tick y repetir TRUE envenenaría el contador;
(4) el flip `enabled = !sliderDragActive` se conserva como segunda capa
(cancela el estado de gesto del scrollable + pone en cero el fling residual).
Placebo blockParentScroll + estado fingerDown ELIMINADOS. Matemática del
arrastre extraída a `gainAfterVerticalDragDelta` (top-level internal) y
fijada con `EqFaderDragMathTest` (7 tests). Pan horizontal con dedo
presionado (fila 138 del registro) INTACTO: el overlay solo consume el eje
vertical. Trampa evitada: el guard lee el estado delegado `activeSliderDrags`
directamente (no una local capturada en remember{} que quedaría congelada).
Suite 776/776 verde (XMLs frescos, build-051-tests.txt). Fila 159 del registro.
LECCIÓN: en Compose, una conexión nestedScroll solo ve los deltas si está
ANTES (ancestro) del scrollable en la cadena de modificadores; y el freeze
de un scroll padre debe levantarse en el DOWN, no en el slop, si el padre
puede estar en inercia (startDragImmediately).

**INVESTIGACIÓN FASE 2 COMPLETA Y SINTETIZADA (2026-08-26):** 4 agentes
read-only (resultados completos en la sección RESULTADOS INVESTIGACIÓN
FASE 2 del plan). Highlights: 034 — Samsung envía config_windowBlurEnabled=false
(blur de ventana no-op silencioso; isGlassSupported() solo mira sdkInt>=S,
GlassEffect.kt:127) y FrostFill alpha 0.34f asume blur (AuraPalette.kt:292-295)
= transparencia total; 12 superficies afectadas; RenderEffect sí funciona en
Samsung; precedente interno de fallback opaco en AuraShell.kt:661-730.
044 — base MediaLibraryService sólida (MusicService.kt:262, sesión :2056-2066,
callback 1102 líneas, árbol 4 categorías rootChildren :724-755); fixes ALTO:
onPlaybackResumption (hoy falla a propósito, :139) con cola persistida +
shelves raíz Recientes/Más reproducidas/Descargadas; Listas entierra
Descargadas + YouTube.home() (red) en browse :236; Car App Library NO
recomendada. 043 — gaps: G1 CRÍTICO overscan cero; G2 CRÍTICO foco D-pad
incompleto + sin foco inicial; G3 CRÍTICO postura plegables CERO
(androidx.window:1.5.0 ya es dep transitiva); G4 ALTO grids 2-col hardcodeados
(AuraAlbumGridScreens.kt:90/:222, AuraArtistItemsScreen.kt:265; patrón bueno
AuraSearchTabs.kt:647); G5 autofocus búsqueda placebo (SearchScreen.kt:137/:574-579);
G8 placebos (Items.kt:1618-1643, OfflineHome.kt:173, Queue.kt:1646); G10
comentario desfasado AxionEqScreen.kt:226-230 (dice 700dp, constante 600).
026 — RC-1 densidad TV pinnada al arranque (Utils.kt:168-190 localeAwareContext
en attachBaseContext corre una vez; configChanges incluye density|uiMode; 0
onConfigurationChanged); RC-2 blur fullscreen clásico sin downscale, TV no
excluida (Player.kt ~928/~956-989); RC-4 chrome dp fijo (AuraShell.kt:135
AuraNavBarHeight 64.dp; rail clásico se dibuja con UI nueva MainActivity.kt:1992);
RC-5 blur nuevo fullscreen + LIVE_MESH re-blurrea por fotograma
(AuraPlayer.kt:2437-2445/:2287-2290, viola regla 5 batería); RC-7 breakpoint
huérfano AlbumScreen.kt:281 (840.dp). LOS FIXES ARRANCAN SOLO CON VEREDICTO
VERDE DE BETA-010.

**BETA-010 ENTREGADA (2026-08-26, commit be66f97):** v2.0.6-nosub/vc961,
arm64. Suite 776/776 (build-051-tests.txt BUILD SUCCESSFUL 1m49s) + build
BUILD SUCCESSFUL 7m36s (build-051-release.txt); verificada aapt2
(iad1tya.aura.music.dev, arm64-v8a, 2.0.6-nosub/961) + apksigner (Verifies,
v2 scheme true, CN=Aura Hi-Res v2, SHA-256 ba82c11d… = misma keystore que
004-009). En la carpeta de nube como BETA-010_Aura_v2.0.6_vc961.apk +
BETA-010_LEEME.txt (pruebas 051: arrastrar barra con página quieta, arrastrar
JUSTO DESPUÉS de un fling de la página, preamp, scroll de página desde fuera
de las barras, dos dedos; + re-checks de la 009). EL DUEÑO DEBE PROBAR LA
010 (incluye todo lo de la 009, cuyo veredicto sigue pendiente). VEREDICTO
PENDIENTE.

**BLOQUEO GITHUB (2026-08-26):** el dueño está bloqueado de GitHub ~7 días
(hasta ~2026-09-02). Solo commits locales; nada de push, tags, CI,
player_configs.json ni Dependabot hasta que se levante.

**HALLAZGOS 052/053 + FIX 034 (2026-08-26, BETA-011 en preparación):** el
dueño probó BETA-010 en su Samsung S26 Ultra y reportó tres cosas:
- 034 RECONFIRMADO EN DISPOSITIVO ("todo me sale transparente sin blur...
  estoy con mi samsung s26 ultra"): FIX IMPLEMENTADO — `utils/WindowBlur.kt`
  NUEVO (windowBlurDecision(sdkInt, frameworkConfigEnabled),
  isWindowBlurSupported(), lectura perezosa del bool de sistema
  config_windowBlurEnabled vía Resources.getSystem().getIdentifier) +
  WindowBlurTest (3 tests); FrostFill de AuraPalette.kt ahora cae a
  compositeOver(Ground) OPACO cuando el blur de ventana no existe;
  AuraFloatingChrome.kt cambia su gate a isWindowBlurSupported().
  Fila 160 del registro.
- HALLAZGO-052 ("las barras de EQ YA NO SE PUEDEN MOVER BIEN"): REGRESIÓN
  del fix 051. CAUSA RAÍZ verificada contra las fuentes de compose ui 1.11.1
  (PointerEvent.kt extraído a C:\Users\AURA\AppData\Local\Temp\PointerEvent-1.11.kt):
  `positionChangeInternal` devuelve Offset.Zero si el cambio está consumido
  (`return if (!ignoreConsumed && isConsumed) Offset.Zero else offset`), y el
  overlay del 051 hacía change.consume() ANTES de change.positionChange().y
  → todo delta cero → el fader solo se movía el nudge del slop y se congelaba.
  FIX (AxionEqScreen.kt EqBandSlider): leer deltaY ANTES de consumir (comentario
  guardián cita PointerEvent.kt) + `sliderDriving` edge-trigger envolviendo el
  Slider de Material (el tap-to-jump dispara varios onValueChange; repetir TRUE
  envenenaría el contador activeSliderDrags y congelaría el scroll de la
  página). Fila 161 del registro.
- HALLAZGO-053 ("Y EL FFT NO SIRVE"): TRES hallazgos de código: (A) el guard
  de requestRebind() (`visualizer != null && consumer activo`) era NO-OP si el
  attach inicial fallaba — el watchdog llamaba cada 1.2s pero nunca re-attacheaba;
  (B) tryAttach tragaba excepciones (runCatching{}.getOrNull()) = cero evidencia
  en app.log; (C) el fallback a sesión 0 necesita RECORD_AUDIO en Android 14+
  (targetSdk 36) y la app solo pide micrófono para búsqueda por voz. NO es
  regresión de BETA-010 (el diff de 051 no toca la ruta FFT, verificado con
  git diff be66f97~1 be66f97). FIX (AuraVisualizerHub.kt): requestRebind
  reescrito (teardown + attachLocked incondicional bajo el mutex si hay
  consumidores), tryAttach loguea el fallo con Timber.tag(TAG).w, attachLocked
  loguea el resultado (session id + clase de excepción, sin datos de usuario).
  Si sigue muerto en el Samsung, el app.log del dueño dirá si el attach falla
  o si One UI silencia los callbacks. Fila 162 del registro.
- Suite 779/779 verde (776 + 3 de WindowBlur). Bump v2.0.7/vc962.
- PETICIÓN DEL DUEÑO CUMPLIDA: preset "AR-SOUND V2" con curva
  31 Hz +9, 62 Hz +6, 125 Hz +1, 250 Hz −1, 500 Hz −2, 1 kHz 0, 2 kHz +1,
  4 kHz 0, 8 kHz +2, 16 kHz +3 (va junto al AR-SOUND existente, NO lo
  reemplaza). Entrada AR_SOUND_V2 en FactoryPreset + test
  arSoundV2CurveMatchesOwnerSpec.
- PETICIÓN DEL DUEÑO CUMPLIDA: preset "Aura Hi-Res" (31 +6, 62 +4, 125 +1,
  250 −1, 500 0, 1k 0, 2k +1, 4k 0, 8k +1, 16k +2) Y default desde el primer
  inicio: la semilla de migrateAudioDefaultsV2 (App.kt) ahora usa
  FactoryPreset.AURA_HI_RES.gains (antes AUDIOPHILE). SOLO instalaciones
  nuevas (gate AudioDefaultsV2AppliedKey corre una vez); el teléfono del
  dueño (instalación existente) conserva su EQ — toca el chip una vez
  (documentado en el LEEME). Test auraHiResCurveMatchesOwnerSpec. Fila 163.
- PETICIÓN DEL DUEÑO VERIFICADA (sin cambios): "el streaming en lo más alto
  posible siempre" — cadena de 5 eslabones toda al máximo: OPUS lock en
  PlayerSettings (pickers Saavn/Qobuz removidos), fallback OPUS en
  MusicService/Data Saver, findFormat maxByOrNull{bitrate} con opus ×2.0
  (itag 251 gana), SIN techo de audio en red medida (auditoría L10),
  AUDIO_ITAG_PREFERENCE 251 primero. Techo real = Opus YouTube ~160 kbps.
- BETA-011 ENTREGADA (v2.0.7-nosub/vc962, arm64, 43,758,940 bytes): 034 +
  052 + 053 + AR-SOUND V2 + Aura Hi-Res. Suite 781/781 (build-011-tests2.txt)
  + build 7m14s (build-011-release2.txt) + aapt2 + apksigner (CN=Aura Hi-Res
  v2, SHA-256 ba82c11d…). En la carpeta de nube como
  BETA-011_Aura_v2.0.7_vc962.apk + LEEME.
- Próximo número de hallazgo nuevo: 054. Próxima fila del registro: 164.

**VEREDICTO BETA-011 + HALLAZGO-054 ARREGLADO + PREAMP +2 (2026-08-26):** el
dueño probó BETA-011 en el S26 Ultra (One UI 8.5):
- 051/052 PERSISTEN: "Y CUANDO MUEVO LAS BARRAS DEL ECUALISADOR TODO SE
  MUEBE" → HALLAZGO-054. CAUSA RAÍZ verificada contra las fuentes REALES de
  androidx-main (fetch web, guardadas en ~/.qwen/tmp/…/tool-results/:
  Scrollable.kt, Draggable.kt, HitPathTracker.kt, DragGestureDetector.kt,
  Slider.kt de material3): (1) el verticalScroll de la página en foundation
  1.11 es un DragGestureNode del sistema unificado con slop 2-D
  (TouchSlopDetector) vs el slop solo-vertical del overlay → el arrastre en
  diagonal cruza el slop de la página PRIMERO; (2) la página reclama y
  consume → el await de slop del overlay cancela → su finally bajaba el
  contador ANTES de que los deltas asíncronos de la página (canal) llegaran a
  performScroll→dispatchPreScroll → freezePageScroll veía 0 → scrolleaba;
  (3) AwaitGesturePickupState re-reclama CUALQUIER evento sin consumir
  mientras haya un dedo abajo (el perdedor no muere: espera); (4) el Slider
  de Material monta press+draggable en su propio root (Slider.kt 1320/1405)
  = segundo escritor (tap-to-jump). DESCARTADA la hipótesis inicial: consumir
  el DOWN NO bloquea a la página (DragGestureNode detecta el down con
  requireUnconsumed=false, Draggable.kt 824). FIX = BARRERA en el Box del
  fader (AxionEqScreen.kt EqBandSlider): awaitFirstDown(requireUnconsumed=
  false, pass=PointerEventPass.Initial) + consume (mata press/tap del
  Slider), contador arriba en el DOWN físico, bucle que consume CADA cambio
  del puntero rastreado en el pase Main hasta el UP físico (la página,
  ancestro, siempre lo ve consumido: sin slop, pickup muere de hambre),
  contador baja SOLO en el UP físico. El overlay conserva solo la escritura
  de valor (más profundo = lee deltas sin consumir; #52 intacto).
  freezePageScroll + flip enabled quedan como defensa en profundidad
  (settling consume en Initial y enmascara; flings vía onPreFling). Efecto
  lateral aceptado: sin thumb-grow ni tap-to-jump en las bandas. PreampCard
  (horizontal) y PEQ intactos. LECCIÓN: la capa de punteros no se prueba con
  la suite JVM (filas 158/050); el dispositivo es la verdad. Fila 164.
- PREAMP +2.0 dB (dueño: "+3 es mucho"): semilla migrateAudioDefaultsV2 ahora
  en +2.0 + mirror echo_eq_prefs, migración one-time migrateEqPreampDefaultV4
  (llave EqPreampDefault2DbAppliedKey, corre DESPUÉS de la V3, baja
  exactamente +3.0/NaN → +2.0 solo a quien sigue en el default, estampado en
  dos fases, reintento si no hay estado EQ), fallbacks 2f (AxionEqViewModel +
  MusicService). El teléfono del dueño baja solo a +2.0 con la próxima beta.
  Fila 165.
- FFT: el app.log del dueño CONFIRMA que el logging de 053 funciona —
  `RuntimeException: Cannot initialize Visualizer engine, error: -3`
  (ERROR_NO_INIT) en AMBOS caminos (sesión y fallback 0). Hipótesis:
  RECORD_AUDIO no concedido (Android 14+ lo exige para Visualizer) vs
  bloqueo Samsung. Prueba barata: pedirle revisar el permiso de micrófono.
- Blur en One UI 8.5: dijo "los diseños de blur en one ui 8.5" — pendiente
  preguntarle qué ve exactamente (¿opaco?, ¿transparente?, ¿qué pantallas?).
- Canciones lentas: rotación de bot-check ~3.5s + reintentos RESOLVE_TIMING;
  la vía rápida player_configs.json está bloqueada hasta que GitHub vuelva.
- PETICIÓN NUEVA: auditoría de caché/ahorro de datos (portadas, portadas
  animadas, canciones, videos; estilo Spotify/YouTube/YT Music) — 2 agentes
  Explore en paralelo, RESULTADOS PENDIENTES.
- Suite 781/781 verde con la barrera + preamp +2. SIGUIENTE: BETA-012
  (v2.0.8-nosub/vc963).

**BETA-012 ENTREGADA (2026-08-27):** la sesión del 2026-08-26 se cortó con el
build interrumpido (murió en KSP, sin APK) y los 2 agentes de la auditoría
caché perdidos sin resultados. Reanudación 2026-08-27: rebuild
assembleArm64FossRelease -Pnosub BUILD SUCCESSFUL 4m35s
(build-012-release.txt); verificada aapt2 (iad1tya.aura.music.dev, arm64-v8a,
2.0.8-nosub/963) + apksigner (Verifies, v2 scheme true, CN=Aura Hi-Res v2,
SHA-256 ba82c11d… = misma keystore que 004-011; evidencia build-012-sig.txt).
En la carpeta de nube como BETA-012_Aura_v2.0.8_vc963.apk (43,758,704 bytes)
+ LEEME (pruebas: faders con página quieta/tras fling/en diagonal + scroll de
página desde fuera de las barras, preamp +2, A/B permiso de micrófono para el
FFT, blur en One UI 8.5, re-checks de la 011/010). Commit evidencia c6ce92b
(local). DECISIÓN: las ganancias de caché van a BETA-013 (FASE 6) para no
retrasar el veredicto de los faders. Auditoría caché RE-LANZADA con 2 agentes
Explore en paralelo (imágenes/portadas/videos por un lado; audio/streams/Data
Saver por el otro), RESULTADOS PENDIENTES. VEREDICTO DEL DUEÑO PENDIENTE.

**Why:** el dueño pidió el 2026-08-25 un plan por fases para recolectar estos
problemas, investigar las soluciones y reparar todo sin romper nada, guardando
siempre el estado en la memoria del proyecto ("hasta el final").
**How to apply:** al reanudar, leer el YAML `estado_actual` al final de
`PLAN RONDA 3 REPORTES DEL DUEÑO.md` (fuente autoritativa de la RONDA 3) y
continuar desde la fase indicada. Ahora: BETA-012 ENTREGADA (2026-08-27,
commit evidencia c6ce92b) con HALLAZGO-054 (barrera de gesto en los faders)
+ preamp default +2.0 dB (migración V4), suite 781/781 — ESPERAR VEREDICTOS:
(1) faders — arrastrar
con página quieta, tras un fling y en diagonal: la página NO debe moverse;
el scroll de página desde fuera de los faders debe funcionar; (2) preamp
amanece en +2; (3) pendientes de la 011: blur en One UI 8.5 (preguntarle qué
ve exactamente), canciones lentas, FFT (pedirle revisar el permiso de
micrófono de la app como A/B barato; su app.log ya dio error -3 en ambos
caminos); (4) pendientes de la 010: 051, 048, 049, 046 oído, 047 A/B. Si
verde: cerrar 054 y arrancar los fixes restantes de FASE 2 (044
onPlaybackResumption + shelves raíz; 043 gaps G1-G3; 026 RC-1/RC-2),
leyendo PRIMERO docs/UI_INVENTORY.md (regla 5 de AGENTS). Si rojo: pedir
app.log y otra pasada. EN PARALELO: integrar los resultados de la auditoría
caché/ahorro de datos (2 agentes re-lanzados 2026-08-27, resultados
pendientes) como fixes de FASE 6 en BETA-013 (lo SEGURO primero; suite +
build + verificación antes de cortar). Commits SIEMPRE
locales ("Audit: RONDA 3 - ..." en inglés, SIN push — GitHub bloqueado hasta
~2026-09-02; el dueño lo reconfirmó: "todos los comit hazlo local").
048/049/050/051 ARREGLADOS (filas 156/157/158/159 del registro). 046
implementado (falta confirmación de oído). 047 (canciones inician cortadas)
INVESTIGADO: esperar del dueño el log (ev=cut-not-ready) y el A/B con
SponsorBlock OFF; fix condicionado a la evidencia (Fix A guardia de inicio /
Fix B latencia de resolves tempranos). Si BETA-010 verde: cerrar
048/049/050/051 y lo confirmado de la 006/007, y arrancar los FIXES de
FASE 2 desde la tabla RESULTADOS INVESTIGACIÓN FASE 2 del plan (prioridades:
034 fallback opaco de blur; 044 onPlaybackResumption + shelves raíz; 043
gaps G1-G3 críticos; 026 RC-1/RC-2), leyendo PRIMERO docs/UI_INVENTORY.md
(regla 5 de AGENTS). YA NO hay preguntas pendientes: 026b respondida
(Native 100%), 033 decidido (REBAUTIZAR sin favoritos reales), dispositivos
confirmados (TV TCL Google TV + Fold 7 + S25 Ultra + carro con Android Auto).
Si rojo: otra pasada con el log nuevo. Próximo número de hallazgo nuevo: 055.
Próxima fila del registro: 166. Próxima beta: BETA-012 (v2.0.8/vc963).
LECCIÓN PERMANENTE (050): los cambios en la capa de composición se
verifican en pantalla real o con tests de composición; la suite JVM no
cubre el arranque de la UI. LECCIÓN PERMANENTE (051): en Compose, una
conexión nestedScroll solo ve los deltas si está ANTES (ancestro) del
scrollable en la cadena de modificadores; el freeze de un scroll padre debe
levantarse en el DOWN, no en el slop, si el padre puede estar en inercia.
LECCIÓN PERMANENTE (054): la capa de punteros de Compose tampoco se puede
probar con la suite JVM; el dispositivo es la verdad. En foundation 1.11 el
verticalScroll es un DragGestureNode con slop 2-D y un estado de pickup que
re-reclama eventos sin consumir: la defensa fiable de un gesto hijo es
consumir CADA cambio del puntero durante TODA la pulsación física (barrera
en el pase Main, DOWN consumido en el pase Initial), y atar cualquier
guardia de scroll a la pulsación física (DOWN..UP), NUNCA al éxito de la
detección del gesto.
Pistas paralelas que NO se mezclan: 021 Fase B (decisión del dueño), publicar
la estable v2 (pre-publish-check + CI verde + permiso), Dependabot.

---

## Estado 2026-08-27 (post-BETA-012, sesión YOLO) — fixes BETA-013 en curso

**Modo YOLO activo:** el dueño ordenó "ponte en modo YOLO QUIERO QUE SEAS FULL
AUTONOMO" — autonomía total, sin confirmar acciones locales. Push a GitHub sigue
BLOQUEADO hasta ~2026-09-02. Commits locales siempre.

**Veredicto BETA-012 del dueño (parcial):**
- 054 faders + preamp +2 dB: veredicto PENDIENTE (no lo mencionó).
- 034 blur: "SE VE OPACO Y TRANSPARENTE SIN BLUR" → reabierto parcial. Agente
  investigando (task general-purpose-call_e16c897781df48e891e103a7).
- HALLAZGO-028 login: hay que darle "Accede" a la interfaz de YouTube en el
  WebView, luego atrás, y la biblioteca tarda DEMASIADO; quiere carga instantánea
  como SimpMusic (fork local en C:\Users\AURA\Desktop\PROYECTOS DE PROGRAMACION\
  AURA FENIX\aura-simpmusic). Agente investigando
  (general-purpose-call_5a0ed6c7676045baa8fc5234).
- HALLAZGO-035 exportar como video no funciona. Agente investigando
  (general-purpose-call_b152f400199e4bee8dcd48c2).
- 055 jank: "la fluidez de las animaciones se perdieron, animaciones trabosas".
  Agente investigando (general-purpose-call_162e38046c204cda8d8fe23a).
- 053 FFT "sigue sin señal" → causa raíz CONFIRMADA en su log y ARREGLADA (abajo).
- ORDEN: "luego de esto continuas con la fase B" = 021 FASE B aprobada
  (extraer video mode/crossfade con colaboradores con estado). Va DESPUÉS de los
  fixes de BETA-013.

**Análisis del log BETA-012 (C:\Users\AURA\Downloads\log de aurav2\aura_feedback (1).txt,
6267 líneas):**
- FFT/053 causa raíz: Android 14+ (API 34+) exige RECORD_AUDIO para CUALQUIER
  Visualizer init (error -3 = ERROR_NO_INIT); 367 attaches fallidos en SDK 36.
- Crossfade de audio SANO: `CROSSFADE_TRACE ev=swap-ok blend running` LINEAR 5.0s;
  NO_REPEAT transition = 1 línea por arranque (MusicService.kt:1728). Lo "roto"
  percibido en transiciones = HALLAZGO-056.
- HALLAZGO-056 NUEVO (registrar fila 166 al cerrar): tormenta de resolves bajo
  429 rateLimitExceeded + bot-check PipePipe — misma canción arranca 4 veces en
  11s; RESOLVE_FAIL con InterruptedException / Parent job is Cancelling /
  Timed out 30000 ms / scope left composition (TVHTML5); `E/PLAYBACK: Playback
  failed`. Sospechosos: retry sin backoff + resolve atado a scope de Compose.
  Agente trazó el bucle (general-purpose-call_3dc93b6d990648cca30f8d66), informe
  pendiente.
- Fetch de avisos del dueño cada 27-61s (throttle force inexistente) → corregido.

**Commits de esta sesión (locales):**
- `4ee0af4` — fix FFT/053 v2: AuraVisualizerHub needsPermission StateFlow +
  recordAudioGrantedProvider + gate visualizerCaptureAllowed (función pura, SDK 34
  = UPSIDE_DOWN_CAKE) + permiso RECORD_AUDIO pedido desde AxionEqScreen
  (launcher + hint ámbar "Requiere micrófono — toca para permitir") + re-arm
  instantáneo en onPermissionGranted. +3 tests (suite 781→784).
- `eeb505e` — fixes caché/datos (037): (1) onTrimMemory de MusicService YA NO
  borra el disk cache de Coil (causa de re-descarga de portadas; solo memoryCache
  trim); (2) auto-download-on-like solo en red no medida (wifiOnly=true en
  MusicService.toggleLike y AutoDownload.kt; gate ConnectivityManager en
  SongDownloadActions; media3 1.10.1 NO tiene setRequiresUnmeteredNetwork —
  verificado con javap sobre el AAR); (3) OwnerAnnouncements: throttle force de
  5 min (FORCE_MIN_REFRESH_MS) con merge local sin red. +4 tests (suite → 788 en
  la próxima corrida completa; última corrida completa: 784/784 con --rerun).
- `716fdf5` — fixes caché H2/H3/H4 (auditorías imágenes+audio): URLs canónicas de
  portada (2 buckets 544/1200 dentro de resize() — elimina hasta 6 llaves de caché
  duplicadas por portada; variantes de DB 544 y cola 1200 intactas), artist video
  con caché LRU de disco 128 MB + gate de red medida (sin video autoplay en datos
  móviles; fallback a imagen estática), canvas ya no fuerza el bitrate más alto en
  red medida (cap 1280), prewarm de portadas del player limitado a actual+4 en vez
  de toda la cola. +21 tests: app 807/807, artistvideo 2/2 (módulo estrena suite).
  DIFERIDOS por bajo impacto: H5 (metadatos canvas solo RAM) y H6 (fallback de
  CoilBitmapLoader sin caché). NOTA: el informe de la auditoría de audio se hizo
  contra GitHub main (atrás del árbol local): su H0/H1/H2 ya estaban resueltos
  localmente (eeb505e + circuit breaker de 8414363).

**Próximos pasos (en orden):**
1. Recibir/reclamar los 5 informes de agentes (task_ids arriba; si no llegan como
   notificación, reenviar send_message pidiendo el informe).
2. Implementar fixes de BETA-013 con TDD: blur 034, 028 login instantáneo,
   035 export video, 055 jank, 056 backoff/dedup de resolves + fixes caché
   restantes priorizados (H2 URLs portada, H3 artist video sin CacheDataSource).
3. 021 FASE B (orden del dueño): extraer video mode/crossfade con colaboradores
   con estado.
4. Entregar BETA-013 (v2.0.9/vc964): suite completa verde (--rerun, XMLs frescos)
   → bump → build Corretto assembleArm64FossRelease -Pnosub → verificar
   aapt2.exe/apksigner (CN=Aura Hi-Res v2) → copiar a "C:\Users\AURA\Desktop\
   BETA OFICIAL  DE AURA FENIX\" como BETA-013_Aura_v2.0.9_vc964.apk + LEEME →
   commit local → actualizar PLAN RONDA 3, REGRESSION_REGISTRY (fila 166) y
   memoria.

**Números:** próximo hallazgo nuevo = 057; próxima fila registro = 166;
próxima beta = BETA-013 (v2.0.9/vc964). Suite actual = app 807/807 verde +
artistvideo 2/2 (XMLs frescos con --rerun, 2026-08-27).

---

## Estado 2026-08-27 (BETA-013 ENTREGADA) — 021 FASE B siguiente

**BETA-013 ENTREGADA** en "C:\Users\AURA\Desktop\BETA OFICIAL  DE AURA FENIX\"
como BETA-013_Aura_v2.0.9_vc964.apk (43 764 632 bytes) + BETA-013_LEEME.txt
(6 secciones qué trae + 8 pruebas + conocidos/pendientes). Build
assembleArm64FossRelease -Pnosub BUILD SUCCESSFUL 4m17s; verificada aapt2
(iad1tya.aura.music.dev, arm64-v8a, 2.0.9-nosub/964) + apksigner (CN=Aura
Hi-Res v2, SHA-256 ba82c11d… = misma keystore que 004-012). Suite 862 verde
(app 833 + innertube 27 + artistvideo 2, XMLs frescos).

**Fixes commiteados (locales):**
- `37ec2fe` — 034 segunda vuelta: H1 velos sin blur opacos de verdad
  (auraFloatingScrimAlpha 0.22→0.88, auraDialogDimAmount 0.22→0.60 cuando
  !windowBlurSupported); H2 windowBlurDecision(sdkInt, config, manufacturer):
  Samsung = no soportado (One UI no expone config_windowBlurEnabled, null no
  es "soportado"); log de decisión 1 vez/proceso.
- `978ade4` — 028 login: retryableInnerTubeError (IOException o
  ServerResponseException 5xx transient; 4xx/cancelación finales) en withRetry;
  accountMenu initialDelay 1000ms; LoginScreen delay 500→1500ms post-sign-in.
- `1e7debb` — 055 jank: bloom rasterizado 1 vez/pista a bitmap ≤1080px + blit
  (AuraBloom); visualizerRebindAllowed tope 3 ciclos de attach fallidos, solo
  reabre con sesión nueva/permiso (AuraVisualizerHub); EqFftSnapshot.Zero
  singleton vía eqFftConvergedToZero (AxionEqScreen).
- `fe8fd9f` — 056 + 035: coalescencia single-flight en SyncUtils
  (shouldStartSyncOp/shouldStartFullSync + inFlightOps; OP_FULL_SYNC_DOWN/UP
  como llaves composite para que un upload NUNCA sea tragado; pulls
  individuales esperan el full sync; ops de escritura nunca coalescen) +
  higiene de logs (isBenignResolveCancellation: CancellationException/
  InterruptedException → debug, no persistido como "Playback failed"); 035
  videoStreamUrlForExport (NewPipe ANDROID_VR primero en Dispatchers.IO,
  fallback diag) cableado en sonda 30s/exportVideo 1080p/DownloadUtil.
- `4797697` — REGRESSION_REGISTRY filas 166-171 (todas ⚠️ pendiente
  dispositivo salvo 170 ✅).
- `ff4644b` — bump v2.0.9/vc964. `ae0bb4f` — evidencia (plan doc +
  build-013-release/badging/sig/fullsuite). `bb53c64` — logs intermedios TDD.

**Causa raíz 056 (verificada):** performFullSyncSuspend()/*Suspend() puentean
la cola serial de SyncUtils → tras login, 3 escritores paralelos (4 browses de
FEmusic_library_privately_owned_tracks en 31ms) → 429 → biblioteca en error.
FALSO descartado: "re-enqueue infinito YTPlayerUtils:1509" (no existe).

**Veredictos pendientes del dueño (BETA-013):** blur Samsung (¿ya se ve
sólido/blur?), login sin ritual "Accede"+atrás, biblioteca instantánea,
fluidez de animaciones (055), export video (035). RE-CHECKS de BETA-012 que
no mencionó: faders 054 y preamp +2 dB. Pendiente histórico: si concedió el
permiso de micrófono para el FFT.

**SIGUIENTE (orden explícita del dueño: "luego de esto continuas con la
fase B"):** 021 FASE B — extraer video mode y crossfade de MusicService.kt
(~11 451 líneas) como COLABORADORES CON ESTADO en playback/ (p. ej.
CrossfadeCoordinator/VideoModeCoordinator). Método: characterization tests
PRIMERO, luego extracción verbatim, suite verde + build antes de dar por
bueno. INVARIANTES: curva 4 del crossfade NO se toca (afinada por el dueño
en 3 rondas); reproducción intocable; NUNCA refactor en caliente. Anclas
grep ya recolectadas en MusicService.kt: crossfadeEnabled :312;
secondaryPlayerListener :329-347; secondaryPlayer :676; prefs collector
:2537-2660; EQ safe-volume swap :5064-5070; release secondary :9747-9753;
región scheduleCrossfade :10159-10524 (keepPreload :10159-10191, HP
force-disable :10194-10201, prepareSecondaryPlayer :10264, CROSSFADE_TRACE
:10345-10348, gate :10429, secondaryPlayer null :10486); videoUrlCache
(expiry 5 min) :7823/:7883/:8070/:8093/:8155/:8186; pre-resolve siguiente
:8032-8093; speculative actual :8119-8186; canvas preload guards
:8705-8811. Agente Explore del mapeo completado (task
Explore-call_ff107dafee66499f9b8fe41c) — informe re-emitido pendiente de
recibir; VERIFICAR sus claims contra el código local (regla 3) antes de
implementar.

**Números ACTUALIZADOS:** próximo hallazgo = 057; próxima fila registro =
172; próxima beta = BETA-014 (v2.0.10/vc965). Suite = 862 verde. Commits
locales acumulados: 41 por delante de origin/main (SIN push hasta ~2026-09-02).

**Candidatos BETA-014 (si el veredicto BETA-013 sigue rojo en esos frentes):**
- 028 biblioteca aún lenta: B1 feedback visible "Sincronizando tu biblioteca…"
  (los syncs post-login son fire-and-forget y la UI no muestra estado) y B2
  fast-path de primera página (insertar solo la 1ª página de cada endpoint al
  primer login, paginación completa al Worker). NO entraron a BETA-013 (solo
  A1 retry 5xx + A2 delay 1500ms + reloj account_menu).
- 056 tormenta: gate hasExceededRetryLimit en la rama 403/expired de
  onPlayerError (verificado ausente en MusicService.kt:7044-7048) + backoff
  por videoId tras RESOLVE_FAIL.

**VEREDICTO BETA-013 (2026-08-27):** el dueño reportó 2 síntomas nuevos y
confirmó que 054 seguía vivo en v2.0.9 con los CUATRO síntomas (página se
mueve, barra no sigue el dedo, casi no responde, no salta al tocar) →
HALLAZGO-054 REABIERTO (2ª vuelta) + HALLAZGO-057 nuevo (portadas de video
deben rellenar su carátula). Transiciones verificadas SANAS con evidencia
del log (crossfade swap-ok; anomalía 07:39-07:42 = tormenta YouTube,
territorio 056).

**HALLAZGO-054 2ª vuelta ARREGLADO (fila 172, commit 1fdf789):** REDESIGN
COMPLETO del fader. La barrera de la fila 164 fue el 6º PLACEBO (verificada
contra la rama de desarrollo de androidx en vez de foundation 1.11.0; tres
mecanismos de gesto en competencia). Corolario adoptado: mecanismo que falla
3 rondas (051/052/054) no se parchea, se REEMPLAZA. EqBandSlider nuevo:
UN pointerInput a medida con MAPEO ABSOLUTO Y→ganancia (pura gainForFaderY,
+8 tests EqFaderMathTest; gainAfterVerticalDragDelta y EqFaderDragMathTest
eliminados); escribe valor en el DOWN (tap-to-jump, respuesta inmediata);
sin slop ni acumulación de deltas; consume DOWN+cambios en AMBAS passes
(Initial y Main) toda la pulsación física; contador de congelado atado al
down..up físico en try/finally. Eliminados: Slider de Material rotado,
overlay, barrera, sliderDriving. Muro page-level freezePageScroll + flip
enabled CONSERVADOS como segunda pared. Visual: track 4dp + relleno desde
abajo + thumb circular 20dp insetado. PreampCard y PEQ intactos.

**HALLAZGO-057 ARREGLADO (fila 173, mismo commit):** regla única — si el
ítem es video (isVideoSong/isVideo) → SIEMPRE ContentScale.Crop,
independiente del ajuste "Recortar las portadas"; arte de álbum intacto.
8 sitios en 6 archivos (mapa de agente Explore verificado sitio por sitio en
local): Thumbnail.kt (ThumbnailItem.currentIsVideoSong + metadata por ítem
del swipe), AuraQueue.kt (AuraCover.fillBleed en 4 callers: sonando/cola/
automix/TV), Items.kt (ItemThumbnail.fillBleed; YouTubeGridItem propaga
ratio+fillBleed solo para SongItem.isVideoSong; SongListItem
fillBleed=song.song.isVideo), AuraPlayer.kt ×2 (letras + overlay
pre-primer-frame), AuraLyricsScreen.kt, Player.kt. Shelves Aura 16:9 ya
recortaban — intactos. OJO: resize() de YouTubeUtils fuerza ytimg→hqdefault
4:3 (fuente de la imagen, out of scope).

**BETA-014 ENTREGADA (2026-08-27, commits 1fdf789 fixes+registro+bump /
017000d evidencia):** v2.0.10-nosub/vc965, arm64. Suite 863/863 verde
(app 834 + innertube 27 + artistvideo 2, XMLs frescos --rerun;
build-014-tests.txt). Build assembleArm64FossRelease -Pnosub BUILD
SUCCESSFUL 3m47s (build-014-release.txt); verificada aapt2
(iad1tya.aura.music.dev, arm64-v8a, 2.0.10-nosub/965) + apksigner
--verbose (Verifies, v2 scheme true, CN=Aura Hi-Res v2, SHA-256
ba82c11d… = misma keystore que 004-013). En la carpeta de nube como
BETA-014_Aura_v2.0.10_vc965.apk (43,765,728 bytes) + LEEME_BETA-014.txt
(4 pruebas: faders con 1 dedo —salta al tocar/sigue el dedo/página quieta/
scroll desde fuera—; portadas de video rellenan; portadas de álbum
intactas; crossfade intacto). VEREDICTO DEL DUEÑO PENDIENTE.

**Números ACTUALIZADOS:** próximo hallazgo = 058; próxima fila registro =
174; próxima beta = BETA-015 (v2.0.11/vc966). Suite = 863 verde. Commits
locales acumulados: 43+ por delante de origin/main (SIN push hasta
~2026-09-02).

**SIGUIENTE (orden explícita del dueño: "luego de esto continuas con la
fase B"):** 021 FASE B — REANUDADA. Método: núcleos puros + characterization
tests PRIMERO (CrossfadePlanning.kt: keepPreload/triggerTime-tailHint/
READY-wait 800ms/durOut=minOf(configured,(fpRemaining-250).coerceAtLeast(600))/
tail tiers fireWindowMs=crossfadeDuration+4000/7s/250ms), luego video mode,
luego colaboradores con estado (CrossfadeCoordinator/VideoModeCoordinator,
extracción verbatim), suite verde + build + commit. Las 5 regiones de
decisión YA leídas y verificadas en scheduleCrossfade (MusicService.kt
10128-10360). CrossfadeMath.kt YA extraído con la curva 4 del dueño (NO
tocar). Config del dueño: crossfade ON/5.0s.

**021 FASE B PASOS 1-3 COMPLETOS (2026-08-27, commits fc02649 refactor +
04a3c2e plan):**
- Paso 1 — `playback/CrossfadePlanning.kt`: núcleo puro Android-free con las
  decisiones de planificación del crossfade (keepPreload, triggerTime/tailHint,
  READY-wait 800ms, durOut=minOf(configured,(fpRemaining-250).coerceAtLeast(600)),
  tail tiers fireWindowMs). 29 characterization tests verdes.
- Paso 2 — `playback/VideoModePlanning.kt`: núcleo puro con las decisiones del
  modo video (decideApply, gating de export/muxed, alturas). 19 tests verdes +
  todos los wirings en el servicio.
- Paso 3 — `playback/VideoModeCoordinator.kt` (746 líneas):
  `class VideoModeCoordinator(private val service: MusicService)`. Extracción
  VERBATIM del núcleo del modo video: toggleVideoMode, enterVideoModeIfNeeded/
  Internal, isVideoDownloadComplete, exportedMuxedVideoUri, scheduleVideoStuck
  RecoveryCheck, maybeRecoverStuckVideo, applyVideoToCurrent, disarmVideoMode
  KeepAudio, swapToVideo, restoreVideoTracksExcept, prebuildNextVideoItem,
  prefetchCurrentVideoUrl, exitVideoMode, pauseOfflineDownloadsForVideoPlayback.
  ESTADO movido: videoSwapGeneration (AtomicInteger), speculativeVideoPrefetches,
  preloadedVideoOriginalUris, newPipeMuxedVideoIds (synchronizedSet),
  VideoTrackState (data class), videoModeItems (ConcurrentHashMap), prebuildingIds,
  videoStuckRecoveryAttemptedAt, videoStuckRecoveryJob, videoModeMaxHeight.
  22 edits (E1-E22) a MusicService.kt: delegates + flips a `internal` + shims
  delegantes para la API pública (`toggleVideoMode()`, `enterVideoModeIfNeeded()`,
  `exitVideoMode()`) + 19 call-sites prefijados `videoCoordinator.`.
- PATRÓN VALIDADO: colaborador con estado recibe `service`; funciones movidas
  verbatim con prefijo `service.`; miembros necesarios del servicio → `internal`;
  el compilador es la red de seguridad; shims mantienen la API pública intacta
  (PlayerConnection y demás callers no cambian).
- SE QUEDÓ EN EL SERVICIO (y por qué): instant-swap dual-player, createMedia
  SourceFactory e instrumentación videoSwapMark/MeasureStart — reasignan o
  tocan el ciclo de vida del `player`. `videoUrlCache` se queda en el companion
  de MusicService porque DownloadUtil.kt:205 lo lee estáticamente
  (MusicService.videoUrlCache).
- DIFERIDOS CON EVIDENCIA: CrossfadeCoordinator — beginCrossfadeSwap/
  performCrossfadeSwap reasignan `player`/`mediaSession.player` y espejan la
  mitad de onMediaItemTransition; NO es un subsistema, es un camino de
  cambio-de-pista dual. Instant-swap dual-player se queda por la misma razón.
  El núcleo de video NUNCA reasigna `player` (rebuild in-place con
  replaceMediaItem) → por eso SÍ fue extraíble.
- Compile-fix: 2 imports faltantes en el coordinador —
  `iad1tya.echo.music.extensions.metadata` (extensión MediaItem.metadata,
  extensions/MediaItemExt.kt:17) y `kotlinx.coroutines.flow.first`
  (dataStore.data.first()). MusicService.kt compiló limpio al primer intento
  (validó los 22 edits).
- VERIFICACIÓN: suite completa 911/911 verde en XMLs frescos con --rerun
  (863 base + 29 CrossfadePlanning + 19 VideoModePlanning). Build
  assembleArm64FossRelease -Pnosub BUILD SUCCESSFUL 3m16s
  (build-faseb-build.txt). MusicService.kt 11,435 → 10,738 líneas.
- Commits: fc02649 (refactor, 6 archivos, +1851/−837; los núcleos de pasos 1-2
  estaban untracked y entraron aquí) + 04a3c2e (plan RONDA 3 actualizado).
  git status limpio (solo untracked los logs build-faseb-*.txt, NO se commitean).

**Números ACTUALIZADOS:** próximo hallazgo = 058; próxima fila registro =
174; próxima beta = BETA-015 (v2.0.11/vc966). Suite base = 911 verde. Commits
locales acumulados: 46 por delante de origin/main (SIN push hasta ~2026-09-02).

**SIGUIENTE:** esperar veredicto del dueño sobre BETA-014 (faders 054 +
portadas video 057). Si reporta fallos, prioridad sobre todo. Si verde,
decidir si FASE B cierra aquí o se continúa FASE 2 (026/034/043/044).

**HALLAZGO-058 ARREGLADO (2026-08-27, fila 174, entra en la ESTABLE):**
reporte del dueño sobre BETA-014: "cuando activo la rotación de pantalla y
giro el celular hay micro cortes en las canciones" (solo música, interfaz
nueva). CAUSA RAÍZ CONFIRMADA con el log que envió el dueño
(C:\Users\AURA\Downloads\log de aurav2\aura_feedback (2).txt — es de la
BETA-014 aunque dijo "beta 13"): MainActivity compone BottomSheetPlayerHost
en DOS posiciones (línea 1793 bottomBar con !showRail, línea 1932 rama
rail/wide; showRail = (isLandscape || isWideLayout) && !inSearch &&
route != ambient, MainActivity.kt:977) — al rotar, showRail flipea, un
AuraPlayer se desecha y el otro se compone en el MISMO frame, el consumidor
RHYTHM del Visualizer flipea off→on y el código viejo del hub hacía teardown
INMEDIATO a cero consumidores: release + re-attach del Visualizer sobre la
cadena de audio viva = micro-corte audible. Evidencia del log: 10 ×
"Visualizer attached (session=602649)" en 12s (13:17:06-18) sin cambio de
pista; cadencia ~1s descarta requestRebind (throttle 10s) → es el flip del
consumidor. Solo afecta interfaz nueva (la clásica no usa Visualizer) con
permiso de micrófono concedido. FIX: ventana de gracia de teardown de 1.5s
en AuraVisualizerHub (TEARDOWN_GRACE_MS; job diferido bajo el mismo Mutex,
cancelado si vuelve un consumidor — la rotación cuesta CERO operaciones
binder) + rebind inmediato SOLO si cambió la sesión de audio
(visualizerSessionRebindNeeded, caso crossfade dual-player). Funciones puras
visualizerTeardownDue/visualizerSessionRebindNeeded + 4 tests nuevos (hub
20/20). LECCIÓN registrada: un booleano de consumidor no distingue "me fui"
de "me mudé"; todo teardown derivado de composición necesita ventana de
gracia.

**ESTABLE v2.0.10/vc966 ENTREGADA (2026-08-27, commit 2262b9e):** el dueño
pidió "la versión estable con la licencia funcionando correctamente" (todas
las betas fueron -Pnosub). Pipeline completo VERDE:
- Bump versionCode 965→966; versionName 2.0.10 sin sufijo = estable.
- RELEASE_INFO.md REESCRITO como changelog consolidado de la primera estable
  v2 (todo viñetas para changelog.json; título línea 1).
- Suite completa 915/915 con --rerun (911 + 4 tests del hub por el 058).
- Build `assembleUniversalGmsRelease` SIN -Pnosub = artifact EXACTO del CI:
  universal (4 ABIs, 87.4 MB), GMS, CON licencia. Verificado: aapt2
  (iad1tya.aura.music —paquete estable, NO .dev—, 2.0.10/966, label
  "Aura Hi-Res v2"), apksigner (Verifies, CN=Aura Hi-Res v2, SHA-256
  ba82c11d…), BuildConfig (REQUIRE_SUBSCRIPTION=true,
  SUPERPOWERED_LICENSE_BOUND=true), pre-publish-check.ps1 -SkipGh → READY.
- Entregada en la carpeta de nube como ESTABLE_Aura_v2.0.10_vc966.apk +
  ESTABLE_LEEME.txt. OJO: se instala AL LADO de las betas .dev (paquete
  distinto) → datos frescos: biblioteca vacía, re-login, prueba 3 días.
- Verificado en código antes (petición del dueño): actualizador interno
  apunta a hck0n3/Aura-Hi-Res-v2 releases/latest con comparación
  estrictamente-mayor y nombres de asset del CI; biblioteca vacía en primer
  inicio (sin siembra ni auto-restauración).
- PUBLICACIÓN PENDIENTE: el repo tiene CERO releases (404 en releases/latest
  = esperado); la primera release se crea al taggear v2.0.10, bloqueado hasta
  ~2026-09-02 y SOLO con permiso explícito del dueño. El actualizador
  reportará "sin actualizaciones" hasta entonces (correcto, no fallo).

**REGLA PERMANENTE SUPERPOWERED (orden del dueño 2026-08-27, repetida dos
veces):** NUNCA publicar el código de Superpowered en GitHub. HALLAZGO
CRÍTICO: el repo hck0n3/Aura-Hi-Res-v2 es PÚBLICO y el código de Superpowered
YA está trackeado en git y expuesto en origin/main desde el baseline 90721d1
(herencia del fork). Remediación bloqueada hasta ~09-02; ese día el dueño
decide: (a) hacer el repo privado, o (b) reescribir el historial + sacar el
SDK del repo. Memoria dedicada: project/regla-superpowered-no-github.md.

**HALLAZGO-028 FASE 3 IMPLEMENTADO (2026-08-27, fila 175):** reporte del
dueño en BETA-012: tras iniciar sesión la biblioteca tarda mucho en cargar.
Los 4 syncs de login acumulaban HASTA 50 páginas de paginación antes de
insertar la primera fila en Room. FIX: `completedStreaming(onPage)` nuevo en
innertube Utils.kt (×2 con @JvmName, recorrido VERBATIM de completed(): tope
50, dedupe de continuaciones vistas, corte por 2 respuestas vacías; entrega
cada página al callback —los callbacks reciben LISTAS, no objetos de página—
y sigue devolviendo la acumulada) + 4 helpers por página en SyncUtils
(insertLikedSongsPage/insertLibrarySongsPage/insertArtistsPage/
syncSavedPlaylistsPage); pasadas de lista completa corren una vez tras la
última página; idempotente bajo withRetry. Tombstones/guardias intactos.
`anySyncActive(SyncState)` pura (+6 tests SyncActiveStateTest) + indicador
"Sincronizando tu biblioteca…" en el Home de ambas UIs (AnimatedVisibility +
spinner 14dp, string home_syncing_library en values/strings.xml).

**HALLAZGO-059 INVESTIGADO Y ARREGLADO (2026-08-27, fila 176):** reporte del
dueño en la ESTABLE v2.0.10 (4 mensajes escalados): animaciones trabadas en
tabs/scroll/expansión del player + portadas animadas congeladas. Veredicto
(agente Explore 3 olas + re-verificación archivo:línea): NO hay animador raíz
único — saturación del hilo de UI por mil cortes. Fixes A/B/C (3 agentes en
paralelo, archivos disjuntos): (A) `auraPillRecipe` copia drift=false,
spin=false — la mini-pill (siempre viva) heredaba el InfiniteTransition de
28 s del GLOW_ANIMATED (default del mini) = único escritor por-fotograma
siempre vivo; + test pin en AuraAppearanceTest (7 estilos × cover × motion);
(B) ThumbnailItem crea la transición de rotación SOLO con rotatingThumbnail
&& isCurrentItem && isPlaying (antes TODOS los ítems tickeaban cada fotograma
aun con rotación OFF, el default); (C) AuraAlbumHero recibe isPlaying real
(isEffectivelyPlaying) — el canvas del álbum ya no decodifica video en pausa
(estaba hardcodeado true). Portadas animadas congeladas = VÍCTIMA
(TextureView invalida en hilo UI), no bug propio. Seguimientos rankeados
(NO entraron): pre-composición del expand + bloom raster fuera del hilo UI,
video vivo de la pill en videoMode, transiciones de tab, tormenta de sync.

**ESTABLE v2.0.11/vc967 ENTREGADA (2026-08-27, commit 41ccca5):** actualización
de la v2.0.10 (misma firma SHA-256 ba82c11d… = se instala ENCIMA conservando
datos, a diferencia de la primera estable que era datos frescos). Contenido:
028 FASE 3 + 059 A/B/C. Pipeline VERDE: suite 922/922 (915 baseline + 6
SyncActiveStateTest + 1 pin de la pill; XMLs frescos --rerun; OJO: `--rerun`
sobre el lifecycle `test` NO fuerza los leaf tasks de módulos sin cambios —
artistvideo quedó up-to-date y se re-corrió con :artistvideo:testDebugUnitTest
--rerun explícito), assembleUniversalGmsRelease SIN -Pnosub BUILD SUCCESSFUL
3m49s, aapt2 (iad1tya.aura.music, 2.0.11/967, universal 4 ABIs), apksigner
(CN=Aura Hi-Res v2), BuildConfig (REQUIRE_SUBSCRIPTION=true,
SUPERPOWERED_LICENSE_BOUND=true), pre-publish-check -SkipGh READY. En la
carpeta de nube como ESTABLE_Aura_v2.0.11_vc967.apk + ESTABLE_LEEME.txt
reescrito. VEREDICTO DEL DUEÑO PENDIENTE (pruebas: biblioteca por página +
indicador tras login, fluidez tabs/scroll/expansión, portadas animadas sin
congelarse, canvas álbum pausa con la música, regresión rotación 058).

**Números ACTUALIZADOS:** próximo hallazgo = 062; próxima fila registro =
177; próxima beta = BETA-015 (v2.0.12/vc968). Suite = 922 verde. Commits
locales: 49+ por delante de origin/main (SIN push hasta ~2026-09-02).

**POST-ENTREGA v2.0.11 (2026-08-27):** el dueño reportó DOS hallazgos nuevos
tras recibir la ESTABLE v2.0.11 — HALLAZGO-060 (FASE 2, UI/render/rendimiento:
"las animaciones no se adaptan a la tasa de refresco del celular, mínimo 60
fps, en el S26 Ultra todo traboso y velocidad de reacción lenta"; panel 120 Hz
LTPO) y HALLAZGO-061 (FASE 3, auth/biblioteca: "cuando inicio sesión no se
reinicia la interfaz para cargar la cuenta"; sospechosa #1 = la detección de
login solo en onPageFinished con allowlist estrecha, causa raíz del 028 abierta
desde FASE 0). Ambos REGISTRADOS en el plan RONDA 3 (commit local 803fcda).
La investigación read-only de la sesión anterior se perdió al cortarse; el
2026-08-27 se RE-LANZARON 3 agentes Explore en paralelo: (A) frame pacing —
APIs de display mode, loops a tasa fija, gates de throttle propios
(highPerfMode/deviceThrottle/rawTierLow de rememberAuraGround), TextureView,
blur/GPU; (B) trabajo de hilo principal durante interacción — verificar los
candidatos restantes del ranking 059 contra el código de vc967 (expand del
player + blur 46dp + raster de bloom, video vivo en la pill, doble composición
de tabs, tormenta de sync) + otros costos en main; (C) flujo completo
login→detección→sync→refresco del Home en ambas UIs con modos de fallo
rankeados. FASE 1 de debugging sistemático: fixes SOLO con causa raíz. Para
061, si la investigación no cierra, pedir app.log del dueño (Ajustes ▸
Registros). Pendiente también: confirmar si el dueño probó la v2.0.11 o sigue
en la v2.0.10.

**INVESTIGACIÓN 060/061 COMPLETA (2026-08-27, esta sesión):** 3 agentes Explore
+ re-verificación principal archivo:línea + app.log del dueño
(`C:\Users\AURA\Downloads\log de aurav2\aura_feedback (3).txt`, confirmado de
la v2.0.11/967, instalación FRESH). Resultados completos en la bitácora del
plan (filas INVESTIGADO del 2026-08-27). Resumen durable:
- **061 CAUSA RAÍZ CONFIRMADA (log + código):** la detección SÍ disparó (la
  cookie quedó persistida — sesión 2 arranca logged-in), pero la finalización
  corre en el composition scope (LoginScreen.kt:214); al salir del WebView o
  cerrar la app en la ventana silenciosa de ~1.5-5s se cancela y
  `finishLoginInPlace` (LoginScreen.kt:72-80, ÚNICO que encola los 4 syncs)
  nunca corre. Log: CERO líneas de sync en 9.5 min logueado; dueño mató la app
  (REMOVE TASK 15:52:17). Sin red de seguridad: watcher de cookie solo hace
  `YouTube.cookie = cookie` (App.kt:1711), arranque solo sync con
  UseLoginForBrowse ON (default OFF, App.kt:633), flechas de atrás sin check,
  BackHandler siempre deshabilitado (webView local no-remembered), fallo de
  validación 100% silencioso (LoginScreen.kt:260-264). Fix: (A) finalización
  en applicationScope, (B) red de seguridad al salir del WebView, (C) watcher
  de cookie que encola los 4 syncs en transición a logged-in, (D) feedback
  visible de fallo.
- **060 CAUSAS CONFIRMADAS (ranking):** (1) la tasa del panel es sugerencia —
  la app pide el modo más alto UNA vez (MainActivity.kt:649-697), no re-aplica
  tras eventos del display, cero `Surface.setFrameRate` en el repo; (2) GPU
  por fotograma con player expandido: lóbulos ground 28s fullscreen
  (AuraPlayer.kt:2489-2540), blur portada 46dp APPLE_MUSIC default
  (AuraPlayer.kt:2284), LIVE_MESH re-blur por frame, ritmo por frame;
  (3) one-shot en el momento de interacción: árbol del player compuesto desde
  el primer píxel de expansión (BottomSheet.kt:120-129) con ~15 semillas
  runBlocking DataStore (DataStore.kt:43-45) + raster bloom en UI
  (AuraBloom.kt:343-380); tab entrante recompuesto completo + doble
  composición 200ms (MainActivity.kt:2654-2662); (4) tormenta post-login:
  bloques 300 filas (SyncUtils.kt:210) + recarga completa del home 600ms tras
  sync de artistas (HomeViewModel.kt:1400-1417); (5) ticker progreso 2Hz
  (AuraPlayer.kt:481-489); (6) video TextureView en la pill en videoMode
  (AuraShell.kt:771-792). Descartados: timers fijos, palette en main, perf
  mode auto (solo seed LOW). Fix: (A) re-apply display mode en config change,
  (B) lóbulos rasterizados a bitmap + traslación (patrón 055), (C) raster
  bloom fuera de UI, (D) gate recarga home mientras anySyncActive. Diferidos:
  stagger expansión, semillas runBlocking (560 call sites), ticker 2Hz, video
  pill.
- Log secundario: YouTube rotó player (hash 6a0e84d6 sin config, cipher/n
  muertos; resolve sigue por otras vías, sin fallos de reproducción en el
  log); micrófono no concedido (gate 053 OK); blur Samsung supported=false
  (fallback opaco OK); batteryOptimisationExempt=no al arrancar.

**FIXES 061 + 060 IMPLEMENTADOS Y BETA-015 ENTREGADA (2026-08-27, commit
local 777d319):** filas 177 (061) y 178 (060) del REGRESSION_REGISTRY con
guardianes. 061: `utils/LoginCompletion.kt` puro nuevo (isLoggedCookie,
shouldCompleteLogin, shouldSyncOnCookieChange, shouldRecoverLibrarySyncOnStart;
+10 tests LoginCompletionTest) + `completeLogin` en `Context.applicationScope`
(extensión nueva en App.kt) con latch AtomicBoolean CAS + red de seguridad en
DisposableEffect (CookieManager) + flanco guest→logged en el watcher de cookie
de App.kt (salta la 1ª emisión) + recuperación en frío si logueado-y-jamás-
sincronizado (auto-repara el estado exacto del dueño) + Toast
login_validation_failed + BackHandler arreglado (webViewRef remembered) +
`syncLibraryAfterLogin()`/`lastLikedSyncTimeMs()` en SyncUtils. 060: (A)
refresh alto re-aplicado en cambios de Configuration (MainActivity
`applyPreferredRefreshRate` + LocalConfiguration key); (B) AuraLobeRaster —
lóbulos GLOW_ANIMATED rasterizados 1080px una vez por artwork + blit con
traslación; (C) caché del raster de bloom en AuraBloomState (`rasterFor`,
fuera del drawWithCache) con intensidad como alpha en el draw (no horneada);
(D) recarga del home gateada en `anySyncActive` (suspensión StateFlow + 600ms).
Suite: CORREGIDO el "922" de memorias previas — baseline real era 921 (app 892
+ innertube 27 + artistvideo 2); con los 10 tests nuevos = **931/931 verde**
(app 902/77 clases + 27 + 2). NOTA DE BUILD EN ESTA PC (40 GB RAM, ~14 libres
con VS Code): el lifecycle `test` completo + 16 workers mató el daemon de
Kotlin por OOM DOS veces (daemons solapados + compila las 8 variantes debug);
la vía que funciona: `gradlew --stop` y luego tareas específicas
`:app:testUniversalFossDebugUnitTest :innertube:test :artistvideo:test
assembleUniversalGmsRelease --max-workers=6 -Pkotlin.daemon.jvmargs=-Xmx8g`
(BUILD SUCCESSFUL 5m37s). BETA-015 = v2.0.12/vc968 (SIN -Pnosub: paquete
iad1tya.aura.music, actualización directa sobre la ESTABLE v2.0.11 — conserva
el estado exacto del fallo del dueño para probar la auto-reparación),
verificada aapt2 + apksigner (Verifies, CN=Aura Hi-Res v2, SHA-256 ba82c11d…),
entregada en `C:\Users\AURA\Desktop\BETA OFICIAL  DE AURA FENIX` (OJO: doble
espacio en el nombre) como BETA-015_Aura_v2.0.12_vc968.apk (87,381,539 bytes)
+ LEEME_BETA-015.txt. Pregunta para el dueño en el LEEME: si tocó el
High-Performance Mode o el toggle de refresco del sistema en el S26 Ultra.

**HALLAZGO-062 CERRADO + BETA-016 ENTREGADA (2026-08-27, commit 23de44e):**
el dueño entregó el código viejo (`...\AURA BETADO\1`, vc951/0.6.231, sin
.git) — "ahí todas las animaciones eran fluidas y la reacción instantánea;
verifica si ahí está la solución". Comparación exhaustiva con 5 agentes
(núcleo visual, shell/interacción, config/deps/servicio, VisualizerHub/App.kt,
crypto/DataStore) + re-verificación principal. VEREDICTO: la "solución" del
viejo era la AUSENCIA de la capa de cifrado del HALLAZGO-013
(`EncryptedSecrets.kt` solo existe en v2), que pagaba AES-GCM de Keystore de
hardware EN EL HILO PRINCIPAL por 4 vías: (1) `getOrCreateSecretKey()` hacía
`KeyStore.load(null)` + `getEntry()` (binder/TEE ~ms en el S26 Ultra) en CADA
operación; (2) el `.map` de descifrado del flow `data` corría POR COLECTOR y
POR EMISIÓN (~640 sitios rememberPreference/collectAsState × 5 valores
cifrados del usuario logueado); (3) semilla `runBlocking { data.first() }` en
composición; (4) `reencrypted` re-cifraba todas las claves sensibles en cada
escritura. DESCARTADOS con evidencia: shell (cada diff = fix nuestro que
quita trabajo), config (idéntica salvo bumps), VERSIONES COMPOSE (material3
1.5.0-alpha18 —idéntico en ambos— arrastra runtime/foundation/ui
1.11.0-beta02 por POM; el viejo declaraba 1.10.2 pero resolvía igual; única
diff real 1.11.0-beta02→1.11.2 + Kotlin 2.3.10→2.4.0), sin baseline profiles,
MusicService (emisiones idénticas), AuraVisualizerHub (INERTE en S26 Ultra:
gateado por permiso Android 14+, attach error -3, reintentos topados a 3),
App.kt (diffs todos IO/onCreate). El viejo hacía MÁS trabajo por frame
(recomposición del árbol entero a 120 Hz con pulso crudo) y era fluido → no
era render, era crypto. FIXES (fila 179): (A) SecretKey cacheada
(`@Volatile cachedKey`, es handle al hardware = seguro) + gate
`!keystoreReady → null` en decryptOrNull; (B) vista descifrada memoizada por
IDENTIDAD de instancia de Preferences (`viewCache`/`decryptedViewFor`) — 1
descifrado por emisión compartido entre colectores; (C) `reencrypted`
conserva el ciphertext de valores sin cambio; (D) `AURA_RHYTHM_STEPS` 8→32
(escalón <1% de intensidad = pulso continuo como el viejo, recomposición
gateada por derivedStateOf y acotada al ground). Seguridad del 013 intacta:
12 tests de contrato pre-existentes verdes. Tests nuevos: 4 en
EncryptedSecretsTest (16/16) + 1 en AuraRhythmTest (11/11). Suite 936/936
(app 907 + innertube 27 + artistvideo 2, XMLs frescos; misma receta OOM de
build, BUILD SUCCESSFUL 5m13s, build-r3-062-full.txt). BETA-016 =
v2.0.13/vc969 (SIN -Pnosub), aapt2 iad1tya.aura.music/2.0.13/969 universal,
apksigner Verifies CN=Aura Hi-Res v2 SHA-256 ba82c11d… (= v2.0.11/v2.0.12 →
actualización directa), entregada en `C:\Users\AURA\Desktop\BETA OFICIAL  DE
AURA FENIX` (doble espacio) como BETA-016_Aura_v2.0.13_vc969.apk
(87,382,695 bytes) + LEEME_BETA-016.txt (pruebas: velocidad de reacción
tabs/player/Ajustes/post-login, pulso continuo, sesión intacta, regresiones
015). NOTA: el seed `runBlocking` de rememberPreference SIGUE existiendo
(diferido), pero ahora paga vista memoizada + clave cacheada.

**MIGRACIÓN A macOS + PREFSBRIDGE (2026-08-27 noche):** el proyecto migró a la
Mac nueva (Apple M4/32 GB) vía zip `Aura-v2_codigofuente-y-memoria_2026-08-27`
— la carpeta NO tiene `.git` (los commits locales de Windows no migraron).
Entorno restaurado: SDK completo (platform 36, build-tools 36, NDK
27.0.12077973, CMake 3.22.1), `local.properties` y `app/keystore/release.keystore`
desde `~/Downloads/AuraHiResDevBackup` (md5 idéntico al backup; copias también
en `~/Downloads/keystore/`), Gradle 9.6.1 con daemon auto-aprovisionado a
JDK 21 (launcher en JDK 25). DIFF contra el zip detectó que la sesión previa
ya había implementado aquí (sin registrar) el cierre del diferido 060
"semillas runBlocking": `object PrefsBridge` + `getNonBlocking` + semillas no
bloqueantes (DataStore.kt) y colector único publicador en `App.onCreate`
(App.kt). Esta sesión lo completó: `PrefsBridgeTest.kt` (6 casos), fila 180
del REGRESSION_REGISTRY, bump v2.0.14/vc970, bitácora del plan actualizada,
y lanzó suite + `assembleUniversalGmsRelease` (receta: `--stop` previo,
`--max-workers=6 -Pkotlin.daemon.jvmargs=-Xmx8g`).

**SIGUIENTE:** verificar el resultado de la suite (esperado 942 = 936 + 6
PrefsBridgeTest) y del build; verificar el APK con aapt2 + apksigner
(CN=Aura Hi-Res v2, SHA-256 ba82c11d…) y ENTREGAR BETA-017 (v2.0.14/vc970)
con su LEEME (pruebas: tiempos de reacción tras tocar cualquier cosa —tabs,
player, Ajustes, post-login—, scroll fluido, persistencia de ajustes y sesión
intacta). Veredictos del dueño pendientes: BETA-015, BETA-016 y BETA-017.
Si verde → preparar ESTABLE v2.0.14 (con permiso explícito). Tras el
desbloqueo de GitHub (~09-02): primer tag estable con permiso explícito del
dueño + decisión Superpowered (repo privado o reescritura + SDK fuera) +
decidir re-inicializar el `.git` de esta carpeta. FASE B (021) pasos 4+ y
FASE 2 (026/034/043/044) siguen en cola; diferidos 060 restantes (stagger
expansión, ticker 2Hz, video pill); vigilancia 056 (rama 403 sin gate +
backoff por videoId). Próximo hallazgo nuevo: 063. Próxima fila del registro:
181. Próxima beta: BETA-018.
