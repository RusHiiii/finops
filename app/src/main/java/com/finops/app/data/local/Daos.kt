package com.finops.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolios ORDER BY createdAt, id")
    fun observeAll(): Flow<List<PortfolioEntity>>

    @Query("SELECT * FROM portfolios WHERE id = :id")
    fun observe(id: Long): Flow<PortfolioEntity?>

    @Insert
    suspend fun insert(portfolio: PortfolioEntity): Long

    @Query("UPDATE portfolios SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM portfolios WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY type, ticker")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets")
    suspend fun getAll(): List<AssetEntity>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun get(id: Long): AssetEntity?

    @Query("SELECT * FROM assets WHERE ticker = :ticker AND exchange = :exchange")
    suspend fun find(ticker: String, exchange: String): AssetEntity?

    @Insert
    suspend fun insert(asset: AssetEntity): Long

    @Query("UPDATE assets SET lastPrice = :price, previousClose = :previousClose, lastPriceAt = :at WHERE id = :id")
    suspend fun updateQuote(id: Long, price: Double, previousClose: Double?, at: Long)

    /** Supprime les actifs qui n'ont plus aucune transaction. */
    @Query("DELETE FROM assets WHERE id NOT IN (SELECT DISTINCT assetId FROM transactions)")
    suspend fun deleteOrphans()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE portfolioId = :portfolioId AND assetId = :assetId")
    suspend fun getForHolding(portfolioId: Long, assetId: Long): List<TransactionEntity>

    @Insert
    suspend fun insert(tx: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FxRateDao {
    @Query("SELECT * FROM fx_rates WHERE pair = :pair")
    fun observe(pair: String): Flow<FxRateEntity?>

    @Upsert
    suspend fun upsert(rate: FxRateEntity)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE ticker = :ticker AND exchange = :exchange)")
    fun observeContains(ticker: String, exchange: String): Flow<Boolean>

    @Query("SELECT * FROM watchlist")
    suspend fun getAll(): List<WatchlistEntity>

    @Upsert
    suspend fun upsert(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE ticker = :ticker AND exchange = :exchange")
    suspend fun delete(ticker: String, exchange: String)

    @Query("UPDATE watchlist SET lastPrice = :price, previousClose = :previousClose, lastPriceAt = :at WHERE ticker = :ticker AND exchange = :exchange")
    suspend fun updateQuote(ticker: String, exchange: String, price: Double, previousClose: Double?, at: Long)
}

@Dao
interface IsinDao {
    @Query("SELECT isin FROM instrument_isin WHERE ticker = :ticker AND exchange = :exchange")
    fun observe(ticker: String, exchange: String): Flow<String?>

    @Upsert
    suspend fun upsert(link: InstrumentIsinEntity)
}

/** Accès en bloc pour la sauvegarde / restauration. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM portfolios") suspend fun portfolios(): List<PortfolioEntity>
    @Query("SELECT * FROM assets") suspend fun assets(): List<AssetEntity>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM watchlist") suspend fun watchlist(): List<WatchlistEntity>
    @Query("SELECT * FROM instrument_isin") suspend fun isins(): List<InstrumentIsinEntity>

    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM portfolios") suspend fun clearPortfolios()
    @Query("DELETE FROM assets") suspend fun clearAssets()
    @Query("DELETE FROM watchlist") suspend fun clearWatchlist()
    @Query("DELETE FROM instrument_isin") suspend fun clearIsins()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPortfolios(items: List<PortfolioEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAssets(items: List<AssetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTransactions(items: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWatchlist(items: List<WatchlistEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertIsins(items: List<InstrumentIsinEntity>)
}
