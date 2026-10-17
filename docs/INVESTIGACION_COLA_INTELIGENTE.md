# Investigación: colas de recomendación (Spotify, YouTube Music, Deezer, Tidal, Apple Music)

Pedida por el dueño el 2026-10-17: *«quien tiene la mejor cola relacionada con lo que uno escucha […] que saque lo mejor de cada una para potenciar la cola inteligente»*.

## Límite honesto de esta investigación

Los algoritmos de **Spotify, YouTube Music, Tidal y Apple Music son propietarios y no están publicados**. Lo que sigue
separa lo **verificado** (fuente oficial o artículo del propio equipo) de lo que **solo cuentan blogs** (no se usa).
Desde el entorno de desarrollo no se pudo abrir arXiv ni los blogs de ingeniería de Spotify (red restringida), así que
de los artículos solo se leyó el resumen que devuelve el buscador. **No hay ningún ranking fiable de «quién la tiene
mejor»: nadie publica una comparativa medible.** No se afirma uno.

## Qué se pudo verificar

| Servicio | Verificado | Fuente |
|---|---|---|
| Spotify (Smart Shuffle) | En listas de más de 15 canciones recomienda 1 de cada 3; basado en lo que escuchas, cuándo, gente con gustos parecidos y especialistas; el usuario puede quitar una sugerencia y se refresca. | [Spotify Newsroom](https://newsroom.spotify.com/2023-03-08/smart-shuffle-new-life-spotify-playlists/) (vía buscador) |
| Spotify (investigación) | Bandits contextuales para la portada y explicaciones; concurso de predicción de saltos secuenciales (los mejores: RNN, 0,648 de exactitud media; 2.º: Multi-RNN, 0,641). | [Calibrated Recommendations with Contextual Bandits](https://arxiv.org/abs/2509.05460), [Multi-RNN skips](https://arxiv.org/pdf/1903.08408), [7.º puesto, código abierto](https://github.com/sainathadapa/spotify-sequential-skip-prediction) |
| YouTube Music | La cola de radio se regenera con filtros **Familiar / Descubrir / Popular / Éxitos ocultos** (más género y década); cada pulsación genera una cola nueva. | [9to5Google](https://9to5google.com/2022/09/29/youtube-music-queue-filters/) |
| Deezer Flow | Usuarios y canciones en un espacio de *embeddings*; se eligen canciones cercanas al vector del usuario, se **reordenan con heurísticas** y se adapta al feedback; mezcla filtrado colaborativo, audio y anotaciones de curadores. | [Flow Moods (arXiv 2207.11229)](https://arxiv.org/pdf/2207.11229) |
| Deezer (2025) | Memoria humana (ACT-R: recencia + frecuencia) para recomendar lo ya conocido y **audio para estimar la activación de canciones nuevas**. | [Beyond the past (arXiv 2507.17356)](https://arxiv.org/pdf/2507.17356) |
| Deezer (continuación de listas) | Marco «representar y agregar» escalable, código abierto. | [deezer/APC-RTA](https://github.com/deezer/APC-RTA), [paper](https://arxiv.org/pdf/2304.09061) |
| Tidal | Si no hay nada en cola, genera «Next Up» a partir de la última selección. Nada más oficial. | [Tidal Play Queue](https://support.tidal.com/hc/en-us/articles/360004182777-Play-Queue) |
| Apple Music | Autoplay continúa con música similar cuando acaba la cola; activado por defecto. No documentan cómo pesa nada. | [RouteNote](https://routenote.com/blog/apple-music-queue/) |
| Plex/Plexamp | Radio por similitud **sonora** del propio audio (Sonic Analysis). | [TechCrunch](https://techcrunch.com/2021/08/12/plexs-new-feature-matches-your-sonically-similar-music-to-make-playlists) |
| Diversidad | MMR (relevancia vs. parecido a lo ya elegido) es la técnica estándar; la tolerancia a la diversidad varía mucho entre usuarios y por ánimo. | [Diversifying Music Recommendations](https://arxiv.org/pdf/1810.01482) |

## Lo que descartamos
Cifras de «pesos» y «canalizaciones» de blogs de marketing (Dynamoi, totalaudiopromo, etc.): sin fuente primaria.

## Qué ya tenía Aura (contrastado con el código)
- **Saltos como señal negativa**: `AffinityEngine` (compleción < 30 % = negativo) y `ExactStyleGate` (salto rápido saca al artista/estilo de esa cola) — equivale a lo que usan los ganadores del concurso de Spotify como señal base.
- **Reordenar con heurísticas sobre un orden de relación** (Deezer): `orderedByTaste` mantiene el orden de relación de YouTube y solo lo «empuja» con gusto, co-relación y contexto.
- **Diversidad por espaciado de artista** (`RadioQueueShaping.spacedByArtist`), coherencia de género/idioma/estilo, memoria entre sesiones (`radio_continuation_played`).
- **Filtros estilo YouTube Music**: los *chips* de autoplay.

## Qué se aplicó (2026-10-17)
El hueco real no era «falta de algoritmo», sino **repetición** — lo que más rompe la percepción de calidad en cualquier cola:
`NoRepeatKey` + `orderedByTaste(strictNoRepeat)` (ver fila 400 del registro). Detalle: nunca relleno con oídas, historial de hasta 5000 canciones distintas, cola viva incluida lo añadido a mano, duplicados dentro del lote, y una clave título+artista que ignora remasters/feat./acentos/Topic sin fundir en vivo/remix/acústica.

## Ideas respaldadas pero NO aplicadas (decisión del dueño)
1. **Memoria ACT-R para el gusto** (recencia+frecuencia, Deezer 2025): ponderar el perfil de gusto con decaimiento temporal. Requiere medir en dispositivo; riesgo de estrechar la cola.
2. **Similitud por audio** para canciones nuevas (Deezer/Plex): necesitaría un modelo de audio en el móvil — calor/batería (regla 7).
3. **MMR formal** en vez de espaciado de artista: mejora de diversidad, sin medida disponible aquí.
Ninguna se activó sin poder probarla en el dispositivo (regla 2: «compila y parece correcto» no es un arreglo).
