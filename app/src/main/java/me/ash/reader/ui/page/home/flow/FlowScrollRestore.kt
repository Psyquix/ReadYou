package me.ash.reader.ui.page.home.flow

import me.ash.reader.domain.model.article.ArticleFlowItem
import java.util.Base64

/**
 * Fork patch 4: per-feed article-list scroll memory.
 *
 * Pure JVM-friendly helpers (no Android, no Compose, no DataStore). The key
 * intentionally uses only primitives: [me.ash.reader.domain.model.general.Filter]
 * pulls in Compose icons, which cannot load in a plain JVM unit test.
 */
data class FlowScrollPosition(
    val articleId: String,
    val index: Int,
    val offset: Int,
) {
    fun encode(): String {
        val id =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(articleId.toByteArray(Charsets.UTF_8))
        return "v1|$id|$index|$offset"
    }
}

fun decodeFlowScrollPosition(raw: String?): FlowScrollPosition? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split("|")
    if (parts.size != 4 || parts[0] != "v1") return null
    val idBytes =
        try {
            Base64.getUrlDecoder().decode(parts[1])
        } catch (_: IllegalArgumentException) {
            return null
        }
    val index = parts[2].toIntOrNull() ?: return null
    val offset = parts[3].toIntOrNull() ?: return null
    if (index < 0) return null
    return FlowScrollPosition(String(idBytes, Charsets.UTF_8), index, offset)
}

fun flowScrollKey(
    accountId: Int,
    filterIndex: Int,
    feedId: String?,
    groupId: String?,
    sortEarliest: Boolean,
): String =
    "flowscroll|acct=$accountId|filter=$filterIndex" +
        "|feed=${feedId ?: "-"}|group=${groupId ?: "-"}|sort=${if (sortEarliest) 1 else 0}"

/**
 * Maps a saved position onto the currently loaded items. The article id wins
 * (new arrivals above must not shift the user); a gone article falls back to
 * the saved index, clamped into range so deletions cannot crash the scroll.
 */
fun findRestoreIndex(items: List<ArticleFlowItem>, saved: FlowScrollPosition): Int {
    if (items.isEmpty()) return 0
    val idIndex =
        items.indexOfFirst {
            (it as? ArticleFlowItem.Article)?.articleWithFeed?.article?.id == saved.articleId
        }
    if (idIndex != -1) return idIndex
    return saved.index.coerceIn(0, items.size - 1)
}
