package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.ui.component.AuraTopAppBar
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.utils.LocalFollowCleanupPolicy
import iad1tya.echo.music.utils.PhantomSubscriptionPolicy
import iad1tya.echo.music.viewmodels.LocalFollowCleanupViewModel
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
    // Registry row 905: the follows an import or a restore wrote in Aura only (never on the account).
    val localViewModel: LocalFollowCleanupViewModel = hiltViewModel()
    val localState by localViewModel.state.collectAsState()
    var localPreview by remember { mutableStateOf(false) }

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
            localFollowSection(localState, localViewModel, onPreview = { localPreview = true })
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
                    // Row 856: how many the account lists (a minimum when the read stopped early) and how many are suggested.
                    if (state.candidates.isNotEmpty()) {
                        val totals = subscriptionCleanupTotals(state.candidates, state.incomplete)
                        item {
                            Text(
                                listOf(
                                    if (totals.atLeast) {
                                        pluralStringResource(R.plurals.sub_cleanup_total_at_least, totals.listed, totals.listed)
                                    } else {
                                        pluralStringResource(R.plurals.sub_cleanup_total, totals.listed, totals.listed)
                                    },
                                    pluralStringResource(R.plurals.sub_cleanup_total_suggested, totals.suggested, totals.suggested),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
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
                    // Row 839: a run the app's process lost (killed by the system, force stop) before it ended.
                    state.interrupted?.takeIf { !running }?.let { interrupted ->
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.sub_cleanup_interrupted, interrupted.done, interrupted.total),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { ActionLog.tap(ActionId.SUBCLEAN_CONTINUE); viewModel.continueInterrupted() },
                                        enabled = state.phase == Phase.READY,
                                    ) { Text(stringResource(R.string.sub_cleanup_continue)) }
                                    OutlinedButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_DISCARD); viewModel.discardInterrupted() }) {
                                        Text(stringResource(R.string.sub_cleanup_discard))
                                    }
                                }
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
                                // Row 838: the run belongs to the app, not to this screen.
                                Text(
                                    stringResource(R.string.sub_cleanup_background_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.refused) {
                                    Text(
                                        stringResource(R.string.sub_cleanup_already_running),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
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
                        // Row 838: reopened while a run goes on — the list may still be being read.
                        when (state.listPhase) {
                            Phase.READY -> item { Text(stringResource(R.string.sub_cleanup_empty)) }
                            Phase.LOADING -> item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.sub_cleanup_loading))
                                }
                            }
                            else -> Unit
                        }
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
                                    // Row 838: what the run in progress (or the last one) already did with this row.
                                    candidate.id in state.removedIds -> Text(
                                        stringResource(R.string.sub_cleanup_row_removed),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    candidate.id in state.failedIds -> Text(
                                        stringResource(R.string.sub_cleanup_row_failed),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
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
            text = {
                // Row 905: the preview — the names of exactly what the batch will remove.
                val names = PhantomSubscriptionPolicy.planBatch(
                    ordered = state.candidates.map { it.id },
                    selected = state.selected,
                    blocked = state.candidates.filter { it.blocked }.map { it.id }.toSet(),
                ).mapNotNull { id -> state.candidates.firstOrNull { it.id == id }?.name }
                CleanupPreview(stringResource(R.string.sub_cleanup_confirm_message), stringResource(R.string.sub_cleanup_confirm_list), names)
            },
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

    if (localPreview) {
        // Row 905: the preview of a local removal — what changes (Aura only) and the names, before anything is written.
        val plan = LocalFollowCleanupPolicy.plan(localState.candidates, localState.selected)
        AlertDialog(
            onDismissRequest = { localPreview = false },
            title = { Text(pluralStringResource(R.plurals.sub_cleanup_local_confirm_title, plan.size, plan.size)) },
            text = {
                CleanupPreview(
                    stringResource(R.string.sub_cleanup_local_confirm_message),
                    stringResource(R.string.sub_cleanup_confirm_list),
                    plan.map { it.name },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = plan.isNotEmpty() && !localState.working,
                    onClick = {
                        ActionLog.tap(ActionId.SUBCLEAN_LOCAL_REMOVE_CONFIRM)
                        localPreview = false
                        localViewModel.removeChosen()
                    },
                ) { Text(stringResource(R.string.sub_cleanup_local_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_LOCAL_REMOVE_CANCEL); localPreview = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Row 905: a confirmation's message, then the names it applies to (scrollable when long). */
@Composable
private fun CleanupPreview(message: String, listTitle: String, names: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(message)
        if (names.isNotEmpty()) {
            Text(listTitle, style = MaterialTheme.typography.titleSmall)
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                items(names) { name ->
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Row 905 — «Seguidos solo en Aura»: the artists an import (Spotify, Tidal, Deezer) or a restored backup followed in Aura
 * and the user never confirmed ([LocalFollowCleanupPolicy]). Shown only when there is something (or an undo) to offer.
 * Select some or all, preview, remove from Aura only, undo. Never touches the account.
 */
@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.localFollowSection(
    state: LocalFollowCleanupViewModel.UiState,
    viewModel: LocalFollowCleanupViewModel,
    onPreview: () -> Unit,
) {
    if (state.failed) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sub_cleanup_local_failed), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_LOCAL_RETRY); viewModel.load() }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        return
    }
    if (state.candidates.isEmpty() && state.undoRows == 0) return
    val planned = LocalFollowCleanupPolicy.plan(state.candidates, state.selected).size
    val suggested = LocalFollowCleanupPolicy.suggestedSelection(state.candidates).size
    item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                pluralStringResource(R.plurals.sub_cleanup_local_title, state.candidates.size, state.candidates.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.sub_cleanup_local_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.undoRows > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.sub_cleanup_local_removed, state.lastRemoved, state.lastRemoved),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_LOCAL_UNDO); viewModel.undoLast() }, enabled = !state.working) {
                        Text(stringResource(R.string.sub_cleanup_local_undo))
                    }
                }
            }
            if (state.candidates.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { ActionLog.tap(ActionId.SUBCLEAN_LOCAL_SELECT_ALL); viewModel.selectAll() }, enabled = !state.working) {
                        Text(stringResource(R.string.sub_cleanup_local_select_all, state.candidates.size))
                    }
                    TextButton(
                        onClick = { ActionLog.tap(ActionId.SUBCLEAN_LOCAL_SELECT_SUGGESTED); viewModel.selectSuggested() },
                        enabled = suggested > 0 && !state.working,
                    ) {
                        Text(stringResource(R.string.sub_cleanup_local_select_suggested, suggested))
                    }
                    TextButton(
                        onClick = { ActionLog.tap(ActionId.SUBCLEAN_LOCAL_CLEAR_SELECTION); viewModel.clearSelection() },
                        enabled = state.selected.isNotEmpty() && !state.working,
                    ) {
                        Text(stringResource(R.string.sub_cleanup_clear_selection))
                    }
                }
                OutlinedButton(
                    onClick = { ActionLog.tap(ActionId.SUBCLEAN_LOCAL_REMOVE); onPreview() },
                    enabled = planned > 0 && !state.working,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(pluralStringResource(R.plurals.sub_cleanup_local_remove_n, planned, planned))
                }
            }
        }
    }
    items(state.candidates, key = { "local:" + it.id }) { candidate ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.working) { ActionLog.tap(ActionId.SUBCLEAN_LOCAL_CANDIDATE); viewModel.toggle(candidate.id) }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = candidate.id in state.selected,
                onCheckedChange = { viewModel.toggle(candidate.id) },
                enabled = !state.working,
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
                if (candidate.verdict.suggested) {
                    Text(
                        stringResource(R.string.sub_cleanup_local_suggested_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    stringResource(R.string.sub_cleanup_local_reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (candidate.verdict.suggested) {
                    Text(
                        stringResource(R.string.sub_cleanup_suggested_reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    item { Spacer(Modifier.height(8.dp)) }
}

/** Row 856: the header's numbers. [atLeast]: the read stopped before the end of the account's list (row 837). */
internal data class SubscriptionCleanupTotals(val listed: Int, val suggested: Int, val atLeast: Boolean)

/**
 * Row 856 (owner test #3, 2026-11-24: the screen showed no total; the tester counted ~413 by scrolling). [listed] is the
 * rows on screen; [suggested] is what «Seleccionar las sugeridas (N)» selects, so the two numbers never disagree.
 */
internal fun subscriptionCleanupTotals(
    candidates: List<PhantomSubscriptionPolicy.Candidate>,
    incomplete: Boolean,
): SubscriptionCleanupTotals = SubscriptionCleanupTotals(
    listed = candidates.size,
    suggested = PhantomSubscriptionPolicy.suggestedSelection(candidates).size,
    atLeast = incomplete,
)
