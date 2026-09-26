# Aura Hi-Res v2.0.61-beta4 — La causa real de "la cola no se adapta hasta la segunda vez"

Beta encima de la 2.0.61-beta3. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## La causa que encontré

- Confirmé el mecanismo exacto: cuando reproducías una canción nueva (suelta, desde el buscador o
  desde Inicio) mientras la radio de lo que sonaba ANTES todavía estaba resolviendo en segundo plano,
  esa radio vieja bloqueaba en silencio que la canción nueva armara su propia continuación.
- Por eso la cola se quedaba "pegada" a lo anterior — y por eso funcionaba bien recién la SEGUNDA vez:
  para entonces la radio vieja ya había terminado y dejado de bloquear.
- Esto es distinto de (y se suma a) los arreglos de las betas anteriores — encontrado leyendo el
  código a fondo, no adivinado.

## Necesito que confirmes en tu dispositivo

Probá exactamente lo que veníamos viendo: reproducir una canción suelta desde el buscador o Inicio
mientras algo más sonaba antes, cambiar de estilo varias veces seguidas, y tocar dos veces la primera
canción de una playlist. Si esto queda resuelto, avisame y armamos la estable.
