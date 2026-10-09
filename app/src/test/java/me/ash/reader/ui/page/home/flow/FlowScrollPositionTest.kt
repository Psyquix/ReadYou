package me.ash.reader.ui.page.home.flow

import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/**
 * Fork patch 4: per-feed article-list scroll memory.
 * Pure JVM test — no Android, no DataStore, no Compose.
 */
class FlowScrollPositionTest {

    private fun article(id: String) = ArticleFlowItem.Article(
        ArticleWithFeed(
            article = Article(
                id = id,
                date = Date(1_000_000L),
                title = "title $id",
                rawDescription = "",
                shortDescription = "",
                link = "https://example.com/$id",
                feedId = "feed",
                accountId = 1,
                isUnread = true,
            ),
            feed = Feed(id = "feed", name = "feed", url = "https://example.com", groupId = "group", accountId = 1),
        )
    )

    private fun header() = ArticleFlowItem.Date("2024-01-01", false)

    @Test
    fun `same inputs produce the same key`() {
        assertEquals(
            flowScrollKey(1, 1, "feed-a", null, false),
            flowScrollKey(1, 1, "feed-a", null, false),
        )
    }

    @Test
    fun `key differs per feed group filter account and sort`() {
        val base = flowScrollKey(1, 1, "feed-a", null, false)
        assertEquals(base, flowScrollKey(1, 1, "feed-a", null, false))
        // Each dimension must change the key.
        assert(base != flowScrollKey(1, 1, "feed-b", null, false))
        assert(base != flowScrollKey(1, 1, null, "group-a", false))
        assert(base != flowScrollKey(1, 2, "feed-a", null, false))
        assert(base != flowScrollKey(2, 1, "feed-a", null, false))
        assert(base != flowScrollKey(1, 1, "feed-a", null, true))
    }

    @Test
    fun `position encodes and decodes`() {
        val pos = FlowScrollPosition(articleId = "abc123", index = 42, offset = -200)
        assertEquals(pos, decodeFlowScrollPosition(pos.encode()))
    }

    @Test
    fun `position round-trips when the id contains separators`() {
        val pos = FlowScrollPosition(articleId = "a|b\nc", index = 3, offset = 7)
        assertEquals(pos, decodeFlowScrollPosition(pos.encode()))
    }

    @Test
    fun `malformed input decodes to null`() {
        assertNull(decodeFlowScrollPosition(null))
        assertNull(decodeFlowScrollPosition(""))
        assertNull(decodeFlowScrollPosition("   "))
        assertNull(decodeFlowScrollPosition("not-a-position"))
    }

    @Test
    fun `restore finds the article id at its new index`() {
        val items = listOf(article("new-1"), article("new-2"), article("saved"), article("old"))
        val saved = FlowScrollPosition(articleId = "saved", index = 0, offset = -200)

        assertEquals(2, findRestoreIndex(items, saved))
    }

    @Test
    fun `restore falls back to the saved index when the id is gone`() {
        val items = listOf(article("a"), article("b"), article("c"))
        val saved = FlowScrollPosition(articleId = "deleted", index = 1, offset = 0)

        assertEquals(1, findRestoreIndex(items, saved))
    }

    @Test
    fun `restore clamps the fallback index and skips headers`() {
        val items = listOf(header(), article("a"), article("b"))
        // Fallback far beyond the end must clamp, and headers count as positions.
        val saved = FlowScrollPosition(articleId = "deleted", index = 99, offset = 0)

        assertEquals(2, findRestoreIndex(items, saved))
        assertEquals(0, findRestoreIndex(emptyList(), saved))
    }
}
