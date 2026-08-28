---
name: Betas históricas BETA-001/002/003 (identidad vieja 0.6.23x) — todas VERDE
description: Registro consolidado de las tres betas de la identidad antigua (v0.6.232/953/954, paquete debug iad1tya.aura.music) — fixes #154/#155, FASE 22 y VUELTA 2; todas VERDE; identidad CONGELADA y superada por la v2
type: project
---

Las tres primeras betas numeradas de la app, todas de la **identidad antigua**
(0.6.23x, paquete debug `iad1tya.aura.music.debug`, firma debug), entregadas en
la carpeta de nube `C:\Users\AURA\Desktop\BETA OFICIAL  DE AURA FENIX` (regla en
`entrega-betas-nube.md`). **Las tres VERDE, todas superadas.** Esa app publicada
(`iad1tya.echo.music`) quedó **CONGELADA** el 2026-08-25 y el proyecto continúa
como **Aura Hi-Res v2** (ver `aura-v2-nueva-identidad.md`); la numeración de betas
siguió con BETA-004 en adelante ya en la v2 (ver `ronda-3-reportes-dueno.md`).

**BETA-001 — v0.6.232/vc952 (entregada y VERDE en remoto 2026-08-24):**
`BETA-001_Aura_v0.6.232_vc952.apk`. Confirmado por el dueño: (a) reproducir/dar
like ya NO auto-suscribe artistas, (b) la letra cambia con el crossfade, (c)
reproducción, toggle música↔video y ecualizador bien. Fixes #154/#155 cerrados:
- Fix 1 (`88cf0e1`, registro #154): el auto-follow era
  `DatabaseDao.followArtistsWithContent()` — bulk que estampa `bookmarkedAt` en
  todo artista con contenido, y las vistas de suscripción leían `bookmarkedAt`.
  Eliminado (tombstone en DatabaseDao.kt) + 5 call sites; todas las vistas leen
  ahora `followedByUserAt`. Fuentes legítimas de follow intactas.
- Fix 2 (`f7022e2`, registro #155): la letra saliente seguía en pantalla durante
  el crossfade; el loop de fade evalúa `CrossfadeLyricsPin.shouldRelease` cada tick.

**BETA-002 — v0.6.233/vc953 (entregada 2026-08-24, VERDE 2026-08-25):**
`BETA-002_Aura_v0.6.233_vc953.apk` + LEEME. Mismo paquete/firma que la 001 →
actualiza encima sin perder datos. BUILD SUCCESSFUL 7m21s (build-beta-002.txt).
Contenido: los 7 fixes de la FASE 22 de la auditoría — HALLAZGO-025
(debugImplementation), 014 (isRouteSafeId), 016-parcial (exclusiones backup),
020 (LastFM.logout con auth.logout), 019-críticos (timeouts en 7 clientes con
política streaming-sin-callTimeout), 015 (provider_paths restringido), 012
(catálogo limpio + código muerto). Todos preventivos/de robustez; la app se ve
y se usa igual. Veredicto del dueño 2026-08-25: "funciona correctamente, ya hice
las pruebas".

**BETA-003 — v0.6.234/vc954 (entregada y VERDE 2026-08-25):**
`BETA-003_Aura_v0.6.234_vc954.apk` + LEEME. Build assembleUniversalFossDebug
BUILD SUCCESSFUL 4m47s (build-beta-003.txt); bump commit 49cd670 (local).
Contenido: la **VUELTA 2 completa** (commit ee949f4) — 013 cifrado de las 16
claves sensibles del DataStore (Keystore AES-256-GCM, migración one-time), 011
dependency locking (17 lockfiles), 016 exclusiones de backup completas, 019
timeouts en todos los clientes de red, 023 RTL documentado; 010/018 documentados
como riesgo aceptado (detalle completo en `estado-super-auditoria.md`).
VERDE confirmada con el registro del dueño (`aura_feedback (15).txt`, 2026-08-25
21:40, Samsung SM-S948B Android 16): 64 RESOLVE_TIMING ok=true winner=ANDROID_VR
(16:03–21:35); ok=false de 16:02 = red caída (ENETUNREACH/DNS), no certificado;
ok=false de 17:03/20:11 auto-reparados en segundos (AntiBotException NewPipe →
fallback BraveNewPipe + retry ok); **cifrado 013 validado en vivo** (cuenta
logueada tras migración, cero errores Keystore); crossfade (swap-ok) y loudness
(loudnessDb real) OK; sin crashes/ANR; SUPERPOWERED licence=ok engine=HEALTHY.
Ruido conocido no bloqueante: CipherWebView N-transform con errores (hash nuevo
b7457b7c sin config; secundario porque ANDROID_VR resuelve sin deobfuscación),
FETCH 416 ocasionales, Paxsenix 403 muteado 6h.

**Why:** el dueño confirmó cada beta en remoto y pidió conservar el historial de
qué llevaba cada una.
**How to apply:** fixes y veredictos CERRADOS — no re-probar. La guía antigua de
"publicar estos fixes" quedó obsoleta: la identidad vieja está congelada (sin más
tags ni releases); cualquier publicación futura es de la v2 y requiere
pre-publish-check + CI verde + permiso explícito del dueño. Quirk de entorno:
`gradlew.bat` exige prefijo `.\` en este shell (NoDefaultCurrentDirectoryInExePath).
