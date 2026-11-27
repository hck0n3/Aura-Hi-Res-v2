package iad1tya.echo.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.net.Uri
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Registry row 910 — the summary card of «Tu año en Aura» as a picture to share. Drawn on the phone from the texts the
 * screen already shows (no cover is downloaded, nothing is sent): the user's share sheet is the only way out. It is kept
 * in the app's private cache ([saveForShare]), never in the public gallery.
 */
object WrappedImage {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val MARGIN = 96f
    private const val SHARE_FILE = "aura_wrapped.png"

    /**
     * Writes [bitmap] to `cacheDir/images` (the FileProvider's `shared_images` path) under ONE fixed name, overwritten on
     * every share, and returns its content URI. Unlike `ComposeToImage.saveBitmapAsFile` on Android 10+, nothing goes to
     * MediaStore: a cancelled share leaves no picture in the gallery, and other apps read it only through the grant.
     */
    fun saveForShare(context: Context, bitmap: Bitmap): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, SHARE_FILE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    }

    suspend fun render(
        title: String,
        subtitle: String,
        lines: List<String>,
        footer: String,
        topColor: Int,
        bottomColor: Int,
        accentColor: Int,
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val background = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), topColor, bottomColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        val width = (WIDTH - 2 * MARGIN).toInt()
        var y = 260f
        fun block(text: String, size: Float, color: Int, bold: Boolean, gapAfter: Float) {
            if (text.isBlank()) return
            val paint = TextPaint().apply {
                isAntiAlias = true
                textSize = size
                this.color = color
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(3)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            canvas.translate(MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + gapAfter
        }

        block(title, 96f, 0xFFFFFFFF.toInt(), bold = true, gapAfter = 24f)
        block(subtitle, 64f, accentColor, bold = true, gapAfter = 120f)
        for (line in lines) block(line, 52f, 0xFFFFFFFF.toInt(), bold = false, gapAfter = 48f)

        val footerPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 44f
            color = 0xB3FFFFFF.toInt()
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(footer, MARGIN, HEIGHT - MARGIN, footerPaint)
        bitmap
    }
}
