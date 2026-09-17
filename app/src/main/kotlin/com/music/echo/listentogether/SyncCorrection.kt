package iad1tya.echo.music.listentogether

import kotlin.math.abs

/**
 * Cómo un invitado alcanza al anfitrión: **ajustando la velocidad**, no saltando.
 *
 * 🔴 PREGUNTA DEL DUEÑO (2026-09-17): *"la función de escuchar juntos nunca he logrado que funcione
 * sincronizada a la perfección y quiero saber por qué"*, y después: *"hazlo entonces como lo hace
 * Sonos o AirPlay"*.
 *
 * ## Por qué saltar nunca puede sonar bien
 * Hasta ahora el invitado comparaba su posición con la del anfitrión y hacía `seekTo` si la
 * diferencia pasaba de 750 ms. Eso obliga a elegir entre dos cosas malas: con una tolerancia
 * pequeña saltas constantemente y cada salto es un corte audible; con una grande dejas de saltar
 * pero aceptas hasta tres cuartos de segundo de desfase como normal. No hay un valor bueno, porque
 * **el salto es la herramienta equivocada**.
 *
 * Los sistemas multi-habitación de verdad (Sonos, AirPlay 2) no saltan: **estiran el tiempo**.
 * Si vas 200 ms adelantado, reproducen un 2 % más lento durante unos segundos hasta alinearse y
 * vuelven a la velocidad normal. Como el estirado preserva el tono, no se oye nada: ni un corte, ni
 * un cambio de afinación. Eso es lo que hace esta clase.
 *
 * ## Los tres regímenes
 *  · **|error| ≤ [IN_SYNC_MS]** → [SyncAction.Hold], velocidad exactamente 1.0. Por debajo de unas
 *    décimas nadie distingue dos fuentes, y perseguir el cero significaría corregir para siempre.
 *  · **hasta [MAX_TRIM_MS]** → [SyncAction.Trim]: converger con la velocidad.
 *  · **por encima** → [SyncAction.Resync]: saltar. No es una rendición: cerrar 5 s a un 2 % tardaría
 *    más de cuatro minutos, o sea la canción entera desincronizada por no dar un salto de 50 ms.
 *    Esto pasa al entrar a una sala a mitad de canción, que es justo cuando un salto no molesta.
 *
 * ## Por qué el tope del 2 %
 * [MAX_TRIM] es lo que decide si esto se oye o no. Con el tono preservado, un ±2 % de tempo es
 * inaudible incluso en música con pulso marcado; a partir de un 4 % empieza a notarse que la canción
 * "corre". Cerrar despacio y que no se note es el objetivo — no cerrar rápido.
 *
 * El coste de ese tope es honesto y conviene tenerlo escrito: al 2 % se recuperan 20 ms por segundo,
 * así que un desfase de 300 ms tarda unos 15 s en cerrarse. Durante esos 15 s la diferencia va
 * bajando todo el rato, que es lo contrario de hoy, donde 300 ms se quedan ahí para siempre porque
 * no llegan al umbral de salto.
 */
object SyncCorrection {
    /** Por debajo de esto se considera sincronizado y la velocidad vuelve a 1.0 exacta. */
    const val IN_SYNC_MS = 60L

    /** A partir de aquí converger con la velocidad tardaría demasiado y se salta. */
    const val MAX_TRIM_MS = 1_000L

    /** Desviación máxima de velocidad. Con el tono preservado, ±2 % es inaudible. */
    const val MAX_TRIM = 0.02f

    /**
     * En cuántos segundos se INTENTA cerrar el hueco, antes de que [MAX_TRIM] recorte.
     *
     * Diez y no cuatro, y el motivo es la forma de la curva y no la prisa: con cuatro, cualquier
     * error por encima de 80 ms ya pedía más del 2 % y salía recortado, así que el recorte era un
     * ESCALÓN — se pega al tope hasta 60 ms y se apaga de golpe, que es justo lo que hace oscilar al
     * invitado alrededor del anfitrión. Con diez, el tope manda por encima de 200 ms (donde lo único
     * sensato es ir todo lo rápido que se pueda sin que se oiga) y entre 60 y 200 ms la corrección se
     * ATENÚA sola conforme se acerca, que es como se aterriza sin sobrepasar.
     */
    const val CONVERGE_SECONDS = 10f

    /**
     * Qué hacer con un desfase de [errorMs] respecto al anfitrión.
     *
     * @param errorMs posición del invitado menos la del anfitrión. POSITIVO = el invitado va
     *   ADELANTADO y tiene que ir más lento.
     * @param targetMs dónde debería estar, para el salto cuando el hueco es demasiado grande.
     */
    fun correct(errorMs: Long, targetMs: Long): SyncAction = when {
        abs(errorMs) <= IN_SYNC_MS -> SyncAction.Hold
        abs(errorMs) > MAX_TRIM_MS -> SyncAction.Resync(targetMs.coerceAtLeast(0L))
        else -> SyncAction.Trim(trimSpeed(errorMs))
    }

    /**
     * La velocidad que cierra [errorMs] en [CONVERGE_SECONDS], recortada a [MAX_TRIM].
     *
     * Proporcional al error y no un escalón fijo: un escalón sobrepasa y deja al invitado oscilando
     * alrededor del anfitrión, corrigiendo en un sentido y luego en el otro sin parar.
     */
    fun trimSpeed(errorMs: Long): Float {
        val delta = (-errorMs / (CONVERGE_SECONDS * 1000f)).coerceIn(-MAX_TRIM, MAX_TRIM)
        return 1f + delta
    }

    /**
     * La velocidad que hay que pedirle al reproductor, respetando el tempo que el usuario haya
     * elegido a mano en «Tempo y tono».
     *
     * Se MULTIPLICA en vez de sobrescribir: alguien que puso la canción a 1.25× sigue oyéndola a
     * 1.25× mientras la sala lo alinea. Sobrescribir le arrebataría su ajuste sin avisar, y al salir
     * de la sala se lo devolvería de golpe con un cambio de velocidad audible.
     */
    fun playerSpeed(userTempo: Float, action: SyncAction): Float = when (action) {
        is SyncAction.Trim -> userTempo * action.speed
        else -> userTempo
    }
}

/** Qué hacer para alcanzar al anfitrión. */
sealed interface SyncAction {
    /** Ya está sincronizado: velocidad normal, no tocar nada. */
    data object Hold : SyncAction

    /** Converger cambiando la velocidad [speed] (relativa a 1.0), sin cortes. */
    data class Trim(val speed: Float) : SyncAction

    /** El hueco es demasiado grande para cerrarlo con la velocidad: saltar a [positionMs]. */
    data class Resync(val positionMs: Long) : SyncAction
}
