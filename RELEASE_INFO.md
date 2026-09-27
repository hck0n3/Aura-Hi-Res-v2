# Aura Hi-Res v2.0.61-beta6 — La cola vieja ya no puede reaparecer sola

Beta encima de la 2.0.61-beta5. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Lo que vi con tus pruebas

- Viste, con tus propios ojos, la cola nueva armarse bien y, minutos después, verse reemplazada de golpe
  por la cola de la canción anterior, sin tocar nada.
- Es el mismo mecanismo detrás de "estoy escuchando urbano y de la nada sale salsa": confirmé en tu log
  que la cola vieja tomó el control del reproductor y siguió sonando 25 minutos seguidos.

## La causa que encontré

- La transición suave entre canciones (crossfade) prepara un segundo reproductor por adelantado y agenda
  un cronómetro basado en cuánto le quedaba a la canción VIEJA para terminar.
- Ese cronómetro nunca se cancelaba al arrancar una cola nueva — así que, cuando vencía por su cuenta,
  la transición se disparaba sola y publicaba la cola vieja como si fuera la actual.
- Ahora se cancela ese cronómetro (y se suelta el reproductor de repuesto) apenas arrancás una cola
  nueva, siempre.

## Necesito que confirmes en tu dispositivo

Repetí las pruebas de siempre: buscar una canción y reproducirla mientras suena otra cosa, cambiar de
estilo varias veces seguidas, y fijarte si "a continuación" se mantiene estable esta vez (sin que
reaparezca la cola anterior por su cuenta).
