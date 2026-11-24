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

    /**
     * Row 818 (owner decision B1): rooms are only Aura's now, so a code Aura's rooms cannot open is refused before any socket, with
     * a clear message instead of a failed connection. [code] is what the session sends: trimmed and upper-cased.
     */
    fun isAuraCode(code: String): Boolean = code.length == AURA_LENGTH && code.all { it.isLetterOrDigit() }

    /** Row 818: what was typed or pasted is a code of the shared (Metrolist / SimpMusic) server, which Aura no longer uses. */
    fun isSharedServerCode(input: String): Boolean = input.uppercase().filter { it.isLetterOrDigit() }.length == SHARED_LENGTH

    /** Row 818: `ListenTogetherState.error` for a code that is not an Aura room's; the screens show it in the user's language. */
    const val NOT_AURA_CODE_ERROR = "not_an_aura_room_code"
}

/** Row 818: whether the one-time switch of a stored «Salas de Aura» to on still has to run. */
internal fun auraRoomsOnlyMigrationNeeded(alreadyApplied: Boolean?): Boolean = alreadyApplied != true
