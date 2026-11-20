package com.finops.app.domain.usecase

import com.finops.app.domain.model.InstrumentSnapshot
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.WatchlistItem
import com.finops.app.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

class ObserveWatchlistUseCase @Inject constructor(private val repository: WatchlistRepository) {
    operator fun invoke(): Flow<List<WatchlistItem>> = repository.observeWatchlist()
}

class ObserveIsWatchedUseCase @Inject constructor(private val repository: WatchlistRepository) {
    operator fun invoke(symbol: Symbol): Flow<Boolean> = repository.observeIsWatched(symbol)
}

/** Ajoute l'instrument à la watchlist (avec son cours courant) ou l'en retire s'il y est déjà. */
class ToggleWatchlistUseCase @Inject constructor(
    private val repository: WatchlistRepository,
    private val clock: Clock,
) {
    /** @return true si l'instrument est désormais suivi. */
    suspend operator fun invoke(snapshot: InstrumentSnapshot): Boolean {
        if (repository.observeIsWatched(snapshot.symbol).first()) {
            repository.remove(snapshot.symbol)
            return false
        }
        val q = snapshot.quote
        repository.add(
            WatchlistItem(
                symbol = snapshot.symbol,
                name = q.name.ifBlank { snapshot.symbol.ticker },
                type = q.type,
                currency = q.currency,
                lastPrice = q.price,
                previousClose = q.previousClose,
                lastPriceAt = clock.millis(),
                addedAt = clock.millis(),
            )
        )
        return true
    }
}

class RemoveFromWatchlistUseCase @Inject constructor(private val repository: WatchlistRepository) {
    suspend operator fun invoke(symbol: Symbol) = repository.remove(symbol)
}
