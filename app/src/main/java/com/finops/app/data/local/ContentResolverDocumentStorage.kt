package com.finops.app.data.local

import android.content.Context
import android.net.Uri
import com.finops.app.common.IoDispatcher
import com.finops.app.domain.model.ValidationException
import com.finops.app.domain.repository.DocumentStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Documents choisis via le sélecteur de fichiers Android (Storage Access Framework). */
class ContentResolverDocumentStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : DocumentStorage {

    override suspend fun write(uri: String, content: String) = withContext(io) {
        val stream = context.contentResolver.openOutputStream(Uri.parse(uri), "wt")
            ?: throw ValidationException("Impossible d'écrire le fichier")
        stream.bufferedWriter().use { it.write(content) }
    }

    override suspend fun read(uri: String): String = withContext(io) {
        val stream = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw ValidationException("Impossible de lire le fichier")
        stream.bufferedReader().use { it.readText() }
    }
}
