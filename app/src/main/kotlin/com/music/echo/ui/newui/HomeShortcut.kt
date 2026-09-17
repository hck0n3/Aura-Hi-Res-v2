package iad1tya.echo.music.ui.newui

/**
 * 🔴 EL CONTRATO DEL ATAJO A INICIO (dueño, 2026-09-17: *"sin importar dónde esté, el botón de inicio
 * del minirreproductor tiene que mandarlo a la pantalla de inicio SÍ O SÍ"*).
 *
 * Las reglas, aparte del `NavController` para poder probarlas de verdad. La navegación se intenta en
 * tres peldaños, cada uno más contundente que el anterior, y el último no puede fallar:
 *
 *  1. **¿Ya estamos?** ([needsNavigation]) — si la pantalla actual ya es Inicio no se hace nada.
 *  2. **Volver** — `popBackStack` hasta la entrada de Inicio que ya exista. Es lo más barato y
 *     conserva su sitio en la lista; devuelve false si Inicio no está en la pila.
 *  3. **Ir como pestaña** — el `navigateAsTab` de siempre (guarda y restaura estado), que es lo que se
 *     hacía antes y funciona en el caso normal.
 *  4. **Forzar** ([forceNeeded]) — se vuelve a mirar dónde estamos y, si por lo que sea seguimos fuera
 *     de Inicio, se navega a pelo. Este peldaño existe porque su orden dice "sí o sí": puede dejar una
 *     entrada de más en la pila, y eso es infinitamente preferible a un botón que no hace nada.
 */
internal object HomeShortcut {

    /** false solo cuando ya estamos en Inicio: pulsar ahí no debe mover nada. */
    fun needsNavigation(currentRoute: String?, homeRoute: String): Boolean {
        val current = currentRoute?.substringBefore('?') ?: return true
        return current != homeRoute
    }

    /**
     * true cuando, después de intentarlo, seguimos sin estar en Inicio y hay que forzar.
     *
     * Una ruta desconocida (null) cuenta como "no estamos": preferimos una navegación de más a
     * dejarle el botón muerto, que es justo lo que él reportó.
     */
    fun forceNeeded(routeAfterAttempt: String?, homeRoute: String): Boolean =
        needsNavigation(routeAfterAttempt, homeRoute)

    /**
     * El nombre de pantalla que se registra en el log: lo de antes de la barra.
     *
     * Regla 4 de AGENTS: "album/OLAK5uy…" lleva el id de lo que él estaba escuchando y NO puede ir al
     * log que él comparte; "album" a secas es el nombre de una pantalla y no dice nada de él.
     */
    fun screenName(route: String?): String =
        route?.substringBefore('/')?.substringBefore('?')?.takeIf { it.isNotBlank() } ?: "none"
}
