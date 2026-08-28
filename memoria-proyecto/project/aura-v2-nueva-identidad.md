---
name: Aura Hi-Res v2 — nueva identidad (repo, keystore, decisión del dueño)
description: HALLAZGO-008 CERRADO — keystore v2 validada en vivo (BETA-004 VERDE 2026-08-25); repo nuevo, app antigua congelada; falta publicar estable
type: project
---

Decisión del dueño (2026-08-25): cerrar HALLAZGO-008 creando la app/repo **Aura Hi-Res v2** y congelar la app anterior para siempre.

**Why:** el certificado de release `CN=JR MUSIC PRO` quedó marcado por YouTube (postmortem 16, 2026-08-19): todo build firmado con él falla al resolver streams; los firmados con debug funcionan. Como la v2 es identidad nueva sin usuarios instalados, estrena keystore dedicada y puede volver a firmar release con su propia clave.

**How to apply:**
- Repo nuevo: `hck0n3/Aura-Hi-Res-v2` (público, necesario porque la app lee `raw.githubusercontent` sin auth). Remote local: `origin` → ese repo. La app antigua (`iad1tya.echo.music`, repo `Aura-Hi-Res-Player`) NO recibe más pushes ni tags; su `player_configs.json` sigue vivo allá para sus usuarios.
- Keystore v2: `app/keystore/release.keystore` (gitignored), alias `aurav2`, DN `CN=Aura Hi-Res v2, O=Aura, C=US`, RSA 2048, 10000 días. Backup: `%USERPROFILE%\AuraHiResDevBackup\release-v2.keystore` + `AURA_V2_KEYSTORE_INFO.txt` (contiene passwords). Credenciales también en `local.properties` (STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD) y deben estar como secrets CI (RELEASE_KEYSTORE_BASE64, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS=aurav2, RELEASE_KEY_PASSWORD, SUPERPOWERED_LICENSE_KEY).
- Identidad cambiada en el código (2026-08-25): versionName 2.0.0 / versionCode 955, app_name "Aura Hi-Res v2", applicationId `iad1tya.aura.music` (se conservó; el publicado antiguo es `iad1tya.echo.music`), URLs del updater/configs → `hck0n3/Aura-Hi-Res-v2`, prefijo de APK `Aura-Hi-Res-v2-`, CI fallback keystore `CN=AURA-V2-EMERGENCY` (pass `aurav2emergency`), scripts pre-publish esperan `CN=Aura Hi-Res v2`. El buildType release vuelve a firmar con `signingConfigs.getByName("release")` (línea ~401 de app/build.gradle.kts, guardián del HALLAZGO-008).
- REGLA CUMPLIDA: el dueño confirmó en su dispositivo que el certificado nuevo resuelve streams. BETA-004 VERDE (registro aura_feedback 16, 2026-08-25 22:18): v2.0.0-nosub (955) foss/release id=iad1tya.aura.music.dev, instalación FRESH, ~37 canciones ok=true winner=ANDROID_VR (21:58–22:17), incluso sin cuenta (guest); audio path, keep-alive y screen-off OK; video mode OK. Únicos fallos: 2 videos UNPLAYABLE en YouTube mismo (IxVPwvdhgs0, H1YF6ya7TWI — no es certificado) y transitorios recuperados en 1-3s. HALLAZGO-008 CERRADO.
- Hecho al 2026-08-25: push a `hck0n3/Aura-Hi-Res-v2` (commits 01c85b3 identidad + b90757c chmod +x gradlew/scripts; el exec bit se pierde al crear el repo desde Windows), secrets CI configurados, CI verde (Android Build & Sign run 32871236352 + CodeQL), APK del CI verificado con apksigner: `CN=Aura Hi-Res v2`, SHA-256 ba82c11d… idéntico a la keystore local. BETA-004 (v2.0.0/vc955 NOSUB) entregada y PROBADA VERDE.
- Dependabot se activó solo en el repo nuevo: PRs abiertas; los bumps de media3 1.11.0 y KSP fallan CodeQL — NADA se mergea sin revisión del dueño.
- Pendientes: (1) HALLAZGO-021 split de MusicService.kt — Fase A completa, Fase B: PreloadPlanning + EnhancedShuffleCycle hechos; decisión del dueño si extraer video mode/crossfade (con estado) o cerrar ahí; (2) publicar la estable v2 cuando el dueño lo ordene (pre-publish-check + CI verde + tag sin -beta).
