package com.finops.app.data.repository

import androidx.room.withTransaction
import com.finops.app.data.local.AppDatabase
import com.finops.app.data.local.AssetEntity
import com.finops.app.data.local.FxRateEntity
import com.finops.app.data.local.PortfolioEntity
import com.finops.app.data.local.toDomain
import com.finops.app.data.local.toEntity
import com.finops.app.domain.calculator.PortfolioCalculator
import com.finops.app.domain.model.Asset
import com.finops.app.domain.model.Portfolio
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.Transaction
import com.finops.app.domain.model.TransactionDraft
import com.finops.app.domain.model.ValidationException
import com.finops.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Données utilisateur stockées dans Room : source de vérité de l'application. */
@Singleton
class OfflineFirstPortfolioRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) : PortfolioRepository {
    private val portfolioDao = db.portfolioDao()
    private val assetDao = db.assetDao()
    private val txDao = db.transactionDao()
    private val fxDao = db.fxRateDao()

    override fun observePortfolios(): Flow<List<Portfolio>> =
        portfolioDao.observeAll().map { l -> l.map { it.toDomain() } }

    override fun observePortfolio(id: Long): Flow<Portfolio?> = portfolioDao.observe(id).map { it?.toDomain() }

    override fun observeAssets(): Flow<List<Asset>> = assetDao.observeAll().map { l -> l.map { it.toDomain() } }

    override fun observeTransactions(): Flow<List<Transaction>> =
        txDao.observeAll().map { l -> l.map { it.toDomain() } }

    override fun observeUsdToEur(): Flow<Double?> = fxDao.observe(USD_EUR).map { it?.rate }

    override suspend fun getAssets(): List<Asset> = assetDao.getAll().map { it.toDomain() }

    override suspend fun getAsset(id: Long): Asset? = assetDao.get(id)?.toDomain()

    override suspend fun findAsset(symbol: Symbol): Asset? = assetDao.find(symbol.ticker, symbol.exchange)?.toDomain()

    override suspend fun createPortfolio(name: String): Long =
        portfolioDao.insert(PortfolioEntity(name = name, createdAt = clock.millis()))

    override suspend fun renamePortfolio(id: Long, name: String) = portfolioDao.rename(id, name)

    override suspend fun deletePortfolio(id: Long) {
        db.withTransaction {
            portfolioDao.deleteById(id) // transactions supprimées en cascade
            assetDao.deleteOrphans()
        }
    }

    override suspend fun addTransaction(draft: TransactionDraft): Long = db.withTransaction {
        val ticker = draft.symbol.ticker
        val exchange = draft.symbol.exchange
        val assetId = assetDao.find(ticker, exchange)?.id ?: assetDao.insert(
            AssetEntity(
                ticker = ticker,
                exchange = exchange,
                name = draft.name,
                type = draft.type,
                currency = draft.currency,
                lastPrice = null,
                lastPriceAt = null,
            )
        )
        val tx = Transaction(
            portfolioId = draft.portfolioId,
            assetId = assetId,
            side = draft.side,
            quantity = draft.quantity,
            price = draft.price,
            fees = draft.fees,
            date = draft.date,
        )
        val existing = txDao.getForHolding(draft.portfolioId, assetId).map { it.toDomain() }
        // id max : la nouvelle transaction passe après celles du même jour.
        if (!PortfolioCalculator.isConsistent(existing + tx.copy(id = Long.MAX_VALUE))) {
            throw ValidationException("Vente supérieure à la quantité détenue à cette date")
        }
        txDao.insert(tx.toEntity())
    }

    override suspend fun getTransaction(id: Long): Transaction? = txDao.getById(id)?.toDomain()

    override suspend fun updateTransaction(transaction: Transaction) {
        db.withTransaction {
            val old = txDao.getById(transaction.id)?.toDomain() ?: throw ValidationException("Transaction introuvable")
            val target = txDao.getForHolding(transaction.portfolioId, transaction.assetId)
                .map { it.toDomain() }
                .filter { it.id != transaction.id }
            val source = if (old.portfolioId == transaction.portfolioId) emptyList()
            else txDao.getForHolding(old.portfolioId, old.assetId).map { it.toDomain() }.filter { it.id != transaction.id }
            if (!PortfolioCalculator.isConsistent(target + transaction) || !PortfolioCalculator.isConsistent(source)) {
                throw ValidationException("Modification impossible : des ventes dépasseraient la quantité détenue")
            }
            txDao.update(transaction.toEntity())
        }
    }

    override suspend fun deleteTransaction(transaction: Transaction) {
        db.withTransaction {
            val remaining = txDao.getForHolding(transaction.portfolioId, transaction.assetId)
                .map { it.toDomain() }
                .filter { it.id != transaction.id }
            if (!PortfolioCalculator.isConsistent(remaining)) {
                throw ValidationException("Suppression impossible : des ventes dépasseraient la quantité détenue")
            }
            txDao.deleteById(transaction.id)
            assetDao.deleteOrphans()
        }
    }

    override suspend fun updateQuote(assetId: Long, price: Double, previousClose: Double?, at: Long) =
        assetDao.updateQuote(assetId, price, previousClose, at)

    override suspend fun updateUsdToEur(rate: Double, at: Long) = fxDao.upsert(FxRateEntity(USD_EUR, rate, at))

    private companion object {
        const val USD_EUR = "USD_EUR"
    }
}
