# Aura Hi-Res v2.0.54-beta1 — Ya no arranca solo, pedir música entiende géneros, y Spotify sí se sincroniza

Beta encima de la 2.0.53. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Reproducción

- **Ya no arranca solo al volver de otra app (TikTok y similares).** Si Aura quedaba pausada por
  cualquier motivo y después entrabas a ver un video a otra app, al salir la reproducción podía
  arrancar sin que la tocaras. Era una bandera interna que debía limpiarse al pausar manualmente y
  nunca lo hacía — quedaba activa por tiempo indefinido y la heredaba cualquier interrupción de audio
  posterior, sin relación con la original.

## Pedir música

- **Ya entiende géneros sueltos, no solo los busca como texto.** Pedir "reggaetón" devolvía canciones
  que literalmente tenían esa palabra en el título — no el género. Pedir "bossanova" traía una canción
  real seguida de relleno instrumental genérico. Ahora un género reconocido (reggaetón, salsa, bachata,
  rock, bossa nova, jazz, trap, k-pop, y varios más) se busca contra las categorías oficiales de
  YouTube Music, la misma fuente confiable que ya se usaba para décadas y estados de ánimo.

## Spotify

- **Una playlist agregada por enlace ahora sí se sincroniza.** El Radar de Novedades (u otra playlist
  que solo se puede agregar pegando el link, no desde tu biblioteca) decía "sincronizado" pero nunca
  traía el contenido actualizado — en realidad nunca se volvía a consultar, ni con el botón manual ni
  con la sincronización automática. Ahora sí se vuelve a leer su contenido real de Spotify cada vez.
