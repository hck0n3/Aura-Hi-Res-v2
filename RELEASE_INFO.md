# Aura Hi-Res v2.0.53-beta1 — Me gusta que sabe si ya lo diste, álbum instantáneo, y pedir música más listo

Beta encima de la 2.0.52. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Deslizar canciones

- **El deslizar de "me gusta" ahora sabe si ya lo diste.** Antes deslizar a la izquierda para "no me
  gusta" no desmarcaba el corazón de una canción que ya tenías marcada — eso se arregló, y de paso se
  simplificó el gesto: ahora un lado siempre alterna me-gusta/no-me-gusta según el estado real de la
  canción (el ícono cambia solo), y el otro lado siempre abre "agregar a una playlist".

## Álbumes

- **Un álbum que ya viste en esta sesión abre al instante.** Antes Álbum era la única pantalla (Artista
  y Playlist ya lo hacían) que no recordaba nada entre visitas — volver a entrar a un álbum que abriste
  hace un minuto lo recargaba todo desde cero. La primera vez que ves un álbum nuevo sigue tardando lo
  normal (eso es la red, no se puede acelerar), pero las siguientes visitas en la misma sesión son
  instantáneas.

## Pedir música

- **Ya no ignora el género cuando también pides una época.** "Reggae de los 90" devolvía éxitos
  genéricos de los 90 sin nada de reggae — la petición completa se reemplazaba por una plantilla fija.
  Ahora se combinan los dos.
- **Repetir una petición, de un toque.** Un chip con tus últimas peticiones aparece al abrir la
  ventana — tocarlo la vuelve a pedir sin escribir nada, y como nunca repite exactamente las mismas
  canciones que ya sonaron, sirve también como "otra tanda" de la misma idea.
- **"Lo que suena ahora" ya funciona de verdad.** Antes esa frase se buscaba tal cual (con poco
  resultado); ahora se responde con los charts reales de tendencias de YouTube Music.

## Verificado sin cambios

- **La cola infinita inteligente**: se auditó a fondo cómo continúa la reproducción al terminar un
  álbum, una playlist o una canción suelta. El algoritmo es preciso y no improvisa — la única pieza que
  alguna vez metió un artista al azar ya se había quitado en una ronda anterior por pedido tuyo, y sigue
  sin usarse. Lo único genuinamente más débil es la continuación de una canción suelta (tiene menos
  información que un álbum o playlist completos para trabajar) — no es un error de código, es un límite
  real de datos, así que no se tocó a ciegas.
