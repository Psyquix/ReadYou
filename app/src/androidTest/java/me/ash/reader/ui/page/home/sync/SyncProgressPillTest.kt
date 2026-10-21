package me.ash.reader.ui.page.home.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

    private fun assertPillDescriptionContains(text: String) {
        rule.onNodeWithTag("sync-progress-pill", useUnmergedTree = true)
            .assertContentDescriptionContains(text, substring = true)
    }

    @Test
    fun fallbackShowsSyncingWhenRunningWithoutProgress() {
        show(progress = null, isSyncRunning = true)
        rule.onNodeWithText("Syncing…", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("sync-progress-bar", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("sync-progress-pill", useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
    }

    @Test
    fun localRunningShowsFeedAndNewArticleCounts() {
        show(progress(feedsDone = 4, feedsTotal = 6, articlesDone = 82), isSyncRunning = true)
        assertPillDescriptionContains("4 / 6 feeds")
        assertPillDescriptionContains("82 new articles")
        rule.onNodeWithTag("sync-progress-bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun completedShowsUpToDateSummary() {
        show(progress(status = SyncStatus.COMPLETED, feedsDone = 6, feedsTotal = 6, articlesDone = 82), false)
        rule.onNodeWithText("Up to date", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("6 / 6 feeds · 82 new articles", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("sync-progress-bar", useUnmergedTree = true).assertExists()
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
        assertPillDescriptionContains("1 / 1 feed")
        assertPillDescriptionContains("1 new article")
    }

    @Test
    fun zeroNewArticlesStillRenders() {
        show(progress(feedsDone = 1, feedsTotal = 6, articlesDone = 0), true)
        assertPillDescriptionContains("0 new articles")
    }

    @Test
    fun completedZeroWorkHasNoCountSubtitle() {
        show(progress(status = SyncStatus.COMPLETED, feedsDone = 0, feedsTotal = 0, articlesDone = 0), false)
        rule.onNodeWithText("Up to date", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("0 / 0 feeds", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("0 / 0 feeds", substring = true, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun completedUnknownTotalZeroWorkHasNoArticleSubtitle() {
        show(progress(status = SyncStatus.COMPLETED), false)
        rule.onNodeWithText("Up to date", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("0 articles found", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("0 articles found", substring = true, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun completedEmptyServerDiffHasNoArticleSubtitle() {
        show(progress(status = SyncStatus.COMPLETED, feedsTotal = 23, articlesDone = 0, articlesTotal = 0), false)
        rule.onNodeWithText("Up to date", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("0 articles", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("0 articles", substring = true, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun failedServerShowsCompletedArticleCount() {
        show(
            progress(
                status = SyncStatus.FAILED,
                feedsDone = 10,
                feedsTotal = 23,
                articlesDone = 100,
                articlesTotal = 410,
            ),
            false,
        )
        rule.onNodeWithText("Sync failed", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("100 articles · 23 feeds", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("410 articles", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun failedZeroDiffKeepsFeedContext() {
        show(
            progress(
                status = SyncStatus.FAILED,
                feedsDone = 0,
                feedsTotal = 23,
                articlesDone = 0,
                articlesTotal = 0,
            ),
            false,
        )
        rule.onNodeWithText("Sync failed", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("0 articles · 23 feeds", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun completedServerShowsDoneCountAndFeedContext() {
        show(
            progress(
                status = SyncStatus.COMPLETED,
                feedsDone = 23,
                feedsTotal = 23,
                articlesDone = 410,
                articlesTotal = 410,
            ),
            false,
        )
        rule.onNodeWithText("Up to date", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("410 articles · 23 feeds", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun exitTransitionKeepsPillUntilFinished() {
        val progressState =
            mutableStateOf<SyncProgress?>(
                progress(feedsDone = 4, feedsTotal = 6, articlesDone = 82)
            )

        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    SyncProgressPill(progress = progressState.value, isSyncRunning = false)
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        assertPillDescriptionContains("4 / 6 feeds")

        rule.runOnUiThread { progressState.value = null }
        rule.mainClock.advanceTimeBy(50)
        rule.onNodeWithTag("sync-progress-pill", useUnmergedTree = true).assertExists()

        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag("sync-progress-pill", useUnmergedTree = true).assertDoesNotExist()
    }
}
