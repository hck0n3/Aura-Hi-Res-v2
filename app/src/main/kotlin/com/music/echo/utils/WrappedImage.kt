package iad1tya.echo.music.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Registry row 910 — the summary card of «Tu año en Aura» as a picture to share. Drawn on the phone from the texts the
 * screen already shows (no cover is downloaded, nothing is sent): the user's share sheet is the only way out, and it is
 * saved with [ComposeToImage.saveBitmapAsFile], the same as the lyrics image.
 */
object WrappedImage {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val MARGIN = 96f

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
