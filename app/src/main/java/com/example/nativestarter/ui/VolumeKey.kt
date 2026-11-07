package com.example.nativestarter.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Captures the hardware Volume Down key as a scan trigger.
 *
 * Unlike the power key, which the framework consumes, volume keys are
 * deliverable to the focused view. The composable owning this modifier must be
 * focusable; focus is requested on first composition. Only key-down is handled
 * so holding the key does not fire repeatedly.
 */
fun Modifier.volumeDownTrigger(onVolumeDown: () -> Unit): Modifier = composed {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    this
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (event.key == Key.VolumeDown && event.type == KeyEventType.KeyDown) {
                onVolumeDown()
                true
            } else {
                false
            }
        }
}
