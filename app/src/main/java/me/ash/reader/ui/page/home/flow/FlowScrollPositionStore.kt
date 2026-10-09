package me.ash.reader.ui.page.home.flow

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.ash.reader.domain.data.FilterState
import me.ash.reader.ui.ext.dataStore

/**
 * Fork patch 4: DataStore backing for per-feed scroll memory.
 *
 * One string entry per [flowScrollKey]; the value is [FlowScrollPosition.encode].
 * Search results are never stored (callers gate on blank search content).
 */
fun flowScrollKey(filterState: FilterState, sortEarliest: Boolean): String =
    flowScrollKey(
        accountId = filterState.feed?.accountId ?: filterState.group?.accountId ?: 0,
        filterIndex = filterState.filter.index,
        feedId = filterState.feed?.id,
        groupId = filterState.group?.id,
        sortEarliest = sortEarliest,
    )

private fun flowScrollPreferencesKey(key: String) = stringPreferencesKey("flow_scroll_position_$key")

suspend fun Context.saveFlowScrollPosition(key: String, position: FlowScrollPosition) {
    dataStore.edit { it[flowScrollPreferencesKey(key)] = position.encode() }
}

suspend fun Context.loadFlowScrollPosition(key: String): FlowScrollPosition? =
    try {
        decodeFlowScrollPosition(
            dataStore.data.map { prefs -> prefs[flowScrollPreferencesKey(key)] }.first(),
        )
    } catch (_: IOException) {
        null
    }
