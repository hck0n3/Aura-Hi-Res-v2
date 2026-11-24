package iad1tya.echo.music.listentogether

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import iad1tya.echo.music.R

/** Row 848: why a guest's shuffle did nothing («En una sala solo el anfitrión decide el orden»). Safe from any thread. */
fun explainGuestShuffleLocked(context: Context) {
    val app = context.applicationContext
    Handler(Looper.getMainLooper()).post {
        runCatching { Toast.makeText(app, R.string.listen_together_guest_shuffle_locked, Toast.LENGTH_SHORT).show() }
    }
}
