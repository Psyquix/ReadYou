package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.markAsReadAtEnd
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalMarkAsReadAtEnd =
    compositionLocalOf<MarkAsReadAtEndPreference> { MarkAsReadAtEndPreference.default }

sealed class MarkAsReadAtEndPreference(val value: Boolean) : Preference() {
    data object ON : MarkAsReadAtEndPreference(true)
    data object OFF : MarkAsReadAtEndPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                markAsReadAtEnd,
                value
            )
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(
            markAsReadAtEnd,
            !value
        )
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[markAsReadAtEnd]?.key as Preferences.Key<Boolean>]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}
