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
 * True end of WebView content. All values are in pixels except [contentHeight],
 * which is in CSS pixels and scaled by [scale]. Unlaid-out ([viewHeight] 0)
 * or unloaded ([contentHeight] 0) content is never the end; content shorter
 * than the view is the end as soon as it is laid out.
 */
fun isWebViewContentAtEnd(
    scrollY: Int,
    scale: Float,
    contentHeight: Int,
    viewHeight: Int,
    tolerancePx: Int = 8,
): Boolean {
    if (viewHeight <= 0 || contentHeight <= 0) return false
    return scrollY + viewHeight >= contentHeight * scale - tolerancePx
}
