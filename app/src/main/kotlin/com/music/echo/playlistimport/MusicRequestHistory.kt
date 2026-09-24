package iad1tya.echo.music.playlistimport

/**
 * Ronda 6 del dueño: "pedir música" no tenía forma de repetir una petición ni de pedir otra tanda sin
 * reescribir el texto. Las dos cosas son el mismo problema — un toque que vuelve a mandar exactamente
 * lo que ya se pidió — así que un solo historial cubre ambas: repetir la ÚLTIMA petición ES "otra
 * tanda" (con canciones distintas, gracias al TTL de [MusicRequestRecents]); repetir una ANTERIOR es el
 * historial.
 *
 * Memoria de sesión, como [MusicRequestRecents]: se pierde al reiniciar la app a propósito — es un
 * atajo de comodidad, no un dato que el dueño necesite conservar entre sesiones, y guardarlo en disco
 * sería una superficie nueva (¿se puede borrar?, ¿cuenta como "dato del usuario" para AGENTS.md regla
 * 4?) para un beneficio marginal frente a la sesión actual, que es donde de verdad se repite una
 * petición.
 */
object MusicRequestHistory {
    private const val MAX_ENTRIES = 6

    private val recent = ArrayDeque<String>()

    /** Registra una petición que sí produjo resultado. Sin duplicados: repetir una mueve al frente. */
    @Synchronized
    fun record(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return
        recent.removeAll { it.equals(trimmed, ignoreCase = true) }
        recent.addFirst(trimmed)
        while (recent.size > MAX_ENTRIES) recent.removeLast()
    }

    /** Las peticiones recientes, más nueva primero. */
    @Synchronized
    fun recentPrompts(): List<String> = recent.toList()
}
