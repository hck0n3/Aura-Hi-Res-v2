# Aura Hi-Res v2.0.47 (beta 4) — "Pedir música" entiende lo que le pides y responde en segundos

BETA para probar antes de decidir si sale para todos (v2.0.47 / versionCode 1008). Se instala encima de
la beta 3 sin perder nada: mismo paquete (iad1tya.aura.music) y misma firma (CN=Aura Hi-Res v2),
conserva tus datos, tu sesión y tus ajustes. No se le ofrece a nadie más — GitHub excluye las betas del
canal de actualización de la app.

## Lo que arregla de la beta 3

- **"Música de los 80s" ya funciona.** Y no funcionaba por una razón concreta y tonta: la app leía "los 80s" como si fuera el **nombre de un artista** (el mismo trozo de código que entiende "solo Bad Bunny"), y a partir de ahí descartaba todos los resultados por no ser de ese "artista". Buscaba bien y luego tiraba todo. Ahora una década no puede confundirse con un nombre.
- **Entiende cómo hablas.** El buscador de YouTube Music no es un asistente: busca la frase tal cual, y "ponme música de los 80s en inglés" escrito así devuelve poco y malo. Ahora tu petición se traduce a lo que ese buscador sí premia — "éxitos de los 80", "80s hits english" — quitando las muletillas ("ponme", "quiero", "dame") y entendiendo la **década** (en cifras o en letra: 80s, los ochentas, noventa, 2000) y el **idioma** ("en inglés", "en español"). Si no reconoce nada especial, busca tu frase limpia: nunca peor que antes.
- **Para una época o un momento, va primero a las listas.** Es lo que hace la propia app de YouTube Music: a "música de los 80" o "música para estudiar" se responde con una lista curada, no con canciones sueltas. Si no encuentra lista, baja a canciones y luego a vídeos.
- **Responde en segundos, no en minuto y medio.** Antes la IA se llevaba hasta 90 segundos de intento **antes** de empezar siquiera a buscar; con el servicio lento eso era un indicador girando eternamente. Ahora las dos rutas salen a la vez y gana la que esté lista: la búsqueda vuelve en uno o dos segundos, y si la IA contesta antes manda la IA. Sigue sin decirte cuál de las dos fue.
- **Diez canciones, no veinticinco**, como pediste. Y no es una cola más corta: esas diez son la **semilla** de la cola infinita — el reproductor se las queda enteras, con su mezcla de artistas y géneros, y cuando se acaban la continuación inteligente sigue con el mismo algoritmo de siempre.

## Lo que ya venía en la beta 3

- **Entrar en la biblioteca no cierra la app** (el cristal interactivo del botón "Más", que se mordía la cola al dibujarse).
- **El botón de Inicio del minirreproductor responde siempre**: encima de esos botones había dos gestos de arrastre que le robaban el toque en cuanto el dedo se movía unos píxeles.
- **Modo sin conexión automático.** Sin red, Inicio, Novedades, Biblioteca y Buscar pasan solos a lo descargado y vuelven solos. No toca tu interruptor manual. Se apaga en Ajustes → Contenido.
- **"No me gusta" le enseña al algoritmo**: antes solo escondía filas y por dentro el artista marcado conservaba toda su puntuación.
- La cola de un álbum no se llena de canciones ajenas, el vídeo no se cuelga, las playlists de solo vídeo avanzan, las canciones no empiezan cortadas, identificar una canción es más rápido, descarga automática por lista, la sincronización acaba aunque cierres la app, portadas a todo el ancho, "Ver álbum" bajo los artistas, compartir en el reproductor, las ventanas miden lo que mide su contenido y Novedades sin estantes de una sola tarjeta.

## Lo que queda pendiente

- **El cristal interactivo del botón "Más" de la biblioteca** (tu punto 2) sigue con la placa de siempre, hasta que confirmes que la biblioteca abre bien en estas betas. La vía para hacerlo bien está identificada: darle a esa pantalla su propia capa de fondo, como ya la tienen la barra de abajo y el minirreproductor.
