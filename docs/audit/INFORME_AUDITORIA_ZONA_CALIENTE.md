# Informe de la auditoría — zonas que más fallan (2026-11-22)

Rama: `claude/compassionate-gates-1dotj5`. Nada se ha publicado: ni `main`, ni tags, ni `gradle.yml`, ni
`player_configs.json`, ni pull request. El detalle de cada lote y cada hallazgo está en `AUDIT_TRACKER.md`.

## Alcance

51 lotes, 24 archivos, 53.209 líneas: `MusicService.kt`, `MainActivity.kt`, `AuraPlayer.kt`, `App.kt`,
`Thumbnail.kt`, `DeviceSyncEngine.kt`, `Player.kt`, `Lyrics.kt`, `YTPlayerUtils.kt`, `ExactStyleGate.kt`,
`AuraShell.kt`, `AuraQueue.kt`, `RemotePlayback.kt`, `AuraPalette.kt`, `SyncUtils.kt`, `Queue.kt`,
`PreferenceKeys.kt`, `DatabaseDao.kt`, `AuraContent.kt`, `AuraBloom.kt`, `DataStore.kt`,
`AuraLibraryTabs.kt`, `AuraHomeScreen.kt` y `AxionEqScreen.kt` (este solo en lectura).

No todo se leyó línea a línea. `PreferenceKeys.kt` se revisó con un script. `DatabaseDao.kt`, `AuraContent.kt`,
`AuraLibraryTabs.kt`, `AuraHomeScreen.kt` y `AxionEqScreen.kt` se leyeron por partes y con búsquedas dirigidas.
El tracker dice qué partes de cada uno.

## Arreglado (un commit cada uno)

| Hallazgo | Commit | Qué notarás |
|---|---|---|
| F-005 (P1) | `6562382` | Un enlace `music.youtube.com/browse/…` o `/channel/…` mal formado ya no cierra la app; solo la abre. |
| F-006 (P1) | `56aab24` | Al terminar un álbum o una lista, la cola sigue con el motor de continuación (estilo e idioma), no con el automix de YouTube. Era el arreglo de la fila #345(c), que solo estaba en el reproductor clásico. |
| F-009 (P1) | `3b9b161` | «En caché» y la búsqueda sin conexión ya no se cierran en Android 8–11 cuando la caché pasa de 999 canciones. La caché es ilimitada por defecto. |
| F-010 (P2) | `66d47e0` | Al abrir un álbum ya no se borra la ficha de sus artistas. Antes se perdían el seguimiento, la foto y los vínculos de ese artista con todas sus canciones. |

Cada arreglo cambia de 1 a 3 líneas en un solo archivo, y ninguno toca un archivo compartido de la lista de
AGENTS. La revisión con ojos frescos del diff acumulado dio **SIN HALLAZGOS**. Aquí no se puede compilar
(Maven Central responde 429), así que lo comprueba el CI `unit-tests.yml` de la rama.

## Esperan tu visto bueno (cambios de riesgo: plan de 3 líneas)

**F-003 (P2, privacidad).**
- Archivo: `MusicService.kt:8410-8415`, que es un archivo compartido.
- Cambio: quitar del aviso de fetch fallido el trozo `body=$peek`, que son los 160 primeros caracteres del cuerpo de la respuesta.
- Por qué: va a `app.log`, el archivo que compartes, y la regla 4 prohíbe cuerpos de respuesta.

**F-004 (P2, limpieza).**
- Archivo: `MusicService.kt:10295`.
- Cambio: borrar `isPowerSaveModeActive()`, que es privada y no se llama desde ningún sitio.
- Por qué: es código muerto en el archivo más grande del proyecto; la misma comprobación ya está escrita en línea en tres sitios.

**F-007 (P2, dispositivos).**
- Archivo: `DeviceSyncEngine.kt:377-388`.
- Cambio: volver a enganchar el listener `onPlayWhenReadyChanged` cuando el crossfade cambia de instancia de reproductor, y soltarlo del viejo.
- Por qué: desde el primer crossfade, «empecé a sonar aquí → pausa el otro» tarda hasta 4 s, así que los dos dispositivos pueden sonar a la vez.

## Decisiones tuyas (no se toca nada sin respuesta)

- **F-001:** `getNonBlocking` bloquea el hilo principal (`runBlocking`) en cada ajuste que nunca has tocado. El arreglo cambia el caso 5 de `PrefsBridgeTest`.
- **F-002:** los logs INFO/WARN llevan el id del vídeo o de la lista. Con esos ids diagnosticaste la fila #305, pero la regla 4 los prohíbe. ¿Los quitamos o los permitimos por escrito?
- **F-008:** la cola inteligente envía al Worker los títulos y artistas de lo que suena, y está activa por defecto. La política de privacidad publicada no lo cuenta. ¿Actualizamos la política o lo apagamos por defecto?
- **F-011:** entrar a las pestañas Canciones, Álbumes o Artistas lanza una sincronización completa que ignora la cadencia que elegiste. ¿Debe respetarla?
- **D-002:** el reproductor clásico (`Player.kt`, ~2.950 líneas) ya no se usa desde ningún sitio. ¿Se conserva como respaldo o se borra?
- **Fila #7 del registro:** dice «fabricar el barrido letra por letra», pero el código pinta la línea entera a propósito (commit `8b44a43`). ¿Actualizamos la fila?

## Propuestas para el registro y tests guardianes (solo si dices que sí)

- F-005: test de texto que compruebe que las ramas `browse` y `channel` de `MainActivity.kt` pasan por `isRouteSafeId`.
- F-006: test de texto que compruebe que el efecto de automix de `AuraPlayer.kt` contiene `automixDefersToContinuation()`.
- F-009: test de texto que compruebe que `CachePlaylistViewModel.kt` trocea `getSongsByIds` (`chunked(`).
- F-010: test de texto que compruebe que `update(album, albumPage, artists)` ya no borra `ArtistEntity` (sin `artists?.forEach(::delete)`).
- Una fila nueva por cada uno de los cuatro, con el `archivo:línea` del guardián.

## Pruebas manuales en tu móvil

1. **F-005.** Desde otra app, abre `https://music.youtube.com/browse/MPREb%2Fx`. Aura debe abrirse sin cerrarse. Después abre un enlace real de álbum y de canal: deben llevarte a su ficha como siempre.
2. **F-006.** Reproduce un álbum hasta la última canción con la reproducción infinita activa. Lo que sigue debe mantener el estilo e idioma del álbum. En el log debe aparecer el motor de continuación, no el automix.
3. **F-009.** Necesitas un móvil con Android 11 o anterior y la caché con muchas canciones. Abre Biblioteca ▸ En caché: debe cargar la lista sin cerrarse.
4. **F-010.** Sigue a un artista, abre varios álbumes suyos (sobre todo colaboraciones) y vuelve a Artistas. Debe seguir apareciendo como seguido, con su foto y sus canciones.

## No verificado aquí

- El límite exacto de variables del SQLite de cada fabricante (F-009). Lo normal es 999 hasta Android 11.
- Con qué frecuencia difiere el número de artistas entre la base de datos y la página del álbum (F-010).
- Las sospechas S-001 a S-006 del tracker: les falta un escenario completo y no se ha tocado nada por ellas.
