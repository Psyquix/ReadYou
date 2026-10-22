package me.ash.reader.infrastructure.rss

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class RssHelperFeedPreviewTest {

    private lateinit var server: TestHttpServer
    private lateinit var helper: RssHelper

    private fun feedBody(siteLink: String, iconUrl: String): ByteArray =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Local Feed</title>
            <link>$siteLink</link>
            <image><url>$iconUrl</url></image>
            <item>
              <title>One</title>
              <link>https://example.com/1</link>
              <description>Hello</description>
            </item>
          </channel>
        </rss>
        """
            .trimIndent()
            .toByteArray(Charsets.UTF_8)

    private val pageBody =
        """
        <html>
          <head>
            <link rel="alternate" type="application/rss+xml" href="/feed.xml">
          </head>
          <body>Example</body>
        </html>
        """
            .trimIndent()
            .toByteArray(Charsets.UTF_8)

    private val siteBody =
        """
        <html>
          <head>
            <link rel="icon" href="/valid-icon.png">
          </head>
          <body>Site</body>
        </html>
        """
            .trimIndent()
            .toByteArray(Charsets.UTF_8)

    private val validIcon = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    @Before
    fun setUp() {
        server =
            TestHttpServer(
                feedBody = feedBody("https://example.com/", "/icon.png"),
                pageBody = pageBody,
                siteBody = siteBody,
                validIcon = validIcon,
            )
        helper = RssHelper(mock<Context>(), Dispatchers.IO, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun `preview reads only the header and resolves a relative icon`() = runBlocking {
        val preview = helper.searchFeedPreview(url("/feed.xml"))

        assertEquals("Local Feed", preview.title)
        assertEquals(url("/feed.xml"), preview.feedLink)
        assertEquals("https://example.com/icon.png", preview.iconUrl)
    }

    @Test
    fun `html discovery still resolves the alternate feed`() = runBlocking {
        val preview = helper.searchFeedPreview(url("/page.html"))

        assertEquals("Local Feed", preview.title)
        assertEquals(url("/feed.xml"), preview.feedLink)
        assertEquals("https://example.com/icon.png", preview.iconUrl)
    }

    @Test
    fun `full load reports byte progress against the identity length`() = runBlocking {
        val progress = mutableListOf<FeedLoadProgress>()
        val feed =
            helper.loadFeed(
                feedLink = url("/feed.xml"),
                iconUrl = url("/valid-icon.png"),
            ) { progress += it }

        assertEquals("Local Feed", feed.title)
        assertEquals(server.feedBody.size.toLong(), progress.last().bytesRead)
        assertEquals(server.feedBody.size.toLong(), progress.last().totalBytes)
        assertEquals(1f, progress.last().fraction)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.bytesRead >= a.bytesRead })
    }

    @Test
    fun `gzip load reports decompressed progress against the identity length`() = runBlocking {
        val progress = mutableListOf<FeedLoadProgress>()
        val feed =
            helper.loadFeed(
                feedLink = url("/feed-gzip"),
                iconUrl = url("/valid-icon.png"),
            ) { progress += it }

        assertEquals("Local Feed", feed.title)
        assertEquals(server.feedBody.size.toLong(), progress.last().bytesRead)
        assertEquals(server.feedBody.size.toLong(), progress.last().totalBytes)
        assertEquals(1f, progress.last().fraction)
    }

    @Test
    fun `an empty declared icon falls back to the site icon`() = runBlocking {
        server.feedBody =
            feedBody(
                siteLink = url("/site.html"),
                iconUrl = url("/bad-icon.ico"),
            )

        val feed =
            helper.loadFeed(
                feedLink = url("/feed.xml"),
                iconUrl = url("/bad-icon.ico"),
            )

        assertEquals(url("/valid-icon.png"), feed.icon?.url)
    }

    private fun url(path: String): String = "http://127.0.0.1:${server.port}$path"

    private class TestHttpServer(
        var feedBody: ByteArray,
        private val pageBody: ByteArray,
        private val siteBody: ByteArray,
        private val validIcon: ByteArray,
    ) {
        private val serverSocket =
            ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int
            get() = serverSocket.localPort
        private var running = true
        private val thread =
            Thread {
                    try {
                        while (running) {
                            serverSocket.accept().use(::handle)
                        }
                    } catch (_: IOException) {
                        // Socket closed by stop().
                    }
                }
                .apply {
                    isDaemon = true
                    start()
                }

        fun stop() {
            running = false
            runCatching { serverSocket.close() }
            thread.join(1_000)
        }

        private fun handle(socket: Socket) {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            var acceptEncoding = ""
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Accept-Encoding:", ignoreCase = true)) {
                    acceptEncoding = line.substringAfter(':').trim()
                }
            }

            val method = requestLine.substringBefore(' ')
            val path = requestLine.substringAfter(' ').substringBefore(' ').substringBefore('?')
            val response =
                when (path) {
                    "/feed.xml" ->
                        Response(200, "OK", "application/rss+xml; charset=UTF-8", feedBody)
                    "/feed-gzip" -> {
                        val identity = method == "HEAD" || acceptEncoding.contains("identity")
                        if (identity) {
                            Response(200, "OK", "application/rss+xml; charset=UTF-8", feedBody)
                        } else {
                            Response(
                                status = 200,
                                reason = "OK",
                                contentType = "application/rss+xml; charset=UTF-8",
                                body = gzip(feedBody),
                                contentEncoding = "gzip",
                            )
                        }
                    }

                    "/page.html" ->
                        Response(200, "OK", "text/html; charset=UTF-8", pageBody)
                    "/site.html" ->
                        Response(200, "OK", "text/html; charset=UTF-8", siteBody)
                    "/valid-icon.png" ->
                        Response(200, "OK", "image/png", validIcon)
                    "/bad-icon.ico" ->
                        Response(204, "No Content", "image/png", ByteArray(0))
                    else -> Response(404, "Not Found", "text/plain", ByteArray(0))
                }

            val headers =
                buildString {
                    append("HTTP/1.1 ${response.status} ${response.reason}\r\n")
                    append("Content-Type: ${response.contentType}\r\n")
                    append("Content-Length: ${response.body.size}\r\n")
                    response.contentEncoding?.let { append("Content-Encoding: $it\r\n") }
                    append("Connection: close\r\n")
                    append("\r\n")
                }
            val output = socket.getOutputStream()
            output.write(headers.toByteArray(Charsets.US_ASCII))
            if (method != "HEAD") output.write(response.body)
            output.flush()
        }

        private fun gzip(bytes: ByteArray): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            java.util.zip.GZIPOutputStream(output).use { it.write(bytes) }
            return output.toByteArray()
        }

        private data class Response(
            val status: Int,
            val reason: String,
            val contentType: String,
            val body: ByteArray,
            val contentEncoding: String? = null,
        )
    }
}
