package com.example.nativestarter.ime

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.nativestarter.scan.BarcodeAnalyzer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "RfCameraEngine"
private const val FLASH_PULSE_MS = 160L

/**
 * Headless CameraX capture engine usable outside Compose (from an
 * [android.inputmethodservice.InputMethodService]).
 *
 * [InputMethodService] is not a [LifecycleOwner], so a private
 * [LifecycleRegistry] is maintained here and driven manually with
 * [start] / [stop]. CameraX requires a lifecycle owner to bind use cases.
 */
class RfCameraEngine(
    private val context: Context,
    private val onBarcode: (value: String) -> Unit,
    private val onError: (Throwable) -> Unit = {}
) {
    private val lifecycleOwner = object : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private var analysisExecutor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var analyzer: BarcodeAnalyzer? = null
    private var camera: Camera? = null
    private var running = false

    fun start() {
        if (running) return
        running = true

        if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.INITIALIZED) {
            lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
        }
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED

        analysisExecutor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val barcodeAnalyzer = BarcodeAnalyzer { result -> onBarcode(result.value) }
                analyzer = barcodeAnalyzer
                analysis.setAnalyzer(analysisExecutor!!, barcodeAnalyzer)

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    analysis
                )
                Log.i(TAG, "Camera bound for IME")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to bind camera", t)
                onError(t)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        if (!running) return
        running = false
        releaseCamera(0L)
    }

    /**
     * Blinks the torch briefly as a visual capture indicator, then releases the
     * camera. Called on a successful decode.
     *
     * The torch is driven through the bound [Camera]'s [CameraControl] so it
     * uses the same camera instance that is already open, which avoids the
     * "camera in use" conflicts a separate torch session would cause.
     */
    fun pulseFlashAndStop(onDone: () -> Unit = {}) {
        if (!running) {
            onDone()
            return
        }
        running = false

        val control = camera?.cameraControl
        if (control != null) {
            try {
                control.enableTorch(true)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to enable torch", t)
            }
            releaseCamera(FLASH_PULSE_MS, onDone)
        } else {
            releaseCamera(0L, onDone)
        }
    }

    private fun releaseCamera(delayMs: Long, onDone: () -> Unit = {}) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                camera?.cameraControl?.enableTorch(false)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to disable torch", t)
            }

            try {
                provider?.unbindAll()
            } catch (t: Throwable) {
                Log.w(TAG, "unbindAll failed", t)
            }
            provider = null
            camera = null

            analysisExecutor?.shutdown()
            analysisExecutor = null

            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
            }
            lifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
            Log.i(TAG, "Camera released for IME")
            onDone()
        }, delayMs)
    }

    val isRunning: Boolean get() = running
}
