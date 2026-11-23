package iad1tya.echo.music.localmedia

/**
 * Row 777: the scanner writes the exported lists in the same order as AudioExportService, newest first, so the
 * `take(ExportedEntriesCap)` that follows drops the OLDEST entries. Ids already listed keep their place; ids seen
 * for the first time go in front.
 */
internal fun newestFirstIds(existing: List<String>, scanned: List<String>): List<String> {
    val known = existing.toSet()
    return (scanned.filterNot(known::contains) + existing).distinct()
}

/** [newestFirstIds] for the `id → uri` map; a scanned id that is already listed keeps its place, with the scanned value. */
internal fun newestFirstUris(existing: Map<String, String>, scanned: Map<String, String>): Map<String, String> {
    val ordered = linkedMapOf<String, String>()
    scanned.forEach { (id, uri) -> if (id !in existing) ordered[id] = uri }
    existing.forEach { (id, uri) -> ordered[id] = scanned[id] ?: uri }
    return ordered
}
