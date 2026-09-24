# Aura Hi-Res v2.0.59 — Discografías completas, pedir música más preciso y deslizar rediseñado

Actualización para todos, encima de la 2.0.48. Conserva tus datos, tu sesión y tus ajustes: mismo
paquete y misma firma. Recoge doce rondas de pruebas y reportes.

## Pedir música

- **Reproduce de inmediato y completa la lista en segundo plano.** Arranca con un primer lote chico
  apenas está listo, y sigue completando hasta 25 canciones sin que tengas que esperar — la cola
  infinita hereda el estilo de esas 25, con transición suave (crossfade) al sumarlas.
- **Le describís lo que querés y suena.** Entiende década (en cifras o en letra), idioma, género suelto
  ("reggaetón", "bossa nova", "bachata"…) y estado de ánimo, y busca por el catálogo oficial de
  categorías/listas editoriales de YouTube Music en vez de improvisar. Cuando pedís dos cosas a la vez
  ("reggae de los 90", "bachata cristiana", "trap cristiano en inglés") las dos tienen que cumplirse, no
  alcanza con una — y si la IA responde, también se verifica que de verdad cumpla lo pedido, no solo se
  le pide que lo haga.
- **Pedir una canción o artista específico ya no llena la cola con copias de lo mismo.** Distintas
  subidas de la misma canción ("Video Oficial", "Lyrics", "Audio") cuentan como la misma; lo específico
  le gana siempre al género, incluso cuando también nombrás el artista explícitamente; y reconoce
  negaciones ("no solo X", "no quiero canciones de X") para no bloquear por error al artista que
  pediste no limitar.
- **Son tus canciones, no las de cualquiera.** El orden de la lista de origen manda, pero tu gusto (por
  artista y por género) empuja lo que ya escuchás, lo marcado "No me gusta" se cae, y no salen dos
  seguidas del mismo artista.
- **No repite las mismas canciones en peticiones seguidas**, y un chip con tus últimas peticiones deja
  repetir una con un toque. "Lo que suena ahora" responde con los charts reales de tendencias.
- **Dictado por voz** que ya no se corta a medio hablar, con el mismo cristal translúcido que el resto
  de las ventanas.

## Discografía de artista

- **Discografías más completas**, incluso en catálogos grandes (topes de reparación más altos: 150
  álbumes / 250 lanzamientos) y cuando YouTube no da la lista completa de una sección — se completa
  contra iTunes/Apple Music también en esos casos.
- **Álbumes y Sencillos/EPs ya no se mezclan ni se duplican entre las dos pantallas**, y el orden ahora
  sigue el de iTunes/Apple Music (más nuevo primero).

## Deslizar canciones

- **Me gusta y no me gusta deslizando**, en tus propias playlists y también en álbumes, singles de
  artista y playlists ajenas donde no podés borrar: un lado alterna me-gusta/no-me-gusta según el
  estado real de la canción, el otro abre "agregar a una playlist". Con vibración corta al cruzar el
  punto de activación.
- **Borrar de tu playlist con deshacer**, y la papelera se ve progresivamente mientras deslizás (no
  solo al final).

## Reproducción

- **La cola de un álbum ya no se corta ni se llena de canciones ajenas.** Se compara cuántas canciones
  hay guardadas contra las que el álbum realmente tiene antes de darlo por completo, y saltar a mano
  hasta el final ya no dispara la cola infinita antes de tiempo.
- **El vídeo ya no se cuelga** (tope de reintentos que no se reinicia) y **las playlists de solo vídeo
  avanzan como una cola** en vez de salir siempre en modo vídeo.
- **Ya no arranca solo al volver de otra app** (TikTok y similares) tras quedar pausada por una
  interrupción de audio.
- **Modo sin conexión: sigue solo de verdad.** En cuanto se detecta que no hay red real (antes tardaba
  15-30s en rendirse, o directamente fallaba con wifi activo aunque estuviera conectado), la cola salta
  al instante a lo que ya está descargado, cacheado o escuchado antes.
- **"No me gusta" le enseña al algoritmo** de verdad (radio, Inicio, aleatorio inteligente), no solo
  esconde filas.
- **La cola vuelve a resumir tras una actualización**, y "Siguiente" muestra un indicador de carga en
  vez de parecer congelado al entrar a la cola infinita.

## Enlaces externos

- **Un link de Spotify, Amazon Music u otro ya no dice "no encontrado" cuando el contenido sí está en
  YouTube Music.** Se agregó Odesli/song.link para resolver el id exacto cuando existe mapeo conocido;
  el mismo User-Agent de vista previa (Facebook/Twitter/Discord) que ya funcionaba para unos sitios
  ahora cubre también Amazon Music, SoundCloud y Tidal; los artistas invitados se comparan uno por uno
  en vez de como una sola cadena (mejora mucho reggaetón y bachata); y un link de Amazon Music de una
  sola canción ya no se busca como si fuera el álbum entero.

## Novedades y recomendaciones

- **El radar de Novedades ordena por lo que más te gusta**, no solo por si seguís al artista, con un
  esqueleto de carga mientras llega. Arreglado un caso raro donde podía vaciarse por un fallo parcial de
  red aunque tuviera una lista buena guardada.
- **"Recomendado para ti (IA)" ya respeta "No me gusta"** — era la única fuente de recomendaciones de
  toda la app que no lo hacía.
- **Una playlist de Spotify agregada por enlace (como el propio Radar) ahora sí se sincroniza de
  verdad**, en vez de decir "sincronizado" sin traer nada nuevo.

## Interfaz

- **El botón de Cast responde siempre al primer toque**, con área de contacto de 48dp.
- **Un álbum que ya viste en esta sesión abre al instante** la segunda vez.
- **Transición animada** al abrir un álbum desde Inicio.
- **Atajo a Inicio en el minirreproductor**, botones que ya no pierden el toque contra los gestos de
  arrastre, botón de salida de audio que responde al primer toque, portadas a todo el ancho, menú de
  "Ver álbum/Ver artista" al tocar un nombre, botón de compartir en el reproductor completo, y ventanas
  que suben desde abajo con el alto de su propio contenido.

## Escuchar juntos

- **Menos desajuste en canciones largas**: además de los eventos de cambio de canción/pausa/salto, ahora
  se revisa la posición cada 15 segundos mientras suena.

## Ecualizador

- **Mover una banda ya no cambia el volumen general.** Era "Headroom automático", que ahora viene
  apagado por defecto (sigue disponible en Ajustes → Sonido para quien lo quiera).

## Batería

- Se quitó una escritura periódica a disco (aleatorio mejorado) cuyo dato nunca se llegaba a leer de
  vuelta — trabajo real sin ningún efecto, ahora eliminado.
