package me.ash.reader.ui.page.home.sync

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
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
            ContentTransform(
                targetContentEnter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                initialContentExit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
                sizeTransform = SizeTransform(clip = false),
            )
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

    val animatedDone by
        animateIntAsState(
            targetValue = (model as? SyncPillModel.Running)?.done ?: 0,
            animationSpec = tween(durationMillis = 250),
            label = "sync-done",
        )
    val animatedSubtitleCount by
        animateIntAsState(
            targetValue =
                (model as? SyncPillModel.Running)
                    ?.takeIf {
                        it.subtitleKind == SyncSubtitleKind.NEW_ARTICLES ||
                            it.subtitleKind == SyncSubtitleKind.ARTICLES_FOUND
                    }
                    ?.subtitleCount ?: 0,
            animationSpec = tween(durationMillis = 250),
            label = "sync-subtitle-count",
        )

    val title: String
    val count: String?
    val subtitle: String?
    val rawCount: String?
    val rawSubtitle: String?
    val errorText: String?
    when (model) {
        is SyncPillModel.Running -> {
            errorText = null
            if (model.total == null) {
                title = stringResource(R.string.syncing)
                count = null
                rawCount = null
            } else {
                title = stringResource(R.string.synchronous)
                count =
                    when (model.unit) {
                        SyncCountUnit.FEEDS ->
                            pluralStringResource(
                                R.plurals.sync_feeds_progress,
                                model.total,
                                animatedDone,
                                model.total,
                            )
                        SyncCountUnit.ARTICLES ->
                            pluralStringResource(
                                R.plurals.sync_articles_progress,
                                model.total,
                                animatedDone,
                                model.total,
                            )
                    }
                rawCount =
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
            rawSubtitle =
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
            subtitle =
                when (model.subtitleKind) {
                    SyncSubtitleKind.NEW_ARTICLES, SyncSubtitleKind.ARTICLES_FOUND ->
                        pluralStringResource(
                            if (model.subtitleKind == SyncSubtitleKind.NEW_ARTICLES) {
                                R.plurals.sync_new_articles
                            } else {
                                R.plurals.sync_articles_found
                            },
                            animatedSubtitleCount,
                            animatedSubtitleCount,
                        )
                    SyncSubtitleKind.FEED_COUNT -> rawSubtitle
                    SyncSubtitleKind.NONE -> null
                }
        }
        is SyncPillModel.Completed -> {
            title = stringResource(R.string.sync_up_to_date)
            count = null
            rawCount = null
            subtitle = summaryText(model.summary, suppressZeroWork = true)
            rawSubtitle = subtitle
            errorText = null
        }
        is SyncPillModel.Failed -> {
            title = stringResource(R.string.sync_failed)
            count = null
            rawCount = null
            subtitle = summaryText(model.summary, suppressZeroWork = false)
            rawSubtitle = subtitle
            errorText = model.errorMessage
        }
    }

    val hideAnimatedTextSemantics = model is SyncPillModel.Running
    val contentDescription = listOfNotNull(title, rawCount, rawSubtitle, errorText).joinToString(", ")

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
                val leadingState =
                    when (model) {
                        is SyncPillModel.Running -> SyncLeadingState.RUNNING
                        is SyncPillModel.Completed -> SyncLeadingState.COMPLETED
                        is SyncPillModel.Failed -> SyncLeadingState.FAILED
                    }
                Box(
                    modifier = Modifier.size(22.dp).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = leadingState,
                        animationSpec = tween(durationMillis = 250),
                        label = "sync-leading",
                    ) { state ->
                        when (state) {
                            SyncLeadingState.RUNNING ->
                                LoadingIndicator(
                                    color = contentColor,
                                    modifier = Modifier.size(22.dp),
                                )
                            SyncLeadingState.COMPLETED ->
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            SyncLeadingState.FAILED ->
                                Icon(
                                    imageVector = Icons.Rounded.SyncProblem,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                        }
                    }
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
                                modifier =
                                    if (hideAnimatedTextSemantics) {
                                        Modifier.clearAndSetSemantics {}
                                    } else {
                                        Modifier
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            modifier =
                                if (hideAnimatedTextSemantics) {
                                    Modifier.clearAndSetSemantics {}
                                } else {
                                    Modifier
                                },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (errorText != null) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val barFraction =
                when (model) {
                    is SyncPillModel.Running -> model.fraction
                    is SyncPillModel.Completed -> 1f
                    is SyncPillModel.Failed -> null
                }
            if (model !is SyncPillModel.Failed) {
                PillProgressBar(fraction = barFraction)
            }
        }
    }
}

private enum class SyncLeadingState { RUNNING, COMPLETED, FAILED }

@Composable
private fun PillProgressBar(fraction: Float?) {
    Spacer(modifier = Modifier.height(8.dp))
    val color = MaterialTheme.colorScheme.primary
    val trackColor = color.copy(alpha = 0.2f)
    val modifier =
        Modifier.fillMaxWidth()
            .height(3.dp)
            .clearAndSetSemantics {
                this[SemanticsProperties.TestTag] = "sync-progress-bar"
            }
    if (fraction != null) {
        val animatedFraction by
            animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(durationMillis = 300),
                label = "sync-progress-fraction",
            )
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    } else {
        LinearProgressIndicator(
            modifier = modifier,
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
            if (suppressZeroWork && summary.articlesDone == 0) {
                null
            } else {
                pluralStringResource(
                    R.plurals.sync_articles_found,
                    summary.articlesDone,
                    summary.articlesDone,
                )
            }
    }
}
