package dev.gabrie.brainwave.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/** Plays back a saved recording. One clip at a time; a new start replaces the old. */
class AudioPlayer {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = player?.isPlaying == true

    fun play(file: File, onFinished: () -> Unit) {
        stop()
        if (!file.exists()) {
            onFinished()
            return
        }
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stop()
                onFinished()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onFinished()
                true
            }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }
}
