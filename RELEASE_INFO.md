# Aura Hi-Res v2.0.61-beta10 — Pedir música pone lo que pides, y la fe se mantiene con su estilo

Beta encima de la 2.0.61-beta9. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.
Incluye también el arreglo de la beta9: la canción que tocás en el buscador ya no hereda la cola de la
anterior.

## Pedir música: la canción que nombrás suena primero

- Pedías "Redimi2 Flipando" y sonaba "Blindao". La función solo reconocía una canción concreta si la
  frase también nombraba un género o un momento ("reggaeton", "para dormir"); "Redimi2 Flipando" no
  nombra ninguno, así que nunca se daba cuenta de que pedías ESA canción.
- Peor: para no repetirte listas, la función aparta durante 45 minutos lo que ya te sirvió. Como no
  sabía que la pedías por nombre, cada vez que la volvías a pedir la apartaba — por eso "nunca me pone
  lo que exactamente pido", por más veces que lo intentaras.
- Ahora, si la frase nombra una canción (el título tiene que coincidir, no solo el artista), esa
  canción va fija en el primer puesto, siempre, aunque la hayas pedido hace un minuto.
- Detrás va su propia radio de YouTube ("más como esto"), no una lista cualquiera del artista.
- Los remixes, versiones en vivo y otras subidas de esa misma canción ya no la siguen. Si querés el
  remix, pedilo: "Flipando remix" pone el remix.
- Pedir solo un artista ("canciones de Redimi2") sigue funcionando como antes: no fija ninguna canción
  al azar.

## Cola infinita: si el artista es cristiano, se mantiene la fe Y el estilo

- Antes "cristiano" era un solo carril: un merengue cristiano y una alabanza quedaban juntos, y la
  cola podía saltar de Geovanni Rios a Marcos Witt.
- Ahora el estilo va aparte. Si la canción desde la que sigue la cola es cristiana y se sabe su estilo
  (tropical, urbano, rock, regional…), la cola exige las dos cosas: cristiana y de ese estilo.
- Un artista que solo figura como "cristiano", sin más etiqueta, cuenta como alabanza: sale de una cola
  de merengue cristiano, pero sigue entrando en una de alabanza.
- Si el artista no es cristiano, todo sigue igual que antes: se mantiene el estilo que estás
  escuchando.
- "El culto", "coritos", "avivamiento" y "gloria a Dios" ahora también cuentan como señal de canción
  cristiana.
- El límite de los datos: el estilo sale de iTunes y del título. Si iTunes etiqueta a un artista de
  merengue cristiano solo como "cristiano" y su título no dice el estilo, la app no puede saber que es
  merengue.

## Fundido cruzado: revisado, sin cambios

- Revisé los 16 fundidos de tu último log: los 16 salieron bien, sin cortes ni silencios.
- El motor ya ancla el fundido a la última música real (no al silencio del final), precarga la
  siguiente canción y no se congela si la que entra tarda en cargar. No encontré nada roto, así que no
  toqué la curva que afinamos de oído.

## Necesito que confirmes en tu dispositivo

- Pedí "Redimi2 Flipando" (y repetilo): tiene que sonar Flipando primero, sin su remix detrás.
- Poné un merengue cristiano y dejá correr la cola: no debería saltar a alabanza.
- Compartime el log: por cada pedido aparece `MUSIC_REQUEST specific=true` cuando reconoció la
  canción, y en la cola `CTX_SINK ... christian + style=tropical` cuando aplica la regla nueva.
