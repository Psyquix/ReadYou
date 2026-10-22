package me.ash.reader.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleLinkChunkTest {

    @Test
    fun `large feeds are queried in bounded link chunks`() {
        val links = (1..1_440).map { "https://example.com/$it" }

        val chunks = articleLinkQueryChunks(links)

        assertEquals(listOf(500, 500, 440), chunks.map { it.size })
        assertEquals(links.distinct(), chunks.flatten())
        assertTrue(chunks.all { it.size <= ARTICLE_LINK_QUERY_CHUNK_SIZE })
    }

    @Test
    fun `duplicate links are removed before querying`() {
        val chunks = articleLinkQueryChunks(listOf("a", "a", "b"))

        assertEquals(listOf(listOf("a", "b")), chunks)
    }
}
