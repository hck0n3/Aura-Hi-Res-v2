# Aura Hi-Res v2.0.69-beta6 — beta privada: sincronización exacta con Spotify y tu cuenta, revisión de suscripciones en segundo plano y números reales

Beta solo para probar, sobre la 2.0.69-beta5 que tienes instalada (incluye todo lo de la beta5, la beta4, la beta3, la beta2 y la beta1; sus notas siguen valiendo y se juntarán en la versión estable). Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma. Te llega por el actualizador solo con «Recibir versiones beta» encendido (Ajustes ▸ Actualizaciones).

## Importar de Spotify: solo lo que pides

- **Nada viene marcado al abrir:** eliges tú qué importar (listas, canciones que te gustan, artistas, álbumes).
- **La sincronización programada solo lleva listas:** los artistas que sigues y los álbumes guardados ya no se vuelven a importar solos cada pocos días.
- **Sin artistas equivocados:** si en YouTube hay más de un artista con el mismo nombre, Aura no elige ninguno y lo pone en «no encontrados».
- **Nunca vuelve a seguir lo que quitaste:** un artista que quitaste (en cualquiera de sus versiones de canal) no se vuelve a seguir, y solo se siguen los artistas nuevos en Spotify desde la última importación.
- **Álbumes exactos:** un álbum guardado solo se empareja si coinciden el artista y el título.
- **Canciones exactas:** se usa el emparejamiento estricto de la migración (mismo artista, título parecido y ventaja clara sobre la segunda opción). Los títulos en coreano, japonés, ruso, etc. ya no aceptan cualquier canción.
- **La importación ya no da «me gusta» en tu cuenta,** y una pasada programada ya no vuelve a marcar canciones que desmarcaste.
- **Nunca se mezcla con una lista tuya:** una lista de Spotify ya no se une a una tuya de YouTube que se llame igual, ni le quita canciones. Solo se actualiza la copia que creó Aura.
- **Sin listas que no pediste:** ya no se crea en tu cuenta la lista «Canciones que te gustan» ni listas vacías.
- **«N artistas esperan confirmación» muestra los nombres:** eliges con casillas (ninguna marcada) a cuáles suscribirte en tu cuenta. Hay un botón para marcar o desmarcar todos.

## Nada llega a tu cuenta de YouTube sin tu permiso

- **Con «Subir a tu cuenta» apagado,** Aura ya no manda sola «me gusta» ni «añadir a biblioteca» a tu cuenta, y ya no añade a tu biblioteca todas las canciones de las listas que guardas.
- **Lo que hace otro aparato no se reenvía a tu cuenta desde este:** los «me gusta», álbumes y listas que llegan por «Sincronizar dispositivos» solo los manda a la cuenta el aparato donde los hiciste.
- **Al cambiar de cuenta,** nada pendiente de una cuenta se envía con la otra.
- **Mientras tus aparatos llevan la biblioteca,** Aura ya no vuelve a dar «me gusta» ni a guardar álbumes que quitaste en YouTube.
- **El botón «Suscribirme» del reproductor solo responde a un toque limpio:** deslizar el dedo por encima o mantenerlo pulsado ya no suscribe.
- **Un enlace o QR de emparejamiento te pregunta antes** de cambiar este aparato a otra sala de sincronización. El código de ejemplo del campo ya no es un código válido.

## Revisar suscripciones

- **Lee tu lista entera:** antes se paraba sin avisar a las 50 páginas (unas 1000 suscripciones). Si YouTube no deja leerla entera, avisa «Lista incompleta» con un botón para recargar.
- **Sigue quitando en segundo plano:** puedes salir de la pantalla o de la app y continúa. Una notificación muestra «Quitando suscripciones: X de N» con «Detener».
- **Al volver ves el progreso actual:** cuántas van quitadas, cuáles fallaron y cuáles faltan. Si Android cerró la app a mitad, te dice hasta dónde llegó y te deja continuar.

## Sincronizar con YouTube Music: números reales

- **Ya no dice «1000» en todo:** las listas, canciones, artistas y álbumes de tu cuenta se leen hasta el final, así que los números son los de verdad.
- **Si una lectura no llega al final, lo dice:** verás «al menos N · lista incompleta» en vez de un número que parece exacto. Y nada se quita ni se borra a partir de una lista incompleta.

## Para que lo pruebes y me cuentes

- No pude probar nada de esto en un aparato: está revisado leyendo el código y con pruebas automáticas, pero falta tu móvil.
- **Revisar suscripciones:** abre Ajustes ▸ Cuentas ▸ «Revisar suscripciones», mira que el total sea el real, quita varias, sal de la app y comprueba que la notificación avanza; vuelve a entrar y debe verse el progreso.
- **Sincronizar con YouTube Music:** los números de cada apartado deben ser los reales, no 1000.
- **Spotify:** importa una sola lista y comprueba que no se suscribe a nadie ni da «me gusta» a nada más.
- **Importante, fuera de Aura:** si la app antigua (la de antes de la v2) sigue instalada, desinstálala; esa versión se suscribía sola a los artistas de Spotify y no se puede arreglar. Y revisa en tu cuenta de Google ▸ Seguridad ▸ Tus dispositivos que no haya aparatos que no sean tuyos.
- Si algo falla, manda el registro desde Ajustes ▸ Registros con la hora.
