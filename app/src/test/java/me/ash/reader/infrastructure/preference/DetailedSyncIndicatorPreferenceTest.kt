package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.ash.reader.ui.ext.dataStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DetailedSyncIndicatorPreferenceTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `default is off`() {
        assertEquals(
            DetailedSyncIndicatorPreference.OFF,
            DetailedSyncIndicatorPreference.fromPreferences(preferencesOf()),
        )
    }

    @Test
    fun `true maps to on through toSettings`() {
        val prefs = preferencesOf(booleanPreferencesKey("detailedSyncIndicator") to true)
        assertEquals(
            DetailedSyncIndicatorPreference.ON,
            DetailedSyncIndicatorPreference.fromPreferences(prefs),
        )
        assertEquals(DetailedSyncIndicatorPreference.ON, prefs.toSettings().detailedSyncIndicator)
    }

    @Test
    fun `explicit false maps to off through toSettings`() {
        val prefs = preferencesOf(booleanPreferencesKey("detailedSyncIndicator") to false)
        assertEquals(
            DetailedSyncIndicatorPreference.OFF,
            DetailedSyncIndicatorPreference.fromPreferences(prefs),
        )
        assertEquals(DetailedSyncIndicatorPreference.OFF, prefs.toSettings().detailedSyncIndicator)
    }

    @Test
    fun `toggle flips the stored value`() {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.filesDir).thenReturn(tempFolder.newFolder("files"))

        val key = booleanPreferencesKey("detailedSyncIndicator")
        val scope = CoroutineScope(Dispatchers.IO)

        runBlocking {
            DetailedSyncIndicatorPreference.OFF.toggle(context, scope).join()
            assertEquals(true, context.dataStore.data.first()[key])

            DetailedSyncIndicatorPreference.ON.toggle(context, scope).join()
            assertEquals(false, context.dataStore.data.first()[key])
        }
    }
}
