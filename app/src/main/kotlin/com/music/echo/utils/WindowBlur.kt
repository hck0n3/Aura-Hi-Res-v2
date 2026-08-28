package iad1tya.echo.music.utils

import android.content.res.Resources
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/**
 * Window-level backdrop blur support (`Window.setBackgroundBlurRadius` /
 * `FLAG_BLUR_BEHIND`) — a DIFFERENT capability from
 * [iad1tya.echo.music.ui.component.isGlassSupported], which gates the in-app RenderEffect
 * pipeline. The RenderEffect path works on Samsung; this one does not.
 *
 * ## Why sdkInt >= S is not enough (HALLAZGO-034)
 * Samsung ships the framework with `config_windowBlurEnabled=false`: every window blur
 * request becomes a SILENT no-op — no exception, no callback, the window simply never
 * blurs. `AuraPalette.FrostFill`'s 0.34 alpha assumes blur behind it, so on Samsung every
 * dialog, sheet and menu rendered as a nearly transparent plate ("todo me sale transparente
 * sin blur", owner's S26 Ultra). The gate has to ask the framework config, not the API level.
 */

/**
 * Pure decision behind [isWindowBlurSupported]: window blur needs API 31+, and the OEM's
 * `config_windowBlurEnabled` must not be false. `null` means the framework resource could
 * not be read: AOSP's default for that config is true, but Samsung One UI does not expose
 * it as a framework resource at all (`getIdentifier` returns 0) while shipping blur
 * DISABLED — so on Samsung unreadable counts as unsupported (HALLAZGO-034 H2: the old
 * null→true default kept every frost plate at 0.34 alpha over windows that never blurred,
 * the "transparente sin blur" on the owner's S26 Ultra).
 */
fun windowBlurDecision(sdkInt: Int, frameworkConfigEnabled: Boolean?, manufacturer: String): Boolean =
    sdkInt >= Build.VERSION_CODES.S && when (frameworkConfigEnabled) {
        true -> true
        false -> false
        null -> !manufacturer.equals("samsung", ignoreCase = true)
    }

/**
 * Whether this device actually processes window blur requests. The framework config is
 * fixed at build time, so the lookup below runs once per process.
 */
fun isWindowBlurSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
    windowBlurDecision(sdkInt, systemWindowBlurConfig, Build.MANUFACTURER).also { supported ->
        logWindowBlurDecisionOnce(sdkInt, systemWindowBlurConfig, Build.MANUFACTURER, supported)
    }

private val windowBlurDecisionLogged = AtomicBoolean(false)

/**
 * Device-only facts (no user data) persisted once per process, so the owner's next app.log
 * settles whether the detector or the scrims caused the "transparente sin blur" report
 * (HALLAZGO-034 H2).
 */
private fun logWindowBlurDecisionOnce(
    sdkInt: Int,
    config: Boolean?,
    manufacturer: String,
    supported: Boolean,
) {
    if (windowBlurDecisionLogged.compareAndSet(false, true)) {
        Timber.tag("WindowBlur")
            .i("sdk=%d config=%s manufacturer=%s supported=%b", sdkInt, config, manufacturer, supported)
    }
}

/**
 * The framework's `config_windowBlurEnabled` value, or null when it cannot be read
 * (resource absent, or [Resources.getSystem()] unavailable). What null MEANS (AOSP default
 * enabled vs Samsung ships blur disabled) is decided by [windowBlurDecision], not here.
 */
private val systemWindowBlurConfig: Boolean? by lazy {
    try {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier("config_windowBlurEnabled", "bool", "android")
        if (id == 0) null else resources.getBoolean(id)
    } catch (_: Throwable) {
        null
    }
}
