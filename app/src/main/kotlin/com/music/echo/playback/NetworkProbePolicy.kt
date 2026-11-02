package iad1tya.echo.music.playback

/**
 * Row 644 (owner 2026-11-02, from his log of 1-nov 16:21): the system said «connected» but nothing resolved (no DNS), and a song that was not
 * cached waited 61 s (`url=51978ms`) before «No hay ninguna fuente disponible» — long enough to look like the app was broken.
 *
 * `hasLiveInternetConnection` only asked for the INTERNET capability, which is true on a network that has no working internet. Android also
 * says whether it VALIDATED the network. Only when it did NOT, one short DNS probe decides; on a validated network nothing is probed (no cost,
 * no behaviour change), and any doubt keeps the old fail-open answer.
 */
internal object NetworkProbePolicy {
    /** How long the DNS probe may take before the network counts as unusable. */
    const val DNS_TIMEOUT_MS = 3_000L

    /** A probe answer is reused for this long, so a queue of unresolved songs probes once, not once per song. */
    const val CACHE_MS = 15_000L

    /** Pure. [dnsOk] is only called on a network Android did not validate. */
    fun usable(hasInternetCapability: Boolean, validated: Boolean, dnsOk: () -> Boolean): Boolean = when {
        !hasInternetCapability -> false
        validated -> true
        else -> dnsOk()
    }

    fun cacheValid(probedAtMs: Long, nowMs: Long): Boolean = nowMs - probedAtMs in 0 until CACHE_MS
}
