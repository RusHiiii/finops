package com.finops.app.data.repository

import com.finops.app.data.local.AppDatabase
import com.finops.app.data.local.WatchlistEntity
import com.finops.app.data.local.toDomain
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.WatchlistItem
import com.finops.app.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomWatchlistRepository @Inject constructor(db: AppDatabase) : WatchlistRepository {
    private val dao = db.watchlistDao()

    override fun observeWatchlist(): Flow<List<WatchlistItem>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    override fun observeIsWatched(symbol: Symbol): Flow<Boolean> = dao.observeContains(symbol.ticker, symbol.exchange)

    override suspend fun getWatchlist(): List<WatchlistItem> = dao.getAll().map { it.toDomain() }

    override suspend fun add(item: WatchlistItem) = dao.upsert(
        WatchlistEntity(
            ticker = item.symbol.ticker,
            exchange = item.symbol.exchange,
            name = item.name,
            type = item.type,
            currency = item.currency,
            lastPrice = item.lastPrice,
            previousClose = item.previousClose,
            lastPriceAt = item.lastPriceAt,
            addedAt = item.addedAt,
        )
    )

    override suspend fun remove(symbol: Symbol) = dao.delete(symbol.ticker, symbol.exchange)

    override suspend fun updateQuote(symbol: Symbol, price: Double, previousClose: Double?, at: Long) =
        dao.updateQuote(symbol.ticker, symbol.exchange, price, previousClose, at)
}
