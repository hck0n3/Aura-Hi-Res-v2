# Aura Hi-Res v2.0.16 — reacción instantánea, login al instante y reproductor más limpio

Versión estable que reúne todo lo probado en las betas v2.0.12, v2.0.13,
v2.0.14 y v2.0.15 más los cambios de esta ronda. Actualización directa sobre
cualquier versión de la identidad v2 (desde la estable v2.0.11): mismo
paquete (iad1tya.aura.music) y misma firma (CN=Aura Hi-Res v2), conserva tus
datos, tu sesión y tus ajustes.

---

## Velocidad y fluidez

- **Reacción instantánea al tocar:** la respuesta tras tocar cualquier cosa (cambiar de pestañas, expandir el reproductor, entrar a Ajustes, reaccionar tras iniciar sesión) ya no tiene la pausa de antes. La causa era una capa de seguridad que pagaba cifrado de hardware en el hilo principal y cientos de lecturas bloqueantes de ajustes en cada navegación; se optimizó sin tocar la seguridad ni la sesión cifrada.
- **Fluidez a 120 Hz:** las animaciones se adaptan a la tasa de refresco del celular y se recuperan si el sistema las baja; el fondo del reproductor expandido ya no trabosa y su pulso se lee continuo, sin escalones.
- **Tras iniciar sesión el inicio no se congela:** la recarga de la pantalla principal espera a que pase la tormenta de sincronización de la biblioteca.

## Inicio de sesión

- **La app captura tu cuenta de inmediato:** al terminar de iniciar sesión, la app vuelve sola y empieza a cargar tu biblioteca, sin quedarse parada en la pantalla de Google.
- **Ya no se queda en blanco tras aceptar la sesión:** en algunos teléfonos la última pantalla del inicio de sesión (blanca) se quedaba colgada y la app nunca volvía sola hasta que elegías tu cuenta con «Usar correo del teléfono». Ahora, si esa pantalla no avanza por sí sola, la app la empuja y vuelve con tu cuenta cargada.
- **Iniciar sesión con otra cuenta** ya logueada en el dispositivo ahora sí re-sincroniza la biblioteca a la cuenta nueva.
- **Salir del WebView o matar la app durante la validación** ya no deja la biblioteca sin sincronizar: la sesión se completa igualmente y, si quedó a medias, el próximo arranque se auto-repara solo.

## Ecualizador

- **Sin parpadeo al mover las bandas:** al arrastrar una banda solo se mueve esa barra; la interfaz ya no parpadea ni se mueve raro debajo del dedo. Los presets de fábrica y personalizados se seleccionan o deseleccionan correctamente al soltar.

## Limpieza

- **Arranque limpio:** se eliminó el sistema completo de avisos del creador (inbox remoto, caché local, punto rojo en el avatar, entrada Ajustes ▸ Avisos y su polling). Los diálogos propios de la app (términos y condiciones, bienvenida, fiabilidad en segundo plano, licencia/suscripción) siguen vivos.

## Reproductor

- **El botón Cast responde al primer toque (interfaz nueva):** su zona táctil real era más grande que la caja donde vivía, y solo una franja central invisible respondía — por eso hacía falta tocarlo varias veces y un toque fallido podía colarse al gesto de la portada y cambiar la canción. Ahora todo el círculo responde al primer toque y los toques cerca del botón ya no cambian de pista.
- **Cabecera más limpia (interfaz nueva):** solo queda el botón Cast en la esquina superior derecha; se eliminó el botón de tres puntos que estaba detrás. Los títulos de las canciones ya no corren por debajo del botón Cast. El menú del reproductor sigue disponible desde la cola («Más opciones» de la cabecera de la cola y botón more de la barra de cola) y el menú de la letra aparece con la letra abierta.

## Biblioteca y música local

- **Local visible en Biblioteca:** al abrir Biblioteca verás la tarjeta «Local» junto a «Podcasts», visible de inmediato; antes solo era alcanzable como el último chip de la fila.
- **Auto-escaneo del dispositivo:** la primera vez que concedes el permiso de almacenamiento, el escaneo arranca solo, sin necesidad de un segundo toque.

## Escuchar juntos

- **Indicador de carga por usuario:** en la sala, bajo el nombre de cada usuario aparece «Cargando…» mientras su teléfono está cargando la canción, para que sepas por qué todavía no suena a la vez.

## Interfaz

- **Renglones estilo Apple en las listas puras de canciones:** descargas (modo sin conexión), historial local y «Todas las canciones» del artista, con la misma estética que la lista Local. El historial remoto y demás listas de YouTube conservan su diseño clásico porque sus elementos pueden llevar portadas de vídeo.
- **Modo sin conexión verificado:** el banner con el botón «Volver online» desactiva el modo desde la propia pantalla; la lista muestra solo canciones descargadas por completo.

## Conocidos

- Bajo saturación fuerte de YouTube (bot-check) alguna canción puede tardar o reintentarse; normalmente se resuelve sola en segundos.
- Algunas canciones pueden arrancar cortadas de vez en cuando; si te pasa, prueba a desactivar «Saltar segmentos sin música» en Ajustes ▸ Reproductor y avísanos.

## Cómo actualizar

- Si tienes cualquier versión de la identidad v2 (estable v2.0.11 o betas v2.0.12 a v2.0.15): actualización directa; conserva tus datos, tu sesión y tus ajustes.
- Si tienes una beta de desarrollo (terminación .dev): esta estable se instala AL LADO, no la pisa. Son apps distintas: aquí empiezas con datos frescos.
- Si tenías la Aura antigua (iad1tya.echo.music): sigue intacta; esta v2 vive a su lado.
