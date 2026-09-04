package ir.seam.player.playback

import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer

/** Small lifecycle-safe wrapper for Android's built-in audio effects. */
class AudioEnhancer {
    private var equalizer: Equalizer? = null
    private var visualizer: Visualizer? = null

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return
        equalizer = runCatching { Equalizer(0, audioSessionId).apply { enabled = true } }.getOrNull()
        visualizer = runCatching { Visualizer(audioSessionId).apply { captureSize = Visualizer.getCaptureSizeRange()[0]; enabled = true } }.getOrNull()
    }

    fun setPreset(name: String) {
        val eq = equalizer ?: return
        val preset = when (name.lowercase()) {
            "bass" -> 1
            "vocal" -> 2
            "rock" -> 3
            "pop" -> 4
            "classical" -> 5
            else -> 0
        }
        runCatching { if (preset in 0 until eq.numberOfPresets) eq.usePreset(preset.toShort()) }
    }

    fun release() {
        runCatching { visualizer?.release() }
        runCatching { equalizer?.release() }
        visualizer = null
        equalizer = null
    }
}
