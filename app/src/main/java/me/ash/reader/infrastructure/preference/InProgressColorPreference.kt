package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.inProgressColor
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.page.settings.color.inprogress.COLOR_AUTO

val LocalInProgressColor =
    compositionLocalOf<InProgressColorPreference> { InProgressColorPreference.default }

/** ARGB int, or [COLOR_AUTO] to follow the theme accent. */
sealed class InProgressColorPreference(val value: Int) : Preference() {
    data class Custom(val argb: Int) : InProgressColorPreference(argb)
    data object Auto : InProgressColorPreference(COLOR_AUTO)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                inProgressColor,
                value
            )
        }
    }

    companion object {

        val default = Auto

        fun fromPreferences(preferences: Preferences) =
            when (val stored = preferences[DataStoreKey.keys[inProgressColor]?.key as Preferences.Key<Int>]) {
                null, COLOR_AUTO -> default
                else -> Custom(stored)
            }
    }
}
