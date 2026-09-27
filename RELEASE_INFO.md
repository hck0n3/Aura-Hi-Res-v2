# Aura Hi-Res v2.0.61-beta9 — La canción que tocás ya no hereda la cola de la anterior

Beta encima de la 2.0.61-beta8. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Lo que confirmé con tu prueba

- Leo el Poeta → Ricardo Montaner: al pasar a la siguiente, siguió la cola de Leo el Poeta.
- Geovanni Rios ("El culto está bueno") → la siguiente fue de Marcos Witt.
- Orquesta Alianza: ahí sí siguió bien.
- En el log se ve la huella: dos segundos después de tocar la canción de Montaner, su propia lista ya
  estaba armada (la app hasta precargó la siguiente), pero cuando avanzaste sonó otra canción distinta y
  la cola había quedado en solo 6 canciones — la lista de Montaner había sido reemplazada.

## La causa que encontré

- Cuando tocás una canción suelta, por un instante el reproductor tiene SOLO esa canción, sin nada
  detrás. La app interpretaba eso como "se acabó la cola" y salía a buscar radio en ese mismo momento.
- Pero la canción desde la que sembraba esa radio (el "ancla") recién se actualizaba cuando terminaba de
  cargar la lista de la canción nueva. Mientras tanto seguía siendo la canción ANTERIOR: la radio de Leo
  el Poeta se metía en la cola de Montaner, la de Montaner en la de Geovanni Rios, y así.
- Por eso "funcionaba la segunda vez": al tocar la misma canción otra vez, el ancla vieja ya era esa
  misma canción. Y por eso a veces salía bien: dependía de cuál de las dos cargas llegaba primero.
- Los arreglos de las betas 2 a 8 cuidaban que una cola VIEJA no pisara a la nueva; esta carrera ocurría
  DENTRO de la cola nueva, con datos viejos, y ninguno de esos arreglos la veía.

## Qué cambié

- El ancla ahora se fija en el mismo momento en que tocás la canción, no cuando termina de cargar.
- Mientras la lista propia de la canción está cargando, no se siembra ninguna radio: esa lista YA es la
  radio de YouTube para esa canción. Solo si llega vacía se siembra una, ya con el ancla correcta.
- Esa lista pasa por el mismo filtro de género que antes tenía la siembra, así que no se pierde la
  protección de "no mezclar estilos".
- Si tocás "siguiente" o la canción termina mientras la lista todavía carga, la app avanza en cuanto
  llega, en vez de quedarse parada.

## Lo que este arreglo NO cambia

- Si YouTube clasifica a un artista como cristiano (por ejemplo, merengue cristiano), su radio va a
  traer música cristiana: el filtro de género distingue "cristiano" de "tropical", pero no "merengue
  cristiano" de "alabanza". Si con esta beta Geovanni Rios sigue trayendo a Marcos Witt, contame y lo
  vemos aparte: ya no sería la cola de otra canción, sino la radio de esa misma canción.

## Necesito que confirmes en tu dispositivo

- Repetí la misma prueba: varios artistas seguidos desde el buscador, esperando unos segundos entre
  cada uno, y fijate si "a continuación" corresponde SIEMPRE a la canción que acabás de tocar.
- Compartime el log después: ahora debería aparecer una línea `CTX_GENRE enrich-before-score
  (playQueue)` por cada canción que toques.
