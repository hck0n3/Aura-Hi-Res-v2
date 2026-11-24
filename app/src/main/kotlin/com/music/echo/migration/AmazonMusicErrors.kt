package iad1tya.echo.music.migration

import androidx.annotation.StringRes
import com.aura.migration.model.SourceType
import com.aura.migration.source.SourceError
import com.aura.migration.source.amazon.AmazonMusicException
import iad1tya.echo.music.R

/** True for the failures that mean «the stored Amazon session is no good»: sign in again. */
fun isAmazonSessionGone(e: Throwable): Boolean =
    (e is SourceError.NotAuthenticated && e.type == SourceType.AMAZON) ||
        (e is AmazonMusicException && e.reason == AmazonMusicException.Reason.SIGN_IN_AGAIN)

/** The Amazon-specific failures, in the user's language; null for anything that is not Amazon's. */
@StringRes
fun amazonSpecificErrorText(e: Throwable): Int? = when {
    e is SourceError.NotAuthenticated && e.type == SourceType.AMAZON -> R.string.migrate_amazon_expired
    e is AmazonMusicException -> when (e.reason) {
        AmazonMusicException.Reason.SIGN_IN_AGAIN -> R.string.migrate_amazon_error_sign_in
        AmazonMusicException.Reason.UNEXPECTED_RESPONSE -> R.string.migrate_amazon_error_format
        AmazonMusicException.Reason.INCOMPLETE_READ -> R.string.migrate_amazon_error_incomplete
        AmazonMusicException.Reason.REJECTED_QUERY -> R.string.migrate_amazon_error_rejected
    }
    else -> null
}

/** Any failure while talking to Amazon Music, as an honest message for the Amazon screens. */
@StringRes
fun amazonErrorText(e: Throwable): Int = amazonSpecificErrorText(e) ?: when (e) {
    is SourceError.Network, is SourceError.RateLimited, is java.io.IOException -> R.string.migrate_amazon_error_network
    else -> R.string.migrate_amazon_error_generic
}
