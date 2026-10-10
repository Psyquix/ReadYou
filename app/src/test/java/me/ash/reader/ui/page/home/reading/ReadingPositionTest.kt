package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Fork patch 7: per-article reading positions.
 * Pure JVM test — no Android, no DataStore, no Room.
 */
class ReadingPositionTest {

    @Test
    fun `position encodes and decodes`() {
        val pos = ReadingPosition(index = 12, offset = 345, scrollY = 0)
        assertEquals(pos, decodeReadingPosition(pos.encode()))
    }

    @Test
    fun `webview position encodes and decodes`() {
        val pos = ReadingPosition(index = 0, offset = 0, scrollY = 2871)
        assertEquals(pos, decodeReadingPosition(pos.encode()))
    }

    @Test
    fun `malformed input decodes to null`() {
        assertNull(decodeReadingPosition(null))
        assertNull(decodeReadingPosition(""))
        assertNull(decodeReadingPosition("   "))
        assertNull(decodeReadingPosition("not-a-position"))
        assertNull(decodeReadingPosition("v9|1|2|3"))
        assertNull(decodeReadingPosition("v1|1|two|3"))
    }

    @Test
    fun `prune drops only ids missing from the database`() {
        assertEquals(
            setOf("gone-a", "gone-b"),
            positionsToPrune(
                savedIds = setOf("kept", "gone-a", "gone-b"),
                existingIds = setOf("kept"),
            ),
        )
    }

    @Test
    fun `prune keeps everything when all exist`() {
        assertEquals(
            emptySet<String>(),
            positionsToPrune(
                savedIds = setOf("a", "b"),
                existingIds = setOf("a", "b", "c"),
            ),
        )
    }

    @Test
    fun `prune of nothing is empty`() {
        assertEquals(
            emptySet<String>(),
            positionsToPrune(savedIds = emptySet(), existingIds = setOf("a")),
        )
    }

    @Test
    fun `prune drops everything when the database has none`() {
        assertEquals(
            setOf("a", "b"),
            positionsToPrune(savedIds = setOf("a", "b"), existingIds = emptySet()),
        )
    }

    @Test
    fun `restore target clamps into the laid-out range`() {
        assertEquals(800, coerceRestoreTarget(saved = 800, maxValue = 1000))
        assertEquals(1000, coerceRestoreTarget(saved = 1500, maxValue = 1000))
    }

    @Test
    fun `restore target is null at top or when unmeasured`() {
        assertNull(coerceRestoreTarget(saved = 0, maxValue = 1000))
        assertNull(coerceRestoreTarget(saved = 800, maxValue = 0))
    }
}
