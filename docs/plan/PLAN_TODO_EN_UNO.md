# Plan «todo en uno» — Aura Hi-Res v2 (2026-11-22)

Orden pedido por el dueño. Cada fase sale primero como **beta privada** (canal de betas del actualizador) y solo se
publica para todos con su orden. Todo cambio de código va con la skill `cirujano` (un hallazgo, un commit; plan y
revisión con ojos frescos en los cambios de riesgo). Nada llega a `main`, tags estables ni pull requests sin su orden.

## Fase 0 — Arreglos (en curso, rama `claude/compassionate-gates-1dotj5`)

| Área | Estado |
|---|---|
| Cola infinita: nunca repite lo que sonó ni lo que puso el usuario (4 huecos + 2 rondas de revisión) | Hecho, 2278 tests en verde |
| Suscripciones a artistas que el usuario no hizo (restaurar, Spotify, Tidal/Deezer, aviso al activar, «Preguntar siempre») | Hecho; arreglos de la revisión en curso |
| Restauración desde la nube que cerraba la app (fila 19) | Hecho |
| Escuchar juntos: 10 arreglos de sincronización | Hecho; segunda ronda (bucles, duración, carreras) en curso |
| Sincronización entre aparatos: 4 arreglos urgentes de pérdida de datos | Hecho; revisión en curso |
| Toques: botones que piden varios toques o hacen otra cosa (Cast, mini barra, menús, borrar al deslizar) | En curso |
| Textos legales: términos v3, privacidad ES/EN, README | Escritos; faltan los datos del dueño (nombre legal, ciudad, plazos) y pegar los textos de `license/` |

## Fase 1 — Pantallas grandes, plegables y lo que sale cortado
1. Capa única `AuraWindowInfo` (ancho del panel, altura compacta, postura, bisagra); quitar las 5 medidas distintas.
2. Ajustes en dos paneles (índice a la izquierda, página a la derecha) en ≥600 dp; diálogos y hojas centrados.
3. Cortes: alturas fijas en diálogos, filas de alto fijo con letra grande, 27 `maxLines = 1` sin «…», cabeceras que
   ignoran la altura, recortes de cámara, desajuste de densidad.
4. Biblioteca (lista en varias columnas), detalle de álbum/lista/artista en dos columnas, Inicio/Búsqueda/Estadísticas,
   resto de pantallas y posturas del plegable.

## Fase 2 — Nueva bienvenida
Términos → **Permisos** (todos, uno a uno, con explicación; orden del dueño, fila 209 d) → Licencia/demo (sin
cambios) → «Conecta tu música» (Inicia sesión con tu cuenta de Google · carpeta del móvil · nube privada · Spotify u
otro servicio · restaurar copia) → Listo. Sin artistas ni géneros (siguen disponibles en Ajustes ▸ Contenido).
Usuarios actuales nunca la vuelven a ver.

## Fase 3 — Quitar las menciones a YouTube de la interfaz
~42 textos traducidos + 37 en el código. «Tu cuenta de Google», «el catálogo en línea». Los términos y la privacidad
siguen nombrando la fuente (obligación legal). Casos funcionales se deciden uno a uno.

## Fase 4 — Música local bien hecha (base de las tres bibliotecas)
Orden de disco/pista, artista del álbum, separadores de artistas, reescaneo que actualiza, mover archivos sin perder
me gusta/listas/historial, búsqueda de Biblioteca y Android Auto con lo local, letras `.lrc`, ReplayGain, escaneo
incremental, recorrer de verdad la carpeta elegida.

## Fase 5 — Nube privada y las tres bibliotecas juntas
**Decisión del dueño (2026-11-22): después de la música local, la nube empieza con rclone** empaquetado dentro de
Aura (todas las nubes: Google Drive, OneDrive, Dropbox, MEGA, pCloud, WebDAV/Nextcloud, SMB, SFTP, S3…). Leer de verdad
las carpetas del selector de Android va como complemento.
**Decisión final del dueño (2026-11-22): solo OneDrive y Dropbox**, sin que los usuarios creen claves y sin
verificar la app (Google Drive, MEGA y el resto quedan fuera).
- **5.1 — OneDrive + Dropbox**: rclone ≥ 1.76 empaquetado como `librclone.so` (proceso `rcd` bajo demanda, credenciales
  aleatorias, alineado a 16 KB), inicio de sesión con el botón de Microsoft/Dropbox en una pestaña del navegador
  (OAuth por `config/oauthstatus`), permisos solo de lectura, streaming por HTTP en 127.0.0.1 hacia la caché de media3,
  `rclone.conf` cifrado con clave en el Keystore y fuera de las copias, nada de rutas ni nombres en el registro.
  Se empieza con las claves compartidas de rclone; el dueño puede registrar una vez la app de Aura en Microsoft y en
  Dropbox (sin página web) si aparecen los límites de uso.
- **5.2** — la nube dentro de la biblioteca («En tu nube» en artistas, búsqueda conjunta, mejor fuente primero).
- **5.3** — descarga sin conexión.
Requiere el permiso explícito del dueño para añadir rclone como dependencia binaria. Página de artista con «En tu móvil / En tu nube / En YouTube
Music», búsqueda conjunta, preferir la mejor fuente (local sin pérdida > nube > en línea).

## Fase 6 — Modo TV
Detección del aparato (TV, tablet, plegable, móvil) con elección manual; riel lateral estilo Spotify TV + reproductor
inmersivo con botón «Pantalla» (portada/letra/vídeo); foco con mando en todas las pantallas.

## Fase 7 — Rendimiento por gama y tasa de refresco
Tres clases (Entrada/Equilibrada/Premium) con semillas de ajustes existentes; refresco alto al interactuar y bajo en
reposo; bucles que paran en pausa; medir con macrobenchmark y JankStats.

## Fase 8 — Sincronización diferencial completa entre aparatos
Diario de acciones del usuario con su hora real (también sin conexión), orden de las listas, acuse por operación,
limpieza en el servidor, y decidir qué hace YouTube cuando hay aparatos emparejados.

## Fase 9 — Extras
Herramienta para limpiar artistas que no siguió el usuario · IA que lee tu gusto (resumen, apagada hasta aceptar) ·
iniciar sesión en la TV con QR (cifrado, un solo uso) · copia de seguridad automática periódica (sin cookies ni licencia).

## Fase 10 — Wrapped y estadísticas (al final, por orden del dueño)
