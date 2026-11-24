package iad1tya.echo.music.license

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of a license check performed by the backend Worker. */
enum class LicenseStatus {
    ACTIVE, ENDED, INVALID_KEY, DEVICE_MISMATCH, NETWORK_ERROR,

    /** The email typed in the app is not the one that bought the license (row 530). Only a Worker that knows about
     *  emails answers this, and only to a request that carried one. */
    EMAIL_MISMATCH,
}

/** Pure parser of the Worker's `{ "status": "..." }` JSON. Unit-testable with plain JUnit. */
object LicenseStatusParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** True when the Worker confirmed the typed email against the Gumroad purchase (`email_ok`). Master keys never carry it. */
    fun emailVerified(body: String): Boolean =
        try {
            json.parseToJsonElement(body).jsonObject["email_ok"]?.jsonPrimitive?.contentOrNull == "true"
        } catch (e: Exception) {
            false
        }

    /** Row 851: true when the Worker says the key is a master key (`master: true`). Absent, an old Worker or garbage: false. */
    fun masterKey(body: String): Boolean =
        try {
            json.parseToJsonElement(body).jsonObject["master"]?.jsonPrimitive?.contentOrNull == "true"
        } catch (e: Exception) {
            false
        }

    fun parse(body: String): LicenseStatus =
        try {
            when (json.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.contentOrNull) {
                "active" -> LicenseStatus.ACTIVE
                "ended" -> LicenseStatus.ENDED
                "device_mismatch" -> LicenseStatus.DEVICE_MISMATCH
                "email_mismatch" -> LicenseStatus.EMAIL_MISMATCH
                "invalid" -> LicenseStatus.INVALID_KEY
                else -> LicenseStatus.NETWORK_ERROR
            }
        } catch (e: Exception) {
            LicenseStatus.NETWORK_ERROR
        }
}

/**
 * Row 851 (owner order 2026-11-24): a master key never pairs devices by email. Pure, so the rule is tested without Android.
 * Cloud backup keeps using the stored key + email ([LicenseManager.purchaseIdentity]) so nobody loses the copies already made.
 */
object MasterKeyPolicy {
    /** The identity device sync may pair by: none for a master key (only a typed code pairs it). */
    fun syncIdentity(identity: Pair<String, String>?, masterKey: Boolean): Pair<String, String>? =
        if (masterKey) null else identity

    /**
     * The master mark after an ACTIVE answer. A different key takes the answer as it is; the same key keeps a mark it already had,
     * so a Worker rolled back to one that stops saying `master` cannot turn pairing by email back on.
     */
    fun markAfterActive(wasMaster: Boolean, saysMaster: Boolean, sameKey: Boolean): Boolean =
        if (sameKey) wasMaster || saysMaster else saysMaster
}
