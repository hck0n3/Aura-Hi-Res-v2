package iad1tya.echo.music.ui.screens.settings.integrations

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.ListenTogetherAutoApprovalKey
import iad1tya.echo.music.constants.ListenTogetherUsernameKey
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.component.IntegrationCard
import iad1tya.echo.music.ui.component.IntegrationCardItem
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.rememberPreference
import iad1tya.echo.music.viewmodels.ListenTogetherViewModel
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

/**
 * Listen Together settings: blocked users, username and auto-approve join requests (host convenience, applied by the session
 * itself). Rooms are only Aura's own (row 818, owner decision B1, 2026-11-24): the «Salas de Aura» switch and the server URL
 * for the shared Metrolist/SimpMusic server were removed with it.
 *
 * REMOVED with the port (deliberate, documented): the remote "server list" fetched from
 * EchoMusicApp/Echo-Music (fila 38 del registro: fuga de branding viva; upstream SimpMusic has no
 * such list), the on-screen connection log (upstream keeps none — the room state IS the state), and
 * the smart-resync/volume-sync toggles (both were knobs of the old engine; the SimpMusic engine has
 * neither a volume publish nor a manual resync — requestSync happens on guest resume by design).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ListenTogetherViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val roomState by viewModel.roomState.collectAsState()
    val blockedUsernames by viewModel.blockedUsernames.collectAsState()

    // ONE flag read for the whole screen. The rows below (IntegrationCard -> Material3SettingsGroup)
    // already re-skin themselves; what was missing was the page background and the TopAppBar, which
    // this screen draws by hand rather than through that seam.
    val skin: AuraPanelSkin = rememberAuraPanelSkin()
    val ground = if (skin.enabled && skin.darkGround) AuraPalette.Ground else MaterialTheme.colorScheme.surface

    // Row 818 (owner decision B1, 2026-11-24): rooms are only Aura's, so the «Salas de Aura» switch and the server URL (with its
    // «Elegir servidor» dialog) are gone; see docs/UI_INVENTORY.md.
    var username by rememberPreference(ListenTogetherUsernameKey, "")
    var autoApproval by rememberPreference(ListenTogetherAutoApprovalKey, false)

    var showUsernameDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockedUsersDialog by rememberSaveable { mutableStateOf(false) }

    val inRoom = roomState?.inRoom == true

    if (showUsernameDialog) {
        var tempUsername by rememberSaveable(showUsernameDialog) { mutableStateOf(username) }

        DefaultDialog(
            onDismiss = { showUsernameDialog = false },
            icon = { Icon(painterResource(R.drawable.person), contentDescription = null) },
            title = { Text(stringResource(R.string.listen_together_username)) },
            buttons = {
                TextButton(onClick = { ActionLog.tap(ActionId.LTSET_USERNAME_RESET); username = ""; showUsernameDialog = false }) {
                    Text(stringResource(R.string.reset))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { ActionLog.tap(ActionId.LTSET_USERNAME_SAVE); username = tempUsername.trim(); showUsernameDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        ) {
            OutlinedTextField(
                value = tempUsername,
                onValueChange = { tempUsername = it.take(50) },
                label = { Text(stringResource(R.string.listen_together_username)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.person), contentDescription = null)
                },
                trailingIcon = {
                    if (tempUsername.isNotBlank()) {
                        IconButton(onClick = { ActionLog.tap(ActionId.LTSET_USERNAME_CLEAR); tempUsername = "" }, onLongClick = null) {
                            Icon(painterResource(R.drawable.close), contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showBlockedUsersDialog) {
        BlockedUsersDialog(
            blockedUsernames = blockedUsernames,
            onUnblock = { viewModel.unblockUser(it) },
            onDismiss = { showBlockedUsersDialog = false }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
    ) {
        Column(
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                IntegrationCard(
                    title = stringResource(R.string.settings),
                    items = listOf(
                        IntegrationCardItem(
                            icon = painterResource(R.drawable.person),
                            title = { Text(stringResource(R.string.listen_together_blocked_users)) },
                            description = {
                                Text(
                                    if (blockedUsernames.isNotEmpty())
                                        stringResource(R.string.listen_together_blocked_users_count, blockedUsernames.size)
                                    else
                                        stringResource(R.string.listen_together_no_blocked_users)
                                )
                            },
                            onClick = if (blockedUsernames.isNotEmpty()) {
                                { showBlockedUsersDialog = true }
                            } else null
                        ),
                        IntegrationCardItem(
                            icon = painterResource(R.drawable.person),
                            title = { Text(stringResource(R.string.listen_together_username)) },
                            description = {
                                Text(username.ifEmpty { stringResource(R.string.not_set) })
                            },
                            // metroserver mints a fresh identity per connection; the NAME is what a
                            // room knows you by, so it cannot change while a room is live.
                            onClick = if (!inRoom) {
                                { showUsernameDialog = true }
                            } else {
                                {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.listen_together_cannot_edit_username_in_room),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ),
                        IntegrationCardItem(
                            icon = painterResource(R.drawable.done),
                            title = { Text(stringResource(R.string.listen_together_auto_approval)) },
                            description = {
                                Text(stringResource(R.string.listen_together_auto_approval_desc))
                            },
                            trailingContent = {
                                Switch(
                                    checked = autoApproval,
                                    onCheckedChange = { autoApproval = it },
                                    enabled = !inRoom || roomState?.isHost == true,
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                id = if (autoApproval) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize),
                                        )
                                    }
                                )
                            },
                            onClick = {
                                if (!inRoom || roomState?.isHost == true) autoApproval = !autoApproval
                            }
                        )
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        iad1tya.echo.music.ui.component.AuraTopAppBar(
            title = { Text(stringResource(R.string.listen_together)) },
            navigationIcon = {
                IconButton(
                    onClick = { ActionLog.tap(ActionId.LTSET_BACK); navController.navigateUp() },
                    onLongClick = null,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = ground,
                scrolledContainerColor = if (skin.enabled && skin.darkGround)
                    AuraPalette.GroundRaised
                else
                    MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

@Composable
fun BlockedUsersDialog(
    blockedUsernames: List<String>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    // ONE flag read for the dialog; the rows below take their colours from it.
    val skin = rememberAuraPanelSkin()

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painterResource(R.drawable.person), contentDescription = null) },
        title = { Text(stringResource(R.string.listen_together_blocked_users)) },
        buttons = {
            TextButton(onClick = { ActionLog.tap(ActionId.LTSET_BLOCKED_CLOSE); onDismiss() }) {
                Text(stringResource(android.R.string.ok))
            }
        }
    ) {
        if (blockedUsernames.isEmpty()) {
            Text(
                text = stringResource(R.string.listen_together_no_blocked_users),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                items(blockedUsernames) { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { ActionLog.tap(ActionId.LTSET_UNBLOCK); onUnblock(name) }) {
                            Text(stringResource(R.string.listen_together_unblock))
                        }
                    }
                }
            }
        }
    }
}
