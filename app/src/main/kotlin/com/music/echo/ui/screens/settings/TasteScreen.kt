package iad1tya.echo.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.reco.TasteSummary
import iad1tya.echo.music.ui.component.AuraTopAppBar
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog
import iad1tya.echo.music.viewmodels.TasteViewModel

/**
 * Registry row 906 — Ajustes ▸ IA ▸ «Tu gusto». The summary made on the phone from the user's own plays (always there, no
 * network) and, below, the optional AI description: off until the user accepts a consent that lists exactly what is sent
 * ([TasteSummary.payload]); then only the «Describir con IA» button sends it, once per tap.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TasteScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val viewModel: TasteViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val accepted by viewModel.consentAccepted.collectAsState()
    var showConsent by remember { mutableStateOf(false) }
    val screenTitle = stringResource(R.string.taste_title)

    Scaffold(
        topBar = {
            AuraTopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = { ActionLog.tap(ActionId.TASTE_BACK); navController.navigateUp() }, onLongClick = null) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val local = state.local
            when {
                state.loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.taste_loading))
                    }
                }
                local == null || state.stats.isEmpty -> item { Text(stringResource(R.string.taste_empty)) }
                else -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.taste_local_title), style = MaterialTheme.typography.titleSmall)
                        Text(localParagraph(local), style = MaterialTheme.typography.bodyLarge)
                        TasteChips(local.chips.map { chipLabel(it) })
                        Text(
                            stringResource(R.string.taste_local_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!state.loading && !state.stats.isEmpty) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.taste_ai_title), style = MaterialTheme.typography.titleSmall)
                        if (!accepted) {
                            Text(
                                stringResource(R.string.taste_ai_off),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = { ActionLog.tap(ActionId.TASTE_AI_REVIEW); showConsent = true }) {
                                Text(stringResource(R.string.taste_ai_review))
                            }
                        } else {
                            when (val ai = state.ai) {
                                TasteViewModel.Ai.Asking -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(R.string.taste_ai_asking))
                                }
                                is TasteViewModel.Ai.Done -> {
                                    Text(ai.taste.summary, style = MaterialTheme.typography.bodyLarge)
                                    TasteChips(ai.taste.chips)
                                    Text(
                                        stringResource(R.string.taste_ai_result_note),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TasteViewModel.Ai.Failed -> Text(
                                    stringResource(R.string.taste_ai_failed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                TasteViewModel.Ai.Idle -> Unit
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { ActionLog.tap(ActionId.TASTE_AI_ASK); viewModel.askAi() },
                                    enabled = state.ai != TasteViewModel.Ai.Asking,
                                ) { Text(stringResource(R.string.taste_ai_ask)) }
                                TextButton(onClick = { ActionLog.tap(ActionId.TASTE_AI_WITHDRAW); viewModel.withdrawConsent() }) {
                                    Text(stringResource(R.string.taste_ai_withdraw))
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showConsent) {
        val consentTitle = stringResource(R.string.taste_consent_title)
        val preview = consentPreview(state.stats)
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text(consentTitle) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(stringResource(R.string.taste_consent_body)) }
                    if (preview.isNotEmpty()) {
                        item { Text(stringResource(R.string.taste_consent_now, preview), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ActionLog.tap(ActionId.TASTE_CONSENT_ACCEPT)
                    showConsent = false
                    viewModel.acceptConsent()
                }) { Text(stringResource(R.string.taste_consent_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { ActionLog.tap(ActionId.TASTE_CONSENT_DECLINE); showConsent = false }) {
                    Text(stringResource(R.string.taste_consent_decline))
                }
            },
        )
    }
}

/** The local summary in a few sentences, only with the parts there is data for. */
@Composable
private fun localParagraph(local: TasteSummary.Local): String {
    val genres = if (local.genres.isEmpty()) null else stringResource(R.string.taste_local_genres, local.genres.joinToString(", "))
    val artists = if (local.artists.isEmpty()) null else stringResource(R.string.taste_local_artists, local.artists.joinToString(", "))
    val decade = local.decade?.let { stringResource(R.string.taste_local_decade, stringResource(R.string.taste_decade, it)) }
    return listOfNotNull(genres, artists, decade).joinToString(" ")
}

@Composable
private fun chipLabel(chip: TasteSummary.Chip): String =
    chip.decade?.let { stringResource(R.string.taste_decade, it) } ?: chip.label

/** Exactly the names and numbers [TasteSummary.payload] would carry now, for the consent screen. */
@Composable
private fun consentPreview(stats: TasteSummary.Stats): String = listOfNotNull(
    stats.artists.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${it.name} (${it.count})" },
    stats.genres.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${it.name} (${it.count})" },
    stats.decades.takeIf { it.isNotEmpty() }?.let { list ->
        list.map { stringResource(R.string.taste_decade, it.decade) + " (${it.count})" }.joinToString(", ")
    },
).joinToString(" · ")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TasteChips(labels: List<String>) {
    if (labels.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { label ->
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
