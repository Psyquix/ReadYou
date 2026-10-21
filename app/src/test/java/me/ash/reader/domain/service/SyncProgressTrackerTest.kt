package me.ash.reader.domain.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncProgressTrackerTest {
    @Test
    fun `start exposes a running session`() = runTest {
        val tracker = SyncProgressTracker(this)
        val state = tracker.start("w1").let { tracker.state.value!! }
        assertEquals("w1", state.sessionId)
        assertEquals(SyncStatus.RUNNING, state.status)
        assertEquals(0, state.feedsDone)
        assertNull(state.feedsTotal)
    }

    @Test
    fun `feed completion increments feeds and articles`() = runTest {
        val tracker = SyncProgressTracker(this)
        val session = tracker.start("w1")
        session.setFeedTotal(6)
        session.feedCompleted(newArticles = 10)
        session.feedCompleted(newArticles = 5)
        assertEquals(2, tracker.state.value!!.feedsDone)
        assertEquals(6, tracker.state.value!!.feedsTotal)
        assertEquals(15, tracker.state.value!!.articlesDone)
    }

    @Test
    fun `article batches increment articles only`() = runTest {
        val tracker = SyncProgressTracker(this)
        val session = tracker.start("w1")
        session.setArticleTotal(410)
        session.articleBatchCompleted(100)
        assertEquals(100, tracker.state.value!!.articlesDone)
        assertEquals(410, tracker.state.value!!.articlesTotal)
        assertEquals(0, tracker.state.value!!.feedsDone)
    }

    @Test
    fun `completion clears after 1500 ms`() = runTest {
        val tracker = SyncProgressTracker(this)
        tracker.start("w1").finish(true)
        assertEquals(SyncStatus.COMPLETED, tracker.state.value!!.status)
        advanceTimeBy(1_499)
        runCurrent()
        assertNotNull(tracker.state.value)
        advanceTimeBy(2)
        runCurrent()
        assertNull(tracker.state.value)
    }

    @Test
    fun `failure clears after 2500 ms`() = runTest {
        val tracker = SyncProgressTracker(this)
        tracker.start("w1").finish(false)
        assertEquals(SyncStatus.FAILED, tracker.state.value!!.status)
        advanceTimeBy(2_499)
        runCurrent()
        assertNotNull(tracker.state.value)
        advanceTimeBy(2)
        runCurrent()
        assertNull(tracker.state.value)
    }

    @Test
    fun `stale session cannot update or finish the active state`() = runTest {
        val tracker = SyncProgressTracker(this)
        val first = tracker.start("w1")
        tracker.start("w2")
        first.setFeedTotal(6)
        first.feedCompleted(10)
        first.finish(true)
        assertEquals("w2", tracker.state.value!!.sessionId)
        assertEquals(SyncStatus.RUNNING, tracker.state.value!!.status)
        assertEquals(0, tracker.state.value!!.feedsDone)
    }

    @Test
    fun `new session cancels the previous completion hold`() = runTest {
        val tracker = SyncProgressTracker(this)
        tracker.start("w1").finish(true)
        advanceTimeBy(1_000)
        tracker.start("w2")
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("w2", tracker.state.value!!.sessionId)
        assertEquals(SyncStatus.RUNNING, tracker.state.value!!.status)
    }

    @Test
    fun `restarting the same session cancels the previous completion hold`() = runTest {
        val tracker = SyncProgressTracker(this)
        tracker.start("w1").finish(true)
        advanceTimeBy(1_000)
        tracker.start("w1")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("w1", tracker.state.value!!.sessionId)
        assertEquals(SyncStatus.RUNNING, tracker.state.value!!.status)
    }

    @Test
    fun `negative article counts are ignored`() = runTest {
        val tracker = SyncProgressTracker(this)
        val session = tracker.start("w1")
        session.feedCompleted(newArticles = -5)
        session.articleBatchCompleted(-2)
        assertEquals(1, tracker.state.value!!.feedsDone)
        assertEquals(0, tracker.state.value!!.articlesDone)

        session.feedCompleted(newArticles = 10)
        session.feedCompleted(newArticles = -5)
        session.articleBatchCompleted(-2)
        assertEquals(3, tracker.state.value!!.feedsDone)
        assertEquals(10, tracker.state.value!!.articlesDone)
    }

    @Test
    fun `negative totals are clamped to zero`() = runTest {
        val tracker = SyncProgressTracker(this)
        val session = tracker.start("w1")
        session.setFeedTotal(-3)
        assertEquals(0, tracker.state.value!!.feedsTotal)
        session.setArticleTotal(-5)
        assertEquals(0, tracker.state.value!!.articlesTotal)
    }

    @Test
    fun `stale session cancel does not clear the active session`() = runTest {
        val tracker = SyncProgressTracker(this)
        val first = tracker.start("w1")
        tracker.start("w2")
        first.cancel()
        assertEquals("w2", tracker.state.value!!.sessionId)
        assertEquals(SyncStatus.RUNNING, tracker.state.value!!.status)
    }

    @Test
    fun `cancel clears immediately`() = runTest {
        val tracker = SyncProgressTracker(this)
        tracker.start("w1").cancel()
        assertNull(tracker.state.value)
    }
}
