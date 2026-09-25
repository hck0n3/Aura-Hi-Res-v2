# Aura Hi-Res v2.0.60-beta1 — Arreglo real de la cola infinita inteligente en álbumes

Beta encima de la 2.0.59 estable. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Cola infinita inteligente

- **La continuación de un álbum ya no mezcla géneros sin relación.** La causa: reproducir un álbum
  desde la tarjeta con botón de Play (Inicio, Biblioteca, Buscar, Artista), desde el menú ⋮ ▸
  "Reproducir", o desde una tarjeta de Novedades/Radar de novedades, dejaba la continuación entera en
  manos de la radio nativa de YouTube sin ningún filtro propio — el motor de contexto/género de Aura
  (el mismo que ya protege playlists y el álbum abierto desde su propia pantalla) nunca llegaba a
  activarse por esas vías. Ahora sí, sea cual sea el botón que uses para arrancar el álbum.
- Como consecuencia también debería notarse menos repetición de canciones ya escuchadas al continuar
  desde esas mismas vías.

Si después de esta beta seguís viendo canciones repetidas, avisame — hay una sospecha adicional (memoria
anti-repetición que se pierde si el sistema mata la app a mitad de álbum) que necesita confirmarse con
un `app.log` real, no algo que se pueda resolver a ciegas.
