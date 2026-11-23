package iad1tya.echo.music.utils

import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * The ONE follow/unfollow tap for an artist, shared by the artist page ([iad1tya.echo.music.viewmodels.ArtistViewModel])
 * and the artist menus, so they can never disagree about what the button means.
 *
 * "Subscribed" is read under EVERY spelling of the channel id ([ArtistSyncPolicy.aliasIds], the same set
 * `DatabaseDao.isAnyArtistFollowed` uses for the label). So:
 *  - subscribed under any spelling -> unfollow EVERY row that carries the follow (the button cannot get stuck on
 *    "Suscrito"), each through `ArtistEntity.toggleLike`, i.e. the live `subscribeChannel(id, false)` at the
 *    tap, the `unfollowedByUserAt` marker as the durable instruction, `confirmArtistUnsubscribed` once the
 *    account accepted it, and the `AccountPush` retry when it did not;
 *  - otherwise -> follow EVERY stored spelling too (row 716: one channel, one state — a re-follow that left an
 *    alias row with its unfollow marker let the next upload pass unsubscribe the account while Aura showed
 *    «Suscrito»), plus the row of this id built from [fallback] when it does not exist yet.
 *
 * Row 716: it SUSPENDS until the write is committed (the old `transaction {}` only queued it), and reads the
 * rows inside the same transaction, so a caller's one-tap-at-a-time guard really covers the write and a quick
 * second tap sees the first one. With [shownFollowed], the tap applies only if the stored state is still the one
 * the button showed. Returns whether it toggled. A database failure is logged (class only) and reads as "not
 * toggled", as the old fire-and-forget `transaction {}` swallowed it — callers launch this unsupervised.
 */
suspend fun MusicDatabase.toggleArtistFollow(
    artistId: String,
    fallback: ArtistEntity?,
    shownFollowed: Boolean? = null,
): Boolean {
    val db = this
    var toggled = false
    try {
        db.withTransaction {
            val rows = artistEntitiesByIdsInTransaction(ArtistSyncPolicy.aliasIds(artistId))
            val plan = ArtistSyncPolicy.followTapPlan(rows, artistId, shownFollowed, hasFallback = fallback != null)
            if (plan == null) {
                // Row 716: a tap dropped because the stored state is no longer what the button showed still reaches
                // app.log (no names or ids, regla 4).
                Timber.i("ARTIST_FOLLOW_TAP intent=%s outcome=ignored", if (shownFollowed == true) "unfollow" else "follow")
                return@withTransaction
            }
            plan.toggle.forEach { update(it.toggleLike(db::confirmArtistUnsubscribed)) }
            if (plan.insertFallback && fallback != null) insert(fallback.toggleLike(db::confirmArtistUnsubscribed))
            toggled = true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w("ARTIST_FOLLOW_TAP failed: %s", e.javaClass.simpleName)
        return false
    }
    return toggled
}
