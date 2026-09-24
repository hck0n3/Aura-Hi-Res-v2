# Aura Hi-Res v2.0.56-beta1 — Pedir música más preciso, links de Amazon Music y cola offline instantánea

Beta encima de la 2.0.55. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Pedir música

- **Ya no ignora el tema cuando pedís género + tema a la vez.** "Bachata cristiana" o "bossa nova
  cristiana" caían en la categoría o lista del género a secas (100% secular), porque bastaba con
  demostrar UNA de las dos partes de la petición. Ahora, si pedís dos cosas a la vez, tienen que
  cumplirse las dos — o se sigue buscando en vez de conformarse con la mitad de lo que pediste.

## Enlaces externos

- **Un link de Amazon Music de una sola canción ya no se busca como si fuera el álbum entero.**
  Amazon comparte una canción como `.../albums/{id}?trackAsin={id}` — la ruta sola parece un álbum, y
  ese identificador de canción nunca se leía. Aura terminaba buscando el ÁLBUM completo por título
  (una comparación mucho más estricta que buscar una canción), así que cualquier variación de nombre
  bastaba para decir "no encontrado" aunque la canción sí estuviera en YouTube Music.

## Reproducción sin conexión

- **La cola pasa a modo sin conexión al instante cuando de verdad no hay red.** Antes, si se cortaban
  el wifi y los datos (o no había señal), cada canción no descargada intentaba igual cargar por red y
  recién se rendía después de 15-30 segundos de espera — eso era lo que se sentía como que "las
  canciones se quedan cargando". Ahora, en cuanto se detecta que no hay conexión de verdad, la cola
  salta al instante a lo que ya está en caché, descargado o guardado localmente, igual que si hubieras
  activado "Modo sin conexión" a mano.
