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

## Reproductor ▸ aviso «Sin conexión» (fila 795)

| Código | Acción |
|---|---|
| `UI.PLR.OFFLINE_NOTICE` | Tocar el aviso «Sin conexión · sonando tu música sin conexión» (abre la explicación) |
| `UI.PLR.OFFLINE_GO_ONLINE` | «Volver online» en esa explicación (apaga el modo sin conexión manual) |

---

## Fila 823 (J1): el resto de pantallas, «sin excepciones»

Los códigos de aquí abajo cubren todos los botones que faltaban. Un mismo código puede salir en dos sitios
solo cuando es el mismo botón dibujado de dos formas (por ejemplo, la línea de la letra en sus tres estilos).
Lo que se escribe (búsquedas, nombres, enlaces, claves, códigos) nunca entra en el registro: solo el código del botón.

### Hoja inferior (reproductor y cola)

| Código | Acción |
|---|---|
| `UI.SHEET.EXPAND` | Tocar la hoja plegada (minirreproductor o cola) para abrirla |

### Chips de filtro (Historial, Cuenta, Estadísticas)

| Código | Acción |
|---|---|
| `UI.CHIPS.SELECT` | Elegir un chip de filtro (Historial, Cuenta…) |
| `UI.CHIPS.MENU` | Abrir el menú del chip de periodo (Semanas…) |
| `UI.CHIPS.MENU_PICK` | Elegir una opción del menú del chip de periodo |
| `UI.CHIPS.CHOICE` | Elegir un chip de opción (Estadísticas) |

### Selector de color

| Código | Acción |
|---|---|
| `UI.COLORPICK.RESET` | «Restablecer» en el selector de color |
| `UI.COLORPICK.CANCEL` | «Cancelar» en el selector de color |
| `UI.COLORPICK.CONFIRM` | «Aceptar» en el selector de color |

### Diálogos compartidos (valor y texto)

| Código | Acción |
|---|---|
| `UI.DLG.PROMPT_RESET` | «Restablecer» en un diálogo de valor (deslizador, número…) |
| `UI.DLG.PROMPT_CANCEL` | «Cancelar» en un diálogo de valor |
| `UI.DLG.PROMPT_CONFIRM` | «Aceptar» en un diálogo de valor |
| `UI.DLG.TEXT_CANCEL` | «Cancelar» en un diálogo de texto (nombre, enlace…; lo escrito no se registra) |
| `UI.DLG.TEXT_CONFIRM` | «Aceptar» en un diálogo de texto (lo escrito no se registra) |

### Lista de opciones de Ajustes

| Código | Acción |
|---|---|
| `UI.ENUMDLG.SELECT` | Elegir una opción en una lista de opciones de Ajustes |

### Botones de reproducir sobre los elementos

| Código | Acción |
|---|---|
| `UI.ITEM.ALBUM_PLAY` | Botón de reproducir sobre un álbum de la biblioteca (cuadrícula) |
| `UI.ITEM.YT_ALBUM_PLAY` | Botón de reproducir sobre un álbum de YouTube (cuadrícula) |

### Filas y celdas de la biblioteca

| Código | Acción |
|---|---|
| `UI.LIBITEM.ARTIST_MENU` | ⋮ de un artista de la biblioteca (lista) |
| `UI.LIBITEM.ARTIST_OPEN` | Abrir un artista de la biblioteca (lista) |
| `UI.LIBITEM.ARTIST_GRID_OPEN` | Abrir un artista de la biblioteca (cuadrícula) |
| `UI.LIBITEM.ARTIST_GRID_MENU` | Pulsación larga en un artista de la biblioteca (cuadrícula): menú |
| `UI.LIBITEM.ALBUM_MENU` | ⋮ de un álbum de la biblioteca (lista) |
| `UI.LIBITEM.ALBUM_OPEN` | Abrir un álbum de la biblioteca (lista) |
| `UI.LIBITEM.ALBUM_GRID_OPEN` | Abrir un álbum de la biblioteca (cuadrícula) |
| `UI.LIBITEM.ALBUM_GRID_MENU` | Pulsación larga en un álbum de la biblioteca (cuadrícula): menú |
| `UI.LIBITEM.PLAYLIST_MENU` | ⋮ de una lista de la biblioteca (lista) |
| `UI.LIBITEM.PLAYLIST_OPEN` | Abrir una lista de la biblioteca (lista) |
| `UI.LIBITEM.PLAYLIST_GRID_OPEN` | Abrir una lista de la biblioteca (cuadrícula) |
| `UI.LIBITEM.PLAYLIST_GRID_MENU` | Pulsación larga en una lista de la biblioteca (cuadrícula): menú |

### Letra (líneas, selección y compartir)

| Código | Acción |
|---|---|
| `UI.LYR.TRANSLATE_YES` | «Traducir» en «¿Traducir la letra?» |
| `UI.LYR.TRANSLATE_NO` | «No» en «¿Traducir la letra?» |
| `UI.LYR.LINE` | Tocar una línea de la letra (salta a ella; en modo selección, la marca o desmarca) |
| `UI.LYR.LINE_SELECT` | Pulsación larga en una línea de la letra (empieza a seleccionar para compartir) |
| `UI.LYR.RESYNC` | «Volver a sincronizar» (la letra vuelve a seguir la canción) |
| `UI.LYR.SELECT_CANCEL` | «Cancelar» la selección de líneas |
| `UI.LYR.SHARE_SELECTED` | «Compartir selección» (líneas elegidas) |
| `UI.LYR.SHARE_TEXT` | «Compartir como texto» |
| `UI.LYR.SHARE_IMAGE` | «Compartir como imagen» |
| `UI.LYR.SHARE_CANCEL` | «Cancelar» en el diálogo de compartir la letra |
| `UI.LYR.IMAGE_STYLE` | Elegir el estilo de fondo de la imagen de la letra |
| `UI.LYR.IMAGE_BG_COLOR` | Elegir el color de fondo de la imagen de la letra |
| `UI.LYR.IMAGE_TEXT_COLOR` | Elegir el color del texto de la imagen de la letra |
| `UI.LYR.IMAGE_TEXT2_COLOR` | Elegir el color del texto secundario de la imagen de la letra |
| `UI.LYR.IMAGE_SHARE` | «Compartir» la imagen de la letra |

### Diálogo «Registros de reproducción»

| Código | Acción |
|---|---|
| `UI.PLAYLOGS.COPY` | «Copiar» en «Registros de reproducción» |
| `UI.PLAYLOGS.CLEAR` | «Limpiar» en «Registros de reproducción» |
| `UI.PLAYLOGS.CLOSE` | «Aceptar» (cerrar) en «Registros de reproducción» |

### Tono de llamada

| Código | Acción |
|---|---|
| `UI.RINGTONE.GRANT` | «Conceder permiso» (tono de llamada) |
| `UI.RINGTONE.OPEN_SETTINGS` | «Abrir ajustes» (tono de llamada) |
| `UI.RINGTONE.CLOSE` | «Cerrar» (tono de llamada) |
| `UI.RINGTONE.PREVIEW` | Escuchar / pausar el fragmento del tono de llamada |
| `UI.RINGTONE.SET` | «Establecer como tono de llamada» |
| `UI.RINGTONE.CANCEL` | «Cancelar» al recortar el tono de llamada |

### Aviso del aleatorio que recuerda dónde iba

| Código | Acción |
|---|---|
| `UI.SHUFMEM.CONTINUE` | «Continuar» el aleatorio donde se quedó |
| `UI.SHUFMEM.RESET` | «Empezar de cero» el aleatorio |

### Diálogo del temporizador de apagado

| Código | Acción |
|---|---|
| `UI.SLEEPDLG.TURN_OFF` | «Apagar el temporizador» |
| `UI.SLEEPDLG.CANCEL` | «Cancelar» en «Temporizador de apagado» |
| `UI.SLEEPDLG.EXTEND` | «+N min» (alargar el temporizador) |
| `UI.SLEEPDLG.START` | Elegir los minutos del temporizador |
| `UI.SLEEPDLG.END_OF_SONG` | «Al finalizar la canción» |

### Radio de esquina de la miniatura

| Código | Acción |
|---|---|
| `UI.CORNER.CUSTOM` | «Personalizado» (radio de esquina) |
| `UI.CORNER.RESET` | «Restablecer» (radio de esquina) |
| `UI.CORNER.CANCEL` | «Cancelar» (radio de esquina) |
| `UI.CORNER.CONFIRM` | «Aceptar» (radio de esquina) |
| `UI.CORNER.PRESET` | Elegir un radio de esquina predefinido |

### Texto de las notas de versión

| Código | Acción |
|---|---|
| `UI.UPDATER.LINK` | Tocar un enlace dentro de las notas de versión |

### Diálogo «Lista con IA»

| Código | Acción |
|---|---|
| `UI.AIPL.CANCEL` | «Cancelar» en «Lista con IA» |
| `UI.AIPL.GENERATE` | «Generar» la lista con IA (lo escrito no se registra) |
| `UI.AIPL.COUNT` | Elegir cuántas canciones tendrá la lista con IA |
| `UI.AIPL.OPEN_SETTINGS` | «Abrir Ajustes de IA» (desde la lista con IA) |

### Añadir a una lista

| Código | Acción |
|---|---|
| `UI.ADDPL.CREATE` | «Crear lista de reproducción» (al añadir a una lista) |
| `UI.ADDPL.CREATE_EMPTY` | «Crear lista de reproducción» (sin listas todavía) |
| `UI.ADDPL.SEARCH_CLEAR` | Borrar lo escrito en la búsqueda de listas |
| `UI.ADDPL.PICK` | Elegir la lista a la que añadir |
| `UI.ADDPL.SKIP_DUPLICATES` | «Saltar duplicados» |
| `UI.ADDPL.ADD_ANYWAY` | «Añadir de todas formas» |
| `UI.ADDPL.DUPLICATES_CANCEL` | «Cancelar» en el aviso de duplicados |

### Añadir canciones de YouTube a una lista

| Código | Acción |
|---|---|
| `UI.ADDPLYT.CREATE` | «Crear lista de reproducción» (al añadir canciones de YouTube) |
| `UI.ADDPLYT.SEARCH_CLEAR` | Borrar lo escrito en la búsqueda de listas |
| `UI.ADDPLYT.PICK` | Elegir la lista a la que añadir |
| `UI.ADDPLYT.LIKED` | Añadir a «Canciones que te gustan» |
| `UI.ADDPLYT.SKIP_DUPLICATES` | «Saltar duplicados» |
| `UI.ADDPLYT.ADD_ANYWAY` | «Añadir de todas formas» |
| `UI.ADDPLYT.DUPLICATES_CANCEL` | «Cancelar» en el aviso de duplicados |

### Importar un CSV

| Código | Acción |
|---|---|
| `UI.CSV.CANCEL` | «Cancelar» al importar un CSV |
| `UI.CSV.CONTINUE` | «Continuar» al importar un CSV |
| `UI.CSV.COLUMN_NONE` | «Ninguno» como columna del CSV |
| `UI.CSV.COLUMN` | Elegir una columna del CSV |

### Menú de la portada de una lista

| Código | Acción |
|---|---|
| `UI.COVERMENU.CHOOSE` | «Elegir de la biblioteca» (portada de la lista) |
| `UI.COVERMENU.REMOVE` | «Eliminar imagen personalizada» (portada de la lista) |

### Fijar en Biblioteca (menús)

| Código | Acción |
|---|---|
| `UI.PIN.TOGGLE` | «Fijar en Biblioteca» / «Quitar de fijados» en un menú |

### Menú de la letra

| Código | Acción |
|---|---|
| `UI.LYRMENU.SEARCH_CANCEL` | «Cancelar» en «Buscar letra» |
| `UI.LYRMENU.SEARCH_WEB` | «Buscar en línea» (abre el navegador) |
| `UI.LYRMENU.SEARCH_RUN` | «Aceptar» en «Buscar letra» (lo escrito no se registra) |
| `UI.LYRMENU.RESULT_PICK` | Elegir una letra de los resultados |
| `UI.LYRMENU.RESULT_EXPAND` | Desplegar / plegar un resultado de letra |
| `UI.LYRMENU.EDIT` | «Editar» la letra |
| `UI.LYRMENU.REFETCH` | «Cargar de nuevo» la letra |
| `UI.LYRMENU.SEARCH` | «Buscar» otra letra |
| `UI.LYRMENU.RESTORE` | «Restaurar anteriores» |
| `UI.LYRMENU.AI_TRANSLATE` | «Traducción de letras con IA» (traducir o quitar la traducción) |
| `UI.LYRMENU.OFFSET` | «Desfase de la letra» |
| `UI.LYRMENU.ROMANIZE` | «Romanizar la pista actual» |

### Tempo y tono

| Código | Acción |
|---|---|
| `UI.TEMPO.RESET` | «Restablecer» en «Tempo y tono» |
| `UI.TEMPO.CLOSE` | «Aceptar» (cerrar) en «Tempo y tono» |

### Ventana de Escuchar juntos (menú del reproductor)

| Código | Acción |
|---|---|
| `UI.LTDLG.NOT_CONFIGURED_CLOSE` | Cerrar el aviso «Escuchar juntos no está configurado» |
| `UI.LTDLG.KICK` | «Expulsar» a alguien de la sala |
| `UI.LTDLG.BLOCK` | «Bloquear permanentemente» a alguien de la sala |
| `UI.LTDLG.TRANSFER_HOST` | «Transferir propiedad» de la sala |
| `UI.LTDLG.CONNECT` | «Conectar» (Escuchar juntos) |
| `UI.LTDLG.DISCONNECT` | «Desconectar» (Escuchar juntos) |
| `UI.LTDLG.RECONNECT` | «Reconectar» (Escuchar juntos) |
| `UI.LTDLG.COPY_LINK` | «Copiar enlace» de la sala |
| `UI.LTDLG.COPY_CODE` | «Copiar código» de la sala |
| `UI.LTDLG.MEMBER` | Tocar a un miembro de la sala (anfitrión: abre sus acciones) |
| `UI.LTDLG.REQUEST_APPROVE` | «Aprobar» una petición para unirse |
| `UI.LTDLG.REQUEST_REJECT` | «Rechazar» una petición para unirse |
| `UI.LTDLG.SUGGESTION_APPROVE` | «Aprobar» una canción sugerida |
| `UI.LTDLG.SUGGESTION_REJECT` | «Rechazar» una canción sugerida |
| `UI.LTDLG.ROOM_CANCEL` | «Cancelar» en la ventana de la sala |
| `UI.LTDLG.LEAVE` | «Salir de la sala» |
| `UI.LTDLG.NAME_CLEAR` | Borrar el nombre escrito (Escuchar juntos) |
| `UI.LTDLG.CREATE` | «Crear» sala (el nombre no se registra) |
| `UI.LTDLG.JOIN` | «Unirse» a una sala (el código no se registra) |
| `UI.LTDLG.JOIN_CANCEL` | «Cancelar» al crear o unirse a una sala |

### Menú de una lista propia (⋮ de la cabecera)

| Código | Acción |
|---|---|
| `UI.LPLMENU.MP3_CANCEL` | «Cancelar» en «Exportar a MP3» |
| `UI.LPLMENU.MP3_PICK` | «Seleccionar» canciones para exportar a MP3 |
| `UI.LPLMENU.MP3_ALL` | «Todas» (exportar a MP3) |
| `UI.LPLMENU.MP3_SELECT_ALL` | Marcar / desmarcar todas (exportar a MP3) |
| `UI.LPLMENU.MP3_SONG` | Marcar / desmarcar una canción (exportar a MP3) |
| `UI.LPLMENU.MP3_SELECT_CANCEL` | «Cancelar» al elegir canciones para MP3 |
| `UI.LPLMENU.MP3_SELECT_CONTINUE` | «Continuar» con las canciones elegidas para MP3 |
| `UI.LPLMENU.REMOVE_DOWNLOAD` | «Eliminar descarga» |
| `UI.LPLMENU.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.LPLMENU.DOWNLOAD` | «Descargar» |
| `UI.LPLMENU.EDIT` | «Editar» |
| `UI.LPLMENU.AI_MODIFY` | «Editar con IA» |
| `UI.LPLMENU.SYNC` | «Sincronizar» |
| `UI.LPLMENU.ADD_TO_QUEUE` | «Añadir a la cola» |
| `UI.LPLMENU.SHARE` | «Compartir» |
| `UI.LPLMENU.EXPORT_MP3` | «Exportar a MP3» |
| `UI.LPLMENU.DELETE` | «Eliminar» la lista |

### Menú de una lista automática

| Código | Acción |
|---|---|
| `UI.AUTOPLMENU.REMOVE_DOWNLOAD` | «Eliminar descarga» |
| `UI.AUTOPLMENU.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.AUTOPLMENU.DOWNLOAD` | «Descargar» |
| `UI.AUTOPLMENU.SYNC` | «Sincronizar» |
| `UI.AUTOPLMENU.ADD_TO_QUEUE` | «Añadir a la cola» |

### Menú de la lista Top

| Código | Acción |
|---|---|
| `UI.TOPPLMENU.REMOVE_DOWNLOAD` | «Eliminar descarga» |
| `UI.TOPPLMENU.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.TOPPLMENU.DOWNLOAD` | «Descargar» |
| `UI.TOPPLMENU.ADD_TO_QUEUE` | «Añadir a la cola» |

### Menú de la lista En caché

| Código | Acción |
|---|---|
| `UI.CACHEPLMENU.REMOVE_DOWNLOAD` | «Eliminar descarga» |
| `UI.CACHEPLMENU.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.CACHEPLMENU.DOWNLOAD` | «Descargar» |
| `UI.CACHEPLMENU.ADD_TO_QUEUE` | «Añadir a la cola» |

### Menú de una canción de la cola

| Código | Acción |
|---|---|
| `UI.QMENU.ARTIST_PICK` | Elegir un artista (varios artistas) |
| `UI.QMENU.LIBRARY_TOGGLE` | Añadir a / quitar de la biblioteca |
| `UI.QMENU.START_RADIO` | «Iniciar radio» |
| `UI.QMENU.ADD_TO_PLAYLIST` | «Añadir a lista de reproducción» |
| `UI.QMENU.SHARE` | «Compartir» |
| `UI.QMENU.PLAY_NEXT` | «Reproducir a continuación» |
| `UI.QMENU.ADD_TO_QUEUE` | «Añadir a la cola» |
| `UI.QMENU.REMOVE_DOWNLOAD` | «Eliminar descarga» |
| `UI.QMENU.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.QMENU.DOWNLOAD` | «Descargar» |
| `UI.QMENU.VIEW_ARTIST` | «Ver artista» |
| `UI.QMENU.VIEW_ALBUM` | «Ver álbum» |
| `UI.QMENU.REFETCH` | «Cargar de nuevo» |
| `UI.QMENU.DETAILS` | «Detalles» |

### Menú de la selección (canciones)

| Código | Acción |
|---|---|
| `UI.SELMENU.REMOVE_DOWNLOAD_CANCEL` | «Cancelar» |
| `UI.SELMENU.REMOVE_DOWNLOAD_CONFIRM` | «Aceptar» |
| `UI.SELMENU.PLAY` | «Reproducir» |
| `UI.SELMENU.SHUFFLE` | «Aleatorio» |
| `UI.SELMENU.ADD_TO_PLAYLIST` | «Añadir a lista de reproducción» |
| `UI.SELMENU.PLAY_NEXT` | «Reproducir a continuación» |
| `UI.SELMENU.SHUFFLE_GRID` | «Aleatorio» (botón de la cuadrícula) |
| `UI.SELMENU.ADD_TO_QUEUE` | «Añadir a la cola» |
| `UI.SELMENU.ADD_TO_PLAYLIST_GRID` | «Añadir a lista de reproducción» (botón de la cuadrícula) |
| `UI.SELMENU.REMOVE_FROM_LIBRARY` | «Quitar de la biblioteca» |
| `UI.SELMENU.REMOVE_DOWNLOAD` | «Eliminar descarga» |
| `UI.SELMENU.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.SELMENU.DOWNLOAD` | «Descargar» |
| `UI.SELMENU.REMOVE_LIKE_ALL` | «Eliminar todos los me gusta» |
| `UI.SELMENU.DELETE` | «Eliminar» de la lista |

### Menú de la selección (cola e historial)

| Código | Acción |
|---|---|
| `UI.SELQMENU.REMOVE_DOWNLOAD_CANCEL` | «Cancelar» |
| `UI.SELQMENU.REMOVE_DOWNLOAD_CONFIRM` | «Aceptar» |
| `UI.SELQMENU.REMOVE_FROM_QUEUE` | «Eliminar» de la cola |
| `UI.SELQMENU.PLAY` | «Reproducir» |
| `UI.SELQMENU.SHUFFLE` | «Aleatorio» |
| `UI.SELQMENU.PLAY_NEXT` | «Reproducir a continuación» |
| `UI.SELQMENU.ADD_TO_QUEUE` | «Añadir a la cola» |
| `UI.SELQMENU.ADD_TO_PLAYLIST` | «Añadir a lista de reproducción» |
| `UI.SELQMENU.LIKE_ALL` | «Me gusta a todo» |
| `UI.SELQMENU.REMOVE_DOWNLOAD` | «Eliminar descarga» |
| `UI.SELQMENU.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.SELQMENU.DOWNLOAD` | «Descargar» |

### Menú de la selección (canciones de YouTube)

| Código | Acción |
|---|---|
| `UI.YTSELMENU.REMOVE_DOWNLOAD_CANCEL` | «Cancelar» |
| `UI.YTSELMENU.REMOVE_DOWNLOAD_CONFIRM` | «Aceptar» |
| `UI.YTSELMENU.PLAY` | «Reproducir» |
| `UI.YTSELMENU.SHUFFLE` | «Aleatorio» |
| `UI.YTSELMENU.PLAY_NEXT` | «Reproducir a continuación» |
| `UI.YTSELMENU.ADD_TO_QUEUE` | «Añadir a la cola» |
| `UI.YTSELMENU.ADD_TO_PLAYLIST` | «Añadir a lista de reproducción» |
| `UI.YTSELMENU.REMOVE_FROM_LIBRARY` | «Quitar de la biblioteca» |
| `UI.YTSELMENU.REMOVE_DOWNLOAD` | «Eliminar descarga» |
| `UI.YTSELMENU.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.YTSELMENU.DOWNLOAD` | «Descargar» |
| `UI.YTSELMENU.DISLIKE_ALL` | «No me gusta a todo» |

### Cuadrículas de álbumes

| Código | Acción |
|---|---|
| `UI.ALBGRID.ALBUM` | Abrir un álbum (cuadrícula de álbumes guardados) |
| `UI.ALBGRID.ALBUM_MENU` | Pulsación larga en un álbum (cuadrícula): menú |
| `UI.ALBGRID.SECTION_ITEM` | Tocar un elemento de la sección de un artista (cuadrícula) |
| `UI.ALBGRID.SECTION_ITEM_MENU` | Pulsación larga en un elemento de la sección (cuadrícula): menú |

### Página de elementos de un artista

| Código | Acción |
|---|---|
| `UI.ARTITEMS.RETRY` | «Reintentar» (canciones/álbumes de un artista) |
| `UI.ARTITEMS.SONG` | Tocar una canción (lista de un artista) |
| `UI.ARTITEMS.SONG_MENU` | Pulsación larga en una canción (lista de un artista): menú |
| `UI.ARTITEMS.ITEM` | Tocar un elemento (cuadrícula de un artista) |
| `UI.ARTITEMS.ITEM_MENU` | Pulsación larga en un elemento (cuadrícula de un artista): menú |

### Listas Top y En caché

| Código | Acción |
|---|---|
| `UI.COLL.VIDEO` | Tocar un vídeo (lista Top / En caché) |
| `UI.COLL.VIDEO_MENU` | Pulsación larga en un vídeo (lista Top / En caché) |
| `UI.COLL.SONG` | Tocar una canción (lista Top / En caché; en selección la marca) |
| `UI.COLL.SONG_SELECT` | Pulsación larga en una canción (lista Top / En caché): seleccionar |
| `UI.COLL.SELECTION_MENU` | «Acciones de la selección» |
| `UI.COLL.PLAY` | «Reproducir» (lista Top / En caché) |
| `UI.COLL.SEARCH` | «Buscar» en la lista |
| `UI.COLL.MORE` | «Más opciones de la colección» |
| `UI.COLL.DESCRIPTION` | Desplegar / plegar la descripción |
| `UI.COLL.SEARCH_CLEAR` | «Borrar la búsqueda» |
| `UI.COLL.SORT_OPEN` | «Cambiar el criterio de orden» (abre el menú) |
| `UI.COLL.REMOVE_DOWNLOAD_CANCEL` | «Cancelar» |
| `UI.COLL.REMOVE_DOWNLOAD_CONFIRM` | «Aceptar» |

### Cabecera de las pantallas

| Código | Acción |
|---|---|
| `UI.HDR.HEADER_TOGGLE` | Interruptor de la cabecera que muestra u oculta los accesos rápidos (Inicio, Buscar, Novedades, Biblioteca) |

### Hoja de descargar

| Código | Acción |
|---|---|
| `UI.DLSHEET.REMOVE` | «Quitar la copia sin conexión» (pide confirmar) |
| `UI.DLSHEET.CANCEL_DOWNLOAD` | «Descargando…» (cancela la descarga) |
| `UI.DLSHEET.OFFLINE` | «Descargar sin conexión» |
| `UI.DLSHEET.MP3` | «Como MP3» |
| `UI.DLSHEET.VIDEO` | «Como vídeo» |
| `UI.DLSHEET.ALL` | «Todas» |
| `UI.DLSHEET.PICK` | «Elegir canciones…» |
| `UI.DLSHEET.START` | «Empezar» con las canciones elegidas |
| `UI.DLSHEET.REMOVE_CONFIRM` | Confirmar «Quitar la copia sin conexión» |
| `UI.DLSHEET.SONG` | Marcar / desmarcar una canción |
| `UI.DLSHEET.SELECT_ALL` | Marcar / desmarcar todas |

### Descargas y listas automáticas

| Código | Acción |
|---|---|
| `UI.AUTOPL.REMOVE_DOWNLOAD_CANCEL` | «Cancelar» |
| `UI.AUTOPL.REMOVE_DOWNLOAD_CONFIRM` | «Aceptar» |
| `UI.AUTOPL.STORAGE` | «Almacenamiento» (desde Descargas) |
| `UI.AUTOPL.RETRY` | «Reintentar» una descarga fallida |
| `UI.AUTOPL.CANCEL_DOWNLOAD` | Cancelar una descarga en curso |

### Selector de cristal por segmentos

| Código | Acción |
|---|---|
| `UI.GLASSROW.SEGMENT` | Elegir un segmento de un selector de cristal |

### Inicio (más botones)

| Código | Acción |
|---|---|
| `UI.HOME.QUICK_LIKED` | Baldosa «Me gusta» de Inicio |
| `UI.HOME.QUICK_DOWNLOADED` | Baldosa «Descargado» de Inicio |
| `UI.HOME.QUICK_TOP` | Baldosa «Top» de Inicio |
| `UI.HOME.QUICK_SURPRISE` | Baldosa «Aleatorio» (mezcla de tu gusto) de Inicio; tocarla mientras elige cancela la elección |
| `UI.HOME.SONG_MENU` | Pulsación larga en una canción de cualquier estante de Inicio: menú |
| `UI.HOME.ITEM_MENU` | Pulsación larga en un elemento de YouTube de cualquier estante de Inicio: menú |
| `UI.HOME.CHIP` | Chip de ánimo de Inicio |
| `UI.HOME.SPEED_DIAL_SONG` | Tocar una canción (estante de canciones de YouTube) |
| `UI.HOME.PODCAST` | Abrir un podcast (Inicio) |
| `UI.HOME.DAILY_TOP` | Cabecera «Top diario» (abre la lista) |
| `UI.HOME.DAILY_TOP_SONG` | Tocar una canción del Top diario |
| `UI.HOME.MOMENT_SONG` | Tocar una canción del estante del momento |
| `UI.HOME.COMMUNITY` | Abrir una lista de la comunidad |
| `UI.HOME.ACCOUNT_PLAYLISTS` | «Sus listas de reproducción de YouTube» |
| `UI.HOME.AI_RECS` | Abrir la lista «Recomendadas por IA» |
| `UI.HOME.VIDEO_SONG` | Tocar una canción (estante de vídeos) |
| `UI.HOME.MOODS` | «Estado de ánimo y géneros» (ver todos) |
| `UI.HOME.MOOD` | Tocar un ánimo o género (Inicio) |

### Fijados y atajos de Biblioteca

| Código | Acción |
|---|---|
| `UI.LIBPIN.PINS_EDIT` | «Editar» / «Listo» de los fijados de Biblioteca |
| `UI.LIBPIN.JUMP_PLAYLISTS` | Atajo «Listas» de Biblioteca |
| `UI.LIBPIN.JUMP_ALBUMS` | Atajo «Álbumes» de Biblioteca |
| `UI.LIBPIN.JUMP_ARTISTS` | Atajo «Artistas» de Biblioteca |

### Lista o cuadrícula en Biblioteca

| Código | Acción |
|---|---|
| `UI.LIBVIEW.TOGGLE` | Cambiar entre lista y cuadrícula (Biblioteca; el valor nuevo sale después como UI.SET.CHOICE) |

### Letra a pantalla completa

| Código | Acción |
|---|---|
| `UI.LYRFULL.CLOSE` | «Cerrar la letra» (letra a pantalla completa) |
| `UI.LYRFULL.APPEARANCE` | «Aspecto de la letra» (abre / cierra el panel) |
| `UI.LYRFULL.MENU` | «Menú de la letra» |
| `UI.LYRFULL.TITLE` | Tocar el título (va al álbum) |
| `UI.LYRFULL.TITLE_COPY` | Pulsación larga en el título (copiar) |
| `UI.LYRFULL.ARTIST` | Tocar el artista (va a su página) |
| `UI.LYRFULL.ARTIST_COPY` | Pulsación larga en el artista (copiar) |
| `UI.LYRFULL.PREV` | Anterior (letra a pantalla completa) |
| `UI.LYRFULL.PLAY` | Reproducir / pausa (letra a pantalla completa) |
| `UI.LYRFULL.NEXT` | Siguiente (letra a pantalla completa) |
| `UI.LYRFULL.APPEARANCE_CANCEL` | «Cancelar» en «Aspecto de la letra» |
| `UI.LYRFULL.PROVIDERS` | «Prioridad de proveedores de letras» (desde la letra) |

### Migración

| Código | Acción |
|---|---|
| `UI.MIG.DEEZER_CONTINUE` | «Continuar» en «Importar de Deezer» (el enlace no se registra) |
| `UI.MIG.DEEZER_CANCEL` | «Cancelar» en «Importar de Deezer» |
| `UI.MIG.ERROR_OK` | «Aceptar» en «Error de migración» |
| `UI.MIG.YOUTUBE_LOGIN` | «Iniciar sesión con Google» (migración) |
| `UI.MIG.SOURCE_FILE` | Origen «Archivo (CSV, M3U, JSPF)» |
| `UI.MIG.SOURCE_DEEZER` | Origen «Deezer» |
| `UI.MIG.SOURCE_TIDAL` | Origen «Tidal» |
| `UI.MIG.SOURCE_APPLE` | Origen «Apple Music» |
| `UI.MIG.PICK_PLAYLIST` | Elegir una lista para migrar |
| `UI.MIG.BACK_TO_SOURCES` | «Elegir otro origen» |
| `UI.MIG.LIBRARY_CANCEL` | «Cancelar» la migración |
| `UI.MIG.LIBRARY_CONTINUE` | «Continuar» la migración |
| `UI.MIG.REVIEW_AMBIGUOUS` | «Revisar ambiguas» |
| `UI.MIG.EXPORT_FAILURES` | «Exportar no migrados (CSV)» |
| `UI.MIG.SHARE_FAILURES` | «Compartir CSV» de no migrados |
| `UI.MIG.OPEN_LIBRARY` | «Abrir en Biblioteca» |
| `UI.MIG.ANOTHER` | «Migrar otra» |
| `UI.MIG.REVIEW_ADD` | «Añadir seleccionadas» (revisión de ambiguas) |
| `UI.MIG.REVIEW_CHOOSE` | Elegir una de las candidatas (revisión de ambiguas) |
| `UI.MIG.REVIEW_SKIP` | «Omitida» (revisión de ambiguas) |
| `UI.MIG.TIDAL_DASHBOARD` | «Abrir panel» de desarrolladores de Tidal |
| `UI.MIG.TIDAL_CLIENT_SAVE` | «Guardar» el Client ID de Tidal (no se registra) |
| `UI.MIG.TIDAL_CLIENT_CHANGE` | «Cambiar» el Client ID de Tidal |
| `UI.MIG.TIDAL_LOGIN` | «Iniciar sesión en Tidal» |
| `UI.MIG.TIDAL_ERROR_OK` | «Aceptar» en el error de Tidal |
| `UI.MIG.TIDAL_LOGOUT` | «Cerrar sesión de Tidal» |
| `UI.MIG.TIDAL_PICK` | Elegir una lista de Tidal |
| `UI.MIG.TIDAL_RELOAD` | «Recargar listas» de Tidal |
| `UI.MIG.TIDAL_IMPORT_URL` | «Importar» una lista de Tidal por enlace (no se registra) |
| `UI.MIG.APPLE_PRIVACY` | «Abrir privacy.apple.com» |

### Novedades

| Código | Acción |
|---|---|
| `UI.NOV.RETRY` | «Reintentar» (Novedades) |
| `UI.NOV.FILTER` | Chip de filtro de Novedades |
| `UI.NOV.OPEN` | Abrir un estreno (tarjeta) |
| `UI.NOV.MENU` | Pulsación larga en un estreno (tarjeta): menú |
| `UI.NOV.UPCOMING_OPEN` | Abrir un próximo estreno |
| `UI.NOV.PRESAVE` | «Guardar para el estreno» |
| `UI.NOV.UPCOMING_COVER` | Tocar la portada de un próximo estreno |
| `UI.NOV.PLAY` | Reproducir / pausa un estreno (detalle) |
| `UI.NOV.DETAIL_OPEN` | «Abrir» un estreno (detalle) |
| `UI.NOV.DETAIL_MENU` | «Más opciones» de un estreno (detalle) |
| `UI.NOV.GRID_OPEN` | Abrir un estreno (cuadrícula) |
| `UI.NOV.GRID_MENU` | Pulsación larga en un estreno (cuadrícula): menú |

### Búsqueda sin conexión

| Código | Acción |
|---|---|
| `UI.OFFSEARCH.FILTER` | Chip de filtro de la búsqueda sin conexión |
| `UI.OFFSEARCH.SONGS_HEADER` | Cabecera «Canciones» (filtra) |
| `UI.OFFSEARCH.SONG` | Tocar una canción (búsqueda sin conexión) |
| `UI.OFFSEARCH.SONG_MENU` | Pulsación larga en una canción (búsqueda sin conexión) |
| `UI.OFFSEARCH.ALBUMS_HEADER` | Cabecera «Álbumes» (filtra) |
| `UI.OFFSEARCH.ALBUM` | Abrir un álbum (búsqueda sin conexión) |
| `UI.OFFSEARCH.PLAYLISTS_HEADER` | Cabecera «Listas» (filtra) |
| `UI.OFFSEARCH.PLAYLIST` | Abrir una lista (búsqueda sin conexión) |
| `UI.OFFSEARCH.ARTISTS_HEADER` | Cabecera «Artistas» (filtra) |
| `UI.OFFSEARCH.ARTIST` | Abrir un artista (búsqueda sin conexión) |
| `UI.OFFSEARCH.ARTIST_BACK` | Volver de la página de un artista (búsqueda sin conexión) |
| `UI.OFFSEARCH.ARTIST_SHUFFLE` | «Aleatorio» (artista, sin conexión) |
| `UI.OFFSEARCH.ARTIST_PLAY_ALL` | «Reproducir todo» (artista, sin conexión) |
| `UI.OFFSEARCH.ARTIST_SONG` | Tocar una canción (artista, sin conexión) |
| `UI.OFFSEARCH.ARTIST_SONG_MENU` | Pulsación larga en una canción (artista, sin conexión) |

### Búsqueda rápida del reproductor

| Código | Acción |
|---|---|
| `UI.QSEARCH.BACK` | «Atrás» (búsqueda rápida del reproductor) |
| `UI.QSEARCH.RESULT` | Tocar un resultado (búsqueda rápida) |
| `UI.QSEARCH.RESULT_MENU` | Pulsación larga en un resultado (búsqueda rápida) |
| `UI.QSEARCH.HISTORY` | Buscar algo del historial (búsqueda rápida) |
| `UI.QSEARCH.SUGGESTION` | Buscar una sugerencia (búsqueda rápida) |
| `UI.QSEARCH.SONG_PLAY` | «Reproducir» (acciones de una canción, búsqueda rápida) |
| `UI.QSEARCH.SONG_PLAY_NEXT` | «Reproducir a continuación» (búsqueda rápida) |
| `UI.QSEARCH.SONG_ADD_TO_QUEUE` | «Añadir a la cola» (búsqueda rápida) |
| `UI.QSEARCH.SONG_ADD_TO_PLAYLIST` | «Añadir a lista de reproducción» (búsqueda rápida) |

### Estante «Relacionado»

| Código | Acción |
|---|---|
| `UI.RELATED.ITEM` | Tocar un elemento del estante «Relacionado» |

### Pestañas de Buscar (Explorar, tendencias, álbumes nuevos)

| Código | Acción |
|---|---|
| `UI.SEARCHTAB.EMPTY_ACTION` | Botón del aviso vacío de una pestaña de Buscar |
| `UI.SEARCHTAB.GENRE` | Baldosa de género / ánimo (pestaña Explorar) |
| `UI.SEARCHTAB.TRACKS_MORE` | Cabecera de un estante de canciones («Ver más») |
| `UI.SEARCHTAB.TRACK` | Tocar una canción de un estante (pestañas de Buscar) |
| `UI.SEARCHTAB.TRACKS_SEE_MORE` | «Ver más» canciones |
| `UI.SEARCHTAB.ALBUMS_MORE` | Cabecera de un estante de álbumes |
| `UI.SEARCHTAB.ALBUM` | Tocar un álbum de un estante (pestañas de Buscar) |
| `UI.SEARCHTAB.ALBUMS_SEE_MORE` | «Ver más» álbumes |
| `UI.SEARCHTAB.ARTIST` | Tocar un artista de un estante (pestañas de Buscar) |
| `UI.SEARCHTAB.NEW_ALBUM` | Abrir un álbum nuevo (pestaña de álbumes) |
| `UI.SEARCHTAB.NEW_ALBUM_MENU` | Pulsación larga en un álbum nuevo: menú |

### Índice de Ajustes

| Código | Acción |
|---|---|
| `UI.SETIDX.BACK` | Flecha atrás de Ajustes |
| `UI.SETIDX.SEARCH_RESULT` | Abrir un resultado de la búsqueda de Ajustes (el texto buscado no se registra) |
| `UI.SETIDX.GROUP` | Tocar un grupo del índice de Ajustes (abre la pantalla o despliega el grupo) |
| `UI.SETIDX.SEARCH_CLEAR` | «Borrar la búsqueda» de Ajustes |

### Hoja de compartir

| Código | Acción |
|---|---|
| `UI.SHARE.LINK` | Compartir «Enlace» |
| `UI.SHARE.MP3` | Compartir «MP3» |
| `UI.SHARE.VIDEO` | Compartir «Vídeo» |
| `UI.SHARE.HIDE` | «Seguir en segundo plano» (mientras se prepara lo compartido) |

### Temporizador de apagado (menú del reproductor)

| Código | Acción |
|---|---|
| `UI.SLEEP.START` | Elegir los minutos del temporizador (menú del reproductor) |
| `UI.SLEEP.END_OF_SONG` | «Al finalizar la canción» (menú del reproductor) |
| `UI.SLEEP.TURN_OFF` | «Apagar el temporizador» (menú del reproductor) |

### Estadísticas

| Código | Acción |
|---|---|
| `UI.STATS.HISTORY` | «Resumen de escucha» (abre el historial de actividad) |
| `UI.STATS.SONG` | Tocar una canción de Estadísticas |
| `UI.STATS.ARTIST` | Abrir un artista de Estadísticas |
| `UI.STATS.ARTIST_MENU` | Pulsación larga en un artista de Estadísticas: menú |
| `UI.STATS.ALBUM` | Abrir un álbum de Estadísticas |
| `UI.STATS.ALBUM_MENU` | Pulsación larga en un álbum de Estadísticas: menú |
| `UI.STATS.SHUFFLE` | Aleatorio de lo más escuchado |
| `UI.STATS.PERIOD_OPEN` | «Cambiar el tipo de periodo» (abre el menú) |
| `UI.STATS.PERIOD_PICK` | Elegir el tipo de periodo |
| `UI.STATS.PERIOD_CHIP` | Chip de periodo (semana, mes…) |

### «Seguir el estilo» (cola)

| Código | Acción |
|---|---|
| `UI.STYLE.OPEN` | Abrir el menú «Seguir el estilo» (cola) |
| `UI.STYLE.PICK` | Elegir una opción de «Seguir el estilo» |

### Letra en el reproductor

| Código | Acción |
|---|---|
| `UI.LYRINLINE.SEARCH` | «Buscar letra» (sin letra) |

### Panel lateral «Sonando» (pantallas anchas)

| Código | Acción |
|---|---|
| `UI.SIDE.EXPAND` | Abrir el reproductor desde el panel lateral (pantalla ancha) |
| `UI.SIDE.DISLIKE` | «No me gusta» (panel lateral) |
| `UI.SIDE.PREV` | Anterior (panel lateral) |
| `UI.SIDE.PLAY` | Reproducir / pausa (panel lateral) |
| `UI.SIDE.NEXT` | Siguiente (panel lateral) |
| `UI.SIDE.LIKE` | «Me gusta» (panel lateral) |

### Aviso de error de reproducción

| Código | Acción |
|---|---|
| `UI.PLRERR.ACTION` | Botón del aviso de error de reproducción (reintentar o abrir el ajuste que lo arregla) |
| `UI.PLRERR.RETRY` | «Reintentar» en el aviso de error de reproducción |

### Cuenta (biblioteca de YouTube)

| Código | Acción |
|---|---|
| `UI.ACCOUNT.PLAYLIST` | Abrir una lista (Cuenta) |
| `UI.ACCOUNT.PLAYLIST_MENU` | Pulsación larga en una lista (Cuenta): menú |
| `UI.ACCOUNT.ALBUM` | Abrir un álbum (Cuenta) |
| `UI.ACCOUNT.ALBUM_MENU` | Pulsación larga en un álbum (Cuenta): menú |
| `UI.ACCOUNT.ARTIST` | Abrir un artista (Cuenta) |
| `UI.ACCOUNT.ARTIST_MENU` | Pulsación larga en un artista (Cuenta): menú |
| `UI.ACCOUNT.BACK` | Atrás (Cuenta) |

### Explorar

| Código | Acción |
|---|---|
| `UI.BROWSE.ITEM` | Tocar un elemento (Explorar) |
| `UI.BROWSE.ITEM_MENU` | Pulsación larga en un elemento (Explorar): menú |
| `UI.BROWSE.BACK` | Atrás (Explorar) |

### Comentarios

| Código | Acción |
|---|---|
| `UI.COMMENTS.CLOSE` | «Cerrar» los comentarios |
| `UI.COMMENTS.REPLIES` | Tocar un comentario (ver respuestas) |
| `UI.COMMENTS.REPLIES_BUTTON` | Botón de respuestas de un comentario |
| `UI.COMMENTS.BACK` | Volver de las respuestas a los comentarios |

### Historial

| Código | Acción |
|---|---|
| `UI.HISTORY.SOURCE` | Chip de origen del historial (este aparato / YouTube) |
| `UI.HISTORY.REMOTE_SONG_MENU` | ⋮ de una canción del historial de YouTube |
| `UI.HISTORY.REMOTE_SONG` | Tocar una canción del historial de YouTube |
| `UI.HISTORY.REMOTE_SONG_LONG` | Pulsación larga en una canción del historial de YouTube: menú |
| `UI.HISTORY.SONG` | Tocar una canción del historial (en selección la marca) |
| `UI.HISTORY.SONG_SELECT` | Pulsación larga en una canción del historial: seleccionar |
| `UI.HISTORY.SHUFFLE` | Botón de aleatorio del historial |
| `UI.HISTORY.SELECT_EXIT` | Salir de la selección (historial) |
| `UI.HISTORY.BACK` | Atrás / cerrar la búsqueda (historial) |
| `UI.HISTORY.SELECT_MENU` | ⋮ de la selección (historial) |
| `UI.HISTORY.SEARCH` | Buscar en el historial (lo escrito no se registra) |

### Inicio: listas de la comunidad

| Código | Acción |
|---|---|
| `UI.HOMECOMM.SONG` | Tocar una canción de una lista de la comunidad (Inicio) |
| `UI.HOMECOMM.PLAY` | Reproducir una lista de la comunidad (Inicio) |
| `UI.HOMECOMM.RADIO` | Radio de una lista de la comunidad (Inicio) |
| `UI.HOMECOMM.SAVE` | Guardar / quitar una lista de la comunidad (Inicio) |

### Ánimos y géneros

| Código | Acción |
|---|---|
| `UI.MOODS.ITEM` | Tocar un ánimo o género |
| `UI.MOODS.BACK` | Atrás (Ánimos y géneros) |

### Lanzamientos nuevos

| Código | Acción |
|---|---|
| `UI.NEWREL.ITEM` | Reproducir un lanzamiento nuevo |
| `UI.NEWREL.ITEM_MENU` | Pulsación larga en un lanzamiento nuevo: menú |
| `UI.NEWREL.BACK` | Atrás (Lanzamientos nuevos) |

### Inicio sin conexión

| Código | Acción |
|---|---|
| `UI.OFFHOME.PLAY_ALL` | «Reproducir todo» (Inicio sin conexión) |
| `UI.OFFHOME.SHUFFLE` | «Aleatorio» (Inicio sin conexión) |
| `UI.OFFHOME.DOWNLOADS` | «Mis descargas» (Inicio sin conexión) |
| `UI.OFFHOME.STORAGE` | «Almacenamiento» (Inicio sin conexión) |
| `UI.OFFHOME.ALBUM` | Abrir un álbum descargado |
| `UI.OFFHOME.PLAYLIST` | Abrir una lista descargada |
| `UI.OFFHOME.SONG` | Tocar una canción (Inicio sin conexión) |
| `UI.OFFHOME.SONG_MENU` | Pulsación larga en una canción (Inicio sin conexión) |
| `UI.OFFHOME.GO_ONLINE` | «Volver online» (aviso del modo sin conexión) |
| `UI.OFFHOME.RETRY_ONLINE` | «Reintentar en línea» |
| `UI.OFFHOME.GO_ONLINE_BANNER` | «Volver online» (franja del modo sin conexión) |
| `UI.OFFHOME.SHELF_SONG` | Tocar una canción de un estante (Inicio sin conexión) |
| `UI.OFFHOME.SHELF_SONG_MENU` | Pulsación larga en una canción de un estante (Inicio sin conexión) |

### Bienvenida: artistas

| Código | Acción |
|---|---|
| `UI.ONBART.CONTINUE` | «Continuar» (bienvenida: artistas) |
| `UI.ONBART.SKIP` | «Omitir» (bienvenida: artistas) |
| `UI.ONBART.SUGGESTION` | Tocar una sugerencia de búsqueda (bienvenida: artistas) |
| `UI.ONBART.ARTIST` | Marcar / desmarcar un artista (bienvenida) |

### Bienvenida: géneros

| Código | Acción |
|---|---|
| `UI.ONBGEN.CONTINUE` | «Continuar» (bienvenida: géneros) |
| `UI.ONBGEN.SKIP` | «Omitir» (bienvenida: géneros) |
| `UI.ONBGEN.GENRE` | Marcar / desmarcar un género (bienvenida) |

### Bienvenida: Spotify

| Código | Acción |
|---|---|
| `UI.ONBSPOT.CONNECT` | «Conectar Spotify y elegir qué migrar» (bienvenida) |
| `UI.ONBSPOT.NEXT` | «Siguiente: tu cuenta de Google» (bienvenida) |

### Bienvenida: cuenta de Google

| Código | Acción |
|---|---|
| `UI.ONBYT.SYNC` | «Sincronizar con tu cuenta de Google» (bienvenida) |
| `UI.ONBYT.START` | «Comenzar» (fin de la bienvenida) |

### Abrir enlaces de música

| Código | Acción |
|---|---|
| `UI.OPENLINKS.ALLOW` | «Permitir» abrir enlaces de música con Aura |
| `UI.OPENLINKS.LATER` | «Más tarde» (abrir enlaces) |
| `UI.OPENLINKS.SERVICE` | Activar / desactivar los enlaces de un servicio (Spotify, YouTube…) |

### Podcasts

| Código | Acción |
|---|---|
| `UI.PODCAST.BACK` | Atrás (Podcasts) |
| `UI.PODCAST.PIN` | Fijar / quitar un podcast (barra) |
| `UI.PODCAST.SHOW_BACK` | Atrás (Podcasts) |
| `UI.PODCAST.SHOW_PIN` | Fijar / quitar un podcast |
| `UI.PODCAST.REGION` | Elegir la región de los podcasts (abre la lista) |
| `UI.PODCAST.CONTINUE` | Seguir escuchando un episodio empezado |
| `UI.PODCAST.OPEN_SHOW` | Abrir un podcast |
| `UI.PODCAST.REGION_PICK` | Elegir una región |
| `UI.PODCAST.REGION_CLOSE` | «Cerrar» la lista de regiones |
| `UI.PODCAST.SEARCH_CLEAR` | Borrar la búsqueda de podcasts |
| `UI.PODCAST.EPISODE` | Reproducir un episodio |
| `UI.PODCAST.REGION_OPEN` | Elegir la región (cabecera) |
| `UI.PODCAST.SHOW_EPISODE` | Reproducir un episodio (página del podcast) |
| `UI.PODCAST.REGION_DIALOG_CLOSE` | «Cerrar» el diálogo de regiones |
| `UI.PODCAST.REGION_DIALOG_PICK` | Elegir una región (diálogo) |

### Ventana de la cuenta (barra superior)

| Código | Acción |
|---|---|
| `UI.SETDLG.CLOSE` | Cerrar la ventana de cuenta |
| `UI.SETDLG.SYNC_EXPAND` | Desplegar / plegar la sincronización con Google |
| `UI.SETDLG.SYNC_ROW` | «Sincronización con tu cuenta de Google» (despliega) |
| `UI.SETDLG.SYNC_NOW` | «Sincronizar biblioteca ahora» |
| `UI.SETDLG.SYNC_SCHEDULE` | «Programar sincronización» |
| `UI.SETDLG.LOGIN` | «Iniciar sesión» |
| `UI.SETDLG.UPDATES` | «Actualizaciones» |
| `UI.SETDLG.DEVICE_SYNC` | «Sincronizar dispositivos» |
| `UI.SETDLG.AURA_SERVICES` | «Servicios Aura» |
| `UI.SETDLG.FEEDBACK` | «Reporte de errores y sugerencias» |
| `UI.SETDLG.SETTINGS` | «Ajustes» |
| `UI.SETDLG.SHARE_APP` | «Compartir Aura Hi-Res» |
| `UI.SETDLG.ABOUT` | «Acerca de» |

### Importar de Spotify

| Código | Acción |
|---|---|
| `UI.SPOTIMP.BACK` | Atrás (Importar de Spotify) |
| `UI.SPOTIMP.ONBOARDING_NEXT` | «Continuar (siguiente paso)» (bienvenida) |
| `UI.SPOTIMP.CONNECT` | «Conectar Spotify» |
| `UI.SPOTIMP.ADD_BY_LINK` | «Importar lista por link» (el enlace no se registra) |
| `UI.SPOTIMP.SELECT_SOURCES` | «Seleccionar listas de Spotify» |
| `UI.SPOTIMP.ADD_BY_LINK_CONNECTED` | «Importar lista por link» (con sesión) |
| `UI.SPOTIMP.IMPORT_SELECTED` | «Importar seleccionadas» |
| `UI.SPOTIMP.REFRESH` | «Actualizar biblioteca de Spotify» |
| `UI.SPOTIMP.LOGOUT` | «Finalizar sesión» de Spotify |
| `UI.SPOTIMP.SYNC_FREQUENCY` | «Frecuencia» de la sincronización automática |
| `UI.SPOTIMP.SYNC_SOURCES` | «Listas a sincronizar» |
| `UI.SPOTIMP.ADD_BY_LINK_CONFIRM` | «Agregar» la lista por link |
| `UI.SPOTIMP.ADD_BY_LINK_CANCEL` | «Cancelar» en «Importar lista por link» |
| `UI.SPOTIMP.SYNC_FREQUENCY_PICK` | Elegir cada cuántos días sincronizar |
| `UI.SPOTIMP.SYNC_FREQUENCY_CLOSE` | «Cerrar» la frecuencia |
| `UI.SPOTIMP.IMPORT_BACKGROUND` | «Segundo plano» (importación de Spotify) |
| `UI.SPOTIMP.IMPORT_CANCEL` | «Cancelar» la importación de Spotify |
| `UI.SPOTIMP.SOURCES_CLEAR` | «Limpiar» la selección de listas |
| `UI.SPOTIMP.SOURCES_ALL` | «Seleccionar todo» (listas de Spotify) |
| `UI.SPOTIMP.SOURCE` | Marcar / desmarcar una lista de Spotify |
| `UI.SPOTIMP.SOURCES_IMPORT` | «Importar seleccionadas» (hoja de listas) |
| `UI.SPOTIMP.SYNC_SOURCE` | Marcar / desmarcar una lista a sincronizar |
| `UI.SPOTIMP.SYNC_SOURCES_DONE` | «Listo» (listas a sincronizar) |
| `UI.SPOTIMP.ERROR_OK` | «Aceptar» en el error de la importación |
| `UI.SPOTIMP.SAVE_FAILURES` | «Guardar CSV en el dispositivo» (fallos) |
| `UI.SPOTIMP.SHARE_FAILURES` | «Compartir CSV» (fallos) |
| `UI.SPOTIMP.RETRY_FAILURES` | «Reintentar fallos» |
| `UI.SPOTIMP.DONE_OK` | «Aceptar» en «Importación de Spotify completada» |

### Página de YouTube

| Código | Acción |
|---|---|
| `UI.YTBROWSE.ITEM` | Tocar un elemento (página de YouTube) |
| `UI.YTBROWSE.ITEM_MENU` | Pulsación larga en un elemento (página de YouTube): menú |
| `UI.YTBROWSE.BACK` | Atrás (página de YouTube) |

### Artista: todas las canciones

| Código | Acción |
|---|---|
| `UI.ARTSONGS.SONG` | Tocar una canción (todas las canciones de un artista) |
| `UI.ARTSONGS.SONG_MENU` | Pulsación larga en una canción (todas las canciones de un artista) |
| `UI.ARTSONGS.BACK` | Atrás (canciones de un artista) |

### Auto-EQ

| Código | Acción |
|---|---|
| `UI.AUTOEQ.REFRESH` | Recargar el catálogo de Auto-EQ |
| `UI.AUTOEQ.FAVORITE` | Añadir / quitar un auricular de favoritos |
| `UI.AUTOEQ.APPLY` | Aplicar el perfil de un auricular |

### Ecualizador

| Código | Acción |
|---|---|
| `UI.EQ.BACK` | Atrás (Ecualizador) |
| `UI.EQ.METER_PERMISSION` | Tocar el medidor para conceder el permiso del micrófono |
| `UI.EQ.ENABLE` | Encender / apagar el ecualizador |
| `UI.EQ.AUTOEQ_CLEAR` | «Quitar Auto-EQ» |
| `UI.EQ.AUTOEQ_CHANGE` | «Cambiar Auto-EQ (por auricular)» |
| `UI.EQ.AUTOEQ_OPEN` | «Auto-EQ (por auricular)» |
| `UI.EQ.PROFILE_SAVE` | «Guardar» el perfil (abre el diálogo) |
| `UI.EQ.VERIFY` | «Verificar ecualizador» |
| `UI.EQ.PER_DEVICE` | «EQ por dispositivo» |
| `UI.EQ.EXPORT` | «Exportar perfiles» |
| `UI.EQ.IMPORT` | «Importar» perfiles |
| `UI.EQ.DEVICE_PROFILE_OPEN` | Elegir el perfil de un dispositivo de salida (abre el menú) |
| `UI.EQ.DEVICE_PROFILE_NONE` | «Ninguno» como perfil de un dispositivo |
| `UI.EQ.DEVICE_PROFILE_PICK` | Elegir el perfil de un dispositivo |
| `UI.EQ.PER_DEVICE_DONE` | «Listo» (EQ por dispositivo) |
| `UI.EQ.PRESET` | Elegir un preajuste |
| `UI.EQ.BAND` | Elegir una banda |
| `UI.EQ.BAND_GAIN_STEP` | Subir / bajar / poner a 0 la ganancia de la banda (botones) |
| `UI.EQ.AUDIBLE_DEMO` | Prueba audible del EQ (curva extrema 5 s) |
| `UI.EQ.BAND_Q_DOWN` | «Q −0.05» |
| `UI.EQ.BAND_Q_DEFAULT` | «Q 1.41» |
| `UI.EQ.BAND_Q_UP` | «Q +0.05» |
| `UI.EQ.BAND_TYPE` | Tipo de filtro de la banda |
| `UI.EQ.BANDS_RESET` | «Restablecer» las bandas |
| `UI.EQ.MODE_GRAPHIC` | Modo «Gráfico» |
| `UI.EQ.MODE_PARAMETRIC` | Modo «Paramétrico» |
| `UI.EQ.BAND_REMOVE` | «Quitar banda» |
| `UI.EQ.BAND_ADD` | «Añadir banda» |
| `UI.EQ.PARAMETRIC_RESET` | «Restablecer» (paramétrico) |
| `UI.EQ.FILTER_TYPE` | Tipo de filtro (PK / LSC / HSC) |
| `UI.EQ.PROFILES_EDIT` | Editar los perfiles guardados |
| `UI.EQ.PROFILE_APPLY` | Aplicar un perfil guardado |
| `UI.EQ.PROFILE_SAVE_CANCEL` | «Cancelar» al guardar un perfil |
| `UI.EQ.PROFILE_SAVE_CONFIRM` | «Guardar» el perfil (el nombre no se registra) |
| `UI.EQ.PROFILE_SELECT` | Marcar / desmarcar un perfil para borrar |
| `UI.EQ.PROFILES_DELETE_CANCEL` | «Cancelar» al borrar perfiles |
| `UI.EQ.PROFILES_DELETE` | «Eliminar» los perfiles marcados |

### Música local

| Código | Acción |
|---|---|
| `UI.LOCAL.SEARCH_BACK` | Atrás desde la búsqueda (música local) |
| `UI.LOCAL.SEARCH_CLEAR` | Borrar la búsqueda (música local) |
| `UI.LOCAL.SEARCH` | Buscar (música local) |
| `UI.LOCAL.SCAN_SETTINGS` | Ajustes del escaneo (música local) |
| `UI.LOCAL.BACK` | Atrás (música local) |
| `UI.LOCAL.SEARCH_EMBEDDED` | Buscar (música local, en Biblioteca) |
| `UI.LOCAL.SCAN_SETTINGS_EMBEDDED` | Ajustes del escaneo (música local, en Biblioteca) |
| `UI.LOCAL.DOWNLOADED` | «Descargado» (música local) |
| `UI.LOCAL.CACHED` | «En caché» (música local) |
| `UI.LOCAL.SONG` | Tocar una canción local |
| `UI.LOCAL.SONG_MENU` | Pulsación larga en una canción local: menú |
| `UI.LOCAL.SCAN` | Botón principal del escaneo (escanear / escaneando) |
| `UI.LOCAL.REMOVE_MISSING` | «Quitar de Aura las canciones locales que ya no están en el teléfono» |
| `UI.LOCAL.REMOVE_MISSING_CONFIRM` | «Quitar» (confirmación) |
| `UI.LOCAL.REMOVE_MISSING_CANCEL` | «Cancelar» (quitar canciones que ya no están) |
| `UI.LOCAL.FOLDER_REMOVE` | Quitar una carpeta del escaneo |

### Radar de novedades

| Código | Acción |
|---|---|
| `UI.RADAR.BACK` | Atrás (Radar de novedades) |
| `UI.RADAR.REFRESH` | Actualizar el radar de novedades |
| `UI.RADAR.OPEN` | Abrir un estreno del radar |
| `UI.RADAR.PLAY` | «Reproducir» un estreno del radar |

### Agregar música (lista propia)

| Código | Acción |
|---|---|
| `UI.ADDMUSIC.ADD_BUTTON` | «Agregar música» (abre la hoja) |
| `UI.ADDMUSIC.SUGGESTIONS_REFRESH` | «Actualizar» las sugerencias |
| `UI.ADDMUSIC.SUGGESTION_PLAY` | Reproducir una canción sugerida |
| `UI.ADDMUSIC.FEATURED_ARTIST` | Abrir un artista destacado |
| `UI.ADDMUSIC.SEARCH_CLEAR` | Borrar la búsqueda (Agregar música) |
| `UI.ADDMUSIC.PREVIEW` | Escuchar / parar el avance de una canción |
| `UI.ADDMUSIC.ADD_SELECTED` | «Agregar» las canciones elegidas |

### Editar una lista con IA

| Código | Acción |
|---|---|
| `UI.AIMOD.OPEN_SETTINGS` | «Abrir Ajustes de IA» (editar con IA) |
| `UI.AIMOD.REVIEW` | «Revisar» los cambios de la IA (lo escrito no se registra) |
| `UI.AIMOD.APPLY` | «Aplicar» los cambios de la IA |
| `UI.AIMOD.CANCEL` | «Cancelar» (editar con IA) |

### Historial de reconocimiento

| Código | Acción |
|---|---|
| `UI.RECHIST.CLEAR_CANCEL` | «Cancelar» en «Borrar historial de reconocimiento» |
| `UI.RECHIST.CLEAR_CONFIRM` | «Limpiar» el historial de reconocimiento |
| `UI.RECHIST.DELETE_CANCEL` | «Cancelar» al borrar una entrada |
| `UI.RECHIST.DELETE_CONFIRM` | «Eliminar» una entrada |
| `UI.RECHIST.BACK` | Atrás (historial de reconocimiento) |
| `UI.RECHIST.CLEAR` | «Borrar historial de reconocimiento» |
| `UI.RECHIST.SEARCH_CLEAR` | Borrar la búsqueda (historial de reconocimiento) |
| `UI.RECHIST.ITEM` | Tocar una entrada (la busca) |
| `UI.RECHIST.ITEM_SEARCH` | Botón de buscar de una entrada |
| `UI.RECHIST.ITEM_DELETE` | «Eliminar del historial» |

### Reconocer música

| Código | Acción |
|---|---|
| `UI.REC.HISTORY` | «Historial de reconocimiento» |
| `UI.REC.CLOSE` | «Cerrar» (reconocer música) |
| `UI.REC.START` | Botón del micrófono (empezar a escuchar) |
| `UI.REC.STOP` | Botón del micrófono mientras escucha (parar) |
| `UI.REC.CANCEL` | «Cancelar» mientras escucha |
| `UI.REC.PLAY` | «Reproducir en Aura» (resultado) |
| `UI.REC.LIKE` | «Me gusta» (resultado) |
| `UI.REC.ADD_TO_PLAYLIST` | «Añadir a lista de reproducción» (resultado) |
| `UI.REC.AGAIN` | «Volver a escuchar» (resultado) |
| `UI.REC.RESULT_CLOSE` | «Cerrar» (resultado) |
| `UI.REC.PLAY_WIDE` | «Reproducir en Aura» (resultado, ventana ancha) |
| `UI.REC.LIKE_WIDE` | «Me gusta» (resultado, ventana ancha) |
| `UI.REC.ADD_TO_PLAYLIST_WIDE` | «Añadir a lista de reproducción» (resultado, ventana ancha) |
| `UI.REC.AGAIN_WIDE` | «Volver a escuchar» (resultado, ventana ancha) |
| `UI.REC.RESULT_CLOSE_WIDE` | «Cerrar» (resultado, ventana ancha) |
| `UI.REC.TRY_AGAIN` | «Inténtalo de nuevo» (sin resultado) |
| `UI.REC.TRY_AGAIN_ERROR` | «Inténtalo de nuevo» (error) |

### Región de sugerencias

| Código | Acción |
|---|---|
| `UI.REGION.SYSTEM` | «Predeterminado del sistema» (región de sugerencias) |
| `UI.REGION.PICK` | Elegir una región de sugerencias |

### Acerca de

| Código | Acción |
|---|---|
| `UI.ABOUT.BACK` | Atrás (Acerca de) |
| `UI.ABOUT.TERMS` | «Términos y condiciones» |
| `UI.ABOUT.FEEDBACK` | «Reporte de errores y sugerencias» |
| `UI.ABOUT.SHARE_APP` | «Compartir Aura Hi-Res» |

### Ajustes ▸ Cuentas

| Código | Acción |
|---|---|
| `UI.ACCOUNTS.BACK` | Atrás (Cuentas) |
| `UI.ACCOUNTS.YT_LOGOUT` | «Finalizar sesión» de YouTube (o iniciarla) |
| `UI.ACCOUNTS.YT_ACCOUNT` | Fila de la cuenta de YouTube |
| `UI.ACCOUNTS.SPOTIFY_LOGOUT` | «Finalizar sesión» de Spotify (o iniciarla) |
| `UI.ACCOUNTS.SPOTIFY_ACCOUNT` | Fila de la cuenta de Spotify |
| `UI.ACCOUNTS.LASTFM_LOGOUT` | «Finalizar sesión» de Last.fm (o iniciarla) |
| `UI.ACCOUNTS.LASTFM_ACCOUNT` | Fila de la cuenta de Last.fm |
| `UI.ACCOUNTS.LISTENBRAINZ_CONNECT` | «Conectar» ListenBrainz |
| `UI.ACCOUNTS.LISTENBRAINZ_ACCOUNT` | Fila de ListenBrainz |

### Ajustes ▸ IA

| Código | Acción |
|---|---|
| `UI.SETAI.PROVIDER_HELP_OK` | «Aceptar» en «Obtener Llaves API» |
| `UI.SETAI.TRANSLATE_MODE_HELP_OK` | «Aceptar» en la ayuda del modo de traducción |
| `UI.SETAI.RECS_REFRESH` | «Refrescar ahora» la lista recomendada |
| `UI.SETAI.PROVIDER` | «Proveedor» |
| `UI.SETAI.PROVIDER_HELP` | «Obtener Llaves API» |
| `UI.SETAI.BASE_URL` | «URL base» |
| `UI.SETAI.DEEPL_API_KEY` | «Clave API» de DeepL (la clave no se registra) |
| `UI.SETAI.DEEPL_FORMALITY` | «Formalidad» (DeepL) |
| `UI.SETAI.API_KEY` | «Clave API» (la clave no se registra) |
| `UI.SETAI.MODEL` | «Modelo» |
| `UI.SETAI.TRANSLATION_MODE` | «Modo de traducción» |
| `UI.SETAI.TRANSLATION_MODE_HELP` | Ayuda del modo de traducción (ⓘ) |
| `UI.SETAI.TARGET_LANGUAGE` | «Idioma de destino» |
| `UI.SETAI.BACK` | Atrás (Ajustes de IA) |

### Ajustes ▸ Apariencia

| Código | Acción |
|---|---|
| `UI.SETAPP.LYRICS_TEXT_SIZE_RESET` | «Restablecer» |
| `UI.SETAPP.LYRICS_TEXT_SIZE_CANCEL` | «Cancelar» |
| `UI.SETAPP.LYRICS_TEXT_SIZE_CONFIRM` | «Aceptar» |
| `UI.SETAPP.LYRICS_LINE_SPACING_RESET` | «Restablecer» |
| `UI.SETAPP.LYRICS_LINE_SPACING_CANCEL` | «Cancelar» |
| `UI.SETAPP.LYRICS_LINE_SPACING_CONFIRM` | «Aceptar» |
| `UI.SETAPP.RESTART_CANCEL` | «Cancelar» |
| `UI.SETAPP.RESTART` | «Reiniciar» |
| `UI.SETAPP.DENSITY_SCALE_CANCEL` | «Cancelar» |
| `UI.SETAPP.DENSITY_PICK` | Elegir la densidad de pantalla |
| `UI.SETAPP.SLIDER_OPTION_CANCEL` | «Cancelar» |
| `UI.SETAPP.THEME` | «Tema» |
| `UI.SETAPP.MINIPLAYER_BACKGROUND_STYLE` | «Estilo de fondo del minirreproductor» |
| `UI.SETAPP.PLAYER_BACKGROUND_STYLE` | «Estilo de fondo del reproductor» |
| `UI.SETAPP.LIQUID_GLASS` | «Liquid Glass (Beta)» |
| `UI.SETAPP.THUMBNAIL_CORNER_RADIUS` | «Radio de esquina de la miniatura» |
| `UI.SETAPP.PLAYER_BUTTONS_STYLE` | «Colores de los botones del reproductor» |
| `UI.SETAPP.PLAYER_SLIDER_STYLE` | «Estilo de la barra del reproductor» |
| `UI.SETAPP.SWIPE_SENSITIVITY` | «Sensibilidad al deslizar el mini reproductor» |
| `UI.SETAPP.SENSITIVITY_RESET` | «Restablecer» en «Sensibilidad al deslizar el mini reproductor» |
| `UI.SETAPP.SENSITIVITY_CANCEL` | «Cancelar» en «Sensibilidad al deslizar el mini reproductor» |
| `UI.SETAPP.SENSITIVITY_CONFIRM` | «Aceptar» en «Sensibilidad al deslizar el mini reproductor» |
| `UI.SETAPP.LYRICS_TEXT_POSITION` | «Posición de la letra de la canción» |
| `UI.SETAPP.LYRICS_ANIMATION_STYLE` | «Estilo de animación de la letra» |
| `UI.SETAPP.LYRICS_TEXT_SIZE` | «Tamaño del texto de la letra» |
| `UI.SETAPP.LYRICS_LINE_SPACING` | «Interlineado de la letra» |
| `UI.SETAPP.DEFAULT_OPEN_TAB` | «Pestaña abierta predeterminada» |
| `UI.SETAPP.DEFAULT_LIB_CHIPS` | «Cambiar chip de biblioteca predeterminado» |
| `UI.SETAPP.GRID_CELL_SIZE` | «Tamaño de la celda de la cuadrícula» |
| `UI.SETAPP.DISPLAY_DENSITY` | «Densidad de pantalla» |
| `UI.SETAPP.BACK` | Atrás (Apariencia) |

### Ajustes ▸ Copia de seguridad

| Código | Acción |
|---|---|
| `UI.BACKUP.CLOUD_RESTORE_CONFIRM` | «Restaurar» desde la nube (confirmación) |
| `UI.BACKUP.CLOUD_RESTORE_CANCEL` | «Cancelar» la restauración desde la nube |
| `UI.BACKUP.BACK` | Atrás (Copia de seguridad) |
| `UI.BACKUP.LOCAL_BACKUP` | «Copia de seguridad local» |
| `UI.BACKUP.SELECTIVE_EXPORT` | «Migración selectiva (Aura)» |
| `UI.BACKUP.CLOUD_BACKUP` | «Guardar copia en la nube de Aura» |
| `UI.BACKUP.CLOUD_RESTORE` | «Restaurar la última copia de la nube» |
| `UI.BACKUP.IMPORT` | «Importar» |
| `UI.BACKUP.IMPORT_SPOTIFY` | «Importar desde Spotify» |
| `UI.BACKUP.MIGRATE` | «Migrar» desde otros servicios |
| `UI.BACKUP.YTM_SYNC` | «Sincronizar desde tu cuenta de Google» |
| `UI.BACKUP.LOCAL_RESTORE` | «Importar desde archivo local» |
| `UI.BACKUP.IMPORT_M3U` | «Importar lista 'm3u'» |
| `UI.BACKUP.IMPORT_CSV` | «Importar lista 'csv'» |
| `UI.BACKUP.IMPORT_JR` | «Importar lista de Aura Hi-Res Player» |
| `UI.BACKUP.SELECTIVE_IMPORT` | «Importar migración selectiva (Aura)» |
| `UI.BACKUP.SELECTIVE_ITEM` | Marcar / desmarcar algo en la migración selectiva |
| `UI.BACKUP.SELECTIVE_EXPORT_CONFIRM` | «Exportar» (migración selectiva) |
| `UI.BACKUP.SELECTIVE_EXPORT_CANCEL` | «Cancelar» (migración selectiva) |

### Ajustes ▸ Contenido

| Código | Acción |
|---|---|
| `UI.SETCONT.PROXY_SAVE` | «Guardar» la configuración del proxy (la dirección no se registra) |
| `UI.SETCONT.PROXY_CANCEL` | «Cancelar» la configuración del proxy |
| `UI.SETCONT.PROVIDERS_CLOSE` | «Cerrar» la prioridad de proveedores de letras |
| `UI.SETCONT.TOP_LENGTH_SAVE` | «Guardar» en «Longitud de la lista Mi Top» |
| `UI.SETCONT.CONTENT_LANGUAGE` | «Idioma de contenido predeterminado» |
| `UI.SETCONT.CONTENT_COUNTRY` | «País de contenido predeterminado» |
| `UI.SETCONT.SUGGESTION_REGION` | «Región de sugerencias» |
| `UI.SETCONT.YOUTUBE_OPTIONS` | «Opciones de YouTube» (despliega) |
| `UI.SETCONT.APP_LANGUAGE_SYSTEM` | «Idioma de la aplicación» (ajustes del sistema) |
| `UI.SETCONT.APP_LANGUAGE` | «Idioma de la aplicación» (lista) |
| `UI.SETCONT.NETWORK_IP_VERSION` | «Versión IP de la red» |
| `UI.SETCONT.CONFIG_PROXY` | «Configurar proxy» |
| `UI.SETCONT.LYRICS_PROVIDER_PRIORITY` | «Prioridad de proveedores de letras» |
| `UI.SETCONT.BATTERY_OPTIMIZATION` | «Evitar que el sistema cierre la app» |
| `UI.SETCONT.APP_DETAILS` | «Ajustes del sistema de la app» |
| `UI.SETCONT.TOP_LENGTH` | «Longitud de la lista Mi Top» |
| `UI.SETCONT.QUICK_PICKS` | «Establecer selecciones rápidas» |
| `UI.SETCONT.OPEN_LINKS` | «Abrir enlaces» (ajustes del sistema) |
| `UI.SETCONT.PLAYBACK_LOGS` | «Registros de reproducción» |
| `UI.SETCONT.SERVICE_UPTIME` | «Estado del servicio» |
| `UI.SETCONT.BACK` | Atrás (Contenido) |

### Reporte de errores y sugerencias

| Código | Acción |
|---|---|
| `UI.FEEDBACK.BACK` | Atrás (Reporte de errores) |
| `UI.FEEDBACK.KIND_BUG` | «Reporte de error» |
| `UI.FEEDBACK.KIND_SUGGESTION` | «Sugerencia» |
| `UI.FEEDBACK.ATTACH_SCREENSHOT` | «Adjuntar captura» |
| `UI.FEEDBACK.SEND` | «Abrir app de correo» (el mensaje no se registra) |

### Ajustes ▸ Liquid Glass

| Código | Acción |
|---|---|
| `UI.GLASS.VIBRANCY` | «Viveza» |
| `UI.GLASS.BLUR_RADIUS` | «Radio de desenfoque» |
| `UI.GLASS.LENS_HEIGHT` | «Altura de refracción de la lente» |
| `UI.GLASS.LENS_AMOUNT` | «Cantidad de refracción de la lente» |
| `UI.GLASS.SURFACE_TINT` | «Tinte de la superficie» |
| `UI.GLASS.SURFACE_OPACITY` | «Opacidad de la superficie» |
| `UI.GLASS.TEXT_COLOR` | «Color del texto sobre cristal» |
| `UI.GLASS.VIBRANCY_RESET` | «Restablecer» en «Viveza» |
| `UI.GLASS.VIBRANCY_CANCEL` | «Cancelar» en «Viveza» |
| `UI.GLASS.VIBRANCY_CONFIRM` | «Aceptar» en «Viveza» |
| `UI.GLASS.BLUR_RADIUS_RESET` | «Restablecer» en «Radio de desenfoque» |
| `UI.GLASS.BLUR_RADIUS_CANCEL` | «Cancelar» en «Radio de desenfoque» |
| `UI.GLASS.BLUR_RADIUS_CONFIRM` | «Aceptar» en «Radio de desenfoque» |
| `UI.GLASS.LENS_HEIGHT_RESET` | «Restablecer» en «Altura de refracción de la lente» |
| `UI.GLASS.LENS_HEIGHT_CANCEL` | «Cancelar» en «Altura de refracción de la lente» |
| `UI.GLASS.LENS_HEIGHT_CONFIRM` | «Aceptar» en «Altura de refracción de la lente» |
| `UI.GLASS.LENS_AMOUNT_RESET` | «Restablecer» en «Cantidad de refracción de la lente» |
| `UI.GLASS.LENS_AMOUNT_CANCEL` | «Cancelar» en «Cantidad de refracción de la lente» |
| `UI.GLASS.LENS_AMOUNT_CONFIRM` | «Aceptar» en «Cantidad de refracción de la lente» |
| `UI.GLASS.SURFACE_OPACITY_RESET` | «Restablecer» en «Opacidad de la superficie» |
| `UI.GLASS.SURFACE_OPACITY_CANCEL` | «Cancelar» en «Opacidad de la superficie» |
| `UI.GLASS.SURFACE_OPACITY_CONFIRM` | «Aceptar» en «Opacidad de la superficie» |
| `UI.GLASS.BACK` | Atrás (Liquid Glass) |

### Ajustes ▸ Last.fm

| Código | Acción |
|---|---|
| `UI.LASTFM.LOGIN_CONFIRM` | Iniciar sesión en Last.fm (usuario y contraseña no se registran) |
| `UI.LASTFM.LOGIN_CANCEL` | «Cancelar» el inicio de sesión de Last.fm |
| `UI.LASTFM.LOGOUT` | «Finalizar sesión» de Last.fm |
| `UI.LASTFM.LOGIN` | «Acceder» a Last.fm (abre el diálogo) |
| `UI.LASTFM.MIN_DURATION_RESET` | «Restablecer» en «Scrobble a canciones más largas que» |
| `UI.LASTFM.MIN_DURATION_CANCEL` | «Cancelar» en «Scrobble a canciones más largas que» |
| `UI.LASTFM.MIN_DURATION_CONFIRM` | «Aceptar» en «Scrobble a canciones más largas que» |
| `UI.LASTFM.DELAY_PERCENT_RESET` | «Restablecer» en «Porcentaje de retraso de Scrobble» |
| `UI.LASTFM.DELAY_PERCENT_CANCEL` | «Cancelar» en «Porcentaje de retraso de Scrobble» |
| `UI.LASTFM.DELAY_PERCENT_CONFIRM` | «Aceptar» en «Porcentaje de retraso de Scrobble» |
| `UI.LASTFM.DELAY_SECONDS_RESET` | «Restablecer» en «Minutos de retraso de Scrobble» |
| `UI.LASTFM.DELAY_SECONDS_CANCEL` | «Cancelar» en «Minutos de retraso de Scrobble» |
| `UI.LASTFM.DELAY_SECONDS_CONFIRM` | «Aceptar» en «Minutos de retraso de Scrobble» |
| `UI.LASTFM.MIN_DURATION` | «Scrobble a canciones más largas que» |
| `UI.LASTFM.DELAY_PERCENT` | «Porcentaje de retraso de Scrobble» |
| `UI.LASTFM.DELAY_SECONDS` | «Minutos de retraso de Scrobble» |
| `UI.LASTFM.LISTENBRAINZ_TOKEN` | «Configurar token de ListenBrainz» (el token no se registra) |
| `UI.LASTFM.BACK` | Atrás (Last.fm) |

### Ajustes ▸ Registros

| Código | Acción |
|---|---|
| `UI.LOGS.BACK` | Atrás (Registros) |
| `UI.LOGS.COPY` | «Copiar» el registro |
| `UI.LOGS.SHARE` | «Compartir» los registros |
| `UI.LOGS.TAB_APP` | Pestaña «Registro de la app» |
| `UI.LOGS.TAB_CRASH` | Pestaña «Último fallo» |
| `UI.LOGS.TAB_EXITS` | Pestaña «Cierres del sistema» |
| `UI.LOGS.DELETE_ALL` | «Borrar todos los registros» |
| `UI.LOGS.DELETE_CONFIRM` | «Eliminar» (borrar todos los registros) |
| `UI.LOGS.DELETE_CANCEL` | «Cancelar» (borrar todos los registros) |

### Ajustes ▸ Rendimiento

| Código | Acción |
|---|---|
| `UI.SETPERF.BACK` | Atrás (Rendimiento) |

### Ajustes ▸ Reproductor y audio

| Código | Acción |
|---|---|
| `UI.SETPLR.CROSSFADE_BETA_CANCEL` | «Cancelar» en el aviso beta de la transición suave |
| `UI.SETPLR.STREAM_QUALITY_WIFI` | «Calidad con Wi-Fi» |
| `UI.SETPLR.STREAM_QUALITY_MOBILE` | «Calidad con datos móviles» |
| `UI.SETPLR.CROSSFADE` | «Transición suave» (apagada: abre el aviso beta; encendida: la apaga y sale UI.SET.TOGGLE) |
| `UI.SETPLR.CROSSFADE_CURVE` | «Estilo de transición» |
| `UI.SETPLR.YOUTUBE_DECRYPTION_SETTINGS` | «Descifrado de reproducción» |
| `UI.SETPLR.BACK` | Atrás (Reproductor y audio) |

### Ajustes ▸ Privacidad

| Código | Acción |
|---|---|
| `UI.SETPRIV.CLEAR_LISTEN_CANCEL` | «Cancelar» (borrar historial de reproducciones) |
| `UI.SETPRIV.CLEAR_LISTEN_CONFIRM` | «Aceptar» (borrar historial de reproducciones) |
| `UI.SETPRIV.CLEAR_SEARCH_CANCEL` | «Cancelar» (borrar historial de búsquedas) |
| `UI.SETPRIV.CLEAR_SEARCH_CONFIRM` | «Aceptar» (borrar historial de búsquedas) |
| `UI.SETPRIV.CLEAR_LISTEN_HISTORY` | «Borrar historial de reproducciones» |
| `UI.SETPRIV.CLEAR_SEARCH_HISTORY` | «Borrar historial de búsquedas» |
| `UI.SETPRIV.BACK` | Atrás (Privacidad) |

### Ajustes ▸ Romanización

| Código | Acción |
|---|---|
| `UI.ROMAN.BACK` | Atrás (Romanización) |

### Ajustes ▸ Sonido

| Código | Acción |
|---|---|
| `UI.SETSOUND.EQUALIZER` | «Ecualizador» |
| `UI.SETSOUND.STEREO_WIDTH_RESET` | Restablecer la anchura estéreo |

### Ajustes ▸ Almacenamiento

| Código | Acción |
|---|---|
| `UI.STORAGE.CACHE_WARNING_CONTINUE` | «Continuar» en «¡Espera!» (caché) |
| `UI.STORAGE.CACHE_WARNING_CANCEL` | «Cancelar» en «¡Espera!» (caché) |
| `UI.STORAGE.CLEAR_ALL_DOWNLOADS` | «Borrar todas las descargas» |
| `UI.STORAGE.EXPORT_DIRECTORY` | «Carpeta de exportación» |
| `UI.STORAGE.CLEAR_SONG_CACHE` | «Borrar caché de canciones» |
| `UI.STORAGE.CLEAR_IMAGE_CACHE` | «Borrar caché de imágenes» |
| `UI.STORAGE.BACK` | Atrás (Almacenamiento) |

### Ajustes ▸ Revisar suscripciones

| Código | Acción |
|---|---|
| `UI.SUBCLEAN.BACK` | Atrás (Revisar suscripciones) |
| `UI.SUBCLEAN.LOGIN` | Iniciar sesión (Revisar suscripciones) |
| `UI.SUBCLEAN.RETRY` | «Reintentar» (Revisar suscripciones) |
| `UI.SUBCLEAN.STOP` | «Detener» la limpieza |
| `UI.SUBCLEAN.RETRY_FAILED` | «Reintentar las fallidas» |
| `UI.SUBCLEAN.RELOAD` | «Volver a cargar la lista» |
| `UI.SUBCLEAN.SELECT_SUGGESTED` | «Seleccionar las sugeridas» |
| `UI.SUBCLEAN.CLEAR_SELECTION` | «Quitar la selección» |
| `UI.SUBCLEAN.REMOVE` | «Quitar» las seleccionadas (pide confirmar) |
| `UI.SUBCLEAN.CANDIDATE` | Marcar / desmarcar un canal |
| `UI.SUBCLEAN.REMOVE_CONFIRM` | «Quitar» (confirmación) |
| `UI.SUBCLEAN.REMOVE_CANCEL` | «Cancelar» (confirmación) |

### Ajustes ▸ Tema y colores

| Código | Acción |
|---|---|
| `UI.THEME.BACK` | Atrás (Tema y colores) |
| `UI.THEME.PALETTE` | Elegir una paleta |
| `UI.THEME.RESET_CANCEL` | «Cancelar» en «Restablecer la configuración inicial» |
| `UI.THEME.RESET_CONFIRM` | «Restablecer» la configuración inicial del tema |
| `UI.THEME.ROLE_BACKGROUND` | «Fondo» |
| `UI.THEME.ROLE_SURFACE` | «Superficie / paneles» |
| `UI.THEME.ROLE_ON_BACKGROUND` | «Texto principal» |
| `UI.THEME.ROLE_ON_SURFACE_VARIANT` | «Texto secundario» |
| `UI.THEME.ROLE_OUTLINE` | «Bordes / líneas» |
| `UI.THEME.ROLE_ON_PRIMARY` | «Texto sobre acento» |
| `UI.THEME.AUTO_RESET` | «Restablecer» (colores de los roles) |
| `UI.THEME.RESET` | «Restablecer la configuración inicial» (abre la confirmación) |
| `UI.THEME.ACCENT_INTENSITY` | Intensidad del acento (Suave / Normal / Intensa) |
| `UI.THEME.ACCENT_HEX_APPLY` | «Aplicar color» (el código escrito no se registra) |

### Ajustes ▸ Actualizaciones

| Código | Acción |
|---|---|
| `UI.SETUPD.SYSTEM_UPDATE` | «Actualización del sistema» |
| `UI.SETUPD.CHANGELOG` | «Versión …» (abre las novedades) |
| `UI.SETUPD.AUTO_CHECK` | «Búsqueda automática de actualizaciones» (sí/no) |
| `UI.SETUPD.NOTIFICATIONS` | «Habilitar notificaciones de actualización» (sí/no) |
| `UI.SETUPD.BETA` | «Recibir versiones beta» (sí/no) |
| `UI.SETUPD.BETA_INFO` | Información de las versiones beta (ⓘ) |
| `UI.SETUPD.CLEAR_DOWNLOADS` | «Borrar actualizaciones descargadas» |
| `UI.SETUPD.BACK` | Atrás (Actualizaciones) |

### Ajustes ▸ Descifrado de reproducción

| Código | Acción |
|---|---|
| `UI.YTDECRYPT.FORCE_UPDATE` | «Forzar actualización del descifrado» |
| `UI.YTDECRYPT.BACK` | Atrás (Descifrado de reproducción) |

### Ajustes ▸ Servicios Aura

| Código | Acción |
|---|---|
| `UI.AURASRV.ADDRESS` | «Dirección del servidor» (la dirección no se registra) |
| `UI.AURASRV.KEY` | «Clave de verificación» (la clave no se registra) |
| `UI.AURASRV.CHECK_NOW` | «Comprobar ahora» |
| `UI.AURASRV.DIAG_SEND` | «Enviar registro» |
| `UI.AURASRV.FIELD_CANCEL` | «Cancelar» al editar la dirección o la clave |
| `UI.AURASRV.FIELD_SAVE` | «Aceptar» al editar la dirección o la clave (no se registran) |

### Ajustes ▸ Escuchar juntos

| Código | Acción |
|---|---|
| `UI.LTSET.USERNAME_RESET` | «Restablecer» el nombre de usuario |
| `UI.LTSET.USERNAME_SAVE` | «Aceptar» el nombre de usuario (no se registra) |
| `UI.LTSET.USERNAME_CLEAR` | Borrar el nombre escrito |
| `UI.LTSET.BACK` | Atrás (Escuchar juntos) |
| `UI.LTSET.BLOCKED_CLOSE` | «Aceptar» en «Usuarios bloqueados» |
| `UI.LTSET.UNBLOCK` | «Desbloquear» a alguien |

### Elegir el formato de exportación

| Código | Acción |
|---|---|
| `UI.EXPORT.CANCEL` | «Cancelar» (elegir formato) |
| `UI.EXPORT.OFFLINE` | «Descargar sin conexión» (elegir formato) |
| `UI.EXPORT.MP3` | «Como MP3» (elegir formato) |
| `UI.EXPORT.VIDEO` | «Como vídeo» (elegir formato) |

### Detalles de la canción

| Código | Acción |
|---|---|
| `UI.MEDIAINFO.DONE` | «Listo» (detalles de la canción) |
| `UI.MEDIAINFO.COPY` | Copiar un dato de los detalles de la canción |

### Desfase de la letra

| Código | Acción |
|---|---|
| `UI.LYROFFSET.RESET` | «Restablecer» el desfase de la letra |
| `UI.LYROFFSET.DECREASE` | «Disminuir» el desfase de la letra |
| `UI.LYROFFSET.INCREASE` | «Aumentar» el desfase de la letra |

### Pantalla de actualización

| Código | Acción |
|---|---|
| `UI.UPDATE.CANCEL` | «Cancelar» (pantalla de actualización) |
| `UI.UPDATE.CHECK` | «Buscar actualización» |
| `UI.UPDATE.LATER` | «Más tarde» |
| `UI.UPDATE.INSTALL` | «Instalar» |
| `UI.UPDATE.DIRECT_DOWNLOAD` | «Descarga directa» (abre GitHub) |
| `UI.UPDATE.LINK` | Tocar un enlace de las notas de versión |

### Pantalla de novedades de la versión

| Código | Acción |
|---|---|
| `UI.CHANGELOG.BACK` | Atrás (Novedades de la versión) |
| `UI.CHANGELOG.LINK` | Tocar un enlace de las novedades de la versión |

### Hoja de dispositivos de audio

| Código | Acción |
|---|---|
| `UI.AUDIODEV.RETRY` | «Reintentar» (dispositivos de audio) |
| `UI.AUDIODEV.OUTPUT_MENU` | Abrir / cerrar la lista de salidas |
| `UI.AUDIODEV.OUTPUT_PICK` | Elegir una salida de audio |
| `UI.AUDIODEV.DONE` | «Listo» (dispositivos de audio) |
| `UI.AUDIODEV.OUTPUT_TOGGLE` | «Dispositivos de audio» (despliega la lista) |
| `UI.AUDIODEV.DEVICE_PICK` | Elegir un dispositivo de audio |
| `UI.AUDIODEV.PICKER_DONE` | «Listo» (lista de dispositivos) |

### Términos y política de privacidad

| Código | Acción |
|---|---|
| `UI.LEGAL.EXIT` | «Salir» (Términos) |
| `UI.LEGAL.ACCEPT` | «Aceptar y continuar» (Términos) |
| `UI.LEGAL.BACK` | Atrás (Términos / Política de privacidad) |

### Barra superior y enlaces de invitación

| Código | Acción |
|---|---|
| `UI.MAIN.TOPBAR_TOGETHER` | Botón «Juntos» de la barra superior |
| `UI.MAIN.TOPBAR_HISTORY` | Botón «Historial» de la barra superior |
| `UI.MAIN.TOPBAR_ACCOUNT` | Botón «Cuenta» de la barra superior |
| `UI.MAIN.INVITE_JOIN` | «Unirme» en «¿Unirte a la sala …?» (enlace de invitación) |
| `UI.MAIN.INVITE_CANCEL` | «Cancelar» en «¿Unirte a la sala …?» |
| `UI.MAIN.PAIR_JOIN` | «Unirme» en «¿Emparejar este aparato con otra sala de sincronización?» (enlace o QR de emparejamiento, fila 836) |
| `UI.MAIN.PAIR_CANCEL` | «Cancelar» en «¿Emparejar este aparato con otra sala de sincronización?» |

### Cast (versión con Google Play)

| Código | Acción |
|---|---|
| `UI.CAST.DISCONNECT` | Desconectar el Cast (fila «Conectado») |
| `UI.CAST.ROUTE` | Elegir un dispositivo de Cast |
| `UI.CAST.BUTTON` | Botón de Cast del reproductor (abre la lista de dispositivos) |

### Menú de álbum (más)

| Código | Acción |
|---|---|
| `UI.ALBUMMENU.PLAYLIST_ERROR_CLOSE` | Cerrar el aviso «Ya está en la lista de reproducción» |
| `UI.ALBUMMENU.ARTIST_PICK` | Elegir un artista (varios artistas) |
| `UI.ALBUMMENU.LIKE` | Corazón de la cabecera del menú (guardar el álbum) |

### Menú de lista (más)

| Código | Acción |
|---|---|
| `UI.PLMENU.MP3_CANCEL` | «Cancelar» en «Exportar a MP3» |
| `UI.PLMENU.MP3_PICK` | «Seleccionar» canciones para exportar a MP3 |
| `UI.PLMENU.MP3_ALL` | «Todas» (exportar a MP3) |
| `UI.PLMENU.MP3_SELECT_ALL` | Marcar / desmarcar todas (exportar a MP3) |
| `UI.PLMENU.MP3_SONG` | Marcar / desmarcar una canción (exportar a MP3) |
| `UI.PLMENU.MP3_SELECT_CANCEL` | «Cancelar» al elegir canciones para MP3 |
| `UI.PLMENU.MP3_SELECT_CONTINUE` | «Continuar» con las canciones elegidas para MP3 |
| `UI.PLMENU.REMOVE_DOWNLOAD_CANCEL` | «Cancelar» (quitar descargas de la lista) |
| `UI.PLMENU.REMOVE_DOWNLOAD_CONFIRM` | «Aceptar» (quitar descargas de la lista) |
| `UI.PLMENU.DELETE_ALSO_YOUTUBE` | «Eliminar también de YouTube» |
| `UI.PLMENU.DELETE_LOCAL_ONLY` | «Solo eliminar de la app» |
| `UI.PLMENU.DELETE_CANCEL` | «Cancelar» al eliminar la lista |
| `UI.PLMENU.DELETE_CONFIRM` | «Aceptar» al eliminar una lista solo local |
| `UI.PLMENU.LIKE` | Corazón de la cabecera del menú (guardar la lista) |

### Menú de canción (más)

| Código | Acción |
|---|---|
| `UI.SONGMENU.PLAYLIST_ERROR_CLOSE` | Cerrar el aviso «Ya está en la lista de reproducción» |
| `UI.SONGMENU.ARTIST_PICK` | Elegir un artista (varios artistas) |
| `UI.SONGMENU.LIKE` | Corazón de la cabecera del menú (me gusta) |

### Menú de álbum de YouTube (más)

| Código | Acción |
|---|---|
| `UI.YTALBUMMENU.PLAYLIST_ERROR_CLOSE` | Cerrar el aviso «Ya está en la lista de reproducción» |
| `UI.YTALBUMMENU.ARTIST_PICK` | Elegir un artista (varios artistas) |
| `UI.YTALBUMMENU.ARTIST_PICK_DIALOG` | Elegir un artista en el diálogo de artistas |
| `UI.YTALBUMMENU.LIKE` | Corazón de la cabecera del menú (guardar el álbum) |

### Menú de lista de YouTube (más)

| Código | Acción |
|---|---|
| `UI.YTPLMENU.LIKE` | Corazón de la cabecera del menú (guardar la lista) |
| `UI.YTPLMENU.REMOVE_DOWNLOAD_CANCEL` | «Cancelar» (quitar descargas de la lista) |
| `UI.YTPLMENU.REMOVE_DOWNLOAD_CONFIRM` | «Aceptar» (quitar descargas de la lista) |
| `UI.YTPLMENU.MP3_CANCEL` | «Cancelar» en «Exportar a MP3» |
| `UI.YTPLMENU.MP3_PICK` | «Seleccionar» canciones para exportar a MP3 |
| `UI.YTPLMENU.MP3_ALL` | «Todas» (exportar a MP3) |
| `UI.YTPLMENU.MP3_SELECT_ALL` | Marcar / desmarcar todas (exportar a MP3) |
| `UI.YTPLMENU.MP3_SONG` | Marcar / desmarcar una canción (exportar a MP3) |
| `UI.YTPLMENU.MP3_SELECT_CANCEL` | «Cancelar» al elegir canciones para MP3 |
| `UI.YTPLMENU.MP3_SELECT_CONTINUE` | «Continuar» con las canciones elegidas para MP3 |
| `UI.YTPLMENU.PLAYLIST_ERROR_CLOSE` | Cerrar el aviso «Ya está en la lista de reproducción» |

### Menú de canción de YouTube (más)

| Código | Acción |
|---|---|
| `UI.YTSONGMENU.ARTIST_PICK` | Elegir un artista (varios artistas) |
| `UI.YTSONGMENU.ARTIST_PICK_DIALOG` | Elegir un artista en el diálogo de artistas |
| `UI.YTSONGMENU.LIKE` | Corazón de la cabecera del menú (me gusta) |

### Página de álbum (más)

| Código | Acción |
|---|---|
| `UI.ALBUM.SONG` | Tocar una canción del álbum (en selección la marca) |
| `UI.ALBUM.SONG_SELECT` | Pulsación larga en una canción del álbum: seleccionar |
| `UI.ALBUM.OTHER_VERSION` | Abrir otra versión del álbum |
| `UI.ALBUM.OTHER_VERSION_MENU` | Pulsación larga en otra versión del álbum: menú |
| `UI.ALBUM.SELECTION_MENU` | «Más opciones» de la selección (álbum) |
| `UI.ALBUM.SHARE` | «Compartir» el álbum |
| `UI.ALBUM.MENU` | «Más opciones» del álbum |
| `UI.ALBUM.ARTIST` | Tocar el artista del álbum |
| `UI.ALBUM.BACK` | Atrás (álbum) |
| `UI.ALBUM.RELATED` | Tocar un elemento relacionado (álbum) |
| `UI.ALBUM.RELATED_MENU` | Pulsación larga en un elemento relacionado (álbum): menú |
| `UI.ALBUM.DESCRIPTION` | Desplegar / plegar la descripción del álbum |
| `UI.ALBUM.RETRY` | «Reintentar» (álbum) |

### Página de artista

| Código | Acción |
|---|---|
| `UI.ART.VIDEO` | Tocar el vídeo del artista (reproduce) |
| `UI.ART.RADIO` | «Radio» del artista |
| `UI.ART.SHUFFLE` | «Aleatorio» del artista |
| `UI.ART.SONGS` | Cabecera «Canciones» (ver todas) |
| `UI.ART.ALBUMS` | Cabecera «Álbumes» (ver todos) |
| `UI.ART.ALBUM` | Abrir un álbum del artista |
| `UI.ART.ALBUM_MENU` | Pulsación larga en un álbum del artista: menú |
| `UI.ART.SECTION_MORE` | Cabecera de una sección del artista (ver todos) |
| `UI.ART.SECTION_ITEM` | Tocar un elemento de una sección del artista |
| `UI.ART.SECTION_ITEM_MENU` | Pulsación larga en un elemento de una sección: menú |
| `UI.ART.SECTION_GRID_ITEM` | Tocar un elemento de una sección (cuadrícula) |
| `UI.ART.SECTION_GRID_ITEM_MENU` | Pulsación larga en un elemento de una sección (cuadrícula): menú |
| `UI.ART.SHARE` | «Compartir» el artista |
| `UI.ART.LIBRARY_SONG` | Tocar una canción tuya del artista |
| `UI.ART.LIBRARY_SONG_MENU` | Pulsación larga en una canción tuya del artista: menú |
| `UI.ART.TOP_SONG` | Tocar una canción destacada del artista |
| `UI.ART.TOP_SONG_MENU` | Pulsación larga en una canción destacada: menú |

### Biblioteca (más)

| Código | Acción |
|---|---|
| `UI.LIB.PIN_REMOVE` | «Quitar de fijados» |
| `UI.LIB.PIN` | Abrir un fijado |
| `UI.LIB.PIN_MENU` | Pulsación larga en un fijado: menú |
| `UI.LIB.SONG_MENU` | Pulsación larga en una canción (lista): menú |
| `UI.LIB.SONG_GRID_MENU` | Pulsación larga en una canción (cuadrícula): menú |
| `UI.LIB.ALBUM_MENU` | Pulsación larga en un álbum: menú |
| `UI.LIB.ARTIST_MENU` | Pulsación larga en un artista: menú |
| `UI.LIB.PLAYLIST_MENU` | Pulsación larga en una lista: menú |
| `UI.LIB.FILTER_CHIP` | Chip de filtro de una pestaña de Biblioteca |

### Página de una lista propia (más)

| Código | Acción |
|---|---|
| `UI.LPL.REMOVE_DOWNLOAD_CANCEL` | «Cancelar» (quitar descargas de la lista) |
| `UI.LPL.REMOVE_DOWNLOAD_CONFIRM` | «Aceptar» (quitar descargas de la lista) |
| `UI.LPL.DELETE_ALSO_YOUTUBE` | «Eliminar también de YouTube» |
| `UI.LPL.DELETE_LOCAL_ONLY` | «Solo eliminar de la app» |
| `UI.LPL.DELETE_CANCEL` | «Cancelar» al eliminar la lista |
| `UI.LPL.DELETE_CONFIRM` | «Aceptar» al eliminar una lista solo local |
| `UI.LPL.SONG` | Tocar una canción de la lista (en selección la marca) |
| `UI.LPL.SONG_SELECT` | Pulsación larga en una canción de la lista: seleccionar |
| `UI.LPL.ADD_MUSIC` | «Agregar música» (lista propia) |
| `UI.LPL.SELECTION_MENU` | «Acciones de la selección» (lista propia) |
| `UI.LPL.SEARCH` | «Buscar» en la lista |
| `UI.LPL.MENU` | «Más opciones de la lista» |

### Página de una lista de YouTube (más)

| Código | Acción |
|---|---|
| `UI.OPL.RETRY` | «Reintentar» (lista de YouTube) |
| `UI.OPL.RELATED` | Tocar un elemento relacionado (lista de YouTube) |
| `UI.OPL.RELATED_MENU` | Pulsación larga en un elemento relacionado: menú |
| `UI.OPL.SELECTION_MENU` | «Acciones de la selección» (lista de YouTube) |
| `UI.OPL.SHARE` | «Compartir» la lista de YouTube |
| `UI.OPL.SEARCH` | «Buscar» en la lista de YouTube |
| `UI.OPL.MENU` | «Más opciones» de la lista de YouTube |

### Reproductor (más)

| Código | Acción |
|---|---|
| `UI.PLR.SUBSCRIBE_CANCEL` | «Cancelar» en «¿Suscribirte a …?» |
| `UI.PLR.OFFLINE_NOTICE_CLOSE` | «Cerrar» en «Sonando sin conexión» |

### Menú del reproductor (más)

| Código | Acción |
|---|---|
| `UI.PMENU.ARTIST_PICK` | Elegir un artista (varios artistas, menú del reproductor) |

### Búsqueda (más)

| Código | Acción |
|---|---|
| `UI.SEARCH.PANE_BACK` | Atrás (panel de detalle de Buscar) |
| `UI.SEARCH.PANE_TRACK` | Tocar una canción del panel de detalle |
| `UI.SEARCH.PANE_PLAY` | Reproducir / pausa en el panel de detalle |
| `UI.SEARCH.PANE_SHELF_ITEM` | Tocar un elemento de un estante del panel de detalle |
| `UI.SEARCH.RESULT_WIDE` | Tocar un resultado (pantalla ancha, abre el detalle) |
| `UI.SEARCH.RESULT_WIDE_LONG` | Pulsación larga en un resultado (pantalla ancha) |
| `UI.SEARCH.RESULT_MENU` | Pulsación larga en un resultado: menú |
| `UI.SEARCH.RESULT_GRID_WIDE` | Tocar un resultado de la cuadrícula (pantalla ancha) |
| `UI.SEARCH.RESULT_GRID_WIDE_LONG` | Pulsación larga en un resultado de la cuadrícula (pantalla ancha) |
| `UI.SEARCH.PODCAST` | Abrir un podcast (resultados) |
| `UI.SEARCH.PODCAST_MENU` | Pulsación larga en un podcast (resultados) |
| `UI.SEARCH.FILTER` | Chip de filtro de los resultados |
| `UI.SEARCH.SOURCE_TOGGLE` | Cambiar entre buscar en línea y en la biblioteca |
| `UI.SEARCH.MIC_SETTINGS` | «Abrir ajustes» (permiso del micrófono) |
| `UI.SEARCH.MIC_CANCEL` | «Cancelar» (permiso del micrófono) |
| `UI.SEARCH.VOICE_CANCEL` | «Cancelar» la búsqueda por voz |
| `UI.SEARCH.VOICE_DONE` | «Aceptar» la búsqueda por voz (lo dictado no se registra) |
| `UI.SEARCH.SUGGESTION_ITEM_LONG` | Pulsación larga en un resultado de las sugerencias |
| `UI.SEARCH.SUGGESTION_ITEM_MENU` | Pulsación larga en un resultado de las sugerencias: menú |
| `UI.SEARCH.LOCAL_ITEM` | Tocar un resultado de la biblioteca (pantalla ancha) |
| `UI.SEARCH.LOCAL_ITEM_LONG` | Pulsación larga en un resultado de la biblioteca (pantalla ancha) |
| `UI.SEARCH.LOCAL_SONG` | Tocar una canción de la biblioteca (búsqueda) |
| `UI.SEARCH.LOCAL_SONG_MENU` | Pulsación larga en una canción de la biblioteca (búsqueda): menú |
| `UI.SEARCH.LOCAL_ALBUM` | Abrir un álbum de la biblioteca (búsqueda) |
| `UI.SEARCH.LOCAL_ARTIST` | Abrir un artista de la biblioteca (búsqueda) |
| `UI.SEARCH.LOCAL_PLAYLIST` | Abrir una lista de la biblioteca (búsqueda) |
| `UI.SEARCH.HISTORY_CLEAR_CANCEL` | «Cancelar» al borrar el historial de búsqueda |

### Barra de navegación y botones de la cabecera

| Código | Acción |
|---|---|
| `UI.NAV.TOPBAR_RECOGNITION` | Botón «Reconocer música» de la cabecera |
| `UI.NAV.TOPBAR_TOGETHER` | Botón «Juntos» de la cabecera |
| `UI.NAV.TOPBAR_HISTORY` | Botón «Historial» de la cabecera |
| `UI.NAV.TOPBAR_OFFLINE` | Botón «Modo sin conexión» de la cabecera (el valor nuevo sale como UI.SET.TOGGLE) |
| `UI.NAV.TOPBAR_ACCOUNT` | Avatar de la cuenta en la cabecera |
| `UI.NAV.TOPBAR_ACCOUNT_ICON` | Icono de la cuenta en la cabecera (sin foto) |
| `UI.NAV.HOME` | Pestaña «Inicio» de la barra de navegación |
| `UI.NAV.NOVEDADES` | Pestaña «Novedades» de la barra de navegación |
| `UI.NAV.LIBRARY` | Pestaña «Biblioteca» de la barra de navegación |
| `UI.NAV.SEARCH` | Pestaña «Buscar» de la barra de navegación |
| `UI.NAV.TOGETHER` | Pestaña «Juntos» de la barra de navegación (si está en la barra) |

### Escuchar juntos (más)

| Código | Acción |
|---|---|
| `UI.LT.BACK` | Atrás (Escuchar juntos) |
| `UI.LT.MEMBER_SHEET_CANCEL` | «Cancelar» en las acciones de un miembro |

### Ajustes ▸ Sincronizar con YouTube Music (más)

| Código | Acción |
|---|---|
| `UI.YTMSYNC.BACK` | Atrás (Sincronizar con YouTube Music) |
| `UI.YTMSYNC.ONBOARDING_START` | «Comenzar a usar Aura» (fin de la bienvenida) |
| `UI.YTMSYNC.LOGIN` | Iniciar sesión (sincronizar) |
| `UI.YTMSYNC.SYNC_ALL` | «Sincronizar todo» |
| `UI.YTMSYNC.SYNC_LIKED_SONGS` | «Me gusta (canciones)» |
| `UI.YTMSYNC.SYNC_LIKED_ALBUMS` | «Álbumes favoritos» |
| `UI.YTMSYNC.SYNC_ARTISTS` | «Artistas y suscripciones» |
| `UI.YTMSYNC.SYNC_PLAYLISTS` | «Playlists guardadas» |
| `UI.YTMSYNC.SYNC_LIBRARY` | «Biblioteca (canciones)» |
| `UI.YTMSYNC.SYNC_UPLOADS` | «Subidas (canciones y álbumes)» |
| `UI.YTMSYNC.FREQ_OFF` | Frecuencia «Desactivada» |
| `UI.YTMSYNC.FREQ_3_DAYS` | Frecuencia «Cada 3 días» |
| `UI.YTMSYNC.FREQ_DAILY` | Frecuencia «Cada día» |
| `UI.YTMSYNC.FREQ_WEEKLY` | Frecuencia «Cada semana» |
| `UI.YTMSYNC.UPLOAD_CONFIRM` | Confirmar «Sincronizar mi biblioteca con mi cuenta» |
| `UI.YTMSYNC.UPLOAD_CANCEL` | «Cancelar» en «Sincronizar mi biblioteca con mi cuenta» |
| `UI.YTMSYNC.UPLOAD_TOGGLE` | «Sincronizar mi biblioteca con mi cuenta» (sí/no) |
| `UI.YTMSYNC.IMPORT_FOLLOWS` | Artistas seguidos pendientes de importar (abre la pregunta) |
| `UI.YTMSYNC.UPLOAD_RUN_NOW` | «Sincronizar toda mi biblioteca ahora» |

### Ajustes ▸ Sincronizar dispositivos (más)

| Código | Acción |
|---|---|
| `UI.DEVSET.QR_CLOSE` | «Aceptar» (cerrar el código QR) |
| `UI.DEVSET.BACK` | Atrás (Sincronizar dispositivos) |
| `UI.DEVSET.SWITCH_ROW` | Fila de un interruptor de Sincronizar dispositivos (sí/no) |
| `UI.DEVSET.TEXT_CANCEL` | «Cancelar» al editar el servidor o el nombre |
| `UI.DEVSET.HELD_BACK` | Atrás (Retenidas) |

### Lo que sigue sin código, a propósito (fila 824)

Cada caso tiene su motivo; `ActionLogGuardTest` lleva la lista de archivos exentos con la razón de cada uno y falla si
aparece un botón nuevo sin código o si una exención ya no hace falta.

- **Flecha de minimizar el reproductor** (fila 513): `FirstTapAndHeaderGuardTest` fija su `onClick` literal. El
  conmutador pantalla completa / dividida sí queda registrado: es un ajuste y sale como
  `UI.SET.TOGGLE key=aura_wide_player_split value=…`.
- **Abrir y cancelar «Pedir música»** (fila 730): `AuraHomeLayoutTest` y `MusicRequestCardGuardTest` fijan sus
  `onClick` literales.
- **Filas de Ajustes cuyo `onClick` fija un test**: «Ocultos» (Contenido; `HiddenItemsTest`), «Romanización de letras»
  (Contenido; `LyricsSettingsLinksGuardTest`), «Política de privacidad» (Privacidad y Acerca de;
  `PrivacyPolicyAssetTest`), «Revisar suscripciones» (Cuentas; `SubscriptionCleanupGuardTest`) y la cabecera «Tu
  biblioteca» de la página de un artista (`ArtistPageLibrarySongsGuardTest`). Las pantallas a las que llevan sí registran sus
  propios botones, salvo la siguiente.
- **Pantalla «Ocultos»**: `HiddenItemsTest` prohíbe cualquier `Log.` en ese archivo (regla 4) y `ActionLog.tap` lo
  contiene; queda fuera hasta que el dueño decida relajar esa comprobación.
- **Cabecera de los acordeones del menú del reproductor**: `PlayerAndAudioSheetGlassGuardTest` fija `onClick = onToggle`.
- **Ajustes de sí/no u opciones**: ya salen como `UI.SET.TOGGLE` / `UI.SET.CHOICE` con la clave y el valor nuevo; no
  llevan un segundo código.
- **Pantallas clásicas** (con una sola interfaz, `SINGLE_UI`, nunca se dibujan) y **componentes genéricos** cuyo toque es
  el del que los usa (ese ya lleva su código).
- **Pantallas de licencia y suscripción** (`license/`): fuera de este paquete de trabajo (otro paquete edita esos textos a
  la vez); pendiente de decisión.
