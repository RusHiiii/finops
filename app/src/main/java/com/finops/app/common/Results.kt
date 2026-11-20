package com.finops.app.common

import kotlinx.coroutines.CancellationException

/** Comme [runCatching], mais laisse passer l'annulation de la coroutine. */
inline fun <T> runSuspendCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
