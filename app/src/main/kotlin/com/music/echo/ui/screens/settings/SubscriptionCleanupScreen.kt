package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.AuraTopAppBar
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy
import iad1tya.echo.music.viewmodels.SubscriptionCleanupViewModel
import iad1tya.echo.music.viewmodels.SubscriptionCleanupViewModel.Phase
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

/**
 * Registry row 813 — Ajustes ▸ Cuentas ▸ «Revisar suscripciones». The account's artist subscriptions, the suggested
 * phantoms first, per-row checkboxes, and «Quitar N suscripciones» behind a dialog that states N. The batch starts only
 * from that dialog ([SubscriptionCleanupViewModel.removeSelected]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionCleanupScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val viewModel: SubscriptionCleanupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    var confirmCount by remember { mutableStateOf(0) }

    val planned = PhantomSubscriptionPolicy.planBatch(
        ordered = state.candidates.map { it.id },
        selected = state.selected,
        blocked = state.candidates.filter { it.blocked }.map { it.id }.toSet(),
    ).size
    val suggestedCount = PhantomSubscriptionPolicy.suggestedSelection(state.candidates).size
    val running = state.phase == Phase.RUNNING

    Scaffold(
        topBar = {
            AuraTopAppBar(
                title = { Text(stringResource(R.string.sub_cleanup_title)) },
                navigationIcon = {
                    IconButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_BACK); navController.navigateUp() }, onLongClick = null) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.sub_cleanup_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (state.phase) {
                Phase.LOADING -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.sub_cleanup_loading))
                    }
                }
                Phase.SIGNED_OUT -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sub_cleanup_signed_out))
                        Button(onClick = { ActionLog.tap(ActionId.SUBCLEAN_LOGIN); navController.navigate("login") }) { Text(stringResource(R.string.login)) }
                    }
                }
                Phase.LOAD_FAILED -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.sub_cleanup_load_failed))
                        Button(onClick = { ActionLog.tap(ActionId.SUBCLEAN_RETRY); viewModel.load() }) { Text(stringResource(R.string.retry)) }
                    }
                }
                Phase.READY, Phase.RUNNING -> {
                    // Row 837: the read stopped before the end of the account's list; say so instead of passing it off as all.
                    if (state.incomplete && !running) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.sub_cleanup_incomplete, state.readCount),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                OutlinedButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_RELOAD); viewModel.load() }) { Text(stringResource(R.string.sub_cleanup_reload)) }
                            }
                        }
                    }
                    if (running) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (state.stopping) {
                                        stringResource(R.string.sub_cleanup_stopping)
                                    } else {
                                        stringResource(R.string.sub_cleanup_progress, state.done, state.total)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                LinearProgressIndicator(
                                    progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(stringResource(R.string.sub_cleanup_counts, state.removed, state.failed))
                                OutlinedButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_STOP); viewModel.stop() }, enabled = !state.stopping) {
                                    Text(stringResource(R.string.sub_cleanup_stop))
                                }
                            }
                        }
                    }
                    state.lastResult?.takeIf { !running }?.let { result ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(
                                        R.string.sub_cleanup_result,
                                        result.removed.size,
                                        result.failed.size,
                                        result.notAttempted.size,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (result.failed.isNotEmpty()) {
                                        Button(onClick = { ActionLog.tap(ActionId.SUBCLEAN_RETRY_FAILED); viewModel.retryFailed() }) {
                                            Text(stringResource(R.string.sub_cleanup_retry_failed))
                                        }
                                    }
                                    OutlinedButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_RELOAD); viewModel.load() }) { Text(stringResource(R.string.sub_cleanup_reload)) }
                                }
                            }
                        }
                    }
                    if (state.candidates.isEmpty()) {
                        item { Text(stringResource(R.string.sub_cleanup_empty)) }
                    } else if (!running) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_SELECT_SUGGESTED); viewModel.selectSuggested() }, enabled = suggestedCount > 0) {
                                        Text(stringResource(R.string.sub_cleanup_select_suggested, suggestedCount))
                                    }
                                    TextButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_CLEAR_SELECTION); viewModel.clearSelection() }, enabled = state.selected.isNotEmpty()) {
                                        Text(stringResource(R.string.sub_cleanup_clear_selection))
                                    }
                                }
                                Button(
                                    onClick = { ActionLog.tap(ActionId.SUBCLEAN_REMOVE); confirmCount = planned },
                                    enabled = planned > 0,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(pluralStringResource(R.plurals.sub_cleanup_remove_n, planned, planned))
                                }
                            }
                        }
                    }
                    items(state.candidates, key = { it.id }) { candidate ->
                        val selectable = !running && !candidate.blocked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = selectable) { ActionLog.tap(ActionId.SUBCLEAN_CANDIDATE); viewModel.toggleSelected(candidate.id) }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = candidate.id in state.selected,
                                onCheckedChange = { viewModel.toggleSelected(candidate.id) },
                                enabled = selectable,
                            )
                            AsyncImage(
                                model = candidate.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(CircleShape),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(candidate.name, style = MaterialTheme.typography.bodyLarge)
                                when {
                                    candidate.removalPending -> Text(
                                        stringResource(R.string.sub_cleanup_pending_badge),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    candidate.resendAllowed -> Text(
                                        stringResource(R.string.sub_cleanup_resend_badge),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    candidate.removalConfirmed -> Text(
                                        stringResource(R.string.sub_cleanup_confirmed_badge),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    candidate.verdict.suggested -> {
                                        Text(
                                            stringResource(R.string.sub_cleanup_suggested_badge),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Text(
                                            stringResource(R.string.sub_cleanup_suggested_reason),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (confirmCount > 0) {
        AlertDialog(
            onDismissRequest = { confirmCount = 0 },
            title = { Text(pluralStringResource(R.plurals.sub_cleanup_confirm_title, confirmCount, confirmCount)) },
            text = { Text(stringResource(R.string.sub_cleanup_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    ActionLog.tap(ActionId.SUBCLEAN_REMOVE_CONFIRM)
                    confirmCount = 0
                    viewModel.removeSelected()
                }) { Text(stringResource(R.string.sub_cleanup_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_REMOVE_CANCEL); confirmCount = 0 }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
