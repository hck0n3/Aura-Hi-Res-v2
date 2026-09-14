# Aura Hi-Res v2.0.42 — Escuchar juntos que sí sigue al anfitrión, música primero y listas con IA más precisas

Versión estable (v2.0.42 / versionCode 1003). Actualización directa sobre cualquier versión de la
identidad v2: mismo paquete (iad1tya.aura.music) y misma firma (CN=Aura Hi-Res v2), conserva tus datos,
tu sesión y tus ajustes. Incluye Cast para enviar la música a tu TV o altavoz.

## Escuchar juntos

- **Los invitados reproducen lo que escucha el anfitrión:** la sala recibía una canción vacía, sin cola y en pausa, así que los dispositivos unidos no sonaban.
- **Arreglado el cierre al pasar el mando** o al cambiar de canción siendo anfitrión.

## Reproductor

- **La música va primero:** un video musical suena como música hasta que tocas «Video», y al pasar a la siguiente canción vuelve a música.

## Sonido

- **Todos los interruptores de Sonido vienen activados por defecto**, incluidos el compresor suave y el headroom automático.
- **La fila «Simulador Tidal» ya se activa al tocarla.**

## Scrobbling y recomendaciones

- **ListenBrainz ya acepta lo que suena:** antes rechazaba cada canción por una duración inválida.
- **Last.fm y ListenBrainz registran la escucha aunque el reproductor aún no conozca la duración.**
- **Enviar métricas de reproducción a Google viene activado por defecto** para afinar tus recomendaciones de YouTube Music; puedes apagarlo en Ajustes ▸ Scrobbling.

## Listas con IA

- **Nuevo modelo gratuito más preciso** para crear listas sin clave propia: respeta mejor el género, la época y los artistas pedidos.
- **Más tiempo para que la IA responda** antes de recurrir a una lista hecha sin IA.

## Modo sin conexión

- **Reproduce todo lo que ya tienes en el teléfono:** además de las descargas, ahora aparecen y suenan sin internet las canciones guardadas completas en la caché, tus MP3 exportados y tus videos exportados.

## Reproducción

- **Compatibilidad con el nuevo reproductor de YouTube:** algunas canciones fallaban porque YouTube cambió su reproductor; la app ya lo reconoce (se aplica también sin actualizar).

## Servicios

- **Se quitaron JioSaavn, Qobuz y Echo Canvas:** ya no aparecen en Estado de servicios ni en Cuentas. La música se sirve del catálogo principal y las portadas animadas de Apple Music y TIDAL Canvas.

## Actualizador

- **Ya no se queda descargando en bucle al llegar al 100 %:** si la app instalada tiene una firma distinta a la oficial, ahora se detiene y te explica que debes instalar Aura desde su página de GitHub, en vez de volver a descargar sin fin.
- **Si el teléfono no logra leer la firma del archivo, la actualización sigue:** Android igualmente comprueba la firma al instalar.
- **Un archivo ya descargado no se vuelve a bajar** al tocar de nuevo; el motivo de cualquier fallo queda en el registro de la app.

## Sincronización de tu biblioteca

- **La sincronización ya termina:** en bibliotecas grandes, cada vez que abrías la app se quedaba «Sincronizando tu biblioteca…» sin fin, porque intentaba volver a subir miles de canciones una por una. Ahora solo sube lo que de verdad falta y en tandas pequeñas.
- **Respeta la frecuencia que elegiste:** si pusiste «todos los días», sincroniza como mucho una vez al día al abrir la app (antes lo hacía cada 30 minutos y en cada arranque). Con la sincronización automática apagada, ya no sincroniza sola.

## Ventanas flotantes con el vidrio del mini reproductor

- **Todas las ventanas, menús y hojas usan el mismo vidrio del mini reproductor:** desenfoque real de lo que hay detrás, el mismo tono, borde fino y esquinas.
- **Siguen el color de la portada:** el vidrio toma el tono de lo que suena, igual que el resto de la app.
- **Menú del avatar, «Agregar a la lista», salida de audio y el botón «+» de Biblioteca salen desde abajo** como el menú «…» del reproductor.
- **«Crear lista» sale flotante y centrada,** también encima de «Agregar a la lista».
- **Con el teclado abierto las ventanas ya no se estiran hasta abajo:** conservan su tamaño y suben por encima del teclado.
- **Biblioteca: un solo botón «+»** que ya no tapa tus listas, con un menú donde cada opción tiene su icono (crear lista, lista con IA, importar desde Spotify o YouTube Music, migrar).
- **Botones, barras superiores y tarjetas con el mismo estilo en todas las pantallas;** los títulos ya no se enciman con el contenido al desplazarte.

## Reproductor

- **Estilo TIDAL:** portada grande de 336 dp en todos los estilos de portada, botón de «me gusta» junto al título, estado del motor (EQ · volumen seguro) entre los tiempos y botones inferiores redondos translúcidos.
- **Búsqueda rápida a pantalla completa:** el teclado se oculta al tocar un resultado o al buscar, para ver mejor.
- **Artistas:** las portadas de discos y canciones tienen la misma altura que las miniaturas de video.
- **Cast:** envía la reproducción a tu Chromecast, TV o altavoz compatible desde el botón de transmitir.

## Compartir y abrir enlaces

- **Compartir con song.link:** el enlace abre la canción en Spotify, Apple Music o la plataforma que use quien lo recibe (se puede apagar en Ajustes ▸ Contenido).
- **Abre enlaces de otras plataformas:** canciones, álbumes y listas de Spotify, Apple Music, Deezer, TIDAL, SoundCloud, Amazon Music y song.link se reconocen y se reproducen en Aura.
- **Enlaces de YouTube Music como propios:** la primera vez la app te ofrece abrirlos siempre con Aura.

## Cuentas

- **Inicio de sesión de YouTube y Spotify rehecho:** se abre en su propia ventana con la página completa visible y una barra superior ordenada; la sesión se guarda y se mantiene conectada.

## Ecualizador y sonido

- **Las bandas suenan exactamente como la curva dibujada:** filtros propios de alta precisión (campana, graves, agudos, paso bajo y paso alto) con Q por banda; el tipo de filtro se aplica al instante.
- **Verificación dentro del motor:** mide la ganancia real en cada frecuencia y el margen con ruido rosa, y lo compara con la curva.
- **Prueba audible de 5 segundos y «mantén presionado para escuchar sin EQ»** para comparar al momento.
- **Headroom automático y ancho estéreo** en Masterización; limitador de picos a −0.3 dBFS.
- **Protección del altavoz del teléfono activada por defecto:** baja el sub-grave solo cuando suena por el altavoz.
- **Perfil «Aura Hi-Res v2» actualizado:** 31 Hz +3, 62 Hz +4, 125 Hz +1, 250 Hz −1, 500 Hz 0, 1k 0, 2k +1, 4k +2, 8k +3, 16k +2. Si lo habías ajustado a mano, no se toca.
- **Compresor suave y dither de salida** con modelado de ruido, en Ajustes ▸ Sonido ▸ Masterización.
- **Ecualizador 100 % manual:** ajuste por banda (ganancia, Q y tipo) y modo paramétrico de hasta 16 bandas.

## Reproducción y datos

- **Enlace caducado (403):** la canción pide un enlace nuevo al instante en lugar de reintentar durante minutos.
- **Tus listas guardadas vuelven a cargar en Inicio y Cuenta.**
- **El caché de canciones guarda lo que escuchas:** una canción completa se reproduce desde el caché al instante, sin gastar datos y sin internet.
- **Ahorro de datos real:** audio Opus de ~70 kbps y portadas más livianas mientras está activo.
- **Calidad máxima sin Premium:** Opus ~160 kbps; si falta, AAC de 128 kbps.
- **Cola infinita anclada a lo que elegiste,** sin artistas fuera de tus gustos.
- **Transición (crossfade) de 8 s y preamp del ecualizador +2.2 dB por defecto;** tu elección se respeta.
- **Android Auto:** tocar un resultado de búsqueda reproduce ese resultado.

## Estabilidad

- **Arreglado el cierre al tocar «Ya me suscribí»** y en pantallas con campo de texto.
- **Arreglado el cierre por memoria llena** cuando la radio saltaba muchas canciones seguidas.
- **Audio que ya no se queda en silencio** con el nuevo motor de filtros.

## Interfaz

- **Ajustes en el engranaje de arriba** con ícono nuevo; la barra de abajo queda para navegar.
- **Micrófono animado y ventana «Escuchando…» siempre visible** en la búsqueda por voz.
- **Modo Video que se queda** como en YouTube Music.
- **Interruptor «Recibir versiones beta»** en Ajustes ▸ Actualizaciones.
