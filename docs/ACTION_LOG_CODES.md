# Códigos del registro de acciones (`ACT a=<n> <código>`)

Fila 730 del registro de regresiones. Cada botón o acción de la lista escribe **una** línea en `app.log`
(Ajustes ▸ Registros) con su código fijo y un número de acción `a=<n>` que crece en cada toque. Durante los
3 s siguientes, **todas** las demás líneas del registro (también las del servicio: `START_CAUSE=`, `ERR_BR=`,
resolución…) terminan en ` a=<n>`, así se ve qué provocó cada toque.

Nunca lleva títulos, artistas, nombres de listas ni texto escrito (regla 4): solo el código, el número y, en
los ajustes, el nombre de la clave y el valor nuevo (sí/no u opción). El número vuelve a 1 en cada arranque
del proceso (la cabecera de sesión marca cada arranque). Un código ya publicado no cambia de significado:
los nuevos se añaden. Fuente: `app/src/main/kotlin/com/music/echo/utils/ActionId.kt`; el test
`ActionLogGuardTest` falla si un código falta aquí.

## Ajustes (cualquier interruptor u opción guardada)

| Código | Acción |
|---|---|
| `UI.SET.TOGGLE` | Un ajuste de sí/no cambiado: `key=<nombre de la clave> value=true` o `value=false` |
| `UI.SET.CHOICE` | Un ajuste de opciones cambiado: `key=<nombre de la clave> value=<OPCIÓN>` |

## Reproductor completo

| Código | Acción |
|---|---|
| `UI.PLR.CONTINUE_HERE` | «Sonando en … · Continuar aquí» (traer la música a este aparato) |
| `UI.PLR.LYRICS_FULL` | Letra a pantalla completa (cabecera, con la letra abierta) |
| `UI.PLR.LYRICS_MENU` | Menú de la letra (cabecera) |
| `UI.PLR.TITLE` | Toque en el título (va al álbum) |
| `UI.PLR.TITLE_COPY` | Pulsación larga en el título (copiar) |
| `UI.PLR.ARTISTS` | Toque en los artistas (abre/cierra sus acciones) |
| `UI.PLR.ARTIST_COPY` | Pulsación larga en los artistas (copiar) |
| `UI.PLR.ARTIST_PICK` | Elegir un artista en el diálogo de varios (ir a su página) |
| `UI.PLR.LIKE` | Corazón junto al título |
| `UI.PLR.VIEW_ALBUM` | Chip «Ver álbum» |
| `UI.PLR.VIEW_ARTIST` | Chip «Ver artista» |
| `UI.PLR.SUBSCRIBE` | Chip «Suscribirme» (un artista) |
| `UI.PLR.SUBSCRIBE_PICK` | Chip «Suscribirme» con varios artistas (abre el diálogo) |
| `UI.PLR.SUBSCRIBE_ROW` | «Suscribirme» de un artista dentro de ese diálogo |
| `UI.PLR.SUBSCRIBE_CONFIRM` | «Suscribirse» en «¿Suscribirte a <canal>?» |
| `UI.PLR.GO_PLAYLIST` | Chip «Ir a la lista» |
| `UI.PLR.PLAYLIST_PICK` | Elegir una de mis listas en el diálogo de «Ir a la lista» |
| `UI.PLR.VIDEO` | Interruptor canción/vídeo |
| `UI.PLR.SEEK` | Soltar la barra de tiempo (salto) |
| `UI.PLR.SWIPE_PREV` | Deslizar el bloque de título/letra hacia la anterior |
| `UI.PLR.SWIPE_NEXT` | Deslizar el bloque de título/letra hacia la siguiente |
| `UI.PLR.SHUFFLE` | Aleatorio |
| `UI.PLR.PREV` | Anterior |
| `UI.PLR.PLAY` | Reproducir/pausa (en invitado de Escuchar juntos: silenciar) |
| `UI.PLR.NEXT` | Siguiente |
| `UI.PLR.REPEAT` | Repetir |
| `UI.PLR.DISLIKE` | No me gusta |
| `UI.PLR.ADD_PLAYLIST` | Añadir a una lista |
| `UI.PLR.DOWNLOAD` | Descargar / exportar |
| `UI.PLR.SEARCH` | Búsqueda rápida desde el reproductor |
| `UI.PLR.SHARE` | Compartir |
| `UI.PLR.VIDEO_PREV` | Vídeo a pantalla completa: anterior |
| `UI.PLR.VIDEO_PLAY` | Vídeo a pantalla completa: reproducir/pausa |
| `UI.PLR.VIDEO_NEXT` | Vídeo a pantalla completa: siguiente |
| `UI.PLR.VIDEO_EXIT` | Vídeo a pantalla completa: salir del vídeo |
| `UI.PLR.CANVAS_PREV` | Lienzo a pantalla completa: anterior |
| `UI.PLR.CANVAS_PLAY` | Lienzo a pantalla completa: reproducir/pausa |
| `UI.PLR.CANVAS_NEXT` | Lienzo a pantalla completa: siguiente |

## Mini reproductor (píldora)

| Código | Acción |
|---|---|
| `UI.MINI.SWIPE_PREV` | Deslizar la píldora hacia la anterior |
| `UI.MINI.SWIPE_NEXT` | Deslizar la píldora hacia la siguiente |
| `UI.MINI.OUTPUT` | Salida de audio (píldora ancha) |
| `UI.MINI.LIKE` | Me gusta (píldora ancha) |
| `UI.MINI.PREV` | Anterior |
| `UI.MINI.PLAY` | Reproducir/pausa (invitado: silenciar) |
| `UI.MINI.NEXT` | Siguiente |
| `UI.MINI.HOME` | Botón de Inicio de la píldora |
| `UI.MINI.DISMISS` | Deslizar la píldora hacia abajo (para la música y vacía la cola) |

## Barra bajo el reproductor

| Código | Acción |
|---|---|
| `UI.QBAR.QUEUE` | Barra bajo el reproductor: abrir la cola |
| `UI.QBAR.OUTPUT` | Barra: salida de audio |
| `UI.QBAR.DEVICES` | Barra: mis dispositivos |
| `UI.QBAR.LYRICS` | Barra: letra |
| `UI.QBAR.COMMENTS` | Barra: comentarios |
| `UI.QBAR.MORE` | Barra: más opciones (menú del reproductor) |

## Cola

| Código | Acción |
|---|---|
| `UI.QUEUE.SHUFFLE` | Cola: aleatorio |
| `UI.QUEUE.REPEAT` | Cola: repetir |
| `UI.QUEUE.MENU` | Cola: más opciones |
| `UI.QUEUE.LIKE` | Cola: me gusta de la que suena |
| `UI.QUEUE.TAB_NEXT` | Cola: pestaña «A continuación» |
| `UI.QUEUE.TAB_LYRICS` | Cola: pestaña «Letra» |
| `UI.QUEUE.TAB_RELATED` | Cola: pestaña «Relacionado» |
| `UI.QUEUE.LYRICS` | Cola: botón de letra del pie |
| `UI.QUEUE.SELECT_EXIT` | Cola: salir de la selección |
| `UI.QUEUE.SELECT_ALL` | Cola: seleccionar todo / nada |
| `UI.QUEUE.SELECT_MENU` | Cola: menú de lo seleccionado |
| `UI.QUEUE.ITEM` | Cola: tocar una canción (saltar a ella o marcarla) |
| `UI.QUEUE.ITEM_SELECT` | Cola: pulsación larga en una canción (empieza la selección) |
| `UI.QUEUE.ITEM_MENU` | Cola: menú de una canción |
| `UI.QUEUE.REMOVE` | Cola: quitar una canción deslizando |
| `UI.QUEUE.REMOVE_UNDO` | Cola: «Deshacer» tras quitarla |
| `UI.QUEUE.MOVE` | Cola: soltar una canción arrastrada (reordenar) |
| `UI.QUEUE.RADIO` | Cola: «Iniciar radio» |
| `UI.QUEUE.AUTOMIX_ITEM` | Automix: tocar una sugerencia (suena ya) |
| `UI.QUEUE.AUTOMIX_NEXT` | Automix: reproducir a continuación |
| `UI.QUEUE.AUTOMIX_ADD` | Automix: añadir a la cola |
| `UI.QUEUE.AUTOMIX_MENU` | Automix: pulsación larga (menú) |
| `UI.QUEUE.WIDE_ITEM` | Columna de cola en ventana ancha: tocar una canción |

## Mis dispositivos (hoja)

| Código | Acción |
|---|---|
| `UI.DEV.TAKE` | Mis dispositivos: traer aquí lo que suena en otro |
| `UI.DEV.PLAY_PAUSE` | Mis dispositivos: reproducir/pausa del otro |
| `UI.DEV.NEXT` | Mis dispositivos: siguiente del otro |
| `UI.DEV.VOLUME` | Mis dispositivos: soltar el volumen del otro |
| `UI.DEV.SEND` | Mis dispositivos: enviar lo mío a otro |
| `UI.DEV.SETTINGS` | Mis dispositivos: abrir los ajustes de sincronización |

## Ajustes ▸ Sincronizar dispositivos

| Código | Acción |
|---|---|
| `UI.DEVSET.BATTERY` | Ajustes ▸ Sincronizar dispositivos: quitar la optimización de batería |
| `UI.DEVSET.LIBRARY_SYNC` | Sincronizar la biblioteca ahora |
| `UI.DEVSET.SERVER` | Abrir el diálogo del servidor |
| `UI.DEVSET.SERVER_SAVE` | Guardar el servidor (el texto NO se registra) |
| `UI.DEVSET.CODE_COPY` | Copiar el código |
| `UI.DEVSET.CODE_CREATE` | Crear un código |
| `UI.DEVSET.CODE_ENTER` | Abrir «Introducir código» |
| `UI.DEVSET.CODE_SAVE` | Guardar el código introducido (el código NO se registra) |
| `UI.DEVSET.CODE_QR` | Mostrar el QR |
| `UI.DEVSET.CODE_REMOVE` | Quitar el código |
| `UI.DEVSET.NAME` | Abrir el diálogo del nombre del aparato |
| `UI.DEVSET.NAME_SAVE` | Guardar el nombre (el nombre NO se registra) |

## Escuchar juntos

| Código | Acción |
|---|---|
| `UI.LT.CONNECT` | Escuchar juntos: conectar |
| `UI.LT.DISCONNECT` | Desconectar |
| `UI.LT.TAB_CREATE` | Pestaña «Crear sala» |
| `UI.LT.TAB_JOIN` | Pestaña «Unirse» |
| `UI.LT.NAME_CLEAR` | Borrar el nombre escrito |
| `UI.LT.CODE_CLEAR` | Borrar el código escrito |
| `UI.LT.CREATE` | Crear sala |
| `UI.LT.JOIN` | Unirse a la sala |
| `UI.LT.JOIN_CANCEL` | Cancelar la espera de aprobación |
| `UI.LT.COPY_LINK` | Copiar el enlace de invitación |
| `UI.LT.COPY_CODE` | Copiar el código de sala |
| `UI.LT.MEMBER` | Tocar a un participante |
| `UI.LT.KICK` | Expulsar |
| `UI.LT.BLOCK_KICK` | Bloquear y expulsar |
| `UI.LT.TRANSFER_HOST` | Pasar el papel de anfitrión |
| `UI.LT.REQUEST_APPROVE` | Aprobar una petición de entrada |
| `UI.LT.REQUEST_REJECT` | Rechazar una petición de entrada |
| `UI.LT.SUGGESTION_APPROVE` | Aprobar una sugerencia |
| `UI.LT.SUGGESTION_REJECT` | Rechazar una sugerencia |
| `UI.LT.LEAVE` | Salir de la sala |
| `UI.LT.SETTINGS` | Abrir los ajustes de Escuchar juntos |

## Pedir música

| Código | Acción |
|---|---|
| `UI.REQ.SEND` | Pedir música: botón «Empezar» (el texto NO se registra) |
| `UI.REQ.SEND_KEY` | Pedir música: Enter del teclado |
| `UI.REQ.VOICE` | Pedir música: micrófono |
| `UI.REQ.SUGGESTION` | Pedir música: una sugerencia |
| `UI.REQ.HISTORY` | Pedir música: repetir una petición del historial |
| `UI.REQ.HISTORY_REMOVE` | Pedir música: quitar una entrada del historial |
| `UI.REQ.HISTORY_CLEAR` | Pedir música: borrar el historial |
| `UI.REQ.SIZE` | Pedir música: listas grandes/pequeñas |
| `UI.REQ.DISMISS` | Pedir música: cerrar el resultado |
| `UI.REQ.SONG` | Pedir música: tocar una canción del resultado |
| `UI.REQ.SONG_REMOVE` | Pedir música: quitar una canción antes de guardar |
| `UI.REQ.EDIT` | Pedir música: «Ver todas / editar» |
| `UI.REQ.SAVE` | Pedir música: guardar como lista |
| `UI.REQ.ALT_SONG` | Pedir música, 2.ª opción: tocar una canción |
| `UI.REQ.ALT_PLAY` | Pedir música, 2.ª opción: reproducir |
| `UI.REQ.ALT_SAVE` | Pedir música, 2.ª opción: guardar |

## Página de artista

| Código | Acción |
|---|---|
| `UI.ART.SUBSCRIBE` | Página de artista: Suscribirse / Suscrito |

## Menú de artista (biblioteca)

| Código | Acción |
|---|---|
| `UI.ARTMENU.SUBSCRIBE` | Menú de artista de la biblioteca: Suscribirse |

## Menú de artista (YouTube)

| Código | Acción |
|---|---|
| `UI.YTARTMENU.SUBSCRIBE` | Menú de artista de YouTube: Suscribirse |

## Panel de detalle (búsqueda ancha)

| Código | Acción |
|---|---|
| `UI.PANE.ARTIST_SUBSCRIBE` | Panel de detalle de la búsqueda ancha: Suscribirse |
