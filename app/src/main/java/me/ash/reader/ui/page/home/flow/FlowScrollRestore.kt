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
    /** The article below the anchor when saved: the exact continuation point
     * if the anchor itself is later read and leaves an unread-only list. */
    val nextArticleId: String? = null,
) {
    fun encode(): String {
        val id =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(articleId.toByteArray(Charsets.UTF_8))
        val base = "v1|$id|$index|$offset"
        val next = nextArticleId ?: return base
        return base +
            "|" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(next.toByteArray(Charsets.UTF_8))
    }
}

fun decodeFlowScrollPosition(raw: String?): FlowScrollPosition? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split("|")
    if ((parts.size != 4 && parts.size != 5) || parts[0] != "v1") return null
    fun decodeId(part: String): String? =
        try {
            String(Base64.getUrlDecoder().decode(part), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    val index = parts[2].toIntOrNull() ?: return null
    val offset = parts[3].toIntOrNull() ?: return null
    if (index < 0) return null
    val next =
        if (parts.size == 5) {
            decodeId(parts[4]) ?: return null
        } else {
            null
        }
    return FlowScrollPosition(decodeId(parts[1]) ?: return null, index, offset, next)
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
 * (new arrivals above must not shift the user); a gone anchor falls back to
 * the saved next article — the exact continuation point when the anchor was
 * read out of an unread-only list; both gone falls back to the saved index,
 * clamped into range so deletions cannot crash the scroll.
 */
fun findRestoreIndex(items: List<ArticleFlowItem>, saved: FlowScrollPosition): Int {
    if (items.isEmpty()) return 0
    var nextIndex = -1
    val nextId = saved.nextArticleId
    items.forEachIndexed { i, item ->
        val id = (item as? ArticleFlowItem.Article)?.articleWithFeed?.article?.id
        if (id == saved.articleId) return i
        // nextId null (legacy entries) must never match header nulls.
        if (nextIndex == -1 && nextId != null && id == nextId) nextIndex = i
    }
    if (nextIndex != -1) return nextIndex
    return saved.index.coerceIn(0, items.size - 1)
}

/** DataStore entry prefix for per-feed scroll positions. */
const val FLOW_SCROLL_PREFIX = "flow_scroll_position_"

/** Upper bound of items scanned for the anchor ids on restore, so a missing
 * anchor never force-loads the whole list through paging. */
const val RESTORE_SCAN_CAP = 300

/** Which feed or group a scroll-memory entry belongs to, if any. */
data class FlowScrollTarget(
    val accountId: Int,
    val filterIndex: Int,
    val feedId: String?,
    val groupId: String?,
)

/** Inverse of [flowScrollKey] (minus sort, which prune does not need). Null
 * for foreign or malformed keys, which prune conservatively keeps. */
fun parseFlowScrollTarget(prefsKeyName: String): FlowScrollTarget? {
    val inner =
        prefsKeyName.removePrefix(FLOW_SCROLL_PREFIX).takeIf { it != prefsKeyName }
            ?: return null
    val parts = inner.split("|")
    if (parts.size != 6 || parts[0] != "flowscroll") return null
    fun value(part: String, name: String) =
        part.removePrefix("$name=").takeIf { it != part }

    val accountId = value(parts[1], "acct")?.toIntOrNull() ?: return null
    val filterIndex = value(parts[2], "filter")?.toIntOrNull() ?: return null
    val feedId = (value(parts[3], "feed") ?: return null).takeIf { it != "-" }
    val groupId = (value(parts[4], "group") ?: return null).takeIf { it != "-" }
    if (value(parts[5], "sort") !in setOf("0", "1")) return null
    return FlowScrollTarget(accountId, filterIndex, feedId, groupId)
}
