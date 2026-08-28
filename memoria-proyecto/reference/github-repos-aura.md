---
name: Repos GitHub de Aura (cuenta hck0n3)
description: Dónde viven el proyecto Aura y sus versiones/forks en GitHub, y qué hay (y qué NO) en cada repo
type: reference
---

Cuenta GitHub del dueño: **hck0n3** (gh CLI autenticado con scopes gist/read:org/repo).

- `hck0n3/Aura-Hi-Res-v2` — PÚBLICO, creado 2026-08-25. El repo ACTIVO del proyecto (este directorio local apunta a él vía `origin`). Identidad nueva que cierra HALLAZGO-008; firma con keystore nueva `CN=Aura Hi-Res v2` (alias aurav2). Ver memoria `project/aura-v2-nueva-identidad.md`.
- `hck0n3/Aura-Hi-Res-Player` — PÚBLICO, rama `main`. CONGELADO desde 2026-08-25 (decisión del dueño): no recibe más pushes ni tags; solo sigue vivo su `player_configs.json` para auto-reparar la reproducción de los usuarios de la app antigua (`iad1tya.echo.music`). NO contiene app/keystore ni local.properties.
- `hck0n3/Aura-Fenix-Player` — público; canal de releases del update in-app de Aura Fénix.
- `hck0n3/aura-simpmusic` — fork PRIVADO de maxrave-dev/SimpMusic para la migración Aura. Su copia local (`C:\Users\AURA\Desktop\PROYECTOS DE PROGRAMACION\AURA FENIX\aura-simpmusic\local.properties`) tiene la SUPERPOWERED_LICENSE_KEY (Base64, len 96) — de ahí se copió al proyecto actual el 2026-08-23.
- `hck0n3/aura-simpmusic-core` — fork privado de maxrave-dev/core.
- Otros históricos: jr-music-player, jr-music-android, jr-music-namida, aura-licensing-worker.

**How to apply:** Para recuperar versiones viejas/APKs publicados de la app ANTIGUA, usar `gh api repos/hck0n3/Aura-Hi-Res-Player/...`. Keystores: la antigua `JR MUSIC PRO` solo existe como secret del repo viejo (marcada por YouTube, no se usa más); la de la v2 está en `app/keystore/release.keystore` (gitignored) con backup en `%USERPROFILE%\AuraHiResDevBackup\release-v2.keystore`.
