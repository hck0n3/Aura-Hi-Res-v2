package iad1tya.echo.music.utils

import iad1tya.echo.music.db.entities.ArtistEntity
import java.time.LocalDateTime

/**
 * The single source of truth for "may Aura change this artist's subscription on the user's real
 * YouTube account, and in which direction?".
 *
 * It exists because the rule used to live only inside SQL, and one backfill statement quietly broke
 * it: the uploader inferred an unsubscribe from an ABSENCE (`followedByUserAt IS NULL AND
 * ytmSyncedAt IS NOT NULL`), while a separate statement stamped `ytmSyncedAt` on every artist the
 * account is subscribed to. Any row whose local follow marker had been lost — by a logout, a "borrar
 * contenido sincronizado", a restore from a pre-v40 backup, a half-written down-sync, an account
 * switch — was therefore rewritten into that exact shape and then unsubscribed for real.
 *
 * ### The invariant this file encodes
 * An unsubscribe requires POSITIVE evidence of user intent ([ArtistEntity.unfollowedByUserAt]), never
 * the absence of a local row or of a local marker. Absence of evidence is what every local wipe, every
 * migration and every interrupted sync produces; it must be inert.
 *
 * The DAO queries mirror these predicates so the database does the filtering, and the uploader
 * re-checks them in Kotlin before every destructive call, so a regression in either layer alone
 * cannot reach the network.
 */
object ArtistSyncPolicy {

    /** Why a row is NOT eligible for an upstream unsubscribe. Used for logging a refusal. */
    enum class UnsubscribeRefusal {
        LOCAL_ARTIST,

        /** No positive record that the user unfollowed anything. The default, and the safe one. */
        NO_RECORDED_INTENT,

        /** The user re-followed after unfollowing; the follow is newer and wins. */
        REFOLLOWED,

        /** We never had a subscription for this artist, so there is nothing to remove. */
        NOT_SUBSCRIBED,

        /**
         * The unfollow was recorded so long ago that it is no longer a plausible pending instruction.
         * See [PENDING_UNFOLLOW_TTL_DAYS].
         */
        STALE_INTENT,
    }

    /**
     * How long a queued unfollow stays actionable.
     *
     * [ArtistEntity.localToggleLike] stamps `unfollowedByUserAt` on every non-local artist the user
     * unfollows, because after `MIGRATION_39_40` there is no way to tell a real subscription from an
     * incidental bookmark — both read (null, null, null) — and refusing to record the intent is what
     * made an unfollow evaporate. The cost of recording it more widely is a marker that can sit on a
     * row the account is NOT subscribed to. Such a row is inert (`ytmSyncedAt` is null, so
     * [refuseUnsubscribe] returns NOT_SUBSCRIBED) — but not forever: if the user later subscribes to
     * that same artist DIRECTLY on YouTube, the read-back stamps `ytmSyncedAt` and a year-old marker
     * would suddenly authorise undoing a subscription they had just made.
     *
     * A pending unfollow is repaired within one sync pass in every normal case, so anything this old
     * is not a pending anything. 30 days is generous enough to cover a long spell offline.
     *
     * This is the BACKSTOP, not the fix. It only bounds the exposure to 30 days, and inside that
     * window the scenario above really did reverse the user's own re-subscription. The fix is
     * [liveCallHonouredTheUnfollow]: an unfollow the live call actually delivered retires its marker
     * immediately, so it is never around to be re-armed. The TTL still matters for the markers that
     * remain — the ones whose live call failed and which nothing ever repaired.
     */
    const val PENDING_UNFOLLOW_TTL_DAYS = 30L

    /** What the uploader must do with a queued unsubscribe whose `channelId` it cannot use. */
    enum class MissingChannelAction {
        /** Ask YouTube for the channel behind this artist's OWN id, persist it, and carry on. */
        RESOLVE_FROM_ID,

        /** There is provably no channel to unsubscribe from; retiring the intent is honest. */
        RETIRE_INTENT,

        /** Resolution failed THIS pass. Keep the intent queued and try again next time. */
        KEEP_QUEUED,
    }

    /**
     * A blank `channelId` is NOT the same thing as "there is nothing to unsubscribe from", and treating
     * it as such destroyed real unfollows. `channelId` is routinely null on rows that ARE subscribed:
     * `LibraryUploadSync.fetchRemoteSubscriptionIds` stamps `ytmSyncedAt` straight from the account's
     * id list without ever writing one, and the artist down-sync leaves it null whenever its
     * `getChannelId()` call failed. Clearing the row on that basis dropped `ytmSyncedAt` AND the
     * `unfollowedByUserAt` intent without contacting YouTube at all — silent and permanent.
     *
     * The subscribe path already resolves the id before acting; this makes the destructive path do the
     * same, and keeps the intent when it cannot.
     *
     * @param resolutionAttempted false before we have asked YouTube, true after a failed attempt.
     *
     * NOTE: resolution is always by the artist's OWN id. Never by name — a name-matched channel
     * belongs to whoever happens to share the name, and persisting one would aim every future
     * subscribe/unsubscribe at a stranger.
     */
    fun onMissingChannelId(
        artist: ArtistEntity,
        resolutionAttempted: Boolean,
    ): MissingChannelAction = when {
        !resolutionAttempted -> MissingChannelAction.RESOLVE_FROM_ID
        // A privately-owned (uploaded-library) artist has no public channel and never will.
        artist.isPrivatelyOwnedArtist -> MissingChannelAction.RETIRE_INTENT
        else -> MissingChannelAction.KEEP_QUEUED
    }

    /**
     * Whether [ArtistEntity.toggleLike] must call the account RIGHT NOW. It depends on nothing except
     * the artist having a channel at all.
     *
     * The owner's standing requirement is that a follow/unfollow he just tapped reaches his YouTube
     * account immediately, and unconditionally: *"si yo me suscribo, me desuscribo, o doy like o quito
     * like, eso se tiene que sincronizar con mi cuenta de YouTube"*. A tap is unambiguous intent aimed
     * at the account signed in at that instant, so none of the stored-marker reasoning in this file
     * applies to it — that reasoning exists to stop the BULK reconciler inferring intent from rows it
     * did not watch being written.
     *
     * A guard was briefly added here that skipped the call when the row carried no follow markers. It
     * silenced every unfollow made in the window between `MIGRATION_39_40` (which leaves all three
     * columns NULL on every row) and the first artist down-sync — which is exactly the window in which
     * a customer opens the app after updating.
     */
    fun mustCallAccountLive(artist: ArtistEntity): Boolean = !artist.isLocal

    /**
     * True when — and only when — removing this artist's subscription from the account is an action the
     * user actually asked for.
     */
    fun mayUnsubscribe(artist: ArtistEntity, now: LocalDateTime = LocalDateTime.now()): Boolean =
        refuseUnsubscribe(artist, now) == null

    /** Null when the unsubscribe is authorised; otherwise the reason it is refused. */
    fun refuseUnsubscribe(
        artist: ArtistEntity,
        now: LocalDateTime = LocalDateTime.now(),
    ): UnsubscribeRefusal? = when {
        artist.isLocal -> UnsubscribeRefusal.LOCAL_ARTIST
        artist.unfollowedByUserAt == null -> UnsubscribeRefusal.NO_RECORDED_INTENT
        artist.followedByUserAt != null -> UnsubscribeRefusal.REFOLLOWED
        artist.ytmSyncedAt == null -> UnsubscribeRefusal.NOT_SUBSCRIBED
        // The DAO predicate cannot express this (it has no clock), so the policy is the authority —
        // exactly the split the three-layer design was built for. `flushPendingUnsubscribes` retires
        // rows refused for this reason so they stop occupying the queue and the pending count.
        isStale(artist, now) -> UnsubscribeRefusal.STALE_INTENT
        else -> null
    }

    /** True when a queued unfollow has outlived [PENDING_UNFOLLOW_TTL_DAYS]. */
    fun isStale(artist: ArtistEntity, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val queuedAt = artist.unfollowedByUserAt ?: return false
        // A marker dated in the FUTURE (clock rolled back) is not stale — treat drift as fresh rather
        // than silently dropping a real instruction.
        return queuedAt.isBefore(now.minusDays(PENDING_UNFOLLOW_TTL_DAYS))
    }

    /**
     * True when this artist is a deliberate follow the account does not have a subscription for yet.
     * Subscribing is the non-destructive direction, but it still writes to someone's account, so it is
     * gated on the same deliberate-follow marker — never on `bookmarkedAt`, which the REMOVED
     * `DatabaseDao.followArtistsWithContent()` bulk statement used to stamp on every artist that
     * merely had a song in the library ("me aparecen muchas suscripciones de cantantes que no sigo";
     * legacy rows like those still exist, registry #154).
     */
    fun maySubscribe(artist: ArtistEntity): Boolean =
        !artist.isLocal &&
            artist.followedByUserAt != null &&
            artist.ytmSyncedAt == null &&
            artist.unfollowedByUserAt == null

    /**
     * What one row must look like after we have READ it back from the account's real subscription list
     * (`FEmusic_library_corpus_artists`). Mirrors `DatabaseDao.markArtistsSubscribedOnYtm`.
     *
     * Two things make this safe where the old version was not:
     *  1. It never reads `bookmarkedAt`. The uploader must not care whether an artist happens to have a
     *     song in the library.
     *  2. It can never MANUFACTURE the pending-unsubscribe shape. YouTube demonstrably has this
     *     subscription, so `followedByUserAt` is stamped alongside `ytmSyncedAt` — unless the row
     *     already carries a real [ArtistEntity.unfollowedByUserAt], in which case the queued unfollow
     *     is preserved so an unsubscribe that never reached YouTube (offline, expired cookie) is still
     *     retried instead of being silently lost.
     *  3. Row 716: [aliasPendingUnfollow] — another spelling of the channel ([aliasIds]) carries the user's
     *     unfollow ([hasPendingUnfollow]) — stops it from following this spelling: that would resurrect
     *     «Suscrito» over an unfollow that has not reached the account yet.
     */
    fun afterRemoteSubscriptionSeen(
        artist: ArtistEntity,
        now: LocalDateTime,
        aliasPendingUnfollow: Boolean = false,
    ): ArtistEntity =
        if (artist.unfollowedByUserAt != null) {
            artist.copy(ytmSyncedAt = now)
        } else if (artist.followedByUserAt == null && aliasPendingUnfollow) {
            artist.copy(ytmSyncedAt = now)
        } else {
            artist.copy(followedByUserAt = artist.followedByUserAt ?: now, ytmSyncedAt = now)
        }

    /** Row 716: the user unfollowed this row and has not followed it again (the marker the account calls honour). */
    fun hasPendingUnfollow(artist: ArtistEntity): Boolean =
        artist.unfollowedByUserAt != null && artist.followedByUserAt == null

    /** Row 716: another spelling of [id]'s channel, among the stored [rows] (by id), carries the user's pending unfollow. */
    fun aliasHasPendingUnfollow(id: String, rows: Map<String, ArtistEntity>): Boolean =
        aliasIds(id).any { it != id && rows[it]?.let(::hasPendingUnfollow) == true }

    /**
     * What one row must look like once an unsubscribe has been confirmed (or is provably impossible,
     * e.g. there is no channel to unsubscribe from). The intent has been honoured, so the marker goes
     * away with it — otherwise a later re-follow would be ambiguous. Mirrors
     * `DatabaseDao.clearArtistsSyncedToYtm`.
     */
    fun afterUnsubscribed(artist: ArtistEntity): ArtistEntity =
        artist.copy(ytmSyncedAt = null, unfollowedByUserAt = null)

    /**
     * Whether the LIVE call [ArtistEntity.toggleLike] just made has HONOURED the queued unfollow, so
     * the intent marker must be retired ([afterUnsubscribed], mirrored by
     * `DatabaseDao.confirmArtistUnsubscribed`).
     *
     * ### The bug this closes
     * `localToggleLike` stamps `unfollowedByUserAt` on EVERY non-local artist the user unfollows —
     * necessarily so, because after `MIGRATION_39_40` a real subscription and an incidental bookmark
     * both read (null, null, null), and refusing to record the intent is what made an unfollow
     * evaporate. But `toggleLike` was fire-and-forget: it sent `subscribeChannel(id, false)` and never
     * wrote anything back. So after a SUCCESSFUL live unsubscribe the row kept the marker with
     * `ytmSyncedAt` still null — inert, and also UNREACHABLE by every cleanup, because
     * `artistsPendingUnsubscribe` requires `ytmSyncedAt IS NOT NULL` and so the STALE_INTENT
     * retirement in `LibraryUploadSync.flushPendingUnsubscribes` never sees it either.
     *
     * A marker like that is a standing order with nobody left to cancel it. If the user changed their
     * mind within [PENDING_UNFOLLOW_TTL_DAYS] and re-subscribed to that artist DIRECTLY on YouTube,
     * the read-back stamped `ytmSyncedAt` while [afterRemoteSubscriptionSeen] deliberately preserved
     * the marker — producing a genuine-looking pending UNSUBSCRIBE — and the flush in the SAME pass
     * reversed the subscription the user had just made. An honoured instruction has to stop being an
     * instruction.
     *
     * ### Why success is the only usable discriminator
     * There is no way to tell "the live unsubscribe landed and the user later changed their mind" from
     * "the live unsubscribe never landed and is still owed" by looking at the stored row: post-
     * migration both are (marker set, `ytmSyncedAt` null) and both end up subscribed on the account.
     * Only the call site knows which happened, so only the call site can retire the marker.
     *
     * ### The FAILURE case is deliberately the opposite
     * When the call did not land (offline, expired cookie, unresolved channel) the marker MUST
     * survive: it is the entire retry mechanism. `flushPendingUnsubscribes` picks the row up once the
     * subscription read-back confirms the account really does hold the subscription, and the TTL
     * eventually retires it if nothing ever does. Returning true here on a failure would silently
     * discard a real unfollow — the blocker this whole file exists to prevent, in miniature.
     *
     * @param subscribing what the live call asked for; true = follow, false = unfollow.
     * @param confirmedByAccount whether YouTube accepted it.
     */
    fun liveCallHonouredTheUnfollow(subscribing: Boolean, confirmedByAccount: Boolean): Boolean =
        !subscribing && confirmedByAccount

    /**
     * What a purely LOCAL reset must leave behind: nothing the reconciler can act on, in either
     * direction. Mirrors `DatabaseDao.clearAllBookmarkedArtists` (logout / "borrar contenido
     * sincronizado" / account switch). The whole point is that wiping local state is not a statement
     * about the user's YouTube account.
     */
    fun afterLocalReset(artist: ArtistEntity): ArtistEntity =
        artist.copy(bookmarkedAt = null, followedByUserAt = null, ytmSyncedAt = null, unfollowedByUserAt = null)

    /**
     * What every row must look like the moment the YouTube ACCOUNT is detached — "cerrar sesión
     * (mantener datos)", "cerrar sesión y borrar datos", an account switch, a cookie we can no longer
     * parse, or a library restored from a backup taken under somebody else's session.
     *
     * ### Why this exists as its own thing, separate from [afterLocalReset]
     * `afterLocalReset` is the "borrar contenido sincronizado" shape: it throws the LIBRARY away too.
     * "Mantener datos" explicitly promises the opposite, so it cannot be used there — and until this
     * function existed, the keep-data logout cleared nothing at all. That left rows in the pending-
     * UNSUBSCRIBE shape (`unfollowedByUserAt` + `ytmSyncedAt`, no `followedByUserAt`) belonging to
     * account A sitting in the database while the user signed into account B. The next upload pass
     * read B's subscription list and fired `subscribeChannel(id, false)` at B for every artist the
     * user had ever unfollowed on A: an irreversible, invisible mass unsubscribe of an account that
     * was never asked about.
     *
     * ### Which markers are account-scoped, and which are not
     *  - [ArtistEntity.ytmSyncedAt] is a claim about ONE specific account ("that account has this
     *    subscription"). The moment the account goes away the claim is unverified, and against any
     *    other account it is simply false. CLEARED.
     *  - [ArtistEntity.unfollowedByUserAt] is an instruction to remove a subscription that account A
     *    holds. It can only ever be honoured against A. Carried into account B it is not merely
     *    useless, it is the destructive half of the bug. CLEARED.
     *  - [ArtistEntity.followedByUserAt] is only PARTLY a statement about the USER. A tap, an
     *    importer, the onboarding picker and a Spotify/Tidal/Deezer migration all write it as "I
     *    follow this artist" — but `DatabaseDao.markArtistsSubscribedOnYtm` ALSO writes it wholesale
     *    from the ATTACHED ACCOUNT's remote subscription list, so on any established install most of
     *    these markers are a copy of that one account's follows. It is what "tus artistas" shows and
     *    what `bookmarkedAt` is deliberately NOT. KEPT — see below, and read the safety argument
     *    there carefully, because it does NOT rest on the marker being account-neutral.
     *  - [ArtistEntity.bookmarkedAt] is the library itself. KEPT, because "mantener datos" says so.
     *
     * ### Why `followedByUserAt` is kept rather than cleared
     * Clearing it looks tidier and is wrong. A deliberate follow whose live `subscribeChannel(true)`
     * never landed (offline, expired cookie) — or one made by an importer, the onboarding picker or a
     * Spotify/Tidal migration — sits as a pending SUBSCRIBE. YouTube does NOT have it, so the
     * subscription read-back on the next login cannot re-stamp it: `markArtistsSubscribedOnYtm` only
     * touches ids the account actually returns. Clearing the marker would therefore silently destroy
     * exactly the follows that still needed pushing, and would do it permanently — the row would
     * become indistinguishable from an incidental bookmark.
     *
     * ### What keeping it actually costs, and what pays for it
     * Re-logging into the SAME account is a no-op: the uploader reads that account's real list first
     * and skips everything already there. Against a DIFFERENT account it is not a no-op at all — every
     * kept marker is a pending SUBSCRIBE that B does not have, so a sync pass would push A's entire
     * follow list onto B, 150 `subscribeChannel(id, true)` calls a pass, invisible until B's owner
     * opens YouTube. Only the non-destructive direction, but still somebody else's account.
     *
     * The ONE thing that stops it is the library-upload switch, and this comment used to claim the
     * switch was "the upload switch the user has to turn on themselves". That was false twice over:
     * `App.applyLibraryUploadOptInV1` turns it ON for a fresh install without asking, and
     * `forgetAccount` used to leave it ON across a logout, so it was never re-consented. Both are now
     * true as stated — `forgetAccount` writes `LibraryUploadOptIn.onAccountDetached()` (an explicit
     * false) in the same edit that drops the session, so the switch is per-ACCOUNT consent and the
     * next account's owner must grant it themselves.
     *
     * If you are about to make the upload switch survive a logout, or to "simplify" the write in
     * `forgetAccount`, you are removing the only thing that makes keeping `followedByUserAt` safe.
     * Clear the marker instead, and accept losing the unpushed follows.
     *
     * ### What is deliberately given up
     * An unfollow that never reached YouTube is dropped by this. That is the intended trade: losing a
     * queued unfollow costs one tap to redo (and the live path fires it again immediately), whereas
     * honouring it against the wrong account is irreversible and invisible until the user opens
     * YouTube. When the two directions conflict, the safe one wins.
     */
    fun afterAccountDetached(artist: ArtistEntity): ArtistEntity =
        artist.copy(ytmSyncedAt = null, unfollowedByUserAt = null)

    // ---- Reading the account's subscription back when an artist page opens -------------------------
    //
    // Owner report 2026-10-20: «los cantantes a los que ya estoy suscrito, en algunos al meterme me
    // aparece que no estoy suscrito». The artist screens show ONLY the local `followedByUserAt`, which
    // is stamped by the library sync (a list read by exact id) and by taps. Anything that list did not
    // stamp for the id the page was opened under — a subscription made in the YouTube app after the last
    // sync, a truncated walk, the same artist stored under another id — read "Suscribirse". What is left
    // of that fix is the ids that name the same artist; writing the page's answer back to the row was
    // removed (row 700: it made follows the owner never did).

    /** Prefix YouTube Music puts in front of a channel id for the entries of the user's LIBRARY artists. */
    const val LIBRARY_ARTIST_PREFIX = "MPLA"

    /**
     * Every id under which the SAME channel can be stored or opened: `UC…` and `MPLAUC…`. Anything that
     * is not a channel id (local `LA…` rows, uploads) has no alias and comes back alone.
     */
    fun aliasIds(id: String): List<String> {
        val bare = id.removePrefix(LIBRARY_ARTIST_PREFIX)
        if (!bare.startsWith("UC")) return listOf(id)
        return listOf(id, bare, LIBRARY_ARTIST_PREFIX + bare).distinct()
    }

    /**
     * One key per CHANNEL: `UC…` and `MPLAUC…` collapse into the bare `UC…`; anything that is not a
     * channel id keys on itself. The Library "Artistas" list de-duplicates on it, so a channel stored
     * under both spellings is one row and one unit of the «N artistas» counter, exactly like the artist
     * screen's [aliasIds]-based `isSubscribed` treats it as one subscription.
     */
    fun channelKey(id: String): String {
        val bare = id.removePrefix(LIBRARY_ARTIST_PREFIX)
        return if (bare.startsWith("UC")) bare else id
    }

    /**
     * Row 716: the key under which a row's account calls are ordered (`ArtistEntity.toggleLike`). Every spelling of
     * a channel (`UC…`, `MPLAUC…`) shares the bare `UC…` of its OWN id — never a divergent `channelId` — so all the
     * alias rows of one channel wait in ONE queue. Only a row whose id is not a channel falls back to its channelId.
     */
    fun accountCallKey(id: String, channelId: String?): String {
        val own = channelKey(id)
        if (own.startsWith("UC")) return own
        return channelKey(channelId?.takeIf { it.isNotBlank() } ?: id)
    }

    /** Which stored rows one follow tap toggles, and whether the row of the tapped id must be created. */
    data class FollowTapPlan(val toggle: List<ArtistEntity>, val insertFallback: Boolean)

    /**
     * Row 716: one follow tap over the stored [rows] of every spelling of [artistId] ([aliasIds]). Followed under
     * any spelling -> unfollow every row that carries the follow. Otherwise -> follow EVERY stored spelling, so no
     * alias row keeps an old unfollow marker next to the new follow (the next upload pass would honour that marker
     * and unsubscribe the account while Aura reads «Suscrito»), and create the tapped id's row when it is missing.
     * Null when [shownFollowed] is given and the stored state is no longer the one the button showed: the tap is
     * dropped instead of toggling the other way.
     *
     * The tapped id's row goes LAST: the account-call queue sends the newest intent of a channel, so the call goes
     * out with that row's channel id (the page's), as it did when only that row was toggled.
     */
    fun followTapPlan(
        rows: List<ArtistEntity>,
        artistId: String,
        shownFollowed: Boolean?,
        hasFallback: Boolean,
    ): FollowTapPlan? {
        val followed = rows.filter { it.followedByUserAt != null }
        if (shownFollowed != null && followed.isNotEmpty() != shownFollowed) return null
        if (followed.isNotEmpty()) return FollowTapPlan(followed.sortedBy { it.id == artistId }, insertFallback = false)
        return FollowTapPlan(
            rows.sortedBy { it.id == artistId },
            insertFallback = hasFallback && rows.none { it.id == artistId },
        )
    }

    /**
     * A follow stamped from the account must not outlive the subscription. Grace before a row whose
     * `ytmSyncedAt` is this recent may be judged "gone": the read-back that stamped it and the list we
     * compare against can disagree for a moment (a subscription made seconds ago).
     */
    const val GONE_FROM_ACCOUNT_GRACE_MINUTES = 15L

    /**
     * Owner 2026-10-20: the Library «Artistas» list must hold EXCLUSIVELY what the account is subscribed
     * to. The list reads `followedByUserAt`, and the sync only ever stamped it towards "followed", so a
     * subscription removed on YouTube itself stayed in the list for ever. This is the pure decision for
     * the opposite direction: which stored follows is the account's own, COMPLETE list proof of "no
     * longer subscribed". It only ever clears LOCAL state; it never talks to the account.
     *
     * Every condition below is a way this could clear a real follow, so each one refuses:
     *  - [readWasComplete] false (a paginated walk cut short — request cap, failed page) or an EMPTY
     *    [remoteIds] (an expired cookie also answers with an empty page): absence proves nothing.
     *  - local / privately-owned artists: they have no public channel to be listed under.
     *  - a row that never came from the account (`ytmSyncedAt == null`): a follow the user made that
     *    the account has not seen yet (pending SUBSCRIBE, guest follows, onboarding picks) is not stale.
     *  - a row with a queued unfollow: that is the unsubscribe path's business ([mayUnsubscribe]).
     *  - a row stamped inside the grace window, or after the read began.
     *  - a row whose id OR channel id names a channel the account did list (UC / MPLAUC aliases).
     *
     * @return the ids of the rows to clear (follow, bookmark and sync stamp).
     */
    fun followsGoneFromAccount(
        candidates: List<ArtistEntity>,
        remoteIds: Set<String>,
        readWasComplete: Boolean,
        readStartedAt: LocalDateTime,
    ): List<String> {
        if (!readWasComplete || remoteIds.isEmpty()) return emptyList()
        val remoteKeys = remoteIds.mapTo(HashSet()) { channelKey(it) }
        val cutoff = readStartedAt.minusMinutes(GONE_FROM_ACCOUNT_GRACE_MINUTES)
        return candidates.asSequence()
            .filter { !it.isLocal && !it.isPrivatelyOwnedArtist }
            .filter { it.followedByUserAt != null && it.unfollowedByUserAt == null }
            .filter { row -> row.ytmSyncedAt?.isBefore(cutoff) == true }
            .filter { row -> channelKey(row.id) !in remoteKeys }
            .filter { row -> row.channelId?.let { channelKey(it) in remoteKeys } != true }
            .map { it.id }
            .toList()
    }

    // TOMBSTONE (row 700): `healFromRemoteSubscription` is gone with its only caller, the artist page's
    // read-back (row 487). A follow is written by a tap or by the library sync's read-back, never by a page.

    /**
     * Cuántos rechazos SEGUIDOS de YouTube bastan para abandonar la subida de suscripciones de esta
     * pasada.
     *
     * Hallado en el log del dueño (`share_all_diagnostics_3.txt`, 2026-09-16): más de ochenta líneas
     * `Could not subscribe to <artista>`, una detrás de otra, todas con el mismo 400 de YouTube —
     * *"Tienes demasiadas suscripciones en comparación con tu recuento de suscriptores"*. Es un límite
     * ANTI-SPAM de la CUENTA, no un problema de cada artista: cuando salta, el artista número ochenta y
     * uno va a ser rechazado exactamente igual que el primero.
     *
     * Y nada lo paraba. Solo `markArtistsSyncedToYtm` (que corre en ÉXITO) saca a un artista de la
     * cola, así que cada pasada volvía a intentar los mismos artistas condenados, gastaba su
     * presupuesto entero en escrituras rechazadas y encadenaba otra pasada — hasta 20 saltos por 400
     * escrituras. Eso es machacar la cuenta de alguien para nada, y en silencio.
     *
     * TRES y no uno: un fallo suelto es un artista raro (un canal borrado, un id que no resuelve) y
     * saltarlo es lo correcto; tres seguidos ya no es casualidad, es que la cuenta no acepta
     * suscripciones ahora mismo. Tres también deja que una pasada normal con un hueco por medio siga
     * adelante, porque el contador se pone a cero con cada éxito.
     */
    const val SUBSCRIBE_FAILURE_STREAK_LIMIT = 3

    /**
     * ¿Abandonar la subida de suscripciones tras [consecutiveFailures] rechazos seguidos?
     *
     * Abandonar NO pierde nada: los artistas siguen pendientes y la próxima pasada los reintenta. Lo
     * único que se deja de hacer es seguir pidiendo cientos de veces algo que la cuenta acaba de
     * rechazar tres veces seguidas.
     */
    fun shouldAbandonSubscribePass(consecutiveFailures: Int): Boolean =
        consecutiveFailures >= SUBSCRIBE_FAILURE_STREAK_LIMIT
}
