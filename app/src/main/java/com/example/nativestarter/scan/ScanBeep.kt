package com.example.nativestarter.scan

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Short confirmation beep fired when a barcode is decoded.
 *
 * [ToneGenerator] is used because it needs no audio asset and works on every
 * device/API level with near-zero latency.
 */
object ScanBeep {

    private const val DURATION_MS = 120
    private const val VOLUME = 85
    private const val TONE = ToneGenerator.TONE_PROP_BEEP

    @Volatile
    private var generator: ToneGenerator? = null

    @Synchronized
    private fun acquire(): ToneGenerator? {
        val existing = generator
        if (existing != null) return existing
        return try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME).also { generator = it }
        } catch (_: RuntimeException) {
            null
        }
    }

    @Synchronized
    fun play() {
        val tone = acquire() ?: return
        try {
            tone.startTone(TONE, DURATION_MS)
        } catch (_: RuntimeException) {
            release()
        }
    }

    @Synchronized
    fun release() {
        try {
            generator?.release()
        } catch (_: RuntimeException) {
            // Ignore: release is best-effort during teardown.
        } finally {
            generator = null
        }
    }
}
