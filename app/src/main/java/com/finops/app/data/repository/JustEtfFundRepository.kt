package com.finops.app.data.repository

import com.finops.app.data.remote.JustEtfApi
import com.finops.app.data.remote.JustEtfParser
import com.finops.app.domain.model.FundProfile
import com.finops.app.domain.model.Isin
import com.finops.app.domain.repository.FundRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Fiches justETF, mises en cache en mémoire (elles changent peu). */
@Singleton
class JustEtfFundRepository @Inject constructor(
    private val api: JustEtfApi,
    private val clock: Clock,
) : FundRepository {
    private class Cached(val at: Long, val profile: FundProfile?)

    private val cache = HashMap<Isin, Cached>()
    private val lock = Mutex()

    override suspend fun profile(isin: Isin): FundProfile? {
        val now = clock.millis()
        lock.withLock { cache[isin] }?.takeIf { now - it.at < TTL }?.let { return it.profile }
        val profile = api.profilePage(isin)?.let { JustEtfParser.parse(it, isin) }
        lock.withLock { cache[isin] = Cached(now, profile) }
        return profile
    }

    private companion object {
        const val TTL = 6 * 60 * 60_000L
    }
}
