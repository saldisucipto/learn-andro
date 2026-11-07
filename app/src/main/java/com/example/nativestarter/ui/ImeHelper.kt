package com.example.nativestarter.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * Read-only state helpers for the Solve Scan keyboard toggle.
 *
 * Android does not allow an ordinary app to set the active input method:
 * writing `Settings.Secure.DEFAULT_INPUT_METHOD` requires the signature-level
 * `WRITE_SECURE_SETTINGS` permission. The only sanctioned paths are the system
 * keyboard settings screen and the keyboard picker.
 *
 * The actual "switch back" action is performed from inside the IME itself via
 * `InputMethodService.switchToPreviousInputMethod`, which the platform allows.
 *
 * This helper therefore exposes:
 *  - [isEnabled] / [isSelected] to render an accurate toggle state
 *  - [openInputMethodSettings] to enable Solve Scan the first time
 *  - [showPicker] to let the user choose the active keyboard
 */
object ImeHelper {

    private const val SERVICE_MARKER = "ScannerImeService"

    private fun imm(context: Context): InputMethodManager =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    fun isEnabled(context: Context): Boolean =
        imm(context).enabledInputMethodList.any { it.packageName == context.packageName }

    fun isSelected(context: Context): Boolean =
        currentIme(context)?.contains(SERVICE_MARKER) == true

    fun currentIme(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)

    fun openInputMethodSettings(context: Context) {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun showPicker(context: Context) {
        imm(context).showInputMethodPicker()
    }
}
