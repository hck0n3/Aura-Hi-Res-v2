# Aura Hi-Res v2.0.61-beta8 — La paginación de la radio ya no se queda sin género por culpa de otras

Beta encima de la 2.0.61-beta7. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Lo que confirmé con tu prueba aislada

- Probaste 4 artistas seguidos, esperando 20 segundos entre cada uno, con la app recién cerrada del
  todo: el 2do, el 3ro y el 4to fallaron — peor que la prueba anterior, donde solo fallaba el 4to.
- Confirmé en el log que la beta7 sí arregló lo que se propuso arreglar (el PRIMER lote de radio de una
  canción suelta), pero dejó un segundo punto con el MISMO problema sin tocar.

## La causa que encontré

- La cola infinita no se arma toda de una vez: se va extendiendo por partes cada vez que quedan pocas
  canciones por delante. Esa extensión tiene su propio paso de "averiguar el género para seguir en el
  mismo estilo" — separado del que ya arreglé en la beta7.
- Ese paso compartía lote con las canciones nuevas que trae YouTube para seguir la cola, con el mismo
  riesgo: una racha de fallos entre esas nuevas podía tumbar el lote antes de que le tocara el turno al
  artista que había que mantener — dejando esa extensión de la cola sin ningún filtro de estilo.
- Esto explica por qué empeoraba con cada artista probado: los fallos se van acumulando durante toda la
  sesión de la app, así que mientras más pruebas seguidas hacés, más fácil que la racha se dispare de
  nuevo antes de tiempo.

## Qué cambié

El artista que hay que mantener durante esa extensión de cola ahora va en su propio turno, separado de
las canciones nuevas — mismo arreglo que la beta7, aplicado al segundo punto que se me había quedado
sin tocar.

## Necesito que confirmes en tu dispositivo

Repetí exactamente la misma prueba: 4 (o más) artistas seguidos, esperando a que cada uno continúe solo
antes de pasar al siguiente, y fijate si todos se adaptan ahora o si alguno sigue fallando.
