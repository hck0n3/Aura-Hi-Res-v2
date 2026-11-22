# Informe de la auditoría — zonas que más fallan (2026-11-22)

Rama: `claude/compassionate-gates-1dotj5`. Nada se ha publicado: ni `main`, ni tags, ni `gradle.yml`, ni
`player_configs.json`, ni pull request. El detalle de cada lote y cada hallazgo está en `AUDIT_TRACKER.md`.

## Cobertura

- 51 lotes, 24 archivos, 53.209 líneas de inventario: `MusicService.kt`, `MainActivity.kt`, `AuraPlayer.kt`,
  `App.kt`, `Thumbnail.kt`, `DeviceSyncEngine.kt`, `Player.kt`, `Lyrics.kt`, `YTPlayerUtils.kt`,
  `ExactStyleGate.kt`, `AuraShell.kt`, `AuraQueue.kt`, `RemotePlayback.kt`, `AuraPalette.kt`, `SyncUtils.kt`,
  `Queue.kt`, `PreferenceKeys.kt`, `DatabaseDao.kt`, `AuraContent.kt`, `AuraBloom.kt`, `DataStore.kt`,
  `AuraLibraryTabs.kt`, `AuraHomeScreen.kt` y `AxionEqScreen.kt` (este, solo en lectura).
- Lectura completa, línea a línea, de 17 archivos.
- Por partes y con búsquedas dirigidas: `DatabaseDao.kt`, `AuraContent.kt`, `AuraLibraryTabs.kt`,
  `AuraHomeScreen.kt` y `AxionEqScreen.kt`.
- Con script: `PreferenceKeys.kt`.
- `Player.kt`: solo la parte viva, porque el resto era inalcanzable (y ya está borrado).
- El tracker indica qué rangos se leyeron de cada archivo.

## Hallazgos por lente y gravedad

Lentes: CON = conexiones e incoherencias; BUG = fallos; SEC = seguridad y privacidad; PERF = rendimiento y batería.

| | P0 | P1 | P2 | P3 | Total |
|---|---|---|---|---|---|
| **BUG** | 0 | 3 (F-005, F-006, F-009) | 2 (F-007, F-010) | — | 5 |
| **SEC** | 0 | 1 (F-002) | 2 (F-003, F-008) | — | 3 |
| **PERF** | 0 | — | 2 (F-001, F-011) | — | 2 |
| **CON** | 0 | — | 2 (F-004, D-002) | 1 (F-012) | 3 |
| **Total** | **0** | **4** | **8** | **1** | **13** |

F-005 cuenta como BUG y también como SEC. F-008 cuenta como SEC y también como CON. Además hay 6 sospechas
sin escenario completo (S-001 a S-006) y 27 observaciones P3. Ninguna de ellas se ha tocado.

Por marca: 10 NUEVO, 1 REGRESIÓN (F-006, de la fila #345 c) y 2 CONOCIDO o heredados del fork (F-010, D-002).

## Arreglado (un commit por hallazgo)

| Hallazgo | Commit | Qué notarás |
|---|---|---|
| F-005 (P1) | `6562382` | Un enlace `/browse/…` o `/channel/…` mal formado ya no cierra la app. |
| F-006 (P1) | `56aab24` | Al terminar un álbum o una lista, la cola sigue con el motor de continuación por estilo e idioma, no con el automix de YouTube. |
| F-009 (P1) | `3b9b161` | «En caché» y la búsqueda sin conexión ya no se cierran en Android 8–11 con más de 999 canciones en caché. |
| F-010 (P2) | `66d47e0` | Abrir un álbum ya no borra la ficha de sus artistas (seguimiento, foto y vínculos). |
| F-003 (P2) | `ccb07c6` | El registro que compartes ya no lleva trozos de las respuestas del servidor de audio. |
| F-004 (P2) | `b409a2d` | Se quitó una función de `MusicService` que no se llamaba desde ningún sitio. |
| F-007 (P2) | `ef23718` | Con dos dispositivos emparejados, empezar a sonar en uno pausa al otro al momento, también después de un crossfade. |
| F-001 (P2) | `1695545` | Los ajustes que nunca has cambiado ya no bloquean el hilo principal al leerse. |
| F-008 (P2) | `1898542`, `df71d59` | La segunda opinión de la IA de la cola viene apagada; quien la había encendido a mano la conserva. La política de privacidad explica qué envía. |
| F-011 (P2) | `4d21d74` | Entrar en Canciones, Álbumes, Artistas o Listas ya no lanza una sincronización completa cada vez: respeta la cadencia que elegiste. «Sincronizar» a mano y tirar para refrescar siguen igual. |
| D-002 | `5844172` | Se borró el reproductor clásico (unas 3.700 líneas que ya no se podían abrir). `InlineLyricsView` sigue, ahora en su propio archivo. |
| F-002 | `78ca0ef` | Sin cambio de código. La regla 4 de AGENTS ahora permite por escrito el id de vídeo o de lista en los registros. |

Documentación:
- Filas nuevas 636-643 en el registro; la fila 7 describe ahora el pintado de la línea entera, que es lo que
  decidiste (`d20ea4f`).
- Test guardián `HotZoneAuditGuardTest` (`34256c3`).
- `PROBLEMAS.md` reconciliado: de los 11 ítems de la zona, 10 ya estaban arreglados y 1 queda parcial (P33)
  (`d8219a8`).
- `UI_INVENTORY.md`: nota sobre el reproductor clásico borrado.

## Tu decisión pendiente

- **F-012 (P3):** el ajuste «Lienzo a pantalla completa al girar» (Ajustes ▸ Rendimiento) solo lo leía el
  reproductor clásico. En la interfaz nueva el interruptor no hace nada, y ya no lo hacía antes del borrado.
  ¿Lo implemento en el reproductor nuevo o quito el interruptor?

## Verificación (fase 3)

- Revisión con ojos frescos de la primera tanda (F-005, F-006, F-009, F-010): **SIN HALLAZGOS**.
- La revisión de la segunda tanda, el resultado de `unit-tests.yml` y la compilación con Cast
  (`test-build.yml`, `assembleUniversalGmsDebug`) se apuntan al final de este informe.
- **Lint: no ejecutado.** No hay un workflow de lint en el repositorio y aquí no se puede compilar.
- **Registro por archivo:** revisé cada fila que cita un archivo tocado. Las 12 filas cuyo guardián vivía en
  el `Player.kt` borrado se comprobaron contra `AuraPlayer.kt`. La única función que solo existía en el
  clásico es F-012.

## Pruebas manuales en tu móvil

1. **F-005.** Desde otra app, abre `https://music.youtube.com/browse/MPREb%2Fx`. Aura debe abrirse sin
   cerrarse. Un enlace real de álbum o de canal debe abrir su ficha.
2. **F-006.** Reproduce un álbum hasta el final con la reproducción infinita activa. Lo que sigue debe
   mantener su estilo e idioma.
3. **F-009.** En un móvil con Android 11 o anterior y mucha caché, abre Biblioteca ▸ En caché. Debe cargar
   sin cerrarse.
4. **F-010.** Sigue a un artista, abre varios álbumes suyos (sobre todo colaboraciones) y vuelve a Artistas.
   Debe seguir como seguido y con su foto.
5. **F-007.** Con dos dispositivos emparejados, reproduce en A, deja pasar un cambio de canción con
   crossfade y pulsa play en B. A debe pausarse en menos de un segundo, no a los 4 s.
6. **F-011.** Pon la sincronización en «cada semana», entra y sal varias veces de las pestañas de la
   Biblioteca. No debe aparecer «Sincronizando tu biblioteca…» cada vez.
7. **F-008.** En Ajustes ▸ Servicios Aura, la «segunda opinión de la IA» debe salir apagada. Enciéndela y
   comprueba que la cola inteligente sigue funcionando.
8. **F-001.** El arranque y la apertura de pantallas deben ir igual o más rápido. Cambia un ajuste que nunca
   hubieras tocado y comprueba que se aplica.
9. **D-002.** El reproductor, la mini barra, la letra de la cola y el reproductor ancho del plegable deben
   funcionar como siempre.

## No verificado aquí

- Ningún cambio se ha probado en un dispositivo.
- El límite exacto de variables del SQLite de cada fabricante (F-009).
- Cuánto tarda la copia en memoria de los ajustes en reflejar una clave escrita por primera vez (F-001).
  Es el mismo desfase de milisegundos que ya tenían los demás ajustes.
- El comportamiento interno de media3 al quitar un listener de un reproductor ya liberado (F-007). Va dentro
  de un `runCatching`.
