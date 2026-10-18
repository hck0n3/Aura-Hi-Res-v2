# Investigación: cómo entender «Pedir música» (YouTube Music, Amazon Music y proyectos abiertos)

Pedida por el dueño el 2026-10-18: *«investiga cómo lo hace YouTube Music y cómo lo hace Amazon Music, o si hay algo en
git que nos pueda ayudar, porque esto sí es importante»* (casos: «Bossa Nova» devuelve otra cosa; «corridos tumbados» se
vuelve «corridos»; «reggaetón cristiano del 2004 a 2010» se desvía a 2012+).

## Límite honesto
Ni YouTube Music ni Amazon publican cómo interpretan una petición. Lo que sigue separa lo **verificado** (documentación o
código abierto leído) de lo **inferido**. Desde el entorno de desarrollo no se pudo abrir last.fm, musicbrainz.org ni las
páginas de desarrolladores de Amazon, Deezer o Apple: esos datos son de resúmenes del buscador y están marcados.

## Qué se pudo verificar
| Tema | Hallazgo | Fuente |
|---|---|---|
| YouTube Music, búsqueda | Es relevancia de texto sobre el catálogo (título, artista, álbum, lista). No hay extracción documentada de género ni año: «bossa nova» devuelve cualquier cosa que contenga esas palabras (versiones, covers, mezclas «chill»). | inferido; `ytmusicapi` (MIT) expone `ignore_spelling` y filtros por `params` |
| Amazon Music / Alexa | Los intents `MusicCreativeWork` traen ranuras de género, artista y tipo; **no** de época ni ánimo. Épocas y ánimos se resuelven con estaciones/listas curadas, no filtrando por año de cada canción. | developer.amazon.com (resumen del buscador) |
| Año de una canción | YouTube Music no da año por canción, **sí por álbum** (`AlbumItem.year`). Una recopilación publicada en 2015 con canciones de 2005 dice 2015. | código de Aura (`AlbumPage.getYear`) |
| Proyectos abiertos | `beetbox/beets` (MIT): árbol de géneros + validación por etiquetas de Last.fm. `sigma67/ytmusicapi` (MIT): cadenas `params` de los filtros. Lista de «Every Noise at Once»: congelada desde dic-2023, sin licencia declarada. MusicBrainz: nombres de género CC0; asignaciones a obras CC BY-NC-SA. No se encontró ningún proyecto abierto que convierta una frase libre en lista de reproducción. | repos y licencias leídos en `raw.githubusercontent.com` |
| Last.fm | Sus condiciones piden acuerdo previo para uso comercial. Aura no cobra por la app: pide donaciones para su desarrollo (aclaración del dueño, 2026-10-18), así que probablemente no sea «uso comercial», pero esa lectura es de Last.fm, no mía. Ya trae una clave embebida y no se amplía su uso en esta beta. | last.fm/api/tos |

## Causas reales en Aura (leídas en el código, no supuestas)
1. **«Corridos tumbados» era un alias de «corridos»** (`MusicStyle`): la búsqueda pedía «corridos» y la comprobación daba
   por buena cualquier canción de corridos. «Bossa nova» sí estaba reconocida, pero un artista de bossa archivado por iTunes
   como «Jazz» se juzgaba FUERA y se sustituía por lo que dejara pasar la relajación del filtro.
2. **Los años eran solo una palabra de la búsqueda**: «del 2004 a 2010» se leía como un año (2010), «años 70 y 80» dejaba
   «80» como si fuera un género, y ninguna canción se comprobaba por año. La cola inteligente que continúa la petición ni
   siquiera conocía los años (por eso «empieza bien y luego se desvía»).
3. **El vocabulario de géneros era corto** (~56) y los tres léxicos del código (`MusicStyle`, `GENRE_HINTS`,
   `GENRE_FAMILIES`) no coincidían: un género que solo estaba en uno no activaba las listas ni las categorías.

## Qué se aplicó (filas 434-435 del registro)
- `corridos tumbados` es un estilo propio (hijo de `corridos`); un artista conocido solo como el padre es DUDOSO, no
  fuera ni acierto. Estilos vecinos (`related`): bossa ↔ jazz/mpb/samba. ~25 géneros nuevos (phonk, hyperpop, city pop,
  synthwave, shoegaze, emo, post-punk, grupera, tejano, plena, trova, MPB, trip hop, UK garage, hardstyle, bandas sonoras,
  infantil…). Un género que sigue sin estar se busca TAL CUAL, sin degradarlo a otro.
- `MusicRequestEra`: «del 2004 a 2010», «2004-2010», «entre… y…», «años 70 y 80», «70s a 90s», «desde/antes de/hasta»
  → un RANGO; sus palabras salen de la petición; una sola década se deja como estaba.
- Año por canción = año de su álbum (`MusicRequestEraYears`, acotado: ≤ 24 álbumes, 5 a la vez, 6 s). Recopilaciones
  («Éxitos», «Greatest», «Mix»…) no cuentan. Año seguro fuera → nunca suena; año desconocido → solo rellena y va detrás.
  Un escalón nuevo busca ÁLBUMES que ya estén en el rango y trae sus canciones.
- La cola inteligente que continúa la petición conserva los años (`Target.era`).

## Qué NO se hizo, y por qué
- Etiquetas de Last.fm para validar subgéneros nuevos más allá de lo que ya hace la app (condiciones de uso arriba).
- iTunes/Deezer/MusicBrainz para años por canción: límites de uso (≈ 20 llamadas/min en iTunes, 1/s en MusicBrainz) y más
  red/batería; el año del álbum de YouTube Music no cuesta una fuente nueva.
- Un modelo de lenguaje en el móvil: batería y calor (regla 7).
