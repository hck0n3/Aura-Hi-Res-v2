# Aura Hi-Res v2.0.11 — tu biblioteca al instante tras iniciar sesión

Primera versión estable de la nueva identidad **Aura Hi-Res v2**. Es una app
nueva: se instala AL LADO de la Aura anterior (no la reemplaza ni toca sus
datos), con certificado nuevo dedicado y reproducción verificada. Incluye la
licencia/suscripción activa y el actualizador interno: las próximas versiones
te llegarán desde la propia app.

---

## Novedades

- **Biblioteca al instante tras iniciar sesión:** al entrar con tu cuenta de YouTube/YouTube Music, tus canciones, artistas y listas empiezan a aparecer desde la primera página, sin esperar a que termine toda la sincronización (antes la biblioteca podía quedar vacía varios minutos).
- **Aviso de sincronización:** un indicador "Sincronizando tu biblioteca…" en la pantalla de inicio te avisa mientras tu cuenta se está cargando.
- **Identidad nueva (Aura Hi-Res v2):** certificado de firma nuevo y dedicado; la reproducción de streams queda resuelta de forma permanente (el certificado antiguo había quedado marcado y ya no funcionaba).
- **Licencia y suscripción activas:** prueba gratis de 3 días desde el primer inicio; después, suscripción. Con un periodo de gracia offline de 3 días si te quedas sin conexión.
- **Actualizador interno:** la app comprueba nuevas versiones automáticamente (cada 6 horas y al abrir). Cuando haya una versión más nueva, te avisa y la instala desde la propia app.
- **Interfaz nueva (beta):** diseño renovado del reproductor, inicio, biblioteca, buscar, ajustes, cola y más, con el interruptor "Interfaz nueva" en Ajustes para volver a la clásica cuando quieras.

## Reproducción

- El audio ya no se queda pausado tras una interrupción: al terminar una llamada, un dictado por voz o cualquier pérdida transitoria de foco, la canción se reanuda sola.
- Las canciones empiezan más rápido: la app ya no insiste en un extractor que falla en tu red y pasa directo al que funciona; la precarga de la siguiente canción respeta su vigencia.
- Menos reintentos en cascada cuando YouTube está saturado: las sincronizaciones se agrupan en una sola petición para no disparar los límites de la red.
- Crossfade afinado: las transiciones entre canciones respetan la curva y duración que elijas en Ajustes.
- Exportar como video vuelve a funcionar: usa el mismo resolutor vivo que el modo video dentro de la app.

## Ecualizador y sonido

- Ya no hay cortes al entrar o salir del ecualizador.
- Girar el celular con música sonando ya no produce micro-cortes (interfaz nueva).
- Los faders del ecualizador se mueven con un solo dedo: la barra sigue el dedo al instante, salta al tocar y la página de fuera no se mueve.
- El medidor FFT del ecualizador funciona; en Android 14 o superior pide el permiso de micrófono (solo para leer tu propia reproducción; nunca graba).
- Los colores del ecualizador y de toda la interfaz siguen a la portada de la canción en tiempo real.
- Presets nuevos: AR-SOUND, AR-SOUND V2 y Aura Hi-Res (este último es el preset por defecto en instalaciones nuevas).
- Preamplificador por defecto en +2.0 dB.
- Animaciones más fluidas: menos trabajo repetido por fotograma mientras suena música.

## Fluidez

- Animaciones más fluidas en toda la app (inicio, novedades, biblioteca, buscar y scroll): el mini-reproductor de abajo ya no mantiene animaciones invisibles trabajando por detrás en cada fotograma.
- La rotación de portadas (si la tienes activada) solo anima la portada que está sonando.
- Las portadas animadas de los álbumes se pausan al pausar la música (ya no siguen decodificando video con la música en pausa).

## Datos y batería

- Las portadas ya descargadas no se vuelven a descargar: el caché de imágenes sobrevive a la presión de memoria rutinaria.
- La descarga automática al dar "me gusta" solo ocurre en redes sin límite de datos (no gasta tus datos móviles); tus descargas manuales siguen como siempre.

## Interfaz

- En dispositivos Samsung el desenfoque de ventanas no está disponible de fábrica: las placas ahora usan un fondo opaco sólido en vez de verse transparentes a medias.
- Las portadas de los videos rellenan su carátula (sin bandas negras); las portadas de álbum conservan su aspecto de siempre.

## Conocidos

- El inicio de sesión puede requerir tocar "Accede" dentro de la pantalla de YouTube y luego volver. Se sigue trabajando para que sea automático.
- Algunas canciones pueden arrancar cortadas de vez en cuando; si te pasa, prueba a desactivar "Saltar segmentos sin música" en Ajustes ▸ Reproductor y avísanos.
- Bajo saturación fuerte de YouTube (bot-check) alguna canción puede tardar o reintentarse; normalmente se resuelve solo en segundos.

## Cómo actualizar

- Si tienes una beta de Aura Hi-Res v2 (terminación .dev): esta estable se instala AL LADO, no la pisa. Son apps distintas: aquí empiezas con datos frescos (biblioteca vacía y prueba gratis de 3 días).
- Si tenías la Aura antigua (iad1tya.echo.music): sigue intacta; esta v2 vive a su lado.
