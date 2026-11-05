package com.example.aegis.ui.messages

import com.example.aegis.domain.model.ChatMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Small pure helpers behind the Messages screens, kept free of Android types so they unit-test.

object MessageText {
    // Must match the API's limit on a message.
    const val MAX_LENGTH = 1000

    /** The text to send: trimmed and capped, or null if there is nothing worth sending. */
    fun clean(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_LENGTH)
}

object UnreadBadge {
    /** What to show on a conversation's badge; null means hide it. */
    fun label(count: Int): String? = when {
        count <= 0 -> null
        count > 9 -> "9+"
        else -> count.toString()
    }
}

object ChatMerge {
    /**
     * Adds newly fetched messages to the ones already shown. A message that arrives twice (a poll
     * overlapping a send) appears once, and the result is always oldest first.
     */
    fun merge(existing: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> =
        (existing + incoming)
            .distinctBy { it.id }
            .sortedBy { it.sentAt ?: Instant.MAX }
}

object ChatTime {
    /** "14:05" for today, "3 Oct" for earlier days, empty when the time is unknown. */
    fun label(time: Instant?, now: Instant, zone: ZoneId, locale: Locale): String {
        if (time == null) return ""
        val sentDay = time.atZone(zone).toLocalDate()
        val pattern = if (sentDay == now.atZone(zone).toLocalDate()) "HH:mm" else "d MMM"
        return DateTimeFormatter.ofPattern(pattern, locale).format(time.atZone(zone))
    }
}
