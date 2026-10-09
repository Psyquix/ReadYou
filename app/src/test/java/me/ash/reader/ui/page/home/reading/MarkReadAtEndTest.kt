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
    fun `webview at bottom is at end`() {
        assertTrue(isWebViewContentAtEnd(scrollY = 1000, scale = 1f, contentHeight = 2000, viewHeight = 1000))
    }

    @Test
    fun `webview within tolerance is at end`() {
        assertTrue(isWebViewContentAtEnd(scrollY = 995, scale = 1f, contentHeight = 2000, viewHeight = 1000, tolerancePx = 8))
    }

    @Test
    fun `webview mid content is not at end`() {
        assertFalse(isWebViewContentAtEnd(scrollY = 500, scale = 1f, contentHeight = 2000, viewHeight = 1000))
    }

    @Test
    fun `webview content scale is applied`() {
        assertTrue(isWebViewContentAtEnd(scrollY = 1500, scale = 1.5f, contentHeight = 2000, viewHeight = 1500))
        assertFalse(isWebViewContentAtEnd(scrollY = 500, scale = 1.5f, contentHeight = 2000, viewHeight = 1500))
    }

    @Test
    fun `webview unlaid-out or unloaded is never at end`() {
        assertFalse(isWebViewContentAtEnd(scrollY = 0, scale = 1f, contentHeight = 2000, viewHeight = 0))
        assertFalse(isWebViewContentAtEnd(scrollY = 0, scale = 1f, contentHeight = 0, viewHeight = 1000))
    }

    @Test
    fun `webview short content is at end once laid out`() {
        assertTrue(isWebViewContentAtEnd(scrollY = 0, scale = 1f, contentHeight = 500, viewHeight = 1000))
    }
}
