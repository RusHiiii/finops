package com.finops.app.domain.usecase

import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.NewsArticle
import com.finops.app.domain.model.WatchedEarnings
import com.finops.app.domain.repository.EarningsReminderScheduler
import com.finops.app.domain.repository.EarningsRepository
import com.finops.app.domain.repository.NewsRepository
import com.finops.app.domain.repository.SettingsRepository
import com.finops.app.domain.repository.WatchlistRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** [trackedCount] = actions américaines suivies (0 : rien à chercher). */
data class WatchlistEarnings(val trackedCount: Int, val events: List<WatchedEarnings>)

class GetMarketNewsUseCase @Inject constructor(private val repository: NewsRepository) {
    suspend operator fun invoke(): List<NewsArticle> = repository.marketNews()
}

/** Publications de résultats des actions américaines de la watchlist entre deux dates, triées par date. */
class GetWatchlistEarningsUseCase @Inject constructor(
    private val watchlist: WatchlistRepository,
    private val earnings: EarningsRepository,
    private val clock: Clock,
) {
    /** Par défaut : de [PAST_DAYS] jours avant à [AHEAD_DAYS] jours après aujourd'hui. */
    suspend operator fun invoke(
        from: LocalDate = LocalDate.now(clock).minusDays(PAST_DAYS),
        to: LocalDate = LocalDate.now(clock).plusDays(AHEAD_DAYS),
    ): WatchlistEarnings {
        val tracked = watchlist.getWatchlist()
            .filter { it.type != AssetType.ETF && it.symbol.exchange in US_EXCHANGES }
            .associateBy { normalize(it.symbol.ticker) }
        if (tracked.isEmpty()) return WatchlistEarnings(0, emptyList())
        val events = earnings.calendar(from, to)
            .mapNotNull { e -> tracked[normalize(e.ticker)]?.let { WatchedEarnings(it.symbol, it.name, e) } }
            .sortedWith(compareBy({ it.event.date }, { it.event.timing }, { it.name }))
        return WatchlistEarnings(tracked.size, events)
    }

    /** « BRK.B » (Google) = « BRK/B » (Nasdaq). */
    private fun normalize(ticker: String) = ticker.uppercase().filter { it.isLetterOrDigit() }

    companion object {
        const val PAST_DAYS = 7L
        const val AHEAD_DAYS = 35L
        val US_EXCHANGES = setOf("NASDAQ", "NYSE", "NYSEARCA", "NYSEAMERICAN", "NYSEMKT", "BATS")
    }
}

/** Publications du prochain jour de bourse (lundi si l'on est vendredi), pour le rappel de la veille. */
class GetNextDayEarningsUseCase @Inject constructor(
    private val getWatchlistEarnings: GetWatchlistEarningsUseCase,
    private val clock: Clock,
) {
    /** null le week-end : le rappel du lundi part le vendredi. */
    suspend operator fun invoke(): Pair<LocalDate, List<WatchedEarnings>>? {
        val today = LocalDate.now(clock)
        val next = when (today.dayOfWeek) {
            DayOfWeek.FRIDAY -> today.plusDays(3)
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> return null
            else -> today.plusDays(1)
        }
        return next to getWatchlistEarnings(next, next).events
    }
}

/** Active ou coupe le rappel des résultats (préférence + tâche de fond). */
class SetEarningsNotificationsUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduler: EarningsReminderScheduler,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settings.update { it.copy(notifyEarnings = enabled) }
        if (enabled) scheduler.schedule() else scheduler.cancel()
    }
}
