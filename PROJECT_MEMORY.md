# 🧠 MEMORIA DEL PROYECTO: AURA HI-RES PLAYER (PORT MODELO SIMPMUSIC)

> Este archivo lo mantiene la IA. El dueño solo ejecuta betas y da feedback.
> Plan maestro: ver `PLAN.md`. Reglas duras del repo: ver `AGENTS.md` y `docs/REGRESSION_REGISTRY.md`.

## 📍 Estado Actual

- **2026-08-24 (continuación): ✅ ARREGLADO EL BUG DE SUSCRIPCIONES AUTOMÁTICAS DE ARTISTAS
  (commit `88cf0e1`, registro #154) + PIN DE LETRAS EN CROSSFADE (commit `f7022e2`, registro #155).
  BETA-001 ENTREGADA Y ✅ PROBADA CON ÉXITO POR EL DUEÑO EN REMOTO (2026-08-24): todo funciona.**
  Causa raíz CONFIRMADA del reporte beta (al reproducir o dar me gusta, el artista quedaba
  suscrito solo): `DatabaseDao.followArtistsWithContent()` — sentencia bulk que cada pase de
  sync estampa `bookmarkedAt` en TODO artista con contenido en la librería — y TODAS las vistas
  de suscripción leían `bookmarkedAt`. Fix: sentencia eliminada (tombstone en `DatabaseDao.kt`)
  + sus 5 call sites; toda vista/query de suscripción ahora lee `followedByUserAt`; ese campo es
  el discriminador de toggleLike; se estampa directo al leer de vuelta las suscripciones reales
  de la cuenta. Fuentes legítimas de follow intactas (tap explícito, onboarding, import
  Spotify/Tidal/Deezer, read-back YTM). Semántica album/playlist y filtro de backup sin cambio.
  Lyrics: el pin `_crossfadeOutgoingMetadata` dejaba la letra de la canción SALIENTE durante el
  crossfade (solo se liberaba en `cleanupCrossfade`); ahora el loop de fade evalúa
  `CrossfadeLyricsPin.shouldRelease` cada tick y libera cuando el player saliente muere, su gain
  cae bajo el umbral audible o su SilenceDetector reporta silencio.
  Nota de build: las fuentes ya estaban en disco antes del build de las 11:01 (mtimes 10:31–10:49);
  el APK de las 11:01 YA contenía ambos fixes — verificado con gradle UP-TO-DATE (BUILD SUCCESSFUL
  en `build-follow-lyrics.txt`). **VEREDICTO DE PRUEBA (2026-08-24, dueño, remoto vía carpeta de
  nube): BETA-001 VERDE — TODO FUNCIONA: (a) reproducir/like YA NO auto-suscribe artistas,
  (b) la letra cambia con el crossfade, (c) reproducción, toggle música↔video y ecualizador
  bien. Fixes #154 y #155 CONFIRMADOS en dispositivo.** SIGUIENTE PASO: continuar la súper
  auditoría — FASES 1 (inventario), 2 (dependencias), 3 (seguridad estática), 5 (almacenamiento),
  6 (red), 7 (auth/cripto), 8 (componentes/IPC), 10 (arquitectura), 11 (calidad de código),
  12 (concurrencia), 13 (rendimiento), 14 (UI/UX técnica), 15 (recursos/build), 16 (testing)
  y 17 (CI/CD) ya COMPLETADAS el 2026-08-24; sigue FASE 18 (privacidad). El CI ya gatea con
  la suite mínima de 630 tests (gradle.yml y test-build.yml).
  La publicación estable de estos fixes queda a decisión del dueño (publicar exige su permiso
  explícito).
- **2026-08-24 (tarde, 9): ✅ SÚPER AUDITORÍA — FASE 12 (CONCURRENCIA Y CICLO DE VIDA)
  COMPLETADA.** Cierre formal en primera persona: HALLAZGO-002 (único hallazgo de concurrencia
  de toda la auditoría) ya estaba CORREGIDO (commit f7022e2, registro #155) y validado en
  dispositivo con BETA-001 VERDE. Spot-checks: cero `GlobalScope` en todo el repo; cero hilos
  crudos en `app/src/main`; 117 usos de `runBlocking` auditados por contexto — los de producción
  están fuera de Main o son deliberados y documentados (loader de media3, checkpoints IO,
  mirror one-shot de App.kt:1816, commit síncrono `onGetSong`, mutex en `createPlaylist`);
  workers todos `CoroutineWorker`. SIN hallazgos nuevos. Nota de higiene:
  `DatabaseDao.incrementPlayCount(songId)` (DatabaseDao.kt:1477) es código muerto sin llamador
  (candidato a limpieza en FASE 22). Ver sección RESULTADOS DE LA FASE 12 en `SUPER AUDITORIA
  DE MI CODIGO ANDROID.md`. Siguen abiertos 13 hallazgos
  (008/010/011/012/013/014/015/016/017/018/019/020/021).
- **2026-08-24 (tarde, 10): ✅ SÚPER AUDITORÍA — FASE 11 (CALIDAD DE CÓDIGO) COMPLETADA.**
  Primera fase de trabajo NUEVO; solo medición, sin cambios de código. Veredicto: CALIDAD
  SÓLIDA, SIN hallazgos nuevos. Números: 141 `!!` en Kotlin de app/src/main (moderado;
  patrones idiomáticos: 12 nav-args de savedStateHandle, getSystemService; playback/ solo 3);
  39 catches ignorados/vacíos TODOS legítimos (ActivityNotFound/SecurityException, teardown
  best-effort, parsing JSON defensivo de Spotify, degradación documentada en MainActivity);
  19 printStackTrace (Utils.kt es el helper de reporte del proyecto; varios en eq/ zona
  protegida); CERO TODO/FIXME/HACK reales; 1 solo UNCHECKED_CAST (MessageCodec.kt:440). La
  deuda estructural ya está registrada como HALLAZGO-021 (no se duplica). Ver sección
  RESULTADOS DE LA FASE 11 en `SUPER AUDITORIA DE MI CODIGO ANDROID.md`. Sigue FASE 13
  (rendimiento).
- **2026-08-24 (tarde, 11): ✅ SÚPER AUDITORÍA — FASE 13 (RENDIMIENTO) COMPLETADA.**
  Auditoría ESTÁTICA (sin dispositivo conectado; la medición runtime va a FASE 19). Veredicto:
  POSTURA SÓLIDA, SIN hallazgos nuevos. Evidencia: Room sin allowMainThreadQueries (fuerza
  fuera de Main); cero busy-loops; WakeLocks gestionados (ListenTogetherClient tope 10 min +
  release en desconexión; PlaybackKeepAlive PARTIAL_WAKE_LOCK+WifiLock con release verificado);
  ThermalManager.kt con polling térmico compartido ref-counted cada 10 s y
  rememberDeviceThrottle() gateando efectos pesados; haptics de scroll throttled a 100 ms
  (comentario explícito battery/heat); Coil con políticas de caché explícitas y tamaño
  configurable vía mirror (sin leer DataStore en frío); 46 derivedStateOf. Potenciación
  anotada para FASE 22: Baseline Profiles (no existe baseline-prof.txt). Ver sección
  RESULTADOS DE LA FASE 13 en `SUPER AUDITORIA DE MI CODIGO ANDROID.md`. Sigue FASE 14
  (UI/UX técnica).
- **2026-08-24 (tarde, 12): ✅ SÚPER AUDITORÍA — FASE 14 (UI/UX TÉCNICA) COMPLETADA.**
  Auditoría ESTÁTICA (app 100% Compose; validación visual runtime → FASE 19). Veredicto:
  SÓLIDA, SIN hallazgos nuevos. Evidencia: 174 contenedores lazy; keys estables
  `key = { it.id }` verificadas línea por línea en HomeScreen (recentSongs con distinctBy,
  quickPicks, playlists, discover, keepListening) — el único `items(5)` sin key es el shimmer
  estático; 375 rememberSaveable/isSystemInDarkTheme (estado sobrevive a configuración);
  accesibilidad con práctica correcta: 1 147 contentDescription, 866 `= null` en miniaturas
  decorativas junto a texto legible y ~280 controles etiquetados con stringResource; RTL con
  iconos AutoMirrored + LocalLayoutDirection; dark mode con override deliberado documentado
  (Utils.kt:176). Composables gigantes ya cubiertos por HALLAZGO-021. Ver sección RESULTADOS
  DE LA FASE 14 en `SUPER AUDITORIA DE MI CODIGO ANDROID.md`. Sigue FASE 15 (recursos/build).
- **2026-08-24 (tarde, 13): ✅ SÚPER AUDITORÍA — FASE 15 (RECURSOS Y BUILD) COMPLETADA.**
  Veredicto: configuración SÓLIDA; 2 hallazgos nuevos + confirmación con prueba directa del
  HALLAZGO-008. Evidencia: (a) `assembleUniversalFossRelease` VERDE — R8 + shrinkResources
  funcionando de verdad, APK 80.9 MB, ProGuard completo (WebView JS, kotlinx.serialization,
  PipePipe/Rhino, cola Serializable, UCrop, Cast, VibraSignature, ktor, Shazam, Listen Together);
  (b) `lintUniversalFossDebug` corrió completo: 500 UnusedResources en origen (drawables del
  fork; shrinkResources ya los saca del APK release) y ~136 errores no bloqueantes heredados
  (abortOnError=false); (c) `testUniversalFossDebugUnitTest` ROJO: 8 fallos de 629 — causa raíz
  verificada en primera persona: codifican el contrato VIEJO del follow (discriminador
  `bookmarkedAt`) que el commit 88cf0e1 cambió A PROPÓSITO a `followedByUserAt` (registry #154);
  tests intactos desde el baseline; NO es regresión funcional (display+toggle consistentes,
  `toggleLike` incondicional en cada tap, ArtistSyncPolicy intacto y verde, BETA-001 VERDE en
  el teléfono) → HALLAZGO-022 (MEDIA): reescribir en FASE 16; el CI no corre tests ni lint,
  así que la suite roja es invisible (gap para FASE 17); (d) `apksigner verify --print-certs`
  sobre el APK release recién generado: `CN=Android Debug` → HALLAZGO-008 CONFIRMADO con
  evidencia directa (bloquea toda publicación; la keystore real CN=JR MUSIC PRO vive en el
  CI/PC de respaldo); (e) `android:supportsRtl="false"` vs código RTL-aware (AutoMirrored,
  LocalLayoutDirection) → HALLAZGO-023 (BAJA, decisión del dueño: documentar LTR-only o
  habilitar+testear). i18n: 45 idiomas, 2 898 stringResource, cero strings hardcoded en XML.
  Ver RESULTADOS DE LA FASE 15 en `SUPER AUDITORIA DE MI CODIGO ANDROID.md`. Sigue FASE 16
  (testing).
- **2026-08-24 (tarde, 14): ✅ SÚPER AUDITORÍA — FASE 16 (TESTING) COMPLETADA.**
  Veredicto: red de pruebas SÓLIDA en lógica crítica; HALLAZGO-022 RESUELTO sin tocar código
  de producción; sin hallazgos nuevos. Evidencia: (a) inventario — 67 archivos de test
  unitario JVM (app 52; canvas 1, innertube 5, jiosaavn 1, lrclib 2, migration 5), cero
  androidTest en todo el repo; cobertura fuerte en follow/sync (4), reproducción/shuffle/
  crossfade (~9), licencia (3), eq/audio (4), backup gate, migraciones, parsing innertube y
  lógica de UI; (b) los 8 tests rotos de la FASE 15 eran exactamente los obsoletos del
  contrato viejo de follow: reescritos al contrato de `followedByUserAt` (helper nuevo
  `followedSubscription()`; el test obsoleto se dividió en dos que fijan el contrato real:
  fila post-MIGRATION_39_40 → tap = FOLLOW idempotente que no arma unsubscribe; artista ya
  seguido → unfollow marca intento reintentable y limpia marcadores); producción intacta;
  suite 629/8 fallos → **630 tests, 0 fallos, 0 errores, 0 skipped** (BUILD SUCCESSFUL 32 s,
  verificado por XMLs frescos: 53 suites; log en `build-fase16.txt`); (c) cero flaky
  estructural (cero Thread.sleep/@Ignore); SearchVideoTest (:innertube) pega contra la red
  real sin assertions → anotado para el CI de FASE 17; (d) gaps anotados como deuda: cero
  characterization tests de migraciones Room (MIGRATION_39_40 sin test), MusicService sin
  tests (HALLAZGO-021 → FASE 22), cero instrumentados, backup cubierto solo por BackupGate;
  (e) suite mínima de seguridad definida: `:app:testUniversalFossDebugUnitTest` (630 tests
  deterministas, ~30 s) = gate que FASE 17 debe añadir al CI. Ver RESULTADOS DE LA FASE 16
  en `SUPER AUDITORIA DE MI CODIGO ANDROID.md`. Sigue FASE 17 (CI/CD).
- **2026-08-24 (tarde, 15): ✅ SÚPER AUDITORÍA — FASE 17 (CI/CD) COMPLETADA.**
  Veredicto: el gap CI-sin-tests queda CERRADO; sin hallazgos nuevos. Evidencia: (a) los 4
  workflows (gradle.yml, test-build.yml, codeql.yml, youtube-player-updater.yml) tenían CERO
  pasos de tests y CERO de lint — el único gate real era la compilación de CodeQL; (b) paso
  nuevo "Run unit tests (quality gate)" añadido en gradle.yml ANTES de construir/firmar
  (suite roja = no sale ningún APK) y también en test-build.yml; corre la suite mínima
  `:app:testUniversalFossDebugUnitTest` (FOSS debug, JVM puro, sin secrets — las claves caen
  a los defaults embebidos); (c) gate verificado en primera persona: YAML validado con
  pyyaml y el comando re-corrido fresco con `--rerun`: 630 tests, 0 fallos, 0 errores,
  0 skipped, 53 suites (log `build-fase17.txt`); (d) SearchVideoTest (:innertube, red real
  sin assertions) neutralizado con @Ignore — `:innertube:test` verde, XML skipped=1 en
  0.006 s sin tocar red (log `build-fase17-innertube.txt`); (e) pipeline estático sólido:
  secrets por env, keystore fallback detectable por pre-publish-check, google-services
  deshabilitado a propósito, artifacts versionados, release solo por tag. Diferidos: lint en
  CI (primero pagar la deuda lint en FASE 22) y HALLAZGO-018 evaluado → recomendada
  integridad de contenido (config firmada) en vez de certificate pinning para no romper la
  auto-reparación de player_configs.json (decisión FASE 22). Recomendado al dueño: branch
  protection en main con checks requeridos + Dependabot opcional. Ver RESULTADOS DE LA
  FASE 17 en `SUPER AUDITORIA DE MI CODIGO ANDROID.md`. Sigue FASE 18 (privacidad).
- **2026-08-24 (tarde): ✅ SÚPER AUDITORÍA — FASE 1 (INVENTARIO COMPLETO) TERMINADA.**
  Resultados R1–R9 dentro de `SUPER AUDITORIA DE MI CODIGO ANDROID.md` (3 agentes en paralelo):
  16 módulos, componentes de manifiesto, flavors, ~60 rutas de navegación, diálogos, widgets,
  workers/FGS/loops, almacenamiento y flujos críticos. Divergencia clave: la UI NUEVA
  (`ui/newui/`, 43 archivos) no está cubierta por `docs/UI_INVENTORY.md`.
  ⚠️ **HALLAZGO-008 (CRÍTICO para publicar, ABIERTO):** el build type `release` firma con el
  keystore DEBUG (app/build.gradle.kts:391-401). Es un diagnóstico temporal del 2026-08-19:
  todos los release firmados con el certificado real "JR MUSIC PRO" fallaban en la resolución
  de streams y el build con keystore debug funcionaba; se aisló el certificado como variable.
  Revertir a la keystore release es OBLIGATORIO antes de publicar (decisión del dueño; puede
  reabrir el fallo de streams). Siguiente fase: FASE 2 dependencias.
- **2026-08-24 (tarde, 2): ✅ SÚPER AUDITORÍA — FASE 2 (DEPENDENCIAS) TERMINADA.**
  Inventario completo (catálogo + 17 build.gradle.kts), repositorios/plugins/fuerzas y escaneo
  OSV por los 25 paquetes fijados (`osv-query.ps1` → `build-osv-api.txt`): solo jsoup aparecía
  como vulnerable (GHSA-pmhh-3w7g-xqp8 / CVE-2026-71497, rango 1.14.3–1.22.2).
  🔍 **VERIFICACIÓN PROFUNDA — HALLAZGO-009 cerrado SIN exposición real:** el pin del catálogo
  decía jsoup 1.22.2, PERO el grafo resuelto ya entregaba 1.23.1 porque `BravePipeExtractor`
  (pin fa5d4a8b4c) depende directamente de jsoup 1.23.1 y Gradle resuelve al mayor
  (`1.22.2 -> 1.23.1`). Prueba en el binario: el APK de BETA-001 contiene `HtmlTagOptions`
  (clase que solo existe en jsoup ≥1.23.1) en classes38.dex → la CVE NUNCA estuvo expuesta en
  el artefacto entregado. Esto también explica por qué los rebuilds quedaban UP-TO-DATE: el
  classpath resuelto no cambió. Aun así se alineó el pin del catálogo 1.22.2 → 1.23.1
  (defensa en profundidad: evita regresión silenciosa si BravePipe se elimina).
  Nuevos hallazgos abiertos: HALLAZGO-010 (mirror `maven.aliyun.com` en la cadena de resolución;
  además mantiene resoluble ffmpeg-kit EOL — decisión del dueño), HALLAZGO-011 (sin lockfile ni
  verification-metadata.xml — candidato FASE 17), HALLAZGO-012 (entradas muertas del catálogo:
  youtubedl-android, org.json, ktor-client-retry, material-icons @1.11.0; doble pin
  kotlinx-serialization; okhttp hardcode en migration). Siguiente: FASE 5 (almacenamiento).
- **2026-08-24 (tarde, 3): ✅ SÚPER AUDITORÍA — FASE 3 (SEGURIDAD ESTÁTICA) TERMINADA.**
  Ejecutada con 3 agentes en paralelo (SQL, validación de entradas/archivos, cripto/TLS), cada
  afirmación clave re-verificada leyendo el código. Resultados completos en la sección
  "RESULTADOS DE LA FASE 3" de `SUPER AUDITORIA DE MI CODIGO ANDROID.md`.
  ✅ **SQL VERDE:** ninguna inyección explotable — toda la superficie pasa por Room con
  parámetros enlazados o strings estáticos; las búsquedas de usuario usan `LIKE '%' || :query || '%'`
  sobre bind param; el `VACUUM INTO` del backup escapa comillas correctamente. Dos interpolaciones
  teóricas de segundo orden en migraciones one-shot antiguas (`MusicDatabase.kt:518/530`) — NO se
  tocan: reescribir migraciones ya ejecutadas arriesga más de lo que protege.
  ✅ **SIN zip-slip ni path traversal:** los dos lectores de ZIP (restore y updater) escriben
  siempre a destinos fijos; el updater además sanitiza la versión de red y verifica firma antes de
  instalar. TLS limpio (cleartext OFF, cero TrustManager/HostnameVerifier inseguros).
  ⚠️ Nuevos hallazgos abiertos:
  - **HALLAZGO-013 (MEDIA):** la cookie de sesión de Google (`innerTubeCookie`) y el `sp_dc` de
    Spotify viven en TEXTO PLANO en el DataStore, mientras los tokens de Tidal/Qobuz del mismo
    código SÍ usan EncryptedSharedPreferences (Keystore). Fix propuesto: migración a store cifrado
    con camino one-time — RIESGOSO (login y reproducción dependen de la cookie), decide el dueño.
  - **HALLAZGO-014 (BAJA):** deep links sin validar (`?list=a%2Fb`) crashean la app vía
    `navigate()` sin `runCatching` — provocable por cualquier app. Fix barato candidato a beta.
  - **HALLAZGO-015 (BAJA):** `provider_paths.xml` expone almacenamiento externo y caches completos
    (defensa en profundidad; no explotable hoy).
  Siguiente: FASE 5 (almacenamiento).
- **2026-08-24 (tarde, 4): ✅ SÚPER AUDITORÍA — FASE 5 (ALMACENAMIENTO) TERMINADA.**
  Inventario completo (DataStore único ~343 claves, 14 SharedPreferences XML, filesDir/cacheDir/
  externo, Room, higiene de temporales, reglas de backup). Resultados en la sección
  "RESULTADOS DE LA FASE 5" de `SUPER AUDITORIA DE MI CODIGO ANDROID.md`.
  ✅ **Lo grueso está bien:** las reglas de backup excluyen el DataStore completo (la cookie de
  sesión), `jr_license.xml` y los caches de exoplayer/descargas — en cloud-backup Y device-transfer.
  `song.db` viaja por decisión de producto documentada (la biblioteca sobrevive el cambio de
  teléfono; lo restaurado se trata como no confiable). Temporales de restore/updater limpiados en
  todas las rutas; los login-WebViews limpian cookies tras logout; cero `allowMainThreadQueries`.
  ⚠️ Nuevo hallazgo abierto:
  - **HALLAZGO-016 (MEDIA):** por el default de inclusión viajan al backup de la nube de Google,
    en silencio: `song_graph.xml` y `artist_genres.xml` (metadatos de gusto), `app.log*` y
    `persistent_*.data` (cola). Fix propuesto barato: excluir cachés reconstruibles y logs;
    `persistent_*.data` a su decisión (restaura la cola en teléfono nuevo).
  Además: clave de sesión Last.fm (`PreferenceKeys.kt:474`) descubierta en texto plano → anexada
  a HALLAZGO-013. `jr_license.xml` en plano reportado pero es zona protegida (sin cambios).
  Siguiente: FASE 6 (red).
- **2026-08-24 (tarde, 5): ✅ SÚPER AUDITORÍA — FASE 6 (RED) TERMINADA.**
  Dos agentes en paralelo (infraestructura de ~40 clientes HTTP + datos en tránsito/auth).
  Resultados en la sección "RESULTADOS DE LA FASE 6" de `SUPER AUDITORIA DE MI CODIGO ANDROID.md`.
  ✅ **Postura sólida:** todos los secretos viajan en headers/body, cero logging de secretos en
  release (redacción centralizada con test), cero tráfico cleartext, 401/403 manejados sin loops
  de credencial quemada.
  ⚠️ Nuevos hallazgos abiertos:
  - **HALLAZGO-017 (MEDIA):** el login de Qobuz manda email y PASSWORD como query parameters
    (`QobuzApi.kt:62-66`) — única credencial real en URL del repo. Sin fix posible: la API oficial
    de Qobuz es GET-only → requiere su aceptación formal del riesgo.
  - **HALLAZGO-018 (MEDIA):** cero certificate pinning en todo el repo; los canales de config
    remota que alimentan auth/reproducción (qobuz_config, player_configs, gist TOTP Spotify,
    updater, Worker de licencia) dependen solo de la CA del sistema. Candidato FASE 17/22.
  - **HALLAZGO-019 (MEDIA, disponibilidad):** ~22 clientes HTTP sin timeouts, varios en el path
    crítico de reproducción. Fix barato con patrón ya existente (`QobuzHiRes.kt`); candidato a beta.
  Siguiente: FASE 7 (auth/cripto — mayormente cubierta ya por FASE 3, cierre formal).
- **2026-08-24 (tarde, 6): ✅ SÚPER AUDITORÍA — FASE 7 (AUTH/CRIPTO) TERMINADA.**
  Ejecutada en primera persona (el agente delegado falló por filtro de contenido del proveedor).
  Resultados en "RESULTADOS DE LA FASE 7" de `SUPER AUDITORIA DE MI CODIGO ANDROID.md`.
  ✅ **Ciclo de vida auth SÓLIDO:** los 4 logouts borran la credencial de verdad
  (`App.forgetAccount` es choke point de Google/InnerTube: BD + DataStore + memoria + WebView),
  Tidal refresh bajo mutex (caso modelo), Spotify 401→refresh→1 reintento (sin loops), cero
  biometría/PIN (superficie que no existe), Keystore bien usado en las bóvedas Qobuz/Tidal,
  licencia con gracia offline acotada a 3 días (zona protegida, solo lectura).
  ⚠️ Nuevo hallazgo abierto:
  - **HALLAZGO-020 (BAJA):** el logout solo borra en local; nadie revoca en el servidor. Caso
    concreto: Last.fm no llama `auth.logout`, la session key huérfana sigue válida en su web.
    Fix barato candidato FASE 22; el resto se acepta (tokens expiran solos).
  Siguiente: FASE 8 (componentes/IPC — cierre formal).
- **2026-08-24 (tarde, 7): ✅ SÚPER AUDITORÍA — FASE 8 (COMPONENTES/IPC) TERMINADA.**
  Cierre formal con re-verificación directa (trabajo de FASE 3/4 consolidado). Resultados en
  "RESULTADOS DE LA FASE 8" de `SUPER AUDITORIA DE MI CODIGO ANDROID.md`.
  ✅ **Superficie IPC SANA, sin hallazgos nuevos:** los 10 componentes exportados del manifiesto
  son el mínimo que Android exige (launcher + alias, MusicService/MediaSession, MediaButtonReceiver,
  4 widget receivers, tile protegido con permiso de sistema); PendingIntents inmutables
  (HALLAZGO-004 LIMPIO); sendBroadcast solo protocolo AudioEffect de sistema + explícito de widget;
  URI grants solo en compartir iniciado por el usuario; notificaciones públicas sin datos sensibles.
  Siguen abiertos 014 (deep-link crash) y 015 (provider_paths).
  Siguiente: FASE 10 (arquitectura — cierre formal).
- **2026-08-24 (tarde, 8): ✅ SÚPER AUDITORÍA — FASE 10 (ARQUITECTURA) TERMINADA.**
  Cierre formal; HALLAZGO-001 (auto-subscribe) ya corregido en `88cf0e1` y PROBADO en dispositivo
  (BETA-001 VERDE). Resultados en "RESULTADOS DE LA FASE 10".
  ✅ Modularización sana (16 módulos acíclicos), Hilt en todo, errores con degradación elegante.
  ⚠️ Nuevo hallazgo abierto:
  - **HALLAZGO-021 (BAJA, mantenibilidad):** archivos gigantes medidos — `MusicService.kt`
    10 953 líneas (god object y hotspot #1 del registro de regresiones), Player.kt 3 543,
    AuraPlayer.kt 2 585, Lyrics.kt 2 514, HomeScreen.kt 2 490. Candidato a potenciación FASE 22:
    dividir incrementalmente CON tests primero; NUNCA refactor en caliente.
  Siguiente: FASE 12 (concurrencia — cierre formal).
- **2026-08-24: ✅ MODERNIZACIÓN DE DEPENDENCIAS COMPLETA (cadena de la auditoría `docs/audit/MODERNIZACION.md`).**
  Cinco pasos, cada uno con build verde y commit propio: Gradle 9.6.1 (`2e01a84`) → AGP 9.2.0
  (`22eb193`) → Kotlin 2.4.0 + KSP 2.3.9 + Hilt **2.60.1** (`e40009b`) → batch seguro + Compose 1.11.0
  (`63beeb2`, verde en foss Y gms) → youtubedl-android 0.18.1 solo-catálogo (`6cbd321`).
  Hallazgos clave: (1) Hilt 2.59.2 NO sirve con Kotlin 2.4 — su `kotlin-metadata-jvm` topea en
  metadata 2.3.0; Dagger 2.60.1 lo desempaqueta/actualiza y arregla el fallo. (2) Las entradas
  `youtubedl-android-*` del catálogo no las usa ningún módulo (remanente del fork), bump sin efecto
  runtime. Intocados según auditoría: material-icons-extended 1.7.8, materialKolor, media3, room,
  Material3 alpha, Superpowered, `license/`. Diferidos (rojo): ffmpeg-kit EOL, rework de Haze.
  Pendiente: prueba beta del dueño con el APK debug universalFoss (estabilidad extendida + descargas no aplican).
- **2026-08-23 (madrugada): 🔴 MURO ANTI-BOT ROMPIÓ LA REPRODUCCIÓN → ✅ FALLBACK BRAVEPIPE INTEGRADO Y VERIFICADO EN CELULAR.**
  Tras el login del dueño, la extracción PipePipe cayó: con cookie → `ExtractionException: android_vr
  player response is not valid`; anónima → `AntiBotException: Sign in to confirm you're not a bot`; y el
  TeamNewPipe v0.25.2 que mantenía la app sonando empezó a devolver CERO streams en ese dispositivo/IP
  (el muro se endureció). Todo caía a las URLs InnerTube quemadas (sonda 206 / fetch real 403) → loop
  infinito. Fix 1 (commit `023e65f`): forzar extracción anónima + capa de emergencia TeamNewPipe.
  Fix 2 (este estado): **BravePipeExtractor** `com.github.maxrave-dev:BravePipeExtractor` pin `fa5d4a8b4c`
  — el MISMO fallback que usa SimpMusic cuando su PipePipe falla (`Extractor.android.kt` de maxrave-dev/core).
  Fork de TeamNewPipe con cliente de extracción ANDROID (no WEB, no limitado al itag 18); usa el mismo
  paquete `org.schabi.*`, así que REEMPLAZA al TeamNewPipe stock (clases duplicadas, no conviven).
  Renombrado `pages/TeamNewPipe.kt` → `pages/BraveNewPipe.kt` (clases `BraveNewPipe*`). Force de nanojson
  `c7a6c1c08d…` en todas las configuraciones desde el root (igual que SimpMusic; sin él el fallback crashea
  con `NoSuchMethodError` porque PipePipe trae un nanojson viejo). Orden de `newPipePlayer`: PipePipe →
  BravePipe. BUILD SUCCESSFUL (`build-bravepipe2.txt`).
  **✅ VERIFICADO EN CELULAR (dueño reconectó ~19:12 local):** install + play. PipePipe sigue muriendo
  con `AntiBotException`, pero **BraveNewPipe devolvió 15 streams** (itags 139/140/18/137/248/136/247/135/
  244/134/243/133/242/160/278 — set adaptativo COMPLETO vía cliente ANDROID, ya no solo itag 18),
  AudioTrack activo y chunks de 5 MB con 206 sostenidos, cero 403. **El dueño confirmó: suena,
  varias canciones funcionan y el toggle música↔video también.** Si BravePipe también cae en su IP algún día,
  siguiente paso: login correcto para PipePipe (cliente `mweb` o PoTokenProvider del fork).
- **2026-08-23 (noche, tarde): ✅ EQ ARREGLADO + VIDEO RESTAURADO POR ORDEN DEL DUEÑO.**
  EQ: la causa era la ausencia de `SUPERPOWERED_LICENSE_KEY` en el build debug; clave copiada
  desde `AURA FENIX\aura-simpmusic\local.properties`; verificado `engine=HEALTHY
  dsp_probe=passed` en vivo. NO hizo falta otra licencia. Commits `3aa45cd` (toggle video),
  `ff3eaa7` (log build con licencia).
  Video: el fallback NewPipe muxed (`ab46318`) se revirtió por queja de calidad (`a288326`),
  pero el dueño probó el build instalado, confirmó que "YA REPRODUCE VIDEO" y dio LUZ VERDE
  para restaurarlo (revert-del-revert) y además trabajar calidad adaptativa.
- **EN CURSO (luz verde del dueño 2026-08-23 noche): CALIDAD DE VIDEO.**
  **✅ PASO 2 COMPLETADO (commit `c61b4cf`, BUILD SUCCESSFUL 05:08):** PipePipeExtractor
  integrado como reemplazo total de TeamNewPipe v0.25.2 — `com.github.maxrave-dev:PipePipeExtractor`
  pin `208e43b184` (el extractor que usa SimpMusic; su cliente de extracción ANDROID_VR no está
  quemado y devuelve los formatos adaptativos COMPLETOS sin login). Cambios: `innertube/pages/NewPipe.kt`
  reescrito (paquetes `dev.maxrave.pipepipe.*`, `executeAsync`, `Response` 6 args, URL
  `music.youtube.com`, `setTokens(cookie)` para la llamada suplementaria WEB_REMIX de itags
  Premium); setter `YouTube.cookie` alimenta los tokens (login y arranque quedan sincronizados);
  `YTPlayerUtils.adaptiveVideoStreamNewPipe` reemplaza `muxedVideoStreamUrlNewPipe`: elige el
  mejor video-only H.264 dentro del tope de red (WiFi 720p / datos 360p / TV su tope) y lo
  MERGEA con el audio del tema; muxed 22/18 solo como último recurso; MusicService actualiza
  la bandera `newPipeMuxedVideoIds` según `isMuxed`; protobuf-java excluido del fork
  (la app usa protobuf-javalite); ProGuard actualizado al paquete del fork.
  **PENDIENTE: instalar el APK en el celular (desconectado del USB ahora) y verificar en logcat**
  que el fallback reporte itags adaptativos (136/137 + 250/251) en vez de solo `[18]`, y que el
  video en WiFi suba a 720p. Si la extracción sigue limitada → BravePipeExtractor (diferido).
  **PASO 1 (cero código, sigue abierto):** el dueño inicia sesión desde la hoja de cuenta
  («Iniciar sesión») — con cookie el fork añade WEB_REMIX (itags 141/774) y TVHTML5 logueado
  podría resolver por InnerTube directamente.
  **(b) ✅ TARDANZA DEL TOGGLE MÚSICA↔VIDEO ARREGLADA (commit `7ebdf7e`, BUILD SUCCESSFUL 05:22):**
  los 3 sitios de resolución de video (`applyVideoToCurrent`, `prebuildNextVideoItem`,
  `prefetchCurrentVideoUrl`) ahora prueban PipePipe PRIMERO (no quemado, responde en una sola
  llamada de extracción) y solo caen a InnerTube si el extractor no devuelve nada — antes el
  toggle esperaba a que InnerTube quemado agotara su presupuesto multi-cliente. Espejo del
  path de audio, donde el extractor ya es la fuente primaria. Bandera muxed intacta.
  **Verificar en celular junto con (a).**
  **NUEVA PETICIÓN DEL DUEÑO (2026-08-23 ~04:40):** actualizar TODO lo actualizable del
  proyecto (dependencias, plugins, SDK) a lo último, de forma segura y verificada.
  Investigación login completada (reporte agente): SimpMusic loguea con WebView a
  `accounts.google.com/ServiceLogin?ltmpl=music...`, señal de éxito = onPageFinished en
  `music.youtube.com/`, persiste cookie/page_id en DataStore, player request SIEMPRE
  setLogin=true con WEB_REMIX `1.20260304.03.00`, y su quirk de timestamp
  (`epochSeconds / 1000`) — Aura ya replica la fórmula estándar; si algo falla con login se
  prueba el quirk. No hay re-login automático en SimpMusic (logout manual).
- **2026-08-23 (noche): ✅ LA APP YA REPRODUCE (primera reproducción confirmada).**
  `state=PLAYING`, posición avanza, fetches `206` por chunks de 5 MB. El dueño lo confirmó
  en vivo ("SI YA REPRODUCE"). Commits: `807b2d8` (port WIP), `4df5936` (fallback itag 18).
- **Causa raíz FINAL del 403 (confirmada con evidencia A/B en el mismo dispositivo):**
  el bloqueo NO era de la URL en la validación (proba de 1 byte → 206) sino del FETCH REAL:
  las URLs directas de InnerTube (ANDROID_VR/IOS) son clase quemada — Google las rechaza
  en la petición de bytes real (403 con Range grande, 206 con Range de 1 byte). Las URLs de
  la extracción NewPipe son clase distinta y SÍ pasan el fetch (206 en chunks de 5 MB).
- **Arreglo vigente:** NewPipe (`StreamInfo.getInfo`) es la fuente PRIMARIA de URLs en
  `findUrlOrNull` (modelo SimpMusic verificado en fuente: maxrave-dev/core dae3ce98 — sus
  URLs de stream vienen de NewPipe, InnerTube solo da metadatos). Selección: itag exacto →
  cualquier itag de audio útil (`AUDIO_ITAG_PREFERENCE`, incluye muxed 22/18 de emergencia).
  Las URLs NewPipe traen `n=` ya desofuscado → se salta el n-transform propio.
- **Limitación actual (SIGUIENTE PASO):** la extracción en el dispositivo devuelve SOLO
  `itags=[18]` (MP4 360p con audio embebido, ~calidad baja) — YouTube limita los formatos
  al contexto de extracción (bot-limit). Se reproduce a calidad baja. Para recuperar la
  calidad alta/Hi-Res hay que mejorar la extracción: portar PipePipeExtractor (fork de
  SimpMusic, `com.github.maxrave-dev:PipePipeExtractor`, pide music.youtube.com y verifica
  itags), y/o login con cuenta (SAPISIDHASH, el anti-bot real de SimpMusic según su fuente).
- **Purga cumplida (mandato del dueño):** proveedores no-SimpMusic desactivados
  (`NON_SIMPMUSIC_PROVIDERS_ENABLED=false`: Qobuz/Saavn); `VIDEO_PROVIDERS_ENABLED=true`
  RESTAURADO (el dueño exige el toggle música↔video; el video usa el mismo proveedor
  YouTube vía NewPipe); persistencia de URLs desactivada (fresh resolve por canción).
- Mandato del dueño vigente: reproducir IGUAL que SimpMusic, SOLO con sus proveedores.
  Él confirmó que SimpMusic v1.7.0 reproduce bien HOY en el mismo celular y red.
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
- media3 1.10.1 · ktor 3.4.0 (sin retrofit) · PipePipeExtractor 208e43b184 + BravePipeExtractor
  fa5d4a8b4c (jitpack; nanojson forzado a c7a6c1c08d en el root) · jsoup 1.22.1 ·
  brotli · Kotlin 2.3.10 · AGP 9.0.0 · JDK 21. Superpowered nativo sin artefacto maven.

## 📋 Roadmap Inmediato

- [x] Mapa completo de la capa de streaming actual.
- [x] Investigación arquitectura SimpMusic + diff por pilar.
- [x] Implementación P1/P2/P4 (P3 ya cubierto) + build verificado.
- [x] Auditoría adversarial (0🔴 1🟡 corregido en código, pendiente recompilar).
- [x] Instalar en dispositivo vía adb → **la app NO reproduce: cascada InnerTube entera falla**.
- [x] Diagnóstico con logcat: clientes InnerTube quemados (herencia Echo Music).
- [x] Ground truth de constantes extraída del APK de SimpMusic 1.7.0 instalado.
- [x] Reconstruir clientes InnerTube con constantes de SimpMusic + purga de proveedores.
- [x] Descubrir el modelo REAL de SimpMusic (fuente: URLs de NewPipe, fetch OkHttp plain).
- [x] NewPipe como fuente primaria de URLs + fallback de itags de audio.
- [x] **Rebuild + instalar + verificar que reproduce DE VERDAD → ✅ CONFIRMADO (itag 18, 206, PLAYING).**
- [x] Commits de los cambios (`807b2d8`, `4df5936`). NUNCA publicar tag/release sin permiso.
- [x] **Recuperar calidad alta (PARCIAL RESUELTO)**: PipePipeExtractor + BravePipeExtractor ya
      integrados y funcionando (set adaptativo completo vía cliente ANDROID: itags 139/140/18/
      137/248/136/247/...; audio 139/140 + video hasta 720p). Falta para Hi-Res/Premium: login
      con cuenta (SAPISIDHASH / WEB_REMIX), diferido.
- [x] Verificar estabilidad (canciones completas, saltos, crossfade): CONFIRMADO por el dueño en
      la prueba remota de BETA-001 (2026-08-24).
- [x] Entregar beta + actualizar esta memoria: BETA-001 entregada en la carpeta de nube y probada.

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
- **2026-08-23 (8):** Informe del investigador + verificación en fuente (maxrave-dev/core
  dae3ce98): SimpMusic obtiene las URLs de stream de NEWPIPE (StreamInfo), InnerTube solo
  metadatos; fetch con OkHttp plain. Corrección de rumbo: NewPipe restaurado como fuente
  primaria en findUrlOrNull; purga de proveedores no-SimpMusic (Qobuz/Saavn/video);
  persistencia de URLs → fresh resolve. Commit `807b2d8`.
- **2026-08-23 (9):** Diagnóstico decisivo: NewPipe devuelve solo `itags=[18]` en el
  dispositivo (bot-limit) y el 18 no estaba en la preferencia → se caía a las URLs quemadas.
  Evidencia A/B del 403: sonda de 1 byte → 206, fetch real de 5 MB → 403 (clase de URL
  InnerTube bloqueada en la petición de bytes). Commit `4df5936`.
- **2026-08-23 (10): ✅ PRIMERA REPRODUCCIÓN.** Con fallback muxed itag 18: fetches 206 por
  chunks de 5 MB, state=PLAYING, posición avanza; confirmado por el dueño en vivo.
  Pendiente: recuperar formatos adaptativos de calidad alta (PipePipeExtractor y/o login).
- **2026-08-23 (11): ✅ EQ ARREGLADO + video en curso.** Causa del EQ muerto = licencia
  Superpowered ausente en el build debug (no un fallo de cableado). Clave copiada desde la
  copia Fénix; verificado `engine=HEALTHY dsp_probe=passed` en vivo. El dueño reporta que el
  toggle música↔video solo da audio → causa: resolución de video por InnerTube quemado;
  implementado fallback NewPipe muxed (itag 22→18) en los 4 puntos de resolución de video
  con bandera anti-doble-audio. Commits: `3aa45cd`, `ff3eaa7`.
- **2026-08-23 (12): 🔴→🟡 Muro anti-bot + fallback BravePipe.** Con el dueño logueado,
  PipePipe murió: con cookie → 'android_vr player response is not valid', anónima →
  AntiBotException; y el TeamNewPipe stock que sonaba empezó a dar cero streams en su IP.
  Fix 1 (`023e65f`): extracción anónima forzada + capa TeamNewPipe. Fix 2 (este commit):
  BravePipeExtractor `fa5d4a8b4c` REEMPLAZA al TeamNewPipe stock (el fallback real de
  SimpMusic, cliente de extracción ANDROID) + force de nanojson `c7a6c1c08d` en el root.
  BUILD SUCCESSFUL (`build-bravepipe2.txt`); pendiente verificar en el celular — el dueño
  salió y lo desconectó.

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
