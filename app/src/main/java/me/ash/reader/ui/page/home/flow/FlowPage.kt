package me.ash.reader.ui.page.home.flow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.data.PagerData
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.infrastructure.preference.LocalFlowArticleListDateStickyHeader
import me.ash.reader.infrastructure.preference.LocalFlowArticleListFeedIcon
import me.ash.reader.infrastructure.preference.LocalFlowArticleListTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarPadding
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarStyle
import me.ash.reader.infrastructure.preference.LocalFlowFilterBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalFlowTopBarTonalElevation
import me.ash.reader.infrastructure.preference.LocalMarkAsReadOnScroll
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.infrastructure.preference.LocalSettings
import me.ash.reader.infrastructure.preference.LocalSharedContent
import me.ash.reader.infrastructure.preference.LocalSortUnreadArticles
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.infrastructure.preference.SortUnreadArticlesPreference
import me.ash.reader.ui.component.FilterBar
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYExtensibleVisibility
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.scrollbar.VerticalScrollIndicatorFactory
import me.ash.reader.ui.component.scrollbar.drawVerticalScrollIndicator
import me.ash.reader.ui.component.scrollbar.scrollIndicator
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.motion.Direction
import me.ash.reader.ui.motion.sharedXAxisTransitionSlow
import me.ash.reader.ui.motion.sharedYAxisTransitionExpressive
import me.ash.reader.ui.page.adaptive.ArticleListReaderViewModel
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults
import me.ash.reader.ui.page.home.reading.PullToLoadDefaults.ContentOffsetMultiple
import me.ash.reader.ui.page.home.reading.PullToLoadState
import me.ash.reader.ui.page.home.reading.pullToLoad
import me.ash.reader.ui.page.home.reading.rememberPullToLoadState

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterialApi::class,
)
@Composable
fun FlowPage(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isTwoPane: Boolean,
    viewModel: ArticleListReaderViewModel,
    onNavigateUp: () -> Unit,
    navigateToArticle: (String, Int) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val articleListTonalElevation = LocalFlowArticleListTonalElevation.current
    val articleListFeedIcon = LocalFlowArticleListFeedIcon.current
    val articleListDateStickyHeader = LocalFlowArticleListDateStickyHeader.current
    val topBarTonalElevation = LocalFlowTopBarTonalElevation.current
    val filterBarStyle = LocalFlowFilterBarStyle.current
    val filterBarPadding = LocalFlowFilterBarPadding.current
    val filterBarTonalElevation = LocalFlowFilterBarTonalElevation.current
    val sharedContent = LocalSharedContent.current
    val markAsReadOnScroll = LocalMarkAsReadOnScroll.current.value
    val context = LocalContext.current

    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current

    val settings = LocalSettings.current
    val pullToSwitchFeed = settings.pullToSwitchFeed

    val flowUiState = viewModel.flowUiState.collectAsStateValue()
    if (flowUiState == null) return

    val pagerData: PagerData = flowUiState.pagerData

    val filterUiState = pagerData.filterState

    val listState = rememberSaveable(pagerData, saver = LazyListState.Saver) { LazyListState(0, 0) }

    // Fork patch 4: per-feed scroll memory — key identifying this list. Pure insertion.
    val scrollMemorySortEarliest =
        filterUiState.filter.isUnread() &&
            LocalSortUnreadArticles.current == SortUnreadArticlesPreference.Earliest
    val scrollMemoryKey =
        remember(
            filterUiState.feed?.id,
            filterUiState.group?.id,
            filterUiState.filter.index,
            scrollMemorySortEarliest,
        ) {
            flowScrollKey(filterUiState, sortEarliest = scrollMemorySortEarliest)
        }
    val scrollMemoryEnabled = filterUiState.searchContent.isNullOrBlank()
    var scrollMemoryReady by remember(scrollMemoryKey) { mutableStateOf(false) }

    val isTopBarElevated = topBarTonalElevation.value > 0
    val scrolledTopBarContainerColor =
        with(MaterialTheme.colorScheme) { if (isTopBarElevated) surfaceContainer else surface }

    val titleText =
        when {
            filterUiState.group != null -> filterUiState.group.name
            filterUiState.feed != null -> filterUiState.feed.name
            else -> filterUiState.filter.toName()
        }

    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var markAsRead by remember { mutableStateOf(false) }
    var onSearch by rememberSaveable { mutableStateOf(false) }

    var currentPullToLoadState: PullToLoadState? by remember { mutableStateOf(null) }
    var currentLoadAction: LoadAction? by remember { mutableStateOf(null) }

    val settleSpec = remember { spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy) }

    val lastVisibleIndex =
        remember(listState) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .filterNotNull()
        }

    val onToggleStarred: (ArticleWithFeed) -> Unit = remember {
        { article ->
            viewModel.updateStarredStatus(
                articleId = article.article.id,
                isStarred = !article.article.isStarred,
            )
        }
    }

    val onToggleRead: (ArticleWithFeed) -> Unit = remember {
        { articleWithFeed -> viewModel.diffMapHolder.updateDiff(articleWithFeed) }
    }

    val sortByEarliest =
        filterUiState.filter.isUnread() &&
            LocalSortUnreadArticles.current == SortUnreadArticlesPreference.Earliest

    val onMarkAboveAsRead: ((ArticleWithFeed) -> Unit)? =
        remember(sortByEarliest) {
            {
                viewModel.markAsReadFromListByDate(
                    date = it.article.date,
                    isBefore = sortByEarliest,
                )
            }
        }

    val onMarkBelowAsRead: ((ArticleWithFeed) -> Unit)? =
        remember(sortByEarliest) {
            {
                viewModel.markAsReadFromListByDate(
                    date = it.article.date,
                    isBefore = !sortByEarliest,
                )
            }
        }

    val onMarkAboveAsUnread: ((ArticleWithFeed) -> Unit)? =
        remember(sortByEarliest) {
            {
                viewModel.markAsUnreadFromListByDate(
                    date = it.article.date,
                    isBefore = sortByEarliest,
                )
            }
        }

    val onMarkBelowAsUnread: ((ArticleWithFeed) -> Unit)? =
        remember(sortByEarliest) {
            {
                viewModel.markAsUnreadFromListByDate(
                    date = it.article.date,
                    isBefore = !sortByEarliest,
                )
            }
        }

    val onShare: ((ArticleWithFeed) -> Unit)? = remember {
        { articleWithFeed ->
            with(articleWithFeed.article) { sharedContent.share(context, title, link) }
        }
    }

    LaunchedEffect(onSearch) {
        if (!onSearch) {
            keyboardController?.hide()
            viewModel.inputSearchContent(null)
        }
    }

    val topAppBarState = rememberTopAppBarState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val scrollAppBarToCollapsed =
        remember(topAppBarState) {
            {
                scope.launch {
                    val initial = topAppBarState.heightOffset
                    val target = topAppBarState.heightOffsetLimit
                    if (initial != target)
                        animate(
                            initialValue = initial,
                            targetValue = target,
                            initialVelocity = 0f,
                            animationSpec = settleSpec,
                        ) { value, _ ->
                            topAppBarState.heightOffset = value
                        }
                }
            }
        }

    val snapAppBarToCollapsed =
        remember(topAppBarState) {
            {
                scope.launch {
                    val initial = topAppBarState.heightOffset
                    val target = topAppBarState.heightOffsetLimit
                    if (initial != target) {
                        topAppBarState.heightOffset = target
                    }
                }
            }
        }

    val readerState = viewModel.readerStateStateFlow.collectAsStateValue()

    var pagingItems: LazyPagingItems<ArticleFlowItem>? by remember { mutableStateOf(null) }

    // Fork patch 4: persist the first visible article per feed (debounced).
    // Skipped until the restore below finishes, so a cold start never
    // overwrites the saved position with the initial top. Saves the first
    // visible *article* (date headers are skipped) plus the next article id
    // as a secondary anchor for when the anchor is later read away. Pure insertion.
    LaunchedEffect(listState, scrollMemoryKey, scrollMemoryEnabled) {
        if (!scrollMemoryEnabled) return@LaunchedEffect
        fun peekArticleId(index: Int): String? =
            try {
                (pagingItems?.peek(index) as? ArticleFlowItem.Article)
                    ?.articleWithFeed?.article?.id
            } catch (_: Exception) {
                null
            }
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.contentType == CONTENT_TYPE_ARTICLE }
                ?.index ?: -1
        }.collectLatest { anchorIdx ->
            delay(500)
            if (!scrollMemoryReady) return@collectLatest
            if (anchorIdx < 0) return@collectLatest
            val anchorId = peekArticleId(anchorIdx) ?: return@collectLatest
            val nextId =
                listState.layoutInfo.visibleItemsInfo
                    .filter { it.contentType == CONTENT_TYPE_ARTICLE && it.index > anchorIdx }
                    .firstOrNull()
                    ?.let { peekArticleId(it.index) }
            viewModel.saveFlowScrollPosition(
                scrollMemoryKey,
                FlowScrollPosition(
                    articleId = anchorId,
                    index = anchorIdx,
                    offset = 0,
                    nextArticleId = nextId,
                ),
            )
        }
    }

    if (isTwoPane) {
        LaunchedEffect(readerState) {
            if (readerState.articleId != null) {
                val articleId = readerState.articleId

                val itemList = pagingItems?.itemSnapshotList

                val index =
                    itemList?.indexOfFirst {
                        it is ArticleFlowItem.Article && it.articleWithFeed.article.id == articleId
                    } ?: -1

                if (index != -1) {
                    scrollAppBarToCollapsed()
                    listState.animateScrollToItem(index, scrollOffset = -200)
                }
            }
        }
    } else {
        LaunchedEffect(Unit) {
            if (readerState.articleId != null) {
                val articleId = readerState.articleId

                val itemList = pagingItems?.itemSnapshotList

                val index =
                    itemList?.indexOfFirst {
                        it is ArticleFlowItem.Article && it.articleWithFeed.article.id == articleId
                    } ?: -1

                if (index != -1) {
                    snapAppBarToCollapsed()
                    listState.requestScrollToItem(index, scrollOffset = -400)
                }
            }
        }
    }

    val isSyncing = viewModel.isSyncingFlow.collectAsStateValue()

    // Fork patch 4: restore the saved position once items load, and again after
    // a sync (upstream's scroll-to-top above is left untouched; restore simply
    // runs after it). An open article owns the position instead. Pure insertion.
    LaunchedEffect(pagerData.pager, scrollMemoryKey, scrollMemoryEnabled, isSyncing) {
        try {
            if (!scrollMemoryEnabled) return@LaunchedEffect
            if (readerState.articleId != null) return@LaunchedEffect
            val saved =
                viewModel.loadFlowScrollPosition(scrollMemoryKey)
                    ?: return@LaunchedEffect
            if (saved.index <= 0) return@LaunchedEffect
            repeat(40) {
                val items = pagingItems
                val count = items?.itemCount ?: 0
                if (count > 0 && items != null) {
                    if (listState.firstVisibleItemIndex != 0) return@LaunchedEffect
                    var target = -1
                    var nextTarget = -1
                    // Capped scan: a missing anchor must not force-load the
                    // whole list through paging; beyond the cap the clamped
                    // fallback below applies without touching those pages.
                    for (i in 0 until minOf(count, RESTORE_SCAN_CAP)) {
                        val id =
                            try {
                                (items.peek(i) as? ArticleFlowItem.Article)
                                    ?.articleWithFeed?.article?.id
                            } catch (_: Exception) {
                                null
                            } ?: continue
                        if (id == saved.articleId) {
                            target = i
                            break
                        }
                        if (nextTarget == -1 && id == saved.nextArticleId) {
                            nextTarget = i
                        }
                    }
                    val index =
                        when {
                            target != -1 -> target
                            nextTarget != -1 -> nextTarget
                            else -> saved.index.coerceIn(0, count - 1)
                        }
                    if (index > 0) listState.scrollToItem(index, saved.offset)
                    return@LaunchedEffect
                }
                delay(250)
            }
        } finally {
            scrollMemoryReady = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RYScaffold(
            containerTonalElevation = articleListTonalElevation.value.dp,
            topBar = {
                MaterialTheme(
                    colorScheme = MaterialTheme.colorScheme,
                    typography =
                        MaterialTheme.typography.copy(
                            headlineMedium = MaterialTheme.typography.displaySmall,
                            titleLarge =
                                MaterialTheme.typography.titleLarge.merge(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                        ),
                ) {
                    LargeTopAppBar(
                        modifier =
                            Modifier.clickable(
                                onClick = {
                                    scope.launch {
                                        if (listState.firstVisibleItemIndex != 0) {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ),
                        title = {
                            val textStyle = LocalTextStyle.current
                            val color = LocalContentColor.current
                            if (textStyle.fontSize.value > 18f) {
                                BasicText(
                                    modifier =
                                        Modifier.padding(
                                            start = if (articleListFeedIcon.value) 34.dp else 8.dp,
                                            end = 24.dp,
                                        ),
                                    text = titleText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = textStyle,
                                    color = { color },
                                    autoSize =
                                        TextAutoSize.StepBased(
                                            minFontSize = 28.sp,
                                            maxFontSize = textStyle.fontSize,
                                        ),
                                )
                            } else {
                                Text(
                                    text = titleText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        expandedHeight = 172.dp,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            FeedbackIconButton(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface,
                            ) {
                                onSearch = false
                                onNavigateUp()
                            }
                        },
                        actions = {
                            RYExtensibleVisibility(visible = !filterUiState.filter.isStarred()) {
                                FeedbackIconButton(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = stringResource(R.string.mark_all_as_read),
                                    tint =
                                        if (markAsRead) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                ) {
                                    if (markAsRead) {
                                        markAsRead = false
                                    } else {
                                        scope
                                            .launch {
                                                if (listState.firstVisibleItemIndex != 0) {
                                                    listState.animateScrollToItem(0)
                                                }
                                            }
                                            .invokeOnCompletion {
                                                markAsRead = true
                                                onSearch = false
                                            }
                                    }
                                }
                            }
                            FeedbackIconButton(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.search),
                                tint =
                                    if (onSearch) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            ) {
                                if (onSearch) {
                                    onSearch = false
                                } else {
                                    scope
                                        .launch {
                                            if (listState.firstVisibleItemIndex != 0) {
                                                listState.animateScrollToItem(0)
                                            }
                                        }
                                        .invokeOnCompletion {
                                            scope.launch {
                                                onSearch = true
                                                markAsRead = false
                                                delay(100)
                                                focusRequester.requestFocus()
                                            }
                                        }
                                }
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                scrolledContainerColor = scrolledTopBarContainerColor
                            ),
                    )
                }
            },
            content = {
                RYExtensibleVisibility(modifier = Modifier.zIndex(1f), visible = onSearch) {
                    BackHandler(onSearch) { onSearch = false }
                    SearchBar(
                        value = filterUiState.searchContent ?: "",
                        placeholder =
                            when {
                                filterUiState.group != null ->
                                    stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.group.name,
                                    )

                                filterUiState.feed != null ->
                                    stringResource(
                                        R.string.search_for_in,
                                        filterUiState.filter.toName(),
                                        filterUiState.feed.name,
                                    )

                                else ->
                                    stringResource(
                                        R.string.search_for,
                                        filterUiState.filter.toName(),
                                    )
                            },
                        focusRequester = focusRequester,
                        onValueChange = { viewModel.inputSearchContent(it) },
                        onClose = {
                            onSearch = false
                            viewModel.inputSearchContent(null)
                        },
                    )
                }

                RYExtensibleVisibility(markAsRead) {
                    BackHandler(markAsRead) { markAsRead = false }

                    MarkAsReadBar {
                        markAsRead = false
                        viewModel.updateReadStatus(
                            groupId = filterUiState.group?.id,
                            feedId = filterUiState.feed?.id,
                            articleId = null,
                            conditions = it,
                            isUnread = false,
                        )
                    }
                }
                val contentTransitionVertical =
                    sharedYAxisTransitionExpressive(direction = Direction.Forward)
                val contentTransitionBackward =
                    sharedXAxisTransitionSlow(direction = Direction.Backward)
                val contentTransitionForward =
                    sharedXAxisTransitionSlow(direction = Direction.Forward)
                AnimatedContent(
                    targetState = flowUiState,
                    contentKey = { it.pagerData.filterState.copy(searchContent = null) },
                    transitionSpec = {
                        val targetFilter = targetState.pagerData.filterState
                        val initialFilter = initialState.pagerData.filterState

                        if (targetFilter.filter.index > initialFilter.filter.index) {
                            contentTransitionForward
                        } else if (targetFilter.filter.index < initialFilter.filter.index) {
                            contentTransitionBackward
                        } else if (
                            targetFilter.group != initialFilter.group ||
                                targetFilter.feed != initialFilter.feed
                        ) {
                            contentTransitionVertical
                        } else {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    },
                ) { flowUiState ->
                    val pager = flowUiState.pagerData.pager
                    val filterState = flowUiState.pagerData.filterState
                    val pagingItems = pager.collectAsLazyPagingItems().also { pagingItems = it }

                    if (markAsReadOnScroll && filterState.filter.isUnread()) {
                        LaunchedEffect(listState.isScrollInProgress) {
                            if (!listState.isScrollInProgress) {
                                val firstItemKey =
                                    listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.contentType == CONTENT_TYPE_ARTICLE }
                                        ?.key
                                val items = mutableListOf<ArticleWithFeed>()
                                var found = false
                                val itemCount = pagingItems.itemCount
                                for (index in 0 until itemCount) {
                                    pagingItems.peek(index).let {
                                        if (it is ArticleFlowItem.Article) {
                                            if (it.articleWithFeed.article.id == firstItemKey) {
                                                found = true
                                                break
                                            }
                                            items.add(it.articleWithFeed)
                                        }
                                    }
                                }
                                if (items.isNotEmpty() && found) {
                                    viewModel.diffMapHolder.updateDiff(
                                        articleWithFeed = items.toTypedArray(),
                                        isUnread = false,
                                    )
                                }
                            }
                        }
                    }

                    if (settings.flowArticleListDateStickyHeader.value) {
                        LaunchedEffect(lastVisibleIndex) {
                            lastVisibleIndex.collect {
                                if (it in (pagingItems.itemCount - 25..pagingItems.itemCount - 1)) {
                                    pagingItems.get(it)
                                }
                            }
                        }
                    }

                    val listState = remember(pager) { listState }

                    val isSyncing by rememberUpdatedState(isSyncing)

                    LaunchedEffect(pagingItems) {
                        snapshotFlow { pagingItems.loadState.isIdle }
                            .collect {
                                if (isSyncing) {
                                    listState.scrollToItem(0)
                                }
                            }
                    }

                    val loadAction =
                        remember(pager, flowUiState, pullToSwitchFeed) {
                                when (pullToSwitchFeed) {
                                    PullToLoadNextFeedPreference.None -> null
                                    else -> {
                                        when {
                                            flowUiState.nextFilterState != null ->
                                                LoadAction.NextFeed.fromFilterState(
                                                    flowUiState.nextFilterState
                                                )

                                            filterState.filter.isUnread() &&
                                                pullToSwitchFeed ==
                                                    PullToLoadNextFeedPreference
                                                        .MarkAsReadAndLoadNextFeed ->
                                                LoadAction.MarkAllAsRead

                                            else -> null
                                        }
                                    }
                                }
                            }
                            .also { currentLoadAction = it }

                    val onLoadNext: (() -> Unit)? =
                        when (loadAction) {
                            is LoadAction.NextFeed -> viewModel::loadNextFeedOrGroup
                            LoadAction.MarkAllAsRead -> {
                                {
                                    viewModel.markAllAsRead()
                                    currentPullToLoadState?.animateDistanceTo(
                                        targetValue = 0f,
                                        animationSpec = settleSpec,
                                    )
                                }
                            }

                            else -> null
                        }

                    val onPullToSync: (() -> Unit)? =
                        if (isSyncing) null
                        else {
                            {
                                viewModel.sync()
                                currentPullToLoadState?.animateDistanceTo(
                                    targetValue = 0f,
                                    animationSpec = settleSpec,
                                )
                            }
                        }

                    val pullToLoadState =
                        rememberPullToLoadState(
                                key = pager,
                                onLoadNext = onLoadNext,
                                onLoadPrevious = onPullToSync,
                                loadThreshold = PullToLoadDefaults.loadThreshold(.1f),
                            )
                            .also { currentPullToLoadState = it }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier =
                                Modifier.pullToLoad(
                                        state = pullToLoadState,
                                        enabled = true,
                                        contentOffsetY = { fraction ->
                                            if (fraction > 0f) {
                                                (fraction * ContentOffsetMultiple * 1.5f)
                                                    .dp
                                                    .roundToPx()
                                            } else {
                                                (fraction * ContentOffsetMultiple * 2f)
                                                    .dp
                                                    .roundToPx()
                                            }
                                        },
                                        onScroll = {
                                            if (it < -10f) {
                                                markAsRead = false
                                            }
                                        },
                                    )
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                                    .fillMaxSize()
                                    .drawVerticalScrollIndicator(listState),
                            state = listState,
                        ) {
                            ArticleList(
                                pagingItems = pagingItems,
                                diffMap = viewModel.diffMapHolder.diffMap,
                                isShowFeedIcon = articleListFeedIcon.value,
                                isShowStickyHeader = articleListDateStickyHeader.value,
                                articleListTonalElevation = articleListTonalElevation.value,
                                isSwipeEnabled = { listState.isScrollInProgress },
                                onClick = { articleWithFeed, index ->
                                    if (articleWithFeed.feed.isBrowser) {
                                        viewModel.diffMapHolder.updateDiff(
                                            articleWithFeed,
                                            isUnread = false,
                                        )
                                        context.openURL(
                                            articleWithFeed.article.link,
                                            openLink,
                                            openLinkSpecificBrowser,
                                        )
                                    } else {
                                        navigateToArticle(articleWithFeed.article.id, index)
                                    }
                                },
                                onToggleStarred = onToggleStarred,
                                onToggleRead = onToggleRead,
                                onMarkAboveAsRead = onMarkAboveAsRead,
                                onMarkBelowAsRead = onMarkBelowAsRead,
                                onMarkAboveAsUnread = onMarkAboveAsUnread,
                                onMarkBelowAsUnread = onMarkBelowAsUnread,
                                onShare = onShare,
                            )
                            item {
                                Spacer(modifier = Modifier.height(128.dp))
                                Spacer(
                                    modifier =
                                        Modifier.windowInsetsBottomHeight(
                                            WindowInsets.navigationBars
                                        )
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
            bottomBar = {
                FilterBar(
                    modifier =
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState("filterBar"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        },
                    filter = filterUiState.filter,
                    filterBarStyle = filterBarStyle.value,
                    filterBarFilled = true,
                    filterBarPadding = filterBarPadding.dp,
                    filterBarTonalElevation = filterBarTonalElevation.value.dp,
                ) {
                    if (filterUiState.filter != it) {
                        viewModel.changeFilter(filterUiState.copy(filter = it))
                    } else {
                        scope.launch {
                            if (listState.firstVisibleItemIndex != 0) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    }
                }
            },
        )
        currentPullToLoadState?.let {
            PullToSyncIndicator(pullToLoadState = it, isSyncing = isSyncing)
            PullToLoadIndicator(
                state = it,
                loadAction = currentLoadAction,
                modifier =
                    Modifier.padding(bottom = 36.dp)
                        .windowInsetsPadding(
                            WindowInsets.safeContent.only(WindowInsetsSides.Horizontal)
                        ),
            )
        }
    }
}
