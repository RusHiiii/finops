package com.finops.app.data.repository

import androidx.room.withTransaction
import com.finops.app.data.local.AppDatabase
import com.finops.app.data.local.AssetEntity
import com.finops.app.data.local.InstrumentIsinEntity
import com.finops.app.data.local.PortfolioEntity
import com.finops.app.data.local.TransactionEntity
import com.finops.app.data.local.WatchlistEntity
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.BackupSummary
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.TransactionSide
import com.finops.app.domain.model.ValidationException
import com.finops.app.domain.repository.BackupRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Sauvegarde JSON versionnée de la base (hors cours, recalculés au rafraîchissement). */
@Singleton
class RoomBackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val clock: Clock,
) : BackupRepository {
    private val dao = db.backupDao()

    override suspend fun export(): String {
        val backup = BackupDto(
            version = VERSION,
            exportedAt = clock.millis(),
            portfolios = dao.portfolios().map { PortfolioDto(it.id, it.name, it.createdAt) },
            assets = dao.assets().map { AssetDto(it.id, it.ticker, it.exchange, it.name, it.type.name, it.currency.name) },
            transactions = dao.transactions().map {
                TransactionDto(it.id, it.portfolioId, it.assetId, it.side.name, it.quantity, it.price, it.fees, it.date.toString())
            },
            watchlist = dao.watchlist().map { WatchlistDto(it.ticker, it.exchange, it.name, it.type?.name, it.currency, it.addedAt) },
            isins = dao.isins().map { IsinDto(it.ticker, it.exchange, it.isin) },
        )
        return JSON.encodeToString(BackupDto.serializer(), backup)
    }

    override suspend fun restore(document: String): BackupSummary {
        val backup = try {
            JSON.decodeFromString(BackupDto.serializer(), document)
        } catch (e: Exception) {
            throw ValidationException("Fichier de sauvegarde invalide")
        }
        if (backup.version > VERSION) throw ValidationException("Sauvegarde créée par une version plus récente de l'application")
        val entities = try {
            Entities(
                portfolios = backup.portfolios.map { PortfolioEntity(it.id, it.name, it.createdAt) },
                assets = backup.assets.map {
                    AssetEntity(it.id, it.ticker, it.exchange, it.name, AssetType.valueOf(it.type), CurrencyCode.valueOf(it.currency), null, null, null)
                },
                transactions = backup.transactions.map {
                    TransactionEntity(it.id, it.portfolioId, it.assetId, TransactionSide.valueOf(it.side), it.quantity, it.price, it.fees, LocalDate.parse(it.date))
                },
                watchlist = backup.watchlist.map {
                    WatchlistEntity(it.ticker, it.exchange, it.name, it.type?.let(AssetType::valueOf), it.currency, null, null, null, it.addedAt)
                },
                isins = backup.isins.map { InstrumentIsinEntity(it.ticker, it.exchange, it.isin) },
            )
        } catch (e: IllegalArgumentException) {
            throw ValidationException("Fichier de sauvegarde invalide : ${e.message}")
        } catch (e: java.time.format.DateTimeParseException) {
            throw ValidationException("Fichier de sauvegarde invalide : date incorrecte")
        }
        val portfolioIds = entities.portfolios.map { it.id }.toSet()
        val assetIds = entities.assets.map { it.id }.toSet()
        if (entities.transactions.any { it.portfolioId !in portfolioIds || it.assetId !in assetIds }) {
            throw ValidationException("Fichier de sauvegarde incohérent : transaction sans portefeuille ou sans actif")
        }
        db.withTransaction {
            clearTables()
            dao.insertPortfolios(entities.portfolios)
            dao.insertAssets(entities.assets)
            dao.insertTransactions(entities.transactions)
            dao.insertWatchlist(entities.watchlist)
            dao.insertIsins(entities.isins)
        }
        return BackupSummary(entities.portfolios.size, entities.transactions.size, entities.watchlist.size)
    }

    override suspend fun clearAll() {
        db.withTransaction {
            clearTables()
            // Même état qu'une nouvelle installation : un portefeuille par défaut.
            dao.insertPortfolios(listOf(PortfolioEntity(name = DEFAULT_PORTFOLIO, createdAt = clock.millis())))
        }
    }

    private suspend fun clearTables() {
        dao.clearTransactions()
        dao.clearPortfolios()
        dao.clearAssets()
        dao.clearWatchlist()
        dao.clearIsins()
    }

    private class Entities(
        val portfolios: List<PortfolioEntity>,
        val assets: List<AssetEntity>,
        val transactions: List<TransactionEntity>,
        val watchlist: List<WatchlistEntity>,
        val isins: List<InstrumentIsinEntity>,
    )

    private companion object {
        const val VERSION = 1
        const val DEFAULT_PORTFOLIO = "Principal"
        val JSON = Json { ignoreUnknownKeys = true; prettyPrint = true }
    }
}

@Serializable
private data class BackupDto(
    val version: Int,
    val exportedAt: Long,
    val portfolios: List<PortfolioDto> = emptyList(),
    val assets: List<AssetDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    val watchlist: List<WatchlistDto> = emptyList(),
    val isins: List<IsinDto> = emptyList(),
)

@Serializable
private data class PortfolioDto(val id: Long, val name: String, val createdAt: Long)

@Serializable
private data class AssetDto(val id: Long, val ticker: String, val exchange: String, val name: String, val type: String, val currency: String)

@Serializable
private data class TransactionDto(
    val id: Long,
    val portfolioId: Long,
    val assetId: Long,
    val side: String,
    val quantity: Double,
    val price: Double,
    val fees: Double,
    /** ISO-8601 (AAAA-MM-JJ). */
    val date: String,
)

@Serializable
private data class WatchlistDto(val ticker: String, val exchange: String, val name: String, val type: String?, val currency: String?, val addedAt: Long)

@Serializable
private data class IsinDto(val ticker: String, val exchange: String, val isin: String)
