package dev.gabrie.brainwave.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Keeps the mic and the spoken prompts on a connected headset.
 *
 * Wired headsets need nothing — the platform routes to them already. Bluetooth
 * is the awkward case: the headset's microphone only exists on the SCO link,
 * which has to be requested explicitly, and the way to request it changed in
 * API 31.
 */
class HeadsetAudioRouter(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode: Int = AudioManager.MODE_NORMAL
    private var engaged = false

    fun headsetConnected(): Boolean = audioManager
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { it.type in HEADSET_OUTPUT_TYPES }

    fun bluetoothHeadsetConnected(): Boolean = audioManager
        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .any { it.type in BLUETOOTH_OUTPUT_TYPES }

    /** Call before recording or speaking; safe to call when already engaged. */
    @Suppress("DEPRECATION")
    fun engage() {
        if (engaged) return
        engaged = true
        previousMode = audioManager.mode

        if (!bluetoothHeadsetConnected()) return

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasBluetoothPermission()) return
            val sco = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            if (sco != null) runCatching { audioManager.setCommunicationDevice(sco) }
        } else {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }
    }

    @Suppress("DEPRECATION")
    fun release() {
        if (!engaged) return
        engaged = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        } else {
            runCatching {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
        }
        audioManager.mode = previousMode
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        val BLUETOOTH_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        )
        val HEADSET_OUTPUT_TYPES = BLUETOOTH_OUTPUT_TYPES + setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
