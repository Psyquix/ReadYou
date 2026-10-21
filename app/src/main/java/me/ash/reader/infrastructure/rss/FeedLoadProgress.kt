package me.ash.reader.infrastructure.rss

import java.io.FilterInputStream
import java.io.InputStream

/** Progress through the uncompressed feed body. A null total means indeterminate. */
data class FeedLoadProgress(
    val bytesRead: Long,
    val totalBytes: Long?,
) {
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0 }
            ?.let { total -> (bytesRead.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat() }
}

/** Counts decompressed bytes as the parser consumes them. */
class CountingInputStream(
    input: InputStream,
    private val onProgress: (Long) -> Unit,
) : FilterInputStream(input) {

    var count: Long = 0
        private set

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) record(1)
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = super.read(b, off, len)
        if (read > 0) record(read)
        return read
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) record(skipped)
        return skipped
    }

    private fun record(bytes: Int) {
        count += bytes
        onProgress(count)
    }

    private fun record(bytes: Long) {
        count += bytes
        onProgress(count)
    }
}
