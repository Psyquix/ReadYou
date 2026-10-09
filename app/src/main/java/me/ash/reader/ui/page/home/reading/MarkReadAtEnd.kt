package me.ash.reader.ui.page.home.reading

/**
 * Fork patch 5: mark as read at end of article.
 *
 * Pure end-of-content checks shared by both reader renderers. Kept free of
 * Compose and Android types so they stay JVM-testable.
 */
fun isNativeListAtEnd(
    lastVisibleIndex: Int,
    lastVisibleOffset: Int,
    lastVisibleSize: Int,
    viewportEndOffset: Int,
    totalItemsCount: Int,
): Boolean {
    if (totalItemsCount <= 0 || lastVisibleIndex < 0) return false
    if (lastVisibleIndex != totalItemsCount - 1) return false
    return lastVisibleOffset + lastVisibleSize <= viewportEndOffset
}

fun isScrollAtEnd(value: Int, maxValue: Int, tolerance: Int = 50): Boolean =
    value >= maxValue - tolerance
