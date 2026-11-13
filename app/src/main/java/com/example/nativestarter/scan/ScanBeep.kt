package com.example.nativestarter.scan

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.example.nativestarter.ime.Haptics

/**
 * Multi-modal capture feedback: tone, vibration, and (from the camera side) the
 * torch pulse.
 *
 * [ToneGenerator] is used because it needs no audio asset and works on every
 * device/API level with near-zero latency. Each call tries to shut the generator
 * down afterwards so a long session does not hold an audio handle open.
 */
object ScanBeep {

    private const val VOLUME = 85

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

    /**
     * Success cue. The tone rises with the payload length so a long serial and a
     * six-digit code are distinguishable by ear without looking at the screen.
     */
    @Synchronized
    fun success(context: Context, scanLength: Int = 0) {
        val tone = when {
            scanLength == 0 -> ToneGenerator.TONE_PROP_BEEP
            scanLength <= 8 -> ToneGenerator.TONE_PROP_BEEP
            scanLength <= 24 -> ToneGenerator.TONE_PROP_ACK
            else -> ToneGenerator.TONE_PROP_PROMPT
        }
        val duration = if (scanLength > 24) 160 else 120
        play(tone, duration)
        Haptics.success(context)
    }

    /** Failure cue used when the camera fails to start or an error is raised. */
    @Synchronized
    fun failure(context: Context) {
        play(ToneGenerator.TONE_PROP_NACK, 220)
        Haptics.error(context)
    }

    /** Generic double beep for secondary confirmations (clipboard copy, etc.). */
    @Synchronized
    fun confirm(context: Context) {
        play(ToneGenerator.TONE_PROP_ACK, 90)
        Haptics.warning(context)
    }

    private fun play(tone: Int, durationMs: Int) {
        val tg = acquire() ?: return
        try {
            tg.startTone(tone, durationMs)
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
