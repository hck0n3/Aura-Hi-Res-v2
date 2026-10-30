# Aura Hi-Res v2.0.67-beta6 — la biblioteca sincroniza sin repetir descargas, tus «me gusta» llegan también a YouTube y nacen los Servicios Aura

Beta encima de la 2.0.67-beta5 (incluye todo lo suyo) y de la 2.0.66 estable. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Sincronización de la biblioteca

- **Se acabó la tormenta de descargas:** cuando un dispositivo subía una biblioteca grande, el otro pedía la misma lista una y otra vez (en un registro se vieron 1656 descargas en 5 segundos). Gastaba batería, calentaba y dejaba la sincronización sin cerrar, y mientras no cerraba, los cambios de ese dispositivo no salían. Ahora pide una sola vez y repite solo si hubo novedades.
- **Tus «me gusta» y «no me gusta» llegan también a tu cuenta de YouTube:** si el dispositivo donde los diste no pudo enviarlos (sin sesión o sin red), el que los recibe por tu servidor los manda. Solo cuando son pocos; una sincronización grande no se reenvía una a una.
- El registro de la aplicación explica ahora por qué un cambio no salió (solo contadores, nunca títulos ni nombres).

## Servicios Aura (nuevo)

- **Ajustes ▸ Servicios Aura:** interruptor general, estado y «Comprobar ahora». Ya viene configurado de fábrica con el servidor de Aura; no hay que escribir nada. Si no hay red o el servidor no contesta, todo sigue encendido como siempre. Se consulta como mucho una vez cada 6 horas y solo pide una configuración pública; no envía nada tuyo. Puedes apagarlo con el interruptor general.
- **Interruptores remotos y avisos:** desde tu servidor puedes apagar o encender una función que ya está en la app (cola inteligente, portadas animadas, letras…) y mostrar un aviso, sin publicar versión. Lo que no venga firmado por tu servidor se ignora. La reproducción y la licencia no se pueden apagar desde fuera.
- **Enviar registro:** un botón que sube el mismo registro que compartirías a mano y te da un código corto para pasarlo. Solo se envía cuando lo pulsas.

## Por qué fallaba la sincronización

- **Cada dispositivo ahora tiene su propia identidad:** el identificador con el que un dispositivo se presenta a tu servidor salía de los ajustes de la app, y los ajustes se copian al restaurar una copia de seguridad o al pasar datos de un móvil a una tablet. Dos aparatos acababan siendo «el mismo» para el servidor: se echaban uno al otro, el contador saltaba entre 2 y 3, las órdenes llegaban al aparato equivocado (uno empezaba a sonar solo, el otro no se detenía) y la biblioteca no se igualaba. Ahora el identificador sale del propio aparato y no se puede copiar.
- **Ajustes ▸ Sincronizar dispositivos** muestra los últimos 4 caracteres de cada dispositivo para distinguirlos, y una fila **«Biblioteca»** con el estado real (al día, esperando, servidor desactualizado, fallo). Tócala para sincronizar ahora.
- **Si tu servidor es viejo, la app lo dice** en vez de quedarse callada.

## El reproductor controla al otro dispositivo, como Spotify

- Sin tarjeta flotante. Cuando otro de tus dispositivos suena y este no, **tu reproductor muestra esa canción**: arriba de la portada aparece «Sonando en <dispositivo> · Continuar aquí».
- **La barra de tiempo avanza con la del otro dispositivo** y **play y pausa, anterior, siguiente y arrastrar la barra** (en el reproductor y en el mini reproductor) controlan a donde se está reproduciendo, sin controles duplicados.
- **«Continuar aquí»** trae la canción desde el mismo segundo; el otro dispositivo se pausa. Si no contesta, se le manda una pausa para que no suenen los dos. Un traspaso viejo ya no puede empezar a sonar un dispositivo por sí solo.

## Biblioteca (me gusta, no me gusta y listas)

- Se sincroniza por tu servidor, al momento o al conectarse, y mientras estén conectados YouTube no la sincroniza. Freno de seguridad si llegaran muchos borrados de golpe. El orden de las canciones dentro de una lista no se sincroniza.

## Portada del reproductor

- **La sombra ya no se corta en recuadro** en los móviles normales: la portada deja margen para que se desvanezca. En tablets y plegables no cambia.
- El botón «Mis dispositivos» está en la barra de abajo del reproductor, con el estilo de sus vecinos.
