package iad1tya.echo.music.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import iad1tya.echo.music.constants.AccountEmailKey
import iad1tya.echo.music.constants.ImportFollowsAwaitingConfirmKey
import iad1tya.echo.music.constants.ImportFollowsLocalOnlyKey
import java.security.MessageDigest
import timber.log.Timber

/**
 * Row 808 (owner 2026-11-24, H13) — the import lists «esperan confirmación» ([ImportFollowsAwaitingConfirmKey]) and «Solo en Aura»
 * ([ImportFollowsLocalOnlyKey]) belong to the Google account they were answered for, not to the phone.
 *
 * The two keys keep their names and stay the lists of the account signed in now, so every reader and writer works as before (registry
 * #646: renaming them would release every held artist). When another account is found signed in, [adopt] puts the outgoing account's
 * lists aside under its own tag and brings the incoming one's back. The first time ever, what is there is the current account's.
 *
 * Union, so a switch never subscribes anybody: every artist held for the outgoing account stays held for the incoming one — kept «Solo
 * en Aura» if that account chose so, otherwise waiting for ITS confirmation ([incoming]). An account seen for the first time therefore
 * waits for its own answer on everything the other one had held.
 *
 * The account is told apart by a hash of its email ([tagOf]); the email itself is never stored here nor logged (AGENTS rule 4).
 */
object ImportFollowsAccounts {
    /** The tag of the account the two lists belong to right now. */
    val OwnerKey = stringPreferencesKey("import_follows_account")

    fun awaitingKeyOf(tag: String) = stringSetPreferencesKey("import_follows_awaiting_confirm@$tag")

    fun localOnlyKeyOf(tag: String) = stringSetPreferencesKey("import_follows_local_only@$tag")

    /** A short hash of the account's email (same shape as AccountPush's tag): only to tell one account from another. Pure. */
    fun tagOf(email: String?): String? {
        val normalized = email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
    }

    data class Lists(val awaiting: Set<String>, val localOnly: Set<String>)

    /** Pure: the lists of the incoming account, from the outgoing account's and what was put aside for it before (null = never seen). */
    fun incoming(outgoing: Lists, stored: Lists?): Lists {
        val kept = stored ?: Lists(emptySet(), emptySet())
        return Lists(
            awaiting = kept.awaiting + (outgoing.awaiting + outgoing.localOnly - kept.localOnly),
            localOnly = kept.localOnly,
        )
    }

    /** Make the two lists those of the account signed in now. Nothing happens with no account signed in or no email known. */
    suspend fun adopt(context: Context) {
        runCatching {
            context.dataStore.edit { p ->
                val tag = tagOf(p[AccountEmailKey]) ?: return@edit
                val owner = p[OwnerKey]
                if (owner == tag) return@edit
                if (owner == null) {
                    // One-time: the lists that exist were answered on the account signed in now.
                    p[OwnerKey] = tag
                    return@edit
                }
                val outgoing = Lists(p[ImportFollowsAwaitingConfirmKey].orEmpty(), p[ImportFollowsLocalOnlyKey].orEmpty())
                val seen = p.contains(awaitingKeyOf(tag)) || p.contains(localOnlyKeyOf(tag))
                val stored = if (seen) Lists(p[awaitingKeyOf(tag)].orEmpty(), p[localOnlyKeyOf(tag)].orEmpty()) else null
                val next = incoming(outgoing, stored)
                p[awaitingKeyOf(owner)] = outgoing.awaiting
                p[localOnlyKeyOf(owner)] = outgoing.localOnly
                p.remove(awaitingKeyOf(tag))
                p.remove(localOnlyKeyOf(tag))
                p[ImportFollowsAwaitingConfirmKey] = next.awaiting
                p[ImportFollowsLocalOnlyKey] = next.localOnly
                p[OwnerKey] = tag
                Timber.i("IMPORT_FOLLOWS account switched seen=%b awaiting=%d localOnly=%d", seen, next.awaiting.size, next.localOnly.size)
            }
        }.onFailure { Timber.w("IMPORT_FOLLOWS account lists not switched: %s", it.javaClass.simpleName) }
    }
}
