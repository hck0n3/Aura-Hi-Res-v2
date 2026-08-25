package iad1tya.echo.music.utils

import java.io.File

/** One row in the shared import-failures CSV (Title, Artists, Album, Source, Reason). */
data class ImportFailureRow(
    val title: String,
    val artists: String,
    val album: String = "",
    val source: String,
    val reason: String,
)

object ImportFailuresCsv {
    fun buildCsv(failures: List<ImportFailureRow>): String {
        val esc = { v: String -> "\"" + v.replace("\"", "\"\"") + "\"" }
        val sb = StringBuilder("Title,Artists,Album,Source,Reason\n")
        failures.forEach { f ->
            sb.append(esc(f.title)).append(',')
                .append(esc(f.artists)).append(',')
                .append(esc(f.album)).append(',')
                .append(esc(f.source)).append(',')
                .append(esc(f.reason)).append('\n')
        }
        return sb.toString()
    }

    fun write(
        cacheDir: File,
        failures: List<ImportFailureRow>,
        filePrefix: String = "import_failures",
    ): File {
        // HALLAZGO-015 (FASE 22): provider_paths.xml only exposes the csv_exports/ subfolder of
        // cacheDir, so shared CSVs must live there.
        val dir = File(cacheDir, "csv_exports").apply { mkdirs() }
        val file = File(dir, "${filePrefix}_${System.currentTimeMillis()}.csv")
        file.writeText(buildCsv(failures))
        return file
    }
}
