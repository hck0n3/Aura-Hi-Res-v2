# Aura Hi-Res v2.0.45 (beta 2) — La cola ya no se contamina, el vídeo ya no se cuelga y el cast responde

BETA para probar antes de decidir si sale para todos (v2.0.45 / versionCode 1006). Se instala encima de
la beta anterior o de tu 2.0.43 sin perder nada: mismo paquete (iad1tya.aura.music) y misma firma
(CN=Aura Hi-Res v2), conserva tus datos, tu sesión y tus ajustes. No se le ofrece a nadie más — GitHub
excluye las betas del canal de actualización de la app.

## Lo primero que deberías notar

- **Arreglado el cuelgue de vídeo de la beta 1.** Lo metí yo: al fallar el formato, la app entraba en un bucle de reintentos que no paraba nunca. Ya no puede repetirse — además del arreglo, hay un tope de ocho intentos por canción que no se reinicia, así que un bucle es imposible incluso por un camino que no haya previsto. Cuando se agotan los intentos: audio y listo.
- **La cola de un álbum ya no se llena de canciones ajenas.** Saltar a mano hasta la última canción metía ahí mismo una tanda de música de otros artistas. Con el aleatorio encendido era peor: podía pasar habiendo escuchado tres canciones de quince. Ahora el álbum se mantiene entero, y la cola infinita entra solo cuando de verdad termina.
- **El botón de salida de audio responde al primer toque**, y ya no cambia de canción cuando insistes. Eran dos cosas distintas: el arrastre de la hoja le robaba el toque al botón, y el minirreproductor dejaba un gesto vivo sobre una franja invisible.

## Reproducción

- **Las playlists de solo vídeo avanzan como una cola.** Antes cada "siguiente" salía de modo vídeo, así que había que volver a tocar Vídeo en cada elemento. Ahora vídeo pasa a vídeo; una canción sin vídeo sigue bajando a audio, así que en una lista mixta nada cambia.
- **Identificar una canción es más rápido y acierta más.** Antes grababa 10 segundos completos y hacía una sola consulta. Ahora consulta a los 3, 5, 8 y 12 segundos sin dejar de grabar, y se queda con el primer acierto: normalmente a los 3-5 segundos, y con cuatro oportunidades en vez de una.

## Biblioteca

- **Descarga automática por lista.** Nuevo interruptor en el menú de cada lista: con él activo se descarga todo lo que tiene y todo lo que le añadas después, venga de donde venga (el menú de una canción, una importación, la sincronización desde otro aparato). Apagarlo no borra nada de lo que ya bajaste.
- **La sincronización con tu cuenta llega hasta el final** aunque cierres la app, con una notificación silenciosa mientras trabaja.

## Interfaz

- **El atajo a Inicio vive dentro del minirreproductor**, al final de los controles, y solo aparece donde la barra de abajo está oculta. Al aparecer, el título se compacta animado para hacerle sitio; al desaparecer, la píldora vuelve a la normalidad.
- **Portadas de álbum y de playlist a todo el ancho**, con el mismo cristal borroso progresivo de las portadas de artista.
- **Tocar los nombres de los artistas** abre justo debajo "Ver álbum" y "Ver artista".
- **Botón de compartir** en el reproductor a pantalla completa.
- **El botón "Más" de la biblioteca** con el cristal interactivo.
- **Las ventanas que suben desde abajo miden lo que mide su contenido.** La tarjeta de salida de audio y los menús subían al 85 % de la pantalla tuvieran tres opciones o treinta.
- **En Novedades ya no hay estantes con una sola tarjeta**, y se fue la categoría de "playlists actualizadas", que repetía contenido de Inicio.
- **El botón de descarga de las listas se fue al menú de tres puntos**, donde ya estaba la misma función.

## Escuchar juntos

- Sincronización al estilo Sonos/AirPlay: el invitado se alinea estirando el tiempo un 2 % como mucho, sin saltos ni cortes.
- Cada teléfono descuenta lo que su propio altavoz o su Bluetooth retrasan.

## Diagnóstico

- El log registra las descargas (en cola, detenida, terminada, fallida y por qué) y el inicio y fin de cada sincronización, así que si algo falla te lo puedo decir exacto.
