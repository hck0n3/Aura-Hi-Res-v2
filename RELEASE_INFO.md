# Aura Hi-Res v2.0.66-beta4 — Canciones +18: ahora sí descifra las URLs que YouTube entrega a tu cuenta

Beta encima de la 2.0.66-beta3 (incluye todo lo suyo). Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Canciones con restricción de edad

- **Encontrada la causa real con tu registro de la beta3:** tu cuenta sí desbloqueaba la edad (YouTube respondía «OK» a la TV, al reproductor de creadores y a YouTube Music), pero las direcciones llegaban cifradas y la app no sabía descifrarlas con el reproductor nuevo de YouTube; por eso se quedaba «cargando» y saltaba a otra canción.
- **Nuevo descifrador:** la app ahora ejecuta el propio reproductor de YouTube con el solucionador de yt-dlp (el mismo que usa esa herramienta), que encuentra las funciones por su forma y no necesita una entrada nueva cada vez que YouTube cambia el reproductor. El descifrador anterior queda de respaldo.
- **La firma de la petición sale del mismo reproductor** con el que se descifra, en vez de pedírsela a otra copia que podía ser de otra versión.
- **La primera canción +18 tras una actualización de YouTube tarda unos segundos más** (analiza el reproductor); las siguientes salen al momento.
- **Esto todavía no está comprobado contra YouTube real.** Si una canción +18 no suena, comparte desde Ajustes ▸ Registros las líneas `EJS_SOLVER`, `EJS_SOLVE`, `AGE_GATE` y `AGE_WEBVIEW` (solo llevan estado y tiempos, nunca cookies, enlaces ni títulos).
