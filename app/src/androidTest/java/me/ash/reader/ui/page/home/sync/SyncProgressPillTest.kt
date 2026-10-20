package me.ash.reader.ui.page.home.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import me.ash.reader.domain.service.SyncProgress
import me.ash.reader.domain.service.SyncStatus
import org.junit.Rule
import org.junit.Test

class SyncProgressPillTest {

    @get:Rule val rule = createComposeRule()

    private fun progress(
        status: SyncStatus = SyncStatus.RUNNING,
        feedsDone: Int = 0,
        feedsTotal: Int? = null,
        articlesDone: Int = 0,
        articlesTotal: Int? = null,
    ) = SyncProgress(
        sessionId = "test",
        status = status,
        feedsDone = feedsDone,
        feedsTotal = feedsTotal,
        articlesDone = articlesDone,
        articlesTotal = articlesTotal,
    )

    private fun show(progress: SyncProgress?, isSyncRunning: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    SyncProgressPill(progress = progress, isSyncRunning = isSyncRunning)
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun localRunningShowsFeedAndNewArticleCounts() {
        show(progress(feedsDone = 4, feedsTotal = 6, articlesDone = 82), isSyncRunning = true)
        rule.onNodeWithText("4 / 6 feeds", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("82 new articles", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("sync-progress-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun completedShowsUpToDateSummary() {
        show(progress(status = SyncStatus.COMPLETED, feedsDone = 6, feedsTotal = 6, articlesDone = 82), false)
        rule.onNodeWithText("Up to date", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("6 / 6 feeds · 82 new articles", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun failedHasNoProgressBar() {
        show(progress(status = SyncStatus.FAILED, feedsDone = 4, feedsTotal = 6), false)
        rule.onNodeWithText("Sync failed", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("sync-progress-bar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun oneFeedUsesSingularCopy() {
        show(progress(feedsDone = 1, feedsTotal = 1, articlesDone = 1), true)
        rule.onNodeWithText("1 / 1 feed", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("1 new article", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun zeroNewArticlesStillRenders() {
        show(progress(feedsDone = 1, feedsTotal = 6, articlesDone = 0), true)
        rule.onNodeWithText("0 new articles", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun completedZeroWorkHasNoCountSubtitle() {
        show(progress(status = SyncStatus.COMPLETED, feedsDone = 0, feedsTotal = 0, articlesDone = 0), false)
        rule.onNodeWithText("Up to date", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("0 / 0 feeds", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun completedEmptyServerDiffHasNoArticleSubtitle() {
        show(progress(status = SyncStatus.COMPLETED, feedsTotal = 23, articlesDone = 0, articlesTotal = 0), false)
        rule.onNodeWithText("Up to date", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("0 articles", useUnmergedTree = true).assertDoesNotExist()
    }
}
