---
name: Regla permanente — código Superpowered NUNCA a GitHub (y ya está expuesto)
description: Orden del dueño (2026-08-27, repetida 2×) de no publicar el SDK Superpowered en GitHub; el repo público YA lo contiene en el historial — remediación decidida ~2026-09-02
type: feedback
---

NUNCA publicar el código del SDK de audio Superpowered en GitHub, en ninguna
forma (commits nuevos, releases, assets, snippets en issues/wiki).

**Why:** el dueño lo ordenó explícitamente dos veces el 2026-08-27 mientras se
preparaba la primera estable v2 ("recuerda no publicar el código de
superpowered en github"). Es un SDK con licencia comercial; publicarlo expone
la licencia y el código propietario.

**How to apply:**
- HALLAZGO CRÍTICO (descubierto el mismo día): el repo `hck0n3/Aura-Hi-Res-v2`
  es PÚBLICO y el código de Superpowered YA está trackeado en git y expuesto
  en origin/main desde el baseline 90721d1 (herencia del fork del que salió el
  proyecto). No fue un error nuestro, pero la exposición existe.
- La remediación está BLOQUEADA hasta ~2026-09-02 (GitHub en pausa por
  decisión del dueño; 47 commits locales sin push). Ese día se decide CON el
  dueño: (a) hacer el repo privado, o (b) reescribir el historial (filter-repo)
  y sacar el SDK del repo (gitignore + carpeta local fuera de git).
- Mientras tanto: no añadir NADA de Superpowered en commits nuevos que
  amplíen la superficie expuesta, y jamás adjuntar código Superpowered a
  releases/issues. La clave de licencia vive en local.properties (gitignored)
  y en el secret del CI — nunca en logs, changelogs ni commits.
- El pre-publish-check ya tiene un gate Gradle que avisa si Superpowered
  queda sin bindar; mantenerlo.
