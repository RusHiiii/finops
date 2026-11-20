package com.finops.app.domain.usecase

import com.finops.app.domain.model.Transaction
import com.finops.app.domain.model.TransactionDraft
import com.finops.app.domain.model.ValidationException
import com.finops.app.domain.repository.PortfolioRepository
import com.finops.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(
    private val repository: PortfolioRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** @throws ValidationException si la saisie est invalide ou si une vente dépasse la quantité détenue. */
    suspend operator fun invoke(draft: TransactionDraft): Long {
        val error = when {
            draft.portfolioId <= 0 -> "Choisissez un portefeuille"
            draft.quantity <= 0 -> "La quantité doit être positive"
            draft.price <= 0 -> "Le prix doit être positif"
            draft.fees < 0 -> "Les frais ne peuvent pas être négatifs"
            draft.date.isAfter(LocalDate.now(clock)) -> "La date ne peut pas être dans le futur"
            draft.currency !in settings.observeSettings().first().currencies ->
                "Devise ${draft.currency} non suivie : activez-la dans Paramètres → Devises suivies"
            else -> null
        }
        if (error != null) throw ValidationException(error)
        return repository.addTransaction(
            draft.copy(name = draft.name.trim().ifBlank { draft.symbol.ticker })
        )
    }
}

class DeleteTransactionUseCase @Inject constructor(private val repository: PortfolioRepository) {
    suspend operator fun invoke(transaction: Transaction) = repository.deleteTransaction(transaction)
}

class GetAssetUseCase @Inject constructor(private val repository: PortfolioRepository) {
    suspend operator fun invoke(id: Long): com.finops.app.domain.model.Asset? = repository.getAsset(id)
}
