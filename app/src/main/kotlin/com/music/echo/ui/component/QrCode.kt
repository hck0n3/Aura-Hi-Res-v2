package iad1tya.echo.music.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Row 615 (owner 2026-10-31: «QR para emparejar y para Escuchar juntos»): the QR of [text], black on white with its quiet zone, as a bitmap.
 * Pure drawing of the text it is given — no network, no camera. Null when the text does not fit a QR.
 */
fun qrBitmap(text: String, sizePx: Int = 512): Bitmap? = try {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) pixels[y * matrix.width + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
} catch (_: Exception) {
    null
}

/** The QR of [text] on a white card (a QR needs a light ground to be read), [size] wide. */
@Composable
fun QrImage(text: String, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val bitmap = remember(text) { qrBitmap(text) } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier.size(size).background(Color.White).padding(6.dp),
    )
}
