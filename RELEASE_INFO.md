# Aura Hi-Res v2.0.43 — Liquid Glass interactivo, la biblioteca al instante y la cola del coche arreglada

Versión estable (v2.0.43 / versionCode 1004). Actualización directa sobre cualquier versión de la
identidad v2: mismo paquete (iad1tya.aura.music) y misma firma (CN=Aura Hi-Res v2), conserva tus datos,
tu sesión y tus ajustes.

## Android Auto

- **Arreglada la cola al reproducir desde el coche:** al abrir un álbum o una lista y elegir una canción, después sonaba algo de otra colección. El servicio seguía paginando la última cola que habías dejado puesta en el móvil y la iba metiendo encima de la que estabas escuchando.
- **Al terminar el álbum o la lista ahora sí entra la cola infinita**, en vez de continuar con canciones de una colección que ya no sonaba.
- El mismo fallo estaba en **Escuchar juntos** (al invitado le entraban canciones que el anfitrión nunca puso) y al usar **Reproducir a continuación** con el reproductor vacío. Corregidos los tres.

## Liquid Glass

- **Nuevo «Cristal interactivo»** (Ajustes ▸ Apariencia ▸ Liquid Glass): el cristal reacciona al tacto y se mueve con la misma física que SimpMusic — se deforma al cambiar de pestaña en vez de limitarse a desplazarse.
- **En teléfonos de gama alta viene activado de fábrica**, junto con Liquid Glass, para que la experiencia visual sea la mejor desde el primer arranque. En cualquier momento puedes apagarlo, y tu elección manda a partir de entonces.
- **Con el cristal interactivo activado, la barra inferior pasa a ser una cápsula flotante de solo iconos.** Al dejar de tapar una banda entera de pantalla, el efecto refracta lo que se mueve por debajo y se lee de verdad como cristal.
- **Corregidas las esquinas del minirreproductor**, que se veían cuadradas en vez de seguir su forma redondeada.

## Biblioteca

- **Al iniciar sesión, tus listas y suscripciones aparecen enseguida.** Iban las últimas en la cola de sincronización, detrás de las dos pasadas más largas; ahora van primero.
- **Mientras sincroniza, la biblioteca lo dice en vez de afirmar que no tienes nada.** Antes, una pestaña vacía aseguraba «no tienes artistas» justo cuando era falso, y la pestaña de listas ni siquiera mostraba nada.

## Artistas

- **La parte de abajo de la portada del artista ahora se desenfoca como un cristal**, con el desenfoque creciendo hacia abajo, para que el nombre, los suscriptores y las visualizaciones se lean mejor. Funciona también sobre el vídeo de fondo.

## Búsqueda

- **Las sugerencias mientras escribes ya no esperan a la red.** El historial local aparece al instante y los resultados en línea se añaden cuando llegan.

## Fluidez

- **Navegación entre pestañas con el barrido completo**: Inicio, Novedades, Biblioteca y Buscar se desplazan de lado a lado a alta velocidad en vez de dar un pequeño empujón.
- **Scroll más fluido en la interfaz nueva**: cada sección declara su tipo, así que el sistema deja de hacer trabajo inútil en cada frontera de sección al desplazarte.
- **Perfil de arranque incluido en el APK** para que el primer scroll, el primer reproductor y la primera ficha de artista vayan compilados en vez de interpretados.

## Reproducción

- **Cambiar entre música y vídeo vuelve a ser instantáneo pasado un rato.** El presupuesto de pre-resolución era un contador de por vida: tras ocho cambios no volvía a adelantarse nada en toda la sesión.
- **Si tu sesión caduca, la app lo dice.** Antes mostraba «Este contenido no está disponible», que culpa a la canción, cuando lo que hacía falta era volver a iniciar sesión.
- **Letras con artistas invitados:** mejorada la búsqueda cuando el tema viene acreditado a varios artistas.

## Ajustes

- **Todas las opciones de Apariencia funcionan en las dos interfaces**, la clásica y la nueva.
- **Tus preferencias no se pierden al actualizar:** auditado y fijado con pruebas para que ningún cambio futuro pueda borrarlas en silencio.
