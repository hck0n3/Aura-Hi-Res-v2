# C2 · Una sola interfaz — auditoría clásica vs Aura (2026-10-07)

Cuatro auditorías de solo lectura compararon **cada fila de `docs/UI_INVENTORY.md`** con lo que se ve con
la interfaz nueva (Aura) encendida. Regla: *ocultar también es perder*. Resultado: **Aura ya cubre la gran
mayoría** (≈ 120 filas del esqueleto/Inicio/Buscar, ≈ 135 del reproductor/cola/letras, casi todo §9 y §17),
pero **retirar la interfaz clásica HOY perdería cosas**: hay pantallas y componentes clásicos que Aura sigue
usando por debajo.

## 1. Huecos reales ya ARREGLADOS (fila #348 del registro)

| Hueco | Arreglo |
|---|---|
| Volver a tocar la pestaña Biblioteca / Novedades no subía al inicio | `AuraScrollToTopOnReselect` en las 5 pestañas de Biblioteca y en Novedades |
| Mini reproductor sin la insignia «E» (explícito) | Insignia «E» como en las filas de Aura |
| Menú del reproductor: el diálogo «Exportar» existía pero ninguna fila lo abría | Fila «Exportar MP3 o vídeo» (respeta «Exportar como MP3») |
| El botón de descarga de Aura ignoraba «Exportar como MP3» desactivado | Con el interruptor apagado, descarga directamente |
| Portadas de álbum sin ▶ (reproducir sin abrir) | ▶ en la pestaña Álbumes de Biblioteca (misma cola y contexto que la clásica) |
| Deslizar fila → «Reproducir a continuación / Añadir a la cola» faltaba en listas | Añadido en Me gusta / Descargado / Caché / Top / Exportado y en Lista online |
| Insignia «en biblioteca» apagada a mano en las auto-listas | Encendida según el dato real |
| «Actualizaciones» solo se encontraba desde la hoja de la cuenta | Fila en Ajustes ▸ Acerca de |

## 2. Huecos que necesitan TU decisión

1. **Temporizador de apagado:** desapareció de **las dos** interfaces antes de este repositorio; solo quedan
   textos e iconos. No hay registro de que lo decidieras. ¿Lo recupero?
2. **Vista lista/cuadrícula en Biblioteca** (Canciones, Artistas, Álbumes): Aura tiene una sola vista por
   pestaña (Canciones en lista; el resto en cuadrícula). ¿Recupero el interruptor?
3. **«Fondo animado a pantalla completa en horizontal»** (canvas al girar): en Aura el interruptor no hace
   nada; solo existe en el reproductor clásico. ¿Lo llevo a Aura o quito el interruptor?
4. **9 ajustes finos del Liquid Glass** (viveza, desenfoque, lente, aberración, profundidad, tinte, opacidad,
   color de texto): en Aura no cambian nada (Aura usa su propio cristal, ahora teñido con la portada). ¿Los
   aplico al cristal de Aura o los oculto con aviso?
5. **Pequeños de diseño** (Aura los quitó a propósito o cambiaron de forma): insignia de calidad
   LOSSLESS/320 en filas, chip «Mix» encendido mientras suena una radio, deslizador de volumen del sistema
   en el reproductor, frases por tarjeta de los mixes diarios («Suena como…», «N reproducciones»), avatar en
   la cabecera de Inicio, logotipo «AURA HI-RES» en Inicio, mantener pulsado «atrás» → Inicio en
   Estadísticas y Migración.

## 3. Lo que impide BORRAR la interfaz clásica hoy (sigue funcionando, pero es código clásico)

- **Pantallas sin versión Aura** (se ven con estilo Aura en parte, pero son clásicas): Onboarding (4 pasos),
  Importar de Spotify, Actualizaciones y Novedades de la versión, Términos, Explorar/Géneros/Podcasts,
  Historial, Cuenta, Escuchar juntos, Reconocer música (+ historial), Modo ambiente, Radar de novedades,
  Artista ▸ canciones / álbumes, canciones locales, y **todas las sub-pantallas de Ajustes**.
- **Componentes clásicos dentro de Aura:** `Thumbnail.kt` (carrusel de portadas, canvas, gestos de doble
  toque), `PlayerProgressSlider`, `Lyrics.kt`/`InlineLyricsView` (toda la vista de letras), `LyricsMenu`,
  `QueueMenu`, todos los menús de `ui/menu/*` (incluido `PlayerMenu.kt`, que define `TempoPitchDialog` y
  `ListenTogetherDialog`), `AddToPlaylistDialog`, `ExportFormatChooserDialog`, `AudioDeviceBottomSheet`,
  `BottomSheet`, `NowPlayingSidePanel` y el rail lateral (pantalla ancha/TV/coche), `SettingDialoge`.

**Conclusión:** «una sola interfaz» no es borrar la clásica, sino **completar Aura pantalla por pantalla** y
solo entonces retirar lo clásico que ya no se use. Orden propuesto: (1) tus decisiones del punto 2;
(2) pantallas Aura para Historial, Artista ▸ canciones/álbumes, Radar y Explorar; (3) quitar el
interruptor «Interfaz nueva» cuando nada dependa de la clásica; (4) borrar el código clásico muerto.
