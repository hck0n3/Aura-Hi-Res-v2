# Aura Hi-Res v2.0.61-beta7 — La canción ancla ya no se queda sin género por culpa de otras

Beta encima de la 2.0.61-beta6. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Lo que confirmé con tus capturas

- Buscaste "Chichi Peralta", tocaste "Nuestra Canción" de Elvis Crespo (merengue) — y "a continuación" y
  "reproducción automática" salieron 100% cristiano/alabanza, justo después de varias pruebas seguidas con
  música cristiana.
- Confirmé en el log: la búsqueda de género de la canción que tocaste SÍ corrió, pero terminó sin
  resultado — así que mi filtro no tuvo con qué comparar y dejó pasar lo que trajo YouTube sin revisar.

## La causa que encontré

- La búsqueda de género de una canción se cancela entera si varias búsquedas fallan seguidas (protección
  normal para no gastar batería/red en una mala racha).
- La búsqueda de la canción que tocaste compartía ese mismo lote con las "relacionadas" que trajo
  YouTube — y esas eran de artistas cristianos de nicho, con buena chance de no tener ficha completa.
  Bastaron algunos fallos entre ellas para cancelar el lote antes de que le tocara el turno a tu canción.
- Esto también confirma que el contenido cristiano mezclado no es al azar: YouTube te devuelve
  recomendaciones contaminadas por tu historial reciente de reproducción, y el filtro de Aura —pensado
  justo para corregir eso— se quedó ciego por esta carrera.

## Qué cambié

La búsqueda de género de la canción que tocás ahora va en su propio turno, separada de las
"relacionadas" — así una racha de fallos entre esas nunca la deja sin resolver.

## Necesito que confirmes en tu dispositivo

Repetí la prueba: buscá y reproducí una canción de un estilo, justo después de haber escuchado otro
estilo bien distinto (como cristiano → secular), y fijate si "a continuación" ahora sí coincide con lo
que tocaste.
