# Aura Hi-Res v2.0.40-beta1 — ventanas flotantes sólidas, micrófono que se ve, caché que se reutiliza y betas por el actualizador

BETA PRIVADA para el dueño (v2.0.40-beta1 / versionCode 999). Actualización directa sobre
cualquier versión de la identidad v2: mismo paquete (iad1tya.aura.music) y misma
firma (CN=Aura Hi-Res v2), conserva tus datos, tu sesión y tus ajustes.

## Ventanas flotantes con el estilo del menú de cuenta

- **Todas las ventanas flotantes, submenús, hojas inferiores y desplegables usan ahora la misma placa del menú «Aura Hi-Res Player»:** fondo sólido elevado, sin agujeros transparentes. En los Galaxy ya no se ven transparentes.

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

## Estabilidad

- **Cierre por memoria llena (tu reporte del 10-09) — ARREGLADO:** la radio automática podía saltar más de 50 canciones seguidas en segundos cuando YouTube no marcaba su tipo, y cada salto lanzaba precargas en paralelo hasta agotar la memoria. Ahora salta como máximo 3 seguidas y reproduce la siguiente.

## Música ↔ video como YouTube Music

- **El modo Video es una preferencia que se queda:** si eliges Video, una canción sin video suena con su portada y la siguiente que sí tiene video vuelve sola a video. Solo se apaga cuando cambias tú a Canción.

## Actualizador

- **Nuevo interruptor «Recibir versiones beta» en Ajustes ▸ Actualizaciones:** actívalo para que el actualizador te ofrezca las betas privadas (como esta). Apagado, solo recibes versiones estables como hasta ahora.
