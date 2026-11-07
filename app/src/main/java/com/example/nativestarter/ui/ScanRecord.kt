package com.example.nativestarter.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScanRecord(
    val id: Long = System.currentTimeMillis(),
    val value: String,
    val format: String,
    val timestamp: Date = Date()
) {
    val timeLabel: String
        get() = TIME_FORMAT.format(timestamp)

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}
