package me.ash.reader.ui.page.home.sync

import me.ash.reader.domain.service.SyncProgress
import me.ash.reader.domain.service.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncProgressDisplayTest {

    private fun progress(
        status: SyncStatus = SyncStatus.RUNNING,
        feedsDone: Int = 0,
        feedsTotal: Int? = null,
        articlesDone: Int = 0,
        articlesTotal: Int? = null,
        errorMessage: String? = null,
    ) = SyncProgress(
        sessionId = "test",
        status = status,
        feedsDone = feedsDone,
        feedsTotal = feedsTotal,
        articlesDone = articlesDone,
        articlesTotal = articlesTotal,
        errorMessage = errorMessage,
    )

    @Test
    fun `local running uses feeds and new articles`() {
        val model = progress(feedsDone = 4, feedsTotal = 6, articlesDone = 82).toPillModel()
        assertEquals(
            SyncPillModel.Running(
                done = 4, total = 6, unit = SyncCountUnit.FEEDS,
                fraction = 4f / 6f, subtitleKind = SyncSubtitleKind.NEW_ARTICLES,
                subtitleCount = 82,
            ),
            model,
        )
    }

    @Test
    fun `server running with a total uses articles`() {
        val model = progress(articlesDone = 82, articlesTotal = 410, feedsTotal = 23).toPillModel()
        assertEquals(SyncCountUnit.ARTICLES, (model as SyncPillModel.Running).unit)
        assertEquals(82, model.done)
        assertEquals(410, model.total)
        assertEquals(23, model.subtitleCount)
        assertEquals(SyncSubtitleKind.FEED_COUNT, model.subtitleKind)
    }

    @Test
    fun `unknown total is indeterminate`() {
        val model = progress(articlesDone = 82).toPillModel() as SyncPillModel.Running
        assertNull(model.total)
        assertNull(model.fraction)
        assertEquals(SyncSubtitleKind.ARTICLES_FOUND, model.subtitleKind)
    }

    @Test
    fun `local zero new articles stays valid`() {
        val model = progress(feedsDone = 1, feedsTotal = 6, articlesDone = 0).toPillModel()
            as SyncPillModel.Running
        assertEquals(1, model.done)
        assertEquals(0, model.subtitleCount)
        assertEquals(1f / 6f, model.fraction)
    }

    @Test
    fun `zero articles does not divide by zero`() {
        val model = progress(articlesDone = 0, articlesTotal = 0, feedsTotal = 23).toPillModel()
            as SyncPillModel.Running
        assertEquals(1f, model.fraction)
    }

    @Test
    fun `zero feeds does not divide by zero`() {
        val model = progress(feedsDone = 0, feedsTotal = 0, articlesDone = 0).toPillModel()
            as SyncPillModel.Running
        assertEquals(1f, model.fraction)
    }

    @Test
    fun `counts over the total are clamped`() {
        val model = progress(feedsDone = 7, feedsTotal = 6, articlesDone = 82).toPillModel()
            as SyncPillModel.Running
        assertEquals(6, model.done)
        assertEquals(1f, model.fraction)
    }

    @Test
    fun `article counts over the total are clamped`() {
        val model = progress(articlesDone = 500, articlesTotal = 410).toPillModel()
            as SyncPillModel.Running
        assertEquals(410, model.done)
        assertEquals(410, model.total)
        assertEquals(1f, model.fraction)
    }

    @Test
    fun `article total without feeds uses the none subtitle sentinel`() {
        val model = progress(articlesDone = 82, articlesTotal = 410).toPillModel()
            as SyncPillModel.Running
        assertEquals(SyncSubtitleKind.NONE, model.subtitleKind)
        assertEquals(0, model.subtitleCount)
    }

    @Test
    fun `negative article total is treated as zero without throwing`() {
        val model = progress(articlesDone = 5, articlesTotal = -1).toPillModel()
            as SyncPillModel.Running
        assertEquals(0, model.done)
        assertEquals(0, model.total)
        assertEquals(1f, model.fraction)
    }

    @Test
    fun `negative feed total is treated as zero without throwing`() {
        val model = progress(feedsDone = 5, feedsTotal = -1).toPillModel()
            as SyncPillModel.Running
        assertEquals(0, model.done)
        assertEquals(0, model.total)
        assertEquals(1f, model.fraction)
    }

    @Test
    fun `negative counts are clamped in terminal summaries`() {
        val completed = progress(
            status = SyncStatus.COMPLETED,
            feedsDone = -2,
            feedsTotal = -6,
            articlesDone = -82,
            articlesTotal = -410,
        ).toPillModel() as SyncPillModel.Completed
        assertEquals(0, completed.summary.feedsDone)
        assertEquals(0, completed.summary.feedsTotal)
        assertEquals(0, completed.summary.articlesDone)
        assertEquals(0, completed.summary.articlesTotal)
    }

    @Test
    fun `completed and failed preserve the summary`() {
        val completed = progress(status = SyncStatus.COMPLETED, feedsDone = 6, feedsTotal = 6, articlesDone = 82)
            .toPillModel() as SyncPillModel.Completed
        assertEquals(6, completed.summary.feedsDone)
        val failed = progress(status = SyncStatus.FAILED, feedsDone = 4, feedsTotal = 6)
            .toPillModel() as SyncPillModel.Failed
        assertEquals(4, failed.summary.feedsDone)
    }

    @Test
    fun `failed preserves the error message`() {
        val failed =
            progress(
                status = SyncStatus.FAILED,
                feedsDone = 5,
                feedsTotal = 6,
                errorMessage = "PLIC: too many SQL variables",
            ).toPillModel() as SyncPillModel.Failed

        assertEquals("PLIC: too many SQL variables", failed.errorMessage)
    }
}
