# Aura Hi-Res v2.0.59-beta1 — Auditoría de algoritmos: discografías, pedir música, IA y colas inteligentes

Beta encima de la 2.0.58-beta2. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Discografía de artista

- **Discografías más completas y sin duplicados entre Álbumes y Sencillos/EPs.** Un ítem que YouTube
  Music clasifica mal (un sencillo listado bajo "Álbumes") ya no aparece repetido en las dos secciones.
- Corregido, dentro de la misma ronda, un caso en el que esa misma mejora podía dejar sin completar los
  sencillos de un artista sin sección propia de "Sencillos y EPs" en YouTube Music — Álbumes vuelve a
  completarse contra el catálogo entero de iTunes para que eso nunca pase.

## Pedir música

- **"Reggae de los 90" y pedidos similares ya no ignoran el género.** Antes, apenas detectaba una década,
  dejaba de mirar el género por completo.
- **"Música cristiana", "alabanza" y pedidos de ánimo (triste, romántica, de amor) ahora sí entran a las
  categorías/listas oficiales de YouTube Music**, en vez de caer directo a una búsqueda cruda sin filtrar.
- Los primeros pasos de búsqueda (charts en tendencia y categoría oficial) ahora también respetan el
  filtro de contenido cristiano, igual que los pasos siguientes.
- Cuando la respuesta viene de la IA, ahora también se verifica que de verdad sea del tema/idioma
  pedido — antes solo se le pedía a la IA que lo respetara, sin comprobarlo después.
- Reconoce mejor las negaciones: "no solo X" o "no quiero canciones de X" ya no bloquean por error al
  artista que justo pediste no limitar.
- La coincidencia de canción/artista específico ahora compara por palabra completa, no por texto
  suelto dentro de otra palabra.
- La cola infinita que sigue a "pedir música" ya vuelve a tener transición suave (crossfade) al sumar
  canciones en segundo plano, en vez de cortarse en seco.
- El perfil de gusto de "pedir música" y del Radar de Novedades ahora también usa tus géneros favoritos
  para ordenar, no solo tus artistas — antes ese dato se calculaba pero nunca se aplicaba.

## Recomendaciones y Radar de Novedades

- **"Recomendado para ti (IA)" ya respeta "No me gusta".** Era la única fuente de recomendaciones de toda
  la app que no lo hacía — un artista marcado podía seguir apareciendo ahí al día siguiente.
- Arreglado un caso raro donde el Radar de Novedades podía vaciarse por un fallo parcial de red aunque
  tuviera una lista buena guardada de la semana anterior.

## Batería

- Se quitó una escritura periódica a disco (aleatorio mejorado) que ocurría en cada guardado de posición
  pero cuyo dato nunca se llegaba a leer de vuelta — trabajo real sin ningún efecto, ahora eliminado.
