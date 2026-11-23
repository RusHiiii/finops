package com.finops.app.data.repository

import com.finops.app.data.remote.GoogleNewsApi
import com.finops.app.data.remote.GoogleNewsParser
import com.finops.app.data.remote.NEWS_SOURCES
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.finops.app.domain.model.NewsArticle
import com.finops.app.domain.repository.NewsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.finops.app.common.DefaultDispatcher
import com.finops.app.common.runSuspendCatching
import javax.inject.Inject

class GoogleNewsRepository @Inject constructor(
    private val api: GoogleNewsApi,
    @param:DefaultDispatcher private val cpu: CoroutineDispatcher,
) : NewsRepository {
    /** Articles Boursorama et Fortuneo fusionnés ; un site en échec n'empêche pas l'autre. */
    override suspend fun marketNews(): List<NewsArticle> = coroutineScope {
        val feeds = NEWS_SOURCES.keys.map { site -> async { runSuspendCatching { api.marketNewsFeed(site) } } }.awaitAll()
        val xmls = feeds.mapNotNull { it.getOrNull() }
        if (xmls.isEmpty()) throw feeds.first().exceptionOrNull()!!
        val allowed = NEWS_SOURCES.values.map { it.lowercase() }
        withContext(cpu) {
            xmls.flatMap { GoogleNewsParser.parse(it) }
                .filter { a -> a.source?.lowercase()?.let { s -> allowed.any { s.startsWith(it) } } == true }
                .distinctBy { it.url }
                .sortedByDescending { it.publishedAt ?: 0 }
        }
    }
}
