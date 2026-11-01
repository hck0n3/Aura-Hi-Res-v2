package iad1tya.echo.music.listentogether

/**
 * Row 601 — the length of a room code depends on WHICH rooms are in use. The shared server mints 8-character codes; Aura's own rooms
 * (docs/worker/rooms) mint 6-character ones and answer 400 «bad code» to anything else. The screens used to ask for exactly 8, so with
 * «Salas de Aura» on the second device could type the code but the Join button never enabled (owner 2026-10-31: «creo el código y cuando
 * lo pongo en otro dispositivo no me deja usar la opción de unirse»). Pure.
 */
object RoomCodes {
    const val SHARED_LENGTH = 8
    const val AURA_LENGTH = 6

    fun length(auraRooms: Boolean): Int = if (auraRooms) AURA_LENGTH else SHARED_LENGTH

    /** What the user may type: letters and digits, upper-cased, cut to the length of the rooms in use. */
    fun normalize(input: String, auraRooms: Boolean): String =
        input.uppercase().filter { it.isLetterOrDigit() }.take(length(auraRooms))

    fun isComplete(code: String, auraRooms: Boolean): Boolean = code.length == length(auraRooms)

    fun placeholder(auraRooms: Boolean): String = if (auraRooms) "ABC234" else "ABCD1234"
}
