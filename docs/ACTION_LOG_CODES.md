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
| `UI.PLR.SUBSCRIBE` | «Suscribirme» de un artista: el chip (un artista) o su fila en el diálogo (si justo antes hay un `UI.PLR.SUBSCRIBE_PICK`) |
| `UI.PLR.SUBSCRIBE_PICK` | Chip «Suscribirme» con varios artistas (abre el diálogo) |
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
| `UI.DEVSET.HELD` | Abrir «Borrados retenidos» (fila 807) |
| `UI.DEVSET.HELD_APPLY` | «Aplicar» los borrados retenidos: se quitan también aquí |
| `UI.DEVSET.HELD_DISCARD` | «Descartar» los borrados retenidos: se conservan y vuelven a los otros aparatos |
| `UI.DEVSET.HELD_REVIEW` | «Revisar» en el aviso de borrados retenidos |

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

## Menú ⋮ del reproductor

| Código | Acción |
|---|---|
| `UI.PMENU.VIEW_ARTIST` | Ver artista |
| `UI.PMENU.VIEW_ALBUM` | Ver álbum |
| `UI.PMENU.EQUALIZER` | Ecualizador |
| `UI.PMENU.SOUND` | Sonido |
| `UI.PMENU.DETAILS` | Detalles |
| `UI.PMENU.SETTINGS` | Ajustes |
| `UI.PMENU.SHUFFLE` | Aleatorio (fila del menú) |
| `UI.PMENU.REPEAT` | Repetir (fila del menú) |
| `UI.PMENU.START_RADIO` | Iniciar radio |
| `UI.PMENU.ADVANCED` | Avanzado (tono/tempo) |
| `UI.PMENU.SLEEP_TIMER` | Temporizador |
| `UI.PMENU.REFETCH` | Volver a obtener |
| `UI.PMENU.LIBRARY_TOGGLE` | Añadir a / quitar de la biblioteca |
| `UI.PMENU.ADD_TO_PLAYLIST` | Añadir a una lista |
| `UI.PMENU.EXPORT` | Exportar |
| `UI.PMENU.SET_AS_RINGTONE` | Poner como tono |
| `UI.PMENU.GO_TO_PODCAST` | Ir al pódcast |
| `UI.PMENU.VIDEO` | Vídeo (cambiar a vídeo/canción) |
| `UI.PMENU.AUDIO_DEVICES` | Dispositivos de audio |
| `UI.PMENU.LISTEN_TOGETHER` | Escuchar juntos |
| `UI.PMENU.RESYNC` | Resincronizar (invitado de Escuchar juntos) |
| `UI.PMENU.AMBIENT_MODE` | Modo ambiente |
| `UI.PMENU.SHARE` | Compartir |

## Menú de canción (biblioteca)

| Código | Acción |
|---|---|
| `UI.SONGMENU.SHARE_EXPORTED_VIDEO` | Compartir (menú de un vídeo exportado) |
| `UI.SONGMENU.EXPORT_AS_MP3` | Exportar como MP3 (menú de un vídeo exportado) |
| `UI.SONGMENU.DELETE_EXPORTED_VIDEO` | Borrar el vídeo exportado |
| `UI.SONGMENU.START_RADIO` | Iniciar radio |
| `UI.SONGMENU.ADD_TO_PLAYLIST` | Añadir a una lista |
| `UI.SONGMENU.SHARE` | Compartir |
| `UI.SONGMENU.REMOVE_LIKE` | Quitar de «Canciones que me gustan» |
| `UI.SONGMENU.SUGGEST_TO_HOST` | Sugerir al anfitrión (Escuchar juntos) |
| `UI.SONGMENU.EDIT` | Editar |
| `UI.SONGMENU.PLAY_NEXT` | Reproducir a continuación |
| `UI.SONGMENU.ADD_TO_QUEUE` | Añadir a la cola |
| `UI.SONGMENU.LIBRARY_TOGGLE` | Añadir a / quitar de la biblioteca |
| `UI.SONGMENU.REMOVE_FROM_HISTORY` | Quitar del historial |
| `UI.SONGMENU.REMOVE_FROM_PLAYLIST` | Quitar de la lista |
| `UI.SONGMENU.REMOVE_FROM_CACHE` | Quitar de la caché |
| `UI.SONGMENU.REMOVE_DOWNLOAD` | Quitar la descarga |
| `UI.SONGMENU.CANCEL_DOWNLOAD` | Cancelar la descarga en curso («Descargando») |
| `UI.SONGMENU.DOWNLOAD` | Descargar |
| `UI.SONGMENU.SHARE_LOCAL_FILE` | Compartir el archivo (canción local) |
| `UI.SONGMENU.EXPORT` | Exportar |
| `UI.SONGMENU.SET_AS_RINGTONE` | Poner como tono |
| `UI.SONGMENU.VIEW_ARTIST` | Ver artista |
| `UI.SONGMENU.VIEW_ALBUM` | Ver álbum |
| `UI.SONGMENU.REFETCH` | Volver a obtener |
| `UI.SONGMENU.DETAILS` | Detalles |

## Menú de canción (YouTube)

| Código | Acción |
|---|---|
| `UI.YTSONGMENU.START_RADIO` | Iniciar radio |
| `UI.YTSONGMENU.ADD_TO_PLAYLIST` | Añadir a una lista |
| `UI.YTSONGMENU.SHARE` | Compartir |
| `UI.YTSONGMENU.SUGGEST_TO_HOST` | Sugerir al anfitrión (Escuchar juntos) |
| `UI.YTSONGMENU.PLAY_NEXT` | Reproducir a continuación |
| `UI.YTSONGMENU.ADD_TO_QUEUE` | Añadir a la cola |
| `UI.YTSONGMENU.LESS_LIKE_THIS` | Menos parecido a esto |
| `UI.YTSONGMENU.REMOVE_FROM_HISTORY` | Quitar del historial |
| `UI.YTSONGMENU.LIBRARY_TOGGLE` | Añadir a / quitar de la biblioteca |
| `UI.YTSONGMENU.REMOVE_DOWNLOAD` | Quitar la descarga |
| `UI.YTSONGMENU.CANCEL_DOWNLOAD` | Cancelar la descarga en curso |
| `UI.YTSONGMENU.DOWNLOAD` | Descargar |
| `UI.YTSONGMENU.EXPORT` | Exportar |
| `UI.YTSONGMENU.SET_AS_RINGTONE` | Poner como tono |
| `UI.YTSONGMENU.VIEW_ARTIST` | Ver artista |
| `UI.YTSONGMENU.VIEW_ALBUM` | Ver álbum |
| `UI.YTSONGMENU.DETAILS` | Detalles |

## Menú de álbum (biblioteca)

| Código | Acción |
|---|---|
| `UI.ALBUMMENU.PLAY` | Reproducir |
| `UI.ALBUMMENU.SHUFFLE` | Aleatorio |
| `UI.ALBUMMENU.SHARE` | Compartir |
| `UI.ALBUMMENU.PLAY_NEXT` | Reproducir a continuación |
| `UI.ALBUMMENU.ADD_TO_QUEUE` | Añadir a la cola |
| `UI.ALBUMMENU.ADD_TO_PLAYLIST` | Añadir a una lista |
| `UI.ALBUMMENU.REMOVE_DOWNLOAD` | Quitar la descarga |
| `UI.ALBUMMENU.CANCEL_DOWNLOAD` | Cancelar la descarga en curso |
| `UI.ALBUMMENU.DOWNLOAD` | Descargar |
| `UI.ALBUMMENU.EXPORT_ALL_MP3` | Exportar todo a MP3 |
| `UI.ALBUMMENU.VIEW_ARTIST` | Ver artista |
| `UI.ALBUMMENU.REFETCH` | Volver a obtener |

## Menú de álbum (YouTube)

| Código | Acción |
|---|---|
| `UI.YTALBUMMENU.PLAY` | Reproducir |
| `UI.YTALBUMMENU.SHUFFLE` | Aleatorio |
| `UI.YTALBUMMENU.SHARE` | Compartir |
| `UI.YTALBUMMENU.PLAY_NEXT` | Reproducir a continuación |
| `UI.YTALBUMMENU.ADD_TO_QUEUE` | Añadir a la cola |
| `UI.YTALBUMMENU.DO_NOT_RECOMMEND` | No recomendar |
| `UI.YTALBUMMENU.ADD_TO_PLAYLIST` | Añadir a una lista |
| `UI.YTALBUMMENU.REMOVE_DOWNLOAD` | Quitar la descarga |
| `UI.YTALBUMMENU.CANCEL_DOWNLOAD` | Cancelar la descarga en curso |
| `UI.YTALBUMMENU.DOWNLOAD` | Descargar |
| `UI.YTALBUMMENU.EXPORT_ALL_MP3` | Exportar todo a MP3 |
| `UI.YTALBUMMENU.VIEW_ALBUM` | Ver álbum |
| `UI.YTALBUMMENU.VIEW_ARTIST` | Ver artista |

## Menú de artista (reproducir y compartir)

| Código | Acción |
|---|---|
| `UI.ARTMENU.PLAY` | Biblioteca: reproducir |
| `UI.ARTMENU.SHUFFLE` | Biblioteca: aleatorio |
| `UI.ARTMENU.SHARE` | Biblioteca: compartir |
| `UI.YTARTMENU.START_RADIO` | YouTube: iniciar radio |
| `UI.YTARTMENU.SHARE` | YouTube: compartir |
| `UI.YTARTMENU.DO_NOT_RECOMMEND` | YouTube: no recomendar |

## Menú de lista (biblioteca)

| Código | Acción |
|---|---|
| `UI.PLMENU.PLAY` | Reproducir |
| `UI.PLMENU.SHUFFLE` | Aleatorio |
| `UI.PLMENU.SHARE` | Compartir |
| `UI.PLMENU.EXPORT_JSON` | Exportar (JSON) |
| `UI.PLMENU.EXPORT_CSV` | Exportar (CSV) |
| `UI.PLMENU.EXPORT_MP3` | Exportar a MP3 |
| `UI.PLMENU.START_RADIO` | Iniciar radio |
| `UI.PLMENU.SYNC_NOW` | Sincronizar ahora |
| `UI.PLMENU.PLAY_NEXT` | Reproducir a continuación |
| `UI.PLMENU.ADD_TO_QUEUE` | Añadir a la cola |
| `UI.PLMENU.EDIT` | Editar |
| `UI.PLMENU.SPEED_DIAL_TOGGLE` | Fijar en / quitar de Acceso rápido |
| `UI.PLMENU.REMOVE_DOWNLOAD` | Quitar la descarga |
| `UI.PLMENU.CANCEL_DOWNLOAD` | Cancelar la descarga en curso |
| `UI.PLMENU.DOWNLOAD` | Descargar |
| `UI.PLMENU.AUTO_DOWNLOAD` | Descarga automática de la lista (interruptor) |
| `UI.PLMENU.DELETE` | Borrar la lista |

## Menú de lista (YouTube)

| Código | Acción |
|---|---|
| `UI.YTPLMENU.PLAY` | Reproducir |
| `UI.YTPLMENU.SHUFFLE` | Aleatorio |
| `UI.YTPLMENU.START_RADIO` | Iniciar radio |
| `UI.YTPLMENU.DO_NOT_RECOMMEND` | No recomendar |
| `UI.YTPLMENU.PLAY_NEXT` | Reproducir a continuación |
| `UI.YTPLMENU.ADD_TO_QUEUE` | Añadir a la cola |
| `UI.YTPLMENU.ADD_TO_PLAYLIST` | Añadir a una lista |
| `UI.YTPLMENU.SPEED_DIAL_TOGGLE` | Fijar en / quitar de Acceso rápido |
| `UI.YTPLMENU.EXPORT_MP3` | Exportar a MP3 |
| `UI.YTPLMENU.REMOVE_DOWNLOAD` | Quitar la descarga |
| `UI.YTPLMENU.CANCEL_DOWNLOAD` | Cancelar la descarga en curso |
| `UI.YTPLMENU.DOWNLOAD` | Descargar |
| `UI.YTPLMENU.SHARE` | Compartir |
| `UI.YTPLMENU.SELECT` | Seleccionar canciones |

## Páginas de álbum y de lista (botones de la cabecera)

| Código | Acción |
|---|---|
| `UI.ALBUM.SAVE` | Álbum: guardar / guardado |
| `UI.ALBUM.DOWNLOAD` | Álbum: descargar o exportar |
| `UI.ALBUM.PLAY` | Álbum: reproducir / pausa |
| `UI.ALBUM.SHUFFLE` | Álbum: aleatorio |
| `UI.OPL.SHUFFLE` | Lista de YouTube: aleatorio |
| `UI.OPL.PLAY` | Lista de YouTube: reproducir |
| `UI.OPL.SAVE` | Lista de YouTube: guardar / guardada |
| `UI.OPL.DOWNLOAD` | Lista de YouTube: descargar o exportar |
| `UI.LPL.SHUFFLE` | Lista propia: aleatorio |
| `UI.LPL.PLAY` | Lista propia: reproducir |
| `UI.LPL.DOWNLOAD` | Lista propia: descargar o exportar |

## Inicio

| Código | Acción |
|---|---|
| `UI.HOME.SONG` | Toque en una canción de la biblioteca en Inicio (reproduce o pausa) |
| `UI.HOME.ITEM` | Toque en una canción, álbum, artista o lista de YouTube en Inicio |

## Biblioteca (pestañas, filtros y orden son ajustes: `UI.SET.CHOICE` / `UI.SET.TOGGLE`)

| Código | Acción |
|---|---|
| `UI.LIB.ADD_MENU` | Botón «+» (abre el menú de añadir) |
| `UI.LIB.CREATE_PLAYLIST` | Crear lista |
| `UI.LIB.AI_PLAYLIST` | Lista con IA |
| `UI.LIB.IMPORT_SPOTIFY` | Importar de Spotify |
| `UI.LIB.IMPORT_YOUTUBE` | Importar de YouTube |
| `UI.LIB.MIGRATE` | Migrar |
| `UI.LIB.SORT_OPEN` | Abrir el orden |
| `UI.LIB.SEARCH` | Buscar dentro de la pestaña |
| `UI.LIB.SEARCH_CLEAR` | Borrar lo escrito en esa búsqueda |
| `UI.LIB.SHUFFLE` | Aleatorio (pestaña Canciones) |
| `UI.LIB.SONG` | Toque en una canción (vista de lista) |
| `UI.LIB.SONG_GRID` | Toque en una canción (vista de cuadrícula) |
| `UI.LIB.ALBUM` | Abrir un álbum |
| `UI.LIB.ARTIST` | Abrir un artista |
| `UI.LIB.PLAYLIST` | Abrir una lista |
| `UI.LIB.SYNC` | Sincronizar (pestaña Artistas) |

## Búsqueda

| Código | Acción |
|---|---|
| `UI.SEARCH.TAB_EXPLORE` | Pestaña 1 de Buscar |
| `UI.SEARCH.TAB_SUGGESTIONS` | Pestaña 2 de Buscar |
| `UI.SEARCH.TAB_ALBUMS` | Pestaña 3 de Buscar |
| `UI.SEARCH.LEADING` | Botón de la izquierda de la barra (buscar / cerrar) |
| `UI.SEARCH.SUBMIT` | Buscar desde el teclado (lo escrito no se registra) |
| `UI.SEARCH.CLEAR` | Borrar lo escrito |
| `UI.SEARCH.VOICE` | Dictar por voz |
| `UI.SEARCH.HISTORY` | Buscar algo del historial |
| `UI.SEARCH.HISTORY_FILL` | Copiar al campo algo del historial (flecha) |
| `UI.SEARCH.HISTORY_REMOVE` | Quitar una entrada del historial |
| `UI.SEARCH.HISTORY_CLEAR_ASK` | «Borrar historial» (abre la confirmación) |
| `UI.SEARCH.HISTORY_CLEAR` | Confirmar el borrado del historial |
| `UI.SEARCH.SUGGESTION` | Buscar una sugerencia |
| `UI.SEARCH.SUGGESTION_FILL` | Copiar una sugerencia al campo (flecha) |
| `UI.SEARCH.ITEM` | Toque en un resultado de las sugerencias |
| `UI.SEARCH.ITEM_PICK` | Elegir un resultado en el panel de detalle (pantalla ancha) |
| `UI.SEARCH.LOCAL_FILTER` | Filtro de la búsqueda en la biblioteca |
| `UI.SEARCH.LOCAL_SECTION` | Cabecera de sección de la búsqueda en la biblioteca |
| `UI.SEARCH.RESULT` | Toque en un resultado de la pantalla de resultados |

## Ajustes ▸ Sincronizar con YouTube Music (artistas seguidos importados)

| Código | Acción |
|---|---|
| `UI.YTMSYNC.IMPORT_SUBSCRIBE` | Suscribirse también en YouTube |
| `UI.YTMSYNC.IMPORT_LOCAL_ONLY` | Solo en la app |
| `UI.YTMSYNC.IMPORT_ASK_AGAIN` | Volver a preguntar |
