# Aura Hi-Res v2.0.61-beta5 — Cola del buscador + filtro de género en canción suelta

Beta encima de la 2.0.61-beta4. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Lo que encontré en tu log

- **Buscador**: si la canción que tocabas sufría un tropiezo momentáneo de reproducción justo mientras
  se armaba su cola de continuación en segundo plano, la cola nueva se descartaba en silencio y el
  reproductor se quedaba pegado a la cola vieja de siempre. Tocar resultados del buscador uno tras otro
  era exactamente el patrón que más lo disparaba. Ya no se descarta: la cola se instala igual.
- **"Relacionado" en canción suelta**: mi propio filtro de género de la ronda pasada usaba un umbral
  pensado para álbumes/playlists grandes, y con lotes chicos de radio (5-9 candidatos, como en tu log)
  casi nunca llegaba a activarse de verdad — bajado al mismo umbral que ya usa la cola infinita normal.

## Sobre el botón "actualizar"

No pude confirmar por lectura de código cuál de los 4 lugares que abren el reproductor a pantalla
completa por sí solo es el responsable — ninguno encaja con que no tocaste video. Agregué un registro
puntual (sin datos tuyos) en los 4, así que si volvés a compartir el `app.log` después de que te pase de
nuevo, ya tengo la prueba en vez de seguir preguntando.

## Necesito que confirmes en tu dispositivo

Probá lo mismo de siempre: tocar canciones distintas desde el buscador una tras otra, cambiar de estilo
varias veces seguidas, y si te vuelve a pasar lo del botón "actualizar", compartime el log de esa sesión.
