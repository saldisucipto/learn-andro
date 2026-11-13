package com.example.nativestarter.ime

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Single entry point for every tactile cue the keyboard and scanner emit.
 *
 * Values are kept short so a fast typist never feels the phone lag behind the
 * finger. All calls are best-effort: a device without a vibrator, or with the
 * system haptics setting off, simply stays silent instead of throwing.
 */
object Haptics {

    /**
     * Plain keypress tick — matches the stock AOSP keyboard feel.
     *
     * No [HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING]: if the user turned
     * touch vibration off system-wide, the keyboard must respect that.
     */
    fun tap(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** Long-press acknowledgement, fired when the alternate popup opens. */
    fun longPress(view: View?) {
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun success(context: Context) {
        vibrate(context, longArrayOf(0L, 18L, 40L, 26L), intArrayOf(0, 120, 0, 190))
    }

    fun warning(context: Context) {
        vibrate(context, longArrayOf(0L, 30L, 60L, 30L), intArrayOf(0, 200, 0, 200))
    }

    fun error(context: Context) {
        vibrate(context, longArrayOf(0L, 60L, 50L, 60L, 50L, 60L), intArrayOf(0, 255, 0, 255, 0, 255))
    }

    private fun vibrate(context: Context, timings: LongArray, amplitudes: IntArray) {
        val vibrator = systemVibrator(context) ?: return
        try {
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (t: Throwable) {
            /* Haptics are cosmetic; never let them break input. */
        }
    }

    private fun systemVibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (t: Throwable) {
        null
    }
}
