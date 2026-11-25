package iad1tya.echo.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.OnboardingGenresKey
import iad1tya.echo.music.ui.screens.Screens
import iad1tya.echo.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import iad1tya.echo.music.utils.ActionId
import iad1tya.echo.music.utils.ActionLog

private val GENRES = listOf(
    "Pop", "Rock", "Hip-Hop / Rap", "R&B", "Electrónica", "Reggaetón", "Latina",
    "Trap", "K-Pop", "Indie", "Jazz", "Clásica", "Country", "Metal", "Cristiana / Gospel",
    "Salsa", "Bachata", "Banda / Regional", "Blues", "Folk", "Punk", "Soul", "Funk", "Lo-fi",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingGenresScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<String>()) }
    // Row 871: opened from Ajustes ▸ Contenido now, so it starts from what is saved — «Guardar» must never wipe the
    // genres picked earlier just because the screen opened empty.
    LaunchedEffect(Unit) {
        val saved = context.dataStore.data.first()[OnboardingGenresKey].orEmpty()
            .split(',').map { it.trim() }.filter { it in GENRES }.toSet()
        if (selected.isEmpty()) selected = saved
    }

    fun goBack() {
        navController.popBackStack()
    }

    fun persistAndContinue() {
        scope.launch {
            context.dataStore.edit { it[OnboardingGenresKey] = selected.joinToString(",") }
            goBack()
        }
    }

    Scaffold(
        topBar = {
            iad1tya.echo.music.ui.component.AuraTopAppBar(title = {
                Column {
                    Text("¿Qué géneros te gustan?", fontWeight = FontWeight.Bold)
                    Text(
                        "Opcional — ayuda a afinar tus recomendaciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            })
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Button(
                    onClick = { ActionLog.tap(ActionId.ONBGEN_CONTINUE); persistAndContinue() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Text(stringResource(R.string.save) + if (selected.isEmpty()) "" else " (${selected.size})")
                }
                TextButton(
                    onClick = { ActionLog.tap(ActionId.ONBGEN_SKIP); goBack() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) { padding ->
        FlowRow(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GENRES.forEach { genre ->
                FilterChip(
                    selected = genre in selected,
                    onClick = {
                        ActionLog.tap(ActionId.ONBGEN_GENRE)
                        selected = if (genre in selected) selected - genre else selected + genre
                    },
                    label = { Text(genre) },
                )
            }
        }
    }
}
