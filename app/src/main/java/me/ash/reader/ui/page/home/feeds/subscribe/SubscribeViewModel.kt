package me.ash.reader.ui.page.home.feeds.subscribe

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rometools.rome.feed.synd.SyndFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.OpmlService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.rss.FeedLoadProgress
import me.ash.reader.infrastructure.rss.RssHelper
import me.ash.reader.ui.ext.formatUrl

@HiltViewModel
class SubscribeViewModel
@Inject
constructor(
    private val opmlService: OpmlService,
    val rssService: RssService,
    private val rssHelper: RssHelper,
    private val androidStringsHelper: AndroidStringsHelper,
    @ApplicationScope private val applicationScope: CoroutineScope,
    accountService: AccountService,
) : ViewModel() {

    private val _subscribeUiState = MutableStateFlow(SubscribeUiState())
    val subscribeUiState: StateFlow<SubscribeUiState> = _subscribeUiState.asStateFlow()

    private val _subscribeState: MutableStateFlow<SubscribeState> =
        MutableStateFlow(SubscribeState.Hidden)
    val subscribeState = _subscribeState.asStateFlow()

    val groupsFlow = MutableStateFlow<List<Group>>(emptyList())

    /** Full parse runs in the background as soon as the preview is available. */
    private var fullFeedJob: Deferred<SyndFeed>? = null

    init {
        viewModelScope.launch {
            accountService.currentAccountFlow.collectLatest {
                rssService.get().pullGroups().collect { groupsFlow.value = it }
            }
        }
        viewModelScope.launch {
            groupsFlow.collect { groups ->
                _subscribeState.update {
                    when (it) {
                        is SubscribeState.Configure -> it.copy(groups = groups)
                        else -> it
                    }
                }
            }
        }
    }

    fun reset() {
        cancelSearch()
    }

    fun importFromInputStream(inputStream: InputStream) {
        applicationScope.launch {
            opmlService.saveToDatabase(inputStream)
            rssService.get().doSyncOneTime()
        }
    }

    fun selectedGroup(groupId: String) {
        _subscribeState.update {
            when (it) {
                is SubscribeState.Configure -> it.copy(selectedGroupId = groupId)
                else -> it
            }
        }
    }

    fun addNewGroup() {
        if (_subscribeUiState.value.newGroupContent.isNotBlank()) {
            applicationScope.launch {
                // TODO: How to add a single group without no feeds via Google Reader API?
                selectedGroup(
                    rssService.get().addGroup(null, _subscribeUiState.value.newGroupContent)
                )
                hideNewGroupDialog()
                _subscribeUiState.update { it.copy(newGroupContent = "") }
            }
        }
    }

    fun toggleParseFullContentPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(fullContent = !state.fullContent, browser = false)

                else -> state
            }
        }
    }

    fun toggleOpenInBrowserPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(browser = !state.browser, fullContent = false)

                else -> state
            }
        }
    }

    fun toggleAllowNotificationPreset() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure -> state.copy(notification = !state.notification)
                else -> state
            }
        }
    }

    fun searchFeed() {
        val currentState = _subscribeState.value
        if (currentState !is SubscribeState.Idle) return
        fullFeedJob?.cancel()
        fullFeedJob = null
        viewModelScope.launch {
            val feedLink = currentState.linkState.text.trim().toString().formatUrl()
            currentState.linkState.edit { this.replace(0, length, feedLink) }

            if (rssService.get().isFeedExist(feedLink)) {
                _subscribeState.value =
                    currentState.copy(
                        errorMessage = androidStringsHelper.getString(R.string.already_subscribed)
                    )
                return@launch
            }
            val groups = groupsFlow.value
            val firstGroupId = groups.firstOrNull()?.id ?: return@launch

            val job =
                viewModelScope.launch {
                    try {
                        val preview = rssHelper.searchFeedPreview(feedLink)
                        if (rssService.get().isFeedExist(preview.feedLink)) {
                            _subscribeState.value =
                                currentState.copy(
                                    errorMessage =
                                        androidStringsHelper.getString(
                                            R.string.already_subscribed
                                        )
                                )
                            return@launch
                        }
                        _subscribeState.value =
                            SubscribeState.Configure(
                                preview =
                                    FeedPreview(
                                        title = preview.title,
                                        iconUrl = preview.iconUrl,
                                    ),
                                feedLink = preview.feedLink,
                                groups = groupsFlow.value,
                                selectedGroupId = firstGroupId,
                            )
                        fullFeedJob = startFullFeedLoad(preview)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _subscribeState.value = currentState.copy(errorMessage = e.message)
                    }
                }

            _subscribeState.value =
                SubscribeState.Fetching(linkState = currentState.linkState, job = job)
        }
    }

    private fun startFullFeedLoad(preview: RssHelper.FeedPreviewResult): Deferred<SyndFeed> {
        fullFeedJob?.cancel()
        return viewModelScope.async {
            try {
                val feed =
                    rssHelper.loadFeed(preview.feedLink, preview.iconUrl) { progress ->
                        updateConfigureProgress(preview.feedLink, progress)
                    }
                val loadedIcon = feed.icon?.url ?: feed.icon?.link
                _subscribeState.update { state ->
                    if (state is SubscribeState.Configure && state.feedLink == preview.feedLink) {
                        state.copy(
                            preview =
                                if (loadedIcon != null && loadedIcon != state.preview.iconUrl) {
                                    state.preview.copy(iconUrl = loadedIcon)
                                } else {
                                    state.preview
                                },
                            isPreparing = false,
                            progress = 1f,
                            preparationError = null,
                        )
                    } else {
                        state
                    }
                }
                feed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _subscribeState.update { state ->
                    if (state is SubscribeState.Configure && state.feedLink == preview.feedLink) {
                        state.copy(
                            isPreparing = false,
                            progress = null,
                            preparationError = e.message ?: "Unable to prepare feed",
                        )
                    } else {
                        state
                    }
                }
                throw e
            }
        }
    }

    private fun updateConfigureProgress(feedLink: String, progress: FeedLoadProgress) {
        // Never show 100% before the parser and feed preparation are complete.
        val next = progress.fraction?.coerceAtMost(0.99f)
        _subscribeState.update { state ->
            if (state !is SubscribeState.Configure || state.feedLink != feedLink) return@update state
            val changed =
                when {
                    next == null -> state.progress != null
                    state.progress == null -> true
                    else -> (state.progress * 100).toInt() != (next * 100).toInt()
                }
            if (changed) state.copy(progress = next) else state
        }
    }

    fun cancelSearch() {
        _subscribeState.value.let {
            if (it is SubscribeState.Fetching && it.job.isActive) {
                it.job.cancel()
            }
        }
        fullFeedJob?.cancel()
        fullFeedJob = null
    }

    fun subscribe() {
        val state = _subscribeState.value
        if (state !is SubscribeState.Configure || state.isSubscribing) return

        val existingJob = fullFeedJob
        val job =
            if (existingJob != null && (existingJob.isActive || state.progress == 1f)) {
                existingJob
            } else {
                _subscribeState.update {
                    if (it is SubscribeState.Configure && it.feedLink == state.feedLink) {
                        it.copy(
                            isPreparing = true,
                            progress = null,
                            preparationError = null,
                        )
                    } else {
                        it
                    }
                }
                val preview =
                    RssHelper.FeedPreviewResult(
                        title = state.preview.title,
                        iconUrl = state.preview.iconUrl,
                        feedLink = state.feedLink,
                    )
                startFullFeedLoad(preview).also { fullFeedJob = it }
            }

        _subscribeState.update {
            if (it is SubscribeState.Configure && it.feedLink == state.feedLink) {
                it.copy(isSubscribing = true)
            } else {
                it
            }
        }

        applicationScope.launch {
            val searchedFeed =
                try {
                    job.await()
                } catch (e: CancellationException) {
                    return@launch
                } catch (e: Exception) {
                    _subscribeState.update {
                        if (it is SubscribeState.Configure && it.feedLink == state.feedLink) {
                            it.copy(isSubscribing = false)
                        } else {
                            it
                        }
                    }
                    return@launch
                }

            val latest = _subscribeState.value as? SubscribeState.Configure ?: state
            searchedFeed.title =
                _subscribeUiState.value.newName.ifBlank { latest.preview.title }
            rssService
                .get()
                .subscribe(
                    searchedFeed = searchedFeed,
                    feedLink = latest.feedLink,
                    groupId = latest.selectedGroupId,
                    isNotification = latest.notification,
                    isFullContent = latest.fullContent,
                    isBrowser = latest.browser,
                )
            hideDrawer()
        }
    }

    fun inputNewGroup(content: String) {
        _subscribeUiState.update { it.copy(newGroupContent = content) }
    }

    fun handleSharedUrlFromIntent(url: String) {
        viewModelScope
            .launch {
                _subscribeState.update { SubscribeState.Idle(linkState = TextFieldState(url)) }
                delay(50)
            }
            .invokeOnCompletion { searchFeed() }
    }

    fun showDrawer() {
        cancelSearch()
        _subscribeState.value =
            SubscribeState.Idle(importFromOpmlEnabled = rssService.get().importSubscription)
    }

    fun hideDrawer() {
        cancelSearch()
        _subscribeState.value = SubscribeState.Hidden
    }

    fun showNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = true) }
    }

    fun hideNewGroupDialog() {
        _subscribeUiState.update { it.copy(newGroupDialogVisible = false) }
    }

    fun showRenameDialog() {
        _subscribeUiState.update { it.copy(renameDialogVisible = true) }
        _subscribeUiState.update { uiState ->
            (_subscribeState.value as? SubscribeState.Configure)?.preview?.title?.let { title
                ->
                uiState.copy(newName = title)
            } ?: uiState
        }
    }

    fun hideRenameDialog() {
        _subscribeUiState.update { it.copy(renameDialogVisible = false, newName = "") }
    }

    fun inputNewName(content: String) {
        _subscribeUiState.update { it.copy(newName = content) }
    }

    fun renameFeed() {
        _subscribeState.update { state ->
            when (state) {
                is SubscribeState.Configure ->
                    state.copy(
                        preview =
                            state.preview.copy(title = _subscribeUiState.value.newName)
                    )

                else -> state
            }
        }
    }
}

data class SubscribeUiState(
    val newGroupDialogVisible: Boolean = false,
    val newGroupContent: String = "",
    val newName: String = "",
    val renameDialogVisible: Boolean = false,
)

sealed interface SubscribeState {
    object Hidden : SubscribeState

    sealed interface Visible

    sealed interface Input : SubscribeState, Visible {
        val linkState: TextFieldState
    }

    data class Idle(
        override val linkState: TextFieldState = TextFieldState(),
        val importFromOpmlEnabled: Boolean = false,
        val errorMessage: String? = null,
    ) : SubscribeState, Input

    data class Fetching(override val linkState: TextFieldState, val job: Job) :
        SubscribeState, Input

    data class Configure(
        val preview: FeedPreview,
        val feedLink: String,
        val groups: List<Group> = emptyList(),
        val notification: Boolean = false,
        val fullContent: Boolean = false,
        val browser: Boolean = false,
        val selectedGroupId: String,
        val isPreparing: Boolean = true,
        val progress: Float? = null,
        val preparationError: String? = null,
        val isSubscribing: Boolean = false,
    ) : SubscribeState, Visible
}

/** Lightweight subscribe preview. Never put a SyndFeed in compose state:
 * hashing a large feed (ROME toString) OOMs low-memory devices. */
data class FeedPreview(
    val title: String,
    val iconUrl: String?,
)
