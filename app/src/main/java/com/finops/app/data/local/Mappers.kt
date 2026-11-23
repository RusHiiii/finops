package com.finops.app.data.local

import com.finops.app.domain.model.Asset
import com.finops.app.domain.model.Portfolio
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.Transaction
import com.finops.app.domain.model.WatchlistItem

fun AssetEntity.toDomain() = Asset(
    id = id,
    symbol = Symbol(ticker, exchange),
    name = name,
    type = type,
    currency = currency,
    lastPrice = lastPrice,
    previousClose = previousClose,
    lastPriceAt = lastPriceAt,
)

fun PortfolioEntity.toDomain() = Portfolio(id, name)

fun TransactionEntity.toDomain() = Transaction(id, portfolioId, assetId, side, quantity, price, fees, date, usdToEur)
fun Transaction.toEntity() = TransactionEntity(id, portfolioId, assetId, side, quantity, price, fees, date, usdToEur)

fun WatchlistEntity.toDomain() = WatchlistItem(
    symbol = Symbol(ticker, exchange),
    name = name,
    type = type,
    currency = currency,
    lastPrice = lastPrice,
    previousClose = previousClose,
    lastPriceAt = lastPriceAt,
    addedAt = addedAt,
)
