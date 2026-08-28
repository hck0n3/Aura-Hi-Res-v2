---
name: Causa raíz bloqueo streaming (Echo Music quemado)
description: Root cause 2026-08-23 del fallo total de reproducción en Aura (clients InnerTube bloqueados por herencia de Echo Music) y mandato de reconstrucción vía SimpMusic
type: project
---

La app NO reproduce nada. Causa raíz confirmada con evidencia de logcat el 2026-08-23:

1. Aura es fork de "Echo Music", app bloqueada y eliminada por YouTube (contexto dado por el dueño). Los clientes InnerTube/API keys heredados están QUEMADOS.
2. Cadena de fallo completa por canción (videoId de ejemplo 5y0ZTZoKLNw): MAIN_CLIENT ANDROID_VR → `LOGIN_REQUIRED "Accede para confirmar que no eres un bot"`; TVHTML5 se salta (requiere login, usuario no logueado); ANDROID devuelve formatos sin URL (hasUrl=false, hasCipher=false); IOS da URL directa pero HEAD=403 con poToken presente (len 800); WEB → UNPLAYABLE "Video no disponible". Total 12 intentos de cliente, ok=false. StreamResolutionException.
3. El poToken BotGuard SÍ se genera (ok=true len=120) pero YouTube lo rechaza igualmente. El WebView de cipher gira con ReferenceError (funciones de cipher inexistentes) pero es secundario: los formatos llegan sin cipher.

**Why:** Aura es fork de Echo Music, app que YouTube BLOQUEÓ y eliminó de todos lados (revelado por el dueño el 2026-08-23). Los clientes/keys InnerTube heredados están quemados por esa asociación. El dueño ordenó reconstruir la capa de streaming basándose en SimpMusic (que SÍ reproduce en su celular hoy, confirmado 2026-08-23) con autorización total: "cambia todo lo que tengas que cambiar para que reproduzca sí o sí".

**How to apply:** La reconstrucción debe portar el enfoque de resolución de SimpMusic (clientes InnerTube vigentes, manejo de visitorData/poToken) conservando donde sea posible los contratos de Aura (PlaybackData, lambda de ResolvingDataSource) y los 131 fixes del registro. Evidencia cruda: logcat en %TEMP%\aura-logcat-full.txt durante la sesión del 2026-08-23. Detalle técnico del port vive en PROJECT_MEMORY.md.

**Ground truth extraída del APK de SimpMusic v1.7.0 INSTALADO en el celular del dueño (dex strings, 2026-08-23):**
- ANDROID_VR 1.65.10 UA completo: `com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip` — SIN Cronet (Aura usa 1.43.32/1.61.48 "Quest 3" + Cronet; esa es la principal diferencia quemada).
- IOS 19.45.4 UA completo: `com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)`
- ANDROID 21.03.36 UA PARCIAL en dex: `com.google.android.youtube/21.03.36 (Linux; U; Android 15; ` (termina ahí; el resto se concatena en runtime o es constante muerta — confirmar en fuente).
- VISIONOS 1.02 UA parcial: `com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; ` (mismo caso).
- ANDROID_MUSIC (?): `com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip`.
- MWEB UA: `Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)`.
- Versiones web: WEB_REMIX 1.20260304.03.00 (también legacy 1.20250310.01.00), WEB 2.20250312.04.00, TVHTML5 7.20250312.16.00.
- poToken vía `serviceIntegrityDimensions(poToken=...)` igual que Aura; también existe `yapi.vyper.me` (¿proveedor externo de poToken BGUtil?) — confirmar en fuente.
- Endpoint player: `https://music.youtube.com/youtubei/v1/player?prettyPrint=false` (igual que Aura).
