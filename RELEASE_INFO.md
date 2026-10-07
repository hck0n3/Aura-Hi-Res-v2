# Aura Hi-Res v2.0.64-beta4 — Menos batería, menos errores, Media3 nuevo y audio en 32 bits opcional

Beta encima de la 2.0.64-beta3. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Batería y temperatura

- **El arranque ya no prepara el descifrador de YouTube si no lo va a usar.** Antes lo construía en cada
  arranque aunque no sirviera para nada; ahora solo cuando de verdad hace falta. Menos procesador y memoria.
- **El reproductor Aura ya no se redibuja entero dos veces por segundo** mientras suena música en cualquier
  pantalla, y la pantalla del ecualizador ya no se redibuja en cada fotograma con el medidor encendido.
- **Las tareas en segundo plano esperan a que haya batería** (recomendaciones, Radar de novedades, Last.fm y
  la sincronización de Spotify).

## Reproducción y actualizaciones

- **Las canciones cargan por el camino rápido aunque YouTube haya cambiado su reproductor.** Mientras no
  hay configuración verificada del reproductor nuevo, la app ya no fuerza un método que fallaba en cada
  canción antes de pasar al siguiente.
- **Actualizaciones más ligeras en móviles modernos:** a partir de la próxima versión la app descarga un
  APK de unos 46 MB en vez de 87 (los televisores y equipos antiguos siguen recibiendo el completo).

## Sonido

- **Nuevo: «Procesado en 32 bits (experimental)»** en Ajustes ▸ Sonido, apagado por defecto. Con él, el
  ecualizador y la salida trabajan en coma flotante también con Opus, AAC y MP3 (como ya pasaba con el
  Hi-Res). Se nota con un DAC USB o auriculares LDAC de 24 bits. Vuelve solo a 16 bits si cambias tempo o
  tono o estás en Escuchar juntos.
- **Motor de reproducción actualizado (Media3 1.11.1)**: corrige cierres del servicio de música, la
  navegación por Bluetooth en Android 16/17, un bloqueo con Android Auto y la carátula borrosa en la
  notificación. Los botones de la notificación, Android Auto, Bluetooth y la pantalla de bloqueo siguen
  funcionando igual.

## Canciones no disponibles

- **Tampoco aparecen en la búsqueda, en las sugerencias ni en las páginas de artista.**

## Menos cierres y errores

- **Un fallo al guardar en la base de datos (por ejemplo, memoria llena) ya no cierra la app** en mitad de
  una canción.
- **La cola guardada ya no se pierde** si el archivo de estado se estropea, y se guarda de forma segura.
- **Exportar una copia, una playlist (JSON/CSV) o una canción ya no dice «creada» cuando falló.**
- **Se corrigieron varios cierres posibles** en la cola, al reordenar playlists y en los menús.

## Registro de diagnóstico

- **Guarda el doble de historia** y la pantalla de Registros va más fluida (muestra lo más reciente;
  «Compartir» envía todo).
- **Ningún error se queda fuera:** los que antes solo iban al sistema ahora quedan en el registro, sin
  títulos, artistas ni identificadores tuyos.

## Novedades

- **Las notas de cada versión ya se ven completas** en la pantalla de Novedades (antes se cortaba cada
  punto en su primera línea).
