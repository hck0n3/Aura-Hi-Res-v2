# Plan de la beta14 (2.0.65-beta14) — siete fases, una sola beta

> Pedido por el dueño el 2026-10-18 (19 puntos). Orden literal: *«crea un plan y lo divides en fases, y cada fase
> evaluada y auditada para así mismo continuar con la siguiente, hasta que todo esté integrado de manera funcional en
> una sola beta; cuando termines, lanza esa beta, estás autorizado desde ya, quiero que salga desde las
> actualizaciones beta de la app»*.
>
> **Autorización de publicación:** el dueño autorizó, en ese mensaje, lanzar UNA prerelease `v2.0.65-beta14`
> (la recibe quien tenga «Actualizaciones beta» encendido, es decir, el dueño). No autoriza `main`, ni un tag
> estable, ni abrir un pull request, ni `player_configs.json`.

## Regla del ciclo (cada fase)

1. Confirmar que el fallo es real en el código actual (regla 3 de AGENTS.md) y escribir la causa raíz.
2. Implementar con pruebas (las de lógica pura; las de texto-guardián donde no hay SDK local).
3. **Auditoría de la fase (puerta A):** registro de regresiones leído y comprobado; traza hasta el efecto visible;
   calor/batería; nada de datos del usuario en `app.log`; `UI_INVENTORY.md`; zonas protegidas (`eq/`, `cpp/`,
   `license/`) intactas. El resultado se escribe en la fila del registro.
4. Commit propio de la fase. **No se empieza la siguiente fase hasta cerrar la auditoría de la anterior.**
5. Solo al final (F7): CI verde en el último commit → beta → certificado → aviso al dueño.

## Los 19 puntos y su fase

| # | Pedido del dueño | Fase |
|---|---|---|
| 1 | La reproducción se dispara sola después de pausar y salir de la app | F1 |
| 2 | El botón Cast hay que tocarlo varias veces; agrandar su zona táctil | F1 |
| 3 | El dictado de Pedir música se corta y busca antes de que yo termine | F1 |
| 4 | Modo sin conexión: mostrar SIEMPRE listas y álbumes descargados | F2 |
| 5 | El color de las ventanas flotantes no se parece a la mezcla de la portada (siempre cristal semitransparente) | F3 |
| 6 | «Añadir a lista»: portadas diminutas en plegable y nombres cortados | F3 |
| 7 | Listas en plegable con el diseño nuevo que tienen los álbumes | F3 |
| 8 | Carrusel de Inicio en plegable: empieza vacío y con pocas portadas; quiero más visibles | F3 |
| 9 | Listas que sigo (no creadas por mí): al final no hay sugerencias/similares | F3 |
| 10 | Inicio más inteligente y personal: que el usuario se sienta identificado | F4 |
| 11 | Novedades › «Lo más nuevo» no tiene que ver con mi biblioteca ni mis suscripciones | F4 |
| 12 | Cola inteligente: rock clásico 70/80 → canción ajena → vuelve al género → otra ajena… | F4 |
| 13 | El historial de búsqueda se pueda borrar (reproductor y búsqueda general) | F4 |
| 14 | Pedir música no entiende lo que pido (Bossa Nova → otra cosa); investigar YouTube Music/Amazon/GitHub | F5 |
| 15 | Pedir música no conoce todos los géneros (corridos tumbados) | F5 |
| 16 | Rango de años (2004–2010) que se desvía a años recientes | F5 |
| 17 | Pedir música: deslizar la tarjeta para ver la segunda opción | F6 |
| 18 | Pedir música: ver las portadas de las canciones del listado | F6 |
| 19 | Pedir música: rediseño estético (se siente básica) | F6 |

## Fases

- **F1 — Fallos urgentes** (1, 2, 3): causa real del arranque solo (recuperación de foco, botón multimedia,
  Cast, reanudación tras error); área táctil de Cast de 48 dp visibles → ≥ 56 dp con zona táctil ampliada y primer
  toque directo; el dictado ya no envía hasta que el reconocedor dice que terminó (silencio), no con el primer
  resultado parcial.
- **F2 — Sin conexión** (4): Biblioteca, Inicio y Listas en modo sin conexión (manual o automático) muestran listas y
  álbumes con canciones descargadas, siempre.
- **F3 — Plegable y ventanas** (5–9): el cristal de las hojas toma el color de la portada con el mismo cálculo
  validado de las barras; hoja «Añadir a lista» con cuadrícula/filas legibles; cabecera ancha de listas como la de
  álbumes; carrusel ancho que rellena desde el primer fotograma; sugerencias en listas seguidas.
- **F4 — Inteligencia** (10–13): Inicio por afinidad real; Novedades › Lo más nuevo desde artistas de la biblioteca y
  suscripciones; cola inteligente con compuerta de género/época sin intrusos; historial borrable.
- **F5 — Pedir música, motor** (14–16): léxico de géneros ampliado y con alias; años como restricción dura;
  escalera de respaldo que NUNCA cambia el género/época pedidos en silencio. Informe de investigación en
  `docs/INVESTIGACION_PEDIR_MUSICA.md`.
- **F6 — Pedir música, interfaz** (17–19): tarjeta con páginas deslizables (opción 1 / opción 2), portadas en la
  lista, rediseño.
- **F7 — Cierre**: notas `RELEASE_INFO.md`, versión `2.0.65-beta14` (versionCode 1069), registro, CI, beta.

## Honestidad sobre la verificación

No hay Android SDK en el entorno del agente: **nada de esto se ha visto funcionando en un móvil**. El CI compila y
ejecuta las pruebas unitarias; todo lo visual y de reproducción queda «⚠️ pendiente dispositivo» hasta que el dueño
lo confirme.
