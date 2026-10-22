package me.ash.reader.ui.page.home.sync

import me.ash.reader.domain.service.SyncProgress
import me.ash.reader.domain.service.SyncStatus

enum class SyncCountUnit { FEEDS, ARTICLES }

enum class SyncSubtitleKind { NEW_ARTICLES, FEED_COUNT, ARTICLES_FOUND, NONE }

data class SyncSummary(
    val feedsDone: Int,
    val feedsTotal: Int?,
    val articlesDone: Int,
    val articlesTotal: Int?,
)

sealed interface SyncPillModel {
    data class Running(
        val done: Int,
        val total: Int?,
        val unit: SyncCountUnit,
        val fraction: Float?,
        val subtitleKind: SyncSubtitleKind,
        val subtitleCount: Int,
    ) : SyncPillModel

    data class Completed(val summary: SyncSummary) : SyncPillModel

    data class Failed(val summary: SyncSummary, val errorMessage: String? = null) : SyncPillModel
}

fun SyncProgress.toPillModel(): SyncPillModel {
    val summary =
        SyncSummary(
            feedsDone = feedsDone.coerceAtLeast(0),
            feedsTotal = feedsTotal?.coerceAtLeast(0),
            articlesDone = articlesDone.coerceAtLeast(0),
            articlesTotal = articlesTotal?.coerceAtLeast(0),
        )
    return when (status) {
        SyncStatus.COMPLETED -> SyncPillModel.Completed(summary)
        SyncStatus.FAILED -> SyncPillModel.Failed(summary, errorMessage?.takeIf { it.isNotBlank() })
        SyncStatus.RUNNING -> runningModel(summary)
    }
}

private fun runningModel(summary: SyncSummary): SyncPillModel.Running {
    summary.articlesTotal?.let { total ->
        val done = summary.articlesDone.coerceIn(0, total)
        return SyncPillModel.Running(
            done = done,
            total = total,
            unit = SyncCountUnit.ARTICLES,
            fraction = fractionOf(done, total),
            subtitleKind = if (summary.feedsTotal != null) SyncSubtitleKind.FEED_COUNT else SyncSubtitleKind.NONE,
            subtitleCount = summary.feedsTotal ?: 0,
        )
    }
    summary.feedsTotal?.let { total ->
        val done = summary.feedsDone.coerceIn(0, total)
        return SyncPillModel.Running(
            done = done,
            total = total,
            unit = SyncCountUnit.FEEDS,
            fraction = fractionOf(done, total),
            subtitleKind = SyncSubtitleKind.NEW_ARTICLES,
            subtitleCount = summary.articlesDone,
        )
    }
    return SyncPillModel.Running(
        done = summary.articlesDone,
        total = null,
        unit = SyncCountUnit.ARTICLES,
        fraction = null,
        subtitleKind = SyncSubtitleKind.ARTICLES_FOUND,
        subtitleCount = summary.articlesDone,
    )
}

private fun fractionOf(done: Int, total: Int): Float =
    if (total == 0) 1f else done.toFloat() / total
