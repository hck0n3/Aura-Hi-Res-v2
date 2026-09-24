# Aura Hi-Res v2.0.58-beta1 — Discografías completas, links externos más certeros, pedir música más preciso

Beta encima de la 2.0.57-beta1. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Discografías de artista

- **Ahora sí completan más allá de los topes de 60 álbumes / 80 lanzamientos.** Esos límites (para no
  quemar batería) existían, pero cuando de verdad truncaban un catálogo grande no quedaba registrado, así
  que la reparación automática en segundo plano nunca se disparaba y la discografía se quedaba incompleta
  para siempre. Ahora sí queda marcada, y esa reparación corre con topes bastante más altos (150/250) para
  avanzar de verdad en catálogos grandes (muy común en artistas latinos/regionales/de alabanza).
- **Álbumes y Singles/EPs ya no se mezclan ni se duplican entre las dos pantallas.** Cada lanzamiento de
  iTunes/Apple Music ahora es candidato para una sola sección, nunca las dos.
- **El orden ahora sigue el de iTunes/Apple Music** (más nuevo primero), en vez de un orden sin criterio.

## Pedir música

- **Pedir un género junto a una canción/artista específico ya no ignora lo específico.** Por ejemplo,
  "death metal Raining Blood Slayer" reproducía género genérico en vez de la canción exacta pedida — el
  mismo problema podía pasar con cualquier género. Ahora lo específico se prioriza siempre que se detecte.
- **El aviso de "ya está en una lista" ahora dice "En lista".**

## Enlaces externos

- **Se agregó una fuente de resolución más precisa (Odesli/song.link).** Cuando un link de Spotify, Apple
  Music, Amazon Music, etc. ya tiene mapeo conocido hacia YouTube Music, se usa ese id exacto en vez de
  adivinar por búsqueda — más rápido y más certero.
- **Sitios tipo app (Amazon Music, SoundCloud) que antes no daban ningún dato para buscar** ahora también
  se prueban con el mismo User-Agent que usan las vistas previas de enlaces (Facebook/Twitter/Discord),
  que muchos de estos sitios sí atienden aunque a un navegador normal no le den nada.
- **Se agregó registro de diagnóstico** (sin títulos, artistas ni URLs completas) para que un próximo
  reporte de "no encontrado" venga con datos reales del `app.log` en vez de otra suposición a ciegas.
