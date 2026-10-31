# Aura Hi-Res v2.0.67-beta10 — aleatorio y control remoto corregidos, listas fijadas sincronizadas, cola inteligente y sugeridas más fieles, menos cosas repetidas

Beta encima de la 2.0.67-beta9 (incluye todo lo suyo) y de la 2.0.66 estable. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma. Para que las listas fijadas se sincronicen no hay que redesplegar nada: usan el mismo servidor de sincronización que ya tienes.

## Aleatorio y control remoto

- **«Empezar desde cero» del aleatorio ya empieza de cero en todos tus dispositivos:** antes solo borraba en el aparato donde lo tocabas y el otro devolvía lo que ya había escuchado («26 de 29» recién reiniciado).
- **El interruptor de aleatorio manda sobre el dispositivo que suena:** si controlas la tablet desde el cel, el aleatorio se activa en la tablet y el interruptor muestra su estado real, en el reproductor nuevo y también en la cola y el menú clásicos. Antes solo reordenaba la cola del cel.
- **El volumen remoto ya no vuelve solo al valor viejo:** el dispositivo que suena avisa de su nuevo volumen en cuanto lo recibe.
- **Tocar el nombre del artista mientras controlas otro dispositivo** ofrece «Ir a la lista» de donde suena.

## Listas fijadas sincronizadas

- **Biblioteca ▸ Fijados y «Fijadas en inicio» se sincronizan entre tus dispositivos** por tu servidor. Fijar o quitar en uno aparece en el otro. Las listas locales se asocian por su identidad de sincronización. El orden manual de Fijados no se sincroniza.

## Cola inteligente, Relacionados y sugeridas

- **La cola infinita conserva el estilo que la guía:** cuando la lista, el álbum o la canción no dicen un estilo claro, la familia que manda se decide una vez con el primer lote y ya no la cambia cada relleno.
- **Las canciones de estilo sin comprobar solo rellenan lo necesario:** antes entraban pasos enteros (un 15% de lo admitido en los registros).
- **«Relacionados» de la cola** se juzga contra lo que hay en la cola (estilo, idioma y fe); lo que claramente no pertenece ya no aparece. Si nada encaja, se muestra como venía.
- **Canciones sugeridas de una lista:** lo que no encaja con la lista se deja fuera, también de los dos rellenos finales; solo si nada encaja se conserva lo mejor del resto para que la sección no quede vacía.

## Menos repetido y más uniforme

- **Cola:** quitadas las filas de vista previa bajo «Reproducción automática»; eran las mismas que la pestaña «Relacionados». El título, «Siguiendo: …» y los chips se conservan.
- **Biblioteca:** los atajos («Canciones que me gustan», «Álbumes favoritos»…) usan la cápsula de cristal de los botones de arriba. «Local» ya no lista los vídeos, que siguen en «Vídeos exportados».
- **Inicio, Novedades y Buscar:** los botones que se despliegan bajo el título llevan la misma cápsula de cristal que Biblioteca.
- **La búsqueda de Ajustes** indexa ahora todo lo que se lee en las pantallas de Ajustes, y una prueba automática avisa si una fila nueva no se indexa.

## Portada animada y registros

- **La portada animada del álbum en el reproductor** ya no se descarta cuando la página del álbum sí la acepta: se valida con el mismo álbum con el que se busca, y una portada rechazada ya no se recuerda como «ninguna».
- **Registros:** el botón de borrar ya no está junto a Compartir; está solo, abajo, y pide confirmación. El encabezado del registro ya no dice «enhancedShuffle=OFF» por error en dispositivos que nunca tocaron ese ajuste.

## Para que lo pruebes y me cuentes

- No pude probar nada de esto en un aparato. Si la cola se desvía, «siguiente» no cambia en remoto o la portada sigue quieta en algún álbum, manda el registro con la canción y la hora.

## De la beta 9: control remoto, arreglos sobre la beta 8

- **El control remoto vuelve a funcionar:** en la beta 8, tras pausar aquí dejaba de aparecer «Continuar aquí» y los controles del otro dispositivo. Ahora solo se retiene tu canción cuando el otro dispositivo está parado y su estado es viejo (hasta 10 minutos tras tu pausa); si el otro suena, siempre ves su canción y sus controles.
- **Tocar una canción de la cola mientras controlas la tablet suena en la tablet** y ya no corta la conexión ni empieza a sonar en el cel.
- **El traspaso ya no deja sin música:** el dispositivo que cede solo se pausa si la orden de «tomar» se envió de verdad. Si no se pudo enviar, sigue sonando.
- **Recordatorio de batería en Sincronizar dispositivos:** si el ahorro de batería puede dormir la reproducción en segundo plano, una fila te lo dice y te lleva a quitar la restricción. Las órdenes remotas que se ignoran ahora dejan su motivo en el registro.

## Biblioteca más limpia y sin repetidos

- **Todos los botones de Biblioteca con el mismo material de cristal,** las acciones de arriba agrupadas en una sola cápsula, las etiquetas de los atajos se leen enteras (hasta 3 líneas) y hay respiro inferior.
- **Quitado lo repetido:** el atajo «Novedades» de Biblioteca (abría lo mismo que la pestaña Novedades de la barra) y el chip «Local» (abría la misma lista que su atajo). Nada se pierde: sigues llegando a lo mismo por un solo camino.
- **Novedades es la única puerta del radar:** al deslizar para actualizar también corre el radar, y al abrirla las filas se marcan como vistas.

## Ajustes: la búsqueda lo encuentra todo

- **La búsqueda de Ajustes ahora indexa todo lo que se lee en las pantallas de Ajustes:** Liquid Glass completo, Servicios Aura, Sincronizar dispositivos, Spotify Canvas y letras, volumen del reproductor, modo offline automático, song.link, listas fijadas en Inicio, métricas a Google, salas de Aura y más. Antes se buscaban y no salían.

## Pedir música

- **Respuestas compartidas:** si otro usuario de Aura ya pidió lo mismo, la app reutiliza la lista que la IA propuso y se ahorra la espera. Solo viaja una huella del pedido, nunca lo que escribiste. Gobierna el interruptor general de Servicios Aura.

## Letras y portadas animadas

- **La traducción de letras ya no falla en silencio:** las respuestas cortas o dudosas no se guardan, se prueba el siguiente traductor de la cadena y queda en el registro (solo números) por qué falló.
- **La portada animada del álbum ahora se anima en el reproductor,** compartida con la página del álbum por artista principal y álbum, sin descargar de nuevo por cada canción del mismo álbum.

## Otros arreglos

- **Cambio automático de formato de vídeo:** si un formato de vídeo falla y la app prueba otro sola, ya no abre el reproductor grande.
- **Menos ruido en GitHub:** quitada una referencia rota (`src/graphify`) que avisaba en cada compilación.

## De la beta 8: «Escuchar juntos» propio de Aura

- **Salas de Aura (nuevo, apagado por defecto):** Ajustes ▸ Escuchar juntos ▸ **Salas de Aura**. Las salas viven en tu propio servidor, solo entre usuarios de Aura, con el código de 6 letras de siempre. Apagado, todo sigue como antes con el servidor compartido que también usan otras apps. No se puede cambiar mientras estás en una sala.
- Sin sala no hay conexión: la pantalla queda lista y el socket se abre al crear o unirse. Si se cae la conexión, vuelve a la misma sala sola; al salir de la sala se cierra todo.

## Control remoto entre tus dispositivos

- **«No me gusta» y «Me gusta» actúan donde suena la música:** antes, tocar «no me gusta» en el cel mientras sonaba la tablet saltaba la canción del cel y la tablet solo lo anotaba. Ahora la orden llega a la tablet, que quita la canción de su cola y salta; y cuando el «no me gusta» llega por la sincronización, el otro dispositivo también la quita de la cola y la salta.
- **El volumen del menú del reproductor mueve el volumen del dispositivo que está sonando** y muestra su nivel real.
- **Lo que reproduces en el cel mientras controlas la tablet suena en la tablet,** como en Spotify. Si no se puede enviar, suena en el cel.
- **«Siguiente» ya no se pierde al final de la cola:** si la cola inteligente aún está buscando más canciones, el «siguiente» espera y salta en cuanto llegan (hasta 3 toques, 30 segundos).
- **Pausar aquí ya no te cambia la canción:** pausar el dispositivo que sonaba hacía que cargara la cola del otro y te cambiaba la canción de la nada. Ahora, si lo que muestra del otro dispositivo es un estado viejo (el otro NO está sonando), tu canción se conserva durante 10 minutos tras tu pausa; si el otro sí suena, siempre ves su canción y sus controles.
- **«Continuar aquí» ya no repone una cola vieja** cuando el otro dispositivo dejó de contestar: avisa «Ese dispositivo no responde».

## Historial y cola inteligente

- **El Historial se sincroniza entre tus dispositivos** por tu servidor (las 300 reproducciones más recientes). Lo recibido no suma tiempo de escucha propio. Dentro de un día el historial se ordena por la hora real. Borrar el historial en un dispositivo no lo borra en el otro.
- **La cola mantiene lo cristiano tras un traspaso:** al pasar la reproducción de un dispositivo a otro, la cola se rehacía y juzgaba la fe solo por las palabras del título. Ahora cuenta también lo que ya se aprendió del artista, para que no derive a rap secular.

## Portada del reproductor

- **La portada se ve más por encima del fondo, no plana:** el filo es más claro (y más oscuro en la esquina opuesta, para que se note incluso sobre portadas claras) y hay un brillo suave arriba y una sombra suave abajo sobre la propia imagen. La sombra de fuera no cambia.

## Para encontrar fallos con tu próximo registro

- **Una canción de la cola que se sale del estilo** deja, al darle «no me gusta», cómo entró (paso y base) y contra qué objetivo se juzgó; las etiquetas de artista dicen si respondió el servidor de Aura o Last.fm; y cada vez que se abre el reproductor grande queda quién lo abrió. Todo en números, nunca títulos ni nombres.

## De la beta 7: copia de seguridad en la nube de Aura

- **Ajustes ▸ Copia de seguridad ▸ «Guardar copia en la nube de Aura»** y **«Restaurar la última copia de la nube»**: la misma copia de tu biblioteca de siempre (sin tu sesión de YouTube ni tu licencia), cifrada en tu móvil antes de salir. El servidor solo guarda bytes que no puede leer. Se conservan las últimas 3 copias y el máximo es de 24 MB cada una.
- La clave sale de tu código de emparejamiento o de tu licencia y correo de compra. Si se prueba con otra clave, o la copia está dañada, no se restaura nada y la app te lo dice. Restaurar pide confirmación porque reemplaza tu biblioteca y reinicia la app.

## Portadas animadas y estilos de la cola inteligente, compartidos

- **Portadas animadas:** si otro usuario de Aura ya encontró la portada animada de un álbum, tu app la toma de un lugar común en vez de repetir la búsqueda larga. Solo se comparten direcciones de sitios conocidos y solo si de verdad son de ese álbum. Menos consultas, menos calor y menos batería.
- **Estilos de la cola inteligente:** las etiquetas de estilo de cada artista se consultan una vez para todos. Si el servidor no contesta en un instante, la app pregunta como siempre.

## Registros más completos

- **«Enviar registro»** ahora sube también la parte anterior del registro, donde queda lo que pasó al conectar los dispositivos. Sigue enviándose solo cuando lo pulsas.
- **Cola infinita:** nueva línea de diagnóstico (solo números, nunca nombres) que dice cuánto de lo que la cola aprende viene de las palabras de la propia canción y cuánto del estilo que se pidió. Sirve para encontrar por qué la cola a veces se va desviando después de varias canciones.

## Sincronización de la biblioteca (de la beta 6)

- **Se acabó la tormenta de descargas:** cuando un dispositivo subía una biblioteca grande, el otro pedía la misma lista una y otra vez (en un registro se vieron 1656 descargas en 5 segundos). Gastaba batería, calentaba y dejaba la sincronización sin cerrar, y mientras no cerraba, los cambios de ese dispositivo no salían. Ahora pide una sola vez y repite solo si hubo novedades.
- **Tus «me gusta» y «no me gusta» llegan también a tu cuenta de YouTube:** si el dispositivo donde los diste no pudo enviarlos (sin sesión o sin red), el que los recibe por tu servidor los manda. Solo cuando son pocos; una sincronización grande no se reenvía una a una.
- **Los álbumes guardados también se sincronizan entre tus dispositivos** (el corazón de la página del álbum): se guarda el álbum, no sus canciones. Los «me gusta» de las canciones siguen siendo tuyos, canción por canción.
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

## Portada del reproductor (de las betas 4 y 5)

- **La sombra ya no se corta en recuadro** en los móviles normales: la portada deja margen para que se desvanezca. En tablets y plegables no cambia.
- El botón «Mis dispositivos» está en la barra de abajo del reproductor, con el estilo de sus vecinos.
