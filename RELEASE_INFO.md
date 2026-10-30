# Aura Hi-Res v2.0.67-beta5 — tus dispositivos dejan de confundirse entre sí, el reproductor controla al otro dispositivo como Spotify, la biblioteca se sincroniza y la portada ya no se corta

Beta encima de la 2.0.67-beta4 (incluye todo lo suyo) y de la 2.0.66 estable. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma. Antes de probar la sincronización de biblioteca haz una copia de seguridad (Ajustes ▸ Copia de seguridad) y vuelve a desplegar tu servidor de sincronización (`npx wrangler deploy`): su dirección `/health` debe responder `"lib":2`.

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
