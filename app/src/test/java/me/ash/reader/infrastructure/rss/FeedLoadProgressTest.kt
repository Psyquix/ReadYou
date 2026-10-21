package me.ash.reader.infrastructure.rss

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedLoadProgressTest {

    @Test
    fun `fraction is the byte ratio`() {
        assertEquals(0.25f, FeedLoadProgress(bytesRead = 25, totalBytes = 100).fraction)
    }

    @Test
    fun `unknown total is indeterminate`() {
        assertNull(FeedLoadProgress(bytesRead = 25, totalBytes = null).fraction)
    }

    @Test
    fun `zero total is indeterminate`() {
        assertNull(FeedLoadProgress(bytesRead = 25, totalBytes = 0).fraction)
    }

    @Test
    fun `fraction is clamped to one`() {
        assertEquals(1f, FeedLoadProgress(bytesRead = 150, totalBytes = 100).fraction)
    }

    @Test
    fun `counting stream reports single byte and bulk reads`() {
        val observed = mutableListOf<Long>()
        val stream = CountingInputStream(
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
        ) { observed += it }

        assertEquals(1, stream.read())
        assertEquals(3, stream.read(ByteArray(8), 0, 3))
        assertEquals(4L, stream.count)
        assertEquals(listOf(1L, 4L), observed)
    }
}
