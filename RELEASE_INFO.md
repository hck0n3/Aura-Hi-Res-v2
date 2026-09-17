# Aura Hi-Res v2.0.46 (beta 3) — La biblioteca ya no cierra la app, y "pedir música" vive en Inicio

BETA para probar antes de decidir si sale para todos (v2.0.46 / versionCode 1007). Se instala encima de
la beta 2 o de tu 2.0.43 sin perder nada: mismo paquete (iad1tya.aura.music) y misma firma
(CN=Aura Hi-Res v2), conserva tus datos, tu sesión y tus ajustes. No se le ofrece a nadie más — GitHub
excluye las betas del canal de actualización de la app.

## Lo que arregla de la beta 2

- **Entrar en la biblioteca ya no cierra la app.** Lo metí yo en la beta 2: al botón "Más" le puse el cristal interactivo, y ese cristal copia el fondo de la pantalla... estando él dentro de la pantalla que copia. Un dibujo que se muerde la cola. El botón vuelve al cristal de antes, que se ve casi igual y no se cae; queda escrito en el código para que no se vuelva a intentar por ahí.
- **"Pedir música" se muda a Inicio y deja de parecer un formulario.** Ahora es una tarjeta en la portada, al estilo de YouTube Music: la tocas y sube una hoja con cinco ideas ya escritas ("para concentrarme", "como Bad Bunny", "para correr"...) o escribes lo que quieras. Mientras trabaja te dice cuántas canciones va encontrando, y al terminar pone la lista a reproducir directamente. Desaparece sola cuando estás sin conexión.

## Reproducción (ya venía de la beta 2)

- **La cola de un álbum ya no se llena de canciones ajenas.** Saltar a mano hasta la última canción metía ahí mismo una tanda de música de otros artistas. Con el aleatorio encendido era peor: podía pasar habiendo escuchado tres canciones de quince. Ahora el álbum se mantiene entero, y la cola infinita entra solo cuando de verdad termina.
- **El vídeo ya no se cuelga.** Al fallar el formato había un bucle de reintentos que no paraba nunca. Además del arreglo hay un tope de ocho intentos por canción que no se reinicia, así que un bucle es imposible incluso por un camino que no haya previsto. Cuando se agotan los intentos: audio y listo.
- **Las playlists de solo vídeo avanzan como una cola.** Antes cada "siguiente" salía de modo vídeo. Ahora vídeo pasa a vídeo; una canción sin vídeo sigue bajando a audio, así que en una lista mixta nada cambia.
- **Las canciones ya no empiezan cortadas** al cambiar a mano ni en Android Auto.
- **Identificar una canción es más rápido y acierta más.** Antes grababa 10 segundos completos y hacía una sola consulta. Ahora consulta a los 3, 5, 8 y 12 segundos sin dejar de grabar, y se queda con el primer acierto: normalmente a los 3-5 segundos, y con cuatro oportunidades en vez de una.

## Biblioteca

- **Descarga automática por lista.** Nuevo interruptor en el menú de cada lista: con él activo se descarga todo lo que tiene y todo lo que le añadas después, venga de donde venga (el menú de una canción, una importación, la sincronización desde otro aparato). Apagarlo no borra nada de lo que ya bajaste.
- **La sincronización con tu cuenta llega hasta el final** aunque cierres la app, con una notificación silenciosa mientras trabaja.

## Interfaz

- **El atajo a Inicio vive dentro del minirreproductor**, al final de los controles, y solo aparece donde la barra de abajo está oculta. Al aparecer, el título se compacta animado para hacerle sitio; al desaparecer, la píldora vuelve a la normalidad.
- **El botón de salida de audio responde al primer toque**, y ya no cambia de canción cuando insistes.
- **Portadas de álbum y de playlist a todo el ancho**, con el mismo cristal borroso progresivo de las portadas de artista.
- **Tocar los nombres de los artistas** abre justo debajo "Ver álbum" y "Ver artista".
- **Botón de compartir** en el reproductor a pantalla completa.
- **Las ventanas que suben desde abajo miden lo que mide su contenido.** La tarjeta de salida de audio y los menús subían al 85 % de la pantalla tuvieran tres opciones o treinta.
- **En Novedades ya no hay estantes con una sola tarjeta**, y se fue la categoría de "playlists actualizadas", que repetía contenido de Inicio.
- **El botón de descarga de las listas se fue al menú de tres puntos**, donde ya estaba la misma función.

## Escuchar juntos

- Sincronización al estilo Sonos/AirPlay: el invitado se alinea estirando el tiempo un 2 % como mucho, sin saltos ni cortes.
- Cada teléfono descuenta lo que su propio altavoz o su Bluetooth retrasan.

## Diagnóstico

- El log registra las descargas (en cola, detenida, terminada, fallida y por qué) y el inicio y fin de cada sincronización, así que si algo falla te lo puedo decir exacto.

## Lo que queda pendiente para la siguiente

- Modo offline automático (que la app se ponga en local sola cuando no hay red y vuelva sola cuando la hay).
- Repasar el algoritmo de predicción y afinar la puntería de la cola infinita.
