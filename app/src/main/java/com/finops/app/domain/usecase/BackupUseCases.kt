package com.finops.app.domain.usecase

import com.finops.app.domain.model.BackupSummary
import com.finops.app.domain.repository.BackupRepository
import com.finops.app.domain.repository.DocumentStorage
import javax.inject.Inject

class ExportDataUseCase @Inject constructor(
    private val backup: BackupRepository,
    private val storage: DocumentStorage,
) {
    suspend operator fun invoke(uri: String) = storage.write(uri, backup.export())
}

/** Remplace toutes les données par le contenu du fichier. */
class ImportDataUseCase @Inject constructor(
    private val backup: BackupRepository,
    private val storage: DocumentStorage,
) {
    suspend operator fun invoke(uri: String): BackupSummary = backup.restore(storage.read(uri))
}

/** Efface portefeuilles, transactions, watchlist et ISIN associés ; les préférences sont conservées. */
class DeleteAllDataUseCase @Inject constructor(private val backup: BackupRepository) {
    suspend operator fun invoke() = backup.clearAll()
}
