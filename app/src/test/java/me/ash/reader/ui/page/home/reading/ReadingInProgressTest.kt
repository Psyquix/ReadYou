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
}
