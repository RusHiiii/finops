package com.finops.app.domain.usecase

import com.finops.app.domain.model.AppSettings
import com.finops.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = repository.observeSettings()
}

class UpdateSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend operator fun invoke(transform: (AppSettings) -> AppSettings) = repository.update(transform)
}
