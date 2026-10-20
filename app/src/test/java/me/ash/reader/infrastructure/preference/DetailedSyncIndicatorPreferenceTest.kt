package me.ash.reader.infrastructure.preference

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailedSyncIndicatorPreferenceTest {
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
}
