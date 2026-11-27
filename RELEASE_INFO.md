# Aura Hi-Res v2.0.69-beta18 — beta privada (6 de 7): sincronización completa entre aparatos y Modo Fidelidad

Beta solo para probar, sobre la 2.0.69-beta17 (incluye todo lo de las betas anteriores, también el arreglo urgente de la reproducción; sus notas siguen valiendo y se juntarán en la versión estable). Es la sexta de las 7 betas que acordamos. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma. Te llega por el actualizador solo con «Recibir versiones beta» encendido (Ajustes ▸ Actualizaciones).

## Sincronización entre tus aparatos

- **Cada cambio viaja con la hora en que lo hiciste**, también sin conexión: si un aparato se conecta más tarde, su cambio antiguo ya no pisa uno más nuevo del otro.
- **El orden de tus listas se sincroniza**, y si en un aparato añades una canción mientras en otro reordenas, se conservan las dos cosas.
- **Nada se da por enviado hasta que el servidor lo confirma:** si se corta la conexión, se vuelve a mandar sin duplicar nada.
- **El servidor se limpia solo** de lo que ya tienen todos tus aparatos, sin borrar nada que alguno no haya recibido.
- **Si la sala se llena**, la app te avisa una vez y lo guarda para enviarlo más tarde: no se pierde nada.
- **Tu cuenta de Google solo la cambia el aparato donde tocaste:** lo que llega de otro aparato se guarda en este, pero no se vuelve a mandar a tu cuenta.

## Modo Fidelidad

- **Nuevo interruptor «Modo Fidelidad»** (Ajustes ▸ Sonido): envía el audio a la salida sin tocarlo. Mientras está activo no se aplican el ecualizador, Volumen seguro, el ancho estéreo, el compresor ni el fundido, y el tempo y el tono se quedan en 1,0×.
- **No hay ajustes que no hagan nada:** el ecualizador y el diálogo de tempo te dicen que el modo está activo, y tus ajustes vuelven al apagarlo.
- **Con un DAC USB en Android 14 o superior**, pide al sistema la salida bit-perfect cuando el DAC admite el formato exacto de la canción.

## Sonido

- **Sin fundido dentro de un álbum en orden:** si escuchas un álbum seguido y sin aleatorio, una pista pasa a la siguiente sin fundido, como en el disco.
- **Tempo y tono en tus archivos Hi-Res:** antes no hacían nada en un FLAC de 24 bits; ahora funcionan (salvo con el Modo Fidelidad).

## Para que lo pruebes y me cuentes

- No pude probarlo con dos aparatos reales ni con un DAC USB.
- **Con dos aparatos emparejados:** reordena una lista en uno y mira el otro; desconecta uno, cambia algo, vuelve a conectarlo.
- **Modo Fidelidad:** enciéndelo y pon una canción; si tienes DAC USB, mándame el registro: la línea BIT_PERFECT dice qué formato aceptó.
- **Un álbum en orden:** ¿pasa de una pista a otra sin fundido?
- Si algo falla, manda el registro desde Ajustes ▸ Registros con la hora.
