package com.finops.app.domain.usecase

import com.finops.app.domain.model.AppSettings
import com.finops.app.domain.model.DisplayCurrency
import com.finops.app.domain.repository.PortfolioRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.finops.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = repository.observeSettings()
}

/** Devise d'affichage choisie et dernier taux USD/EUR connu. */
class ObserveDisplayCurrencyUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val portfolios: PortfolioRepository,
) {
    operator fun invoke(): Flow<DisplayCurrency> =
        combine(settings.observeSettings().map { it.displayCurrency }.distinctUntilChanged(), portfolios.observeUsdToEur()) { c, fx ->
            DisplayCurrency(c, fx)
        }
}

class UpdateSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(transform: (AppSettings) -> AppSettings) = repository.update(transform)
}
