package iad1tya.echo.music.utils

/**
 * 🔴 MODO SIN CONEXIÓN AUTOMÁTICO — la decisión, en puro (punto 4 del dueño, 2026-09-17).
 *
 * Tres entradas y una salida, sin Android por medio, para que las reglas se puedan probar:
 *
 *  · [manual] — [iad1tya.echo.music.constants.OfflineModeKey], el interruptor que él pone a mano.
 *    Manda siempre: si lo enciende, la app está sin conexión aunque haya cobertura de sobra.
 *  · [auto] — [iad1tya.echo.music.constants.AutoOfflineModeKey], "que se ponga solo". ON por defecto.
 *  · [online] — si el aparato tiene red ahora mismo ([NetworkState]).
 *
 * ── Por qué NO se escribe el interruptor manual ───────────────────────────────────────────────────
 * Lo obvio sería poner `OfflineModeKey = true` al perder la red, y es exactamente lo que no se hace:
 * ese interruptor es del dueño, es persistente, y además cambia el comportamiento del reproductor
 * (`MusicService` se niega a resolver por red con él encendido). Escribirlo significaría (a) dejarle
 * el ajuste cambiado para siempre por un túnel de metro, y (b) que un fallo de detección le bloquee
 * la reproducción. Así que el automático es una capa **encima**, que se calcula y no se guarda: al
 * volver la red desaparece sola, sin nada que deshacer y sin tocar nada suyo.
 *
 * ── Y por qué el automático es SOLO interfaz ──────────────────────────────────────────────────────
 * No toca `MusicService`. Sin red, resolver por red falla de todos modos, así que no hay nada que
 * ganar; y si la detección se equivoca, una capa de interfaz muestra descargas de más, mientras que
 * bloquear el reproductor le dejaría la app muda con red disponible. El error barato, no el caro.
 */
object AutoOfflinePolicy {
    /**
     * Cuánto hay que llevar sin red para declarar "sin conexión".
     *
     * Ni 0 ni mucho: un cambio de wifi a datos, un ascensor o un túnel corto sueltan un `onLost`
     * seguido de un `onAvailable` en menos de un segundo, y cambiar Inicio entero en ese hueco es
     * peor que esperar — se ve un parpadeo de "sin conexión" que ya no era verdad al terminar de
     * dibujarse. Volver, en cambio, es inmediato (0 ms): recuperar la red es lo que él quiere ver ya.
     */
    const val OFFLINE_DEBOUNCE_MS = 2_500L

    /** Lo que las pantallas preguntan: ¿toca mostrar solo lo local? */
    fun offline(manual: Boolean, auto: Boolean, online: Boolean): Boolean =
        manual || (auto && !online)

    /**
     * ¿Es el automático — y no él — quien nos ha puesto sin conexión? Gobierna el cartel: con el
     * manual hay un "Desactivar" que sirve; con el automático no hay nada que desactivar (vuelve
     * solo), así que ese botón sería un placebo y el cartel dice otra cosa.
     */
    fun automatic(manual: Boolean, auto: Boolean, online: Boolean): Boolean =
        !manual && auto && !online

    /** El retardo de arriba, como decisión y no como comparación suelta repartida por ahí. */
    fun shouldDeclareOffline(msWithoutNetwork: Long): Boolean =
        msWithoutNetwork >= OFFLINE_DEBOUNCE_MS
}
