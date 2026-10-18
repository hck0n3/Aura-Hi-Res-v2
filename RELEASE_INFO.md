# Aura Hi-Res v2.0.65-beta16 — Cast más estable: el video no se apaga solo, la canción no vuelve atrás y la cola carga antes

Beta encima de la 2.0.65-beta15 (incluye todo lo suyo). Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Transmitir a la tele (Cast)

- **El video ya no se apaga solo:** al pasar a Video, la tele dejaba el video y volvía a modo música unos segundos después, porque el
  teléfono tomaba su propio ajuste interno de la canción por un cambio de canción. Ahora la canción que la tele muestra en video es
  «la misma canción» y se queda en video hasta que tú pases a Música o cambies de canción.
- **Al pasar a la siguiente canción ya no vuelve a la anterior:** un cambio de Música/Video no arrastra la tele a una canción que
  ya dejó.
- **La barra de tiempo continúa:** cuando hay que recargar la cola, la tele sigue desde donde va, no desde donde se quedó el teléfono
  (que está en pausa mientras transmites).
- **Empieza antes:** la canción de la tele y las que la rodean se preparan a la vez, no una tras otra.
- **Si la tele se queda en negro con sonido,** el registro (Ajustes ▸ Registros) ahora dice qué estado y qué error de la tele hubo
  (`CAST_STATE`, `CAST_ERROR`, `CAST_LOAD`). Esa causa todavía no está demostrada: con ese registro se puede arreglar de verdad.
