package me.ash.reader.ui.page.home.reading

/**
 * Fork patch 7: per-article reading positions.
 *
 * Pure helpers. [index]/[offset] serve the Native renderer, [scrollY] the
 * WebView renderer; both are always stored so a renderer switch simply falls
 * back to the top instead of misreading the other format.
 */
data class ReadingPosition(
    val index: Int,
    val offset: Int,
    val scrollY: Int,
) {
    fun encode(): String = "v1|$index|$offset|$scrollY"
}

fun decodeReadingPosition(raw: String?): ReadingPosition? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split("|")
    if (parts.size != 4 || parts[0] != "v1") return null
    val index = parts[1].toIntOrNull() ?: return null
    val offset = parts[2].toIntOrNull() ?: return null
    val scrollY = parts[3].toIntOrNull() ?: return null
    if (index < 0 || offset < 0 || scrollY < 0) return null
    return ReadingPosition(index, offset, scrollY)
}

/** Ids worth dropping: saved but no longer in the database. */
fun positionsToPrune(savedIds: Set<String>, existingIds: Set<String>): Set<String> =
    savedIds - existingIds

/**
 * Clamp a saved offset into a laid-out scroller. Null when there is nothing
 * to do ([saved] at top) or nothing to clamp to ([maxValue] unmeasured —
 * the caller polls). User movement is checked by the caller.
 */
fun coerceRestoreTarget(saved: Int, maxValue: Int): Int? {
    if (saved <= 0 || maxValue <= 0) return null
    return saved.coerceIn(0, maxValue)
}
