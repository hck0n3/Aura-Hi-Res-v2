# Informe de la auditoría — zonas que más fallan (2026-11-22)

Rama: `claude/compassionate-gates-1dotj5`. Lo único publicado es la beta privada `v2.0.69-beta1` (por orden del
dueño; ver «Cierre»). No se ha tocado `main`, no hay tag estable, ni `player_configs.json`, ni pull request. El
detalle de cada lote y cada hallazgo está en `AUDIT_TRACKER.md`.

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
| **CON** | 0 | — | 2 (F-004, D-002) | 1 (F-012, implementado) | 3 |
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

## F-012 (implementado con tu visto bueno)

- `2caa1d3`. El ajuste «Fondo animado a pantalla completa en horizontal» (Ajustes ▸ Rendimiento, apagado por
  defecto) ya funciona en el reproductor nuevo.
- Se activa con el móvil de verdad en horizontal, con el lienzo animado activo, con lienzo para esa canción
  y sin vídeo. Entonces solo se ve el lienzo a pantalla completa, con anterior, play y siguiente: se ocultan
  a los 3,5 s y vuelven al tocar.
- Fila 644 del registro y guardián en `HotZoneAuditGuardTest` (`dfd1b4f`).

## Verificación (fase 3) — CERRADA

- Revisión con ojos frescos de la primera tanda (F-005, F-006, F-009, F-010): **SIN HALLAZGOS**.
- Revisión fresca de la segunda tanda (F-001, F-003, F-004, F-007, F-008, F-011, D-002). Hallazgos y lo que
  se hizo con cada uno:
  - **Arreglar:** la política decía «solo título y artista», pero también se envían estilos, idioma y la
    señal de fe. Corregido con tu visto bueno (`0961639`).
  - **Rebatido:** según el revisor, F-002 y la fila 7 estaban fuera de las órdenes. No es así: fueron
    decisiones tuyas en esta conversación.
  - **Menor:** quedaban 6 comentarios que citaban el reproductor borrado. Actualizados (`5644fe2`).
- `unit-tests.yml` sobre `3f69f96` (toda la segunda tanda): **success**
  ([run 45034045389](https://github.com/hck0n3/Aura-Hi-Res-v2/actions/runs/45034045389)).
- Compilación con Cast (`test-build.yml`, `assembleUniversalGmsDebug`) sobre `34256c3`: **success**
  ([run 45033819989](https://github.com/hck0n3/Aura-Hi-Res-v2/actions/runs/45033819989)).
- F-012, CI del primer intento (`2caa1d3`): **falló** el test guardián `RemotePlaybackGuardTest:87`
  ([run 45034941734](https://github.com/hck0n3/Aura-Hi-Res-v2/actions/runs/45034941734); la app sí compiló).
  La vista del lienzo copiaba la orden de play/pausa que se reenvía al otro dispositivo, y el test cuenta
  exactamente dos copias. Arreglado dentro de F-012 sin tocar el test: las dos vistas a pantalla completa
  comparten la misma función (`a93ee5a`).
- F-012, primera revisión fresca: 1 bloqueante y 5 defectos (la barra de la cola tapaba los controles, el
  lienzo se decodificaba sin sonar, faltaba el «primer toque», el lienzo se buscaba en `Thumbnail` y saltaba
  al diseño ancho en cada canción…). Todos arreglados con tu visto bueno (`7742d73`, `08184fc`, `1dc08f5`).
- F-012, revisiones siguientes: hubo **seis revisiones frescas** en total. Sus hallazgos se arreglaron todos dentro
  de F-012:
  - búsqueda doble del lienzo;
  - lienzo retenido de canciones anteriores;
  - entrada que dependía de una ventana de 6 s;
  - la letra abierta;
  - trabajo con el reproductor recogido o la pantalla apagada;
  - pantallazo negro al abrir.

  Commits: `8035e77`, `8fd3bf7`, `25a7448`, `f93769c`. **La sexta revisión: SIN HALLAZGOS.** Ese revisor compiló y
  ejecutó 613 tests de los archivos que leen el reproductor, con 0 fallos.
- Queda un caso menor, aceptado: si la pantalla se apaga en mitad de la búsqueda por red y se vuelve a encender
  antes de que termine, puede hacerse una búsqueda duplicada. Como mucho es una por vez, y el resultado final es
  correcto.
- **Lint: no ejecutado.** No hay un workflow de lint en el repositorio y aquí no se puede compilar.
- **Registro por archivo:** revisé cada fila que cita un archivo tocado. Las 12 filas cuyo guardián vivía en
  el `Player.kt` borrado se comprobaron contra `AuraPlayer.kt`. La única función que solo existía en el
  clásico es F-012.

## Cierre (fase 4) — CERRADO

- Registro: filas 636-644. Guardián: `HotZoneAuditGuardTest`. `PROBLEMAS.md` reconciliado. Tracker al día.
- Beta privada `2.0.69-beta1` preparada (`fa67b80`) para el canal de betas del actualizador, por orden del dueño.
- **Beta publicada** el 2026-11-22 a las 16:05 UTC:
  [release `v2.0.69-beta1`](https://github.com/hck0n3/Aura-Hi-Res-v2/releases/tag/v2.0.69-beta1).
  - Sale de `gradle.yml` con `beta_tag`, sobre `5745157`
    ([run 45040298070](https://github.com/hck0n3/Aura-Hi-Res-v2/actions/runs/45040298070)).
  - Va marcada como prerelease, así que `releases/latest` sigue siendo `v2.0.68` y solo la recibe quien tenga
    encendido «Recibir versiones beta».
  - Lleva los APK universal y arm64, y `changelog.json`.
  - Firma comprobada sobre el APK publicado: `CN=Aura Hi-Res v2`, SHA-256
    `BA:82:C1:1D:…:CB:F0:61:42`. Es idéntica a la de la 2.0.68, así que se instala encima sin perder datos.
- Pruebas unitarias sobre `5745157`: **success**
  ([run 45039338308](https://github.com/hck0n3/Aura-Hi-Res-v2/actions/runs/45039338308)).

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
10. **F-012.** Activa el lienzo animado y, en Ajustes ▸ Rendimiento, «Fondo animado a pantalla completa en
   horizontal». Pon una canción con lienzo y gira el móvil: debe verse solo el lienzo, y al tocar aparecen
   los controles. Con el ajuste apagado, el reproductor ancho de siempre.

## No verificado aquí

- Ningún cambio se ha probado en un dispositivo.
- El límite exacto de variables del SQLite de cada fabricante (F-009).
- Cuánto tarda la copia en memoria de los ajustes en reflejar una clave escrita por primera vez (F-001).
  Es el mismo desfase de milisegundos que ya tenían los demás ajustes.
- El comportamiento interno de media3 al quitar un listener de un reproductor ya liberado (F-007). Va dentro
  de un `runCatching`.
