package iad1tya.echo.music.utils

/**
 * Plan, phase 6.1 (registry row 895): «Tipo de aparato» — what the user picked in Ajustes ▸ Apariencia. [AUTO] is the
 * default and keeps the detection below. Stored by NAME in [iad1tya.echo.music.constants.DeviceFormChoiceKey]: never rename
 * a constant, a saved choice would fall back to [AUTO].
 */
enum class DeviceFormChoice { AUTO, PHONE, TABLET, FOLDABLE, TV }

/** The kind of device the interface dresses for. [CAR] is only ever detected (there is no manual «coche»). */
enum class DeviceFormFactor { PHONE, TABLET, FOLDABLE, TV, CAR }

/**
 * Pure (no Android, no Compose) so every combination is tested on the JVM. The Compose side reads it once at the root
 * (`ProvideDeviceForm`, ui/utils/DeviceFormUi.kt) and the shell asks it through `rememberIsTvShell`, `rememberIsTvOrCar`
 * and `rememberIsWideShell`.
 */
object DeviceFormResolution {

    /**
     * What the device looks like right now. [isTelevision] / [isCar] are [DeviceForm]'s system answers; [hasHinge] is "the
     * window manager reports a fold for this window" (an unfolded foldable; folded on its outer screen it reports none and
     * is a phone); [wideWindow] is the wide-chrome window test (`isWideLayoutWindow`: >= 600 x 480 dp).
     */
    fun detect(isTelevision: Boolean, isCar: Boolean, hasHinge: Boolean, wideWindow: Boolean): DeviceFormFactor = when {
        isTelevision -> DeviceFormFactor.TV
        isCar -> DeviceFormFactor.CAR
        hasHinge -> DeviceFormFactor.FOLDABLE
        wideWindow -> DeviceFormFactor.TABLET
        else -> DeviceFormFactor.PHONE
    }

    /** The form the interface uses: the detected one in [DeviceFormChoice.AUTO], otherwise the user's choice. */
    fun effective(choice: DeviceFormChoice, detected: DeviceFormFactor): DeviceFormFactor = when (choice) {
        DeviceFormChoice.AUTO -> detected
        DeviceFormChoice.PHONE -> DeviceFormFactor.PHONE
        DeviceFormChoice.TABLET -> DeviceFormFactor.TABLET
        DeviceFormChoice.FOLDABLE -> DeviceFormFactor.FOLDABLE
        DeviceFormChoice.TV -> DeviceFormFactor.TV
    }

    /** The TV shell (side rail, full-screen player with «Pantalla») is the TV form's, detected or chosen. */
    fun usesTvShell(form: DeviceFormFactor): Boolean = form == DeviceFormFactor.TV

    /**
     * The wide chrome (capsule bar, split player, cards). [DeviceFormChoice.AUTO] and [DeviceFormChoice.FOLDABLE] keep
     * today's window rule ([wideWindow]: a foldable is wide open and a phone folded); a manual phone never takes it, a
     * manual tablet always does, and the TV form always does (its shell draws a rail instead of the capsule).
     */
    fun wideShell(choice: DeviceFormChoice, form: DeviceFormFactor, wideWindow: Boolean): Boolean = when {
        form == DeviceFormFactor.TV -> true
        choice == DeviceFormChoice.PHONE -> false
        choice == DeviceFormChoice.TABLET -> true
        else -> wideWindow
    }

    /**
     * The D-pad affordances (focus ring, initial focus, never auto-hiding controls). They answer "is this driven by a
     * remote", so a manual TV turns them on and no manual choice ever turns them off: a real TV box set to «Tablet» still
     * has only a remote.
     */
    fun remoteAffordances(choice: DeviceFormChoice, detectedRemote: Boolean): Boolean =
        detectedRemote || choice == DeviceFormChoice.TV
}
