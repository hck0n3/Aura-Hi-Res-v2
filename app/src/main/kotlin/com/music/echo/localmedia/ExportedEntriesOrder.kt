package iad1tya.echo.music.localmedia

/**
 * Row 777: the scanner shares the exported lists with AudioExportService, which keeps them newest first. A device
 * video is rediscovered on every scan, but a real export's id and SAF uri exist only in these lists, so the scanner
 * never evicts an entry that is already listed: ids it sees for the first time go BEHIND the existing ones, and the
 * `take(ExportedEntriesCap)` that follows drops those first. A second identical scan then writes the same lists.
 */
internal fun withScannedIdsAppended(existing: List<String>, scanned: List<String>): List<String> =
    (existing + scanned).distinct()

/** [withScannedIdsAppended] for the `id → uri` map; a scanned id that is already listed keeps its place, with the scanned value. */
internal fun withScannedUrisAppended(existing: Map<String, String>, scanned: Map<String, String>): Map<String, String> {
    val merged = LinkedHashMap(existing)
    merged.putAll(scanned)
    return merged
}
