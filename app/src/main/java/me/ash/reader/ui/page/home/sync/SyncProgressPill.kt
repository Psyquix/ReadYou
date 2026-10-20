package me.ash.reader.ui.page.home.sync

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.domain.service.SyncProgress
import me.ash.reader.infrastructure.preference.LocalDetailedSyncIndicator

@Composable
fun SyncProgressPillHost(
    isSyncRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!LocalDetailedSyncIndicator.current.value) return

    val viewModel: SyncProgressViewModel = hiltViewModel()
    val progress by viewModel.progress.collectAsState()

    Box(modifier = modifier) {
        SyncProgressPill(
            progress = progress,
            isSyncRunning = isSyncRunning,
            modifier =
                Modifier.padding(horizontal = 24.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncProgressPill(
    progress: SyncProgress?,
    isSyncRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val fallback =
        SyncPillModel.Running(
            done = 0,
            total = null,
            unit = SyncCountUnit.ARTICLES,
            fraction = null,
            subtitleKind = SyncSubtitleKind.NONE,
            subtitleCount = 0,
        )
    val model = progress?.toPillModel() ?: if (isSyncRunning) fallback else null

    AnimatedContent(
        targetState = model,
        modifier = modifier,
        transitionSpec = {
            (fadeIn() + slideInVertically(initialOffsetY = { -it / 2 })) togetherWith
                (fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }))
        },
        contentKey = { it != null },
        label = "sync-progress-pill",
    ) { targetModel ->
        if (targetModel != null) {
            PillContent(model = targetModel)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PillContent(model: SyncPillModel) {
    val isFailed = model is SyncPillModel.Failed
    val containerColor =
        if (isFailed) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryFixedDim
    val contentColor =
        if (isFailed) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryFixedVariant

    val title: String
    val count: String?
    val subtitle: String?
    when (model) {
        is SyncPillModel.Running -> {
            if (model.total == null) {
                title = stringResource(R.string.syncing)
                count = null
            } else {
                title = stringResource(R.string.synchronous)
                count =
                    when (model.unit) {
                        SyncCountUnit.FEEDS ->
                            pluralStringResource(
                                R.plurals.sync_feeds_progress,
                                model.total,
                                model.done,
                                model.total,
                            )
                        SyncCountUnit.ARTICLES ->
                            pluralStringResource(
                                R.plurals.sync_articles_progress,
                                model.total,
                                model.done,
                                model.total,
                            )
                    }
            }
            subtitle =
                when (model.subtitleKind) {
                    SyncSubtitleKind.NEW_ARTICLES ->
                        pluralStringResource(
                            R.plurals.sync_new_articles,
                            model.subtitleCount,
                            model.subtitleCount,
                        )
                    SyncSubtitleKind.FEED_COUNT ->
                        pluralStringResource(
                            R.plurals.sync_feed_count,
                            model.subtitleCount,
                            model.subtitleCount,
                        )
                    SyncSubtitleKind.ARTICLES_FOUND ->
                        pluralStringResource(
                            R.plurals.sync_articles_found,
                            model.subtitleCount,
                            model.subtitleCount,
                        )
                    SyncSubtitleKind.NONE -> null
                }
        }
        is SyncPillModel.Completed -> {
            title = stringResource(R.string.sync_up_to_date)
            count = null
            subtitle = summaryText(model.summary, suppressZeroWork = true)
        }
        is SyncPillModel.Failed -> {
            title = stringResource(R.string.sync_failed)
            count = null
            subtitle = summaryText(model.summary, suppressZeroWork = false)
        }
    }

    val contentDescription = listOfNotNull(title, count, subtitle).joinToString(", ")

    Surface(
        modifier =
            Modifier.testTag("sync-progress-pill")
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                    this.contentDescription = contentDescription
                },
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (model) {
                    is SyncPillModel.Running ->
                        LoadingIndicator(
                            color = contentColor,
                            modifier = Modifier.size(22.dp),
                        )
                    is SyncPillModel.Completed ->
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    is SyncPillModel.Failed ->
                        Icon(
                            imageVector = Icons.Rounded.SyncProblem,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (count != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = count,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            when (model) {
                is SyncPillModel.Running -> PillProgressBar(fraction = model.fraction)
                is SyncPillModel.Completed -> PillProgressBar(fraction = 1f)
                is SyncPillModel.Failed -> Unit
            }
        }
    }
}

@Composable
private fun PillProgressBar(fraction: Float?) {
    Spacer(modifier = Modifier.height(8.dp))
    val color = MaterialTheme.colorScheme.primary
    val trackColor = color.copy(alpha = 0.2f)
    if (fraction != null) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier =
                Modifier.fillMaxWidth()
                    .height(3.dp)
                    .testTag("sync-progress-bar"),
            color = color,
            trackColor = trackColor,
        )
    } else {
        LinearProgressIndicator(
            modifier =
                Modifier.fillMaxWidth()
                    .height(3.dp)
                    .testTag("sync-progress-bar"),
            color = color,
            trackColor = trackColor,
        )
    }
}

@Composable
private fun summaryText(summary: SyncSummary, suppressZeroWork: Boolean): String? {
    val articlesTotal = summary.articlesTotal
    return when {
        articlesTotal != null -> {
            if (suppressZeroWork && articlesTotal == 0) {
                null
            } else {
                val articles =
                    pluralStringResource(
                        R.plurals.sync_articles_count,
                        summary.articlesDone,
                        summary.articlesDone,
                    )
                val feedsTotal = summary.feedsTotal
                if (feedsTotal == null) {
                    articles
                } else {
                    val feeds =
                        pluralStringResource(
                            R.plurals.sync_feed_count,
                            feedsTotal,
                            feedsTotal,
                        )
                    "$articles · $feeds"
                }
            }
        }
        summary.feedsTotal != null -> {
            if (suppressZeroWork && summary.feedsTotal == 0) {
                null
            } else {
                val feeds =
                    pluralStringResource(
                        R.plurals.sync_feeds_progress,
                        summary.feedsTotal,
                        summary.feedsDone,
                        summary.feedsTotal,
                    )
                val newArticles =
                    pluralStringResource(
                        R.plurals.sync_new_articles,
                        summary.articlesDone,
                        summary.articlesDone,
                    )
                "$feeds · $newArticles"
            }
        }
        else ->
            pluralStringResource(
                R.plurals.sync_articles_found,
                summary.articlesDone,
                summary.articlesDone,
            )
    }
}
