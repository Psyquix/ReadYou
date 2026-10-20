package me.ash.reader.domain.service

enum class SyncStatus { RUNNING, COMPLETED, FAILED }

data class SyncProgress(
    val sessionId: String,
    val status: SyncStatus = SyncStatus.RUNNING,
    val feedsDone: Int = 0,
    val feedsTotal: Int? = null,
    val articlesDone: Int = 0,
    val articlesTotal: Int? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

interface SyncProgressSession {
    fun setFeedTotal(total: Int)
    fun setArticleTotal(total: Int?)
    fun feedCompleted(newArticles: Int)
    fun articleBatchCompleted(count: Int)
    fun finish(success: Boolean)
    fun cancel()
}
