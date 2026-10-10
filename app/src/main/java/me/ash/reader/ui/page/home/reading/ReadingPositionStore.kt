package me.ash.reader.ui.page.home.reading

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.ui.ext.readingPositionStore

/**
 * Fork patch 7: per-article reading positions in their own DataStore file
 * (`reading_positions`), loaded lazily on first access — the main settings
 * file and app startup are untouched.
 */
class ReadingPositionStore
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
) {
    suspend fun save(articleId: String, position: ReadingPosition) {
        context.readingPositionStore.edit { it[positionKey(articleId)] = position.encode() }
        prune()
    }

    suspend fun load(articleId: String): ReadingPosition? =
        try {
            decodeReadingPosition(
                context.readingPositionStore.data
                    .map { prefs -> prefs[positionKey(articleId)] }
                    .first(),
            )
        } catch (_: IOException) {
            null
        }

    suspend fun clear(articleId: String) {
        context.readingPositionStore.edit { it.remove(positionKey(articleId)) }
    }

    suspend fun clearAll() {
        context.readingPositionStore.edit { it.clear() }
    }

    /**
     * Fork patch 8: live set of article ids holding a saved spot, for the
     * in-progress accent bar. Emits empty on read failure.
     */
    fun savedIdsFlow(): kotlinx.coroutines.flow.Flow<Set<String>> =
        context.readingPositionStore.data
            .map { prefs ->
                inProgressIdsFromKeys(prefs.asMap().keys.map { it.name }.toSet())
            }
            .catch { e ->
                if (e is IOException) emit(emptySet()) else throw e
            }

    /**
     * Drops entries whose articles no longer exist. Runs after every save, on
     * a background thread; the IN query is chunked past SQLite's variable
     * limit.
     */
    suspend fun prune() {
        val savedIds =
            try {
                context.readingPositionStore.data
                    .map { prefs ->
                        prefs.asMap().keys.mapNotNull { key ->
                            key.name.removePrefix(PREFIX).takeIf { it != key.name }
                        }.toSet()
                    }
                    .first()
            } catch (_: IOException) {
                return
            }
        if (savedIds.isEmpty()) return
        val gone =
            positionsToPrune(
                savedIds,
                savedIds.chunked(500).flatMap { articleDao.queryExistingIds(it.toSet()) }.toSet(),
            )
        if (gone.isNotEmpty()) {
            context.readingPositionStore.edit { prefs ->
                gone.forEach { prefs.remove(positionKey(it)) }
            }
        }
    }

    companion object {
        const val PREFIX = "reading_pos_"
        const val FILE_NAME = "reading_positions"

        fun positionKey(articleId: String) = stringPreferencesKey("$PREFIX$articleId")
    }
}
