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
 * WebView end check. [contentLoaded] must be true: before the content loads,
 * [maxValue] is still 0 (unmeasured) and indistinguishable from genuinely
 * short content — treating it as the end marks the article on open.
 */
fun isScrollAtEnd(value: Int, maxValue: Int, contentLoaded: Boolean, tolerance: Int = 50): Boolean {
    if (!contentLoaded) return false
    return value >= maxValue - tolerance
}
