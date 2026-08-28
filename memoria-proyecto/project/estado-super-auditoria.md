---
name: Estado de la SÚPER AUDITORÍA (fases, hallazgos, siguiente paso)
description: Progreso vivo de la auditoría de Aura Hi-Res Player — 22 fases + VUELTA 2 completas, 008 CERRADO; RONDA 3 BETA-006 veredicto pendiente; 046 IMPLEMENTADO (preamp +3 dB, fbb7d25) y preset AR-SOUND (bd5e843); 047 en investigación; 043/044/045 en cola FASE 2; GitHub bloqueado hasta ~2026-09-02
type: project
---

Estado 2026-08-25 — **VUELTA 2 COMPLETADA** (documento vivo: `SUPER AUDITORIA DE MI CODIGO ANDROID.md`,
con YAML maestro, bitácora —fila VUELTA 2— y tabla de hallazgos al día).

- AUDITORÍA 22/22 cerrada el 2026-08-24 (FASE 22, commit abf0dbb). BETA-002 (v0.6.233/vc953,
  commit 11844d9) probada en el celular y confirmada **VERDE** por el dueño el 2026-08-25.
  **BETA-003 VERDE confirmada por registro** (v0.6.234/vc954; aura_feedback (15).txt, 2026-08-25
  21:40): 64 resolves ok=true winner=ANDROID_VR (16:03–21:35); fallos 16:02 = red caída
  (ENETUNREACH/DNS), no certificado; ok=false de 17:03/20:11 auto-reparados en segundos;
  cifrado 013 validado en vivo (cuenta logueada tras migración, cero errores Keystore);
  crossfade y loudness OK; sin crashes/ANR. Ver memoria `project/beta-0234-round2.md`.
- **VUELTA 2 (2026-08-25), commit LOCAL ee949f4 (35 archivos, sin push):** misión del dueño = reparar
  TODO lo abierto salvo 008 sin dañar la reproducción. Ejecutado y verificado SIN --write-locks:
  suite 642/642 (--rerun), assembleUniversalGmsRelease (10m11s) y assembleArm64FossRelease -Pnosub
  (9m29s) — los dos BUILD SUCCESSFUL (build-verif-tests.txt / build-verif-release.txt):
  - **013 RESUELTO:** cifrado de las 16 claves sensibles del DataStore. Wrapper transparente
    `EncryptedSecretsDataStore` en el chokepoint `Context.dataStore` (DataStore.kt), Keystore
    AES-256-GCM (alias aura_echo_secrets_v1, prefijo "\u0000ENC1:"), migración one-time en
    App.onCreate (solo proceso default), disponibilidad-primero (sin Keystore = plaintext passthrough),
    ciphertext bloqueado se presenta "" pero JAMÁS se sobreescribe. Cero cambios en ~560 consumidores.
    Archivo nuevo utils/EncryptedSecrets.kt + 12 tests (EncryptedSecretsTest). API real = datastore
    1.2.1: `toMutablePreferences()` es MIEMBRO de Preferences (no importar como extensión);
    `Preferences.Pair` es opaco (key/value internal, sin destructuring) — en tests usar infix propio.
  - **011 RESUELTO:** `dependencyLocking { lockAllConfigurations() }` root+subprojects; 17 lockfiles
    (16 módulos + settings-gradle.lockfile) generados con las 3 tareas EXACTAS del CI + --write-locks
    (3/3 verdes), y re-verificado sin --write-locks (tests + las 2 release).
  - **016 RESUELTO completo:** persistent_queue/automix/player_state excluidos de cloud-backup Y
    device-transfer (aprobado por el dueño).
  - **019 RESUELTO completo:** 6 clientes + 2 extractores vendoreados (NewPipe/BraveNewPipe 15s/30s,
    sin callTimeout por el player.js ~2MB); barrido final verificó que el resto YA tenía topes.
    Deliberado: ArtistVideo.kt (video-streaming, defaults OkHttp), SSE sin read timeout corto.
  - **023 RESUELTO:** LTR-only documentado como decisión deliberada en el manifiesto (:55-58).
  - **010 riesgo documentado:** mirror aliyun IRREMPLAZABLE (ffmpeg-kit-full:6.0-2 + tinypinyin:2.0.3
    EOL solo existen en el espejo JCenter; evidencia build-010-resolve/restored.txt). Salida: reemplazarlas.
  - **018 riesgo documentado:** canales remotos todos HTTPS, cleartext OFF global (NSC), el updater YA
    verifica ApkSignatureVerifier.matchesInstalledSignature antes de instalar, config remota fail-closed.
    Pinning RECHAZADO: rotación de certs de GitHub rompería la auto-reparación = reproducción.
- DECISIONES DEL DUEÑO (2026-08-25, en ejecución):
  - **017 ACEPTADO FORMALMENTE** ("del numero dos acepto") — registrado en el doc de auditoría.
  - **008 — CERRADO (2026-08-25):** pipeline v2 completo y VERIFICADO EN VIVO ("del numero tres..."):
    push a hck0n3/Aura-Hi-Res-v2 (01c85b3 identidad, b90757c exec-bit gradlew/scripts), secrets CI,
    CI verde (Build & Sign + CodeQL), APK del CI verificado con apksigner `CN=Aura Hi-Res v2`
    (SHA-256 ba82c11d… = keystore local). BETA-004 (v2.0.0/vc955 NOSUB) probada VERDE por el dueño
    (registro aura_feedback 16, 2026-08-25 22:18): ~37 canciones ok=true winner=ANDROID_VR,
    instalación FRESH, incluso sin cuenta (guest); el certificado nuevo resuelve streams.
    Ver memoria `project/aura-v2-nueva-identidad.md`. Dependabot activo en el
    repo nuevo: NADA se mergea sin revisión del dueño (media3 1.11.0 y KSP fallan CodeQL).
  - **021 ORDENADO ANTES DE PUBLICAR — FASE A COMPLETA + FASE B INICIADA (2026-08-25):** FASE A: TRES
    extracciones puras con characterization tests primero, en `playback/`:
    `PlaybackErrorClassifier` (25 tests, 70e8b24), `RadioQueueShaping` (17 tests, 9748e1c) y
    `DeliveredQuality` (13 tests, 48087b9; doc c9cff06). Fase A AGOTADA: los demás núcleos puros YA
    vivían fuera (AudioGain.kt, ShuffleOrdering.kt, DownloadUtil); streamUrlExpiryMillis/parseQuality
    son código MUERTO.
    **FASE B (orquestación con estado):**
    1. `PreloadPlanning` (commit f788c8d, pusheado) — núcleo puro de `preloadUpcomingItems`: lookahead
       tope 10, límite 1 bajo powerSave, url-only = powerSave∨perfMode, planItems preserva
       take(limit)-ANTES-de-distinct() y las reglas descargado (cae entero)/local (no resolve, sí
       lyrics)/URL-cacheada (no resolve, sí lyrics; NUNCA saltar por hit de player-cache = ghost-path
       #28), y mergeLoudness del FIX A (resuelto gana, existente rellena, medido solo de BD,
       persistRow solo si BD sin loudness). 18 tests. Un test cayó por over-pinning: take(-3) lanza
       IllegalArgumentException y el límite negativo es inalcanzable (slider 1..10) — se quitó el
       caso, no se tocó la función.
    2. Barrido de clusters: loudness orchestration y enhanced shuffle YA tenían sus núcleos puros
       fuera (AudioGain.kt con tests, ShuffleOrdering, EnhancedShuffleCycle con tests) — pero
       EnhancedShuffleCycle vivía al final de MusicService.kt; mudanza verbatim a
       `playback/EnhancedShuffleCycle.kt` (commit 720f923, pusheado). Video mode y crossfade quedan
       como orquestación GENUINAMENTE con estado (player/threads/jobs); safeVolumeAppliedGain ya
       delega en AudioGain.kt.
    MusicService 11 702 → 11 451 líneas. Suite 715/715 --rerun (58 XMLs). Doc commits 46a0b36,
    aa52298. Técnica MediaItem de la Fase A sigue vigente (reflexión en tests, nunca equals() sobre
    MediaItems con LocalConfiguration de Uri null).
- ABIERTOS ahora: 021 (Fase B: preload + shuffle-cycle hechos; DECISIÓN PENDIENTE del dueño:
  video mode y crossfade son orquestación con estado — extraer colaboradores con estado (más
  riesgo) o cerrar la Fase B aquí) + RONDA 3 (HALLAZGO-026..046 sobre BETA-004/005 en S26 Ultra;
  FASE 1 segunda pasada COMPLETADA y BETA-006 ENTREGADA 2026-08-26: fixes commit 8414363
  (keep-alive Visualizer, cuantización quantizeAuraRhythmIntensity + derivedStateOf, canvas
  ExoPlayer diferido 300ms, expiry preload, ExtractorCircuitBreaker PipePipe→BraveNewPipe) +
  bump 5396bdd; suite app 756/756 + innertube 24/24 verde; BETA-006 v2.0.2-nosub/vc957 arm64
  verificada aapt2+apksigner (CN=Aura Hi-Res v2, SHA-256 ba82c11d…) y entregada por carpeta;
  VEREDICTO DEL DUEÑO PENDIENTE (027 flip ambos sentidos, 042 arranque rápido, reintento
  dictado 031). 043 (UI adaptativa TV/tablet/plegables/celular, ambas UIs), 044 (Android Auto)
  y 045 (color por portada más notorio) registrados para FASE 2; 046 (preamp del EQ por
  defecto en +3 dB, petición del dueño) IMPLEMENTADO 2026-08-26 (commit fbb7d25: migración
  one-time EqPreampDefault3DbAppliedKey tras siembra V2 —que ahora entrega +3.0—, respeta
  preamp elegido ≠ 0.0, interacción Safe Volume verificada en AudioGain.kt, suite 757/757 +
  assemble verde; falta confirmación de oído del dueño) + preset AR-SOUND añadido a la grilla
  audiòfila (commit bd5e843). Dispositivos del dueño
  confirmados: TV TCL Google TV, Galaxy Fold 7, S25 Ultra, carro con Android Auto).
  008 CERRADO el 2026-08-25:
  BETA-004 VERDE en el celular del dueño (keystore nueva CN=Aura Hi-Res v2 resuelve streams).
  Publicar la estable v2 queda desbloqueado en lo técnico (falta pre-publish-check + CI verde
  + permiso del dueño) PERO GitHub está bloqueado ~7 días desde 2026-08-26 (~2026-09-02): solo
  commits locales hasta entonces.
- RESUELTOS históricos: 001, 002, 022, 024, 009 + FASE 22 (025, 014, 016-parcial, 020, 019-críticos,
  015, 012) + VUELTA 2 (013, 011, 016-resto, 019-resto, 023). Aceptados: 006, 007, 010, 018, 017.
  Próximo número de hallazgo nuevo: 050 (026–049 usados por la RONDA 3; 048 = cortes al
  salir/entrar del EQ, 049 = colores del EQ no siguen la portada en vivo + paleta global en todas
  las pantallas, ambos 2026-08-26 sobre BETA-007, investigación en curso). HALLAZGO-047 registrado
  2026-08-26 durante la prueba de BETA-006 (commit a4a0d9f): "las canciones cuando inician, inician
  cortadas"; INVESTIGACIÓN DE CÓDIGO COMPLETA (agente solo lectura): 5 candidatas por probabilidad —
  (1) cut-not-ready hard cut (MusicService.kt:10495-10524), (2) SponsorBlock music_offtopic salteando
  el segundo 0 sin loguear (2280, 5924-5934; ON por defecto), (3) blend tardío curva 4 (entrante ~2s
  bajo −12dB), (4) underrun BUFFERING congela fade-in, (5) lead corto de preload; descartados arranque
  adelantado (seekTo 0 siempre), regresión BETA-006, primer item, Visualizer; siguiente paso: evidencia
  del dueño (log ev=cut-not-ready + A/B "Saltar segmentos sin música" OFF), fix condicionado.
- Próximo paso: RONDA 3 — esperar veredicto del dueño sobre BETA-006 (si falla, pedir log desde
  Ajustes ▸ Registros y tercera pasada; si verde, cerrar 027/042). 046 IMPLEMENTADO y AR-SOUND
  añadido (commits fbb7d25, bd5e843 — entran en la próxima beta; falta confirmación de oído).
  047 investigación completa — esperar evidencia del dueño (log + A/B SponsorBlock). Después FASE 2
  (026 + 034 +
  043 adaptativo + 044 Android Auto + 045 color por portada), leyendo PRIMERO
  docs/UI_INVENTORY.md (regla 5 de AGENTS). Sin preguntas pendientes: 026b respondida (densidad
  Native 100%, nunca la tocó), 033 decidido (REBAUTIZAR sin favoritos reales, "y que funcione
  de verdad"), dispositivos confirmados (TV TCL Google TV, Fold 7, S25 Ultra, carro con Android
  Auto). Plan en `PLAN RONDA 3 REPORTES DEL DUEÑO.md`.
  Aparte, decisión del dueño sobre la Fase B del 021 (cerrar aquí o extraer video mode y
  crossfade con colaboradores CON ESTADO — más riesgo, beneficio decreciente). Publicar la
  estable v2 requiere pre-publish-check, CI verde y permiso explícito del dueño — y GitHub
  bloqueado hasta ~2026-09-02 (solo commits locales mientras tanto).
- Lecciones permanentes: (1) verificar versión RESUELTA del grafo, no el pin declarado. (2) No
  reescribir migraciones ya ejecutadas en dispositivos. (3) Leer backup_rules.xml antes de reportar
  fugas de backup. (4) Verificar SIEMPRE claims contra el código. (5) API externa que obliga patrón
  inseguro → aceptación formal del dueño. (6) Filtro de contenido puede matar agentes en temas auth →
  fallback primera persona. (7) Cerrar primero las EN_CURSO antes de abrir trabajo nuevo. (8) Al editar
  tablas markdown, incluir la fila separadora |---|---| en el ancla. (9) Gradle solo imprime "N tests
  completed, M failed" si hay fallos: verificar SIEMPRE contra XMLs frescos de
  `app/build/test-results/<variant>/TEST-*.xml`; si el task quedó UP-TO-DATE usar `--rerun`.
  (10) Timeouts de streaming: NUNCA callTimeout en el cliente que sirve la canción entera.
  (11) Polls largos de build en is_background:true. (12) En cmd usar `powershell -NoProfile -Command`
  para Select-String/Get-Content; `&` es secuencial y `2>nul` traga errores útiles. (13) Datastore
  1.2.1 KMP: `toMutablePreferences()` miembro, `Preferences.Pair` opaco (ver arriba). (14) --write-locks
  con tareas UP-TO-DATE puede no escribir locks: verificar que los lockfiles existen y re-validar SIN
  --write-locks. (15) En APKs con R8 los XML de res/xml/ quedan renombrados (res/Qq.xml): dumpear con
  `aapt dump xmltree`. (16) El APK release local puede ser de una revisión git anterior:
  version-control-info.textproto en META-INF dice la exacta. (17) En esta PC Gradle puede resolver el
  JRE de la extensión Red Hat de VS Code (sin jlink) y tumbar `JdkImageTransform`: lanzar SIEMPRE los
  builds con `set "JAVA_HOME=C:\Program Files\Amazon Corretto\jdk21.0.12_9"` delante Y los flags
  `-Dorg.gradle.java.installations.paths="C:\Program Files\Amazon Corretto\jdk21.0.12_9"
  -Dorg.gradle.java.installations.auto-detect=false`; si un daemon ya cacheó la resolución mala,
  `gradlew --stop` antes de reintentar (reintento sin stop falla en segundos).

**Why:** el dueño dirige la auditoría desde el chat y no quiere repetir contexto.
**How to apply:** al reanudar, leer el YAML `estado_actual` al final de
`SUPER AUDITORIA DE MI CODIGO ANDROID.md` (fuente autoritativa) y continuar desde `proxima_accion`
(ahora DECISIONES_DEL_DUENO); esta memoria es solo el índice rápido.
