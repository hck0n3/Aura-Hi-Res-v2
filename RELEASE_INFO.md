# Aura Hi-Res v2.0.66-beta3 — Canciones +18 con tu cuenta por varias vías, también con el reproductor de YouTube

Beta encima de la 2.0.66-beta2 (incluye todo lo suyo). Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Canciones con restricción de edad

- **Varias vías, una detrás de otra, siempre con tu cuenta:** si YouTube pide confirmar la edad, la app prueba los reproductores web embebido, de TV, de creadores y de YouTube Music (con los tokens que cada uno exige), y si una vía da un enlace que no sirve pasa a la siguiente en lugar de rendirse.
- **Último recurso con el propio reproductor de YouTube:** si todas fallan y tienes sesión iniciada, la app abre en segundo plano la página del vídeo con tu cuenta, deja que YouTube confirme tu edad y toma el audio que él mismo pide. Solo se usa con canciones con restricción de edad y como mucho 15 segundos por canción.
- **Si aun así no se puede, el mensaje es claro** («YouTube pide confirmar la edad y no la ha confirmado con tu cuenta»), y la recuperación de sesión caducada sigue funcionando como antes.
- **Esto todavía no está comprobado contra YouTube real.** Si una canción +18 no suena, abre Ajustes ▸ Registros y comparte las líneas `AGE_GATE` y `AGE_WEBVIEW` (llevan solo el paso y el estado, nunca cookies, enlaces ni títulos): dirán en qué vía se cae.
