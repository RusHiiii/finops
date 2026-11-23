package com.finops.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PortfolioEntity::class, AssetEntity::class, TransactionEntity::class, FxRateEntity::class, WatchlistEntity::class, InstrumentIsinEntity::class],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun portfolioDao(): PortfolioDao
    abstract fun assetDao(): AssetDao
    abstract fun transactionDao(): TransactionDao
    abstract fun fxRateDao(): FxRateDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun isinDao(): IsinDao
    abstract fun backupDao(): BackupDao

    companion object {
        private const val NAME = "finops.db"

        /** Crée la base ; un portefeuille « Principal » est inséré à la création. */
        fun build(context: Context, now: () -> Long): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(*ALL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO portfolios (name, createdAt) VALUES (?, ?)",
                            arrayOf<Any>("Principal", now()),
                        )
                    }
                })
                .build()
    }
}
