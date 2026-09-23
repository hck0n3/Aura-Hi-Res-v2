# Aura Hi-Res v2.0.49-beta1 — Sin conexión de verdad, cola fiel al álbum y pedir música con voz

Beta encima de la 2.0.48. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.
Ocho rondas de reportes, cada una auditada por separado antes de pasar a la siguiente.

## Reproducción

- **La cola de un álbum de Novedades ya no se corta a la primera canción.** Un álbum recién salido podía tener en tu teléfono solo su primera canción guardada de antes (por ejemplo, si Novedades ya te la había mostrado suelta); la app daba por hecho que ya tenía el álbum completo y nunca pedía el resto. Al terminar esa única canción, la cola infinita entraba en escena mezclando artistas relacionados — lo que se sentía como "saltar a otro artista a mitad del álbum". Ahora se compara cuántas canciones hay guardadas contra las que el álbum realmente tiene antes de decidir que ya está completo.
- **La biblioteca de un artista ya no se repite a los 15-20 temas.** Si tenías Ahorro de datos activado, la pantalla del artista mostraba y encolaba canciones con vídeo que el reproductor descarta al armar la cola real — la cola terminaba siendo mucho más corta de lo que parecía, y al acabarse volvía a sonar desde el principio. Ahora la pantalla filtra exactamente igual que el reproductor.
- **El ecualizador ya no te cambia el volumen.** Mover una banda subía o bajaba el volumen general por culpa de "Headroom automático", un ajuste que recorta el preamplificador según el mayor realce activo. Viene apagado por defecto (sigue disponible en Ajustes → Sonido para quien quiera esa protección contra saturación).

## Sin conexión

- **El modo sin conexión automático ahora funciona en toda la app.** La detección de "no hay red" ya existía, pero Inicio, Biblioteca y Buscar en la interfaz clásica solo miraban tu interruptor manual — nunca se enteraban de que se había ido la conexión. Ahora las cuatro pantallas reaccionan igual.

## Pedir música

- **Reproduce de inmediato y completa 25 canciones en segundo plano.** Antes esperabas a que terminaran de buscarse 10 canciones para empezar a sonar. Ahora arranca con un primer lote chico apenas está listo, y sigue completando hasta 25 sin que tengas que esperar — la cola infinita hereda el estilo de las 25, no solo de las primeras.
- **El mismo cristal translúcido que las demás ventanas.** La hoja de "pedir música" tenía fondo opaco; ahora usa el mismo efecto que el buscador y los demás menús que suben desde abajo.
- **Dictado por voz.** Un micrófono junto al campo de texto: en cuanto terminas de hablar, la petición se envía sola, igual que en la búsqueda normal.

## Links de otras apps

- **Un link de Spotify (o similar) ya no dice "no encontrado" cuando el contenido sí está en YouTube Music.** Se agregó una segunda pasada de búsqueda para canciones que YouTube Music clasifica como vídeo, se limpian mejor los títulos con "(Live)", "(Radio Edit)" o "Single Version" que antes hundían la comparación, y una lectura más robusta de la página de origen evita mandarle al comparador un texto de "artista" que en realidad era basura.

## Playlists propias

- **Deslizar para eliminar una canción, de una vez.** El gesto ya existía pero estaba encadenado al candado de "bloquear edición" (pensado para el arrastre, no para esto) y venía apagado por defecto — así que nunca se veía. Ahora es independiente de ese candado, viene activado, solo aparece en tus propias playlists editables, y el fondo se pone rojo con un ícono de papelera mientras deslizas.

## Interfaz

- **El botón de Cast responde al primer toque.** Su área de contacto real seguía por debajo del mínimo recomendado en ambos reproductores (y en el clásico ni siquiera coincidía con el círculo dibujado) — ahora mide 48dp en los dos.

## Nota técnica (sin verificar aún en dispositivo)

- **Sospecha de auto-suscripción al dar "me gusta"**: no se encontró en el código ninguna llamada que suscriba a un artista desde el "me gusta" o "no me gusta" de una canción — son caminos completamente separados del botón de suscribirse (ese sí suscribe de verdad, a propósito). Se agregaron líneas de registro (sin títulos ni nombres) en los cinco puntos relevantes para que, si vuelve a pasar, el próximo registro compartido desde Ajustes ▸ Registros traiga la evidencia real.
