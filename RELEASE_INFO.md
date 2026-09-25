# Aura Hi-Res v2.0.60-beta3 — Diagnóstico: ¿tiene Spotify géneros más finos que iTunes?

Beta encima de la 2.0.60-beta2. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Sobre "que no me mezcle de otras cosas" (cola infinita, cualquier género)

Encontré la causa: el género "real" que uso hoy viene de iTunes por artista completo, y es muy amplio —
la mayoría de artistas de merengue y de salsa (por ejemplo) comparten la misma etiqueta "Latino" ahí, así
que el sistema no los distingue. No es un error de código, es un límite de esa fuente de datos.

**Esta beta no cambia el comportamiento todavía** — solo agrega un registro de diagnóstico para
confirmar si Spotify (que sí tiene géneros mucho más finos) los expone por la vía que usa esta app. Sin
esa confirmación real, construir algo sobre un supuesto sin verificar sería inventar.

## Qué necesito que hagas

1. Instalá esta beta con tu cuenta de Spotify conectada (Ajustes ▸ Spotify).
2. Abrí la pestaña **Novedades** una vez (para que consulte tus artistas seguidos).
3. Compartime el `app.log` desde Ajustes ▸ Registros.

Con eso confirmo si existe un dato de género más preciso para repotenciar la cola infinita de verdad, en
vez de adivinar.
