package com.finops.app.domain.calculator

import com.finops.app.domain.model.Asset
import com.finops.app.domain.model.PricePoint
import com.finops.app.domain.model.Transaction
import com.finops.app.domain.model.ValuePoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reconstitue la courbe de valeur d'un ensemble de positions à partir de l'historique
 * des cours et des transactions. Le taux USD/EUR courant est appliqué à tout l'historique
 * (approximation : pas d'historique de change).
 */
object PortfolioHistoryCalculator {

    /**
     * @param histories cours par id d'actif ; un actif sans historique est valorisé à son dernier cours connu.
     * @return points triés par date, à partir du premier instant où une position existe.
     */
    fun compute(
        assets: List<Asset>,
        transactions: List<Transaction>,
        histories: Map<Long, List<PricePoint>>,
        usdToEur: Double?,
        zone: ZoneId,
    ): List<ValuePoint> {
        val timeline = histories.values.flatten().map { it.epochMillis }.distinct().sorted()
        if (timeline.isEmpty()) return emptyList()

        val tracks = assets.mapNotNull { asset ->
            val rate = PortfolioCalculator.rateToEur(asset.currency, usdToEur) ?: return@mapNotNull null
            val txs = PortfolioCalculator.sorted(transactions.filter { it.assetId == asset.id })
            if (txs.isEmpty()) return@mapNotNull null
            Track(asset, txs, histories[asset.id].orEmpty().sortedBy { it.epochMillis }, rate)
        }
        if (tracks.isEmpty()) return emptyList()

        val points = timeline.map { t ->
            val date = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            var value = 0.0
            var invested = 0.0
            for (track in tracks) {
                val position = track.positionAt(date)
                if (position.quantity <= 0.0 && position.cost == 0.0) continue
                value += position.quantity * track.priceAt(t) * track.rate
                invested += position.cost * track.rate
            }
            ValuePoint(t, value, invested)
        }
        return points.dropWhile { it.valueEur == 0.0 && it.investedEur == 0.0 }
    }

    private class Track(
        val asset: Asset,
        val transactions: List<Transaction>,
        val prices: List<PricePoint>,
        val rate: Double,
    ) {
        private var txIndex = 0
        private var position = PortfolioCalculator.Position.EMPTY
        private var priceIndex = -1

        /** Appels à dates croissantes : parcours incrémental. */
        fun positionAt(date: LocalDate): PortfolioCalculator.Position {
            while (txIndex < transactions.size && !transactions[txIndex].date.isAfter(date)) {
                position = position.apply(transactions[txIndex++])
            }
            return position
        }

        /** Dernier cours connu à [t] ; avant le premier point : premier cours ; sans historique : dernier cours. */
        fun priceAt(t: Long): Double {
            while (priceIndex + 1 < prices.size && prices[priceIndex + 1].epochMillis <= t) priceIndex++
            return when {
                priceIndex >= 0 -> prices[priceIndex].price
                prices.isNotEmpty() -> prices.first().price
                else -> asset.lastPrice ?: (if (position.quantity > 0) position.cost / position.quantity else 0.0)
            }
        }
    }
}
