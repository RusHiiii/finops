package com.finops.app.domain.model

import java.time.LocalDate

/** Article d'actualité ; [url] ouvre l'article d'origine. */
data class NewsArticle(
    val title: String,
    val source: String?,
    val url: String,
    val publishedAt: Long?,
)

enum class EarningsTiming { BEFORE_OPEN, AFTER_CLOSE, UNKNOWN }

/** Publication de résultats trimestriels d'une société américaine (montants en USD). */
data class EarningsEvent(
    val ticker: String,
    val companyName: String,
    val date: LocalDate,
    val timing: EarningsTiming,
    val fiscalQuarter: String?,
    val epsForecast: Double?,
    /** Publié : BPA réel et écart au consensus (%). */
    val eps: Double?,
    val surprisePercent: Double?,
)

/** Résultat d'une société suivie, rattaché à son symbole Google Finance. */
data class WatchedEarnings(val symbol: Symbol, val name: String, val event: EarningsEvent)
