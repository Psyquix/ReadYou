package me.ash.reader.infrastructure.rss

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FeedMetadataParserTest {

    private fun parse(xml: String): FeedMetadata =
        FeedMetadataParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    @Test
    fun `rss reads title link and image before the first item`() {
        val metadata = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <title>PLIC</title>
                <link>https://example.com/plic/</link>
                <image>
                  <url>https://example.com/favicon.ico</url>
                  <title>PLIC</title>
                </image>
                <item>
                  <title>Article title that must not overwrite the feed title</title>
                  <link>https://example.com/plic/1/</link>
                </item>
              </channel>
            </rss>
            """.trimIndent()
        )

        assertEquals("PLIC", metadata.title)
        assertEquals("https://example.com/plic/", metadata.siteLink)
        assertEquals("https://example.com/favicon.ico", metadata.iconUrl)
    }

    @Test
    fun `atom reads title alternate link and icon before the first entry`() {
        val metadata = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Example Atom</title>
              <link rel="self" href="https://example.com/feed.xml"/>
              <link rel="alternate" href="https://example.com/"/>
              <icon>https://example.com/icon.png</icon>
              <entry>
                <title>Entry title that must not overwrite the feed title</title>
                <link href="https://example.com/1"/>
              </entry>
            </feed>
            """.trimIndent()
        )

        assertEquals("Example Atom", metadata.title)
        assertEquals("https://example.com/", metadata.siteLink)
        assertEquals("https://example.com/icon.png", metadata.iconUrl)
    }

    @Test
    fun `atom falls back to logo when icon is absent`() {
        val metadata = parse(
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Example Atom</title>
              <link href="https://example.com/"/>
              <logo>https://example.com/logo.png</logo>
              <entry><title>One</title></entry>
            </feed>
            """.trimIndent()
        )

        assertEquals("https://example.com/logo.png", metadata.iconUrl)
    }

    @Test
    fun `rss ignores an atom self link and reads the channel link`() {
        val metadata = parse(
            """
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>RSS with atom self link</title>
                <atom:link href="https://example.com/feed.xml" rel="self"/>
                <link>https://example.com/</link>
                <item><title>One</title></item>
              </channel>
            </rss>
            """.trimIndent()
        )

        assertEquals("https://example.com/", metadata.siteLink)
    }

    @Test
    fun `rss with a legacy doctype still previews`() {
        val metadata = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE rss PUBLIC "-//Netscape Communications//DTD RSS 0.91//EN"
                "http://my.netscape.com/publish/formats/rss-0.91.dtd">
            <rss version="0.91">
              <channel>
                <title>Legacy feed</title>
                <link>https://example.com/</link>
                <item><title>One</title></item>
              </channel>
            </rss>
            """.trimIndent()
        )

        assertEquals("Legacy feed", metadata.title)
    }

    @Test
    fun `rss 1 0 reads channel metadata before a sibling item`() {
        val metadata = parse(
            """
            <rdf:RDF
                xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                xmlns="http://purl.org/rss/1.0/">
              <channel rdf:about="https://example.com/feed.xml">
                <title>RSS 1.0</title>
                <link>https://example.com/</link>
              </channel>
              <item rdf:about="https://example.com/1">
                <title>One</title>
              </item>
            </rdf:RDF>
            """.trimIndent()
        )

        assertEquals("RSS 1.0", metadata.title)
        assertEquals("https://example.com/", metadata.siteLink)
    }

    @Test
    fun `stops at the first item before parsing entry contents`() {
        val metadata = parse(
            """
            <rss version="2.0">
              <channel>
                <title>Header only</title>
                <item>
                  <title>Malformed &missing; entry body must not be read</title>
                </item>
              </channel>
            </rss>
            """.trimIndent()
        )

        assertEquals("Header only", metadata.title)
    }

    @Test
    fun `missing optional metadata is null`() {
        val metadata = parse(
            """
            <rss version="2.0">
              <channel>
                <title>Only a title</title>
                <item><title>One</title></item>
              </channel>
            </rss>
            """.trimIndent()
        )

        assertEquals("Only a title", metadata.title)
        assertNull(metadata.siteLink)
        assertNull(metadata.iconUrl)
    }

    @Test
    fun `html is rejected as not a feed`() {
        assertThrows(NotAFeedException::class.java) {
            parse("<html><head><title>A web page</title></head></html>")
        }
    }
}
