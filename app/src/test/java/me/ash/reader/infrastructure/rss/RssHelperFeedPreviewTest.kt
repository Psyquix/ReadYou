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

    private val feedBody =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <title>Local Feed</title>
            <link>https://example.com/</link>
            <image><url>/icon.png</url></image>
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

    @Before
    fun setUp() {
        server = TestHttpServer(feedBody = feedBody, pageBody = pageBody)
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
                iconUrl = "https://example.com/icon.png",
            ) { progress += it }

        assertEquals("Local Feed", feed.title)
        assertEquals(feedBody.size.toLong(), progress.last().bytesRead)
        assertEquals(feedBody.size.toLong(), progress.last().totalBytes)
        assertEquals(1f, progress.last().fraction)
        assertTrue(progress.zipWithNext().all { (a, b) -> b.bytesRead >= a.bytesRead })
    }

    @Test
    fun `gzip load reports decompressed progress against the identity length`() = runBlocking {
        val progress = mutableListOf<FeedLoadProgress>()
        val feed =
            helper.loadFeed(
                feedLink = url("/feed-gzip"),
                iconUrl = "https://example.com/icon.png",
            ) { progress += it }

        assertEquals("Local Feed", feed.title)
        assertEquals(feedBody.size.toLong(), progress.last().bytesRead)
        assertEquals(feedBody.size.toLong(), progress.last().totalBytes)
        assertEquals(1f, progress.last().fraction)
    }

    private fun url(path: String): String = "http://127.0.0.1:${server.port}$path"

    private class TestHttpServer(
        private val feedBody: ByteArray,
        private val pageBody: ByteArray,
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
