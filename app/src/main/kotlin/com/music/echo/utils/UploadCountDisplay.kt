package iad1tya.echo.music.utils

/**
 * Registry row 841: which account lists' last read was cut short. Kept as a string set in DataStore
 * ([iad1tya.echo.music.constants.YtmAccountReadsIncompleteKey]); every read of a list (the sync pulls and the upload
 * pass) records whether it reached the end, so the sync screen never shows a number that looks exact when it is not.
 */
object AccountReadLists {
    const val PLAYLISTS = "playlists"
    const val ARTISTS = "artists"
    const val LIKED_SONGS = "liked_songs"
    const val LIKED_ALBUMS = "liked_albums"

    /** The set after a read of [list] that did ([complete]) or did not reach the end. */
    fun afterRead(current: Set<String>, list: String, complete: Boolean): Set<String> =
        if (complete) current - list else current + list
}

/** The count line under a category of the sync screen. */
sealed interface UploadCountLine {
    /** «%1$d de %2$d sincronizadas». */
    data class Exact(val synced: Int, val total: Int) : UploadCountLine

    /** «Al menos %1$d sincronizadas · lista incompleta»: the account list was not read to its end. */
    data class AtLeast(val synced: Int) : UploadCountLine

    /** Nothing to count. */
    data object Hidden : UploadCountLine
}

/** The status at the right of a category of the sync screen. */
enum class UploadCategoryStatus { RUNNING, INCOMPLETE, NOTHING, DONE, PENDING }

/** Registry row 841: the pure derivation of what the sync screen says for one category. */
object UploadCountDisplay {
    fun line(synced: Int, total: Int, readIncomplete: Boolean): UploadCountLine = when {
        readIncomplete -> UploadCountLine.AtLeast(synced)
        total > 0 -> UploadCountLine.Exact(synced, total)
        else -> UploadCountLine.Hidden
    }

    fun status(running: Boolean, total: Int, isComplete: Boolean, readIncomplete: Boolean): UploadCategoryStatus = when {
        running -> UploadCategoryStatus.RUNNING
        readIncomplete -> UploadCategoryStatus.INCOMPLETE
        total == 0 -> UploadCategoryStatus.NOTHING
        isComplete -> UploadCategoryStatus.DONE
        else -> UploadCategoryStatus.PENDING
    }
}
