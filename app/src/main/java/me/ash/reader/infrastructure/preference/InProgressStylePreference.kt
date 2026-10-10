package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.inProgressStyle
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalInProgressStyle =
    compositionLocalOf<InProgressStylePreference> { InProgressStylePreference.default }

sealed class InProgressStylePreference(val value: Int) : Preference() {
    data object Bar : InProgressStylePreference(0)
    data object Highlight : InProgressStylePreference(1)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                inProgressStyle,
                value
            )
        }
    }

    companion object {

        val default = Bar
        val values = listOf(Bar, Highlight)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[inProgressStyle]?.key as Preferences.Key<Int>]) {
                1 -> Highlight
                else -> default
            }
    }
}
