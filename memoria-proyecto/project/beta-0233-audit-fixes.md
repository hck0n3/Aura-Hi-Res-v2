---
name: BETA-002 entregada — v0.6.233/vc953 con los 7 fixes de la FASE 22
description: Estado 2026-08-24 — BETA-002 (v0.6.233, 953) ENTREGADA a la carpeta de nube; pendiente prueba del dueño en remoto
type: project
---

APK v0.6.233 (vc953) entregado como `BETA-002_Aura_v0.6.233_vc953.apk` en
`C:\Users\AURA\Desktop\BETA OFICIAL  DE AURA FENIX` (carpeta de nube) junto a
`BETA-002_LEEME.txt`. Mismo paquete debug (`iad1tya.aura.music.debug`) y misma
firma que BETA-001 → actualiza encima sin pérdida de datos, sin pisar la app
publicada. Build exit-code 0, BUILD SUCCESSFUL en 7m21s (`build-beta-002.txt`).

**Contenido:** los 7 fixes de la FASE 22 de la auditoría — HALLAZGO-025
(debugImplementation), 014 (isRouteSafeId), 016-parcial (exclusiones backup),
020 (LastFM.logout con auth.logout), 019-críticos (timeouts en 7 clientes con
política streaming-sin-callTimeout), 015 (provider_paths restringido), 012
(catálogo limpio + código muerto). Todos preventivos/de robustez; la app se
ve y se usa igual que BETA-001.

**Qué probar:** reproducción general, compartir el registro, enlaces de playlist
desde otra app, cierre de sesión de Last.fm (funciona con o sin internet).

**Estado:** VERDE — el dueño la probó en el celular y confirmó el 2026-08-25
que "funciona correctamente, ya hice las pruebas". Superada por BETA-003.

**Why:** el dueño aprobó explícitamente ("lo apruebo has lo que sea necesario sin
dañar lo que ya tenemos") construir y entregar BETA-002 tras cerrar la FASE 22.
**How to apply:** al recibir feedback del dueño, actualizar este registro con el
veredicto (VERDE/ROJO + detalles). Si sale verde y el dueño decide publicar,
antes de taggear hay que: (1) revertir la firma a release en
app/build.gradle.kts:391-401 (HALLAZGO-008), (2) investigar por qué la firma
real fallaba en resolución de streams, (3) correr pre-publish-check, (4) esperar
Actions en verde, (5) pedir permiso explícito para pushear el tag.
