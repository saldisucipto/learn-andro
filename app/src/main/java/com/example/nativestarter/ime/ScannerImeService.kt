package com.example.nativestarter.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
 * Two capabilities share one window:
 *  - **Scanning** — SCAN (or Volume Down) opens a headless camera, decodes a
 *    barcode and commits the value into the host field exactly like a hardware
 *    scanner typing into a form.
 *  - **Typing** — a full on-screen keyboard with shift/caps, long-press
 *    alternates, swipe-to-delete, hold-to-repeat backspace, cursor movement,
 *    autocorrect with next-word prediction, a clipboard ring and an emoji board.
 *
 * Everything except the camera runs on the main thread; the camera reports back
 * through a [Handler] pinned to the main looper.
 */
class ScannerImeService : InputMethodService() {

    private var cameraEngine: RfCameraEngine? = null
    private var scanning by mutableStateOf(false)
    private var lastValue by mutableStateOf<String?>(null)
    private var status by mutableStateOf("IDLE")
    private var autoEnter by mutableStateOf(true)

    private var mode by mutableStateOf(InputMode.NUM)
    private var shift by mutableStateOf(false)
    private var capsLock by mutableStateOf(false)
    private var panel by mutableStateOf(BoardPanel.NONE)

    private var suggestions by mutableStateOf<List<String>>(emptyList())
    private val clipboardItems = mutableStateListOf<String>()
    private val recentEmoji = mutableStateListOf<String>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val engine = TextEngine()
    private lateinit var clipboard: ClipboardHistory

    /** Cursor position at the last suggestion refresh, so we only refetch on change. */
    private var lastCursor = -1
    private var lastComposingWord: String? = null

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

    override fun onCreate() {
        super.onCreate()
        clipboard = ClipboardHistory(this)
        refreshClipboardState()
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
                    mode = mode,
                    shift = shift,
                    capsLock = capsLock,
                    suggestions = suggestions,
                    clipboardItems = clipboardItems.toList(),
                    recentEmoji = recentEmoji.toList(),
                    panel = panel,
                    onScan = { commitScan() },
                    onToggleAutoEnter = { autoEnter = !autoEnter },
                    onKey = { key -> onKeyPressed(key) },
                    onLongPressKey = { key -> onAlternateRequested(key) },
                    onSuggestion = { candidate -> applySuggestion(candidate) },
                    onSuggestionDismiss = {
                        suggestions = emptyList()
                        lastComposingWord = null
                    },
                    onModeChange = { newMode ->
                        mode = newMode
                        panel = BoardPanel.NONE
                        if (newMode != InputMode.ABC) {
                            shift = false
                            capsLock = false
                        }
                    },
                    onPanelToggle = { target ->
                        panel = if (panel == target) BoardPanel.NONE else target
                        if (panel == BoardPanel.CLIPBOARD) captureSystemClipboard()
                    },
                    onClipboardPick = { value -> commitText(value) },
                    onClipboardDelete = { value ->
                        clipboard.remove(value)
                        refreshClipboardState()
                    },
                    onClipboardClear = {
                        clipboard.clear()
                        refreshClipboardState()
                    },
                    onEmojiPick = { emoji -> commitEmoji(emoji) },
                    onShift = { onShiftPressed() },
                    onBackspace = { backspace() },
                    onBackspaceHeld = { backspace() },
                    onDeleteWord = { deleteWord() },
                    onClear = { clearAll() },
                    onEnter = { commitEnter() },
                    onMoveLeft = { moveCursor(-1) },
                    onMoveRight = { moveCursor(1) },
                    onSwitchKeyboard = { switchToOtherKeyboard() },
                    onHide = { requestHideSelf(0) }
                )
            }
        }

        composeView.setViewTreeLifecycleOwner(treeOwner)
        composeView.setViewTreeViewModelStoreOwner(treeOwner)
        composeView.setViewTreeSavedStateRegistryOwner(treeOwner)

        // The IME window is sized from the input view's measured height. A
        // ComposeView defaults to MATCH_PARENT, which lets the framework
        // measure this window at a tiny height and clip the board. WRAP_CONTENT
        // makes the window grow to exactly the keyboard's intrinsic size.
        composeView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return composeView
    }

    /**
     * Height the IME reports before the view is laid out. Compose needs one
     * measure pass, so reporting the content as touchable prevents the window
     * from flashing at a clipped size on the first frame.
     */
    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        outInsets?.let {
            it.contentTopInsets = it.visibleTopInsets
            it.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
        }
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
        panel = BoardPanel.NONE
        suggestions = emptyList()

        // Present the board that matches the focused field: number keypad for
        // numeric/phone/datetime fields, QWERTY for free text.
        val inputType = info?.inputType ?: InputType.TYPE_CLASS_TEXT
        if (!restarting) {
            mode = when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_DATETIME -> InputMode.NUM
                else -> InputMode.ABC
            }
            shift = false
            capsLock = false
        }
        refreshClipboardState()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        stopScanning()
        suggestions = emptyList()
    }

    /**
     * Fires on every cursor move, including after `commitText`, which is what
     * keeps the suggestion strip in sync with what the user is typing.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (newSelStart != newSelEnd) {
            suggestions = emptyList()
            return
        }
        if (newSelStart == lastCursor) return
        lastCursor = newSelStart
        updateSuggestions()
    }

    /**
     * Volume Down is used as a hardware scan trigger, mirroring the standalone
     * scanner screen. The key is consumed so it does not also change the media
     * volume while the scanner keyboard is up.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val repeat = event?.repeatCount ?: 0
            if (repeat == 0) {
                toggleScanFromKey()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val repeat = event?.repeatCount ?: 0
            if (repeat == 0 && panel == BoardPanel.CLIPBOARD) {
                panel = BoardPanel.NONE
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
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
            Log.w(TAG, "Keyboard switch failed", t)
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

    /* ---------------------------------------------------------------- scanning */

    private fun commitScan() {
        if (scanning) {
            stopScanning()
            status = "READY"
            return
        }

        scanning = true
        status = "SCANNING\u2026"

        cameraEngine = RfCameraEngine(
            context = this,
            onBarcode = { value ->
                mainHandler.post {
                    if (scanning) {
                        commitScanned(value)
                    }
                }
            },
            onError = { t ->
                mainHandler.post {
                    if (!scanning) return@post
                    status = "ERROR: ${t.javaClass.simpleName}"
                    scanning = false
                    ScanBeep.failure(this)
                    Haptics.error(this)
                }
            }
        ).also { it.start() }
    }

    private fun stopScanning() {
        scanning = false
        cameraEngine?.stop()
        cameraEngine = null
    }

    private fun commitScanned(value: String) {
        val ic = currentInputConnection
        ic?.commitText(value, 1)
        ic?.finishComposingText()

        lastValue = value
        status = if (autoEnter) "CAPTURED + ENTER" else "CAPTURED"
        ScanBeep.success(this, scanLength = value.length)
        Haptics.success(this)
        clipboard.push(value)
        refreshClipboardState()

        // Flash pulses while the camera is still bound, then it is released.
        val engine = cameraEngine
        cameraEngine = null
        scanning = false
        engine?.pulseFlashAndStop()

        if (autoEnter) {
            performEnter()
        }

        lastCursor = -1
        updateSuggestions()
    }

    /* ------------------------------------------------------------------ typing */

    private fun commitText(value: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(value, 1)
        ic.finishComposingText()
        clipboard.push(value)
        refreshClipboardState()
        panel = BoardPanel.NONE
        lastCursor = -1
        updateSuggestions()
    }

    private fun commitEmoji(emoji: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(emoji, 1)
        ic.finishComposingText()
        recentEmoji.remove(emoji)
        recentEmoji.add(0, emoji)
        while (recentEmoji.size > MAX_RECENT_EMOJI) {
            recentEmoji.removeAt(recentEmoji.size - 1)
        }
    }

    /**
     * Routes a board tap. Letters honour the shift/caps state (single-shot
     * shift clears itself after one character, exactly like a stock IME);
     * everything else is committed verbatim.
     *
     * After committing, autocorrect runs on the word behind the cursor: if the
     * engine has a confident fix it is applied immediately, which is what makes
     * typing feel like a modern keyboard instead of a key grid.
     */
    private fun onKeyPressed(key: String) {
        val ic = currentInputConnection ?: return

        val text = if (key.length == 1 && key[0].isLetter()) {
            val upper = shift || capsLock
            val out = if (upper) key.uppercase() else key.lowercase()
            if (shift && !capsLock) shift = false
            out
        } else {
            key
        }

        if (text == " ") {
            commitSpace(ic)
            return
        }

        ic.commitText(text, 1)
        ic.finishComposingText()

        if (text.length == 1 && !text[0].isLetterOrDigit()) {
            commitWordBoundary(ic)
        }

        lastCursor = -1
        updateSuggestions()
    }

    /**
     * Commits a space, resolving the word behind the cursor first.
     *
     * Behaviour mirrors a modern IME:
     *  - a visible leading chip whose word differs from what is typed replaces it
     *    (this is the autocorrect / completion path)
     *  - the resolved word is fed to the engine so predictions improve
     *  - an exact duplicate of the typed word is not re-committed
     */
    private fun commitSpace(ic: android.view.inputmethod.InputConnection) {
        val typed = currentWordBefore(ic)
        val leadChip = suggestions.firstOrNull()

        if (!typed.isNullOrBlank() && leadChip != null && !leadChip.equals(typed, ignoreCase = true)) {
            ic.deleteSurroundingText(typed.length, 0)
            ic.commitText(leadChip, 1)
            engine.learn(leadChip, previousWordBefore(ic, leadChip.length))
        } else if (!typed.isNullOrBlank()) {
            engine.learn(typed, previousWordBefore(ic, typed.length))
        }

        ic.commitText(" ", 1)
        ic.finishComposingText()

        suggestions = emptyList()
        lastComposingWord = null
        lastCursor = -1
    }

    private fun commitWordBoundary(ic: android.view.inputmethod.InputConnection) {
        val word = currentWordBefore(ic) ?: return
        if (word.isBlank()) return
        engine.learn(word, previousWordBefore(ic, word.length))
    }

    /**
     * Long-press fallback: commits the first alternate for [base].
     *
     * Kept intentionally simple and predictable. Diacritics are the first entry
     * for accented letters, while the keys people actually reach for on a long
     * press (`.`, `$`, `@`) put the useful value first in [KeyLayout.longPress].
     * Shift is honoured so a capitalised key yields a capitalised alternate.
     */
    private fun onAlternateRequested(base: String) {
        val alternates = KeyLayout.alternates(base)
        if (alternates.isEmpty()) return
        val ic = currentInputConnection ?: return

        var value = alternates.first()
        if (base.length == 1 && base[0].isLetter() && (shift || capsLock)) {
            value = value.uppercase()
            if (shift && !capsLock) shift = false
        }

        ic.commitText(value, 1)
        ic.finishComposingText()
        lastCursor = -1
        updateSuggestions()
    }

    private fun applySuggestion(candidate: String) {
        val ic = currentInputConnection ?: return
        val typedWord = currentWordBefore(ic) ?: return
        if (typedWord.isEmpty()) {
            ic.commitText(candidate, 1)
        } else {
            ic.deleteSurroundingText(typedWord.length, 0)
            ic.commitText(candidate, 1)
        }
        ic.finishComposingText()
        engine.learn(candidate, previousWordBefore(ic, candidate.length))
        suggestions = emptyList()
        lastCursor = -1
    }

    /**
     * Recomputes the suggestion strip from the word behind the cursor.
     *
     * Cheap enough to run on every selection change: the engine is a hash map
     * lookup plus a bounded scan, and the list is only re-set when it changes so
     * Compose does not recompose the board.
     */
    private fun updateSuggestions() {
        val ic = currentInputConnection ?: run {
            suggestions = emptyList()
            return
        }
        val word = currentWordBefore(ic)
        if (word.isNullOrEmpty()) {
            suggestions = emptyList()
            lastComposingWord = null
            return
        }

        val lower = word.lowercase()
        lastComposingWord = lower

        val next = ArrayList<String>(3)

        /*
         * 1. Resolve an explicit correction first: "teh" -> "the" must win over
         *    a plain prefix match, otherwise autocorrect would never fire.
         */
        val corrections = engine.suggest(lower, 3)
        val correction = engine.shouldAutocorrect(lower, corrections)

        if (correction != null) {
            next.add(correction)
        }
        corrections.forEach { candidate ->
            if (next.none { it.equals(candidate, ignoreCase = true) }) next.add(candidate)
        }

        /*
         * 2. Next-word prediction when the cursor sits after a space.
         */
        if (next.size < 3) {
            val previous = previousWordBefore(ic, 0)
            val predicted = previous?.let { engine.nextWord(it) }
            if (predicted != null && next.none { it.equals(predicted, ignoreCase = true) }) {
                next.add(predicted)
            }
        }

        val capped = next.take(3)
        if (capped != suggestions) suggestions = capped
    }

    /** The word fragment between the cursor and the last separator. */
    private fun currentWordBefore(ic: android.view.inputmethod.InputConnection): String? {
        val before = ic.getTextBeforeCursor(48, 0)?.toString() ?: return null
        if (before.isEmpty()) return null
        var end = before.length
        while (end > 0 && !before[end - 1].isLetterOrDigit() && before[end - 1] != '\'') {
            end--
        }
        var start = end
        while (start > 0 && (before[start - 1].isLetterOrDigit() || before[start - 1] == '\'')) {
            start--
        }
        return if (end > start) before.substring(start, end) else null
    }

    /** Word before the last [skip] characters, used to feed the transition model. */
    private fun previousWordBefore(
        ic: android.view.inputmethod.InputConnection,
        skip: Int
    ): String? {
        val window = ic.getTextBeforeCursor(64 + skip, 0)?.toString() ?: return null
        if (window.length <= skip) return null
        val text = window.substring(0, window.length - skip).trimEnd()
        if (text.isEmpty()) return null
        var end = text.length
        var start = end
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '\'')) {
            start--
        }
        return if (end > start) text.substring(start, end).lowercase() else null
    }

    private fun onShiftPressed() {
        when {
            capsLock -> { capsLock = false; shift = false }
            shift -> { shift = false; capsLock = true }
            else -> shift = true
        }
    }

    private fun moveCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)?.length ?: 0
        if (delta < 0 && before == 0) return
        if (delta > 0 && (ic.getTextAfterCursor(1, 0)?.isEmpty() != false)) return

        val extracted = ic.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest().apply {
                flags = 0
                hintMaxChars = Int.MAX_VALUE
                hintMaxLines = Int.MAX_VALUE
            },
            0
        ) ?: return

        val selection = extracted.selectionStart
        if (selection < 0) return
        val target = (selection + delta).coerceIn(0, extracted.text?.length ?: selection)
        ic.setSelection(target, target)
        lastCursor = -1
        updateSuggestions()
    }

    private fun clearAll() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(4096, 0) ?: return
        if (before.isNotEmpty()) {
            ic.deleteSurroundingText(before.length, 0)
        }
        suggestions = emptyList()
        lastCursor = -1
    }

    private fun commitEnter() {
        val ic = currentInputConnection ?: return
        performEnter(ic)
    }

    private fun performEnter(ic: android.view.inputmethod.InputConnection? = currentInputConnection) {
        ic ?: return
        val action = currentInputEditorInfo?.imeOptions
            ?.and(EditorInfo.IME_MASK_ACTION)

        if (action != null &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        suggestions = emptyList()
        lastCursor = -1
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(1, 0)
        ic.finishComposingText()
        lastCursor = -1
        updateSuggestions()
    }

    /**
     * Deletes the word before the cursor, plus the whitespace that separated it.
     *
     * Matches the swipe-left-on-space gesture. Stops at line breaks so a swipe
     * cannot silently eat a previous paragraph.
     */
    private fun deleteWord() {
        val ic = currentInputConnection ?: return
        val window = ic.getTextBeforeCursor(64, 0)?.toString()
        if (window.isNullOrEmpty()) return

        var index = window.length
        /* Skip trailing spaces and tabs. */
        while (index > 0 && (window[index - 1] == ' ' || window[index - 1] == '\t')) index--
        /* Then the word itself. */
        while (index > 0 &&
            window[index - 1] != ' ' &&
            window[index - 1] != '\t' &&
            window[index - 1] != '\n'
        ) {
            index--
        }

        val count = window.length - index
        if (count <= 0) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.deleteSurroundingText(count, 0)
        }
        ic.finishComposingText()
        lastCursor = -1
        updateSuggestions()
    }

    /* --------------------------------------------------------------- clipboard */

    /**
     * Pulls the current system clipboard into the ring so a copy made in another
     * app shows up in the panel. Silently does nothing if the platform refuses
     * the read (Android 10+ outside a focused input context).
     */
    private fun captureSystemClipboard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val manager = getSystemService(android.content.ClipboardManager::class.java)
                ?: return
            val clip = manager.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (clipboard.push(text)) refreshClipboardState()
        } catch (t: Throwable) {
            Log.w(TAG, "Clipboard read refused", t)
        }
    }

    private fun refreshClipboardState() {
        val current = clipboard.items()
        clipboardItems.clear()
        clipboardItems.addAll(current)
    }

    private companion object {
        const val TAG = "ScannerImeService"
        const val MAX_RECENT_EMOJI = 24
    }
}
