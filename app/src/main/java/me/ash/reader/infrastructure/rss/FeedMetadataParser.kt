package me.ash.reader.infrastructure.rss

import java.io.InputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Metadata needed by the subscribe preview. Never contains article entries. */
data class FeedMetadata(
    val title: String?,
    val siteLink: String?,
    val iconUrl: String?,
)

class NotAFeedException : Exception("The document is not an RSS, Atom, or RDF feed")

/**
 * Reads only the feed header. Stops at the first item/entry, so large
 * full-content feeds are not materialized just to show the preview.
 */
object FeedMetadataParser {

    fun parse(input: InputStream, encoding: String? = null): FeedMetadata {
        val handler = Handler()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            runCatching {
                setFeature(
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false,
                )
            }
        }
        try {
            val source = InputSource(input)
            if (!encoding.isNullOrBlank()) source.encoding = encoding
            factory.newSAXParser().parse(source, handler)
        } catch (e: StopParsing) {
            // Expected: the feed header is complete.
        } catch (e: SAXException) {
            if (e.cause is NotAFeedException) throw e.cause as NotAFeedException
            if (e.cause is StopParsing) {
                // Some parsers wrap handler exceptions.
            } else {
                throw e
            }
        }
        return FeedMetadata(
            title = handler.title,
            siteLink = handler.siteLink,
            iconUrl = handler.iconUrl,
        )
    }

    private class StopParsing : SAXException("feed header complete")

    private class Handler : DefaultHandler() {
        var title: String? = null
            private set
        var siteLink: String? = null
            private set
        var iconUrl: String? = null
            private set

        private var root: String? = null
        private var rootDepth = 0
        private var containerDepth: Int? = null
        private var imageDepth: Int? = null
        private var depth = 0

        private var capture: StringBuilder? = null
        private var captureTarget: Target? = null
        private var captureElement: String? = null

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes,
        ) {
            depth++
            val name = (localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty())
                .substringAfter(':')
                .lowercase()

            if (root == null) {
                root = name
                rootDepth = depth
                if (name !in FEED_ROOTS) throw NotAFeedException()
                if (name == "feed") {
                    // Atom's feed element is both the root and the header container.
                    containerDepth = depth
                    return
                }
            }

            if (containerDepth == null && depth == rootDepth + 1 &&
                (name == "channel" || name == "feed")
            ) {
                containerDepth = depth
                return
            }

            // RSS 1.0 places <item> beside <channel>; RSS 2.0 and Atom place it
            // inside the container. Stop at either shape.
            if (root == "rdf" && depth == rootDepth + 1 && name == "item") {
                throw StopParsing()
            }

            val container = containerDepth ?: return
            if (name == "item" || name == "entry") throw StopParsing()

            val image = imageDepth
            if (depth == container + 1) {
                when (name) {
                    "title" -> beginCapture(Target.TITLE, name)
                    "link" -> {
                        val href = attributes.getValue("href")
                        if (href != null) {
                            // Atom links, including atom:link inside an RSS channel.
                            val rel = attributes.getValue("rel")
                            if (rel == null || rel == "alternate") siteLink = href
                        } else if (root != "feed") {
                            beginCapture(Target.SITE_LINK, name)
                        }
                    }

                    "icon", "logo" -> if (root == "feed") beginCapture(Target.ICON, name)
                    "image" -> imageDepth = depth
                }
            } else if (image != null && depth == image + 1 && name == "url") {
                beginCapture(Target.ICON, name)
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = (localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty())
                .substringAfter(':')
                .lowercase()

            if (captureElement == name) {
                val value = capture?.toString()?.trim()
                if (!value.isNullOrBlank()) {
                    when (captureTarget) {
                        Target.TITLE -> if (title == null) title = value
                        Target.SITE_LINK -> if (siteLink == null) siteLink = value
                        Target.ICON -> if (iconUrl == null) iconUrl = value
                        null -> Unit
                    }
                }
                capture = null
                captureTarget = null
                captureElement = null
            }

            if (depth == imageDepth) imageDepth = null
            if (depth == containerDepth) containerDepth = null
            depth--
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            capture?.append(ch, start, length)
        }

        private fun beginCapture(target: Target, elementName: String) {
            if (captureTarget != null) return
            captureTarget = target
            captureElement = elementName
            capture = StringBuilder()
        }

        private enum class Target { TITLE, SITE_LINK, ICON }

        companion object {
            private val FEED_ROOTS = setOf("rss", "feed", "rdf")
        }
    }
}
