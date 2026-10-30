package com.example.aegis.ui.messages

import com.example.aegis.domain.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class MessageLogicTest {

    private fun msg(id: String, at: String?, sender: String = "a", text: String = id) =
        ChatMessage(id, sender, text, at?.let(Instant::parse))

    // ---- MessageText ----

    @Test
    fun clean_trimsSurroundingWhitespace() {
        assertEquals("Hi there", MessageText.clean("  Hi there \n"))
    }

    @Test
    fun clean_blankOrNull_returnsNull() {
        assertNull(MessageText.clean("   \n\t"))
        assertNull(MessageText.clean(""))
        assertNull(MessageText.clean(null))
    }

    @Test
    fun clean_overLimit_isCappedToApiLimit() {
        val result = MessageText.clean("x".repeat(MessageText.MAX_LENGTH + 50))
        assertEquals(MessageText.MAX_LENGTH, result?.length)
    }

    @Test
    fun clean_keepsInnerNewlines() {
        assertEquals("line one\nline two", MessageText.clean("line one\nline two"))
    }

    // ---- UnreadBadge ----

    @Test
    fun badge_zeroOrNegative_isHidden() {
        assertNull(UnreadBadge.label(0))
        assertNull(UnreadBadge.label(-3))
    }

    @Test
    fun badge_smallCounts_showTheNumber() {
        assertEquals("1", UnreadBadge.label(1))
        assertEquals("9", UnreadBadge.label(9))
    }

    @Test
    fun badge_tenOrMore_collapsesToNinePlus() {
        assertEquals("9+", UnreadBadge.label(10))
        assertEquals("9+", UnreadBadge.label(250))
    }

    // ---- ChatMerge ----

    @Test
    fun merge_appendsNewMessagesInOrder() {
        val existing = listOf(msg("1", "2026-10-28T10:00:00Z"))
        val incoming = listOf(msg("2", "2026-10-28T10:01:00Z"), msg("3", "2026-10-28T10:02:00Z"))
        assertEquals(listOf("1", "2", "3"), ChatMerge.merge(existing, incoming).map { it.id })
    }

    @Test
    fun merge_dropsDuplicates_whenAPollOverlapsASend() {
        val sent = msg("2", "2026-10-28T10:01:00Z")
        val existing = listOf(msg("1", "2026-10-28T10:00:00Z"), sent)
        val result = ChatMerge.merge(existing, listOf(sent))
        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun merge_sortsOutOfOrderArrivals() {
        val existing = listOf(msg("3", "2026-10-28T10:02:00Z"))
        val incoming = listOf(msg("1", "2026-10-28T10:00:00Z"))
        assertEquals(listOf("1", "3"), ChatMerge.merge(existing, incoming).map { it.id })
    }

    @Test
    fun merge_messageWithoutTime_goesLast() {
        val result = ChatMerge.merge(listOf(msg("n", null)), listOf(msg("1", "2026-10-28T10:00:00Z")))
        assertEquals(listOf("1", "n"), result.map { it.id })
    }

    // ---- ChatTime ----

    private val zone = ZoneId.of("Africa/Johannesburg") // UTC+2
    private val now = Instant.parse("2026-10-28T12:00:00Z")

    @Test
    fun time_sameDay_showsClockTimeInLocalZone() {
        val label = ChatTime.label(Instant.parse("2026-10-28T12:05:00Z"), now, zone, Locale.ENGLISH)
        assertEquals("14:05", label)
    }

    @Test
    fun time_earlierDay_showsDayAndMonth() {
        val label = ChatTime.label(Instant.parse("2026-10-03T08:00:00Z"), now, zone, Locale.ENGLISH)
        assertEquals("3 Oct", label)
    }

    @Test
    fun time_justBeforeLocalMidnight_isStillYesterday() {
        // 21:59Z on the 27th is 23:59 on the 27th in Johannesburg, while "now" is the 28th.
        val label = ChatTime.label(Instant.parse("2026-10-27T21:59:00Z"), now, zone, Locale.ENGLISH)
        assertEquals("27 Oct", label)
    }

    @Test
    fun time_unknown_isEmpty() {
        assertEquals("", ChatTime.label(null, now, zone, Locale.ENGLISH))
    }
}
