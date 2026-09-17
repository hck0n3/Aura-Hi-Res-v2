package iad1tya.echo.music.utils

/**
 * # Cuando YouTube rechaza QUIÉN eres, no QUÉ pides
 *
 * 🔴 REGISTRO DEL DUEÑO (2026-09-16, app estable 2.0.42, sesión iniciada): **36 canciones distintas, cero
 * éxitos**, todas muertas igual:
 *
 * ```
 * Client failed: ANDROID_VR — UNPLAYABLE: Este contenido no está disponible. Vuelve a intentarlo más tarde.
 * Client failed: IOS — 400 "Precondition check failed."
 * Bad stream player response - all clients failed
 * ```
 *
 * Ni una línea de cifrado ni de poToken en todo el registro: las dos identidades que el camino de audio
 * usa ([YTPlayerUtils.MAIN_CLIENT] ANDROID_VR y su único respaldo IOS) mueren ANTES de firmar nada. Y en
 * el mismo teléfono y la misma red, la beta **sin sesión** reproducía perfectamente. Lo que YouTube está
 * rechazando es la identidad de la cuenta, no el contenido ni la red.
 *
 * ## El defecto que esto arregla
 * Aura YA tiene la red de seguridad para exactamente esta situación: reintentar esa canción de forma
 * anónima y rotar la sesión de invitado. En el registro del dueño **se intentó cero veces**, porque el
 * disparador es [YTPlayerUtils.AUTH_SHAPED_STATUSES], que solo contiene `LOGIN_REQUIRED`, y lo suyo llega
 * como `UNPLAYABLE`. La red existía y no veía el caso.
 *
 * ## Por qué NO basta con meter UNPLAYABLE en ese conjunto
 * Porque casi siempre `UNPLAYABLE` **sí** es una propiedad del CONTENIDO: un vídeo retirado, bloqueado por
 * región o solo para miembros. El KDoc de ese conjunto ya explica por qué se sacaron de ahí
 * `AGE_CHECK_REQUIRED` y `CONTENT_CHECK_REQUIRED` — un reintento anónimo sobre contenido que de verdad no
 * está disponible es tiempo perdido garantizado, y el usuario espera el doble antes del salto.
 *
 * La diferencia entre las dos cosas no está en un fallo suelto: está en la **racha**. Una canción que da
 * `UNPLAYABLE` es contenido. Treinta y seis distintas seguidas, sin un solo acierto, es identidad. Eso es
 * lo único que mide esta clase, y por eso cuenta **vídeos distintos** y no intentos: reintentar la misma
 * canción tres veces no dice nada nuevo.
 *
 * Es una clase pura para que ese umbral se pueda comprobar en un test en vez de a ojo.
 */
class IdentityRejectionTracker(
    private val threshold: Int = DEFAULT_THRESHOLD,
) {
    /**
     * Vídeos distintos que han fallado así desde el último acierto. Es un conjunto y no un contador
     * porque la señal es "cuántas canciones DIFERENTES", y está acotado para que una sesión larga sin
     * red no lo haga crecer sin fin: pasado el umbral, un id más no aporta nada a la decisión.
     */
    private val rejected = LinkedHashSet<String>()

    /** Cuántos vídeos distintos llevan rechazados. Para registro y tests; no decide por sí solo. */
    @get:Synchronized
    val distinctRejections: Int get() = rejected.size

    /**
     * Un rechazo con forma de identidad: la sesión está iniciada y el servidor dijo que este vídeo no se
     * puede reproducir, ANTES de cualquier trabajo de firma.
     */
    @Synchronized
    fun recordRejection(videoId: String) {
        if (videoId.isBlank()) return
        if (rejected.size >= MAX_TRACKED && videoId !in rejected) return
        rejected += videoId
    }

    /**
     * Cualquier reproducción que sale bien borra la sospecha. Es lo que impide que un puñado de canciones
     * legítimamente no disponibles, repartidas a lo largo de una tarde de escucha normal, se sumen hasta
     * cruzar el umbral y disparen un reintento anónimo que nadie necesitaba.
     */
    @Synchronized
    fun recordSuccess() {
        rejected.clear()
        warned = false
    }

    /** True cuando la racha ya no se explica por el contenido. */
    @get:Synchronized
    val isIdentityShaped: Boolean get() = rejected.size >= threshold

    /** Ya se avisó al usuario de esta racha. Se limpia con el primer acierto. */
    private var warned = false

    /**
     * Pide el turno para avisar al usuario UNA vez por racha.
     *
     * Esto es lo que le habría ahorrado la tarde al dueño: su app decía *"Este contenido no está
     * disponible"*, que culpa a la canción, mientras el problema real era su sesión caducada. Treinta y
     * seis canciones después seguía sin saberlo. Un aviso que nombre la causa convierte un misterio de
     * media tarde en treinta segundos: cerrar sesión y volver a entrar.
     *
     * Una sola vez por racha a propósito: la alternativa es un aviso por cada canción que falla, que es
     * ruido y acaba ignorándose.
     */
    @Synchronized
    fun consumeReLoginHint(): Boolean {
        if (!isIdentityShaped || warned) return false
        warned = true
        return true
    }

    companion object {
        /**
         * Tres vídeos distintos. Dos es poco — un álbum puede tener dos pistas bloqueadas seguidas — y
         * más alto solo alarga lo que el dueño ya vivió: con su fallo, el umbral se cruza en la tercera
         * canción en vez de en la trigésimo sexta.
         */
        const val DEFAULT_THRESHOLD = 3

        /** Tope de memoria: pasado el umbral, un id más no cambia ninguna decisión. */
        const val MAX_TRACKED = 32
    }
}
