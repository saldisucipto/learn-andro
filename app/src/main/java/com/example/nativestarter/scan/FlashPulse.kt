package com.example.nativestarter.scan

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraControl

/**
 * Fires a short torch blink to signal a successful capture.
 *
 * Uses [CameraControl.enableTorch] on the already-bound camera, so it does not
 * open a second camera session. Calls are serialized so a burst of decodes can
 * not leave the torch stuck on.
 */
object FlashPulse {

    private const val TAG = "FlashPulse"
    private const val PULSE_MS = 160L

    private val handler = Handler(Looper.getMainLooper())

    fun fire(control: CameraControl) {
        try {
            control.enableTorch(true)
            handler.postDelayed({
                try {
                    control.enableTorch(false)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to disable torch", t)
                }
            }, PULSE_MS)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to enable torch", t)
        }
    }
}
