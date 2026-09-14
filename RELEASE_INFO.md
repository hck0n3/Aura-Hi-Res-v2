# Aura Hi-Res v2.0.40-beta2 — sin esperas de 4 minutos ante enlaces caducados y listas guardadas que vuelven a cargar

BETA PRIVADA para el dueño (v2.0.40-beta2 / versionCode 1000). Actualización directa sobre
cualquier versión de la identidad v2: mismo paquete (iad1tya.aura.music) y misma
firma (CN=Aura Hi-Res v2), conserva tus datos, tu sesión y tus ajustes.

## Nuevo en beta2 (corregido a partir del registro de tu teléfono)

- **Enlace de canción rechazado por YouTube (403):** antes la app lo reintentaba unas 55 veces durante más de 4 minutos y la canción acababa en error. Ahora pide un enlace nuevo al instante (máximo 3 intentos por canción).
- **Tus listas guardadas vuelven a cargar en Inicio y Cuenta:** YouTube Music cambió el formato de esa página y la app no la reconocía.

## Incluido desde beta1

## Ventanas flotantes con el estilo del menú de cuenta

- **Todas las ventanas flotantes, submenús, hojas inferiores y desplegables usan ahora la misma placa del menú «Aura Hi-Res Player»:** fondo sólido elevado, sin agujeros transparentes. En los Galaxy ya no se ven transparentes.

## Reproductor

- **Portada tamaño TIDAL:** en el reproductor abierto la portada ahora ocupa casi todo el ancho (margen fino de 20 dp, antes 32 dp), con la sombra suave de tarjeta y tu radio de esquinas de siempre.

## Simetría de portadas

- **Todas las portadas de discos, canciones, EP, singles, playlists y podcasts tienen la misma altura que las miniaturas de video:** en las filas de Inicio, Novedades y Artista, y en las listas. Videos y portadas quedan alineados a la misma línea.

- **Botón Música/Video legible en modo video:** en modo video el botón para volver a música se veía como un parche blanco (texto blanco sobre el color de acento claro). Ahora usa el color de contraste del acento y siempre se lee.

## Barra de navegación

- **Se quitó «Configuración» de la barra de abajo:** Ajustes queda solo en el engranaje de arriba a la derecha, que ahora tiene un ícono nuevo más limpio y profesional.

## Búsqueda por voz

- **El micrófono se anima mientras te escucha:** late en color de acento desde que lo tocas hasta que termina de reconocer.
- **La ventana «Escuchando…» ya se ve:** antes se abría detrás de la pantalla de búsqueda; ahora aparece siempre encima (también desde el reproductor).

## Ecualizador

- **Perfil «Aura Hi-Res v2» actualizado:** 31 Hz +3, 62 Hz +4, 125 Hz +1, 250 Hz −1, 500 Hz 0, 1k 0, 2k +1, 4k +2, 8k +3, 16k +2.
- Si tu ecualizador estaba en el v2 anterior, pasa solo al nuevo; si lo habías ajustado a mano, no se toca.

## Caché de escucha

- **El caché de canciones por fin GUARDA lo que escuchas (arreglado de raíz):** el descargador por partes le decía al reproductor «tamaño desconocido», y con eso el reproductor no escribía NI UN BYTE en el caché — por eso en Ajustes ▸ Almacenamiento el caché de canciones salía vacío aunque estuviera en ilimitado. Ahora informa el tamaño real (mismo modelo que SimpMusic) y el caché se llena mientras suena (verificado en tu S26: 4 KB → 8 MB en 45 s).
- **Una canción ya escuchada completa se reproduce desde el caché sin volver a descargarla:** ni siquiera consulta a YouTube; empieza al instante y sin gastar datos.
- **Sin internet también suena:** una canción completa en caché se reproduce sin conexión (incluso con Modo sin conexión activado), igual que una descargada.
- **La precarga ya no gasta datos en canciones que ya tienes en caché:** la app no vuelve a pedir su enlace a YouTube.
- **La barra de progreso se ve cargada al 100 %** cuando la canción está completa en caché o descargada.
- **Los videos también primero desde el caché:** un video que ya viste completo se reproduce desde el disco sin volver a pedirlo a YouTube (también sin internet). Las portadas ya se servían primero desde su caché de 2 GB.

## Android Auto

- **Tocar un resultado de búsqueda reproduce ESE resultado:** antes, al tocarlo, la app repetía la búsqueda y si YouTube devolvía otra lista empezaba por el primer resultado, no por el que elegiste.

## Masterización (Ajustes ▸ Sonido ▸ Masterización — todo se activa y desactiva a tu gusto)

- **Compresor suave (pegamento):** compresión 2:1 muy sutil justo antes del limitador; une la mezcla sin subir el volumen. Apagado por defecto.
- **Dither de salida:** dither triangular con modelado de ruido al convertir el audio a 16 bits (tu S26 sale en 16 bits): pasajes suaves más limpios. Encendido por defecto.
- **Proteger el altavoz del teléfono:** baja el sub-grave solo cuando suena por el altavoz; al conectar audífonos o Bluetooth se quita solo. Apagado por defecto.
- **El limitador ahora también protege las pistas mono** (antes solo actuaba en estéreo).
- Ya tenías y siguen igual: limitador de picos, crossfeed y ancho estéreo (en Audio espacial del ecualizador), filtros shelf/campana con Q por banda y pre-ganancia.

## Valores por defecto de audio (se aplican a todos una vez con esta actualización)

- **Duración de la transición (crossfade): 8 segundos.** Además se corrigió que las instalaciones nuevas quedaban en 5 s.
- **Preamp del ecualizador: +2.2 dB.**
- Después puedes cambiar ambos cuando quieras; tu elección se respeta.

## Ecualizador 100 % manual

- **Modo barras:** nuevo panel «Ajuste manual por banda»: eliges la banda y ajustas su ganancia en pasos de 0.1 dB, su ancho (Q de 0.3 a 10) y su tipo de filtro (Auto, Campana, Graves, Agudos). Si no tocas nada, suena igual que antes.
- **Modo paramétrico:** hasta 16 bandas libres (antes 10), cada una con frecuencia, Q, ganancia y tipo.

## Ahorro de datos (verificado y completado)

- **Ahora sí baja el audio:** con Ahorro de datos activado se usa el Opus de ~70 kbps en vez del de 160 kbps (antes el audio no ahorraba nada). Al apagarlo vuelve a máxima calidad.
- **Portadas más livianas:** con Ahorro de datos, la portada del reproductor se descarga a 544 px en vez de 1200 px.
- Ya funcionaba: oculta videos y portadas animadas, no precarga canciones (ni la del crossfade), no busca letras solo, pausa Last.fm/ListenBrainz y limita el video a 360p con datos móviles.

## Calidad de audio

- **Verificado en tu S26: siempre Opus a la máxima calidad sin Premium (itag 251, ~160 kbps).** Las 264 canciones registradas en tu app y todo tu caché son Opus 251.
- **Respaldo corregido:** si una canción no trae su enlace Opus, la app ya no baja a AAC de 48 kbps (itag 139, mal etiquetado como Opus): usa primero el AAC de 128 kbps.

## Cola infinita inteligente

- **Ya no improvisa:** se quitó la cuota que metía a propósito artistas fuera de tus gustos en la cola automática.
- **Siempre anclada a lo que elegiste:** si empezaste desde una canción, cada nueva tanda sale de ESA canción (antes salía de la última que sonó, que ya era de la radio, y se iba desviando).
- **Estudia el álbum o la playlist completa:** las semillas salen de todo el contenido (hasta 5, antes 4) y la canción que suena solo cuenta si pertenece a tu lista.

## Estabilidad

- **Cierre por memoria llena (tu reporte del 10-09) — ARREGLADO:** la radio automática podía saltar más de 50 canciones seguidas en segundos cuando YouTube no marcaba su tipo, y cada salto lanzaba precargas en paralelo hasta agotar la memoria. Ahora salta como máximo 3 seguidas y reproduce la siguiente.

## Música ↔ video como YouTube Music

- **El modo Video es una preferencia que se queda:** si eliges Video, una canción sin video suena con su portada y la siguiente que sí tiene video vuelve sola a video. Solo se apaga cuando cambias tú a Canción.

## Actualizador

- **Nuevo interruptor «Recibir versiones beta» en Ajustes ▸ Actualizaciones:** actívalo para que el actualizador te ofrezca las betas privadas (como esta). Apagado, solo recibes versiones estables como hasta ahora.
