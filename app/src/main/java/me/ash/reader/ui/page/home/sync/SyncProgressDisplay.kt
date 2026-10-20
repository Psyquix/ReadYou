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

    data class Failed(val summary: SyncSummary) : SyncPillModel
}

fun SyncProgress.toPillModel(): SyncPillModel {
    val summary =
        SyncSummary(
            feedsDone = feedsDone,
            feedsTotal = feedsTotal,
            articlesDone = articlesDone,
            articlesTotal = articlesTotal,
        )
    return when (status) {
        SyncStatus.COMPLETED -> SyncPillModel.Completed(summary)
        SyncStatus.FAILED -> SyncPillModel.Failed(summary)
        SyncStatus.RUNNING -> runningModel()
    }
}

private fun SyncProgress.runningModel(): SyncPillModel.Running {
    articlesTotal?.let { total ->
        val done = articlesDone.coerceIn(0, total)
        return SyncPillModel.Running(
            done = done,
            total = total,
            unit = SyncCountUnit.ARTICLES,
            fraction = fractionOf(done, total),
            subtitleKind = if (feedsTotal != null) SyncSubtitleKind.FEED_COUNT else SyncSubtitleKind.NONE,
            subtitleCount = feedsTotal ?: 0,
        )
    }
    feedsTotal?.let { total ->
        val done = feedsDone.coerceIn(0, total)
        return SyncPillModel.Running(
            done = done,
            total = total,
            unit = SyncCountUnit.FEEDS,
            fraction = fractionOf(done, total),
            subtitleKind = SyncSubtitleKind.NEW_ARTICLES,
            subtitleCount = articlesDone,
        )
    }
    return SyncPillModel.Running(
        done = articlesDone.coerceAtLeast(0),
        total = null,
        unit = SyncCountUnit.ARTICLES,
        fraction = null,
        subtitleKind = SyncSubtitleKind.ARTICLES_FOUND,
        subtitleCount = articlesDone,
    )
}

private fun fractionOf(done: Int, total: Int): Float =
    if (total == 0) 1f else done.toFloat() / total
