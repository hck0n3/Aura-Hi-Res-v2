# Decisiones del dueño — 2026-11-24

El dueño, tras probar la beta 2.0.69-beta3, respondió literalmente: *«todas las deciciones que faltan de la lista de
desiciones mi respuesta es si atodas tud desiciones»*. Es decir: **acepta la recomendación del agente en cada punto
pendiente** de `PENDIENTES_2026-11-23.md` §H. Este archivo fija qué se recomendó en cada uno, para que nadie lo
reinterprete después. Lo que exige una acción que solo puede hacer el dueño (su cuenta de Gumroad, una orden para
`main`) sigue esperándole aunque la decisión esté tomada.

También informó del resultado de la beta3: «Pedir música» ya no suscribe a nadie; el modo avión pasa a la música
sin conexión, **pero quiere un aviso visible en el reproductor mientras sigue en modo sin conexión**; y **Escuchar
juntos a veces sincroniza y a veces no** («eso no me gusta»).

## Decididas antes (recomendaciones explícitas, ahora aceptadas)

| # | Decisión | Queda así |
|---|---|---|
| H15 | Baja confirmada vs. fallida (fila 716) | Sí: columna nueva en Room con su migración |
| H16 | Compresor suave guardado en ON (fila 712) | Sí: migración única que lo apaga |
| H12 | La música vuelve sola hasta 30 min tras un corte | Sí: ventana de 5 min |
| H30 | «Exportadas» muestra MP3 borrados | Sí: mismo filtro que Descargadas |
| — | Deshacer «Menos de esto» sobre un artista | Aviso con botón «Deshacer» |
| — | 6 textos de es-rUS mejores que los de values-es | Se quedan los de es-rUS (ya hecho, fila 784) |
| — | Preamp al apagar Auto-EQ | Vuelve al preamp que había antes de activar Auto-EQ |

## Resto de §H (recomendación del agente = decisión)

| # | Pregunta | Decisión |
|---|---|---|
| H1 | Deslizar filas: un significado por dirección | Se mantiene por pantalla, pero siempre con icono y texto mientras se desliza |
| H2 | Vibración al desplazar (cada 100 ms) | Apagada por defecto; se quedan las que confirman algo (interruptor para volver a encenderla) |
| H3 | Insignia de calidad y hoja «Ruta de la señal» | Sí (Fase 9, extras) |
| H4 | Botones de notificación configurables y accesos directos dinámicos | Accesos directos dinámicos sí ya; botones configurables en Fase 9 |
| H5 | Atrás predictivo, «Guardar como lista», «Vaciar lo siguiente», RTL, «Porque escuchaste X» | Sí, todo, en Fase 9 |
| H6 | Estudio comparativo | Ahora: pantalla «Ocultos» y calidad distinta en wifi/datos. Voz/Asistente, carpetas de listas y el resto: Fase 9 |
| H7 | «Más» de las listas de éxitos abre el navegador | Pantalla propia dentro de la app (Fase 9) |
| H8 | Avisos efímeros sin acción | Aviso con botón («Volver a entrar», «Ver filtros») |
| H9 | Enlace de invitación a Escuchar juntos sin preguntar | Diálogo de confirmación |
| H10 | Borrados masivos retenidos sin pantalla | Pantalla para aceptarlos o rechazarlos |
| H11 | Bajas antiguas en el primer emparejamiento (654a) | Se mantiene hoy; pasa a la Fase 8 |
| H13 | «Esperan confirmación» / «Solo en Aura» globales | Por cuenta de Google |
| H14 | Suscripción hecha en YouTube no visible hasta sincronizar | Se acepta (sin lecturas extra que gasten batería) |
| H17 | Modo «Fidelidad» (bit-perfect USB, sin fundido en álbum, 32 bits, tempo/tono Hi-Res) y renombrar la curva | Sí, como fase propia tras la Fase 8; renombrar la curva ya |
| H18 | Dependencias retenidas | Beta propia tras la beta3 con `deps-majors`; Cast/MediaRouter y targetSdk 37 en la siguiente; Firebase no (la app no lleva Firebase) |
| H19 | ffmpeg-kit → ffmpeg-kit-audio, tinypinyin → pinyin4j | Sí, en la beta de dependencias |
| H20 | Propuestas de SimpMusic | Sí: robustez de Last.fm y descargas inteligentes. No: el resto por ahora |
| H21 | Producto nuevo de Gumroad sin «JR-MUSIC-PRO» | Sí — **espera al dueño**: crear el producto en su cuenta y dar el nuevo ID |
| H22 | Nombre legal o ciudad en los textos legales | No: se quedan como «desarrollador independiente con residencia en Honduras» |
| H23 | Reactivar `player-watch.yml` | Sí — **espera la orden del dueño** para el push a `main` (AGENTS.md) |
| H24 | «Reproducir a continuación»/«Añadir a la cola» al otro aparato; 30 s y 10 s (filas 660, 663) | Sí al comando nuevo (app + Worker sync, compatible hacia atrás); se confirman 30 s y 10 s |
| H25 | Reconocedor de voz que se reinicia (fila 611) | Se acepta como límite |
| H26 | Mensaje cortado de la beta 14 (fila 631) | Sin decisión posible: **el dueño debe repetir qué pedía** |
| H27 | «YouTube» en lo funcional; locales como «sin conexión»; «Inicia sesión con YouTube Music» en Cuentas | Se mantiene «YouTube» donde es funcional (también en Cuentas); las canciones locales cuentan como sin conexión |
| H28 | Wrapped: otros aparatos y artistas invitados | Sí a las escuchas de otros aparatos; los invitados no cuentan como artista principal |
| H29 | «Resultados compatibles seleccionables» = interruptores por servicio (fila 676) | Sí, esa es la interpretación |

## Respuestas del dueño (2026-11-24, segunda tanda)

| # | Tema | Decisión del dueño |
|---|---|---|
| H21 | Gumroad | **No se crea producto nuevo**: el dueño cambia solo el **nombre** del producto en Gumroad. El enlace (`toberto.gumroad.com/l/JR-MUSIC-PRO-OFFICIAL`, `LicenseScreens.kt` `GUMROAD_URL`) y el `PRODUCT_ID` no cambian, así que las apps instaladas siguen funcionando. |
| H23 | `player-watch.yml` | **Descartado** («no me sirve de nada»). En su lugar: un vigilante que, cuando la reproducción falle, **tome la solución de SimpMusic** (`github.com/maxrave-dev/simpmusic`). Diseño en curso. |
| H26 | Mensaje cortado de la beta 14 | **Descartado** (el dueño no recuerda qué era). |
| — | Herramienta de limpieza de suscripciones fantasma | **Ahora**, no en la Fase 9: el dueño lleva más de 4 meses con el fallo. |
| A | Inicio de sesión | **A1**: «Inicia sesión con tu cuenta de Google» y sin «YouTube Music» en los textos visibles (sustituye a H27). |
| B1 | Servidor de Escuchar juntos | **Sí**: dejar de usar el servidor compartido de Metrolist; salas solo de Aura. |
| B2, B3 | Aleatorio del invitado; aviso tras 3 recargas | Sin respuesta todavía. |
