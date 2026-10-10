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

private fun flowScrollPreferencesKey(key: String) = stringPreferencesKey(FLOW_SCROLL_PREFIX + key)

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

/** All per-feed scroll-memory entry names in the main settings file. */
suspend fun Context.flowScrollKeys(): Set<String> =
    try {
        dataStore.data
            .map { prefs ->
                prefs.asMap().keys
                    .map { it.name }
                    .filter { it.startsWith(FLOW_SCROLL_PREFIX) }
                    .toSet()
            }
            .first()
    } catch (_: IOException) {
        emptySet()
    }

suspend fun Context.removeFlowScrollPosition(prefsKeyName: String) {
    dataStore.edit { it.remove(stringPreferencesKey(prefsKeyName)) }
}
