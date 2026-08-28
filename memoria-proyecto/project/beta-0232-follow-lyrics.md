---
name: Beta 0.6.232 — fixes follow-bulk y pin de letras (BETA-001, VERDE)
description: Estado 2026-08-24 — BETA-001 (v0.6.232, 952) PROBADA VERDE por el dueño en remoto; fixes de auto-suscripción (88cf0e1) y pin de letras en crossfade (f7022e2) confirmados
type: project
---

APK 0.6.232 (952) entregado como `BETA-001_Aura_v0.6.232_vc952.apk` en `C:\Users\AURA\Desktop\BETA OFICIAL  DE AURA FENIX` (carpeta de nube). **PROBADA VERDE por el dueño el 2026-08-24 (remoto):** (a) reproducir/like ya NO auto-suscribe artistas, (b) la letra cambia con el crossfade, (c) reproducción, toggle música↔video y ecualizador bien. Fixes #154/#155 confirmados en dispositivo.

**Fix 1 (`88cf0e1`, registro #154):** el bug beta "al reproducir/dar like el artista queda suscrito solo" era `DatabaseDao.followArtistsWithContent()` — bulk que estampa `bookmarkedAt` en todo artista con contenido, y las vistas de suscripción leían `bookmarkedAt`. Eliminado (tombstone en DatabaseDao.kt) + 5 call sites; todas las vistas leen ahora `followedByUserAt`. Fuentes legítimas de follow intactas.

**Fix 2 (`f7022e2`, registro #155):** la letra de la canción saliente seguía en pantalla durante el crossfade; el loop de fade ahora evalúa `CrossfadeLyricsPin.shouldRelease` cada tick.

**Why:** el dueño reportó ambos fallos en la beta 0.6.231/0.6.232 con BravePipe ya funcionando (reproducción OK).
**How to apply:** estos fixes quedan cerrados. Si el dueño quiere publicar los fixes a todos los usuarios, hace falta su permiso explícito + release estable (RELEASE_INFO.md, pre-publish-check, Actions en verde). Siguiente beta que se genere: BETA-002 (regla en entrega-betas-nube.md). Quirk de entorno: `gradlew.bat` exige prefijo `.\` en este shell (NoDefaultCurrentDirectoryInExePath); JAVA_HOME no hace falta — `gradle/gradle-daemon-jvm.properties` fija JDK 21.
