package com.example.nativestarter.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.nativestarter.scan.ScanBeep

/**
 * IME that turns the phone into a barcode scanner keyboard.
 *
 * While another app has focus on a text field, the user picks this IME and
 * presses SCAN. A headless camera decodes the barcode and the value is written
 * into the host app through [currentInputConnection] via `commitText`, exactly
 * like a hardware scanner typing into a form.
 */
class ScannerImeService : InputMethodService() {

    private var cameraEngine: RfCameraEngine? = null
    private var scanning by mutableStateOf(false)
    private var lastValue by mutableStateOf<String?>(null)
    private var status by mutableStateOf("IDLE")
    private var autoEnter by mutableStateOf(true)

    private val treeOwner = object :
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    override fun onCreateInputView(): View {
        if (!treeOwnerCreated) {
            treeOwner.onCreate()
            treeOwnerCreated = true
        }
        attachTreeOwners()

        val composeView = ComposeView(this).apply {
            setContent {
                ScannerKeyboard(
                    scanning = scanning,
                    status = status,
                    lastValue = lastValue,
                    autoEnter = autoEnter,
                    onScan = { commitScan() },
                    onToggleAutoEnter = { autoEnter = !autoEnter },
                    onSpace = { commitChar(" ") },
                    onEnter = { commitEnter() },
                    onBackspace = { backspace() },
                    onSwitchKeyboard = { switchToOtherKeyboard() },
                    onHide = { requestHideSelf(0) }
                )
            }
        }

        composeView.setViewTreeLifecycleOwner(treeOwner)
        composeView.setViewTreeViewModelStoreOwner(treeOwner)
        composeView.setViewTreeSavedStateRegistryOwner(treeOwner)

        return composeView
    }

    /**
     * Compose resolves its recomposer by walking the view tree, starting from
     * the IME window's decor view. That decor view is created by the framework
     * and is not an [androidx.lifecycle.LifecycleOwner], so the required owners
     * are attached manually here before the input view is inflated.
     */
    private fun attachTreeOwners() {
        val decor = window?.window?.decorView ?: return
        decor.setViewTreeLifecycleOwner(treeOwner)
        decor.setViewTreeViewModelStoreOwner(treeOwner)
        decor.setViewTreeSavedStateRegistryOwner(treeOwner)
    }

    override fun onEvaluateInputViewShown(): Boolean {
        attachTreeOwners()
        return super.onEvaluateInputViewShown()
    }

    private var treeOwnerCreated = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Do not force the camera open the moment a field is focused; the user
        // arms the reader explicitly with SCAN to avoid constant camera use.
        status = "READY"
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopScanning()
    }

    /**
     * Volume Down is used as a hardware scan trigger, mirroring the standalone
     * scanner screen. The key is consumed so it does not also change the media
     * volume while the scanner keyboard is up.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            val repeat = event?.repeatCount ?: 0
            if (repeat == 0) {
                toggleScanFromKey()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Returns control to the user's regular keyboard.
     *
     * An ordinary app cannot change the active IME, but an IME is allowed to
     * switch away from itself: [switchToPreviousInputMethod] was added for
     * exactly this and restores the keyboard used before this one.
     */
    private fun switchToOtherKeyboard() {
        stopScanning()
        try {
            if (!switchToPreviousInputMethod()) {
                switchToNextInputMethod(false)
            }
        } catch (t: Throwable) {
            android.util.Log.w("ScannerImeService", "Keyboard switch failed", t)
        }
    }

    private fun toggleScanFromKey() {
        if (scanning) {
            stopScanning()
            status = "READY"
        } else {
            commitScan()
        }
    }

    override fun onDestroy() {
        stopScanning()
        ScanBeep.release()
        treeOwner.onDestroy()
        super.onDestroy()
    }

    private fun commitScan() {
        if (scanning) {
            stopScanning()
            status = "READY"
            return
        }

        scanning = true
        status = "SCANNING…"

        cameraEngine = RfCameraEngine(
            context = this,
            onBarcode = { value ->
                android.os.Handler(mainLooper).post {
                    if (scanning) {
                        commitText(value)
                    }
                }
            },
            onError = { t ->
                android.os.Handler(mainLooper).post {
                    status = "ERROR: ${t.javaClass.simpleName}"
                    scanning = false
                }
            }
        ).also { it.start() }
    }

    private fun stopScanning() {
        scanning = false
        cameraEngine?.stop()
        cameraEngine = null
    }

    private fun commitText(value: String) {
        currentInputConnection?.commitText(value, 1)
        currentInputConnection?.finishComposingText()

        lastValue = value
        status = if (autoEnter) "CAPTURED + ENTER" else "CAPTURED"
        ScanBeep.play()

        // Flash pulses while the camera is still bound, then it is released.
        val engine = cameraEngine
        cameraEngine = null
        scanning = false
        engine?.pulseFlashAndStop()

        if (autoEnter) {
            currentInputConnection?.let { ic ->
                val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
                if (action != null &&
                    action != EditorInfo.IME_ACTION_NONE &&
                    action != EditorInfo.IME_ACTION_UNSPECIFIED
                ) {
                    ic.performEditorAction(action)
                } else {
                    ic.sendKeyEvent(
                        android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_DOWN,
                            android.view.KeyEvent.KEYCODE_ENTER
                        )
                    )
                    ic.sendKeyEvent(
                        android.view.KeyEvent(
                            android.view.KeyEvent.ACTION_UP,
                            android.view.KeyEvent.KEYCODE_ENTER
                        )
                    )
                }
            }
        }
    }

    private fun commitChar(char: String) {
        currentInputConnection?.commitText(char, 1)
    }

    private fun commitEnter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)

        if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(1, 0)
    }
}
