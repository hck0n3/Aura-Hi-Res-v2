# Aura Hi-Res v2.0.58-beta2 — Corrige 3 fallos reportados en la 2.0.58-beta1

Beta encima de la 2.0.58-beta1. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Corregido tras probar la 2.0.58-beta1

- **El badge "En lista" ya se ve en todos los idiomas/regiones configurados.** Quedaba un segundo
  archivo de idioma (español - región Estados Unidos) que el arreglo anterior no había tocado, así que
  un teléfono con esa configuración regional seguía mostrando el texto viejo "Ya está".
- **Pedir una canción/artista específico ya se respeta también cuando nombrás el artista.** El arreglo
  de la beta anterior (para que lo específico le gane al género) se apagaba justo cuando el pedido
  nombraba un artista explícito ("rock de Queen, Bohemian Rhapsody") — la forma más común de ser
  específico. Ahora corre siempre que hay algo concreto que buscar.
- **Los links de Spotify ya no dicen "no encontrado" por las mismas.** Esa rama nunca había recibido el
  arreglo del User-Agent de vista previa que ya funcionaba para Amazon Music y SoundCloud. Mismo
  tratamiento ahora, y de paso también para Tidal.
