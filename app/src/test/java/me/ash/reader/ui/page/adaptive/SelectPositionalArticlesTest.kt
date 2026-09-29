package me.ash.reader.ui.page.adaptive

import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/**
 * Fork patch 3: covers the selection rules behind "mark above/below as unread".
 * Pure JVM test — no Android, no mocks, no emulator.
 */
class SelectPositionalArticlesTest {

    private val pivot = Date(1_000_000L)

    private fun article(
        id: String,
        date: Long,
        isUnread: Boolean = false,
    ) = ArticleFlowItem.Article(
        ArticleWithFeed(
            article =
                Article(
                    id = id,
                    date = Date(date),
                    title = "title $id",
                    rawDescription = "",
                    shortDescription = "",
                    link = "https://example.com/$id",
                    feedId = "feed",
                    accountId = 1,
                    isUnread = isUnread,
                ),
            feed = Feed(id = "feed", name = "feed", url = "https://example.com", groupId = "group", accountId = 1),
        )
    )

    private fun header(date: Long) = ArticleFlowItem.Date(date.toString(), false)

    /** Resolves effective read state from the article's own flag, as DiffMapHolder does. */
    private val fromDb: (ArticleWithFeed) -> Boolean = { it.article.isUnread }

    @Test
    fun `isBefore true selects only articles dated after the pivot`() {
        // Read articles, so the only thing that can exclude them is the date.
        val items = listOf(
            article("older", 500_000),
            article("newer", 1_500_000),
        )

        val selected =
            selectPositionalArticles(items, pivot, isBefore = true, targetUnread = true, isCurrentlyUnread = fromDb)

        assertEquals(listOf("older"), selected.map { it.article.id })
    }

    @Test
    fun `isBefore false selects only articles dated before the pivot`() {
        val items = listOf(
            article("older", 500_000),
            article("newer", 1_500_000),
        )

        val selected =
            selectPositionalArticles(items, pivot, isBefore = false, targetUnread = true, isCurrentlyUnread = fromDb)

        assertEquals(listOf("newer"), selected.map { it.article.id })
    }

    @Test
    fun `targetUnread true selects only currently read articles`() {
        val items = listOf(
            article("already-unread", 500_000, isUnread = true),
            article("already-read", 600_000, isUnread = false),
        )

        val selected =
            selectPositionalArticles(items, pivot, isBefore = true, targetUnread = true, isCurrentlyUnread = fromDb)

        assertEquals(listOf("already-read"), selected.map { it.article.id })
    }

    @Test
    fun `targetUnread false selects only currently unread articles`() {
        val items = listOf(
            article("already-unread", 500_000, isUnread = true),
            article("already-read", 600_000, isUnread = false),
        )

        val selected =
            selectPositionalArticles(items, pivot, isBefore = true, targetUnread = false, isCurrentlyUnread = fromDb)

        assertEquals(listOf("already-unread"), selected.map { it.article.id })
    }

    @Test
    fun `articles dated exactly at the pivot are excluded from both sides`() {
        val items = listOf(article("same", 1_000_000))

        assertEquals(
            emptyList<String>(),
            selectPositionalArticles(items, pivot, isBefore = true, targetUnread = true, isCurrentlyUnread = fromDb)
                .map { it.article.id },
        )
        assertEquals(
            emptyList<String>(),
            selectPositionalArticles(items, pivot, isBefore = false, targetUnread = true, isCurrentlyUnread = fromDb)
                .map { it.article.id },
        )
    }

    @Test
    fun `date headers are ignored`() {
        val items = listOf(
            header(400_000),
            article("a", 500_000),
            header(600_000),
        )

        val selected =
            selectPositionalArticles(items, pivot, isBefore = true, targetUnread = true, isCurrentlyUnread = fromDb)

        assertEquals(listOf("a"), selected.map { it.article.id })
    }

    @Test
    fun `duplicate article ids are collapsed`() {
        val items = listOf(
            article("dupe", 500_000),
            article("dupe", 600_000),
        )

        val selected =
            selectPositionalArticles(items, pivot, isBefore = true, targetUnread = true, isCurrentlyUnread = fromDb)

        assertEquals(listOf("dupe"), selected.map { it.article.id })
    }

    @Test
    fun `a pending diff overrides the stored database value`() {
        // The article is read in the database, but a diff has it marked unread this
        // session. Marking above as unread must not touch it, or the user would never
        // be able to clear it.
        val items = listOf(article("pending-unread", 500_000, isUnread = false))
        val effectiveUnread: Set<String> = setOf("pending-unread")

        val selected =
            selectPositionalArticles(
                items,
                pivot,
                isBefore = true,
                targetUnread = true,
                isCurrentlyUnread = { it.article.id in effectiveUnread },
            )

        assertEquals(emptyList<String>(), selected.map { it.article.id })
    }
}
