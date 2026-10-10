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
    fun `outer scroll at bottom is at end`() {
        assertTrue(isReaderScrollAtEnd(value = 1000, maxValue = 1000))
    }

    @Test
    fun `outer scroll within tolerance is at end`() {
        assertTrue(isReaderScrollAtEnd(value = 960, maxValue = 1000, tolerance = 50))
    }

    @Test
    fun `outer scroll mid content is not at end`() {
        assertFalse(isReaderScrollAtEnd(value = 500, maxValue = 1000))
    }

    @Test
    fun `outer scroll unmeasured is never at end`() {
        assertFalse(isReaderScrollAtEnd(value = 0, maxValue = 0))
    }
}
