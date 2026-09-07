# MEMORIA.md — Aura Hi-Res v2

> Estado vivo del proyecto. Cualquier agente/herramienta que trabaje aquí debe leer este archivo
> primero y mantenerlo actualizado tras cada hito. Propietario: el dueño (Product Owner);
> mantenimiento: Tech Lead (agente IA de desarrollo).

## Arquitectura

- **App Android de música Hi-Res** (Kotlin + Jetpack Compose, Material 3), Gradle multi-módulo.
- Paquete: `com.music.echo` (herencia del fork, NO cambiar); `applicationId`: `iad1tya.aura.music`.
- Repo: `hck0n3/Aura-Hi-Res-v2` (identidad nueva, 2026-08-25, para cerrar HALLAZGO-008: keystore
  antigua `CN=JR MUSIC PRO` marcada por YouTube). Firma release: `CN=Aura Hi-Res v2`, alias `aurav2`.
- Módulos: `app` (UI Compose, `ui/newui/`), `eq/` (ecualizador + Superpowered), `playback/`
  (extracción R3 de lógica de reproducción), `utils/` (AppLogger persiste INFO+ a
  `filesDir/logs/app.log` — es el archivo que se comparte desde Ajustes), `license/`
  (puerta de suscripción — zona intocable), `cipher/` (decodificador local), `playlistimport/`
  (IA + importadores), `api/` (servicios externos).
- Sabores: `universalFoss` (sin Google) / `universalGms` (Cast + Crashlytics).
- Pantallas clave: `MainActivity` → `AuraShell` (nav + player sheet + overlays in-window
  `AuraInWindowDialog`). Glass/blur: haze 1.7.2 (patrones con fuente local, jamás hazeChild
  descendiente compartiendo state — historial sig-11 Samsung).

## Dependencias (de `gradle/libs.versions.toml`, verificadas 2026-09-07)

- Kotlin 2.4.0 · AGP 9.2.0 · Compose 1.11.0 · Material3 1.5.0-alpha18
- Media3 1.10.1 (ExoPlayer) · Room 2.8.4 · Hilt 2.60.1 · Ktor 3.5.0 · KSP 2.3.9
- haze 1.7.2 · coil 3.5.0 · Lottie 6.7.1 · WorkManager 2.10.2 · ffmpegKit 6.0-2
- Extractores: pipepipe f8982ca9e7 / bravenewpipe fa5d4a8b4c (YouTube)
- JDK 21 (toolchain fijo) · compileSdk 36 · build-tools 36 · NDK 27.0.12077973 · CMake 3.22.1

## Estado actual (2026-09-07)

- **Beta vigente: BETA-045 v2.0.39 / versionCode 998** (commit a96e816, 2026-09-05).
  APK entregado en `~/Desktop/betas apks/`.
- Suite de tests al cierre de la BETA-040: 994/994 en verde.
- Sin publicar en GitHub: 0 tags en el remoto, actualizador in-app no ve nada; todas las betas
  viven solo en la carpeta de betas del escritorio.
- Maratón 04-05/09: BETA-040→045 (buffer SimpMusic, caché a prueba de reinicios, login Spotify
  3 capas + cookie manual, video No-media-id curado, preset EQ 'Aura Hi-Res v2' +2.3dB,
  crossfade 8s, download chooser forceWindow, BootDiag en app.log).
- **Convivencia con otra herramienta**: los últimos commits (06-07/09, firma "Claude Code")
  mueven un submodule `src/graphify` (herramienta Python ajena a la app) y hacen revisión
  UI/UX sobre MainActivity/AuraShell/strings. `git status` muestra `m src/graphify`
  (cambios sin commit dentro del submodule). **Releer esos archivos antes de editarlos.**

## Problemas conocidos / lecciones (vigilancia permanente)

1. **Cadena de placebos**: TODO fix exige (a) traza al efecto, (b) validación en dispositivo,
   (c) auditor independiente si es complejo. "Compila y parece correcto" NO es un arreglo.
2. **Re-reportes "sigue igual"**: verificar siempre el APK instalado (aapt + strings del dex)
   antes de concluir que el fix llegó.
3. Archivos compartidos = fuente #1 de regresiones: `MusicService.kt`, `Player.kt`, `App.kt`,
   `utils/Utils.kt`, `Lyrics.kt`, `YTPlayerUtils.kt`, `BackupRestoreViewModel.kt`,
   `HomeScreen.kt`, `ArtistItemsViewModel.kt`, `Thumbnail.kt`.
4. Reglas duras: `docs/REGRESSION_REGISTRY.md` (30+ fallos con guardián archivo:línea) es
   lectura obligatoria antes de dar por bueno un cambio. Zonas intocables: `eq/`+`cpp/`
   (salvo pedido explícito) y `license/`.
5. Trampas de build: `./gradlew … | tail` devuelve el código de `tail`, no el de Gradle
   (mirar `PIPESTATUS[0]`); daemon corrupto → `./gradlew --stop` + retry.
6. Nada que muestree pantalla/red por frame mientras suena música (criterio térmico/batería).
7. `google-services.json` no existe y es correcto. Las claves Last.fm/Tidal/Qobuz embebidas en
   `app/build.gradle.kts` son deliberadamente públicas — no "arreglarlas".

## Siguiente paso

- **Plan guardado, pendiente de orden del dueño**: switch música↔video estilo YouTube Music en
  caliente (1 player, tracks del mismo item, `setTrackTypeDisabled(VIDEO)` sin cortar audio;
  merge en `createMediaSource` con URI audio + videoUrl preArmed; prefetch ya existe; ~7-13h).
- Mantener pendientes de dispositivo de la BETA-045: confirmar en el S26 Ultra el conjunto
  (Spotify 3 capas, video, preset EQ, crossfade 8s, chooser, mic-permiso).
- Esperar el feedback del dueño sobre BETA-045 antes de tocar nada más.

---

*Última actualización: 2026-09-07 — creado por el Tech Lead (Qwen Code). Fuente: memorias del
proyecto + git log verificado. Verificar contra `git log`/código antes de basar decisiones en
este archivo.*
