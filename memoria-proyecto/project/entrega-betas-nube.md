---
name: Entrega de betas numeradas vía carpeta de nube
description: Regla permanente 2026-08-24 — cada beta se numera BETA-NNN, se copia a "BETA OFICIAL  DE AURA FENIX" (carpeta sincronizada en la nube) y se avisa en el chat cuando esté lista para probar
type: project
---

El dueño prueba las betas en remoto (celular sin USB mientras está en el trabajo). Regla permanente desde 2026-08-24:

1. Cuando una beta esté lista para probar, copiar el APK a `C:\Users\AURA\Desktop\BETA OFICIAL  DE AURA FENIX` (ojo: el nombre tiene DOBLE espacio entre "OFICIAL" y "DE"). Es una carpeta sincronizada en la nube.
2. Nombrar el archivo `BETA-NNN_Aura_v<version>_vc<versionCode>.apk` con numeración secuencial (BETA-001, BETA-002, ...) para que el dueño no confunda versiones.
3. Dejar junto al APK un `BETA-NNN_LEEME.txt` con: qué contiene la beta, cómo instalarla y qué probar.
4. Avisar en el chat que la beta está lista para probar.

**Why:** el dueño lo pidió el 2026-08-24: "cuando la beta ya esté lista para probar... me avises que está lista para probar y copias la beta en esta carpeta... yo la tengo sincronizada en la nube entonces podré hacer las pruebas remoto y a cada beta que hagas enuméralas para así no confundirme".
**How to apply:** aplica a TODA beta futura de Aura, sin preguntar. El contador se lleva consultando los archivos BETA-NNN ya presentes en la carpeta. BETA-001 = v0.6.232 (versionCode 952), entregada 2026-08-24.
