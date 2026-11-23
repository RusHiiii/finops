package com.finops.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations du schéma. Procédure pour un changement d'entité :
 * 1. Incrémenter `version` dans [AppDatabase].
 * 2. Ajouter ici une `Migration(n, n + 1)` avec le SQL et l'ajouter à [ALL_MIGRATIONS].
 * 3. Compiler : le schéma `app/schemas/.../<n+1>.json` est généré, le versionner.
 * 4. Lancer `./gradlew connectedAndroidTest` (MigrationTest).
 */

/** v2 : clôture précédente, pour la variation du jour. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE assets ADD COLUMN previousClose REAL")
    }
}

/** v3 : watchlist. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `watchlist` (`ticker` TEXT NOT NULL, `exchange` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT, `currency` TEXT, `lastPrice` REAL, `previousClose` REAL, " +
                "`lastPriceAt` INTEGER, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`ticker`, `exchange`))"
        )
    }
}

/** v4 : correspondance symbole → ISIN. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `instrument_isin` (`ticker` TEXT NOT NULL, `exchange` TEXT NOT NULL, " +
                "`isin` TEXT NOT NULL, PRIMARY KEY(`ticker`, `exchange`))"
        )
    }
}

/** v5 : taux USD/EUR saisi par transaction. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN usdToEur REAL")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
