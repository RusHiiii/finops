package com.finops.app.ui.feature.news

import androidx.compose.foundation.clickable
import com.finops.app.domain.model.AssetType
import com.finops.app.ui.common.stockLogoUrl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.model.EarningsTiming
import com.finops.app.domain.model.NewsArticle
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.WatchedEarnings
import com.finops.app.ui.common.NewsArticlesCard
import com.finops.app.ui.common.formatDayHeader
import com.finops.app.ui.common.formatMoney
import com.finops.app.ui.common.formatRelative
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.ChangeBadge
import com.finops.app.ui.designsystem.InstrumentRow
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.MessageBox
import com.finops.app.ui.designsystem.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onOpenInstrument: (Symbol) -> Unit,
    onExplore: () -> Unit,
    vm: NewsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text("Actu") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Actualités") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Résultats") })
            }
            if (tab == 0) {
                Refreshable(state.news.refreshing && state.news.items != null, vm::refreshNews) {
                    NewsTab(state.news)
                }
            } else {
                Refreshable(state.earnings.refreshing && state.earnings.items != null, vm::refreshEarnings) {
                    EarningsTab(state.earnings, state.hasUsStocks, onOpenInstrument, onExplore)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Refreshable(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { content() }
}

private val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)

@Composable
private fun NewsTab(news: Loadable<List<NewsArticle>>) {
    val items = news.items
    when {
        items == null && news.error != null -> ScrollableMessage { MessageBox(title = "Actualités indisponibles", message = news.error) }
        items == null -> LoadingBox()
        items.isEmpty() -> ScrollableMessage { MessageBox(title = "Aucune actualité", icon = Icons.Outlined.Newspaper) }
        else -> {
            LazyColumn(contentPadding = ListPadding) {
                item { NewsArticlesCard(items) }
                item { Caption("Sources : Boursorama, Fortuneo", Modifier.padding(top = 12.dp, start = 4.dp)) }
            }
        }
    }
}

@Composable
private fun EarningsTab(
    earnings: Loadable<List<WatchedEarnings>>,
    hasUsStocks: Boolean,
    onOpenInstrument: (Symbol) -> Unit,
    onExplore: () -> Unit,
) {
    val items = earnings.items
    when {
        items == null && earnings.error != null -> ScrollableMessage { MessageBox(title = "Calendrier indisponible", message = earnings.error) }
        items == null -> LoadingBox()
        !hasUsStocks -> ScrollableMessage {
            MessageBox(
                title = "Aucune action américaine suivie",
                message = "Le calendrier affiche les publications de résultats des actions américaines de votre watchlist.",
                icon = Icons.Outlined.StarBorder,
                actionLabel = "Explorer les marchés",
                onAction = onExplore,
            )
        }
        items.isEmpty() -> ScrollableMessage {
            MessageBox(
                title = "Aucune publication prévue",
                message = "Aucune de vos actions américaines ne publie ses résultats dans les 5 prochaines semaines.",
                icon = Icons.Outlined.CalendarMonth,
            )
        }
        else -> {
            val today = LocalDate.now()
            LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.groupBy { it.event.date }.forEach { (date, events) ->
                    item(key = date.toString()) {
                        SectionCard(
                            title = date.formatDayHeader() + when (date) {
                                today -> " · aujourd'hui"
                                today.plusDays(1) -> " · demain"
                                else -> ""
                            },
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                        ) {
                            events.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                EarningsRow(e, onClick = { onOpenInstrument(e.symbol) })
                            }
                        }
                    }
                }
                item { Caption("Sociétés américaines de votre watchlist · BPA en USD · Source : Nasdaq", Modifier.padding(start = 4.dp)) }
            }
        }
    }
}

@Composable
private fun EarningsRow(item: WatchedEarnings, onClick: () -> Unit) {
    val e = item.event
    val timing = when (e.timing) {
        EarningsTiming.BEFORE_OPEN -> "Avant ouverture"
        EarningsTiming.AFTER_CLOSE -> "Après clôture"
        EarningsTiming.UNKNOWN -> null
    }
    val published = e.eps != null
    InstrumentRow(
        ticker = item.symbol.ticker,
        title = item.name,
        subtitle = listOfNotNull(item.symbol.display, timing).joinToString(" · "),
        trailingTop = if (published) "BPA ${e.eps.formatMoney("USD")}" else e.epsForecast?.let { "Att. ${it.formatMoney("USD")}" },
        trailingBottom = when {
            e.surprisePercent != null -> { { ChangeBadge(e.surprisePercent) } }
            published && e.epsForecast != null -> { { Caption("Att. ${e.epsForecast.formatMoney("USD")}") } }
            e.fiscalQuarter != null -> { { Caption("Trim. ${e.fiscalQuarter}") } }
            else -> null
        },
        trend = e.surprisePercent,
        onClick = onClick,
        logoUrl = stockLogoUrl(item.symbol, AssetType.STOCK),
    )
}

/** Message dans une liste défilante, pour garder le tirer-pour-actualiser. */
@Composable
private fun ScrollableMessage(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 48.dp)) { item { content() } }
}
