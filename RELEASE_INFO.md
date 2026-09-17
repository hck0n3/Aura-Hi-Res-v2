# Aura Hi-Res v2.0.44 (beta 1) — Vídeo sin errores, canciones que no empiezan cortadas y la sincronización que sí termina

BETA para probar antes de decidir si sale para todos (v2.0.44 / versionCode 1005). Se instala encima de
tu 2.0.43 sin perder nada: mismo paquete (iad1tya.aura.music) y misma firma (CN=Aura Hi-Res v2), conserva
tus datos, tu sesión y tus ajustes. No se le ofrece a nadie más — GitHub excluye las betas del canal de
actualización de la app.

## Modo vídeo

- **Ninguna canción con vídeo debería volver a dar error.** Cuando el formato que YouTube devuelve no se deja leer, la app ya no se queda atascada pidiendo el mismo: prueba otro formato, luego otro proveedor y por último uno progresivo. Cuatro caminos antes de rendirse.
- **Y si ninguno funciona, la canción NO se salta.** Se queda sonando en audio con la música intacta. Antes el fallo del vídeo arrastraba al audio y acababa saltándose la canción entera — ese `io_unspecified (2000)` que veías en pantalla era el último eslabón, no la causa.
- Un formato que ya falló para un vídeo no se vuelve a elegir en toda la sesión, ni siquiera al preparar la siguiente canción.

## Reproducción

- **Arregladas las canciones que empezaban cortadas** al cambiar de canción a mano y en Android Auto. La entrada suave esperaba a que el audio «estuviera sonando» con el volumen en cero, y esa condición es falsa mientras el coche o el navegador bajan el volumen un momento — así que el principio de la canción se perdía. Ahora esa espera dura poco más de un segundo y, si se agota, vuelve el volumen entero de golpe: mejor sin fundido que sin el principio.
- **Y arreglado que una canción se quedara sonando a medio volumen** el resto de la mezcla cuando un crossfade empezaba justo encima del cambio.

## Biblioteca

- **La sincronización con tu cuenta ahora llega hasta el final.** Era una tarea normal de Android, y esas las corta el sistema a los diez minutos; como cada intento empezaba desde el principio, una biblioteca grande nunca terminaba. Ahora es una tarea de primer plano, sin ese límite, y con una notificación silenciosa para que veas que sigue trabajando aunque cierres la app.
- **Deja de insistir cuando YouTube no acepta más suscripciones.** Tu cuenta tiene un límite y estaba rechazando decenas de artistas seguidos; la app los reintentaba en bucle sin decírtelo. Ahora para y lo intenta más tarde.

## Interfaz

- **Botón de compartir en el reproductor a pantalla completa**, en la misma fila de accesos rápidos, sin cambiar nada del diseño.
- **Botón flotante para volver al inicio** en todas las pantallas donde la barra de abajo está oculta, para no tener que dar atrás muchas veces. Abajo a la izquierda, por encima del minirreproductor y fuera del camino.
- **Las ventanas que suben desde abajo ahora miden lo que mide su contenido.** La tarjeta de salida de audio y los menús subían al 85 % de la pantalla tuvieran tres opciones o treinta.

## Escuchar juntos

- **Sincronización al estilo Sonos/AirPlay:** en vez de dar saltos, el invitado se alinea estirando el tiempo un 2 % como mucho. Como el tono se conserva, no se oye ni un corte.
- **Compensación de la latencia de salida:** cada teléfono descuenta lo que su propio altavoz o su Bluetooth retrasan, así que dos aparatos con salidas distintas dejan de sonar desfasados.

## Diagnóstico

- El log compartido ahora registra las descargas para escuchar sin conexión (en cola, detenida, terminada, fallida y por qué). Antes solo escribía cuando una fallaba del todo, así que una descarga que no arrancaba no dejaba ningún rastro.
