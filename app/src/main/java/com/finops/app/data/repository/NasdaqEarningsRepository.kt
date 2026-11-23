package com.finops.app.data.repository

import com.finops.app.data.remote.NasdaqEarningsApi
import com.finops.app.data.remote.NasdaqEarningsParser
import com.finops.app.domain.model.EarningsEvent
import com.finops.app.domain.repository.EarningsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Calendrier Nasdaq, interrogé jour par jour (jours ouvrés) et mis en cache en mémoire. */
@Singleton
class NasdaqEarningsRepository @Inject constructor(
    private val api: NasdaqEarningsApi,
    private val clock: Clock,
) : EarningsRepository {
    private class Cached(val at: Long, val events: List<EarningsEvent>)

    private val cache = HashMap<LocalDate, Cached>()
    private val lock = Mutex()
    private val permits = Semaphore(PARALLEL_REQUESTS)

    override suspend fun calendar(from: LocalDate, to: LocalDate): List<EarningsEvent> = coroutineScope {
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(to) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .map { day -> async { day(day) } }
            .toList()
            .awaitAll()
            .flatten()
    }

    private suspend fun day(date: LocalDate): List<EarningsEvent> {
        val now = clock.millis()
        lock.withLock { cache[date] }?.takeIf { now - it.at < TTL }?.let { return it.events }
        val events = permits.withPermit { NasdaqEarningsParser.parse(api.day(date), date) }
        lock.withLock { cache[date] = Cached(now, events) }
        return events
    }

    private companion object {
        const val TTL = 6 * 60 * 60_000L
        const val PARALLEL_REQUESTS = 6
    }
}
