package com.example.nativestarter.ui

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.nativestarter.scan.BarcodeAnalyzer
import com.example.nativestarter.scan.FlashPulse
import com.example.nativestarter.scan.ScanResult
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraCapture"

/**
 * Binds CameraX to the current lifecycle owner.
 *
 * When [showPreview] is false the preview use case is not created at all: only
 * [ImageAnalysis] is bound, so no surface is rendered and the composable
 * contributes nothing to the layout. This is the "RF reader" mode where the
 * camera captures purely in the background of the running activity.
 */
@Composable
fun CameraCapture(
    modifier: Modifier = Modifier,
    showPreview: Boolean = false,
    scanningEnabled: Boolean = true,
    onError: (Throwable) -> Unit = {},
    onBarcode: (ScanResult) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val mainExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentScanningEnabled by rememberUpdatedState(scanningEnabled)

    val previewView = if (showPreview) {
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
    } else {
        null
    }

    if (previewView != null) {
        AndroidView(modifier = modifier, factory = { previewView })
    }

    DisposableEffect(lifecycleOwner) {
        var boundCamera: Camera? = null

        val analyzer = BarcodeAnalyzer { result ->
            if (currentScanningEnabled) {
                boundCamera?.cameraControl?.let { FlashPulse.fire(it) }
                currentOnBarcode(result)
            }
        }

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                boundCamera = bind(
                    cameraProvider = cameraProvider,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    analyzer = analyzer,
                    analysisExecutor = analysisExecutor
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Camera initialization failed", t)
                onError(t)
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            if (cameraProviderFuture.isDone) {
                cameraProviderFuture.get().unbindAll()
            }
            analysisExecutor.shutdown()
        }
    }
}

private fun bind(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView?,
    analyzer: BarcodeAnalyzer,
    analysisExecutor: Executor
): Camera {
    val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .apply { setAnalyzer(analysisExecutor, analyzer) }

    cameraProvider.unbindAll()

    return if (previewView != null) {
        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageAnalysis
        )
    } else {
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            imageAnalysis
        )
    }
}
