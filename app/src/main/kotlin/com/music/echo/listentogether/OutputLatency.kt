package iad1tya.echo.music.listentogether

/**
 * Cuánto tarda el sonido en salir de verdad por el altavoz, y cómo compensarlo en una sala.
 *
 * 🔴 PETICIÓN DEL DUEÑO (2026-09-17): *"la compensación de latencia Bluetooth sigue pendiente, y
 * probablemente es la que más notas tú" → "bueno soluciona esto también"*.
 *
 * ## El problema, y por qué el reloj compartido no lo ve
 * `player.currentPosition` dice dónde va el DECODIFICADOR, no lo que está saliendo por el altavoz.
 * Entre una cosa y otra hay un retardo real: unas decenas de ms por el altavoz del teléfono, pero
 * **150-250 ms por Bluetooth**, según el códec. Así que dos teléfonos pueden estar en la MISMA
 * posición —el reloj compartido diciendo que todo va perfecto— y sonar con dos décimas de desfase.
 * Ninguna cantidad de sincronizar posiciones arregla eso, porque el desfase está después.
 *
 * ## Por qué esto son valores por defecto y no una medición
 * **Android no expone ninguna API pública para la latencia de salida de un dispositivo Bluetooth.**
 * `AudioManager.getOutputLatency` es `@hide` y está bloqueada por las restricciones de interfaces
 * no-SDK; `AudioDeviceInfo` no trae el dato; y el reloj de `AudioTrack.getTimestamp` lo gestiona
 * media3 por dentro y en A2DP casi nunca incluye el búfer del aparato receptor. Por eso VLC, MX
 * Player y Plex —todos— llevan un ajuste manual de sincronía: no es pereza suya, es que el dato no
 * existe.
 *
 * Lo que sí se puede saber es **por dónde está saliendo el audio**, y eso ya lo resuelve
 * `EqDeviceProfileStore.currentOutputKey`. Con el tipo de salida se acierta el orden de magnitud
 * automáticamente, que es de donde viene casi toda la mejora: la diferencia entre 0 y 180 ms es lo
 * que se oye; la diferencia entre 180 y 200 no.
 *
 * ## Cada extremo compensa LO SUYO, y eso es lo que hace que funcione
 * El primer diseño que probé compensaba solo en el invitado, y está mal: con los dos aparatos por
 * Bluetooth los retardos se cancelan entre sí, así que corregir 180 ms de un lado **sobrecorrige** y
 * deja la sala peor que antes. Un dispositivo solo puede conocer su propia salida, nunca la del
 * otro.
 *
 * La regla correcta es que **el anfitrión publique lo que OYE** (su posición menos su retardo) en
 * vez de lo que decodifica, y que el invitado compare contra eso su propia posición oída. Así cada
 * uno resta lo que sabe y las tres combinaciones salen bien:
 *
 * | anfitrión | invitado | resultado |
 * |---|---|---|
 * | altavoz | Bluetooth | el invitado adelanta su decodificador 180 ms → se oyen a la vez |
 * | Bluetooth | altavoz | el invitado atrasa 180 ms → se oyen a la vez |
 * | Bluetooth | Bluetooth | los dos restan 180, la diferencia es cero → nada que corregir |
 *
 * No hace falta ningún campo nuevo en el protocolo: se publica otro NÚMERO en el campo de posición
 * que ya existe. Y para las salas mixtas (Metrolist, SimpMusic, una versión vieja de Aura) esto es
 * **más correcto que antes**, no un cambio arbitrario: si el anfitrión está por Bluetooth y publica
 * su posición cruda, un invitado por altavoz oye la música 180 ms ANTES que él. Publicar lo que se
 * oye lo arregla para todos.
 */
object OutputLatency {

    /** Altavoz del teléfono: el camino más corto que hay. */
    const val SPEAKER_MS = 0

    /** Cable: analógico, sin búfer que compensar. */
    const val WIRED_MS = 0

    /** USB: hay una conversión de por medio, pero es corta. */
    const val USB_MS = 10

    /**
     * Bluetooth A2DP. 180 ms es la mediana honesta del rango real: SBC ronda los 200, AAC unos 170
     * (y en Android varía más que en iOS), aptX unos 130, LDAC vuelve a subir a 200. Elegir la
     * mediana en vez del mejor caso es deliberado: quedarse corto deja desfase audible, y pasarse
     * lo compensa el ajuste manual.
     */
    const val BLUETOOTH_MS = 180

    /**
     * Retardo estimado para una clave de salida de [iad1tya.echo.music.eq.data.EqDeviceProfileStore].
     *
     * Se decide por el PREFIJO de la clave y no por un `AudioDeviceInfo`, para que esta función sea
     * pura y comprobable sin un dispositivo. Las claves las construye `EqDeviceProfileStore`:
     * `__phone__`, `bt:<nombre>`, `wired`, `usb`.
     */
    fun defaultMsFor(outputKey: String): Int = when {
        outputKey.startsWith("bt:") -> BLUETOOTH_MS
        outputKey == "wired" -> WIRED_MS
        outputKey == "usb" -> USB_MS
        else -> SPEAKER_MS
    }

    /**
     * El desfase respecto al anfitrión, ya compensado por lo que tarda MI salida.
     *
     * Si mi Bluetooth añade 180 ms, lo que oigo va 180 ms por detrás de mi decodificador, así que
     * para OÍR a la vez que el anfitrión mi decodificador tiene que ir 180 ms POR DELANTE del suyo.
     * De ahí el signo: el objetivo no es su posición, es su posición más mi retardo.
     *
     * @param myPositionMs dónde va mi decodificador.
     * @param hostPositionMs dónde va el suyo, ya corregido por el tiempo de vuelo del mensaje.
     * @param myLatencyMs lo que tarda mi salida ([effectiveMs]).
     * @return positivo = voy adelantado y tengo que frenar, que es lo que espera [SyncCorrection].
     */
    fun syncErrorMs(myPositionMs: Long, hostPositionMs: Long, myLatencyMs: Int): Long =
        myPositionMs - hostPositionMs - myLatencyMs
}
