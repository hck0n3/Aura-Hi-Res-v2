# Aura Hi-Res v2.0.57-beta1 — Arregla que el wifi cortara la música, pedir música más estricto

Beta encima de la 2.0.56. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## ⚠️ Arreglo urgente

- **El wifi ya no corta la reproducción de canciones online.** La 2.0.56-beta1 dejaba de reproducir
  cualquier canción que no estuviera en caché, descargada o guardada localmente en cuanto se activaba
  el wifi (con datos móviles sí funcionaba). La causa: al preferir wifi, el teléfono apaga la conexión
  de datos que ya no necesita, y eso se leía por error como "sin internet en general" aunque el wifi
  siguiera perfectamente conectado. Corregido en dos capas, para que no vuelva a pasar por una causa
  parecida: la señal de conectividad ahora sigue todas las redes activas a la vez, y el freno que
  bloquea la reproducción sin red pregunta directo al sistema en el momento, en vez de confiar en un
  estado que puede quedar desactualizado.

## Pedir música

- **El idioma y el tema cristiano/gospel que pedís ahora se respetan siempre, no solo cuando conviene.**
  Pedir "trap cristiano en inglés" podía devolver resultados en español, y "reggae cristiano" podía
  traer artistas que no son cristianos — el idioma y el tema religioso antes solo sumaban puntos a un
  resultado, no lo descartaban si faltaban. Ahora son una condición dura: si los pedís explícitamente,
  tienen que cumplirse.
- **Pedir una canción o artista específico ya no llena la cola con 20-25 copias de lo mismo.** Distintas
  subidas de la misma canción a YouTube ("Video Oficial", "Lyrics", "Audio") contaban como canciones
  distintas. Ahora se detectan como la misma, y si la cola queda corta, se completa con canciones reales
  del mismo artista en vez de repetir lo poco que había.

## Enlaces externos

- **Spotify, Amazon Music y demás: menos "no encontrado" por artistas invitados.** Comparar todos los
  artistas acreditados como una sola cadena de texto castigaba cualquier canción con un featuring que
  las dos plataformas no acreditan exactamente igual — muy común en reggaetón y bachata. Ahora se
  compara cada artista por separado y se usa la mejor coincidencia.
