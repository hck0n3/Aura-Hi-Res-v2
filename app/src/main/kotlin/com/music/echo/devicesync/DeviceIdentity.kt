package iad1tya.echo.music.devicesync

import java.security.MessageDigest

/**
 * Row 540 — who a device IS in the sync room.
 *
 * The id used to be a random string saved in the app's own settings. Settings travel: a restored backup, Samsung Smart Switch or any
 * device-to-device transfer copied the id from the phone onto the tablet, and the room — which knows devices only by id — saw ONE
 * device on two pieces of hardware. The two kept replacing each other's connection (the device counter jumping 2↔3), an order for
 * «the tablet» reached the phone (a device starting to play out of nowhere, the other not stopping), and the library never settled
 * (owner 2026-10-30). The id now comes from the hardware: it cannot be copied to another device, and a copied id is overwritten on
 * the first start.
 */
object DeviceIdentity {
    /** The value some old emulators and rooted builds report as ANDROID_ID: shared by many devices, so useless as an identity. */
    private const val BROKEN_ANDROID_ID = "9774d56d682e549c"

    /** The room id of the device whose hardware id is [androidId], or null when there is no usable one (the caller falls back to a random id). */
    fun fromHardware(androidId: String?): String? {
        val raw = androidId?.trim().orEmpty()
        if (raw.length < 8 || raw.equals(BROKEN_ANDROID_ID, ignoreCase = true)) return null
        val hash = MessageDigest.getInstance("SHA-256").digest("aura-sync-device:$raw".toByteArray())
        return "d" + hash.joinToString("") { "%02x".format(it) }.take(19)
    }

    /** The last four characters, enough for the user to tell two devices apart on screen without showing the id. */
    fun shortTag(id: String): String = id.takeLast(4)
}
