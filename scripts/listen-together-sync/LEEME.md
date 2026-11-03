# Medir la sincronía de «Escuchar juntos» con el micrófono de la laptop

Herramientas usadas en la sesión del 2026-11-02 (fila 647/648 de `docs/REGRESSION_REGISTRY.md`). Solo para PRUEBAS: no se publican ni forman parte de la app.

## Con el video de pitidos (la medida buena)
1. En el celular (anfitrión) busca **«Audio Video Sync Test 60 FPS»** (resultado «Vídeo · coral») y reprodúcelo. Crea la sala y une la tablet.
2. Graba con la laptop: `.\grabar-mic.ps1 -Segundos 60 -Salida C:\Users\PC\mic.wav` (empieza a grabar justo antes de unir la tablet para ver cómo arranca).
3. Analiza: `node attacks.js mic.wav` → una entrada por pitido: `1` = solo se oye un ataque; `88(2)` = el segundo aparato suena 88 ms después del primero. **Meta: < 25 ms.**
4. El signo no se ve (no se sabe cuál va primero): cambia el «Ajuste de sincronía» de un aparato y mira si el hueco baja o sube.

## Con música (menos precisa)
`node ceps.js mic.wav` busca una copia retrasada de la música (cepstro). Valida con `node ceps.js mic.wav synth 45`. Pasa de ~30 ms con copia ≥ 25 % del volumen; no ve nada por debajo de ~12 ms.

## Trampas
- Pon los dos aparatos a distancia parecida de la laptop.
- Detrás del video de pitidos la lista sigue con otro contenido: graba mientras suena el video.
- Con los dos altavoces Bluetooth la app asume 180 ms en ambos y se cancelan; el hueco real (~130 ms medido) lo corrige el ajuste manual por salida.

## `onsets.js` (útil sin Bluetooth)
`node onsets.js mic.wav` lista el instante de cada pitido (ms) y los huecos entre ataques: un segundo ataque a +15…+40 ms del primero es el otro aparato. Con altavoces Bluetooth conviene `attacks.js`.
