package com.finops.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.TransactionSide
import java.time.LocalDate

@Entity(tableName = "assets", indices = [Index(value = ["ticker", "exchange"], unique = true)])
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    val exchange: String,
    val name: String,
    val type: AssetType,
    val currency: CurrencyCode,
    val lastPrice: Double?,
    val lastPriceAt: Long?,
    /** Clôture de la séance précédente (variation du jour). Ajoutée en v2. */
    val previousClose: Double? = null,
)

@Entity(tableName = "portfolios")
data class PortfolioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = PortfolioEntity::class,
            parentColumns = ["id"],
            childColumns = ["portfolioId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("portfolioId"), Index("assetId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val portfolioId: Long,
    val assetId: Long,
    val side: TransactionSide,
    val quantity: Double,
    val price: Double,
    val fees: Double,
    val date: LocalDate,
    val usdToEur: Double? = null,
)

@Entity(tableName = "fx_rates")
data class FxRateEntity(
    @PrimaryKey val pair: String,
    val rate: Double,
    val updatedAt: Long,
)

/** Instrument suivi sans être détenu. Ajoutée en v3. */
@Entity(tableName = "watchlist", primaryKeys = ["ticker", "exchange"])
data class WatchlistEntity(
    val ticker: String,
    val exchange: String,
    val name: String,
    val type: AssetType?,
    /** Code ISO brut (peut être hors EUR / USD). */
    val currency: String?,
    val lastPrice: Double?,
    val previousClose: Double?,
    val lastPriceAt: Long?,
    val addedAt: Long,
)

/** Correspondance symbole → ISIN. Ajoutée en v4. */
@Entity(tableName = "instrument_isin", primaryKeys = ["ticker", "exchange"])
data class InstrumentIsinEntity(
    val ticker: String,
    val exchange: String,
    val isin: String,
)
