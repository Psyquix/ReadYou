package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fork patch 8: in-progress accent bar.
 * Pure JVM test — no Android, no DataStore, no Compose.
 */
class ReadingInProgressTest {

    @Test
    fun `unread article with a saved spot is reading`() {
        assertTrue(isReadingNow(isUnread = true, inProgressIds = setOf("a"), articleId = "a"))
    }

    @Test
    fun `read article is never reading even with a stale spot`() {
        assertFalse(isReadingNow(isUnread = false, inProgressIds = setOf("a"), articleId = "a"))
    }

    @Test
    fun `unread article without a spot is not reading`() {
        assertFalse(isReadingNow(isUnread = true, inProgressIds = setOf("b"), articleId = "a"))
    }

    @Test
    fun `store keys resolve to article ids`() {
        assertEquals(
            setOf("abc", "def"),
            inProgressIdsFromKeys(
                setOf(
                    "reading_pos_abc",
                    "reading_pos_def",
                    "unrelated_key",
                    "reading_pos_",
                )
            ),
        )
    }

    @Test
    fun `empty store resolves to empty`() {
        assertEquals(emptySet<String>(), inProgressIdsFromKeys(emptySet()))
    }

    @Test
    fun `bar sits in the gutter left of content, vertically centered`() {
        val bar = readingBarRect(
            boxWidth = 1000f,
            boxHeight = 400f,
            gutterInsetPx = 30f,
            barWidthPx = 9f,
            fraction = 0.65f,
        )

        assertEquals(-30f, bar.left)
        assertEquals(9f, bar.width)
        assertEquals(400f * 0.65f, bar.height)
        assertEquals((400f - 400f * 0.65f) / 2f, bar.top)
    }

    @Test
    fun `bar never leaves the row vertically`() {
        val bar = readingBarRect(
            boxWidth = 1000f,
            boxHeight = 100f,
            gutterInsetPx = 30f,
            barWidthPx = 9f,
            fraction = 0.65f,
        )

        assertTrue(bar.top >= 0f)
        assertTrue(bar.top + bar.height <= 100f)
    }
}
