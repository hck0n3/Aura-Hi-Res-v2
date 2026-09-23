# Aura Hi-Res v2.0.50-beta1 — Pedir música sin repetir, deslizar para más, discografías completas

Beta encima de la 2.0.49. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.
Diez rondas de reportes, cada una auditada por separado antes de pasar a la siguiente.

## Pedir música

- **Ya no repite las mismas canciones.** Pedir "reggae" dos veces por separado devolvía siempre la
  misma lista — la elección en sí siempre fue correcta (el orden de origen manda, tu gusto empuja),
  lo que faltaba era recordar qué ya te sirvió antes. Ahora lo recuerda durante 45 minutos y elige
  entre lo que queda, sin dejar nunca una lista más corta de lo pedido.
- **El reconocimiento de voz ya no se cierra a medio hablar.** Tanto aquí como en el buscador normal:
  antes cortaba el dictado con el primer silencio corto (pensado para una palabra suelta), ahora da
  más margen para una frase con pausas naturales.

## Deslizar canciones

- **La papelera de tus playlists solo aparece mientras deslizas.** Antes se veía en todas las
  canciones aunque no las tocaras — quedó así por un descuido de la ronda anterior.
- **Deshacer.** Al eliminar una canción de tu playlist con el deslizar, aparece un aviso con la
  opción de deshacerlo.
- **Me gusta deslizando, en álbumes, playlists ajenas y canciones de artista.** Donde no puedes
  borrar (no es tuyo) ahora deslizar a la derecha marca "me gusta" de una vez, y a la izquierda abre
  el mismo menú de siempre para "no me gusta" y "agregar a una playlist".

## Video

- **Un video que ya falló no lo vuelve a ofrecer.** Si "pasar a video" ya dio error una vez en una
  canción, deja de aparecer esa opción para ella — en pedir música, en la cola, en cualquier lista.
- **Más formatos de video compatibles**, cuando tu teléfono los decodifica por hardware (se
  comprueba antes de ofrecerlos, para no arriesgar tartamudeo en equipos más viejos).
- **La prioridad sigue siendo audio primero** — ya funcionaba así desde la ronda pasada, esta vez se
  confirmó que sigue intacto.

## Novedades y artistas

- **El radar de Novedades ahora ordena por lo que más te gusta**, no solo por si sigues al artista o
  no.
- **La discografía de un artista ya no sale incompleta** cuando YouTube no da la lista completa de
  una — ahora sí se completa contra iTunes/Apple Music en esos casos también.

## Escuchar juntos

- **Menos desajuste en canciones largas.** El anfitrión avisaba su posición solo en momentos puntuales
  (cambiar de canción, pausar, saltar); ahora también se revisa cada 15 segundos mientras suena, para
  que una canción larga sin esos momentos no se desalinee.

## Nota técnica (sin verificar aún en dispositivo)

- **Ecualizador y volumen**: se confirma que el ajuste de la ronda anterior sigue activo y correcto en
  el código; no se encontró ningún otro mecanismo que cambie el volumen al mover una banda. Se agregó
  registro (sin datos personales) para diagnosticarlo con evidencia real si vuelve a pasar — y ayuda
  confirmar si lo que se mueve es una banda de frecuencia o el deslizador de preamplificación (son
  controles distintos).
