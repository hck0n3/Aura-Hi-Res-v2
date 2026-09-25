# Aura Hi-Res v2.0.61-beta3 — Registro para confirmar de verdad si la carrera de colas quedó resuelta

Beta encima de la 2.0.61-beta2. Conserva tus datos, tu sesión y tus ajustes: mismo paquete y misma firma.

## Por qué esta beta

- Tu `app.log` de la beta2 no alcanzaba para confirmar si el arreglo de "dos colas peleando" funcionó
  — el dato que lo hubiera mostrado depende de tener Aleatorio mejorado activado.
- Esta beta **no cambia ningún comportamiento**: solo agrega un registro puntual (`QUEUE_RACE_GUARD`)
  en los 4 lugares donde el arreglo actúa, sin datos personales.

## Qué necesito que hagas

1. Instalá esta beta.
2. Reproducí a propósito lo que veníamos viendo: cambiá de canción o de estilo varias veces seguidas y
   rápido, y también probá tocar dos veces la primera canción de una playlist.
3. Compartime el `app.log` de esa prueba puntual (Ajustes ▸ Registros).

Con eso sí puedo confirmarte, con evidencia real y no a ciegas, si el problema quedó resuelto.
