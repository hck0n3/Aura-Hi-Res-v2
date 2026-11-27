package iad1tya.echo.music.eq.audio

import android.bluetooth.BluetoothClass
import android.media.AudioDeviceInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Row 919: a Bluetooth speaker, soundbar or car is not a pair of headphones. Pins the classification table (Bluetooth
 * class → category, name fallback, device type, route priority) and that the headphone-only spatial effects and the
 * phone-speaker protection are gated by the category.
 */
class OutputClassifierTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile } ?: error("repo root not found")
    private fun source(path: String) = File(root, path).also { assertTrue("$path moved", it.isFile) }.readText()

    private val a2dp = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP

    @Test
    fun `the Bluetooth class constants are the SDK values the table was written against`() {
        // Read with javap from android-37's android.jar (BluetoothClass$Device).
        assertEquals(1028, BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET)
        assertEquals(1032, BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE)
        assertEquals(1044, BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER)
        assertEquals(1048, BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES)
        assertEquals(1052, BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO)
        assertEquals(1056, BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO)
        assertEquals(1060, BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX)
        assertEquals(1064, BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO)
        assertEquals(1080, BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR)
        assertEquals(1084, BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER)
    }

    @Test
    fun `each Bluetooth class maps to its category`() {
        val table = mapOf(
            1048 to OutputCategory.HEADPHONES_BT, // HEADPHONES
            1028 to OutputCategory.HEADPHONES_BT, // WEARABLE_HEADSET
            1044 to OutputCategory.SPEAKER_BT, // LOUDSPEAKER
            1064 to OutputCategory.SPEAKER_BT, // HIFI_AUDIO
            1084 to OutputCategory.SPEAKER_BT, // VIDEO_DISPLAY_AND_LOUDSPEAKER (a TV)
            1060 to OutputCategory.SPEAKER_BT, // SET_TOP_BOX
            1080 to OutputCategory.SPEAKER_BT, // VIDEO_MONITOR
            1056 to OutputCategory.CAR, // CAR_AUDIO
        )
        for ((deviceClass, expected) in table) {
            assertEquals("class $deviceClass", expected, OutputClassifier.fromBluetoothClass(deviceClass))
            // A class that says what it is wins over a misleading name.
            assertEquals("class $deviceClass", expected, OutputClassifier.classify(a2dp, deviceClass, "Buds Speaker Car"))
        }
        // Ambiguous classes and other majors decide nothing: the name (or the old default) does.
        for (deviceClass in listOf(null, 1024, 1032, 1052, 0x0100, 0x0200, 0x0704)) {
            assertNull("class $deviceClass", OutputClassifier.fromBluetoothClass(deviceClass))
        }
    }

    @Test
    fun `without a usable class the name decides, and an unknown name stays headphones as before`() {
        val cases = mapOf(
            "JBL Flip 5" to OutputCategory.SPEAKER_BT,
            "JBL Charge 4" to OutputCategory.SPEAKER_BT,
            "UE BOOM 3" to OutputCategory.SPEAKER_BT,
            "SRS-XB13" to OutputCategory.SPEAKER_BT,
            "[TV] Samsung Q60 Series" to OutputCategory.SPEAKER_BT,
            "Samsung Soundbar Q60T" to OutputCategory.SPEAKER_BT,
            "LG Sound Bar SN5Y" to OutputCategory.SPEAKER_BT,
            "Bose SoundLink Flex" to OutputCategory.SPEAKER_BT,
            "Bose SoundLink Around-Ear II" to OutputCategory.HEADPHONES_BT,
            "WH-1000XM4" to OutputCategory.HEADPHONES_BT,
            "LE_WF-1000XM5" to OutputCategory.HEADPHONES_BT,
            "AirPods Pro" to OutputCategory.HEADPHONES_BT,
            "My Car" to OutputCategory.CAR,
            "Toyota Corolla" to OutputCategory.CAR,
            "CAR MULTIMEDIA" to OutputCategory.CAR,
        )
        for ((name, expected) in cases) {
            assertEquals(name, expected, OutputClassifier.classify(a2dp, null, name))
            // Hands-free / portable audio are ambiguous: the name decides there too.
            assertEquals(name, expected, OutputClassifier.classify(a2dp, 1032, name))
        }
        // Whole words only: «car» inside «Scarlett», «charge» inside «Discharge» are not hints.
        for (name in listOf("Scarlett 2i2", "Discharge", "Galaxy Buds2 Pro", "Tune 510BT", "", null)) {
            assertNull("$name", OutputClassifier.fromName(name))
            assertEquals("$name", OutputCategory.HEADPHONES_BT, OutputClassifier.classify(a2dp, null, name))
        }
        assertEquals(OutputCategory.HEADPHONES_BT, OutputClassifier.BLUETOOTH_DEFAULT)
        assertEquals(OutputCategory.SPEAKER_BT, OutputClassifier.classify(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, 1044, null))
    }

    @Test
    fun `every other output type has its category`() {
        val table = mapOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to OutputCategory.PHONE_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE to OutputCategory.PHONE_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES to OutputCategory.HEADPHONES_WIRED,
            AudioDeviceInfo.TYPE_WIRED_HEADSET to OutputCategory.HEADPHONES_WIRED,
            AudioDeviceInfo.TYPE_USB_HEADSET to OutputCategory.HEADPHONES_WIRED,
            AudioDeviceInfo.TYPE_USB_DEVICE to OutputCategory.USB_DAC,
            AudioDeviceInfo.TYPE_HDMI to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_HDMI_EARC to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_AUX_LINE to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_LINE_ANALOG to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_LINE_DIGITAL to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_DOCK to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_DOCK_ANALOG to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_BLE_SPEAKER to OutputCategory.SPEAKER_BT,
            AudioDeviceInfo.TYPE_BLE_BROADCAST to OutputCategory.SPEAKER_BT,
            AudioDeviceInfo.TYPE_BLE_HEADSET to OutputCategory.HEADPHONES_BT,
            AudioDeviceInfo.TYPE_HEARING_AID to OutputCategory.HEADPHONES_BT,
            AudioDeviceInfo.TYPE_BLE_HEARING_AID to OutputCategory.HEADPHONES_BT,
        )
        for ((type, expected) in table) {
            // The Bluetooth class and the name are only read for classic Bluetooth (A2DP / SCO).
            assertEquals("type $type", expected, OutputClassifier.classify(type, 1044, "JBL Flip 5"))
        }
        for (type in listOf(AudioDeviceInfo.TYPE_BUILTIN_MIC, AudioDeviceInfo.TYPE_TELEPHONY, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)) {
            assertNull("type $type", OutputClassifier.classify(type, null, null))
        }
    }

    @Test
    fun `the route keeps the old priority and now carries the category`() {
        fun c(type: Int, category: OutputCategory) = OutputClassifier.Candidate(type, category)
        val phone = c(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, OutputCategory.PHONE_SPEAKER)
        assertEquals(OutputCategory.PHONE_SPEAKER, OutputClassifier.route(emptyList()))
        assertEquals(OutputCategory.PHONE_SPEAKER, OutputClassifier.route(listOf(phone)))
        assertEquals(OutputCategory.SPEAKER_BT, OutputClassifier.route(listOf(phone, c(a2dp, OutputCategory.SPEAKER_BT))))
        assertEquals(OutputCategory.CAR, OutputClassifier.route(listOf(phone, c(a2dp, OutputCategory.CAR))))
        assertEquals(
            OutputCategory.HEADPHONES_BT,
            OutputClassifier.route(listOf(c(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, OutputCategory.HEADPHONES_WIRED), c(a2dp, OutputCategory.HEADPHONES_BT))),
        )
        assertEquals(
            OutputCategory.HEADPHONES_WIRED,
            OutputClassifier.route(listOf(c(AudioDeviceInfo.TYPE_HDMI, OutputCategory.HDMI), c(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, OutputCategory.HEADPHONES_WIRED))),
        )
        assertEquals(OutputCategory.USB_DAC, OutputClassifier.route(listOf(phone, c(AudioDeviceInfo.TYPE_USB_DEVICE, OutputCategory.USB_DAC))))
        assertEquals(OutputCategory.HDMI, OutputClassifier.route(listOf(c(AudioDeviceInfo.TYPE_BLE_SPEAKER, OutputCategory.SPEAKER_BT), c(AudioDeviceInfo.TYPE_HDMI_EARC, OutputCategory.HDMI))))
        assertEquals(OutputCategory.SPEAKER_BT, OutputClassifier.route(listOf(phone, c(AudioDeviceInfo.TYPE_BLE_SPEAKER, OutputCategory.SPEAKER_BT))))
    }

    @Test
    fun `a hearing aid, a broadcast or a line out next to the phone's speaker is not the phone's speaker`() {
        fun c(type: Int, category: OutputCategory) = OutputClassifier.Candidate(type, category)
        val phone = c(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, OutputCategory.PHONE_SPEAKER)
        val expected = mapOf(
            AudioDeviceInfo.TYPE_HEARING_AID to OutputCategory.HEADPHONES_BT,
            AudioDeviceInfo.TYPE_BLE_HEARING_AID to OutputCategory.HEADPHONES_BT,
            AudioDeviceInfo.TYPE_BLE_BROADCAST to OutputCategory.SPEAKER_BT,
            AudioDeviceInfo.TYPE_LINE_ANALOG to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_LINE_DIGITAL to OutputCategory.HDMI,
            AudioDeviceInfo.TYPE_DOCK_ANALOG to OutputCategory.HDMI,
        )
        for ((type, category) in expected) {
            // The route uses the category classify() gives the type, whatever the order the system lists them in.
            assertEquals("type $type", category, OutputClassifier.classify(type, null, null))
            assertEquals("type $type", category, OutputClassifier.route(listOf(phone, c(type, category))))
            assertEquals("type $type", category, OutputClassifier.route(listOf(c(type, category), phone)))
            // So neither phone-speaker effect (psychoacoustic bass, speaker protection) is armed there.
            assertFalse("type $type", VirtualBass.allowedOn(OutputClassifier.route(listOf(phone, c(type, category)))))
        }
        // Every output type classify() puts in another category wins over the built-in speaker.
        for (type in 0..64) {
            val category = OutputClassifier.classify(type, null, null) ?: continue
            if (category == OutputCategory.PHONE_SPEAKER) continue
            assertEquals("type $type", category, OutputClassifier.route(listOf(phone, c(type, category))))
        }
        // A type the priority does not list still beats the phone's speaker.
        assertEquals(OutputCategory.HDMI, OutputClassifier.route(listOf(phone, c(9999, OutputCategory.HDMI))))
        // Classic Bluetooth keeps the first place in its group, ahead of a hearing aid listed before it.
        assertEquals(
            OutputCategory.SPEAKER_BT,
            OutputClassifier.route(listOf(c(AudioDeviceInfo.TYPE_HEARING_AID, OutputCategory.HEADPHONES_BT), c(a2dp, OutputCategory.SPEAKER_BT))),
        )
    }

    @Test
    fun `headphone-only spatial effects run only on headphones`() {
        val headphoneLike = setOf(OutputCategory.HEADPHONES_WIRED, OutputCategory.HEADPHONES_BT, OutputCategory.USB_DAC)
        for (category in OutputCategory.values()) {
            val kind = OutputClassifier.spatialKind(category)
            for (profile in SpatialAudioProfile.values()) {
                val algorithm = SpatialAudioProfile.nativeAlgorithm(profile, kind)
                if (category !in headphoneLike) {
                    assertTrue("$category / $profile", algorithm == SpatialAudioProfile.ALGO_SPEAKER_MS || kind == SpatialOutputKind.BYPASS)
                }
            }
        }
        assertEquals(SpatialOutputKind.SPEAKER, OutputClassifier.spatialKind(OutputCategory.SPEAKER_BT))
        assertEquals(SpatialOutputKind.SPEAKER, OutputClassifier.spatialKind(OutputCategory.PHONE_SPEAKER))
        assertEquals(SpatialOutputKind.BYPASS, OutputClassifier.spatialKind(OutputCategory.CAR))
        assertEquals(SpatialOutputKind.BYPASS, OutputClassifier.spatialKind(OutputCategory.HDMI))
        for (category in headphoneLike) assertEquals(SpatialOutputKind.HEADPHONE, OutputClassifier.spatialKind(category))
        assertEquals(
            setOf(OutputCategory.HEADPHONES_WIRED, OutputCategory.HEADPHONES_BT),
            OutputCategory.values().filter { OutputClassifier.isHeadphones(it) }.toSet(),
        )
    }

    @Test
    fun `the live paths use the category, and the device name never reaches a log`() {
        val spatial = source("app/src/main/kotlin/com/music/echo/eq/audio/SpatialAudio.kt")
        assertTrue(spatial.contains("OutputClassifier.spatialKind(OutputClassifier.detect(context))"))
        assertFalse("the A2DP = headphones shortcut is back", spatial.contains("TYPE_BLUETOOTH_A2DP"))
        val service = source("app/src/main/kotlin/com/music/echo/playback/MusicService.kt")
        val mastering = service.substringAfter("private fun applyMasteringFromPrefs() {").substringBefore("\n    }\n")
        assertTrue(mastering.contains("OutputClassifier.detect(this) == iad1tya.echo.music.eq.audio.OutputCategory.PHONE_SPEAKER"))
        assertFalse(mastering.contains("SpatialOutputKind.SPEAKER"))
        val spatialApply = service.substringAfter("private fun applySpatialFromPrefs() {").substringBefore("\n    }\n")
        assertTrue(spatialApply.contains("SpatialAudioProfile.detectOutputKind(this)"))
        assertTrue(spatialApply.contains("SpatialAudioProfile.nativeAlgorithm(profile, kind)"))
        val store = source("app/src/main/kotlin/com/music/echo/eq/data/EqDeviceProfileStore.kt")
        assertTrue(store.contains("OutputClassifier.categoryOf(context, this) ?: OutputClassifier.BLUETOOTH_DEFAULT"))
        val offer = source("app/src/main/kotlin/com/music/echo/ui/screens/equalizer/autoeq/AutoEqDeviceOffer.kt")
        assertTrue(offer.contains("OutputClassifier.isHeadphones(output.category)"))
        val classifier = source("app/src/main/kotlin/com/music/echo/eq/audio/OutputCategory.kt")
        for (logger in listOf("Timber", "AppLogger", "Log.", "println")) assertFalse(logger, classifier.contains(logger))
        // BLUETOOTH_CONNECT is checked before the class is read (API 31+).
        assertTrue(classifier.contains("ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)"))
    }
}
