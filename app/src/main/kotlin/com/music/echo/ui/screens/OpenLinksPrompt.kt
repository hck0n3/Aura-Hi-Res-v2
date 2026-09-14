package iad1tya.echo.music.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import iad1tya.echo.music.R

/**
 * One-shot prompt to make Aura the app that opens music links (YouTube Music and the other platforms).
 * Android 12+ never lets an app claim someone else's domain by itself, so the prompt sends the user to
 * the system "Open by default" page for Aura, where the links are enabled with one tap.
 */
@Composable
fun OpenLinksDefaultPrompt(onDone: () -> Unit) {
    val context = LocalContext.current
    iad1tya.echo.music.ui.component.AuraAlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.open_links_prompt_title)) },
        text = { Text(stringResource(R.string.open_links_prompt_body)) },
        confirmButton = {
            TextButton(onClick = {
                openAppOpenByDefaultSettings(context)
                onDone()
            }) { Text(stringResource(R.string.open_links_prompt_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.open_links_prompt_later)) }
        },
    )
}

/** True when the user already chose Aura for music.youtube.com links (Android 12+ only; false before). */
fun isAuraOpeningMusicLinks(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }.getOrNull() ?: return false
    val youTubeMusic = state.hostToStateMap["music.youtube.com"]
    return youTubeMusic == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
        youTubeMusic == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
}

fun openAppOpenByDefaultSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
