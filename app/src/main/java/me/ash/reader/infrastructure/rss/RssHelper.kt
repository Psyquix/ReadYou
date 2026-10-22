package me.ash.reader.infrastructure.rss

import android.content.Context
import android.util.Log
import com.rometools.modules.mediarss.MediaEntryModule
import com.rometools.modules.mediarss.MediaModule
import com.rometools.modules.mediarss.types.UrlReference
import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.feed.synd.SyndImageImpl
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.html.Readability
import me.ash.reader.ui.ext.currentAccountId
import me.ash.reader.ui.ext.decodeHTML
import me.ash.reader.ui.ext.extractDomain
import me.ash.reader.ui.ext.isFuture
import me.ash.reader.ui.ext.spacerDollar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.executeAsync
import okhttp3.internal.commonIsSuccessful
import okio.IOException
import org.jsoup.Jsoup

val enclosureRegex = """<enclosure\s+url="([^"]+)"\s+type=".*"\s*/>""".toRegex()
val imgRegex = """img.*?src=(["'])((?!data).*?)\1""".toRegex(RegexOption.DOT_MATCHES_ALL)

/** Some operations on RSS. */
class RssHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val okHttpClient: OkHttpClient,
) {

    data class FeedPreviewResult(
        val title: String,
        val iconUrl: String?,
        val feedLink: String,
        val siteLink: String? = null,
    )

    /**
     * Reads only the feed header and resolves the preview. The full feed is
     * loaded separately in [loadFeed] so this call stays fast on large feeds.
     */
    @Throws(Exception::class)
    suspend fun searchFeedPreview(feedLink: String): FeedPreviewResult {
        return withContext(ioDispatcher) {
            val directResponse = response(okHttpClient, feedLink)
            if (!directResponse.commonIsSuccessful) throw IOException(directResponse.message)
            val directEncoding = charsetName(directResponse.header("Content-Type"))
            val directMetadata =
                directResponse.body.byteStream().use { inputStream ->
                    runCatching { FeedMetadataParser.parse(inputStream, directEncoding) }.getOrNull()
                }

            val resolvedFeedLink: String
            val metadata: FeedMetadata
            if (directMetadata != null) {
                resolvedFeedLink = feedLink
                metadata = directMetadata
            } else {
                // The body was consumed while checking whether this URL is a
                // feed. Re-fetch the page so its alternate link can be found.
                val pageResponse = response(okHttpClient, feedLink)
                if (!pageResponse.commonIsSuccessful) throw IOException(pageResponse.message)
                val pageBody = pageResponse.body.bytes()
                resolvedFeedLink =
                    discoverFeedLink(feedLink, pageBody)
                        ?: throw IOException("Unable to detect RSS feed URL")

                val discoveredResponse = response(okHttpClient, resolvedFeedLink)
                if (!discoveredResponse.commonIsSuccessful) {
                    throw IOException(discoveredResponse.message)
                }
                metadata =
                    discoveredResponse.body.byteStream().use { inputStream ->
                        FeedMetadataParser.parse(
                            inputStream,
                            charsetName(discoveredResponse.header("Content-Type")),
                        )
                    }
            }

            val title = metadata.title?.takeIf { it.isNotBlank() } ?: resolvedFeedLink
            val siteLink =
                metadata.siteLink
                    ?.takeIf { it.isNotBlank() }
                    ?.let { resolveUrl(resolvedFeedLink, it) }
            // Only the icon declared in the header is available this early. If
            // it is missing, the background parse resolves a fallback icon.
            val iconUrl =
                metadata.iconUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { resolveUrl(siteLink ?: resolvedFeedLink, it) }
                    ?.takeIf { it.startsWith("http") }

            FeedPreviewResult(
                title = title,
                iconUrl = iconUrl,
                feedLink = resolvedFeedLink,
                siteLink = siteLink,
            )
        }
    }

    /**
     * Streams the complete feed through ROME. [onProgress] reports decompressed
     * bytes; the total is obtained with an identity HEAD request so it matches
     * the bytes ROME actually reads.
     */
    @Throws(Exception::class)
    suspend fun loadFeed(
        feedLink: String,
        iconUrl: String?,
        onProgress: (FeedLoadProgress) -> Unit = {},
    ): SyndFeed =
        withContext(ioDispatcher) {
            coroutineScope {
                val coroutineContext = currentCoroutineContext()
                // Start the length probe and the feed download together so a
                // slow HEAD never delays the parse.
                val headTotal = async { queryUncompressedContentLength(feedLink) }
                val response = response(okHttpClient, feedLink)
                if (!response.commonIsSuccessful) throw IOException(response.message)
                val total = AtomicReference<Long?>(
                    response.body.contentLength().takeIf {
                        it > 0 && response.header("Content-Encoding") == null
                    }
                )
                onProgress(FeedLoadProgress(bytesRead = 0, totalBytes = total.get()))

                val countingStream =
                    CountingInputStream(response.body.byteStream()) { bytesRead ->
                        if (!coroutineContext.isActive) {
                            throw CancellationException("Feed load cancelled")
                        }
                        onProgress(
                            FeedLoadProgress(bytesRead = bytesRead, totalBytes = total.get())
                        )
                    }

                val headWatcher =
                    launch {
                        val length = headTotal.await()
                        if (length != null) {
                            total.set(length)
                            onProgress(
                                FeedLoadProgress(
                                    bytesRead = countingStream.count,
                                    totalBytes = length,
                                )
                            )
                        }
                    }

                try {
                    val feed =
                        countingStream.use { inputStream ->
                            SyndFeedInput()
                                .build(
                                    XmlReader(
                                        inputStream,
                                        toHttpContentType(response.header("Content-Type")),
                                    )
                                )
                        }

                    val resolvedIcon =
                        resolveIcon(
                            declaredIcon =
                                iconUrl ?: (feed.icon?.url ?: feed.icon?.link),
                            siteLink = feed.link,
                            feedLink = feedLink,
                        )
                    feed.icon = SyndImageImpl()
                    feed.icon.link = resolvedIcon
                    feed.icon.url = resolvedIcon
                    onProgress(FeedLoadProgress(countingStream.count, total.get()))
                    feed
                } finally {
                    headWatcher.cancel()
                    headTotal.cancel()
                }
            }
        }

    private suspend fun queryUncompressedContentLength(url: String): Long? =
        withTimeoutOrNull(3_000) {
            try {
                val request =
                    Request.Builder()
                        .url(url)
                        .head()
                        .header("Accept-Encoding", "identity")
                        .build()
                okHttpClient.newCall(request).executeAsync().use { response ->
                    if (response.commonIsSuccessful &&
                        response.header("Content-Encoding") == null
                    ) {
                        response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                    } else {
                        null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

    private fun resolveUrl(base: String, value: String): String? =
        runCatching { URI(base).resolve(value.trim()).toString() }.getOrNull()

    private fun toHttpContentType(contentType: String?): String =
        contentType?.let {
            if (it.contains("charset=", ignoreCase = true)) it else "$it; charset=UTF-8"
        } ?: "text/xml; charset=UTF-8"

    private fun charsetName(contentType: String?): String? =
        contentType
            ?.substringAfter("charset=", missingDelimiterValue = "")
            ?.substringBefore(';')
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }

    private fun discoverFeedLink(pageUrl: String, body: ByteArray): String? {
        val document = Jsoup.parse(String(body, Charsets.UTF_8), pageUrl)
        val links = document.select("head link[rel~=(?i)alternate][href]")
        val preferred =
            links.firstOrNull {
                val type = it.attr("type").lowercase(Locale.ROOT)
                type == "application/rss+xml" ||
                    type == "application/atom+xml" ||
                    type == "application/rdf+xml"
            }
        val fallback = links.firstOrNull()
        return (preferred ?: fallback)?.absUrl("href")?.takeIf { it.isNotBlank() }
    }

    @Throws(Exception::class)
    suspend fun parseFullContent(link: String, title: String): String {
        return withContext(ioDispatcher) {
            val response = response(okHttpClient, link)
            if (response.commonIsSuccessful) {
                val responseBody = response.body
                val charset = responseBody.contentType()?.charset()
                val content =
                    responseBody.source().use {
                        if (charset != null) {
                            return@use it.readString(charset)
                        }

                        val peekContent = it.peek().readString(Charsets.UTF_8)

                        val charsetFromMeta =
                            runCatching {
                                    val element =
                                        Jsoup.parse(peekContent, link)
                                            .selectFirst("meta[http-equiv=content-type]")
                                    return@runCatching if (element == null) Charsets.UTF_8
                                    else {
                                        element
                                            .attr("content")
                                            .substringAfter("charset=")
                                            .removeSurrounding("\"")
                                            .lowercase()
                                            .let { Charset.forName(it) }
                                    }
                                }
                                .getOrDefault(Charsets.UTF_8)

                        if (charsetFromMeta == Charsets.UTF_8) {
                            peekContent
                        } else {
                            it.readString(charsetFromMeta)
                        }
                    }

                val articleContent = Readability.parseToElement(content, link)
                articleContent?.let {
                    val h1Element = articleContent.selectFirst("h1")
                    if (h1Element != null && h1Element.hasText() && h1Element.text() == title) {
                        h1Element.remove()
                    }
                    articleContent.toString()
                } ?: throw IOException("articleContent is null")
            } else throw IOException(response.message)
        }
    }

    suspend fun queryRssXml(
        feed: Feed,
        latestLink: String?,
        preDate: Date = Date(),
    ): List<Article> =
        try {
            val accountId = context.currentAccountId
            val response = response(okHttpClient, feed.url)
            val contentType = response.header("Content-Type")

            val httpContentType =
                contentType?.let {
                    if (it.contains("charset=", ignoreCase = true)) it
                    else "$it; charset=UTF-8"
                } ?: "text/xml; charset=UTF-8"

            response.body.byteStream().use { inputStream ->
                SyndFeedInput()
                    .apply { isPreserveWireFeed = true }
                    .build(XmlReader(inputStream, httpContentType))
                    .entries
                    .asSequence()
                    .takeWhile { latestLink == null || latestLink != it.link }
                    .map { buildArticleFromSyndEntry(feed, accountId, it, preDate) }
                    .toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("RLog", "queryRssXml[${feed.name}]: ${e.message}")
            listOf()
        }

    fun buildArticleFromSyndEntry(
        feed: Feed,
        accountId: Int,
        syndEntry: SyndEntry,
        preDate: Date = Date(),
    ): Article {
        val desc = syndEntry.description?.value
        val content =
            syndEntry.contents
                .takeIf { it.isNotEmpty() }
                ?.let { it.joinToString("\n") { it.value } }
        //        Log.i(
        //            "RLog",
        //            "request rss:\n" +
        //                    "name: ${feed.name}\n" +
        //                    "feedUrl: ${feed.url}\n" +
        //                    "url: ${syndEntry.link}\n" +
        //                    "title: ${syndEntry.title}\n" +
        //                    "desc: ${desc}\n" +
        //                    "content: ${content}\n"
        //        )
        return Article(
            id = accountId.spacerDollar(UUID.randomUUID().toString()),
            accountId = accountId,
            feedId = feed.id,
            date =
                (syndEntry.publishedDate ?: syndEntry.updatedDate)?.takeIf { !it.isFuture(preDate) }
                    ?: preDate,
            title = syndEntry.title.decodeHTML() ?: feed.name,
            author = syndEntry.author,
            rawDescription = content ?: desc ?: "",
            shortDescription = Readability.parseToText(desc ?: content, syndEntry.link).take(280),
            //            fullContent = content,
            img = findThumbnail(syndEntry) ?: findThumbnail(content ?: desc),
            link = syndEntry.link ?: "",
            updateAt = preDate,
        )
    }

    fun findThumbnail(syndEntry: SyndEntry): String? {
        if (syndEntry.enclosures?.firstOrNull()?.url != null) {
            return syndEntry.enclosures.first().url
        }

        val mediaModule = syndEntry.getModule(MediaModule.URI) as? MediaEntryModule
        if (mediaModule != null) {
            return findThumbnail(mediaModule)
        }

        return null
    }

    private fun findThumbnail(mediaModule: MediaEntryModule): String? {
        val candidates =
            buildList {
                    add(mediaModule.metadata)
                    addAll(mediaModule.mediaGroups.map { mediaGroup -> mediaGroup.metadata })
                    addAll(mediaModule.mediaContents.map { content -> content.metadata })
                }
                .flatMap { it.thumbnail.toList() }

        val thumbnail = candidates.firstOrNull()

        if (thumbnail != null) {
            return thumbnail.url.toString()
        } else {
            val imageMedia = mediaModule.mediaContents.firstOrNull { it.medium == "image" }
            if (imageMedia != null) {
                return (imageMedia.reference as? UrlReference)?.url.toString()
            }
        }
        return null
    }

    fun findThumbnail(text: String?): String? {
        text ?: return null
        val enclosure = enclosureRegex.find(text)?.groupValues?.get(1)
        if (enclosure?.isNotBlank() == true) {
            return enclosure
        }
        // From https://gitlab.com/spacecowboy/Feeder
        // Using negative lookahead to skip data: urls, being inline base64
        // And capturing original quote to use as ending quote
        // Base64 encoded images can be quite large - and crash database cursors
        return imgRegex.find(text)?.groupValues?.get(2)?.takeIf { !it.startsWith("data:") }
    }

    suspend fun queryRssIconLink(feedLink: String?): String? {
        if (feedLink.isNullOrEmpty()) return null
        val iconFinder = BestIconFinder(okHttpClient)
        val domain = feedLink.extractDomain()
        return iconFinder.findBestIcon(domain ?: feedLink).also {
            Log.i("RLog", "queryRssIconByLink: get $it from $domain")
        }
    }

    /**
     * Unified icon resolution, Twine-style: feed-declared <image> first if it
     * actually returns an image, then the site homepage's icons, then the
     * legacy feed-host lookup. This is the only entry point new code should use.
     */
    suspend fun queryRssIcon(feedLink: String?): String? {
        if (feedLink.isNullOrEmpty()) return null
        return try {
            val preview = searchFeedPreview(feedLink)
            resolveIcon(
                declaredIcon = preview.iconUrl,
                siteLink = preview.siteLink,
                feedLink = preview.feedLink,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("RLog", "queryRssIcon: $e")
            queryRssIconLink(feedLink)
        }
    }

    private suspend fun resolveIcon(
        declaredIcon: String?,
        siteLink: String?,
        feedLink: String,
    ): String? {
        val declared = declaredIcon?.takeIf { it.startsWith("http") }
        if (declared != null && isUsableIcon(declared)) return declared

        val site = siteLink?.takeIf { it.startsWith("http") }
        return site?.let { BestIconFinder(okHttpClient).findBestIcon(it) }
            ?: queryRssIconLink(feedLink)
    }

    /**
     * Some sites answer /favicon.ico with 204 or an HTML error page. Treat
     * those as missing so the caller can use the site's real icon instead.
     */
    internal suspend fun isUsableIcon(url: String): Boolean =
        withTimeoutOrNull(3_000) {
            try {
                val request = Request.Builder().url(url).head().build()
                okHttpClient.newCall(request).executeAsync().use { response ->
                    if (!response.commonIsSuccessful || response.code == 204) {
                        return@use false
                    }
                    val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT)
                    val contentLength = response.header("Content-Length")?.toLongOrNull()
                    (contentType == null ||
                        contentType.startsWith("image/") ||
                        contentType.contains("octet-stream")) &&
                        (contentLength == null || contentLength > 0)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
        } ?: false

    suspend fun saveRssIcon(feedDao: FeedDao, feed: Feed, iconLink: String) {
        feedDao.update(feed.copy(icon = iconLink))
    }

    private suspend fun response(client: OkHttpClient, url: String): okhttp3.Response =
        client.newCall(Request.Builder().url(url).build()).executeAsync()
}
