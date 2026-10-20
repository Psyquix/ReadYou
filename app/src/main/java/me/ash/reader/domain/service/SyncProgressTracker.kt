package me.ash.reader.domain.service

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.di.ApplicationScope

/**
 * Holds the in-process progress of the active sync session.
 *
 * Sessions are identified by an id so that a stale session cannot update the state of a newer one.
 * Completed and failed sessions remain visible for a short hold before being dismissed
 * automatically.
 */
@Singleton
class SyncProgressTracker
@Inject
constructor(
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val lock = Any()
    private val _state = MutableStateFlow<SyncProgress?>(null)
    val state: StateFlow<SyncProgress?> = _state.asStateFlow()

    private var activeSessionId: String? = null
    private var dismissJob: Job? = null

    fun start(sessionId: String): SyncProgressSession {
        synchronized(lock) {
            dismissJob?.cancel()
            dismissJob = null
            activeSessionId = sessionId
            _state.value = SyncProgress(sessionId = sessionId)
        }
        return Session(sessionId)
    }

    private fun update(sessionId: String, transform: (SyncProgress) -> SyncProgress) {
        synchronized(lock) {
            val current = _state.value ?: return
            if (activeSessionId != sessionId || current.sessionId != sessionId) return
            _state.value = transform(current)
        }
    }

    private fun complete(sessionId: String, success: Boolean) {
        synchronized(lock) {
            val current = _state.value ?: return
            if (activeSessionId != sessionId || current.sessionId != sessionId) return

            dismissJob?.cancel()
            _state.value =
                current.copy(
                    status = if (success) SyncStatus.COMPLETED else SyncStatus.FAILED,
                    finishedAt = System.currentTimeMillis(),
                )

            val holdMillis = if (success) COMPLETED_HOLD_MILLIS else FAILED_HOLD_MILLIS
            dismissJob =
                scope.launch {
                    delay(holdMillis)
                    synchronized(lock) {
                        if (activeSessionId == sessionId) {
                            activeSessionId = null
                            _state.value = null
                        }
                    }
                }
        }
    }

    private fun cancel(sessionId: String) {
        synchronized(lock) {
            if (activeSessionId != sessionId) return
            dismissJob?.cancel()
            dismissJob = null
            activeSessionId = null
            _state.value = null
        }
    }

    private inner class Session(private val sessionId: String) : SyncProgressSession {
        override fun setFeedTotal(total: Int) = update(sessionId) { it.copy(feedsTotal = total) }

        override fun setArticleTotal(total: Int?) =
            update(sessionId) { it.copy(articlesTotal = total) }

        override fun feedCompleted(newArticles: Int) =
            update(sessionId) {
                it.copy(
                    feedsDone = it.feedsDone + 1,
                    articlesDone = it.articlesDone + newArticles.coerceAtLeast(0),
                )
            }

        override fun articleBatchCompleted(count: Int) =
            update(sessionId) { it.copy(articlesDone = it.articlesDone + count.coerceAtLeast(0)) }

        override fun finish(success: Boolean) = complete(sessionId, success)

        override fun cancel() = cancel(sessionId)
    }

    private companion object {
        const val COMPLETED_HOLD_MILLIS = 1_500L
        const val FAILED_HOLD_MILLIS = 2_500L
    }
}
