package com.finops.app.data.remote

import android.util.Xml
import com.finops.app.domain.model.MarketDataException
import com.finops.app.domain.model.NewsArticle
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Flux RSS public de Google Actualités, restreint aux sites de [SOURCES] (en français). */
class GoogleNewsApi @Inject constructor(private val http: HttpClient) {

    /** Flux d'un site de [SOURCES] (une requête par site : un « OR » est plafonné à 100 articles du premier). */
    suspend fun marketNewsFeed(site: String): String = feed("site:$site when:2d")

    private suspend fun feed(query: String): String {
        val q = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        val response = try {
            http.get("$BASE?q=$q&hl=fr&gl=FR&ceid=FR:fr", mapOf("User-Agent" to USER_AGENT))
        } catch (e: java.io.IOException) {
            throw MarketDataException("Connexion impossible à Google Actualités", e)
        }
        if (response.code != 200) throw MarketDataException("Google Actualités : HTTP ${response.code}")
        return response.body
    }

    private companion object {
        const val BASE = "https://news.google.com/rss/search"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}

/** Sites retenus pour l'actualité : domaine interrogé → nom de source attendu dans le flux. */
val NEWS_SOURCES = mapOf("boursorama.com" to "Boursorama", "fortuneo.fr" to "Fortuneo")

object GoogleNewsParser {
    /** Articles du flux RSS ; le suffixe « - Source » est retiré du titre. */
    fun parse(xml: String): List<NewsArticle> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val articles = mutableListOf<NewsArticle>()
        var fields: MutableMap<String, String>? = null
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when {
                        parser.name == "item" -> fields = HashMap()
                        fields != null && parser.name in TAGS -> fields[parser.name] = parser.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "item") {
                        fields?.let { f -> article(f)?.let(articles::add) }
                        fields = null
                    }
                }
            }
        } catch (e: Exception) {
            if (articles.isEmpty()) throw MarketDataException("Format du flux d'actualités inattendu", e)
        }
        return articles.sortedByDescending { it.publishedAt ?: 0 }
    }

    private fun article(f: Map<String, String>): NewsArticle? {
        val url = f["link"]?.takeIf { it.isNotBlank() } ?: return null
        val source = f["source"]?.takeIf { it.isNotBlank() }
        val raw = f["title"].orEmpty()
        val title = source?.let { raw.removeSuffix(" - $it") } ?: raw
        if (title.isBlank()) return null
        val date = f["pubDate"]?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
        return NewsArticle(title = title, source = source, url = url, publishedAt = date)
    }

    private val TAGS = setOf("title", "link", "pubDate", "source")
}
