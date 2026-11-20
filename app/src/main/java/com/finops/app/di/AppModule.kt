package com.finops.app.di

import android.content.Context
import com.finops.app.common.DefaultDispatcher
import com.finops.app.common.IoDispatcher
import com.finops.app.data.local.AppDatabase
import com.finops.app.data.local.ContentResolverDocumentStorage
import com.finops.app.data.remote.HttpClient
import com.finops.app.data.remote.UrlConnectionHttpClient
import com.finops.app.data.repository.GoogleFinanceMarketRepository
import com.finops.app.data.repository.JustEtfFundRepository
import com.finops.app.data.repository.PreferencesSettingsRepository
import com.finops.app.data.repository.RoomBackupRepository
import com.finops.app.data.repository.RoomIsinRepository
import com.finops.app.data.repository.OfflineFirstPortfolioRepository
import com.finops.app.data.repository.RoomWatchlistRepository
import com.finops.app.domain.repository.BackupRepository
import com.finops.app.domain.repository.DocumentStorage
import com.finops.app.domain.repository.FundRepository
import com.finops.app.domain.repository.IsinRepository
import com.finops.app.domain.repository.MarketRepository
import com.finops.app.domain.repository.PortfolioRepository
import com.finops.app.domain.repository.SettingsRepository
import com.finops.app.domain.repository.WatchlistRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, clock: Clock): AppDatabase =
        AppDatabase.build(context) { clock.millis() }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun httpClient(impl: UrlConnectionHttpClient): HttpClient

    @Binds
    abstract fun portfolioRepository(impl: OfflineFirstPortfolioRepository): PortfolioRepository

    @Binds
    abstract fun marketRepository(impl: GoogleFinanceMarketRepository): MarketRepository

    @Binds
    abstract fun watchlistRepository(impl: RoomWatchlistRepository): WatchlistRepository

    @Binds
    abstract fun fundRepository(impl: JustEtfFundRepository): FundRepository

    @Binds
    abstract fun isinRepository(impl: RoomIsinRepository): IsinRepository

    @Binds
    abstract fun settingsRepository(impl: PreferencesSettingsRepository): SettingsRepository

    @Binds
    abstract fun backupRepository(impl: RoomBackupRepository): BackupRepository

    @Binds
    abstract fun documentStorage(impl: ContentResolverDocumentStorage): DocumentStorage
}
