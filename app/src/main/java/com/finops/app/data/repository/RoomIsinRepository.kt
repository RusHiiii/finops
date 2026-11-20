package com.finops.app.data.repository

import com.finops.app.data.local.AppDatabase
import com.finops.app.data.local.InstrumentIsinEntity
import com.finops.app.domain.model.Isin
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.repository.IsinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomIsinRepository @Inject constructor(db: AppDatabase) : IsinRepository {
    private val dao = db.isinDao()

    override fun observeIsin(symbol: Symbol): Flow<Isin?> =
        dao.observe(symbol.ticker, symbol.exchange).map { it?.let(Isin::parse) }.distinctUntilChanged()

    override suspend fun link(symbol: Symbol, isin: Isin) =
        dao.upsert(InstrumentIsinEntity(symbol.ticker, symbol.exchange, isin.value))
}
