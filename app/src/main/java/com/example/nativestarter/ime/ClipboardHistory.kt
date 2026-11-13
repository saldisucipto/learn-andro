package com.example.nativestarter.ime

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Clipboard ring buffer that survives process death.
 *
 * Android 10+ blocks background apps from reading the clipboard, but an IME is
 * a foreground input target so reads are allowed while a field is focused. Each
 * pinned entry is copied to private storage because the system clipboard itself
 * only ever holds one item and is owned by another process.
 */
class ClipboardHistory(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val entries = ArrayList<String>(MAX_ENTRIES)

    init {
        load()
    }

    /** Newest first. */
    fun items(): List<String> = entries.toList()

    val isEmpty: Boolean get() = entries.isEmpty()

    /** Adds [value], moves duplicates to the top, trims to [MAX_ENTRIES]. */
    fun push(value: String): Boolean {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) return false
        if (cleaned.length > MAX_LENGTH) return false

        entries.remove(cleaned)
        entries.add(0, cleaned)
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(entries.size - 1)
        }
        persist()
        return true
    }

    fun remove(value: String): Boolean {
        val removed = entries.remove(value)
        if (removed) persist()
        return removed
    }

    fun clear() {
        entries.clear()
        persist()
    }

    private fun load() {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return
        raw.split(SEPARATOR).forEach { entry ->
            if (entry.isNotEmpty()) entries.add(entry)
        }
    }

    private fun persist() {
        try {
            prefs.edit().putString(KEY_ENTRIES, entries.joinToString(SEPARATOR)).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to persist clipboard history", t)
        }
    }

    private companion object {
        const val TAG = "ClipboardHistory"
        const val PREFS = "scanner_ime_clipboard"
        const val KEY_ENTRIES = "entries"
        const val SEPARATOR = "\u241E"
        const val MAX_ENTRIES = 30
        const val MAX_LENGTH = 8192
    }
}
