package iad1tya.echo.music.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import iad1tya.echo.music.LocalListenTogetherManager
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AppBarHeight
import iad1tya.echo.music.constants.ListenTogetherInTopBarKey
import iad1tya.echo.music.constants.ListenTogetherUsernameKey
import iad1tya.echo.music.listentogether.ConnectionState
import iad1tya.echo.music.listentogether.PendingJoin
import iad1tya.echo.music.listentogether.PendingSuggestion
import iad1tya.echo.music.listentogether.RoomMember
import iad1tya.echo.music.ui.component.DefaultDialog
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.newui.AuraPalette
import iad1tya.echo.music.ui.newui.AuraPanel
import iad1tya.echo.music.ui.newui.AuraPanelSkin
import iad1tya.echo.music.ui.newui.AuraShapes
import iad1tya.echo.music.ui.newui.AuraSpacing
import iad1tya.echo.music.ui.newui.AuraType
import iad1tya.echo.music.ui.newui.rememberAuraPanelSkin
import iad1tya.echo.music.utils.rememberPreference
import kotlinx.coroutines.launch

/**
 * Listen Together — the SimpMusic/Metrolist room screen, on this app's surfaces.
 *
 * The ENGINE is the SimpMusic port (protocol + session + playback bridge); this screen only renders
 * [iad1tya.echo.music.listentogether.ListenTogetherState]. Room semantics that differ from the previous
 * implementation and are visible here:
 * - Joining waits for the HOST to approve (`pendingJoinCode`), shown as its own state.
 * - The buffer barrier is named out loud (`waitingForNames`) — playback stops for the slowest
 *   device, and silence without explanation reads as a hang.
 * - Chat is GONE: the metroproto wire protocol has no chat message, so the old chat was a private
 *   extension that only worked between two of OUR clients. (Regla 5: ocultar también es perder —
 *   documentado en el registro, fila 190.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherScreen(
    navController: NavController,
    showTopBar: Boolean = false
) {
    val context = LocalContext.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    if (listenTogetherManager == null) {
        NotConfiguredContent()
        return
    }

    val connectionState by listenTogetherManager.connectionState.collectAsState()
    val roomState by listenTogetherManager.roomState.collectAsState()
    val pendingJoinRequests by listenTogetherManager.pendingJoinRequests.collectAsState()
    val pendingSuggestions by listenTogetherManager.pendingSuggestions.collectAsState()
    // The room fans out BUFFER_WAIT with the participants still loading the track; the state
    // exposes it per member (isBuffering) AND as the id list — both are rendered below.
    val bufferingUsers by listenTogetherManager.bufferingUsers.collectAsState()
    val blockedUsernames by listenTogetherManager.blockedUsernames.collectAsState()

    val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, defaultValue = true)
    val shouldShowTopBar = showTopBar || listenTogetherInTopBar

    var savedUsername by rememberPreference(ListenTogetherUsernameKey, "")
    var roomCodeInput by rememberSaveable { mutableStateOf("") }
    var usernameInput by rememberSaveable { mutableStateOf(savedUsername) }
    var joinErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var managing by rememberSaveable { mutableStateOf<String?>(null) }

    val kickedFromRoomText = stringResource(R.string.listen_together_kicked_from_room)
    val connectionFailedText = stringResource(R.string.listen_together_connection_failed)

    LaunchedEffect(savedUsername) {
        if (usernameInput.isBlank() && savedUsername.isNotBlank()) {
            usernameInput = savedUsername
        }
    }

    // The session owns the error message (bad code, host declined, server error, kicked); surface
    // it once and clear it — a transient failure must not wedge the form.
    LaunchedEffect(roomState?.error) {
        val error = roomState?.error
        joinErrorMessage = error?.let { raw ->
            when {
                raw.contains("kicked", ignoreCase = true) -> kickedFromRoomText
                else -> raw
            }
        }
        if (error != null) listenTogetherManager.clearError()
    }

    val isInRoom = roomState?.inRoom == true
    val isHost = roomState?.isHost == true

    val memberForDialog = managing?.let { id -> roomState?.members?.firstOrNull { it.userId == id } }
    if (memberForDialog != null) {
        UserActionDialog(
            username = memberForDialog.username,
            isBlocked = blockedUsernames.any { it.equals(memberForDialog.username, ignoreCase = true) },
            onKick = {
                listenTogetherManager.kickUser(memberForDialog.userId, "Removed by host")
                managing = null
            },
            onBlockAndKick = {
                listenTogetherManager.blockUser(memberForDialog.username)
                listenTogetherManager.kickUser(memberForDialog.userId, "Removed by host")
                managing = null
            },
            onTransferOwnership = {
                listenTogetherManager.transferHost(memberForDialog.userId)
                managing = null
            },
            onDismiss = { managing = null }
        )
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    // ONE flag read for the whole screen; every panel below takes it as a parameter.
    val skin = rememberAuraPanelSkin()
    val auraDark = skin.enabled && skin.darkGround

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .fillMaxSize()
            .background(if (auraDark) AuraPalette.Ground else MaterialTheme.colorScheme.background)
            .imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = windowInsets.asPaddingValues().calculateTopPadding() + 16.dp,
            bottom = windowInsets.asPaddingValues().calculateBottomPadding() + 16.dp + AppBarHeight
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Not-in-room + connected: the room you create disappears if you background the app with
        // nothing playing, because Android freezes an idle process. Said once, not as a dialog.
        if (connectionState is ConnectionState.Connected && !isInRoom) {
            item {
                Text(
                    text = stringResource(R.string.listen_together_background_disconnect_note),
                    style = if (skin.enabled) AuraType.CalloutSubtitle else MaterialTheme.typography.bodySmall,
                    color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (isInRoom) {
            roomState?.let { room ->
                // The buffer barrier, said out loud: playback genuinely stops until everyone is
                // ready, and naming who is being waited for is what stops the silence reading as
                // a hang (SimpMusic rule).
                if (room.waitingForNames.isNotEmpty()) {
                    item { BufferBarrierBanner(names = room.waitingForNames, skin = skin) }
                }

                item {
                    RoomStatusCard(
                        roomCode = room.roomCode.orEmpty(),
                        isHost = isHost,
                        hostName = room.members.firstOrNull { it.isHost }?.username.orEmpty(),
                        skin = skin,
                        context = context
                    )
                }

                item {
                    ConnectedUsersSection(
                        members = room.members,
                        isHost = isHost,
                        skin = skin,
                        currentUserId = room.selfUserId,
                        onUserClick = { clickedUserId ->
                            if (isHost && clickedUserId != room.selfUserId) {
                                managing = clickedUserId
                            }
                        }
                    )
                }

                if (isHost && pendingJoinRequests.isNotEmpty()) {
                    item {
                        PendingJoinRequestsSection(
                            requests = pendingJoinRequests,
                            skin = skin,
                            onApprove = { listenTogetherManager.approveJoin(it) },
                            onReject = { listenTogetherManager.rejectJoin(it, "Rejected by host") }
                        )
                    }
                }

                if (isHost && pendingSuggestions.isNotEmpty()) {
                    item {
                        PendingSuggestionsSection(
                            suggestions = pendingSuggestions,
                            skin = skin,
                            onApprove = { listenTogetherManager.approveSuggestion(it) },
                            onReject = { listenTogetherManager.rejectSuggestion(it, "Rejected by host") }
                        )
                    }
                }

                item {
                    Button(
                        onClick = { listenTogetherManager.leaveRoom() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.logout),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.leave_room),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else if (roomState?.pendingJoinCode != null) {
            // Joining is not immediate — the host has to approve it. Without this state a wrong
            // code and a host who has not looked at their phone are indistinguishable.
            item {
                WaitingForApprovalCard(
                    code = roomState?.pendingJoinCode.orEmpty(),
                    skin = skin,
                    onCancel = { listenTogetherManager.session.cancelJoin() }
                )
            }
        } else {
            item {
                JoinCreateRoomSection(
                    skin = skin,
                    usernameInput = usernameInput,
                    onUsernameChange = { usernameInput = it },
                    roomCodeInput = roomCodeInput,
                    onRoomCodeChange = { roomCodeInput = it },
                    savedUsername = savedUsername,
                    joinErrorMessage = joinErrorMessage,
                    isConnected = connectionState is ConnectionState.Connected,
                    waitingForApprovalText = stringResource(R.string.waiting_for_approval),
                    bringIntoViewRequester = bringIntoViewRequester,
                    onCreateRoom = {
                        val username = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                        val finalUsername = username.trim()
                        if (finalUsername.isNotBlank()) {
                            savedUsername = finalUsername
                            joinErrorMessage = null
                            listenTogetherManager.createRoom(finalUsername)
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onJoinRoom = {
                        val username = usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                        val finalUsername = username.trim()
                        if (finalUsername.isNotBlank()) {
                            savedUsername = finalUsername
                            joinErrorMessage = null
                            listenTogetherManager.joinRoom(roomCodeInput, finalUsername)
                        } else {
                            Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFieldFocused = {
                        coroutineScope.launch {
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                )
            }
        }

        item {
            SettingsLinkCard(
                onClick = { navController.navigate("settings/integrations/listen_together") }
            )
        }
    }

    if (shouldShowTopBar) {
        TopAppBar(
            title = { Text(stringResource(R.string.listen_together)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = null
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            actions = {
                when (val state = connectionState) {
                    is ConnectionState.Connected -> {
                        TextButton(onClick = { listenTogetherManager.disconnect() }) {
                            Text(stringResource(R.string.disconnect))
                        }
                    }
                    is ConnectionState.Failed -> {
                        TextButton(onClick = { listenTogetherManager.connect() }) {
                            Text(stringResource(R.string.connect))
                        }
                    }
                    is ConnectionState.Disconnected -> {
                        TextButton(onClick = { listenTogetherManager.connect() }) {
                            Text(stringResource(R.string.connect))
                        }
                    }
                    is ConnectionState.Connecting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }
        )
    }
}

/** The room waits for the slowest device; the banner names them (SimpMusic BufferBanner). */
@Composable
private fun BufferBarrierBanner(
    names: List<String>,
    skin: AuraPanelSkin
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (names.isEmpty()) stringResource(R.string.listen_together_waiting_everyone)
                else stringResource(R.string.listen_together_waiting_for, names.joinToString(", ")),
                style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.listen_together_buffer_resumes),
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NotConfiguredContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.listen_together_not_configured),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp)
        )
    }
}

@Composable
private fun RoomStatusCard(
    roomCode: String,
    isHost: Boolean,
    hostName: String,
    skin: AuraPanelSkin,
    context: Context
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.room_code),
                style = if (skin.enabled) AuraType.SectionLabel else MaterialTheme.typography.labelLarge,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = roomCode,
                style = if (skin.enabled)
                    AuraType.Technical.copy(fontSize = 34.sp, lineHeight = 40.sp)
                else
                    MaterialTheme.typography.displaySmall,
                color = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isHost)
                    stringResource(R.string.listen_together_you_are_host)
                else
                    stringResource(R.string.listen_together_hosted_by, hostName),
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodyMedium,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (isHost) {
                Spacer(modifier = Modifier.height(16.dp))
                // echomusic://listen?code=… is the scheme this app registers (AndroidManifest) and
                // parses (MainActivity.handleDeepLink: host "listen" + "code" query parameter).
                val inviteLink = remember(roomCode) { "echomusic://listen?code=$roomCode" }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(12.dp),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Listen Together Link", inviteLink)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = stringResource(R.string.copy_link),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_link))
                    }

                    FilledTonalButton(
                        shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(12.dp),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Room Code", roomCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.content_copy),
                            contentDescription = stringResource(R.string.copy_code),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.copy_code))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedUsersSection(
    members: List<RoomMember>,
    isHost: Boolean,
    skin: AuraPanelSkin,
    currentUserId: String,
    onUserClick: (String) -> Unit
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.listen_together_users, members.size),
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleMedium,
                fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(Modifier.fillMaxWidth()) {
                members.forEachIndexed { index, member ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .padding(start = 52.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    if (skin.enabled) skin.line
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                                )
                        )
                    }
                    val clickable = isHost && member.userId != currentUserId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (skin.enabled) Modifier.sizeIn(minHeight = AuraSpacing.MinTouchTarget)
                                else Modifier
                            )
                            .then(if (clickable) Modifier.clickable { onUserClick(member.userId) } else Modifier)
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        AvatarBadge(
                            letter = member.username.take(1).uppercase(),
                            isHost = member.isHost,
                            isSelf = member.userId == currentUserId,
                            skin = skin
                        )
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = member.username,
                                    style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (skin.enabled) skin.ink else Color.Unspecified,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (member.isHost) {
                                    Text(
                                        text = stringResource(R.string.listen_together_you_are_host),
                                        style = if (skin.enabled) AuraType.QualityBadge else MaterialTheme.typography.labelSmall,
                                        color = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                member.isBuffering -> MaterialTheme.colorScheme.tertiary
                                                member.isConnected -> if (skin.enabled) skin.accent
                                                else MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                )
                                Text(
                                    text = when {
                                        member.isBuffering -> stringResource(R.string.listen_together_user_buffering)
                                        !member.isConnected -> stringResource(R.string.listen_together_user_disconnected)
                                        else -> stringResource(R.string.listen_together_user_in_sync)
                                    },
                                    style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                                    color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarBadge(
    letter: String,
    isHost: Boolean,
    isSelf: Boolean,
    skin: AuraPanelSkin
) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        // Host = accent, you = blue, everyone else = the card's own wash (the render's ramp).
        color = when {
            isHost -> if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
            isSelf -> if (skin.enabled && skin.darkGround) AuraPalette.Blue
            else MaterialTheme.colorScheme.secondary
            else -> if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = letter,
                style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    isHost || isSelf ->
                        if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                        else MaterialTheme.colorScheme.onPrimary
                    else -> if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun WaitingForApprovalCard(
    code: String,
    skin: AuraPanelSkin,
    onCancel: () -> Unit
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.waiting_for_approval),
                style = if (skin.enabled) AuraType.SectionLabel else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = code.chunked(4).joinToString("  "),
                style = if (skin.enabled) AuraType.Technical.copy(fontSize = 24.sp)
                else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = if (skin.enabled) skin.ink else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.listen_together_waiting_approval_desc),
                style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel_join))
            }
        }
    }
}

@Composable
private fun PendingJoinRequestsSection(
    requests: List<PendingJoin>,
    skin: AuraPanelSkin,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.listen_together_join_requests),
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleMedium,
                fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            requests.forEach { request ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (skin.enabled) Modifier.sizeIn(minHeight = AuraSpacing.MinTouchTarget) else Modifier)
                        .padding(vertical = 8.dp)
                ) {
                    AvatarBadge(letter = request.username.take(1).uppercase(), isHost = false, isSelf = false, skin = skin)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = request.username,
                            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyLarge,
                            fontWeight = if (skin.enabled) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (skin.enabled) skin.ink else Color.Unspecified
                        )
                        Text(
                            text = stringResource(R.string.listen_together_just_asked),
                            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MaterialIconButton(onClick = { onApprove(request.userId) }) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.approve),
                            tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    MaterialIconButton(onClick = { onReject(request.userId) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.reject),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSuggestionsSection(
    suggestions: List<PendingSuggestion>,
    skin: AuraPanelSkin,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.pending_suggestions),
                style = if (skin.enabled) AuraType.MenuGroupLabel else MaterialTheme.typography.titleMedium,
                fontWeight = if (skin.enabled) FontWeight.Normal else FontWeight.Bold,
                color = if (skin.enabled) skin.inkFaint else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (skin.enabled) Modifier.sizeIn(minHeight = AuraSpacing.MinTouchTarget) else Modifier)
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.queue_music),
                        contentDescription = null,
                        tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.track.title,
                            style = if (skin.enabled) AuraType.RowTitle else MaterialTheme.typography.bodyMedium,
                            fontWeight = if (skin.enabled) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (skin.enabled) skin.ink else Color.Unspecified,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.listen_together_suggestion_by, suggestion.fromUsername),
                            style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                            color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    MaterialIconButton(onClick = { onApprove(suggestion.suggestionId) }) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = stringResource(R.string.approve),
                            tint = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    MaterialIconButton(onClick = { onReject(suggestion.suggestionId) }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.reject),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinCreateRoomSection(
    skin: AuraPanelSkin,
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    savedUsername: String,
    joinErrorMessage: String?,
    isConnected: Boolean,
    waitingForApprovalText: String,
    bringIntoViewRequester: BringIntoViewRequester,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onFieldFocused: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // The SimpMusic input rules, applied at the edge so the protocol never has to reject:
    // metroserver caps usernames at 50 and mints 8-char uppercase room codes.
    fun onUsernameChangeCapped(value: String) = onUsernameChange(value.take(50))
    fun onRoomCodeChangeNormalized(value: String) =
        onRoomCodeChange(value.uppercase().filter { it.isLetterOrDigit() }.take(8))

    AuraPanel(
        skin = skin,
        modifier = Modifier.fillMaxWidth(),
        classicShape = RoundedCornerShape(24.dp),
        classicColors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp))
                    .background(
                        if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Button(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 0) {
                            if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary
                        } else Color.Transparent,
                        contentColor = if (selectedTab == 0) {
                            if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                            else MaterialTheme.colorScheme.onPrimary
                        } else {
                            if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.create_room), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                    shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == 1) {
                            if (skin.enabled && skin.darkGround) AuraPalette.Blue
                            else MaterialTheme.colorScheme.tertiary
                        } else Color.Transparent,
                        contentColor = if (selectedTab == 1) {
                            if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                            else MaterialTheme.colorScheme.onTertiary
                        } else {
                            if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.login),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.join_room), fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedTextField(
                value = usernameInput,
                onValueChange = ::onUsernameChangeCapped,
                label = { Text(stringResource(R.string.username)) },
                placeholder = { Text(stringResource(R.string.enter_username)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.person),
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (usernameInput.isNotBlank()) {
                        MaterialIconButton(onClick = { onUsernameChange("") }) {
                            Icon(painterResource(R.drawable.close), null)
                        }
                    }
                },
                singleLine = true,
                shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (skin.enabled) skin.line
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused) onFieldFocused() }
            )

            AnimatedVisibility(
                visible = selectedTab == 1,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OutlinedTextField(
                    value = roomCodeInput,
                    onValueChange = ::onRoomCodeChangeNormalized,
                    label = { Text(stringResource(R.string.room_code)) },
                    placeholder = { Text(stringResource(R.string.enter_room_code)) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.group),
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (roomCodeInput.isNotBlank()) {
                            MaterialIconButton(onClick = { onRoomCodeChange("") }) {
                                Icon(painterResource(R.drawable.close), null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (skin.enabled) skin.line
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = if (skin.enabled) skin.fill else MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged { if (it.isFocused) onFieldFocused() }
                )
            }

            AnimatedVisibility(
                visible = joinErrorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = if (skin.enabled) AuraShapes.Card else RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.error),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = joinErrorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            val hasUsername = usernameInput.trim().isNotBlank() || savedUsername.isNotBlank()
            val hasRoomCode = roomCodeInput.length == 8
            // SimpMusic gates both buttons on a live socket: sending create_room with no server is
            // a message that goes nowhere, and the room state never changes.
            val canAct = hasUsername && isConnected

            if (selectedTab == 0) {
                Button(
                    onClick = onCreateRoom,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canAct,
                    shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (skin.enabled) skin.accent else MaterialTheme.colorScheme.primary,
                        contentColor = if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                        else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.create_room), fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onJoinRoom,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canAct && hasRoomCode,
                    shape = if (skin.enabled) AuraShapes.Pill else RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (skin.enabled && skin.darkGround) AuraPalette.Blue
                        else MaterialTheme.colorScheme.tertiary,
                        contentColor = if (skin.enabled && skin.darkGround) AuraPalette.OnAccent
                        else MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.login),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.join_room), fontWeight = FontWeight.SemiBold)
                }
            }

            if (!isConnected) {
                Text(
                    text = connectionNeededText(),
                    style = if (skin.enabled) AuraType.RowSubtitle else MaterialTheme.typography.bodySmall,
                    color = if (skin.enabled) skin.inkMuted else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun connectionNeededText(): String = stringResource(R.string.listen_together_not_connected_hint)

@Composable
private fun SettingsLinkCard(onClick: () -> Unit) {
    iad1tya.echo.music.ui.component.Material3SettingsGroup(
        items = listOf(
            iad1tya.echo.music.ui.component.Material3SettingsItem(
                icon = painterResource(R.drawable.settings),
                title = { Text(stringResource(R.string.settings)) },
                description = { Text(stringResource(R.string.listen_together_settings_desc)) },
                onClick = onClick
            )
        )
    )
}

@Composable
private fun UserActionDialog(
    username: String,
    isBlocked: Boolean,
    onKick: () -> Unit,
    onBlockAndKick: () -> Unit,
    onTransferOwnership: () -> Unit,
    onDismiss: () -> Unit
) {
    DefaultDialog(
        onDismiss = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.group),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.manage_user),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onKick),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.kick_user),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.kick_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Blocks by NAME, then kicks. The protocol has no ban and the server mints a new user
            // id per connection, so the id cannot be blocked on — the name is the only thing that
            // survives a reconnect, and it is changeable. A convenience, not a security control
            // (upstream documents the same limitation).
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBlockAndKick),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.permanently_kick_user),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.permanently_kick_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTransferOwnership),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.crown),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.transfer_ownership),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.transfer_ownership_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
