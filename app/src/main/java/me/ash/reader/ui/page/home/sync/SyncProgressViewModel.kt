package me.ash.reader.ui.page.home.sync

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import me.ash.reader.domain.service.SyncProgress
import me.ash.reader.domain.service.SyncProgressTracker

@HiltViewModel
class SyncProgressViewModel
@Inject
constructor(
    tracker: SyncProgressTracker,
) : ViewModel() {
    val progress: StateFlow<SyncProgress?> = tracker.state
}
