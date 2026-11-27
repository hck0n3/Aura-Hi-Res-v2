package iad1tya.echo.music.eq.audio

/**
 * Registry row 920: «Graves para altavoces pequeños», the psychoacoustic bass stage of the native chain
 * (`app/src/main/cpp/superpowered/AuraVirtualBass.h`). It removes the bass a phone speaker cannot play and adds its
 * 2nd/3rd harmonics, so the missing fundamental is still heard (Larsen & Aarts, JAES 2002).
 */
object VirtualBass {

    /** Mirrors `kVbMaxExtraDb`: the stage's measured peak growth, which «Headroom automático» takes off the preamp. */
    const val MAX_EXTRA_DB = 2.0

    /**
     * Where the stage may run: the phone's own speaker only. Never headphones, a USB DAC, HDMI, a car or a Bluetooth
     * speaker (those play the real bass; removing it there would be a loss, not a fix).
     */
    fun allowedOn(category: OutputCategory): Boolean = category == OutputCategory.PHONE_SPEAKER
}
