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
    fase_2_dependencias: COMPLETADA      # 2026-08-24: inventario + OSV; HALLAZGO-009..012; jsoup nunca expuesto (BravePipe ya forzaba 1.23.1)
    fase_3_seguridad_estatica: COMPLETADA # quick pass 003..007 + SQL/entradas/cripto 2026-08-24 (3 agentes); HALLAZGO-013..015; SQL y zip-slip VERDES
    fase_4_manifest_config: COMPLETADA    # HALLAZGO-005 (manifiesto completo leído, todo legítimo)
    fase_5_almacenamiento: COMPLETADA     # 2026-08-24: inventario completo DataStore/XML/archivos/Room/backup; HALLAZGO-016; exclusiones gruesas correctas
    fase_6_red: COMPLETADA                # 2026-08-24: inventario ~40 clientes + secretos en tránsito + auth/401 + pinning; HALLAZGO-017..019
    fase_7_auth_cripto: COMPLETADA        # 2026-08-24: ciclo de vida auth completo en primera persona; logouts borran de verdad, refresh acotado, Keystore bien; HALLAZGO-020
    fase_8_ipc_componentes: COMPLETADA    # 2026-08-24: cierre formal; 10 exportados legítimos, PendingIntents inmutables (004 LIMPIO), broadcasts explícitos/sistema; siguen 014/015
    fase_9_webview: COMPLETADA            # inventario completo; HALLAZGO-006 riesgo aceptado
    fase_10_arquitectura: COMPLETADA      # 2026-08-24: cierre formal; HALLAZGO-001 corregido+probado en dispositivo; HALLAZGO-021 (deuda MusicService 11k líneas)
    fase_11_calidad_codigo: COMPLETADA      # 2026-08-24: calidad sólida; !! moderado e idiomático, catches legítimos, cero TODO/FIXME; sin hallazgos nuevos (deuda ya en 021)
    fase_12_concurrencia: COMPLETADA      # 2026-08-24: cierre formal; HALLAZGO-002 ya corregido (f7022e2); cero GlobalScope/hilos crudos; sin hallazgos nuevos
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
  proxima_accion: FASE_13_RENDIMIENTO (FASE_11 completada 2026-08-24; abiertos HALLAZGO-008/010/011/012/013/014/015/016/017/018/019/020/021)
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

## RESULTADOS DE LA FASE 2 (2026-08-24)

### R1. Inventario de dependencias (verificado contra gradle/libs.versions.toml y los 17 build.gradle.kts)

**Compose / UI:** compose runtime/foundation/ui 1.11.0 · material3 1.5.0-alpha18 (deliberado: 197 usos APIs Expressive) · material3-adaptive 1.3.0-alpha09 · material-icons-extended 1.7.8 (hardcode; congelado por Google; entrada del catálogo @1.11.0 muerta) · materialKolor 4.1.1 · haze 1.0.2 (vieja, rework diferido) · lottie-compose 6.7.1 · reorderable 3.0.0 · compose-shimmer 1.3.3 (mantenimiento bajo) · smooth-corner-rect v1.0.0 jitpack (abandonada) · activity-compose 1.12.3 · lifecycle 2.10.0 · core-splashscreen 1.2.0 · appcompat 1.7.1 · palette-ktx 1.0.0 (estable final).

**Media3 / Cast:** media3 exoplayer/session/hls/ui/datasource-okhttp 1.10.1 · media3-cast 1.10.1 + mediarouter 1.8.1 + play-services-cast-framework 22.2.0 (solo gms).

**Red:** ktor 3.5.0 (app + 13 módulos cliente; ktor-client-retry-jvm = entrada muerta) · okhttp 4.12.0 hardcode en migration (vieja, solo migración) · org.brotli:dec 0.1.2 (2017, abandono upstream, única versión publicada) · org.json:json (entrada muerta).

**Imágenes:** coil3 3.5.0 · ucrop 2.2.11 jitpack (semi-mantenida).

**Inyección:** hilt 2.60.1 · hilt-navigation-compose 1.3.0.

**DB / persistencia:** room 2.8.4 · datastore-preferences 1.2.1 · androidx.security:security-crypto 1.1.0-alpha06 (hardcode ×2, app y migration, deben coincidir por Tink) · work-runtime-ktx 2.10.2.

**Firebase / GMS (solo gms):** firebase-bom 34.15.0 · play-services-auth 21.3.0 · google-api-client-android 2.9.0 · google-api-services-drive v3-rev20260428-2.0.0 (excluye httpcomponents).

**Letras / extracción:** PipePipeExtractor jitpack pin 208e43b184 · BravePipeExtractor jitpack pin fa5d4a8b4c · nanojson jitpack c7a6c1c08d (FORZADO global) · jsoup 1.23.1 (pin del catálogo alineado 2026-08-24; el grafo resuelto YA entregaba 1.23.1 vía BravePipeExtractor — ver HALLAZGO-009) · kuromoji-ipadic 0.9.0 (abandonada 2016) · tinypinyin 2.0.3 jitpack (abandonada ~2017).

**Otros:** guava 33.6.0-jre + coroutines-guava 1.10.2 + concurrent-futures-ktx 1.3.0 · commons-lang3 3.20.0 · timber 5.0.1 · process-phoenix 3.0.0 · androidx.browser 1.9.0 · desugar_jdk_libs_nio 2.1.5 · junit 4.13.2 (tests) · ffmpeg-kit-full 6.0-2 (**EOL 2025**) · youtubedl-android 0.18.1 (entradas MUERTAS, ningún módulo las usa) · kotlinx-serialization-json 1.9.0/1.6.3 (doble pin inconsistente).

### R2. Repositorios y plugins

Repositorios (en dependencyResolutionManagement FAIL_ON_PROJECT_REPOS y buildscript): google(), mavenCentral(), https://jitpack.io (necesario: ucrop, extractores, tinypinyin, smoothCorner, nanojson) y **https://maven.aliyun.com/repository/public** (mirror chino de Central; ver HALLAZGO-010). Sin pluginManagement, sin verification-metadata.xml ni lockfiles.

Plugins: AGP 9.2.0 · Kotlin 2.4.0 (+plugin.compose, +plugin.serialization) · KSP 2.3.9 · Hilt 2.60.1 · protobuf 0.9.6 · google-services 4.4.3 (condicional; no hay google-services.json) · firebase-crashlytics-gradle 3.0.2 (condicional).

Fuerzas/exclusiones: force nanojson @ c7a6c1c08d en todas las subprojects (sin él, NoSuchMethodError en fallback BravePipe) · exclude protobuf-java de PipePipeExtractor (choca con protobuf-javalite) · exclude httpcomponents de api-services-drive · packaging excludes META-INF estándar · substitution NewPipeExtractor local comentada/inactiva.

Gradle 9.6.1 + AGP 9.2.0 + Kotlin 2.4.0 coherentes (jvmToolchain 21 en todos los módulos). KSP 2.3.9 con prefijo 2.3 vs Kotlin 2.4: emparejamiento inusual (KSP2 laxo; builds verdes) — vigilar.

### R3. Escaneo de vulnerabilidades (OSV)

OSV-Scanner v2 instalado global vía winget. `osv-scanner scan` sobre el repo no extrae paquetes: el catálogo de versiones de Gradle (libs.versions.toml) no es resoluble por el escáner sin lockfile. Fallback ejecutado: consulta directa a la API OSV por 25 paquetes fijados (script osv-query.ps1, resultados en build-osv-api.txt):

- **jsoup 1.22.2 → GHSA-pmhh-3w7g-xqp8 / CVE-2026-71497 (MODERADA en el pin declarado, SIN exposición real):** el Cleaner de jsoup puede exponer marcado activo (XSS) al sanitizar HTML malformado con etiquetas de texto crudo personalizadas terminadas en caracteres de control. Afecta 1.14.3–1.22.2; corregida en 1.23.1. Vector: red, requiere interacción. jsoup parsea HTML de red no confiable en providers de letras/scrapers → aplicable en teoría. **VERIFICACIÓN PROFUNDA (2026-08-24):** el pin del catálogo decía 1.22.2, pero el grafo RESUELTO ya entregaba 1.23.1 porque `BravePipeExtractor:fa5d4a8b4c` depende directamente de `org.jsoup:jsoup:1.23.1` y Gradle resuelve al mayor (el 1.22.2 de PipePipeExtractor queda upgradado: `1.22.2 -> 1.23.1` en `:app:dependencies`). Prueba en el binario entregado: el APK de BETA-001 contiene `HtmlTagOptions` (clase que SOLO existe en jsoup ≥1.23.1) en `classes38.dex` → la CVE NUNCA estuvo expuesta en el artefacto que probó el dueño. **FIX igualmente aplicado (defensa en profundidad):** pin del catálogo alineado 1.22.2 → 1.23.1 en gradle/libs.versions.toml para que la declaración coincida con la realidad y no haya regresión silenciosa si BravePipeExtractor se elimina; builds de verificación build-fase2-jsoup.txt / build-fase2-jsoup2.txt (todo UP-TO-DATE = el classpath resuelto no cambió, ya era 1.23.1). Ver HALLAZGO-009.
- ffmpeg-kit-full 6.0-2: sin CVE registrado en OSV, pero EOL oficial (retirado 2025); reemplazo diferido (DIFERIDOS.md).
- Resto (okhttp 4.12.0, guava 33.6.0-jre, security-crypto 1.1.0-alpha06, ucrop, brotli, ktor 3.5.0, room 2.8.4, coil 3.5.0, lottie, kotlinx-serialization 1.9.0, junit 4.13.2, commons-lang3, timber, work 2.10.2, datastore 1.2.1, haze, reorderable, media3 1.10.1, hilt 2.60.1, protobuf-javalite 4.34.2, kuromoji, tinypinyin): **0 vulnerabilidades conocidas** a la fecha del escaneo.

### R4. Hallazgos de cadena de suministro (estado)

1. HALLAZGO-010: `maven.aliyun.com` en la cadena de resolución — abierto, decisión del dueño.
2. HALLAZGO-011: sin verificación de dependencias (no lockfile ni verification-metadata.xml) — abierto, candidato FASE 17.
3. HALLAZGO-012: entradas muertas del catálogo (youtubedl-android bundle, org.json, ktor-client-retry, material-icons @1.11.0) + doble pin kotlinx-serialization + okhttp hardcode en migration — limpieza pendiente.
4. Nota: `gradle.properties` trae `sdk.dir=/Users/aditya/...` (path macOS commiteado; debería vivir en local.properties) y `android.newDsl=false` (DSL legado bajo AGP 9). No se cambian: riesgo de romper otras máquinas; documentado.

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
  criticas_vulnerables: []        # jsoup CVE-2026-71497: nunca expuesta (grafo ya resolvía 1.23.1 vía BravePipeExtractor)
  actualizadas: [jsoup pin catálogo 1.22.2 -> 1.23.1 (defensa en profundidad; el classpath resuelto no cambió)]
  revertidas: []
  pendientes: [ffmpeg-kit EOL reemplazo diferido, limpieza HALLAZGO-012, mirror aliyun HALLAZGO-010, lockfile HALLAZGO-011]
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

## RESULTADOS DE LA FASE 3 (2026-08-24)

Ejecutada con 3 agentes en paralelo (SQL, validación de entradas/archivos, cripto/TLS/aleatoriedad),
cada afirmación clave re-verificada leyendo el código. El pase rápido previo ya había cubierto
secretos, logs, WebView y componentes exportados (HALLAZGO-003…007).

### R1 — SQL injection: VERDE, ninguna inyección explotable

- Toda la superficie SQL de `app/` pasa por Room con parámetros enlazados o por strings estáticos.
  Los 15 módulos de librería no tocan SQLite en absoluto (grep global `.kt`/`.java`).
- Las búsquedas de usuario (`searchSongs/searchArtists/searchAlbums/searchPlaylists`, historial de
  reconocimiento) usan `LIKE '%' || :query || '%'`: la concatenación ocurre DENTRO de SQLite sobre
  un parámetro enlazado (`DatabaseDao.kt:1297/1306/1316/1325/1442`). Ningún llamador construye
  `"%$q%"` en Kotlin antes del bind.
- `@RawQuery` (`DatabaseDao.kt:2231`) tiene un único llamador: `checkpoint()` con PRAGMA estático.
  `String.toSQLiteQuery()` solo recibe literales estáticos en sus 5 usos.
- El `VACUUM INTO` del backup escapa correctamente las comillas (`BackupRestoreViewModel.kt:155-157`)
  y usa ruta interna generada por la app.
- **Nota de higiene (sin número de hallazgo):** `Migration5To6`/`Migration6To7`
  (`MusicDatabase.kt:518/530`) interpolan IDs leídos de la propia BD sin escapar — inyección de
  segundo orden teórica. NO se recomienda tocarlas: corren una sola vez al migrar desde versiones
  muy viejas, el dato lo escribió la propia app (charset restringido de YouTube) y reescribir
  migraciones ya ejecutadas en dispositivos reales arriesga más de lo que protege.

### R2 — Validación de entradas / deep links: 1 hallazgo accionable

- **HALLAZGO-014 (BAJA):** parámetros de deep link sin validar antes de navegar
  (`MainActivity.kt:2474`): `?list=a%2Fb` produce `online_playlist/a/b?autoSave=true`, que no
  matchea el nav graph y `navigate()` lanza `IllegalArgumentException` sin `runCatching` → crash
  local provocable por cualquier app (MainActivity es `exported=true`). Impacto: DoS local, no
  secuestro de navegación (verificado: el primer segmento de ruta es fijo).
  Relacionados en la misma superficie: `file://` deja a la app leer sus propios archivos privados
  a petición de terceros (confused deputy; el contenido solo se reproduce como media y nunca vuelve
  al atacante — sin exfiltración) y el callback OAuth de Tidal no valida `state` (documentado en el
  código; el PKCE persistido hace fallar el intercambio de códigos ajenos).
- Los intents de `MusicService` (exportado, obligatorio para MediaSession) permiten a terceros
  evictar caché de canciones o lanzar búsquedas — comportamiento by-design de Android Auto,
  registrado como informativo.

### R3 — Manejo de archivos: SIN zip-slip ni path traversal

- ZIP de restore (`BackupRestoreViewModel.kt:193-230`): entradas por nombre exacto o whitelist
  estricta (`EQ_APPEARANCE_PREFS`); los temporales son nombres fijos en `cacheDir`; nunca se
  construye un path con `entry.name`.
- ZIP del updater (`UpdateDownloadWorker.kt:279-303`): extracción SIEMPRE al `targetApk` fijo;
  la versión de red pasa por `UpdateApkFiles.sanitize` (whitelist `[A-Za-z0-9._-]`, colapso de
  puntos, sin puntos iniciales) y además hay verificación de versión declarada y de firma antes
  de instalar.
- **HALLAZGO-015 (BAJA, defensa en profundidad):** `provider_paths.xml` expone TODO el
  almacenamiento externo y ambos caches (`path="."`). No explotable hoy (provider `exported=false`,
  todos los `getUriForFile` usan archivos fijos), pero latente para futuras URIs compartidas.
- Exportación MP3/vídeo: `sanitizeTitle` bloquea `/`; destino final vía SAF. Teórico únicamente.

### R4 — Cripto, TLS y aleatoriedad: 1 hallazgo accionable

- **HALLAZGO-013 (MEDIA):** inconsistencia real de almacenamiento de credenciales — la cookie de
  sesión de Google (`innerTubeCookie`, `PreferenceKeys.kt:837`) y el `sp_dc` de Spotify
  (`PreferenceKeys.kt:240`) viven en TEXTO PLANO en el DataStore, mientras los tokens de Tidal y
  Qobuz del MISMO código usan `EncryptedSharedPreferences` (AES256_SIV + AES256_GCM con MasterKey
  del Keystore: `QobuzTokenStore.kt:71-76`, `TidalTokenStore.kt:52-57`). Menores en la misma
  línea: token ListenBrainz (`:495`) y claves OpenRouter/DeepL (`:784/790`). La zona `license/`
  (`jr_license.xml` en plano) se reporta pero NO se toca — zona protegida por AGENTS.md.
- MD5/SHA-1 todos legítimos: SAPISIDHASH de innertube, `api_sig` de Last.fm, firma Qobuz (protocolo
  de cada API), o claves de caché no criptográficas. Cero `javax.crypto.Cipher` en código de app;
  el HmacSHA1 de `SpotifyAuth.kt` es TOTP legítimo.
- Aleatoriedad: `SecureRandom` para PKCE de Tidal (correcto); `kotlin.random.Random` solo en el
  nonce de telemetría `cpn` (no sensible).
- TLS limpio: `network_security_config.xml` con cleartext OFF (excepciones loopback únicamente),
  cero overrides de `TrustManager`/`HostnameVerifier`, ~23 `OkHttpClient.Builder` con confianza del
  sistema. Sin certificate pinning (informativo).

### R5 — Veredicto global de la fase

Ningún hallazgo CRÍTICO o ALTO. La superficie SQL es segura por construcción (Room + binding);
los lectores de ZIP escriben siempre a destinos fijos; TLS íntegro. Quedan ABIERTOS:
HALLAZGO-013 (credenciales en plano — fix con camino de migración, decide el dueño),
HALLAZGO-014 (crash por deep link — fix barato candidato a beta) y HALLAZGO-015 (FileProvider
ancho — defensa en profundidad).

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

## RESULTADOS DE LA FASE 5 (2026-08-24)

Inventario completo de almacenamiento con agente dedicado (DataStore, SharedPreferences XML,
archivos, Room, higiene de temporales, reglas de backup); afirmaciones clave re-verificadas
leyendo los XML y el código.

### R1 — Superficie de almacenamiento

- **Un solo DataStore** (`utils/DataStore.kt:25`, archivo `filesDir/datastore/settings.preferences_pb`,
  ~343 claves). Categorías: UI/tema, reproducción, estado home/sync, flags one-time, podcasts y
  credenciales/cuenta (ya cubiertas por HALLAZGO-013).
- **14 SharedPreferences XML**: solo 2 con datos sensibles — `jr_license.xml` (subscription key +
  ANDROID_ID, zona protegida: se reporta, no se toca) y los cachés de gusto `song_graph.xml` /
  `artist_genres.xml` (metadatos de escucha). El resto: flags de updater, EQ, apariencia, widget —
  nada sensible.
- **filesDir**: `logs/app.log` (rotación 256 KB + 1 backup, redactado en el chokepoint),
  `persistent_*.data` (cola/estado del player), `exoplayer/` + `download/` (contenido de escucha;
  caché de audio ilimitada por defecto — riesgo de espacio, no de seguridad), caches de cipher /
  player_configs / changelog / anuncios (no sensibles).
- **cacheDir**: snapshots y temporales de backup/restore, imágenes Coil (2 GB), canvas (256 MB LRU),
  artist pages (TTL 30 días), solvers de WebView, temporales de export/ringtone.
- **Externo**: solo `getExternalFilesDir` del updater (privado de la app en Android moderno), el APK
  vía MediaStore.Downloads y exports/ringtones/imágenes iniciados por el usuario. Todo legítimo.
- **Room**: `song.db` con WAL + busy_timeout; cero `allowMainThreadQueries` en el repo. Contiene el
  artifact más personal (biblioteca, historial `event`, letras) — viaja en backup por decisión de
  producto documentada en los XML.

### R2 — Backup automático de Android: exclusiones gruesas correctas, fuga menor

- `backup_rules.xml` y `data_extraction_rules.xml` (cloud-backup Y device-transfer) excluyen:
  el DataStore completo (la cookie InnerTube), `jr_license.xml`, `./exoplayer`, `./download` y
  `exoplayer_internal.db`. `song.db` y `aura_install_marker` viajan INTENCIONALMENTE (la biblioteca
  debe sobrevivir el cambio de teléfono; los datos restaurados se tratan como no confiables en
  `App.classifyInstallOrigin`).
- **HALLAZGO-016 (MEDIA):** por el default de inclusión viajan también, en silencio,
  `song_graph.xml`, `artist_genres.xml` (metadatos de gusto), `filesDir/logs/app.log*` y
  `persistent_*.data` — datos del usuario que salen del dispositivo hacia la nube de Google sin
  que la UI lo mencione.

### R3 — Higiene de temporales: bien

- Restore: `.tmp` borrados en éxito y en TODAS las rutas de fallo (`cleanupRestoreTemps`),
  `.restore_bak` borrado en éxito y rollback; el restore ya no aplica `settings.preferences_pb`
  de backups viejos.
- Updater: `.part` borrados en fallo/verificación, rename atómico, purge de versiones viejas.
- Logs: rotación real. Login-WebViews limpian cookies/WebStorage tras logout
  (`WebAuthSessionCleaner`). Pendiente menor (higiene, sin número): `aura_crash_*.txt`, CSVs de
  fallos de import (con títulos/artistas) y `cacheDir/images` no tienen prune — todos en cacheDir
  privado, riesgo bajo.

### R4 — Veredicto de la fase

Nada CRÍTICO ni ALTO. Controles que funcionan y quedan documentados: DataStore y licencia fuera
del backup, temporales con limpieza en todas las rutas, WebView sin sesión residual. Abierto nuevo:
HALLAZGO-016. `LastFMSessionKey` (:474) descubierta aquí se anexa a HALLAZGO-013. `jr_license.xml`
en plano se reporta pero es zona protegida por AGENTS.md (mitigado además por la exclusión de
backup y la verificación online).

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

## RESULTADOS DE LA FASE 6 (2026-08-24)

Ejecutada con 2 agentes en paralelo (A: infraestructura de clientes; B: datos en tránsito y flujos
de autenticación); afirmaciones clave re-verificadas contra el código. TLS ya verificado limpio en
FASE 3 (cleartext OFF, cero TrustManager/HostnameVerifier inseguros).

### R1 — Clientes HTTP: inventario y timeouts

- ~40 clientes HTTP (Ktor engine OkHttp, OkHttp directo, HttpURLConnection); cero Retrofit, cero
  `Jsoup.connect`. Todos singleton/lazy — nadie crea cliente por request (salvo `UptimeScreen`,
  impacto nulo).
- **HALLAZGO-019 (MEDIA, disponibilidad):** ~22 clientes sin timeouts explícitos, varios en el
  path crítico de reproducción/resolve (`MusicService.kt:7662/8828`, `DownloadUtil.kt:171`,
  NewPipe/BraveNewPipe, `PlayerJsFetcher.kt:25`, `PoTokenWebView.kt:462`, `SongPreviewController`,
  `CanvasArtworkPlayer`). Con red hostil/lenta un resolve puede colgar indefinidamente. El modelo
  a copiar ya existe en el repo: `QobuzHiRes.kt:47-54` con `callTimeout`.
- Cero `callTimeout` fuera de `YTPlayerUtils` y `QobuzHiRes`; `LocalFileDownloader` además es
  bloqueante y no cancelable (menor).
- **Caché InnerTube 50 MB en disco** (`InnerTube.kt:116-120`) con respuestas de la API YTM,
  inclusive autenticadas — dentro del dir privado, parcialmente mitigada por `Cache-Control:
  no-cache` del defaultRequest (menor, sin número).

### R2 — Secretos en tránsito

- **HALLAZGO-017 (MEDIA):** el login de Qobuz envía email y PASSWORD como query parameters en un
  GET (`QobuzApi.kt:62-66`) — única credencial real que viaja en URL en todo el repo. TLS la
  protege en tránsito, pero queda expuesta a logs de servidor/proxy. Mitigante: la API oficial de
  Qobuz es GET-only (diseño del proveedor, no de la app).
- Todo lo demás viaja bien: cookie InnerTube + SAPISIDHASH en headers, `sp_dc` en header Cookie,
  Bearer/`X-User-Auth-Token`/`Authorization: Token` en headers, credenciales Last.fm/Tidal en
  POST body. El TOTP de Spotify va en query pero es un código de 30 s de vida impuesto por el
  endpoint (BAJA, sin número).
- Cero logging de secretos en release: no existe `HttpLoggingInterceptor` en el repo; el chokepoint
  `AppLogger` redacta Authorization/Cookie/tokens con test incluido; el único log de red con
  contenido es un peek de 160 chars del cuerpo de ERROR de googlevideo (query string excluida por
  comentario explícito). Trampa armada: `Spotify.kt:251/324` loguea `token.take(8)` vía un logger
  hoy NO conectado (no-op) — vigilar.
- Cero endpoints reales `http://` (los 5 matches son namespaces XML y checks `startsWith`).

### R3 — Flujos de autenticación y 401/403

- Spotify (sp_dc + TOTP vía gist comunitario), Tidal (PKCE, verifier en store cifrado), Qobuz,
  Last.fm, licencia: todos con expiración/refresh manejado y SIN loops de credencial quemada
  (Spotify refresca y reintenta una vez; YouTube cae a anónimo + cascada de clientes; Tidal fuerza
  re-login; Qobuz abandona al fallback).
- Informativo: callback Tidal sin `state` (PKCE lo mitiga, ya en HALLAZGO-014); session key
  Last.fm muerta no se auto-limpia (higiene, no seguridad); el secreto TOTP de Spotify viene de un
  gist de terceros (supply chain, entra en HALLAZGO-018).

### R4 — Integridad de canales remotos

- **HALLAZGO-018 (MEDIA):** cero `CertificatePinner` y cero certs embebidos en todo el repo. Los
  canales remotos que alimentan autenticación/reproducción dependen solo de la CA del sistema:
  `qobuz_config.json` (candidatos app_id/app_secret), `player_configs.json` (mecanismo de
  auto-reparación documentado en AGENTS.md), el gist TOTP de Spotify, el actualizador
  (releases de GitHub + APK) y el Worker de licencia (zona protegida, solo reporte). Un MITM con
  CA válida o el control del repo/gist podría inyectar configuración. El actualizador mitiga con
  verificación de versión declarada + firma del APK antes de instalar.

### R5 — Veredicto de la fase

Nada CRÍTICO ni ALTO. Postura de tráfico sólida: secretos en headers/body (salvo Qobuz, impuesto
por su API), cero logging de secretos, 401/403 sin loops. Abiertos nuevos: HALLAZGO-017, 018, 019.

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

## RESULTADOS DE LA FASE 7 (ejecutada 2026-08-24)

> Cobertura: ciclo de vida de autenticación de TODOS los proveedores + biometría/PIN + Keystore +
> revocación. La criptografía base (primitivas, TLS, aleatoriedad) ya quedó cubierta por el agente
> cripto de FASE 3; esta fase la revalida y se concentra en lo que faltaba. Se ejecutó en primera
> persona (el agente delegado falló por un filtro de contenido del proveedor y se reemplazó por
> lectura directa del código).

- **R1 — Superficie de login.** No hay cuentas propias: la app no registra usuarios ni gestiona
  contraseñas de primera parte (registro/recuperación = N/A, viven en el proveedor). Logins de
  terceros: Google/InnerTube (cookie de cuenta), Spotify (cookie sp_dc/sp_key o token anónimo),
  Qobuz (email+password → user_auth_token), Tidal (OAuth PKCE sin client secret), Last.fm
  (session key), ListenBrainz (token pegado a mano). La licencia/suscripción es sistema propio
  (zona protegida, solo lectura): máquina de estados con gracia offline ACOTADA de 3 días
  (`LicenseLogic.kt:16`), verificación en cada apertura y estados explícitos de expirado/bloqueado;
  sin vía offline indefinida.
- **R2 — Logout (lo más auditado).** ✅ Los cuatro cierres de sesión BORRAN la credencial de verdad:
  - Google/InnerTube: `App.forgetAccount` (`App.kt:1842`) es el choke point ÚNICO de ambos botones
    de logout y de todo cambio de cuenta. Limpia los marcadores de sync de la BD ANTES de borrar la
    credencial, quita las claves del DataStore (cookie, visitorData, dataSyncId, nombre/email/canal),
    revoca el consentimiento de subida con un `false` explícito (no `remove`), anula
    `YouTube.cookie/visitorData/dataSyncId` en memoria y vacía el `CookieManager` del WebView.
    Logs redactados (solo presencia, nunca valores).
  - Qobuz: `QobuzTokenStore.logout()` = `clear()` del archivo cifrado (`QobuzTokenStore.kt:128`).
  - Tidal: `TidalTokenStore.logout()` = `clear()` (`TidalTokenStore.kt:114`).
  - Spotify: `SpotifyImportRepository.logout()` quita las 6 claves del DataStore (sp_dc, sp_key,
    access token, expiración, nombre, avatar), anula `Spotify.accessToken` en memoria y limpia la
    sesión web-auth.
  - Last.fm/ListenBrainz: borrado de la clave del DataStore (Last.fm en `AccountsScreen.kt:513-516`).
- **R3 — Refresh y expiración.** ✅ Tidal es el caso modelo del repo: refresh automático bajo
  `Mutex` con doble chequeo y margen de 60 s (`TidalTokenStore.kt:100-110`). Spotify: ante un 401
  hace UN refresh y reintenta UNA vez (`spotifyCallWithTokenRetry`, `SpotifyImportRepository.kt:558`);
  sin loops infinitos; el token anónimo se auto-cura igual. Qobuz: el user_auth_token es de larga
  vida y la API oficial NO tiene endpoint de refresh (limitación del proveedor, ligada al
  HALLAZGO-017). Last.fm/ListenBrainz: claves de larga vida sin expiración local (normal en
  scrobbling).
- **R4 — Biometría/PIN/app-lock.** CERO usos de `BiometricPrompt`, `KeyguardManager` o
  `isDeviceSecure` en todo el repo. La app no ofrece bloqueo de aplicación: es una superficie que
  no existe (decisión de producto, no hallazgo).
- **R5 — Keystore.** Las dos únicas bóvedas de credenciales serias usan el camino correcto:
  `MasterKey AES256_GCM` en AndroidKeyStore + `EncryptedSharedPreferences` (claves AES256_SIV,
  valores AES256_GCM) en Qobuz (`QobuzTokenStore.kt:68-77`) y Tidal (`TidalTokenStore.kt:49-58`).
  Ambas tienen recuperación sana ante keyset corrupto (p. ej. restore a otro dispositivo): borrar
  y recrear, con re-login barato. No hay claves autogestionadas fuera del Keystore. Lo que queda
  en DataStore plano ya está registrado en HALLAZGO-013.
- **R6 — Revocación.** Solo borrado local en TODOS los proveedores: ninguno llama a revocar la
  sesión en el servidor. El caso más concreto es Last.fm (`auth.logout` existe en su API y no se
  usa): la session key huérfana sigue válida del lado de Last.fm hasta revocarla en su web →
  HALLAZGO-020 (BAJA). Tidal/Spotify tampoco revocan el OAuth, pero sus tokens expiran solos.
- **Veredicto:** el ciclo de vida de auth es SÓLIDO: logouts que borran de verdad (incl. memoria y
  WebView), refresh acotado sin loops, PKCE sin secreto embebido, Keystore bien usado donde importa.
  Un hallazgo nuevo (020, BAJA). Cripto de FASE 3 revalidada. `fase_7_auth_cripto: COMPLETADA`.

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

## RESULTADOS DE LA FASE 8 (ejecutada 2026-08-24)

> Cierre formal: PendingIntents ya auditados (HALLAZGO-004 LIMPIO), deep links/intents/exports ya
> cubiertos en FASE 3 (HALLAZGO-014/015) y el manifiesto completo en FASE 4 (HALLAZGO-005). Esta
> pasada re-verifica directamente y cierra la fase.

- **R1 — Componentes exportados.** Exactamente 10 `exported="true"` en `app/src/main/AndroidManifest.xml`,
  todos legítimos y necesarios: `MainActivity` + 2 alias de launcher (:85/:238/:254), `MusicService`
  (:288, obligatorio para MediaSession/MediaBrowser — Android Auto/Bluetooth), `RecognitionTileService`
  (:340, protegido con el permiso de sistema `BIND_QUICK_SETTINGS_TILE`), `MediaButtonReceiver` (:364,
  requisito de media3) y 4 widget receivers (:376/:393/:415/:428, obligatoriamente exportados para
  `APPWIDGET_UPDATE`). FileProvider queda `exported="false"` con `grantUriPermissions` (:266);
  `ListenTogetherActionReceiver` queda `exported="false"`.
- **R2 — Intent filters / deep links.** Ya auditados en FASE 3: el único fallo real es el crash por
  deep link sin validar → HALLAZGO-014 sigue ABIERTO (fix barato candidato a beta).
- **R3 — PendingIntents.** HALLAZGO-004 LIMPIO: cero `FLAG_MUTABLE`; los widgets usan
  `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`. Re-verificado el patrón en los spot-checks de esta fase.
- **R4 — ContentProviders / permisos URI.** Solo el FileProvider del sistema (rutas anchas ya
  registradas en HALLAZGO-015). Los 21 usos de `FLAG_GRANT_READ/WRITE_URI_PERMISSION` son flujos de
  compartir iniciados por el usuario (logs, exportaciones, carátulas, backups de migración); ningún
  grant automático o implícito.
- **R5 — Broadcasts.** Los únicos `sendBroadcast` son el protocolo de sistema
  `ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION` (`MusicService.kt:5251/5264`, extras = sessionId +
  packageName, nada sensible) y un broadcast EXPLÍCITO de refresco de widget
  (`MusicRecognizerWidgetService.kt:310`, componente explícito = no interceptable). Los
  `registerReceiver` solo escuchan eventos de sistema (pantalla, becoming-noisy, volumen, audio,
  PiP). Riesgo aceptado menor: las acciones custom de los widget receivers (`...widget.PLAY_PAUSE`
  etc.) son falseables por cualquier app del dispositivo — control de reproducción, impacto
  molestia; estándar en widgets Android, sin hallazgo nuevo.
- **R6 — Notificaciones.** Solo las de descarga del updater usan `VISIBILITY_PUBLIC` (progreso, sin
  datos del usuario); la notificación de música usa la visibilidad por defecto. Nada sensible en
  pantalla de bloqueo.
- **Veredicto:** superficie IPC SANA — lo exportado es lo mínimo que Android exige (launcher,
  media, widgets, tile), todo lo interno queda sin exportar, PendingIntents inmutables, broadcasts
  explícitos o de sistema. Sin hallazgos nuevos; siguen abiertos 014 (deep-link crash) y 015
  (provider_paths). `fase_8_ipc_componentes: COMPLETADA`.

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

## RESULTADOS DE LA FASE 10 (ejecutada 2026-08-24)

> Cierre formal. El bug estructural grave de esta fase (HALLAZGO-001, suscripción automática de
> artistas) ya está CORREGIDO (commit `88cf0e1`) y VERIFICADO en dispositivo (BETA-001 VERDE).
> Esta pasada evalúa el resto de la estructura y registra la deuda pendiente.

- **R1 — Modularización.** 16 módulos Gradle: `app` + librerías hoja (`innertube`, `kugou`,
  `lrclib`, `betterlyrics`, `simpmusic`, `youlyplus`, `shazamkit`, `artistvideo`, `canvas`,
  `applecanvas`, `echomusiccanvas`, `paxsenixlyrics`, `unison`, `jiosaavn`, `migration`).
  Dependencias acíclicas por construcción (Gradle no compilaría un ciclo): las librerías no
  conocen a `app`. Sano.
- **R2 — Inyección de dependencias.** Hilt en todo `app` (`@HiltViewModel`, módulos,
  `@AndroidEntryPoint`); los proveedores de datos de red son stateless por diseño documentado
  (p. ej. `QobuzApi`). Sano.
- **R3 — Manejo de estado/navegación.** MVVM + StateFlow/Compose; navegación centralizada en
  `MainActivity` (~60 rutas, inventario FASE 1). Mezcla conocida: parte de la UI lee DataStore
  directamente desde composables (`rememberPreference`) en vez de pasar por ViewModel — aceptado,
  es el patrón heredado del fork y funciona.
- **R4 — Deuda estructural (lo único pendiente).** Archivos gigantes medidos:
  `MusicService.kt` 10 953 líneas (god object: reproductor + resolve + scrobble + sync +
  reconocimiento + widgets), `Player.kt` 3 543, `AuraPlayer.kt` 2 585, `Lyrics.kt` 2 514,
  `HomeScreen.kt` 2 490, `MainActivity.kt` 2 475, `DatabaseDao.kt` 1 987, `App.kt` 1 807.
  `MusicService.kt` es además el archivo compartido #1 del registro de regresiones
  (docs/REGRESSION_REGISTRY.md). → HALLAZGO-021 (BAJA, mantenibilidad).
- **R5 — Manejo de errores.** `runCatching` + degradación elegante como norma (bóvedas que leen
  "no vinculado" si fallan, resolver que cae al path normal);CancellationException siempre
  re-lanzada en los sitios muestreados. Sano.
- **Veredicto:** arquitectura FUNCIONAL y el único fallo estructural crítico ya corregido y
  probado. La deuda pendiente es el tamaño de `MusicService.kt` y compañía (021) — candidata a la
  potenciación de FASE 22 con characterization tests primero (regla 1 de esta fase: nada de
  refactor en caliente). `fase_10_arquitectura: COMPLETADA`.

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

## RESULTADOS DE LA FASE 11 (ejecutada 2026-08-24)

**Metodología:** medición directa con grep + muestreo de casos en primera persona (patrón de los cierres de FASE 8/10/12). Fase de trabajo nuevo; sin cambios de código — solo medición y veredicto.

**R1 — Uso de `!!`:** 141 apariciones en Kotlin de `app/src/main` para un codebase de este tamaño = nivel moderado. Patrones dominantes, todos idiomáticos:
- 12 en `savedStateHandle.get<String>("…")!!` (ViewModels de navegación): argumentos garantizados por el grafo de navegación; ausencia = error de programación, crash correcto.
- Getters de servicios del sistema (`getSystemService<ConnectivityManager>()!!` en `DownloadUtil.kt:94`, `MusicService.kt:2087`): el sistema siempre los provee.
- Sin concentración peligrosa en hotspots: `playback/` completo tiene solo 3 (dos de sistema y un `queue.preloadItem!!` con item garantizado por el flujo de preload).

**R2 — Excepciones silenciadas:** 39 `catch` con excepción ignorada (`_`) o vacíos, clasificados por categoría. Todos corresponden a patrones legítimos:
- `ActivityNotFoundException` (intents sin handler disponible) y `SecurityException` (permiso ausente): idioms de Android.
- Teardown best-effort (`dataSource.close()`, `muxer.stop()`, `extractor.release()`, cancelación de notificación).
- Parsing defensivo de APIs externas: los helpers JSON de `Spotify.kt:145-174` devuelven `null` a propósito ante respuesta malformada.
- Degradación documentada: `MainActivity.kt:2536-2560` intenta dos estrategias de `setDataSource` y cae a metadatos parciales/nombre de archivo — el flujo está escrito para fallar suave.
- 19 `printStackTrace()`: `utils/Utils.kt:67-70` es el helper de reporte de errores del proyecto (el comentario documenta que además escribe al log que el usuario puede enviar); varios más están en `eq/` (zona protegida, solo lectura).

**R3 — Deuda marcada:** CERO `TODO(`/`FIXME`/`HACK` reales en `app/src/main` (las 5 coincidencias son placeholders de clave de licencia `XXXXXXXX` y la palabra "hack" en un comentario).

**R4 — Casts inseguros:** una sola supresión `UNCHECKED_CAST` en todo `app/src/main` (`MessageCodec.kt:440`, codec de protocolo propio, acotado).

**R5 — Clases/métodos gigantes:** se remite a la FASE 10 — ya medido y registrado como HALLAZGO-021 (`MusicService.kt` 10 953 líneas y demás); potenciación en FASE 22 con tests primero. No se duplica el hallazgo.

**R6 — Código muerto:** el único caso conocido sigue siendo `DatabaseDao.incrementPlayCount(songId)` (registrado en RESULTADOS de FASE 12; limpieza FASE 22). Nada nuevo encontrado en esta fase.

**Veredicto:** CALIDAD DE CÓDIGO SÓLIDA. Sin hallazgos nuevos — la única deuda estructural ya está registrada (HALLAZGO-021). FASE 11 COMPLETADA.

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

## RESULTADOS DE LA FASE 12 (ejecutada 2026-08-24)

**Metodología:** cierre formal en primera persona (grep + lectura directa). HALLAZGO-002 —el único hallazgo de concurrencia de toda la auditoría— ya estaba CORREGIDO antes de esta fase: commit f7022e2, registro #155 — el pin de letras del crossfade se libera por el predicado de audibilidad `shouldRelease` cableado dentro del loop de fade. Evidencia: `CrossfadeLyricsPinTest` + prueba en dispositivo BETA-001 VERDE.

**R1 — Scopes e hilos:** CERO usos de `GlobalScope` en todo el repo (app + 15 módulos). CERO hilos crudos (`Thread(`) en `app/src/main`. El trabajo asíncrono usa scopes con dueño (`viewModelScope`, `lifecycleScope`, scopes de servicio) o los dispatchers de media3/Room.

**R2 — `runBlocking` (117 apariciones, auditadas por contexto):** la mayoría está en tests (uso correcto). En producción todas son deliberadas y documentadas:
- Resolución de streams de `MusicService`: `runBlocking` en el hilo loader de media3, nunca en Main. Los comentarios P46/H4 (`MusicService.kt:747-781`, `App.kt:1681/1771`, `YTPlayerUtils.kt:321-452`, `DataStore.kt:60`) documentan la migración activa de lecturas que antes bloqueaban Main.
- `BackupRestoreViewModel.kt:152/269`: checkpoint de Room con `runBlocking(Dispatchers.IO)` — fuera de Main.
- `App.kt:1816`: lectura bloqueante UNA sola vez por instalación (mirror de tamaño de caché sin sembrar); siembra el mirror y toda lectura posterior es no bloqueante.
- Familia `onGetSong` (`SelectionSongsMenu.kt:563` y demás menús): patrón deliberado de commit síncrono — la firma es `suspend` y los comentarios documentan que las filas deben estar confirmadas antes del `addSongToPlaylist` inmediato (`YouTubePlaylistMenu.kt:192-203/527-535`).
- `YouTube.createPlaylist` (innertube): firma no-suspend heredada; todos sus llamadores la invocan fuera de Main y protegidos por mutex (`MigrationViewModel.kt:899-902`).

**R3 — WorkManager:** todos los workers son `CoroutineWorker` (YtmSync, YtmAutoSync, UpdateCheck, UpdateDownload y el de licencia) — ejecución automática fuera de Main, con gates de frescura y `KEEP` documentados (`ReleaseRadarViewModel.kt:39`). Zona de licencia: solo lectura, intacta.

**R4 — Notas de higiene (sin hallazgo nuevo):**
- `DatabaseDao.incrementPlayCount(songId)` (`DatabaseDao.kt:1477-1489`) envuelve un `runBlocking` y NO tiene ningún llamador en el código: código muerto, sin ruta de ejecución (sin riesgo); candidato a limpieza en FASE 22.
- Los `runBlocking` de DataStore llevan la advertencia de ANR (`DataStore.kt:60`) y sus llamadores están fuera de Main.

**Veredicto:** CONCURRENCIA SANA. Sin hallazgos nuevos. HALLAZGO-002 ya cerrado. FASE 12 COMPLETADA.

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
| HALLAZGO-009 | FASE 2 | MODERADA en el pin declarado · SIN exposición real | jsoup: el catálogo declaraba 1.22.2 (rango afectado por GHSA-pmhh-3w7g-xqp8 / CVE-2026-71497: el Cleaner puede exponer marcado activo/XSS al sanitizar HTML malformado con etiquetas de texto crudo terminadas en caracteres de control). PERO el grafo resuelto ya entregaba 1.23.1 (corregida): `BravePipeExtractor:fa5d4a8b4c` depende directamente de `org.jsoup:jsoup:1.23.1` y Gradle resuelve al mayor (`1.22.2 -> 1.23.1`). Verificado en el binario: el APK BETA-001 contiene `HtmlTagOptions` (clase exclusiva de jsoup ≥1.23.1) en classes38.dex → la CVE NUNCA estuvo expuesta. | gradle/libs.versions.toml | CERRADO (defensa en profundidad) | Pin del catálogo alineado 1.22.2 → 1.23.1 para que la declaración coincida con la resolución real y no regrese en silencio si se elimina BravePipeExtractor. El rebuild quedó UP-TO-DATE porque el classpath resuelto no cambió (ya era 1.23.1), no por fallo del build. | OSV API por paquete (build-osv-api.txt), `:app:dependencies` (build-jsoup-deps.txt), diff de jars jar-diff.ps1 (HtmlTagOptions añadida en 1.23.1), marker presente en classes38.dex del APK (BETA-001) |
| HALLAZGO-010 | FASE 2 | MEDIA (cadena de suministro) | El repositorio `https://maven.aliyun.com/repository/public` (mirror chino de Maven Central) está en la cadena de resolución (settings.gradle.kts): terceros pueden servir artefactos alterados; además es lo que mantiene resoluble ffmpeg-kit EOL. | settings.gradle.kts | ABIERTO | Ninguna aún: quitar el mirror puede romper la resolución de ffmpeg-kit; decisión del dueño (FASE de reemplazo de ffmpeg-kit lo desbloquea) | Lectura de settings.gradle.kts |
| HALLAZGO-011 | FASE 2/17 | BAJA/MEDIA | No hay verificación de integridad de dependencias: sin `gradle.lockfile` ni `gradle/verification-metadata.xml`. Un artefacto sustituido en un mirror pasaría inadvertido. | repo raíz | ABIERTO | Candidato FASE 17: generar verification-metadata o lockfile + CI que lo valide | Escaneo OSV-Scanner (sin lockfile no pudo resolver el catálogo) |
| HALLAZGO-012 | FASE 2 | BAJA (higiene) | Entradas muertas del catálogo de versiones: youtubedl-android (library/ffmpeg/aria2c/bundle 0.18.1), org.json:json, ktor-client-retry-jvm, material-icons core/extended @1.11.0 (extended inexistente >1.7.8). Además: doble pin kotlinx-serialization (1.6.3 vs 1.9.0) y okhttp 4.12.0 hardcode solo en migration = drift de classpath. | gradle/libs.versions.toml, app y migration build.gradle.kts | ABIERTO | Limpieza pendiente (borrar entradas sin referencia, unificar serialization, alinear okhttp); requiere build verde | Inventario FASE 2 (agente) |
| HALLAZGO-013 | FASE 3/7 | MEDIA | Credenciales en texto plano en el DataStore: la cookie de sesión de Google (`innerTubeCookie`, `PreferenceKeys.kt:837`, escrita en LoginScreen.kt:211/247) y el `sp_dc` de Spotify (`:240`), mientras los tokens de Tidal/Qobuz del mismo código SÍ usan `EncryptedSharedPreferences` (AES256_SIV+AES256_GCM, Keystore). Menores: clave de sesión Last.fm (`:474`, descubierta en FASE 5), token ListenBrainz (`:495`), claves OpenRouter/DeepL (`:784/790`). La licencia (`jr_license.xml` en plano) se reporta pero es zona protegida — sin cambios. | PreferenceKeys.kt, LoginScreen.kt, QobuzTokenStore.kt, TidalTokenStore.kt | ABIERTO | Propuesta: migrar esas claves a EncryptedSharedPreferences con migración one-time del valor existente. Riesgoso: el login y la reproducción dependen de la cookie; requiere camino de migración probado — decisión del dueño en fase de reparación | Verificado por grep/lectura: claves planas vs stores cifrados (agente cripto + re-verificación) |
| HALLAZGO-014 | FASE 3/8 | BAJA | Crash local vía deep link: `?list=a%2Fb` (o browseIds con `/`) llega a `navController.navigate("online_playlist/$playlistId?autoSave=true")` sin validar ni `runCatching`; la ruta no matchea el nav graph y `navigate()` lanza `IllegalArgumentException`. Provocable por cualquier app (MainActivity `exported=true`). Impacto DoS local, sin secuestro de navegación. Relacionados (informativos): `file://` sobre archivos propios (confused deputy sin exfiltración) y callback Tidal sin `state` (mitigado por PKCE). | MainActivity.kt:2474/2483/2427 | ABIERTO | Propuesta: validar charset/formato de `playlistId`/`browseId` (rechazar `/`) o envolver el navigate en `runCatching` — fix barato, candidato a próxima beta | Lectura de MainActivity.kt; trazado del path de crash por el agente de entradas |
| HALLAZGO-015 | FASE 3 | BAJA (defensa en profundidad) | `provider_paths.xml` más ancho de lo necesario: `external-path`, `cache-path` y `external-cache-path` con `path="."` exponen TODO el almacenamiento externo y ambos caches. No explotable hoy: provider `exported=false` y todos los `getUriForFile` usan archivos fijos generados por la app; riesgo latente si una futura URI compartida usa path influenciable. | app/src/main/res/xml/provider_paths.xml | ABIERTO | Propuesta: restringir cada entrada a las subcarpetas reales que comparten los call sites (logs, playlist_covers, update, export) | Lectura del XML + grep de todos los usos de getUriForFile |
| HALLAZGO-016 | FASE 5/18 | MEDIA | El auto-backup a la nube de Google incluye por default (sin exclusión explícita) metadatos del usuario: `song_graph.xml` y `artist_genres.xml` (gusto/escucha), `filesDir/logs/app.log*` (diagnóstico) y `persistent_*.data` (cola de reproducción). Las exclusiones gruesas sí existen (DataStore completo, `jr_license.xml`, caches de exoplayer/descargas) y `song.db` viaja por decisión de producto documentada; estos cuatro se suman en silencio sin que la UI lo mencione. | app/src/main/res/xml/backup_rules.xml, data_extraction_rules.xml | ABIERTO | Propuesta: excluir `song_graph.xml`/`artist_genres.xml` (cachés reconstruibles, pérdida cero) y `./logs`; `persistent_*.data` a decisión del dueño (restaura la cola al cambiar de teléfono). Fix barato y seguro | Lectura de ambos XML de backup + inventario de archivos del agente FASE 5 |
| HALLAZGO-017 | FASE 6 | MEDIA | El login de Qobuz envía email y PASSWORD como query parameters de un GET (`addQueryParameter("email"/"password")`): única credencial real que viaja en URL en todo el repo. TLS la protege en tránsito, pero queda expuesta a logs del servidor/proxy. Mitigante de diseño: la API oficial de Qobuz es GET-only; el resto de credenciales del repo viaja en headers/body. | app/src/main/kotlin/com/music/echo/qobuz/QobuzApi.kt:62-66 | ABIERTO | Sin fix posible dentro de la app mientras la API Qobuz sea GET-only: documentar y aceptar el riesgo, o pedir al proveedor POST (fuera de nuestro control). Decisión del dueño: aceptar formalmente | Lectura de QobuzApi.kt (agente FASE 6B + re-verificación) |
| HALLAZGO-018 | FASE 6 | MEDIA | Cero certificate pinning y cero certs embebidos en todo el repo: los canales remotos que alimentan autenticación/reproducción (`qobuz_config.json`, `player_configs.json`, gist TOTP de Spotify, releases del actualizador, Worker de licencia) dependen solo de la CA del sistema. Un MITM con CA válida o el control del repo/gist inyectaría configuración. Mitiga parcialmente: el actualizador verifica versión declarada + firma del APK antes de instalar; `player_configs.json` es mecanismo de auto-reparación documentado en AGENTS.md. La parte de licencia es zona protegida (solo reporte). | QobuzConfigProvider.kt:130, RemotePlayerConfig.kt:81, SpotifyAuth.kt:35, echomusicupdater.kt:792, LicenseBackendClient.kt:75-76 | ABIERTO | Candidato FASE 17/22: pinning o verificación de integridad (firma del contenido) en los canales de config remota; priorizar el gist TOTP y qobuz_config. Tocar el Worker de licencia requiere permiso explícito | Grep global CertificatePinner (cero matches) + inventario de endpoints del agente FASE 6B |
| HALLAZGO-019 | FASE 6/13 | MEDIA (disponibilidad) | ~22 clientes HTTP sin timeouts explícitos, varios en el path crítico de reproducción/resolve (MusicService.kt:7662/8828, DownloadUtil.kt:171, NewPipe/BraveNewPipe, PlayerJsFetcher.kt:25, PoTokenWebView.kt:462, SongPreviewController.kt:234, CanvasArtworkPlayer.kt:84). Con red hostil o lenta un resolve puede colgar indefinidamente ("la app se queda pensando"); solo cuentan con los defaults por fase de OkHttp, sin tope de request completa. | varios (ver descripción) | ABIERTO | Propuesta: añadir connect/read/write + `callTimeout` siguiendo el patrón ya existente en `QobuzHiRes.kt:47-54`; candidato a beta por ser mejora de robustez con riesgo bajo | Inventario de clientes del agente FASE 6A + spot-checks |
| HALLAZGO-020 | FASE 7/22 | BAJA | El logout es solo borrado local en TODOS los proveedores: ninguna llamada de revocación server-side. Caso más concreto: Last.fm — el cierre de sesión limpia las claves locales y `LastFM.sessionKey` pero no llama a `auth.logout`, así que la session key huérfana sigue válida del lado de Last.fm hasta revocarla en su web (agravado por el almacenamiento plano, ver HALLAZGO-013). Tidal/Spotify tampoco revocan su OAuth al cerrar sesión, pero esos tokens expiran solos. | AccountsScreen.kt:513-516; SpotifyImportRepository.kt:132-146; TidalTokenStore.kt:114; QobuzTokenStore.kt:128 | ABIERTO | Propuesta: llamar `auth.logout` de Last.fm al cerrar sesión (barato, candidato FASE 22); el resto se acepta (expiración natural) | Lectura directa FASE 7 |
| HALLAZGO-021 | FASE 10/22 | BAJA (mantenibilidad) | Deuda estructural: archivos gigantes — `MusicService.kt` 10 953 líneas (god object: reproductor, resolve, scrobble, sync, reconocimiento y widgets en una clase), `Player.kt` 3 543, `AuraPlayer.kt` 2 585, `Lyrics.kt` 2 514, `HomeScreen.kt` 2 490, `MainActivity.kt` 2 475. `MusicService.kt` es el archivo compartido #1 del registro de regresiones: cada cambio ahí arriesga regressions cruzadas. No es un bug funcional hoy. | playback/MusicService.kt, ui/player/Player.kt, ui/newui/AuraPlayer.kt, ui/component/Lyrics.kt, ui/screens/HomeScreen.kt, MainActivity.kt | ABIERTO | Candidato a potenciación de FASE 22: dividir `MusicService.kt` incrementalmente (extractors de responsabilidades) con characterization tests primero; NUNCA refactor en caliente durante la auditoría | Medición directa (wc) + REGRESSION_REGISTRY.md |

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
| 2026-08-24 | FASE 2 | Inventario completo de dependencias (catálogo + 17 build.gradle.kts), repositorios/plugins/fuerzas, escaneo OSV (API por 25 paquetes fijados, osv-query.ps1) y verificación profunda del caso jsoup | COMPLETADA — R1–R4; HALLAZGO-009 cerrado SIN exposición real: BravePipeExtractor ya forzaba jsoup 1.23.1 en el grafo resuelto y el APK BETA-001 contiene el marker `HtmlTagOptions` (clase exclusiva de ≥1.23.1) en classes38.dex; pin del catálogo alineado 1.22.2→1.23.1 como guarda; nuevos abiertos HALLAZGO-010 (mirror aliyun), 011 (sin lockfile/verification), 012 (entradas muertas) | build-osv-api.txt, build-jsoup-deps.txt, build-fase2-jsoup.txt, jar-diff.ps1 | FASE_3_RESTO_SQL_ENTRADAS |
| 2026-08-24 | FASE 3 | Auditoría estática completa con 3 agentes en paralelo: SQL (barrido total de rawQuery/execSQL/Room en app + 15 módulos), validación de entradas y archivos (deep links, zip-slip, FileProvider, intents, exports), cripto/TLS/aleatoriedad; afirmaciones clave re-verificadas contra el código | COMPLETADA — SQL VERDE (todo Room+binding; 2 interpolaciones teóricas en migraciones one-shot que NO se tocan), SIN zip-slip (restore y updater con destinos fijos + sanitize), TLS limpio; nuevos abiertos HALLAZGO-013 (cookie Google + sp_dc Spotify en texto plano vs EncryptedSharedPreferences de Tidal/Qobuz), 014 (crash por deep link sin validar), 015 (provider_paths.xml ancho) | SUPER AUDITORIA (sección RESULTADOS DE LA FASE 3) | FASE_5_ALMACENAMIENTO |
| 2026-08-24 | FASE 5 | Inventario completo de almacenamiento (agente dedicado): DataStore único (~343 claves), 14 SharedPreferences XML, archivos filesDir/cacheDir/externo, Room, higiene de temporales y reglas de backup; XML de backup re-verificados directamente | COMPLETADA — exclusiones gruesas correctas (DataStore con la cookie, jr_license.xml, caches de exoplayer/descargas fuera del backup; song.db viaja por decisión documentada); higiene de temporales bien (restore/updater limpian en todas las rutas); nuevo abierto HALLAZGO-016 (song_graph/artist_genres/app.log/persistent_*.data viajan al cloud backup por default); LastFMSessionKey (:474) anexada a HALLAZGO-013 | SUPER AUDITORIA (sección RESULTADOS DE LA FASE 5) | FASE_6_RED |
| 2026-08-24 | FASE 6 | Auditoría de red con 2 agentes en paralelo (infraestructura de ~40 clientes HTTP + secretos en tránsito/flujos auth/401/pinning); afirmaciones clave re-verificadas | COMPLETADA — postura sólida: secretos en headers/body, cero logging de secretos en release, cero tráfico cleartext, 401/403 sin loops; nuevos abiertos HALLAZGO-017 (password Qobuz en query string, impuesto por su API GET-only), 018 (cero pinning en canales de config remota), 019 (~22 clientes sin timeouts, disponibilidad) | SUPER AUDITORIA (sección RESULTADOS DE LA FASE 6) | FASE_7_AUTH_CRIPTO |
| 2026-08-24 | FASE 7 | Ciclo de vida de auth auditado en primera persona (el agente delegado falló por filtro de contenido del proveedor; se reemplazó por lectura directa): login/logout/refresh/expiración de Google-InnerTube, Spotify, Qobuz, Tidal, Last.fm y ListenBrainz + licencia (zona protegida, solo lectura) + biometría/PIN + Keystore + revocación | COMPLETADA — logouts borran de verdad (BD+DataStore+memoria+WebView, choke point `App.forgetAccount`), refresh acotado sin loops (Tidal con mutex, Spotify 401→refresh→1 reintento), cero biometría/PIN (superficie inexistente), Keystore correcto en las 2 bóvedas, gracia de licencia acotada a 3 días; revocación solo local en todos los proveedores; nuevo abierto HALLAZGO-020 (BAJA: sin revocación server-side; Last.fm `auth.logout` sin usar); cripto de FASE 3 revalidada | SUPER AUDITORIA (sección RESULTADOS DE LA FASE 7) | FASE_8_COMPONENTES_IPC |
| 2026-08-24 | FASE 8 | Cierre formal de componentes/IPC: re-verificación directa de los 10 componentes exportados del manifiesto, PendingIntents, broadcasts, URI grants y visibilidad de notificaciones (trabajo previo de FASE 3/4 consolidado) | COMPLETADA — superficie IPC sana: lo exportado es el mínimo exigido por Android (launcher, MediaSession, widgets, tile protegido por permiso de sistema), PendingIntents inmutables (HALLAZGO-004 LIMPIO), sendBroadcast solo protocolo AudioEffect de sistema + explícito de widget, URI grants solo en flujos de compartir del usuario, notificaciones públicas sin datos sensibles; SIN hallazgos nuevos; siguen abiertos 014 (deep-link crash) y 015 (provider_paths) | SUPER AUDITORIA (sección RESULTADOS DE LA FASE 8) | FASE_10_ARQUITECTURA |
| 2026-08-24 | FASE 10 | Cierre formal de arquitectura: evaluación de modularización (16 módulos), DI (Hilt), estado/navegación, manejo de errores y medición directa del tamaño de archivos; HALLAZGO-001 ya corregido y probado en dispositivo | COMPLETADA — módulos acíclicos, DI sana, errores con degradación elegante; única deuda: archivos gigantes (`MusicService.kt` 10 953 líneas = god object y hotspot #1 de regresiones) → nuevo abierto HALLAZGO-021 (BAJA mantenibilidad, candidato potenciación FASE 22, nada de refactor en caliente) | SUPER AUDITORIA (sección RESULTADOS DE LA FASE 10) | FASE_12_CONCURRENCIA |
| 2026-08-24 | FASE 12 | Cierre formal de concurrencia y ciclo de vida: spot-checks en primera persona (GlobalScope, hilos crudos, 117 usos de `runBlocking` auditados por contexto, WorkManager); HALLAZGO-002 ya estaba corregido (commit f7022e2) y validado en dispositivo (BETA-001 VERDE) | COMPLETADA — concurrencia sana: cero `GlobalScope`, cero hilos crudos en `app/src/main`, todo `runBlocking` de producción fuera de Main o deliberado y documentado (loader de media3, checkpoints IO, mirror one-shot, commit síncrono `onGetSong`, mutex en `createPlaylist`), workers todos `CoroutineWorker`; SIN hallazgos nuevos; nota: código muerto `incrementPlayCount(songId)` (limpieza FASE 22) | SUPER AUDITORIA (sección RESULTADOS DE LA FASE 12) | FASE_11_CALIDAD_CODIGO |
| 2026-08-24 | FASE 11 | Calidad de código (trabajo nuevo): medición directa de `!!` (141 en Kotlin de app/src/main, muestreados por patrón), 39 catches ignorados clasificados por categoría, 19 `printStackTrace`, TODO/FIXME/HACK, casts inseguros y código muerto; sin cambios de código | COMPLETADA — calidad sólida: `!!` idiomáticos (nav-args de savedStateHandle, getSystemService) sin concentración en hotspots, catches todos patrones legítimos (ActivityNotFound/SecurityException, teardown best-effort, parsing defensivo, degradación documentada), CERO deuda marcada, 1 solo UNCHECKED_CAST; SIN hallazgos nuevos (la deuda estructural ya es HALLAZGO-021) | SUPER AUDITORIA (sección RESULTADOS DE LA FASE 11) | FASE_13_RENDIMIENTO |

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
  fase_actual: FASE_11_COMPLETADA
  proxima_accion: FASE_13_RENDIMIENTO
  bloqueos: []
  memoria: ACTIVA
  auditoria_completa: false
  beta: BETA-001_VERDE (2026-08-24, prueba remota del dueño)
  hallazgos_abiertos: HALLAZGO-008 (firma release = keystore debug; decisión del dueño antes de publicar) · HALLAZGO-010 (mirror aliyun) · HALLAZGO-011 (sin lockfile/verification-metadata) · HALLAZGO-012 (entradas muertas del catálogo) · HALLAZGO-013 (cookie Google + sp_dc Spotify + sesión Last.fm en texto plano en DataStore; fix con migración, decide el dueño) · HALLAZGO-014 (crash por deep link sin validar; fix barato candidato a beta) · HALLAZGO-015 (provider_paths.xml ancho; defensa en profundidad) · HALLAZGO-016 (metadatos de escucha y logs viajan al cloud backup por default; fix barato de exclusiones) · HALLAZGO-017 (password Qobuz en query string; aceptar riesgo — API GET-only) · HALLAZGO-018 (cero pinning en canales de config remota; candidato FASE 17/22) · HALLAZGO-019 (~22 clientes sin timeouts; candidato a beta) · HALLAZGO-020 (logout sin revocación server-side; Last.fm auth.logout sin usar, candidato FASE 22) · HALLAZGO-021 (deuda estructural: MusicService.kt 10 953 líneas; potenciación FASE 22 con tests primero)
```