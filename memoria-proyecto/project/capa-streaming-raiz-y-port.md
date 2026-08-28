---
name: Capa de streaming — causa raíz del bloqueo (Echo Music quemado) y reconstrucción BravePipe
description: Root cause 2026-08-23 (clientes InnerTube quemados por herencia de Echo Music) + ground truth del APK de SimpMusic + port BravePipe que RESTAURÓ la reproducción (2026-08-23/24) + modernización de dependencias
type: project
---

**Estado actual: reproducción RESTAURADA y confirmada por el dueño (2026-08-23/24).**
Lo de abajo es la historia completa: primero la causa raíz (estado al 2026-08-23,
ANTES del fix, cuando la app no reproducía nada) y después la reconstrucción.

## Causa raíz (confirmada con logcat 2026-08-23)

1. Aura es fork de "Echo Music", app bloqueada y eliminada por YouTube (contexto
   dado por el dueño). Los clientes InnerTube/API keys heredados están QUEMADOS.
2. Cadena de fallo completa por canción (ej. videoId 5y0ZTZoKLNw): MAIN_CLIENT
   ANDROID_VR → `LOGIN_REQUIRED "Accede para confirmar que no eres un bot"`;
   TVHTML5 se salta (requiere login); ANDROID devuelve formatos sin URL
   (hasUrl=false, hasCipher=false); IOS da URL directa pero HEAD=403 con poToken
   presente (len 800); WEB → UNPLAYABLE. Total 12 intentos, ok=false,
   StreamResolutionException.
3. El poToken BotGuard SÍ se genera (ok=true len=120) pero YouTube lo rechaza.
   El WebView de cipher gira con ReferenceError (funciones inexistentes) pero es
   secundario: los formatos llegan sin cipher.

**Why:** los clientes/keys InnerTube heredados están quemados por la asociación
con Echo Music. El dueño ordenó reconstruir la capa basándose en SimpMusic (que
SÍ reproducía en su celular, confirmado 2026-08-23) con autorización total:
"cambia todo lo que tengas que cambiar para que reproduzca sí o sí".

**How to apply:** el port debía conservar los contratos de Aura (PlaybackData,
lambda de ResolvingDataSource) y los 131 fixes del registro. Evidencia cruda
original: logcat en %TEMP%\aura-logcat-full.txt (sesión 2026-08-23).

## Ground truth del APK de SimpMusic v1.7.0 instalado (dex strings, 2026-08-23)

- ANDROID_VR 1.65.10 UA completo: `com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip` — SIN Cronet (Aura usaba 1.43.32/1.61.48 "Quest 3" + Cronet; la principal diferencia quemada).
- IOS 19.45.4 UA completo: `com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)`
- ANDROID 21.03.36 UA PARCIAL en dex: `com.google.android.youtube/21.03.36 (Linux; U; Android 15; ` (el resto se concatena en runtime o es constante muerta).
- VISIONOS 1.02 UA parcial: `com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; `
- ANDROID_MUSIC (?): `com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip`
- MWEB UA: `Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)`
- Versiones web: WEB_REMIX 1.20260304.03.00 (legacy 1.20250310.01.00), WEB 2.20250312.04.00, TVHTML5 7.20250312.16.00.
- poToken vía `serviceIntegrityDimensions(poToken=...)` igual que Aura; también existe `yapi.vyper.me` (¿proveedor externo poToken BGUtil?).
- Endpoint player: `https://music.youtube.com/youtubei/v1/player?prettyPrint=false` (igual que Aura).

## Reconstrucción (2026-08-23/24) — BravePipe

El muro anti-bot rompió la reproducción otra vez durante el 2026-08-23: con
cookie, PipePipeExtractor falla (`android_vr player response is not valid` —
YouTube sustituye la respuesta autenticada); anónimo (fix emergencia `023e65f`)
falla con `AntiBotException`; TeamNewPipe v0.25.2 pasó a devolver lista VACÍA
(bot-limit de la IP subió de grado). La resolución caía a URLs directas
InnerTube (quemadas: probe 206, fetch 403) en loop infinito. El revert a
`88b81a3` pedido por el dueño NO arreglaría (vía seca en su IP — demostrado).

**INTEGRADO (commit `5e62f4b`):** BravePipeExtractor
`com.github.maxrave-dev:BravePipeExtractor` pin `fa5d4a8b4c` — el MISMO fallback
de SimpMusic (Extractor.android.kt de maxrave-dev/core). Fork de TeamNewPipe con
cliente ANDROID (no WEB, no limitado a itag 18), mismo paquete `org.schabi.*` →
REEMPLAZÓ a TeamNewPipe stock en libs.versions.toml (clases duplicadas si
conviven). Cambios: `pages/TeamNewPipe.kt` → `pages/BraveNewPipe.kt`; fallback en
`NewPipe.kt` PipePipe → BravePipe; force de nanojson `c7a6c1c08d…` desde root
build.gradle.kts (sin él, NoSuchMethodError). Pendiente entonces (mantener
simple): verificación de itags (audio 250/251/774/141, video 137/136/134) +
HEAD-check que SimpMusic sí hace.

**CONFIRMADO 2026-08-23 ~19:20 (celular e6f2c8eb):** BraveNewPipe devolvió 15
streams (set adaptativo completo), AudioTrack activo, chunks 5 MB con 206
sostenidos y CERO 403 (PipePipe sigue AntiBotException; BravePipe lo salva).
Dueño: suena bien, varias canciones OK, toggle música↔video OK → REPRODUCCIÓN
RESTAURADA. Si BravePipe cae algún día, siguiente cartucho = login correcto para
PipePipe (cliente `mweb` vía setYoutubePlayerClient, o PoTokenProvider del fork).

**Hallazgos PipePipe:** tokens = String plano (cookie completa); con cookie el
fork añade Cookie + SAPISIDHASH + X-Origin + DNT a TODOS los /youtubei/v1/player;
exige SAPISID o __Secure-3PAPISID; poToken/visitorData opcionales vía
`NewPipe.setYoutubeSessionPoTokenProvider` (SimpMusic NO lo instala); SimpMusic
usa `ServiceList.YouTube.tokens = cookie ?: ""` en su logIn, igual que Aura — su
ventaja era el fallback BravePipe, ya portado. setTokens de Aura sigue forzando
anónimo (emergencia `023e65f`) hasta verificar extracción autenticada.

## Modernización de dependencias COMPLETA (2026-08-24)

Orden del dueño "has lo que quedó pendiente en el orden". Cadena de
`docs/audit/MODERNIZACION.md`, un build verde + commit por paso: Gradle 9.6.1
(`2e01a84`) → AGP 9.2.0 (`22eb193`) → Kotlin 2.4.0 + KSP 2.3.9 + Hilt **2.60.1**
(`e40009b`; Hilt 2.59.2 NO basta — su kotlin-metadata-jvm topea en metadata
2.3.0) → batch seguro + Compose 1.11.0 (`63beeb2`, verde en foss Y gms: coil
3.5.0, ktor 3.5.0, protobuf 4.34.2, Firebase BOM 34.15.0) → youtubedl-android
0.18.1 solo-catálogo (`6cbd321`; ningún módulo usa esas entradas — remanente del
fork, sin efecto runtime). Intocados: material-icons-extended 1.7.8, materialKolor,
media3, room, Material3 alpha, Superpowered, license/. Diferidos en rojo:
ffmpeg-kit EOL, rework Haze.

**Recetas:** Build: Corretto JDK 21 (`set "JAVA_HOME=C:\Program Files\Amazon Corretto\jdk21.0.12_9"`),
verificar literal BUILD SUCCESSFUL (los pipes mienten); si falla JdkImageTransform
o "Unable to delete classes.jar" → `gradlew --stop` y reintentar. ADB:
`C:\Users\AURA\AppData\Local\Android\Sdk\platform-tools\adb.exe -s e6f2c8eb`.

**Reglas duras vigentes:** SOLO proveedores de SimpMusic; NO degradar calidad sin
orden; commit SIEMPRE; nunca push/tag sin permiso; NUNCA loguear datos de usuario
(la cookie JAMÁS a logs). Nota RONDA 3 (2026-08-26): la fragilidad persiste —
PipePipe falla SIEMPRE en la IP del dueño (~1.7s perdidos por resolve) y se añadió
`ExtractorCircuitBreaker` (3 fallos → directo a BraveNewPipe 10 min); detalle en
`ronda-3-reportes-dueno.md`.
