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
        val error = validate(draft.portfolioId, draft.quantity, draft.price, draft.fees, draft.date, LocalDate.now(clock)) ?: when {
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

/** Erreur de saisie commune à l'ajout et à la modification, ou null. */
private fun validate(portfolioId: Long, quantity: Double, price: Double, fees: Double, date: LocalDate, today: LocalDate): String? = when {
    portfolioId <= 0 -> "Choisissez un portefeuille"
    quantity <= 0 -> "La quantité doit être positive"
    price <= 0 -> "Le prix doit être positif"
    fees < 0 -> "Les frais ne peuvent pas être négatifs"
    date.isAfter(today) -> "La date ne peut pas être dans le futur"
    else -> null
}

class GetTransactionUseCase @Inject constructor(private val repository: PortfolioRepository) {
    suspend operator fun invoke(id: Long): Transaction? = repository.getTransaction(id)
}

class UpdateTransactionUseCase @Inject constructor(
    private val repository: PortfolioRepository,
    private val clock: Clock,
) {
    /** @throws ValidationException si la saisie est invalide ou rend des ventes incohérentes. */
    suspend operator fun invoke(transaction: Transaction) {
        val t = transaction
        validate(t.portfolioId, t.quantity, t.price, t.fees, t.date, LocalDate.now(clock))?.let { throw ValidationException(it) }
        repository.updateTransaction(t)
    }
}

class DeleteTransactionUseCase @Inject constructor(private val repository: PortfolioRepository) {
    suspend operator fun invoke(transaction: Transaction) = repository.deleteTransaction(transaction)
}

class GetAssetUseCase @Inject constructor(private val repository: PortfolioRepository) {
    suspend operator fun invoke(id: Long): com.finops.app.domain.model.Asset? = repository.getAsset(id)
}
