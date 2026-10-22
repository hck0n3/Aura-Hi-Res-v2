package iad1tya.echo.music.utils

import iad1tya.echo.music.db.MusicDatabase
import iad1tya.echo.music.db.entities.ArtistEntity

/**
 * The ONE follow/unfollow tap for an artist, shared by the artist page ([iad1tya.echo.music.viewmodels.ArtistViewModel])
 * and the artist menus, so they can never disagree about what the button means.
 *
 * "Subscribed" is read under EVERY spelling of the channel id ([ArtistSyncPolicy.aliasIds], the same set
 * `DatabaseDao.isAnyArtistFollowed` uses for the label). So:
 *  - subscribed under any spelling -> unfollow EVERY row that carries the follow (the button cannot get stuck
 *    on "Suscrito"), each through `ArtistEntity.toggleLike`, i.e. the live `subscribeChannel(id, false)` at the
 *    tap, the `unfollowedByUserAt` marker as the durable instruction, `confirmArtistUnsubscribed` once the
 *    account accepted it, and the `AccountPush` retry when it did not;
 *  - otherwise -> follow the row of this id, or insert one built from [fallback] (the page / menu item).
 *
 * Suspending only for the one Room read; the writes go through [MusicDatabase.transaction]. The caller
 * should launch it on a scope that outlives its screen (a menu closes right after the tap).
 */
suspend fun MusicDatabase.toggleArtistFollow(artistId: String, fallback: ArtistEntity?) {
    val db = this
    val rows = getArtistEntitiesByIds(ArtistSyncPolicy.aliasIds(artistId))
    val followed = rows.filter { it.followedByUserAt != null }
    db.transaction {
        if (followed.isNotEmpty()) {
            followed.forEach { update(it.toggleLike(db::confirmArtistUnsubscribed)) }
        } else {
            val own = rows.firstOrNull { it.id == artistId }
            if (own != null) {
                update(own.toggleLike(db::confirmArtistUnsubscribed))
            } else if (fallback != null) {
                insert(fallback.toggleLike(db::confirmArtistUnsubscribed))
            }
        }
    }
}
