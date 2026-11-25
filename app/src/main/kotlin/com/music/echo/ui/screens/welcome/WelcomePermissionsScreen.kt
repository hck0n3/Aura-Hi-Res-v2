package iad1tya.echo.music.ui.screens.welcome

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.LastOpenedVersionCodeKey
import iad1tya.echo.music.constants.OnboardingArtistsDoneKey
import iad1tya.echo.music.constants.TermsAcceptedAppVersionKey
import iad1tya.echo.music.constants.WelcomeDoneKey
import iad1tya.echo.music.constants.WelcomeNotificationsDeclinedKey
import iad1tya.echo.music.constants.WelcomePermissionsDoneKey
import iad1tya.echo.music.legal.TermsAuraScheme
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.launch

/**
 * Registry row 872 (plan phase 2; owner order of row 209 d: «que pida TODOS los permisos desde el primer inicio»):
 * the welcome's permissions step. It sits between the terms and the licence (MainActivity: TermsGate → this →
 * LicenseGate) and renders INSTEAD of everything behind it until «Continuar», like the terms gate does. Only a fresh
 * install sees it ([WelcomeFlow.showPermissionsStep]); for everybody else it is a pure pass-through.
 *
 * One card per runtime permission Aura uses on this Android release, each with its reason, «Permitir» (the system
 * dialog) and «Ahora no». Nothing here blocks: «Continuar» is always enabled, whatever was answered.
 */
@Composable
fun WelcomePermissionsGate(appContent: @Composable () -> Unit) {
    // rememberPreference seeds from the stored value synchronously, so an existing user never sees this step flash.
    val permissionsDone by rememberPreference(WelcomePermissionsDoneKey, defaultValue = false)
    val welcomeDone by rememberPreference(WelcomeDoneKey, defaultValue = false)
    val lastOpenedVersionCode by rememberPreference(LastOpenedVersionCodeKey, defaultValue = WelcomeFlow.NEVER_OPENED)
    val onboardingArtistsDone by rememberPreference(OnboardingArtistsDoneKey, defaultValue = false)
    val termsAcceptedAppVersionCode by rememberPreference(TermsAcceptedAppVersionKey, defaultValue = 0)
    if (WelcomeFlow.showPermissionsStep(
            permissionsDone = permissionsDone,
            welcomeDone = welcomeDone,
            lastOpenedVersionCode = lastOpenedVersionCode,
            onboardingArtistsDone = onboardingArtistsDone,
            termsAcceptedAppVersionCode = termsAcceptedAppVersionCode,
        )
    ) {
        WelcomePermissionsScreen()
    } else {
        appContent()
    }
}

private fun isGranted(context: Context, permission: WelcomeFlow.Permission): Boolean =
    ContextCompat.checkSelfPermission(context, permission.permission) == PackageManager.PERMISSION_GRANTED

private fun WelcomeFlow.Permission.icon(): Int = when (this) {
    WelcomeFlow.Permission.NOTIFICATIONS -> R.drawable.notification
    WelcomeFlow.Permission.MUSIC_FILES, WelcomeFlow.Permission.MUSIC_FILES_LEGACY -> R.drawable.library_music
    WelcomeFlow.Permission.MICROPHONE -> R.drawable.mic
    WelcomeFlow.Permission.BLUETOOTH -> R.drawable.bluetooth
    WelcomeFlow.Permission.LOCAL_NETWORK -> R.drawable.wifi_proxy
}

private fun WelcomeFlow.Permission.title(): Int = when (this) {
    WelcomeFlow.Permission.NOTIFICATIONS -> R.string.welcome_permission_notifications
    WelcomeFlow.Permission.MUSIC_FILES, WelcomeFlow.Permission.MUSIC_FILES_LEGACY -> R.string.welcome_permission_music_files
    WelcomeFlow.Permission.MICROPHONE -> R.string.welcome_permission_microphone
    WelcomeFlow.Permission.BLUETOOTH -> R.string.welcome_permission_bluetooth
    WelcomeFlow.Permission.LOCAL_NETWORK -> R.string.welcome_permission_local_network
}

private fun WelcomeFlow.Permission.reason(): Int = when (this) {
    WelcomeFlow.Permission.NOTIFICATIONS -> R.string.welcome_permission_notifications_reason
    WelcomeFlow.Permission.MUSIC_FILES, WelcomeFlow.Permission.MUSIC_FILES_LEGACY -> R.string.welcome_permission_music_files_reason
    WelcomeFlow.Permission.MICROPHONE -> R.string.welcome_permission_microphone_reason
    WelcomeFlow.Permission.BLUETOOTH -> R.string.welcome_permission_bluetooth_reason
    WelcomeFlow.Permission.LOCAL_NETWORK -> R.string.welcome_permission_local_network_reason
}

private val CardShape = RoundedCornerShape(20.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomePermissionsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cards = remember { WelcomeFlow.permissionsFor(Build.VERSION.SDK_INT) }
    // Re-read after every answer: Android may grant a permission of the same group together with the one asked.
    var granted by remember { mutableStateOf(cards.filter { isGranted(context, it) }.toSet()) }
    // Names, comma-joined, so they survive a fold / rotation without a custom saver.
    var skipped by rememberSaveable { mutableStateOf("") }
    var asking by rememberSaveable { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = cards.filter { isGranted(context, it) }.toSet()
        if (!ok && asking.isNotEmpty()) skipped = (skipped.split(',').filter { it.isNotEmpty() } + asking).joinToString(",")
        asking = ""
    }

    MaterialTheme(colorScheme = TermsAuraScheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AuraPalette.Ground)
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Scrolls as a whole (title included): the Fold's cover screen and the large font must reach every card.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.welcome_permissions_title),
                    style = AuraType.SheetTitle,
                    color = AuraPalette.OnGround,
                )
                Text(
                    text = stringResource(R.string.welcome_permissions_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuraPalette.OnGroundMuted,
                )
                cards.forEach { card ->
                    val isSkipped = card.name in skipped.split(',')
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardShape)
                            .background(AuraPalette.GroundRaised)
                            .border(0.5.dp, AuraPalette.SurfaceLine, CardShape)
                            .padding(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                painter = painterResource(card.icon()),
                                contentDescription = null,
                                tint = AuraPalette.Teal,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(card.title()),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AuraPalette.OnGround,
                                )
                                Text(
                                    text = stringResource(card.reason()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuraPalette.OnGroundMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (card in granted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = AuraPalette.Teal,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.welcome_permission_granted),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AuraPalette.Teal,
                                )
                            }
                        } else {
                            if (isSkipped) {
                                Text(
                                    text = stringResource(R.string.welcome_permission_skipped),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AuraPalette.OnGroundMuted,
                                )
                            }
                            // Wraps onto two lines with a large font instead of cutting a label.
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            ) {
                                if (!isSkipped) {
                                    TextButton(onClick = {
                                        ActionLog.tap(ActionId.WELCOME_PERM_NOT_NOW)
                                        skipped = (skipped.split(',').filter { it.isNotEmpty() } + card.name).joinToString(",")
                                    }) {
                                        Text(stringResource(R.string.welcome_permission_not_now))
                                    }
                                }
                                Button(
                                    onClick = {
                                        ActionLog.tap(ActionId.WELCOME_PERM_ALLOW)
                                        asking = card.name
                                        launcher.launch(card.permission)
                                    },
                                    shape = AuraShapes.Pill,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AuraPalette.Teal,
                                        contentColor = AuraPalette.OnAccent,
                                    ),
                                ) {
                                    Text(stringResource(R.string.welcome_permission_allow))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = AuraPalette.SurfaceLine, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    ActionLog.tap(ActionId.WELCOME_PERM_CONTINUE)
                    val notificationsDeclined = WelcomeFlow.Permission.NOTIFICATIONS in cards &&
                        !isGranted(context, WelcomeFlow.Permission.NOTIFICATIONS)
                    scope.launch {
                        context.dataStore.edit {
                            if (notificationsDeclined) it[WelcomeNotificationsDeclinedKey] = true
                            it[WelcomePermissionsDoneKey] = true
                        }
                    }
                },
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                shape = AuraShapes.Pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuraPalette.Teal,
                    contentColor = AuraPalette.OnAccent,
                ),
            ) {
                Text(stringResource(R.string.welcome_continue))
            }
        }
    }
}
