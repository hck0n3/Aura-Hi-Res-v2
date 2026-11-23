# Aura Hi-Res v2.0.69-beta3 — beta privada: suscripciones que no se marcan solas, cola sin conexión de verdad y Escuchar juntos al mismo tiempo

Beta solo para probar, sobre la 2.0.69-beta2 (incluye todo lo de la beta2 y la beta1). Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma. Te llega por el actualizador solo con «Recibir versiones beta» encendido (Ajustes ▸ Actualizaciones).

## Suscripciones y artistas

- **Aura ya no te suscribe sola a artistas:** abrir la página de un artista (por ejemplo, después de «Pedir música») ya no marca «Suscrito» por su cuenta. Solo te suscribe un toque tuyo.
- **Dos toques rápidos se respetan en orden:** si te suscribes y lo deshaces al momento, la cuenta queda como dijo tu último toque. Vale para la página del artista, el panel de la búsqueda, el chip del reproductor y el menú del artista en Biblioteca.
- **Un artista con dos identificadores en YouTube ya no queda a medias:** dejar de seguirlo y volver a seguirlo ya no da de baja tu cuenta mientras Aura dice «Suscrito».
- **Al cerrar sesión o cambiar de cuenta,** las suscripciones que faltaban por enviar se descartan y no llegan a la otra cuenta.
- Las suscripciones fantasma que ya estaban en tu cuenta de Google no se quitan solas: por ahora hay que quitarlas a mano.

## Modo sin conexión

- **Sin internet, la cola cambia a tu música sin conexión:** lo que viene después de la canción actual pasa a ser música que suena sin red (primero del mismo álbum y del mismo artista, luego el resto de lo descargado, lo del móvil, lo guardado en caché y lo exportado). Te avisa «Sin internet: la cola cambió a tu música sin conexión».
- **Al volver la red vuelve tu cola** con lo que no llegó a sonar, y avisa «Conexión recuperada: volviste a tu cola». No repite la canción que ya habías oído entera.
- **La música sin conexión que se añade respeta tus filtros:** tus «No me gusta», el contenido explícito oculto y la opción de ocultar vídeos.
- **Con el modo sin conexión manual,** al terminar la cola la música sigue con tu música sin conexión.
- **Android Auto:** «Descargadas» cuenta solo las descargas terminadas, y sin conexión ya no intenta cargar YouTube.

## Cola y recomendaciones

- **Los saltos rápidos enseñan a Aura lo que no te gusta:** saltar una canción a los pocos segundos baja un poco a su artista en tus gustos (Inicio y la radio), aunque no llegara a guardarse en el historial.
- **«Menos de esto» hace lo que promete:** también baja a ese artista en la radio y en los estantes de Inicio. Un «No me gusta» en una canción baja un poco a su artista principal.
- **Borrar el historial de escuchas borra también la memoria de saltos.**
- **Una canción que falla ya no arranca la música sola:** si la cola estaba en pausa o recién restaurada, salta a la siguiente y se queda en pausa.
- **Una radio que se estaba cargando ya no se cuela en la cola nueva** que pones después, ni tras cerrar el reproductor.
- **Una cola nueva ya no arrastra las recomendaciones de la anterior:** lo que sigue se basa en lo que suena.
- **«Pedir música» con un artista exacto:** un fallo de red a medias ya no se toma por catálogo agotado ni termina la cola.
- **Segunda opinión con IA de la cola (si la tienes encendida):** ya no gasta sus consultas en canciones que no van a sonar porque ya las oíste.
- **La lista diaria con IA usa el mismo modelo que el resto de la app.**

## Sonido

- **Tu música del móvil suena al mismo nivel que la de internet con Volumen seguro:** Aura lee el ReplayGain de tus archivos (si lo traen) en vez de bajarlos todos por igual. Lo lee poco a poco, solo en archivos nuevos o cambiados, sin alargar el escaneo.
- **Wavelet, JamesDSP o el ecualizador del sistema siguen funcionando tras un fundido:** antes dejaban de actuar desde la segunda canción con la transición suave.
- **El compresor suave («pegamento») viene apagado por defecto:** solo baja un poco los pasajes fuertes. Si lo encendiste tú a mano, conservas tu elección.
- **Normalización y Volumen seguro más fiables:** se quitó un efecto de audio que no hacía nada y que en algunos teléfonos, o justo al empezar a sonar, podía dejarlos sin aplicar.
- **Offload más claro:** con el ecualizador apagado, la firma Tidal ya no lo bloquea, y su ajuste dice el motivo real cuando no se puede usar (compresor suave, protección del altavoz, ancho estéreo o una sala de Escuchar juntos).
- **Textos honestos:** Volumen seguro explica que también sube los temas muy bajos y que apagarlo no garantiza sonido bit-perfect; Acerca de y la Bienvenida dejan claro que el margen automático del preamp solo actúa con «Headroom automático» encendido; la firma Tidal dice la curva que aplica y que solo suena con el ecualizador encendido.

## Ecualizador

- El preamp ya suena exactamente con el valor que ves: quitamos 2 dB que se restaban a escondidas. Por eso el ecualizador sonará un poco más fuerte que antes con el mismo ajuste; si te parece mucho, bájalo 2 dB.
- El preamp ahora llega hasta +9 dB (antes +6).
- Auto-EQ aplica el perfil de tus auriculares completo, con sus filtros de graves y agudos (antes se perdían y solo quedaba el recorte de volumen). Si quieres la corrección pura, sin la curva de la casa, elige el preset «Bypass».
- Apagar y volver a encender Auto-EQ ya no borra el perfil: vuelve tal cual, con su preamp.
- Nuevo interruptor «De-esser (control de eses)» en Ecualizador ▸ Efectos. Viene encendido, como hasta ahora; apágalo si prefieres las eses sin suavizar.
- Volumen Seguro: quitamos una medición que no hacía nada. El volumen no cambia.

## Pantalla de pago

- Textos más claros: el precio en dólares con «+ impuestos», que la suscripción se renueva sola y que no hay reembolsos. Los botones se agrandan para que el texto no se corte.

## Escuchar juntos y control entre aparatos

- **Escuchar juntos: todos empiezan la siguiente canción en el mismo instante,** también el anfitrión. Cada aparato espera un momento en pausa y arranca a la vez; si alguien tarda, verás «Esperando a …».
- **Los toques del anfitrión llegan en orden,** su cola se comparte en el orden en que suena y en la sala todos suenan a velocidad normal.
- **Reconexión más fiable:** detecta una conexión que se quedó colgada y vuelve a conectar, vuelve a la sala aunque Android haya cerrado la app y deja de intentarlo si la sala ya no existe (antes se quedaba «Conectando» varios minutos en cada arranque).
- **Un invitado ya no puede pausar ni saltar** desde la portada ni desde la letra a pantalla completa.
- **Se corrigen una vez las duraciones** de canciones que una sala anterior guardó mal.
- **Control remoto: el mini reproductor muestra el aparato que de verdad está sonando,** no el último que se pausó.
- **Siguiente y anterior del widget, la notificación, los auriculares y Android Auto** van al otro aparato cuando lo controlas a distancia, y respetan el bloqueo del invitado de Escuchar juntos. Play/pausa del widget pausa el otro aparato si está sonando.
- **Una lista enlazada a YouTube Music ya no sale duplicada en tus otros aparatos,** y las que ya se duplicaron se juntan en una sin borrar nada.
- **Guardar o quitar un álbum se sincroniza** con tus otros aparatos, y un cambio hecho justo al reconectar conserva su hora.

## Novedades

- **Sencillo, EP o Álbum también en los estrenos que solo están en YouTube** (antes uno de 2 a 6 canciones salía «Álbum»), y un estreno de una sola canción ya no sale como álbum en su tarjeta ni en la página del álbum.
- **La lista clásica de Novedades también dice Sencillo, EP o Álbum.**
- **Si tu sesión de Spotify deja de funcionar, Novedades sigue actualizándose con YouTube** en vez de quedarse congelada.

## Interfaz y accesibilidad

- **Errores de reproducción en español claro** y con un paso siguiente («Ver música sin conexión», «Iniciar sesión»…), en vez de mensajes técnicos en inglés.
- **Pantallas vacías con salida:** «Letras no encontradas» ofrece buscar la letra, y Álbumes, Listas, Descargadas, Me gusta o una búsqueda sin resultados proponen qué hacer.
- **«Cola cerrada · Deshacer»** al cerrar el reproductor deslizándolo hacia abajo.
- **Confirmación al «Reproducir a continuación», «Añadir a la cola» y «Añadir a lista»** desde los menús (y no confirma si no había nada que añadir). Quitar una canción de la cola ya no dice «de la lista de reproducción».
- **Temporizador de apagado en un toque:** 15, 30, 45 o 60 minutos o al terminar la canción, desde el menú ⋯ del reproductor. Un chip en el reproductor muestra la cuenta atrás (tócalo para sumar 15 minutos o apagarlo) y la música se apaga con un fundido suave justo a la hora.
- **TalkBack dice el nombre de los botones de solo icono** de la interfaz nueva.
- **Textos que salían en inglés con la app en español, ahora en español,** y en español de Latinoamérica se ven las redacciones revisadas.
- **La búsqueda de Ajustes te lleva a la opción exacta y la resalta,** y ahora también encuentra Volumen seguro, el compresor suave y la política de privacidad. Las pantallas de ajustes de letras se enlazan entre sí.
- **Mantener pulsado el icono de Aura ▸ «Buscar» o «Biblioteca»** abre esta app (antes abría la app vieja o nada).
- **El primer toque después de cerrar una ventana flotante ya funciona,** y deslizar una canción pide la misma distancia en cualquier pantalla.

## Tu música del móvil

- **Si un escaneo no encuentra nada y Aura se niega a borrar,** el aviso y la confirmación cuentan todas las canciones que se quitarían.
- **En las listas de exportados, los vídeos del teléfono van detrás de tus exportaciones,** así que el tope nunca deja fuera una exportación tuya por ellos.

## Privacidad y registros

- **Política de privacidad dentro de la app** (Ajustes ▸ Privacidad), en español o inglés. Ahora dice también a qué servidor van los datos de Escuchar juntos cuando las salas de Aura están apagadas.
- **Exportar a MP4 se describe como uso personal,** como dicen los términos.
- **El registro que compartes lleva menos datos:** ya no copia respuestas de Last.fm ni direcciones de canciones con su firma; solo el tipo de error.
- **Cada botón deja su código en el registro** (sin títulos ni artistas) para saber qué tocaste y qué pasó después.
- **El registro se escribe por tandas** y se guarda antes de que Android cierre la app, sin perder las últimas líneas.
- **La cabecera del registro dice bien** si tienes el fundido y Volumen seguro encendidos.

## Batería y rendimiento

- **Menos trabajo con la música en pausa:** el brillo de la letra, el medidor del ecualizador y el pulso de ritmo se detienen. El pulso y su captura respetan «Quitar animaciones» y el ahorro de batería.
- **El medidor del ecualizador sigue al reproductor tras un fundido,** y su indicador de ganancia ya no resta un margen que no se aplica.
- **Listas más ligeras:** sin portadas falsas ni trabajo repetido en cada fila; Inicio ya no pide dos veces la misma página al bajar, y la búsqueda en Biblioteca va más rápida.
- **El Modo rendimiento se aplica a las imágenes en cuanto lo cambias,** y libera memoria cuando el sistema la pide.
- **Sincronización con menos reintentos** (uno por descarga, no por página), y la cola persistente se lee sin trabar la app.

## Para que lo pruebes y me cuentes

- No pude probar nada de esto en un aparato. Lo que más necesito ver en tu móvil: que ningún artista aparezca suscrito solo tras «Pedir música»; modo avión mientras suena y la vuelta de la red; Escuchar juntos con 2 o 3 aparatos; un ecualizador externo tras un fundido; y el temporizador con su fundido.
- Si algo falla, manda el registro desde Ajustes ▸ Registros con la canción y la hora.
