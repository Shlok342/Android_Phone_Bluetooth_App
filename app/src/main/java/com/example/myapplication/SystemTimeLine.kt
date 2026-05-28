package com.example.myapplication

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SystemTimeline {

    private const val MAX_EVENTS = 300

    data class TimelineEvent(
        val timestampMs: Long = System.currentTimeMillis(),
        val message: String
    ) {
        val formatted: String get() {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            return "${sdf.format(Date(timestampMs))}  $message"
        }
    }

    private val _events = ArrayDeque<TimelineEvent>()

    fun log(message: String) {
        synchronized(_events) {
            _events.addFirst(TimelineEvent(message = message))
            if (_events.size > MAX_EVENTS) _events.removeLast()
        }
    }

    fun getEvents(): List<TimelineEvent> = synchronized(_events) { _events.toList() }

    fun clear() = synchronized(_events) { _events.clear() }
}