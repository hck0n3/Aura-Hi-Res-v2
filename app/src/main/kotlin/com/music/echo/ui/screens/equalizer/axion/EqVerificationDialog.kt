package iad1tya.echo.music.ui.screens.equalizer.axion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.ui.component.AuraAlertDialog

/** Result of the in-engine EQ verification: expected (drawn curve) vs measured (real engine), and headroom. */
@Composable
fun EqVerificationDialog(result: EqVerification, onDismiss: () -> Unit) {
    AuraAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verificación del ecualizador") },
        text = {
            when {
                result.running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Midiendo en el motor…")
                }
                !result.engineReady -> Text(
                    "El motor Superpowered no está cargado ahora mismo. Reproduce una canción con el ecualizador encendido y vuelve a verificar.",
                )
                else -> Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        if (result.responsePasses) "✅ La curva que suena coincide con la dibujada (±0.5 dB)."
                        else "⚠️ Hay frecuencias que no coinciden con la curva dibujada.",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Cell("Frecuencia", 0.3f, bold = true)
                        Cell("Esperado", 0.25f, bold = true)
                        Cell("Medido", 0.25f, bold = true)
                        Cell("", 0.2f, bold = true)
                    }
                    result.rows.forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            Cell(formatHz(row.frequency), 0.3f)
                            Cell(formatDb(row.expectedDb), 0.25f)
                            Cell(formatDb(row.measuredDb), 0.25f)
                            Cell(if (row.passes) "✅" else "⚠️ ${formatDb(row.differenceDb)}", 0.2f)
                        }
                    }
                    Text(" ")
                    Text(
                        if (result.headroomPasses) "✅ Headroom: ruido rosa a −0.1 dBFS nunca llega a 0 dBFS."
                        else "⚠️ Headroom: la salida pasó el techo del limitador.",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("Entrada: ${formatDbfs(result.peakInDb)} · tras EQ: ${formatDbfs(result.peakAfterEqDb)} · salida: ${formatDbfs(result.peakOutDb)}")
                    Text(
                        "«Tras EQ» puede pasar de 0 dBFS con realces fuertes: el limitador (techo −0.3 dBFS) lo contiene en la salida.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, bold: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun formatHz(f: Double): String = if (f >= 1000) "%.1f kHz".format(f / 1000) else "%.0f Hz".format(f)
private fun formatDb(v: Double): String = "%+.1f".format(v)
private fun formatDbfs(v: Float): String = "%.2f dBFS".format(v)
