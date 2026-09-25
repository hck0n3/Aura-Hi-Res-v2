# Aura Hi-Res v2.0.61-beta2 — Arregla la carrera de "dos colas peleando"

Beta encima de la 2.0.61-beta1. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## La causa real de lo que reportaste

- Confirmado con tus logs: cuando cambiabas de canción/estilo (urbano → Elvis Crespo, salsa → Manny
  Montes), a veces la radio VIEJA (la que sonaba antes) terminaba de cargar DESPUÉS de que ya habías
  cambiado, y pisaba la cola nueva con canciones del estilo anterior — como dos colas peleando por el
  reproductor, y ganaba la equivocada.
- Mismo problema al reproducir manualmente la primera canción de una playlist por segunda vez: dos
  pedidos de esa misma lista se cruzaban, y el más viejo podía llegar después y dejar la cola
  desincronizada. Ahora una cola nueva siempre cancela a la anterior, sin importar cuál tarda más en
  cargar. Esto aplica igual a álbumes, EPs y playlists — no es un caso especial de playlists.

## Además

- La cola infinita ahora recuerda, entre reinicios de la app, qué canciones ya sonaron después de un
  álbum/playlist/EP/single — así que volver a ponerlo no repite lo mismo de la vez anterior.

## Necesito que confirmes en tu dispositivo

Probá exactamente los casos que reportaste: cambiar de estilo/canción varias veces seguidas, y
reproducir la primera canción de una playlist dos veces. Si esto queda resuelto en tu prueba, avisame
y armamos la estable.
