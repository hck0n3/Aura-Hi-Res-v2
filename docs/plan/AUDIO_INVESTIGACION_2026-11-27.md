# Investigación de audio — la mejor experiencia posible en Aura (2026-11-27)

Pedido del dueño (2026-11-27): «la mejor experiencia de audio que podría tener en un Android… fidelidad… que las
canciones me revelen todo su mínimo detalle… como si estuviese escuchando un FLAC de alta calidad de Tidal… sin
distorsiones, sin voces rasposas… en streaming (Opus) y en local (MP3, FLAC…)… en cualquier salida… investigación
exhaustiva real, sin placebo… compara lo que ya tenemos con lo que falta».

Tres investigaciones independientes: (1) auditoría del código actual, solo lectura; (2) estado del arte con fuentes
(Android, AES, códecs, apps líderes); (3) piezas de código abierto y sus licencias. Nada de esto ha cambiado código.

---

## 0. La verdad por delante (lo que es físicamente posible)

- **Lo que el códec de YouTube borró no se puede recuperar.** Opus a 128-160 kbps ya es de banda completa (hasta
  20 kHz) y «casi transparente» (Xiph, Opus Recommended Settings). No hay agudos «perdidos» que reconstruir.
- **Subir a 96/192 kHz no añade información** y puede empeorar por intermodulación ultrasónica (Montgomery, Xiph,
  «24/192 Music Downloads… make no sense»). Samsung UHQ Upscaler no tiene ninguna medición publicada; Sony DSEE
  sintetiza, no recupera, y Sony admite que sin auriculares Hi-Res no se nota.
- **Los «excitadores» y la «extensión de banda» empeoran el streaming:** realzan justo los artefactos del códec y la
  sibilancia (la receta de las «voces rasposas»). No se harán.
- **Más alto suena «mejor» y no lo es.** Toda comparación tiene que hacerse con el volumen igualado.

Lo que **sí** se puede (y es mucho): que nada distorsione nunca, que cada canción llegue al mismo volumen con margen,
que el sonido se corrija para TU auricular o altavoz (lo único con respaldo científico fuerte para «más detalle y
más cuerpo»: Olive, Welti y Khonsaripour, AES 2018, preferencia ↔ desviación de la curva Harman, r = 0,91), que a
volumen bajo no se pierdan graves, que las eses no piquen, y pedir siempre el mejor flujo que YouTube dé.

---

## 1. Lo que Aura ya tiene (auditoría del código)

| Pieza | Estado real | Dónde |
|---|---|---|
| Calidad del flujo | Siempre Opus; gana el de mayor bitrate (774 ~256k si llega, si no 251 ~160k). El resolvedor principal va **sin sesión**, así que 774 casi nunca llega [NO VERIFICADO] | `utils/YTPlayerUtils.kt:1814-1825` |
| Decodificación | Solo MediaCodec del sistema; Opus sale en **16 bits** (124/124 pistas en el log del dueño, fila 336) | `playback/MusicService.kt:10822` |
| Ruta Hi-Res | FLAC 24 bits en float 32 en gama media/alta; en gama baja se trunca a 16 bits **sin dither** antes del EQ | `playback/audio/SinkDspRouting.kt:11-15` |
| Motor (Superpowered) | Preamp → EQ (biquads RBJ float) → de-esser fijo 6,5 kHz −5 dB → Volumen seguro → ancho → espacial → firma Tidal → protección de altavoz → compresor → limitador (−3 dB, techo −0,3 dBFS, 32 muestras) → soft-clip tanh → dither TPDF | `cpp/superpowered/SuperpoweredBridge.cpp:1157-1411` |
| Ecualizador | Gráfico 10 bandas ±18 dB; paramétrico 5-16 bandas; 14 presets; **AutoEQ** (jaakkopasanen) ya integrado; EQ por dispositivo; medición de la respuesta dentro del motor | `eq/` |
| Volumen seguro | ≈ −14 LUFS; baja hasta −12 dB y **sube hasta +12 dB**; ReplayGain local; **ignora los picos**; sin dato asume +7 | `eq/audio/AudioGain.kt` |
| Espacial / crossfeed | 6 perfiles (HRTF, crossfeed 700 Hz, M/S) | `eq/audio/SpatialAudio.kt` |
| Modo Fidelidad | Todo fuera; bit-perfect USB en Android 14+ si el formato coincide | `playback/audio/FidelityMode.kt` (fila 902) |
| Fundido | Dos reproductores con rampas; **sin margen** (`xfHeadroom = 1f`) | `playback/CrossfadeEngine.kt:1226` |

### Problemas encontrados (por gravedad)

1. **El sonido de fábrica suma ganancia sin margen.** Preset «Aura Hi-Res v3» (+3,5 dB a 31/62 Hz, +3 dB a 16 kHz) +
   preamp +2,5 dB + firma Tidal (+1 dB graves, +1,5 dB a 12 kHz) ≈ **+7 dB en graves y +7 dB en 12-16 kHz**, con
   el margen automático apagado. En una canción baja, Volumen seguro añade hasta +12 dB. Todo eso lo frena el
   limitador: bombeo en canciones dinámicas y **agudos realzados justo donde están los artefactos del Opus**. Es la
   explicación más probable de «voces rasposas» y «sonido cansado».
2. **El limitador no es de pico real** (mide muestras, no picos entre muestras) y el techo es −0,3 dBFS; el estándar
   para streaming y Bluetooth es −1 dBTP (AES TD1008, Spotify).
3. **Opus en 16 bits** desde el decodificador: el modo «Procesado en 32 bits» no lo arregla, solo evita volver a
   cuantizar.
4. **El fundido puede pasar de 0 dBFS** (dos señales limitadas sumadas sin margen).
5. **Un altavoz Bluetooth se trata como auriculares** en el espacial (`SpatialAudio.kt:171`).
6. **De-esser estático** (siempre −5 dB a 6,5 kHz con el EQ encendido): apaga las voces aunque no haya eses.
7. **Biquads en float32 sin suavizado** de coeficientes: posible «zipper» al mover un deslizador; poca precisión en
   31 Hz a 96/192 kHz.
8. **Código muerto / placebo:** lista de itags sin uso, 4 procesadores vacíos, la clave de normalización sin efecto,
   `measuredLoudnessDb` que nadie rellena. La fila 51 («¿funciona Volumen seguro?») sigue abierta.

---

## 2. Lo que hacen los mejores (resumen con veredicto)

| App / función | Veredicto |
|---|---|
| Wavelet: AutoEq hacia Harman (3.600+ modelos), balance, graves | **Real y medible.** Aura ya tiene AutoEQ; falta hacerlo automático y seguro |
| Poweramp DVC | Ganancia, no calidad; con Bluetooth y volumen absoluto no hace nada |
| Neutron / Poweramp: EQ paramétrico, limitador, crossfeed | Real si se deja margen |
| USB Audio Player Pro / bit-perfect | Real solo con DAC USB (Aura ya lo tiene en Fidelidad) |
| JamesDSP / ViPER convolver | Real con una respuesta medida; «modelado analógico» = gusto |
| Samsung UHQ, Sony DSEE | Sin mediciones / síntesis: **no** |
| Normalización Spotify/Apple/Tidal/YouTube | Real: −14 (Spotify/YouTube), ≈ −16 (Apple), limitador a −1 dB |

---

## 3. Plan propuesto, de más a menos impacto

Cada punto: qué se oye, riesgo, y si toca zona con candado (`eq/` o `cpp/`, que solo el dueño puede abrir a mano).

| # | Mejora | Qué vas a notar | Candado |
|---|---|---|---|
| 1 | **Cadena de ganancia limpia:** preamp automático = −(mayor realce), margen automático encendido por defecto, objetivo de volumen −16 LUFS cuando hay realces, usar los **picos** de ReplayGain, y **limitador de pico real** a −1 dBTP con lookahead (x42 `peaklim`, GPL-3.0+) | Se acaba la distorsión, el bombeo y la aspereza al subir volumen o EQ, en cualquier salida | **Sí** (`eq/`, `cpp/`) |
| 2 | **Nuevo sonido de fábrica «Aura Referencia»:** sin realces fijos en 12-16 kHz sobre streaming; graves con margen; la firma Tidal solo como opción | Voces limpias, agudos sin «arena», cuerpo sin saturar | **Sí** (`eq/`) |
| 3 | **Corrección automática para tu auricular** (AutoEQ, que ya está): base offline compacta (~0,5 MB), elegir el modelo por el nombre del Bluetooth, preamp automático, sin corregir por encima de ~10 kHz, crédito a las fuentes | Lo más parecido a «escuchar más detalle y cuerpo» que existe con respaldo científico | **Sí** (`eq/autoeq`) |
| 4 | **Mejor flujo de YouTube:** pedir Opus 774 (~256k) con la sesión cuando la cuenta lo permita; verificar que llega | Más bits reales cuando exista | No (`YTPlayerUtils.kt`) |
| 5 | **Decodificar Opus en float** (extensión FFmpeg o libopus de media3) para no truncar a 16 bits antes del EQ | Menos ruido de cuantización al ecualizar (sutil) | No, pero es **dependencia nueva** (permiso) |
| 6 | **Compensación de sonoridad a volumen bajo** (ISO 226, ligada al volumen, con tope e intensidad ajustable) | A volumen bajo los graves y el cuerpo no desaparecen | **Sí**; revisar patente US 8135146 |
| 7 | **De-esser dinámico** (5-9 kHz, ≤ 6 dB, solo cuando hay eses) en lugar del fijo | Voces sin eses que pican y sin apagar la mezcla | **Sí** (`cpp/`) |
| 8 | **Perfiles por salida bien detectados:** altavoz del móvil, barra/altavoz BT, auriculares cable/BT, USB; **bajo psicoacústico solo en altavoces pequeños** (bankstown, MIT) | Más graves percibidos en el móvil y altavoces pequeños; nada raro en auriculares | **Sí** |
| 9 | **Margen en el fundido** y fin del truncado sin dither en gama baja | Transiciones sin picos | Parcial (`playback/` no; `SinkDspRouting` no) |
| 10 | **Limpieza de placebos** (código muerto) y cerrar la fila 51 con una prueba medible | Nada que no funcione | Parcial |
| — | Excitador, «upscaling» a 96/192 kHz, «IA que recupera agudos» | **No se hará**: empeora o es placebo | — |

Batería (regla 7): todo lo anterior son filtros biquad, envolventes y un limitador; despreciable frente a decodificar.

### Cómo se comprobará que no es placebo

- Medición dentro del propio motor (ya existe `measureEqResponse` / `measureHeadroom`) en tests: ninguna salida pasa
  de −1 dBTP con el peor caso de realces; la respuesta medida coincide con la curva pedida.
- Un interruptor de comparación A/B **con volumen igualado** para que el dueño escuche la diferencia real.
- Cada punto con su fila en el registro y su test guardián.

### Decisiones que necesita el dueño

1. **Abrir el candado** de `eq/` y `app/src/main/cpp/` (`.claude/hooks/protect-zones.sh`) para los puntos 1-3 y 6-8.
2. **Dependencia nueva** para el punto 5 (decodificador FFmpeg/libopus de media3).
3. **Datos de AutoEq en una app de pago:** el código es MIT, las mediciones son de terceros sin licencia explícita;
   propuesta: acreditar las fuentes (como hace Wavelet).
4. **Cambiar el sonido de fábrica** a «Aura Referencia» (punto 2) para usuarios que nunca tocaron el EQ.
5. **Patente** sobre EQ ISO 226 ligado al volumen (punto 6).

---

## 4. Piezas de código abierto elegidas (licencias compatibles con GPL-3.0)

| Pieza | Elegida | Licencia |
|---|---|---|
| Limitador de pico real | x42/dpl.lv2 `peaklim.cc` | GPL-3.0-or-later |
| Loudness / ReplayGain | libebur128 | MIT |
| Crossfeed | libbs2b | MIT |
| Remuestreo (si hiciera falta) | soxr (o r8brain) | LGPL-2.1+ (MIT) |
| Dither | Airwindows TPDF / NotJustAnotherDither | MIT |
| Bajo psicoacústico | bankstown (portado a C) | MIT |
| De-esser / EQ dinámico | Airwindows DeEss / Calf Deesser | MIT / LGPL-2.1+ |
| Perfiles de auricular | AutoEq (resultados ParametricEQ) | código MIT; datos: ver decisión 3 |

**No compatibles o a evitar:** pleezer (Sustainable Use License), JamesDSPManager original (sin licencia), EEL_VM
(sin confirmar «-or-later»), DSPFilters (licencia sin verificar). AGPL (Xfeed, ZLEqualizer): solo con permiso.

## Fuentes principales

Android AudioMixerAttributes / bit-perfect, latencia y remuestreo (developer.android.com, source.android.com); AES
TD1008; Spotify loudness normalization; ReplayGain 2 (Hydrogenaudio); ISO 226:2023; Olive-Welti-Khonsaripour AES 2018;
Larsen y Aarts JAES 2002 (bajo virtual); Reiss JAES 2016 (Hi-Res); Montgomery/Xiph (24/192); Xiph Opus Recommended
Settings; bs2b; AutoEq (GitHub); Wavelet docs; páginas de soporte de Sony y Samsung. Enlaces completos en las
transcripciones de la investigación de esta sesión.
