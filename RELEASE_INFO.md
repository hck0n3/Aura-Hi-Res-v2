# Aura Hi-Res v2.0.69-beta1 — beta privada: arreglos de la auditoría de las zonas que más fallan

Beta solo para probar, sobre la 2.0.68. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma. Te llega por el actualizador solo con «Recibir versiones beta» encendido (Ajustes ▸ Actualizaciones).

## Cierres y bloqueos

- **Un enlace de YouTube Music mal formado ya no cierra la app:** los enlaces `/browse/…` y `/channel/…` con caracteres raros abren Aura sin cerrarse.
- **«En caché» ya no se cierra con una caché grande:** en Android 8 a 11, con más de 999 canciones en la caché de escucha, la pantalla «En caché» y la búsqueda sin conexión se cerraban.
- **Abrir un álbum ya no borra a sus artistas:** al refrescar un álbum podía perderse el seguimiento, la foto y las canciones de un artista que sigues.

## Cola y reproducción

- **Al terminar un álbum o una lista, la cola sigue con su estilo e idioma** (el motor de continuación) en vez de con la radio cruda de YouTube.
- **Dos dispositivos emparejados no suenan a la vez:** empezar a sonar en uno pausa el otro al momento, también después de un crossfade.
- **Fondo animado a pantalla completa en horizontal:** el ajuste de Ajustes ▸ Rendimiento vuelve a funcionar. Con el móvil girado y un lienzo animado, solo se ve el lienzo con anterior, play y siguiente, que se ocultan solos y vuelven al tocar.

## Batería, datos y privacidad

- **Entrar a las pestañas de la Biblioteca ya no sincroniza todo con YouTube cada vez:** respeta la frecuencia que elegiste.
- **Los ajustes que nunca cambiaste ya no frenan la app** al leerse en el arranque y al abrir pantallas.
- **La segunda opinión de la IA de la cola viene apagada:** si la quieres, enciéndela en Ajustes ▸ Servicios Aura. La política de privacidad explica qué envía.
- **El registro que compartes ya no lleva trozos de las respuestas del servidor de audio.**

## Por dentro

- Se borró el reproductor clásico, que ya no se podía abrir desde ningún sitio. El reproductor de siempre (el nuevo), la mini barra y la letra de la cola no cambian.
