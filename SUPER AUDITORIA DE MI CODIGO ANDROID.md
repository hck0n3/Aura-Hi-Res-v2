---
plan: SUPER AUDITORÍA ANDROID COMPLETA
version: 1.0
estado: INICIAL
memoria: ACTIVADA
objetivo: Auditar, reparar, proteger y potenciar el código Android sin romper funcionalidad
modo: Auditoría + Reparación + Potenciación
---

# PLAN MAESTRO DE SÚPER AUDITORÍA, REPARACIÓN Y POTENCIACIÓN ANDROID

## 0. PROPÓSITO DEL PLAN

Este plan tiene como objetivo ejecutar una auditoría completa del código Android, detectando:

- Problemas de seguridad.
- Errores de arquitectura.
- Código frágil o difícil de mantener.
- Problemas de rendimiento.
- Fugas de memoria.
- Dependencias vulnerables.
- Configuraciones inseguras.
- Errores de concurrencia.
- Problemas de red, almacenamiento, WebView, IPC, permisos y privacidad.
- Falta de pruebas.
- Deficiencias en CI/CD.

Además, este plan incluye:

- Reparación segura de cada hallazgo.
- Validación para no romper funcionalidad existente.
- Potenciación del código, rendimiento, seguridad, arquitectura y experiencia de desarrollo.
- Memoria persistente del progreso para que la auditoría se cumpla de principio a fin.

---

## 1. REGLAS DE ORO DEL PLAN

Estas reglas son obligatorias durante toda la auditoría y reparación.

### 1.1 No romper funcionalidad existente

1. No se aplica ningún cambio crítico sin una prueba, validación o evidencia de comportamiento.
2. No se elimina código sin verificar primero si está en uso.
3. No se refactoriza profundamente sin antes proteger el flujo con tests o documentación de comportamiento.
4. No se actualizan dependencias mayores sin validar compatibilidad.
5. No se cambia el comportamiento de una función crítica sin respaldo, test o plan de rollback.
6. No se modifican contratos de API pública sin compatibilidad hacia atrás, salvo decisión explícita.
7. No se toca el keystore, firma, permisos o backup sin validación especial.
8. No se introduce una mejora que pueda degradar estabilidad sin evaluación previa.

### 1.2 Reparación segura

1. Cada hallazgo tendrá severidad, riesgo y plan de reparación.
2. Cada reparación tendrá validación.
3. Cada cambio debe ser pequeño, reversible y verificable.
4. Se prioriza estabilidad antes que estética del código.
5. Se evita refactorizar y agregar features al mismo tiempo.
6. Primero se protege el comportamiento actual, luego se mejora.

### 1.3 Potenciación

1. Se potenciará todo lo que aporte valor real: seguridad, rendimiento, mantenibilidad, UX y estabilidad.
2. No se potenciará por estética si implica riesgo innecesario.
3. Toda potenciación debe tener beneficio claro y verificable.
4. Se prioriza:
   - Seguridad.
   - Estabilidad.
   - Rendimiento.
   - Arquitectura.
   - Testeabilidad.
   - UX.
   - Tamaño de app.
   - Batería.
   - Developer experience.

---

## 2. MEMORIA MAESTRA DEL PLAN

Esta sección funciona como memoria oficial del proyecto. Debe actualizarse después de cada fase, hallazgo importante, reparación o decisión.

```yaml
memoria_maestra:
  proyecto: Aura Hi-Res Player (port modelo SimpMusic)
  repositorio: echomusic (local)
  rama_principal: main
  rama_auditoria: main (la auditoría consolida directo en main; commits 88cf0e1, f7022e2, 1e5f418)
  commit_base: 1e5f418
  fecha_inicio: 2026-08-23
  version_plan: 1.0
  estado_global: EN_CURSO

  fases:
    fase_0_preparacion: EN_CURSO   # build verde y checkpoints; falta rama formal y base de tests
    fase_1_inventario: COMPLETADA        # 2026-08-24, resultados R1–R9; HALLAZGO-008
    fase_2_dependencias: NO_INICIADA
    fase_3_seguridad_estatica: EN_CURSO   # pase rápido: secretos y logs (HALLAZGO-003/007); falta SQL/entradas
    fase_4_manifest_config: COMPLETADA    # HALLAZGO-005 (manifiesto completo leído, todo legítimo)
    fase_5_almacenamiento: NO_INICIADA
    fase_6_red: NO_INICIADA
    fase_7_auth_cripto: NO_INICIADA
    fase_8_ipc_componentes: EN_CURSO      # PendingIntents verificados (HALLAZGO-004); faltan deep links/intents
    fase_9_webview: COMPLETADA            # inventario completo; HALLAZGO-006 riesgo aceptado
    fase_10_arquitectura: EN_CURSO        # HALLAZGO-001 corregido (commit 88cf0e1)
    fase_11_calidad_codigo: NO_INICIADA
    fase_12_concurrencia: EN_CURSO        # HALLAZGO-002 corregido (commit f7022e2)
    fase_13_rendimiento: NO_INICIADA
    fase_14_ui_ux_tecnica: NO_INICIADA
    fase_15_recursos_build: EN_CURSO      # debug 0.6.232 (952) verde; faltan release, lint, tests
    fase_16_testing: NO_INICIADA
    fase_17_cicd: NO_INICIADA
    fase_18_privacidad: NO_INICIADA
    fase_19_dinamica: NO_INICIADA
    fase_20_resiliencia: NO_INICIADA
    fase_21_reporte_final: NO_INICIADA
    fase_22_reparacion_potenciacion: NO_INICIADA

  seguridad:
    backup_creado: false
    pruebas_base_verdes: false
    flujos_criticos_protegidos: false
    riesgos_criticos_abiertos: 0
    riesgos_altos_abiertos: 0

  calidad:
    compila: true    # assembleUniversalFossDebug verde: 0.6.232 (versionCode 952)
    prueba_beta: BETA-001_VERDE   # 2026-08-24, dueño, remoto; fixes #154/#155 confirmados
    lint_ok: false
    unit_tests_ok: false
    android_tests_ok: false
    coverage_registrado: false

  decisiones_criticas: []
  bloqueos_activos: []
  proxima_accion: FASE_2_DEPENDENCIAS (FASE_1 completada 2026-08-24; HALLAZGO-008 abierto)
```

---

## 3. PROTOCOLO DE MEMORIA

Para que la auditoría se cumpla completa, se aplica este protocolo:

1. Cada avance debe registrar:
   - Fase actual.
   - Hallazgos encontrados.
   - Reparaciones aplicadas.
   - Pruebas ejecutadas.
   - Decisiones tomadas.
   - Riesgos pendientes.
   - Próxima acción.

2. No se marca una fase como completada si:
   - No se ejecutaron las verificaciones mínimas.
   - No se registraron los hallazgos.
   - No se definieron las reparaciones.
   - No se validó que no se rompió funcionalidad crítica.

3. Toda reparación debe actualizar:
   - Memoria de hallazgos.
   - Memoria de decisiones.
   - Memoria de pruebas.
   - Estado de la fase.

4. Si se interrumpe la auditoría:
   - Se retoma desde `fase_actual`.
   - Se revisa `proxima_accion`.
   - Se revisan `bloqueos_activos`.
   - Se continúa sin reiniciar el plan.

5. Si hay conflicto entre avanzar rápido y no romper:
   - Gana no romper.

---

## 4. MEMORIA DE FUNCIONES PROTEGIDAS

Estas son las funciones que deben permanecer operativas durante toda la auditoría. Deben completarse con las funciones reales del proyecto.

```yaml
funciones_protegidas:
  criticas:
    - PENDIENTE_LOGIN
    - PENDIENTE_REGISTRO
    - PENDIENTE_LOGOUT
    - PENDIENTE_NAVEGACION_PRINCIPAL
    - PENDIENTE_PERFIL
    - PENDIENTE_PAGOS
    - PENDIENTE_NOTIFICACIONES
    - PENDIENTE_ACTUALIZACION_DATOS

  importantes:
    - PENDIENTE_BUSQUEDA
    - PENDIENTE_FILTROS
    - PENDIENTE_LISTADOS
    - PENDIENTE_DETALLES
    - PENDIENTE_FORMULARIOS
    - PENDIENTE_UPLOAD_IMAGENES
    - PENDIENTE_DESCARGAS

  secundarias:
    - PENDIENTE_SETTINGS
    - PENDIENTE_THEME
    - PENDIENTE_ONBOARDING
    - PENDIENTE_ANALYTICS
```

Cada función protegida debe tener al menos una de estas salvaguardas:

- Test unitario.
- Test de integración.
- Test UI.
- Smoke manual documentado.
- Golden output.
- Screenshot test.
- Registro de comportamiento esperado.

---

## 5. FASES DE LA AUDITORÍA

---

# FASE 0 — PREPARACIÓN, RESPALDO Y RED DE SEGURIDAD

## Objetivo

Preparar el proyecto para que la auditoría y reparación se ejecuten sin riesgo.

## Acciones

1. Definir alcance:
   - Proyecto.
   - Rama.
   - Commit base.
   - Módulos incluidos.
   - Flujos críticos.
   - Entornos.
   - Fuera de alcance.

2. Crear rama de trabajo:

```bash
git checkout -b audit/super-audit
```

3. Crear commit base:

```bash
git add .
git commit -m "chore: base para auditoría completa"
```

4. Verificar que el proyecto compila:

```bash
./gradlew clean assembleDebug assembleRelease
```

5. Ejecutar pruebas existentes:

```bash
./gradlew test
./gradlew connectedDebugAndroidTest
```

6. Si no hay pruebas suficientes:
   - Crear smoke tests.
   - Documentar flujos críticos.
   - Registrar comportamiento esperado antes de tocar código.

7. Crear lista de funciones protegidas.

8. Registrar estado inicial en memoria maestra.

## Criterio de salida

```yaml
fase_0_preparacion: COMPLETADA
backup_creado: true
compila: true
pruebas_base_verdes: true
flujos_criticos_identificados: true
```

---

# FASE 1 — INVENTARIO COMPLETO

## Objetivo

Crear un mapa total del proyecto y superficie de ataque.

## Acciones

1. Inventariar módulos Gradle.
2. Inventariar Activities, Fragments, Services, Receivers, Providers.
3. Inventariar permisos.
4. Inventariar dependencias.
5. Inventariar SDKs externos.
6. Inventariar endpoints.
7. Inventariar bases de datos y almacenamiento.
8. Inventariar secretos, API keys, tokens, configuraciones.
9. Inventariar deep links, app links, WebView y JS bridges.
10. Inventariar CI/CD.

## Entregable

```yaml
inventario:
  modulos: []
  componentes_android: []
  permisos: []
  dependencias: []
  endpoints: []
  almacenamiento: []
  secretos_detectados: []
  deep_links: []
  webviews: []
  sdks_externos: []
```

## RESULTADOS DE LA FASE 1 (2026-08-24)

### R1. Módulos Gradle (16, según settings.gradle.kts)

| Módulo | Rol |
|---|---|
| `:app` | App principal (Compose UI, reproducción, EQ Superpowered, licencia, widgets) |
| `:innertube` | Cliente InnerTube/YouTube Music (metadatos y streaming) |
| `:simpmusic` | Cliente SimpMusic (letras; capa de streaming port) |
| `:migration` | Importación de playlists (Tidal/Deezer/Apple/archivo → YTM) |
| `:canvas` | Providers de canvas (Monochrome API, fondo artista Apple Music, Tidal) |
| `:applecanvas` | Provider de canvas Apple Music |
| `:echomusiccanvas` | Provider de canvas propio (Echo Music) |
| `:artistvideo` | Videos de artista como canvas |
| `:kugou` / `:lrclib` / `:betterlyrics` / `:youlyplus` / `:paxsenixlyrics` / `:unison` | Proveedores de letras |
| `:shazamkit` | Reconocimiento de música |
| `:jiosaavn` | Cliente API JioSaavn |

### R2. Componentes de manifiesto (todos en `app/src/main/AndroidManifest.xml`)

| Tipo | Clase | Exportado | Filtros / permisos | Riesgo |
|---|---|---|---|---|
| activity | `.ui.screens.CrashActivity` | no | proceso `:crash` | bajo |
| activity | `.MainActivity` | ⚠️ sí, sin permiso | MAIN; VIEW audio; app links (youtube.com, youtu.be, vnd.youtube, echomusic://, listen-together) | media — superficie IPC principal, auditar entradas en FASE 8 |
| activity-alias | `.MainActivityAlias` | ⚠️ sí | MAIN + LAUNCHER + LEANBACK | bajo |
| activity-alias | `.MainActivityStatic` | sí (enabled=false) | MAIN + LAUNCHER | bajo |
| activity | `com.yalantis.ucrop.UCropActivity` | no | recorte de imagen | bajo |
| activity | `.recognition.RecognitionLaunchActivity` | no | trampolín tile | bajo |
| service | `.playback.MusicService` | ⚠️ sí, sin permiso | MediaLibraryService/MediaBrowser (FGS mediaPlayback) | media — patrón estándar media3 (Android Auto), auditar comandos en FASE 8 |
| service | `.playback.ExoDownloadService` | no | FGS dataSync | bajo |
| service | `.playback.AudioExportService` | no | FGS dataSync | bajo |
| service | `.widget.MusicRecognizerWidgetService` | no | FGS microphone | bajo |
| service | `.recognition.RecognitionForegroundService` | no | FGS microphone | bajo |
| service | `.widget.RecognitionTileService` | sí, con permiso | BIND_QUICK_SETTINGS_TILE | bajo |
| service | WorkManager `SystemForegroundService` | merge | FGS dataSync | infraestructura |
| receiver | MediaButtonReceiver | ⚠️ sí, sin permiso | MEDIA_BUTTON | bajo — patrón obligatorio media3 |
| receiver | 4× widget receivers (Music/Turntable/Playlist/Recognizer) | ⚠️ sí, sin permiso | APPWIDGET_UPDATE + acciones propias | bajo — patrón obligatorio de widgets; las acciones custom se validan en FASE 8 |
| receiver | `.listentogether.ListenTogetherActionReceiver` | no | acciones de notificación | bajo |
| provider | FileProvider | no, con grants URI | authority `${applicationId}.FileProvider` | auditar paths en FASE 5 |
| provider | `com.dpi.DensityScaler` | no | density | bajo |

Permisos solicitados: INTERNET, POST_NOTIFICATIONS, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, RECEIVE_BOOT_COMPLETED, WAKE_LOCK, FOREGROUND_SERVICE (+MEDIA_PLAYBACK/DATA_SYNC/MICROPHONE), REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, RECORD_AUDIO, READ_MEDIA_AUDIO, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE (maxSdk 28), WRITE_SETTINGS, BLUETOOTH_CONNECT, BLUETOOTH/BLUETOOTH_ADMIN (maxSdk 30), REQUEST_INSTALL_PACKAGES (solo gms; el flavor foss lo quita). Permisos declarados (`<permission>`): ninguno. A justificar en FASE 4/18: RECEIVE_BOOT_COMPLETED, WRITE_SETTINGS, BLUETOOTH_*.

### R3. Flavors / build types

| Elemento | Detalle | applicationId |
|---|---|---|
| flavor `foss` (default) | sin GMS/Cast | `iad1tya.aura.music` |
| flavor `gms` | con Cast/Crashlytics | `iad1tya.aura.music` |
| abi: universal/arm64/armeabi/x86/x86_64 | abiFilters por arquitectura | idem |
| build type `release` | minify+shrink | ⚠️ firma con keystore DEBUG (ver HALLAZGO-008) |
| build type `debug` | debuggable | `iad1tya.aura.music.debug` |
| `-Pnosub=true` | sin puerta de suscripción | `iad1tya.aura.music.dev` |

Nota: `namespace` sigue siendo `iad1tya.echo.music` (deliberado, herencia del fork). AGENTS.md sigue citando `iad1tya.echo.music.dev` para nosub: desactualizado (hoy es `iad1tya.aura.music.dev`).

### R4. Pantallas / navegación (NavHost en `ui/screens/NavigationBuilder.kt`)

| Ruta | Composable | Flujo crítico |
|---|---|---|
| `home` | HomeScreenHost (clásica/Aura según flag) | no |
| `novedades` (NUEVA, solo UI nueva) | NovedadesScreenHost | no |
| `search_input`, `search/{query}` | SearchScreenHost / SearchResultHost | no |
| `library` | LibraryScreenHost | no |
| `listen_together` (+`/chat`) | ListenTogetherScreen / CommentTogetherScreen | no |
| `history` | HistoryScreen | no |
| `local_songs` | LocalSongScreen | descargas |
| `favorite_albums`, `release_radar`, `stats`, `mood_and_genres` | hosts varios | no |
| `account` | AccountScreen | CUENTA |
| `new_release` | NewReleaseScreen | MUERTA (sin navigate) |
| `browse/{browseId}`, `album/{albumId}`, `artist/{artistId}` (+`/songs`, `/albums`, `/items`, `artist_section_buffer`) | hosts varios | no |
| `online_playlist/{id}`, `local_playlist/{id}`, `auto_playlist/{p}`, `cache_playlist/{p}`, `top_playlist/{top}` | hosts de listas | descargas |
| `youtube_browse/{id}` | YouTubeBrowseScreen | reproducción |
| `settings` + ~25 sub-rutas (update, accounts, lastfm, qobuz, appearance(+theme/liquidglass), content(+romanization), uptime, ai, player, youtube_decryption, sound(+autoeq), performance, storage, equalizer, privacy, backup_restore, spotify_import, ytm_sync, integrations/listen_together, about, feedback, terms, logs, changelog, notices) | SettingsScreenHost y subpantallas | cuenta/backup/licencia según ruta |
| `migration` (+`/tidal`, `/apple`) | MigrationScreenHost | CUENTA |
| `update` | UpdateScreen | actualización |
| `login` | LoginScreen | CUENTA |
| `onboarding_artists/_genres/_spotify/_youtube` | onboarding | cuenta (spotify/youtube) |
| `podcasts?feedUrl` | PodcastScreen | descargas |
| `recognition`, `recognition_history` | RecognitionScreen | no |
| `ambient_mode` | AmbientModeScreen | reproducción |

Fuera del NavHost: `TermsGate` (legal/), `LicenseGate`+`LicenseScreens` (license/) = flujo LICENCIA; `BottomSheetPlayer`/`NowPlayingSidePanel`/cola/letras = flujo REPRODUCCIÓN.

Divergencias contra `docs/UI_INVENTORY.md` (4 133 líneas, cubre la UI clásica): (1) la UI NUEVA completa (`ui/newui/`, 43 archivos: AuraShell con 4 pestañas, AuraPlayer, AuraQueue, AuraLyricsScreen, NewUiGate, OwnerNoticePopup...) NO está en el doc y hoy es capa de routing real vía hosts, gateada por `rememberNewUiEnabled()`; (2) rutas nuevas `novedades`, `settings/notices`; (3) renombrada `artist/section_buffer` → `artist_section_buffer`; (4) eliminadas por higiene `charts_screen`, `settings/account`, `settings/integrations`; (5) siguen muertas `new_release`, `listen_together_from_topbar`; (6) `LIQUID_GLASS_ROUTE` rebota solo con flag de UI nueva ON.

### R5. Diálogos / hojas

| Elemento | Archivo | Nota |
|---|---|---|
| `SettingDialoge` (cuenta/login/sync YTM) | ui/screens/SettingDialoge.kt | hoja principal de cuenta |
| `WelcomeDialog`, `BackgroundReliabilityDialog` | ui/screens/ | arranque |
| `BottomSheetMenu` / `BottomSheetPage` globales | ui/component/ | |
| Reproductor: hoja + menús player/cola/canción/letras | ui/player/, ui/menu/ | coincide con doc §2–§8 |
| Biblioteca/Álbum/Listas: CreatePlaylistDialog, AiPlaylistDialog, AddMusicSheet, AiModifyPlaylistDialog, ShuffleMemoryPrompt, LocalSongScanSheet | ui/screens/library/, ui/menu/ | doc §22.1 |
| Ajustes: ~50 diálogos (Apariencia, Reproductor, Contenido, IA, Almacenamiento, Backup SAF ×8, Cuentas, Scrobbling, ListenTogether) | ui/screens/settings/ | doc §22.2 |
| Puertas `TermsGate` / `LicenseGate` | legal/, license/ | fullscreen, fuera de NavHost |
| `NewUiGate`, `OwnerNoticePopup` | ui/newui/ | NUEVOS, no están en el doc |

### R6. Widgets / notificaciones

Sin Glance: todos AppWidgetProvider + RemoteViews. Widgets: reproductor (3 layouts), tocadiscos (⚠️ botón «me gusta» descrito en doc pero no cableado), listas de reproducción, reconocer música (3 tamaños, FGS micrófono), tile QS de reconocimiento, atajos launcher (Buscar + Biblioteca).

| Canal de notificación | Builder | Nota |
|---|---|---|
| `music_channel_01` | MusicService (MediaStyle + like/repeat/shuffle/radio) | |
| `download` | ExoDownloadService | |
| `download_progress_channel`, `app_updates` | updater (descarga APK + aviso id 2003) | flujo actualización |
| `updates` | creado en App.kt:609 | ⚠️ HUÉRFANO: ningún builder lo usa |
| `listen_together_channel` | ListenTogetherClient | |
| `release_radar` | ReleaseRadarWorker | |
| `recognition_channel`, `music_recognizer_widget` | FGS de reconocimiento | micrófono |
| `spotify_import` | SpotifyImportManager | |
| `export` | AudioExportService | |
| `local_downloads` | LocalFileDownloader | |

### R7. Workers / servicios / loops de larga vida

Workers WorkManager: YtmSyncWorker (one-time, sync YTM), YtmAutoSyncWorker (periódico 3 días), UpdateCheckWorker (periódico 6 h, `releases/latest` GitHub), UpdateDownloadWorker (foreground dataSync, descarga APK reanudable), LastFmTasteWorker, AutoRecoPlaylistWorker, ReleaseRadarWorker (7 días), SpotifyAutoSyncWorker. Sin AlarmManager; periodicidad reafirmada en `App.onCreate`.

FGS: MusicService (mediaPlayback), ExoDownloadService + AudioExportService (dataSync), MusicRecognizerWidgetService + RecognitionForegroundService (microphone), SystemForegroundService (WorkManager).

Loops relevantes (MusicService salvo indicación): persistencia cola 30 s / posición 10 s (:2815/:2826); startPeriodicPersist 5 s (:6121, loudness + podcast); SponsorBlock watcher 1 s (:6020, solo si activo); refresco widgets adaptativo 1/30/60 s (:10183, gateado); crossfade fade (:11165); ScrobbleManager 1 s (solo playing); ThermalManager poll 10 s ref-counted; DownloadUtil progreso 500 ms; ping ListenTogether; inventario caché (CachePlaylistViewModel); anuncios del owner 1 h (MainActivity:433); loops visuales Compose (vida de composición). Batería: los loops de MusicService ya traen auditoría previa (registro #55); SponsorBlock corre en Main 1 s solo con feature activo.

### R8. Almacenamiento

| Ítem | Detalle | Sensible |
|---|---|---|
| Room `song.db` (v42, WAL) | Song, Artist, Album, Playlist, mapas, SearchHistory, FormatEntity, LyricsEntity, Event, PlayCount, RecognitionHistory, SpeedDial, ReleaseRadarItem, UpcomingRelease, EnhancedShuffle*; DatabaseDao + SpeedDialDao | sí (historial, búsquedas, gustos) |
| Room `migration_match_cache.db` | caché desechable del resolver de migración | no |
| DataStore `"settings"` | incluye `InnerTubeCookieKey`, AccountName/Email/ChannelHandle, VisitorData, DataSyncId | ⚠️ SÍ — cookie de cuenta en texto plano (FASE 5/18) |
| EncryptedSharedPreferences | tokens Tidal y Qobuz | sí, cifrado |
| SharedPreferences varios | `jr_license` (clave de suscripción), `echo_eq_prefs`, `app_prefs`, install-marker, prefs widget | ⚠️ `jr_license` en plano (zona license/, no tocar; documentar en FASE 18) |
| filesDir | `logs/app.log` (+rotación, last_crash), `exoplayer/`, `download/`, cola/automix/player-state, `player_configs_cache.json`, APK del updater | mixto; log redactado vía LogRedaction.kt |
| cacheDir | imágenes Coil, album-art widget | no |
| externo | MediaStore solo lectura; SAF Uri del usuario para backup/export/ringtone | no escribe fuera sin SAF |

### R9. Flujos críticos

| Flujo | Entrada | Cadena principal | Estado |
|---|---|---|---|
| Reproducción | playback/MusicService.kt | resolve utils/YTPlayerUtils.kt + utils/cipher/ → UI ui/player/Player.kt | PARCIAL (P1/P4 streaming ya auditados) |
| Descarga offline | playback/DownloadUtil.kt + ExoDownloadService.kt | CachePlaylistViewModel, AuraDownloadsScreen | PARCIAL (registro #91 chunking) |
| Login de cuenta | ui/screens/LoginScreen.kt (WebView Google) | cookie → DataStore → SyncUtils.sync* | NO_AUDITADO (FASE 7/9) |
| Backup/restore | viewmodels/BackupRestoreViewModel.kt | zip (Room + settings.preferences_pb) vía SAF; restaurar preserva cookie | NO_AUDITADO (FASE 5) |
| Licencia/suscripción | license/LicenseGate.kt → LicenseManager.evaluate | LicenseBackendClient (verify/demo por DeviceId), estado en `jr_license` | ZONA PROTEGIDA — solo documentar |
| Letras | ui/component/Lyrics.kt → lyrics/LyricsUtils.kt | cadena de providers → LyricsEntity en song.db | NO_AUDITADO |
| Actualización in-app | echomusic/updater/UpdateCheckWorker.kt | UpdateDownloadWorker + REQUEST_INSTALL_PACKAGES | NO_AUDITADO (FASE 17) |

## Reparación asociada

Si se detecta algo fuera de control:

- Documentar.
- Clasificar riesgo.
- Priorizar en fase correspondiente.

## Criterio de salida

```yaml
fase_1_inventario: COMPLETADA
```

---

# FASE 2 — AUDITORÍA DE DEPENDENCIAS

## Objetivo

Detectar dependencias vulnerables, obsoletas o peligrosas.

## Acciones

1. Revisar:
   - `build.gradle`
   - `build.gradle.kts`
   - `settings.gradle`
   - `settings.gradle.kts`
   - `gradle/libs.versions.toml`
   - `gradle-wrapper.properties`

2. Detectar:
   - CVEs.
   - Versiones antiguas.
   - Librerías abandonadas.
   - Conflictos.
   - Repositorios inseguros.
   - Plugins sospechosos.
   - SDKs con recolección excesiva de datos.

3. Ejecutar análisis:

```bash
./gradlew dependencyCheckAnalyze
```

O alternativas:

```bash
osv-scanner scan .
trivy fs .
```

## Reparación segura

1. Actualizar primero dependencias críticas con CVE alto/crítico.
2. Actualizar una por una.
3. Compilar y probar después de cada actualización.
4. Si una actualización rompe:
   - Revertir.
   - Documentar.
   - Buscar versión segura compatible.
   - Crear test para reproducir el problema.

## Memoria

```yaml
dependencias:
  criticas_vulnerables: []
  actualizadas: []
  revertidas: []
  pendientes: []
```

## Criterio de salida

```yaml
fase_2_dependencias: COMPLETADA
```

---

# FASE 3 — AUDITORÍA DE SEGURIDAD ESTÁTICA

## Objetivo

Detectar vulnerabilidades en código fuente.

## Puntos críticos

- Secretos embebidos.
- Logs con datos sensibles.
- SQL injection.
- Validación de entradas.
- Uso inseguro de criptografía.
- Componentes exportados.
- Intents inseguros.
- WebView inseguro.
- Manejo inseguro de archivos.
- Uso inseguro de Random, MD5, SHA1, AES/ECB.
- Tokens en texto plano.
- Contraseñas en texto plano.

## Acciones

1. Buscar secretos:

```bash
grep -R "apiKey" .
grep -R "api_key" .
grep -R "secret" .
grep -R "password" .
grep -R "token" .
grep -R "BEGIN PRIVATE KEY" .
```

2. Buscar logs sensibles:

```bash
grep -R "Log.d" .
grep -R "Log.e" .
grep -R "println" .
grep -R "Timber.d" .
```

3. Revisar SQL:

```bash
grep -R "rawQuery" .
grep -R "execSQL" .
```

4. Revisar WebView:

```bash
grep -R "addJavascriptInterface" .
grep -R "allowFileAccess" .
grep -R "javaScriptEnabled" .
```

5. Revisar TrustManager y HostnameVerifier inseguros:

```bash
grep -R "X509TrustManager" .
grep -R "HostnameVerifier" .
```

## Reparación segura

| Problema | Reparación recomendada |
|---|---|
| Secreto hardcoded | Mover a variable segura, local.properties no versionado, CI secrets, Android Keystore o servidor. |
| Log sensible | Eliminar log o enmascarar dato. Solo logs en debug si no contienen PII. |
| SQL injection | Usar queries parametrizadas, Room seguro, `SupportSQLiteQuery` con bind args. |
| WebView inseguro | Restringir URLs, deshabilitar JS si no es necesario, eliminar JS bridge peligroso, validar entrada. |
| TrustManager vacío | Implementar validación correcta de certificados. |
| HostnameVerifier inseguro | Usar verificación estándar. |
| AES/ECB | Cambiar a AES/GCM/NoPadding con IV aleatorio y claves en Keystore. |

## Criterio de salida

```yaml
fase_3_seguridad_estatica: COMPLETADA
```

---

# FASE 4 — AUDITORÍA DE MANIFIESTO Y CONFIGURACIÓN

## Objetivo

Detectar configuraciones inseguras en AndroidManifest y recursos.

## Acciones

Revisar:

```xml
android:allowBackup
android:debuggable
android:usesCleartextTraffic
android:exported
android:permission
android:authorities
android:grantUriPermissions
```

Revisar Network Security Config:

```xml
<network-security-config>
```

## Reparación segura

1. En release:
   - `debuggable=false`
   - `allowBackup` justificado.
   - `usesCleartextTraffic=false` si no se necesita HTTP.
   - Componentes exportados solo si son necesarios.
   - ContentProviders protegidos.
   - BroadcastReceivers protegidos si manejan acciones sensibles.

2. Network Security Config mínima segura:

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

3. Si se necesita HTTP específico:
   - Solo dominios explícitos.
   - Justificación documentada.
   - Nunca permitir todo HTTP sin motivo.

## Criterio de salida

```yaml
fase_4_manifest_config: COMPLETADA
```

---

# FASE 5 — AUDITORÍA DE ALMACENAMIENTO

## Objetivo

Asegurar que los datos sensibles no se guarden mal.

## Puntos críticos

- SharedPreferences.
- EncryptedSharedPreferences.
- Room.
- SQLite.
- DataStore.
- Archivos internos/externos.
- Caché.
- Backups.

## Acciones

1. Buscar almacenamiento inseguro:

```bash
grep -R "SharedPreferences" .
grep -R "putString" .
grep -R "openFileOutput" .
grep -R "getExternalFilesDir" .
grep -R "Environment.getExternalStorageDirectory" .
```

2. Revisar qué datos se guardan:
   - Tokens.
   - Passwords.
   - Datos personales.
   - Sesiones.
   - Caché sensible.
   - Archivos temporales.

## Reparación segura

| Problema | Reparación |
|---|---|
| Token en SharedPreferences plano | Usar EncryptedSharedPreferences o Android Keystore. |
| Password guardado localmente | No guardar password. Usar token/refresh token seguro. |
| Archivo sensible externo | Mover a almacenamiento interno o cifrar. |
| Base de datos sin cifrar con datos sensibles | Evaluar SQLCipher o protección según requisito. |
| Caché sensible | Definir expiración y limpieza. |
| Backup inseguro | Excluir datos sensibles en `fullBackupContent`/`dataExtractionRules`. |

## Criterio de salida

```yaml
fase_5_almacenamiento: COMPLETADA
```

---

# FASE 6 — AUDITORÍA DE RED

## Objetivo

Validar que toda comunicación sea segura.

## Puntos críticos

- HTTP inseguro.
- TLS mal validado.
- Certificate pinning mal implementado.
- Tokens en URL.
- Logs de red en release.
- Timeouts incorrectos.
- Manejo de errores pobre.
- Refresh token inseguro.
- Falta de reintentos seguros.
- Falta de cancelación de requests.

## Acciones

1. Revisar Retrofit/OkHttp/Ktor.
2. Revisar interceptors.
3. Revisar logging.
4. Revisar manejo de 401/403.
5. Revisar refresh token.
6. Revisar headers sensibles.
7. Revisar URLs hardcoded.

## Reparación segura

| Problema | Reparación |
|---|---|
| HTTP inseguro | Forzar HTTPS. |
| TrustManager vacío | Eliminar y usar validación correcta. |
| HostnameVerifier inseguro | Eliminar override inseguro. |
| Logging interceptor en release | Activar solo en debug. |
| Token en URL | Enviar en header seguro. |
| Refresh token inseguro | Usar almacenamiento seguro y flujo de renovación robusto. |
| Sin timeouts | Añadir connect/read/write timeouts razonables. |

## Criterio de salida

```yaml
fase_6_red: COMPLETADA
```

---

# FASE 7 — AUDITORÍA DE AUTENTICACIÓN Y CRIPTOGRAFÍA

## Objetivo

Revisar login, sesión, tokens, biometría, PIN, recuperación y criptografía.

## Acciones

1. Auditar flujo de login.
2. Auditar registro.
3. Auditar recuperación.
4. Auditar logout.
5. Auditar refresh token.
6. Auditar expiración de sesión.
7. Auditar biometría.
8. Auditar manejo de claves.
9. Auditar Keystore.
10. Auditar revocación.

## Reparación segura

- Nunca guardar contraseñas en texto plano.
- Nunca guardar tokens sensibles sin protección.
- Usar Android Keystore para claves.
- Usar algoritmos seguros.
- No usar MD5/SHA1 para seguridad.
- No usar `Random` para seguridad.
- No reutilizar IV.
- No usar AES/ECB.
- Usar expiración y renovación de tokens.
- Cerrar sesión correctamente.
- Invalidar sesión tras cambio de contraseña si aplica.

## Criterio de salida

```yaml
fase_7_auth_cripto: COMPLETADA
```

---

# FASE 8 — AUDITORÍA DE COMPONENTES E IPC

## Objetivo

Detectar componentes Android inseguros o mal protegidos.

## Componentes

- Activities.
- Services.
- BroadcastReceivers.
- ContentProviders.
- PendingIntents.
- Deep links.
- App links.
- Notifications.
- Widgets.
- WorkManager.

## Acciones

1. Buscar componentes exportados.
2. Revisar intent filters.
3. Revisar deep links.
4. Revisar PendingIntents.
5. Revisar ContentProviders.
6. Revisar permisos URI.
7. Revisar notificaciones con datos sensibles.

## Reparación segura

| Problema | Reparación |
|---|---|
| Componente exportado sin protección | Exportar solo si es necesario y proteger con permisos. |
| Deep link sin validación | Validar parámetros, rutas y estado de sesión. |
| PendingIntent mutable | Usar `FLAG_IMMUTABLE` cuando no se necesite mutabilidad. |
| ContentProvider abierto | Proteger con permisos y validar rutas. |
| Broadcast sensible público | Usar permisos, LocalBroadcastManager o mecanismos seguros. |

## Criterio de salida

```yaml
fase_8_ipc_componentes: COMPLETADA
```

---

# FASE 9 — AUDITORÍA DE WEBVIEW

## Objetivo

Detectar riesgos graves en WebView.

## Acciones

1. Buscar WebViews.
2. Verificar JavaScript habilitado.
3. Verificar `addJavascriptInterface`.
4. Verificar acceso a archivos.
5. Verificar URLs permitidas.
6. Verificar manejo de redirects.
7. Verificar cookies y caché.

## Reparación segura

- Deshabilitar JavaScript si no es necesario.
- Restringir dominios.
- Validar URLs.
- No exponer objetos sensibles a JS sin control.
- Deshabilitar acceso universal a archivos.
- Controlar `shouldOverrideUrlLoading`.
- Limpiar caché si contiene datos sensibles.

## Criterio de salida

```yaml
fase_9_webview: COMPLETADA
```

---

# FASE 10 — AUDITORÍA DE ARQUITECTURA

## Objetivo

Detectar problemas estructurales y proponer mejoras sin romper comportamiento.

## Aspectos

- Separación de capas.
- MVVM/MVI/Clean Architecture.
- Inyección de dependencias.
- Modularización.
- Navegación.
- Manejo de estado.
- Manejo de errores.
- Acoplamiento.
- Dependencias circulares.
- Testeabilidad.

## Reparación segura

1. No refactorizar en caliente sin tests.
2. Crear characterization tests primero.
3. Hacer refactors pequeños.
4. Mantener compatibilidad.
5. Extraer clases/módulos de forma incremental.
6. No mezclar refactor con cambio funcional.

## Potenciación

- Separar UI de lógica.
- Mejorar uso de ViewModel.
- Introducir UseCases si aportan claridad.
- Separar modelos de red, dominio y UI.
- Mejorar navegación.
- Hacer módulos más independientes.

## Criterio de salida

```yaml
fase_10_arquitectura: COMPLETADA
```

---

# FASE 11 — AUDITORÍA DE CALIDAD DE CÓDIGO

## Objetivo

Detectar code smells, malas prácticas y deuda técnica.

## Puntos críticos

- Uso excesivo de `!!`.
- Nullability mal gestionada.
- Casts inseguros.
- Código duplicado.
- Métodos gigantes.
- Clases gigantes.
- Magic numbers.
- Strings hardcoded.
- Excepciones silenciadas.
- Código muerto.
- Nombres ambiguos.
- Complejidad excesiva.

## Reparación segura

| Problema | Reparación |
|---|---|
| `!!` excesivo | Reemplazar por safe call, requireNotNull, elvis o flujo nullable seguro. |
| Método gigante | Extraer funciones pequeñas con tests. |
| Código duplicado | Extraer función/utilidad común. |
| Magic numbers | Constantes. |
| Strings hardcoded | Recursos strings si aplica. |
| Excepción silenciada | Manejar, loguear o propagar correctamente. |

## Criterio de salida

```yaml
fase_11_calidad_codigo: COMPLETADA
```

---

# FASE 12 — AUDITORÍA DE CONCURRENCIA Y CICLO DE VIDA

## Objetivo

Detectar condiciones de carrera, bloqueos, memory leaks y crashes.

## Puntos críticos

- Coroutines.
- Flows.
- LiveData.
- Callbacks.
- Threads.
- Handlers.
- Listeners.
- GlobalScope.
- Dispatchers incorrectos.
- Cancelación incorrecta.
- Fugas por contexto.
- WorkManager.

## Reparación segura

| Problema | Reparación |
|---|---|
| GlobalScope | Usar lifecycleScope/viewModelScope o scope controlado. |
| Flow no cancelado | Recolectar con lifecycle awareness. |
| Listener sin remover | Registrar y remover correctamente. |
| Main thread bloqueado | Mover trabajo pesado a background. |
| UI actualizada desde background | Usar mecanismos seguros de lifecycle. |
| Race condition | Mutex, estado inmutable o sincronización adecuada. |

## Criterio de salida

```yaml
fase_12_concurrencia: COMPLETADA
```

---

# FASE 13 — AUDITORÍA DE RENDIMIENTO

## Objetivo

Mejorar startup, UI, memoria, batería, red y base de datos.

## Acciones

1. Detectar trabajo en main thread.
2. Detectar memory leaks.
3. Detectar layouts costosos.
4. Detectar recomposition excesiva en Compose.
5. Detectar RecyclerView ineficiente.
6. Detectar imágenes pesadas.
7. Detectar queries lentas.
8. Detectar requests innecesarias.
9. Detectar startup lento.
10. Detectar uso excesivo de batería.

## Potenciación

- Baseline Profiles.
- App Startup.
- Lazy loading.
- Paging.
- DiffUtil.
- Cache HTTP.
- Optimización de imágenes.
- Índices en Room.
- Reducción de layouts anidados.
- Menos recomposition.
- Menos inicialización fría.

## Reparación segura

1. Medir antes.
2. Aplicar mejora.
3. Medir después.
4. Verificar que no haya regresión.

## Criterio de salida

```yaml
fase_13_rendimiento: COMPLETADA
```

---

# FASE 14 — AUDITORÍA DE UI/UX TÉCNICA

## Objetivo

Detectar problemas técnicos de interfaz que afectan estabilidad, rendimiento y usabilidad.

## Si es Compose

Revisar:

- State hoisting.
- Recomposition.
- `remember`.
- `derivedStateOf`.
- LazyColumn keys.
- Side effects.
- Estado no sobreviviente a configuración.
- Composables gigantes.

## Si es XML

Revisar:

- Layouts profundos.
- findViewById repetido.
- ViewBinding.
- Adapters ineficientes.
- RecyclerView.
- Inflado repetido.
- ConstraintLayout.

## Accesibilidad

- ContentDescription.
- Tamaño táctil.
- Contraste.
- RTL.
- Dark mode.
- Texto escalable.

## Reparación segura

- Cambios visuales pequeños.
- Pruebas de pantalla.
- Screenshot tests si es posible.
- Validación manual documentada.

## Criterio de salida

```yaml
fase_14_ui_ux_tecnica: COMPLETADA
```

---

# FASE 15 — AUDITORÍA DE RECURSOS Y BUILD

## Objetivo

Revisar recursos, build types, ofuscación y configuración de release.

## Acciones

1. Revisar build types.
2. Revisar minify.
3. Revisar shrinkResources.
4. Revisar ProGuard/R8.
5. Revisar recursos sin usar.
6. Revisar strings hardcoded.
7. Revisar temas, colores, dark mode, RTL.
8. Revisar tamaño de recursos.
9. Revisar configuración de firma.

## Reparación segura

Release recomendado si aplica:

```kotlin
isMinifyEnabled = true
isShrinkResources = true
isDebuggable = false
```

Precaución:

- Antes de activar R8/Minify, verificar reglas ProGuard.
- Probar release después de activar.
- Mantener reglas necesarias para serialización, reflexión, SDKs y JNI.

## Criterio de salida

```yaml
fase_15_recursos_build: COMPLETADA
```

---

# FASE 16 — AUDITORÍA DE TESTING

## Objetivo

Asegurar que existe una red de pruebas suficiente para reparar sin romper.

## Acciones

1. Identificar cobertura actual.
2. Identificar tests rotos.
3. Identificar tests flaky.
4. Identificar ausencia de tests críticos.
5. Crear tests donde no existen.
6. Definir suite mínima de seguridad.

## Suite mínima

- Unit tests de lógica crítica.
- Tests de repositorios.
- Tests de UseCases.
- Tests de ViewModels.
- Tests de Room.
- Tests de navegación.
- Tests de deep links.
- UI smoke tests.
- Tests de errores.
- Tests de estados límite.

## Reparación segura

Si no hay tests:

1. No refactorizar profundo todavía.
2. Crear characterization tests.
3. Documentar comportamiento esperado.
4. Aplicar cambios pequeños.
5. Validar manualmente si no se puede automatizar.

## Criterio de salida

```yaml
fase_16_testing: COMPLETADA
```

---

# FASE 17 — AUDITORÍA DE CI/CD

## Objetivo

Validar que el pipeline construye, prueba y entrega de forma segura.

## Acciones

1. Revisar CI:
   - GitHub Actions.
   - GitLab CI.
   - Bitrise.
   - Jenkins.
   - CircleCI.
   - Codemagic.

2. Verificar:
   - Build automático.
   - Tests automáticos.
   - Lint.
   - Static analysis.
   - Dependency scan.
   - Firma segura.
   - Secrets seguros.
   - Artefactos versionados.
   - Publicación controlada.

## Reparación segura

- Nunca guardar keystore o secretos en texto plano dentro del repo.
- Usar secrets de CI.
- Proteger ramas principales.
- Requerir checks verdes.
- Generar APK/AAB firmado de forma segura.
- Mantener pipeline reproducible.

## Criterio de salida

```yaml
fase_17_cicd: COMPLETADA
```

---

# FASE 18 — AUDITORÍA DE PRIVACIDAD

## Objetivo

Detectar recolección, exposición o retención excesiva de datos personales.

## Acciones

1. Revisar permisos.
2. Revisar logs.
3. Revisar analytics.
4. Revisar crashlytics.
5. Revisar backups.
6. Revisar SDKs externos.
7. Revisar consentimiento.
8. Revisar eliminación de cuenta.
9. Revisar retención de datos.
10. Revisar datos enviados a terceros.

## Reparación segura

- Minimizar permisos.
- Enmascarar o eliminar PII de logs.
- No enviar PII innecesaria a analytics.
- Excluir datos sensibles de backups.
- Revisar configuración de SDKs externos.
- Documentar consentimiento si aplica.

## Criterio de salida

```yaml
fase_18_privacidad: COMPLETADA
```

---

# FASE 19 — AUDITORÍA DINÁMICA

## Objetivo

Verificar comportamiento en runtime y detectar problemas no visibles en código.

## Acciones

1. Inspeccionar APK/AAB.
2. Revisar permisos reales.
3. Revisar componentes reales.
4. Verificar firma.
5. Probar deep links.
6. Probar intents.
7. Pro WebView.
8. Probar backup.
9. Probar almacenamiento local.
10. Probar tráfico de red.
11. Probar sesión.
12. Probar errores.

## Comandos útiles

```bash
apktool d app-release.apk -o apktool_output
jadx -d jadx_output app-release.apk
apksigner verify --print-certs app-release.apk
aapt dump permissions app-release.apk
aapt dump badging app-release.apk
```

## Reparación segura

Cada hallazgo dinámico debe:

- Reproducirse.
- Documentarse.
- Clasificar severidad.
- Repararse en código.
- Volverse a probar.

## Criterio de salida

```yaml
fase_19_dinamica: COMPLETADA
```

---

# FASE 20 — AUDITORÍA DE RESILIENCIA Y OFUSCACIÓN

## Objetivo

Mejorar protección frente a ingeniería inversa, manipulación y abuso.

## Puntos críticos

- R8 activo.
- Ofuscación.
- Eliminación de logs.
- Debug info.
- Root detection si aplica.
- Emulator detection si aplica.
- Play Integrity si aplica.
- Anti-tampering si aplica.
- Keystore seguro.
- Protección de secretos en runtime.

## Reparación segura

- No agregar seguridad teatral sin valor real.
- Priorizar protección real de datos y flujos.
- Mantener compatibilidad con dispositivos legítimos.
- No romper flujos por detección excesiva.

## Criterio de salida

```yaml
fase_20_resiliencia: COMPLETADA
```

---

# FASE 21 — REPORTE FINAL

## Objetivo

Consolidar todos los hallazgos, reparaciones y mejoras.

## Contenido del reporte

1. Resumen ejecutivo.
2. Estado de cada fase.
3. Hallazgos críticos.
4. Hallazgos altos.
5. Hallazgos medios.
6. Hallazgos bajos.
7. Reparaciones aplicadas.
8. Reparaciones pendientes.
9. Riesgos aceptados.
10. Mejoras implementadas.
11. Potenciaciones aplicadas.
12. Pruebas realizadas.
13. Recomendaciones futuras.

## Formato de hallazgo

```yaml
hallazgo:
  id: SEC-001
  severidad: CRITICA
  categoria: Seguridad
  archivo: ejemplo.kt
  linea: 10
  descripcion: Ejemplo de problema
  impacto: Ejemplo de impacto
  evidencia: fragmento de código
  cwe: CWE-xxx
  reparacion: acción recomendada
  estado: PENDIENTE
```

## Criterio de salida

```yaml
fase_21_reporte_final: COMPLETADA
```

---

# FASE 22 — REPARACIÓN Y POTENCIACIÓN CONTROLADA

## Objetivo

Aplicar todas las reparaciones y mejoras sin romper el proyecto.

## Orden obligatorio de reparación

1. Estabilidad y compilación.
2. Red de pruebas y observabilidad.
3. Seguridad crítica.
4. Errores funcionales graves.
5. Dependencias vulnerables.
6. Almacenamiento inseguro.
7. Red insegura.
8. Autenticación y tokens.
9. WebView e IPC.
10. Concurrencia y memory leaks.
11. Rendimiento.
12. Arquitectura.
13. Calidad de código.
14. UI/UX técnica.
15. CI/CD.
16. Privacidad.
17. Resiliencia.
18. Potenciaciones adicionales.

## Flujo de reparación segura

Para cada hallazgo:

```text
1. Detectar hallazgo
2. Clasificar severidad
3. Crear prueba o validación de comportamiento
4. Aplicar reparación mínima
5. Ejecutar pruebas
6. Validar funcionalidad crítica
7. Si rompe: revertir
8. Si no rompe: aplicar potenciación opcional
9. Documentar
10. Actualizar memoria
```

## Potenciación segura

Solo se potencia si:

- No rompe funcionalidad.
- Hay prueba o validación.
- El beneficio es claro.
- El riesgo es bajo o controlado.
- Se puede revertir fácilmente.

## Áreas de potenciación

- Seguridad.
- Rendimiento.
- Startup.
- Memoria.
- Batería.
- Tamaño de app.
- Arquitectura.
- Testeabilidad.
- CI/CD.
- UX.
- Accesibilidad.
- Logging seguro.
- Manejo de errores.
- Modularización.
- Observabilidad.

---

## 6. MEMORIA DE HALLAZGOS

Esta tabla debe mantenerse actualizada durante todo el proceso.

| ID | Fase | Severidad | Descripción | Archivo | Estado | Reparación | Validación |
|---|---|---|---|---|---|---|---|
| HALLAZGO-001 | FASE 10 | CRITICA | Reproducir o dar like a una canción suscribía al artista automáticamente y mostraba corazón rojo en todo lo escuchado: `DatabaseDao.followArtistsWithContent()` estampeaba `bookmarkedAt` en masa sobre todo artista con filas en `song_artist_map`/`album_artist_map` desde 5 sitios de sync/import, y toda la UI leía `bookmarkedAt` como "suscrito". | DatabaseDao.kt, SyncUtils.kt, Items.kt, +14 archivos | CORREGIDO (commit `88cf0e1`, registro #154) | DAO eliminado (tombstone), 5 call sites borrados, toda la UI y queries viradas a `followedByUserAt` (única columna de follow real); el read-back de suscripciones YTM ahora estampa `followedByUserAt` directamente | Trazado completo del flujo play/like→map→bookmark→UI; fuentes legítimas de follow verificadas (onboarding, importadores, read-back YTM) |
| HALLAZGO-002 | FASE 12 | ALTA | Letras de otra canción aparecían sobre la canción en curso: el pin de letras de crossfade (`_crossfadeOutgoingMetadata`) solo se liberaba en `cleanupCrossfade()` (espera AMBAS rampas) o transición manual; el predicado de audibilidad `CrossfadeLyricsPin.shouldRelease` existía con tests pero nunca fue cableado al loop de fade. | playback/MusicService.kt | CORREGIDO (commit `f7022e2`, registro #155) | `shouldRelease` cableado dentro del loop de fade: libera el pin apenas la saliente es inaudible (rampa completa, silencio detectado o ganancia bajo umbral) | Test unitario existente del predicado (CrossfadeLyricsPinTest) + verificación de consumo en PlayerConnection/Lyrics.kt |
| HALLAZGO-003 | FASE 3 | MEDIA (verificación) | Auditoría de logs sensibles: los valores de cookies de sesión NUNCA se loguean; solo booleanos y conteos (App.kt). Sin datos de usuario en app.log por esta vía. | App.kt | LIMPIO | Ninguna acción necesaria | Grep dirigido sobre todos los sitios de log de cookies |
| HALLAZGO-004 | FASE 8 | MEDIA (verificación) | PendingIntents: cero usos de `FLAG_MUTABLE` en toda la app; widget muestreado usa `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`. | EchoMusicWidgetManager.kt y demás | LIMPIO | Ninguna acción necesaria | Grep global + muestra |
| HALLAZGO-005 | FASE 4 | MEDIA (verificación) | Componentes `exported="true"` del manifiesto: todos legítimos — launcher alias, `MusicService` (MediaSession/MediaBrowser, obligatorio en reproductores), receivers de AppWidget, tile QS (protegido por `BIND_QUICK_SETTINGS_TILE`), MediaButtonReceiver. FileProvider no exportado. | AndroidManifest.xml | LIMPIO | Ninguna acción necesaria | Lectura del manifiesto completo |
| HALLAZGO-006 | FASE 9 | MEDIA (riesgo aceptado) | WebViews con JS + `allowFileAccess`/`allowFileAccessFromFileURLs` + JS interface en `CipherWebView`, `EjsNTransformSolver`, `PoTokenWebView`. Son infraestructura de descifrado de streaming que ejecuta el propio JS de YouTube/bundled; tocarlas pone en riesgo la reproducción. `LoginScreen`/`SpotifyImportScreen`: JS activo pero `allowFileAccess=false`. | utils/cipher, utils/sabr, utils/potoken | RIESGO ACEPTADO | Sin código cambiado: documentado aquí. Solo cargan contenido local/bundled; no navegan a URLs arbitrarias del usuario | Inventario completo de WebViews |
| HALLAZGO-007 | FASE 3 | BAJA (riesgo aceptado) | `GOOGLE_API_KEY` embebida en PoTokenWebView.kt: es la clave pública del cliente web de YouTube (InnerTube), la misma que usan todos los clientes open-source; pública por diseño, igual que las claves Last.fm/Tidal/Qobuz documentadas en AGENTS.md. | utils/potoken/PoTokenWebView.kt | RIESGO ACEPTADO | Ninguna — no es un secreto | Verificación contra el patrón de clientes InnerTube públicos |
| HALLAZGO-008 | FASE 1/15 | CRITICA (para publicar) + investigación abierta | El build type `release` firma HOY con el keystore DEBUG (app/build.gradle.kts:401, diagnóstico temporal del 2026-08-19 con comentario explícito de revertir). Motivo del diagnóstico: todos los builds release firmados con el certificado real "JR MUSIC PRO" fallaban en la resolución de streams, mientras el build con keystore debug funcionaba — se aisló el certificado como única variable. Consecuencias: (1) si se publica así, TODOS los usuarios pierden la capacidad de actualizar (cambio de firma); (2) sigue abierta la pregunta de por qué el certificado release parece marcado por YouTube. | app/build.gradle.kts:391-401 | ABIERTO | Ninguna aún: revertir a `signingConfigs.getByName("release")` es obligatorio antes de cualquier publicación, y requiere decisión del dueño (puede reabrir el fallo de streams) | Lectura del build.gradle.kts + comentario in situ; pendiente confirmar si el CI sobreescribe la firma en el workflow de release |

---

## 7. MEMORIA DE DECISIONES

| Fecha | Decisión | Motivo | Impacto | Alternativa descartada |
|---|---|---|---|---|
| 2026-08-23 | El follow real vive SOLO en `followedByUserAt`; `bookmarkedAt` queda como flag legado de librería | El estampado en masa de `bookmarkedAt` era la causa raíz de las suscripciones fantasma; la columna no puede volver a alimentar ninguna vista de "suscrito" | Toda la UI de artistas lee `followedByUserAt`; filas legadas estampeadas aparecen como no seguidas hasta tap explícito | Filtrar el bookmark en la UI sin tocar el DAO (dejaba viva la fuente del bug) |
| 2026-08-23 | No ampliar el alcance de `LyricsMatchRepair` a todos los proveedores | Costo de red/batería: la reparación re-verifica contra la red; el gate por proveedor sospechoso + versión de reglas lo mantiene acotado | Las letras guardadas erróneas de proveedores fuera del alcance se curan con el refetch manual (menú de letras) | Borrar todas las letras fetched de una vez (borraría también letras correctas y editadas) |
| 2026-08-23 | Riesgo aceptado en WebViews de descifrado (CipherWebView/EjsNTransformSolver/PoTokenWebView) | Ejecutan el JS de YouTube para resolver cifrado de streams; funcionan sobre contenido local/bundled, no sobre páginas arbitrarias | Documentado en HALLAZGO-006; tocarlas arriesga la reproducción, que es la función central | Endurecer flags de file-access (rompe el pipeline de descifrado sin beneficio real de seguridad) |

---

## 8. MEMORIA DE PRUEBAS

| Suite | Estado | Fecha | Resultado | Observación |
|---|---|---|---|---|
| Build debug | PENDIENTE | - | - | - |
| Build release | PENDIENTE | - | - | - |
| Unit tests | PENDIENTE | - | - | - |
| Android tests | PENDIENTE | - | - | - |
| Smoke manual | PENDIENTE | - | - | - |
| Lint | PENDIENTE | - | - | - |
| Dependency scan | PENDIENTE | - | - | - |

---

## 9. MEMORIA DE RIESGOS

| Riesgo | Probabilidad | Impacto | Mitigación | Estado |
|---|---|---|---|---|
| WebView de descifrado con acceso a archivos cargue contenido malicioso | Baja (solo carga JS local/bundled del propio YouTube, no URLs del usuario) | Medio | Documentado; vigilancia si se añade carga remota | Aceptado |
| Filas legadas de `bookmarkedAt` estampeadas por el bug muestran artistas como no seguidos | Media | Bajo (dirección segura: nunca muestra un follow fantasma; el tap explícito lo corrige) | El read-back YTM restaura las suscripciones reales al iniciar sesión | Cerrado (comportamiento esperado) |

---

## 10. COMANDOS DE VERIFICACIÓN OBLIGATORIA

Después de cada reparación importante, ejecutar según aplique:

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew lint
./gradlew test
./gradlew connectedDebugAndroidTest
./gradlew dependencyCheckAnalyze
```

Si se usan herramientas adicionales:

```bash
./gradlew detekt
./gradlew ktlintCheck
./gradlew spotlessCheck
```

---

## 11. CRITERIOS PARA MARCAR UNA REPARACIÓN COMO COMPLETADA

Una reparación solo se considera completada si cumple todo:

```yaml
reparacion_completada:
  codigo_aplicado: true
  compila: true
  no_rompe_funciones_criticas: true
  pruebas_ejecutadas: true
  pruebas_verdes: true
  rollback_disponible: true
  documentacion_actualizada: true
  memoria_actualizada: true
```

Si falta alguno:

```yaml
estado: PENDIENTE
```

---

## 12. PLAN DE ROLLBACK

Si una reparación rompe funcionalidad:

1. Detener cambios adicionales.
2. Revertir el commit problemático:

```bash
git revert <commit>
```

O volver al commit base:

```bash
git reset --hard <commit_base>
```

3. Restaurar respaldo si existe.
4. Reproducir el error.
5. Crear test que capture el problema.
6. Aplicar reparación alternativa más pequeña.
7. Validar nuevamente.
8. Registrar incidente en memoria de decisiones.

---

## 13. PLANTILLA DE CAMBIO SEGURO

Cada cambio debe registrarse así:

```yaml
cambio:
  id: FIX-001
  objetivo: Reparar o potenciar algo
  riesgo: BAJO/MEDIO/ALTO
  archivos_afectados: []
  funciones_afectadas: []
  pruebas_previas: []
  pruebas_posteriores: []
  rollback: true/false
  estado: PENDIENTE/COMPLETADO
```

---

## 14. BITÁCORA DE EJECUCIÓN

| Fecha | Fase | Acción | Resultado | Evidencia | Próxima acción |
|---|---|---|---|---|---|
| 2026-08-23 | FASE 3/8 | Trazado del bug de suscripciones automáticas reportado por el dueño | Causa raíz: `followArtistsWithContent()` + UI sobre `bookmarkedAt` | HALLAZGO-001 | Fix |
| 2026-08-23 | FASE 10 | Fix aplicado: DAO eliminado, 5 call sites borrados, UI a `followedByUserAt` | Commit `88cf0e1`, registro #154 | 20 archivos | Build |
| 2026-08-23 | FASE 12 | Trazado del bug de letras equivocadas en crossfade | Causa raíz: pin de letras nunca liberado; predicado testeado sin cablear | HALLAZGO-002 | Fix |
| 2026-08-23 | FASE 12 | Fix aplicado: `shouldRelease` cableado al loop de fade | Commit `f7022e2`, registro #155 | MusicService.kt | Build |
| 2026-08-23 | FASE 3/4/8/9 | Auditoría de seguridad rápida: logs de cookies, PendingIntents, manifiesto, WebViews, secretos | 0 hallazgos accionables nuevos; 2 riesgos aceptados documentados | HALLAZGO-003…007 | Build beta |
| 2026-08-23 | FASE 15 | Build beta `assembleUniversalFossDebug` (v0.6.232, versionCode 952) | BUILD SUCCESSFUL; APK contiene ambos fixes (verificado UP-TO-DATE) | build-beta-fenix.txt, build-follow-lyrics.txt | Entregar APK al dueño |
| 2026-08-24 | FASE 15 | Entrega de la beta para prueba REMOTA (celular desconectado, dueño en el trabajo): APK copiado como `BETA-001_Aura_v0.6.232_vc952.apk` + `BETA-001_LEEME.txt` a `C:\Users\AURA\Desktop\BETA OFICIAL  DE AURA FENIX` (carpeta sincronizada en la nube) | Entregada; convención permanente BETA-NNN | output-metadata.json (952 / 0.6.232) | Esperar reporte de prueba del dueño; al retomar, FASE_1_INVENTARIO |
| 2026-08-24 | FASE 16 | Prueba de BETA-001 por el dueño (remota, vía carpeta de nube): VERDE — fix A (reproducir/like ya NO auto-suscribe artistas) y fix B (la letra cambia con el crossfade) CONFIRMADOS; reproducción, toggle música↔video y ecualizador bien | APROBADA — fixes #154/#155 validados en dispositivo | reporte del dueño en el chat | Continuar FASE_1_INVENTARIO |
| 2026-08-24 | FASE 1 | Inventario completo ejecutado con 3 agentes en paralelo: 16 módulos, componentes de manifiesto, flavors, ~60 rutas de navegación (+ divergencias vs docs/UI_INVENTORY.md por la UI nueva `ui/newui/`), diálogos, widgets/notificaciones, workers/FGS/loops, almacenamiento y flujos críticos | COMPLETADA — resultados R1–R9 en la sección FASE 1 | SUPER AUDITORIA (sección RESULTADOS) | FASE_2_DEPENDENCIAS |
| 2026-08-24 | FASE 1/15 | HALLAZGO-008: el build type release firma con keystore DEBUG (diagnóstico temporal 2026-08-19; el certificado release "JR MUSIC PRO" parece marcado: release firmados reales fallan en streams, debug funciona) | ABIERTO — bloqueante para publicar; requiere decisión del dueño | app/build.gradle.kts:391-401 | Decisión del dueño + verificar firma del CI |

---

## 15. ESTADO FINAL DE LA AUDITORÍA

La auditoría se considera completa cuando:

```yaml
auditoria_completada:
  todas_las_fases_completadas: true
  hallazgos_criticos_resueltos_o_aceptados: true
  hallazgos_altos_resueltos_o_aceptados: true
  funciones_criticas_validadas: true
  pruebas_verdes: true
  reporte_final_generado: true
  memoria_actualizada: true
  potenciaciones_aplicadas: true
```

Si algún riesgo crítico o alto queda abierto, debe estar:

- Documentado.
- Justificado.
- Aceptado explícitamente.
- Con plan de mitigación.

---

## 16. INSTRUCCIÓN OPERATIVA PARA CONTINUAR

Para continuar la auditoría sin perder memoria, siempre se debe responder con este formato:

```yaml
avance:
  fase_actual: FASE_X
  actividad_realizada: descripcion
  hallazgos: []
  reparaciones_aplicadas: []
  pruebas_ejecutadas: []
  riesgos_abiertos: []
  proxima_accion: siguiente paso
```

---

## 17. ENTRADAS NECESARIAS PARA EJECUTAR LA AUDITORÍA REAL

Para ejecutar la auditoría y reparación sobre el código real, se necesita acceso a:

1. Código fuente o repositorio.
2. Rama y commit base.
3. AndroidManifest.xml.
4. Archivos Gradle.
5. `gradle/libs.versions.toml` si existe.
6. ProGuard/R8 rules.
7. Network security config.
8. Código de autenticación.
9. Código de red/API.
10. Código de almacenamiento.
11. Código de WebView si existe.
12. CI/CD si existe.
13. Lista de flujos críticos.
14. APK/AAB si se hará auditoría dinámica.

---

## 18. ORDEN RECOMENDADO PARA EMPEZAR

1. Enviar AndroidManifest.xml.
2. Enviar archivos Gradle principales.
3. Enviar código de login/auth.
4. Enviar código de red/API.
5. Enviar código de almacenamiento.
6. Enviar WebView/deep links si existen.
7. Enviar CI/CD si existe.

Con eso se inicia:

```yaml
proxima_accion: INICIAR_FASE_0_CON_ARCHIVOS_BASE
```

---

## 19. COMPROMISO DEL PLAN

Este plan se compromete a:

- Auditar todo el proyecto por fases.
- Mantener memoria del progreso.
- Reparar sin romper funcionalidad si se siguen las reglas.
- Validar cada cambio.
- Priorizar estabilidad.
- Potenciar solo lo que aporte valor real.
- Documentar decisiones.
- Registrar hallazgos.
- Mantener un camino claro hasta completar la auditoría.

---

## 20. ESTADO ACTUAL

```yaml
estado_actual:
  fecha: 2026-08-24
  fase_actual: FASE_2
  proxima_accion: FASE_2_DEPENDENCIAS_EN_CURSO
  bloqueos: []
  memoria: ACTIVA
  auditoria_completa: false
  beta: BETA-001_VERDE (2026-08-24, prueba remota del dueño)
  hallazgos_abiertos: HALLAZGO-008 (firma release = keystore debug; decisión del dueño antes de publicar)
```