package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fork patch 5: mark as read at end of article.
 * Pure JVM test — no Android, no Compose runtime.
 */
class MarkReadAtEndTest {

    @Test
    fun `native list with last item fully visible is at end`() {
        assertTrue(isNativeListAtEnd(
            lastVisibleIndex = 4,
            lastVisibleOffset = 800,
            lastVisibleSize = 200,
            viewportEndOffset = 1000,
            totalItemsCount = 5,
        ))
    }

    @Test
    fun `native list with last item cut off is not at end`() {
        assertFalse(isNativeListAtEnd(
            lastVisibleIndex = 4,
            lastVisibleOffset = 900,
            lastVisibleSize = 200,
            viewportEndOffset = 1000,
            totalItemsCount = 5,
        ))
    }

    @Test
    fun `native list not showing the last item is not at end`() {
        assertFalse(isNativeListAtEnd(
            lastVisibleIndex = 2,
            lastVisibleOffset = 800,
            lastVisibleSize = 200,
            viewportEndOffset = 1000,
            totalItemsCount = 5,
        ))
    }

    @Test
    fun `native empty list is never at end`() {
        assertFalse(isNativeListAtEnd(
            lastVisibleIndex = -1,
            lastVisibleOffset = 0,
            lastVisibleSize = 0,
            viewportEndOffset = 1000,
            totalItemsCount = 0,
        ))
    }

    @Test
    fun `scroll at max is at end`() {
        assertTrue(isScrollAtEnd(value = 1500, maxValue = 1500))
    }

    @Test
    fun `scroll within tolerance is at end`() {
        assertTrue(isScrollAtEnd(value = 1460, maxValue = 1500, tolerance = 50))
    }

    @Test
    fun `scroll far from end is not at end`() {
        assertFalse(isScrollAtEnd(value = 500, maxValue = 1500, tolerance = 50))
    }

    @Test
    fun `nothing to scroll counts as at end`() {
        assertTrue(isScrollAtEnd(value = 0, maxValue = 0))
    }
}
