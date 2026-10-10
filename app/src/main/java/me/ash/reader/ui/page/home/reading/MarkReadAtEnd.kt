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

/**
 * End of the reader's outer scroll. The WebView renderer lays its article out
 * at full height inside the outer column, so the outer scroll is the reading
 * movement there too. [maxValue] 0 means unmeasured — never the end, which is
 * what previously marked articles on open or at the slightest swipe.
 */
fun isReaderScrollAtEnd(value: Int, maxValue: Int, tolerance: Int = 50): Boolean {
    if (maxValue <= 0) return false
    return value >= maxValue - tolerance
}
