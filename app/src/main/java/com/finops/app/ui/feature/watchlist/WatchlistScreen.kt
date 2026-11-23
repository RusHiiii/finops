package com.finops.app.ui.feature.watchlist

import com.finops.app.ui.common.money
import com.finops.app.ui.common.stockLogoUrl
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.WatchlistItem
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.model.Symbol
import com.finops.app.ui.common.formatTime
import com.finops.app.ui.common.pluralLabel
import com.finops.app.ui.designsystem.ChangeBadge
import com.finops.app.ui.designsystem.InstrumentRow
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.MessageBox
import com.finops.app.ui.designsystem.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onOpenInstrument: (Symbol) -> Unit,
    onExplore: () -> Unit,
    vm: WatchlistViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.onMessageShown() }
    }

    Scaffold(

        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text("Watchlist") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading -> LoadingBox()
                state.items.isEmpty() -> Column(Modifier.fillMaxSize()) {
                    MessageBox(
                        title = "Aucun instrument suivi",
                        message = "Depuis la fiche d'un ETF ou d'une action, touchez l'étoile pour le suivre ici.",
                        icon = Icons.Outlined.StarBorder,
                        actionLabel = "Explorer les marchés",
                        onAction = onExplore,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Une carte par type (ETF puis actions) ; type inconnu en dernier.
                    val groups = state.items.groupBy { it.type }
                    (AssetType.entries + listOf(null)).forEach { type ->
                        val items = groups[type].orEmpty()
                        if (items.isEmpty()) return@forEach
                        item(key = type?.name ?: "OTHER") {
                            WatchlistSection(
                                title = type?.pluralLabel ?: "Autres",
                                icon = when (type) {
                                    AssetType.ETF -> Icons.Outlined.PieChart
                                    AssetType.STOCK -> Icons.Outlined.Business
                                    null -> Icons.AutoMirrored.Outlined.HelpOutline
                                },
                                items = items,
                                onOpen = onOpenInstrument,
                                onRemove = vm::remove,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSection(
    title: String,
    icon: ImageVector,
    items: List<WatchlistItem>,
    onOpen: (Symbol) -> Unit,
    onRemove: (Symbol) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                items.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        SectionCard(contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            items.forEachIndexed { i, w ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InstrumentRow(
                        ticker = w.symbol.ticker,
                        title = w.name,
                        subtitle = listOfNotNull(w.symbol.display, w.lastPriceAt?.formatTime(seconds = true)).joinToString(" · "),
                        trailingTop = w.lastPrice?.money(w.currency),
                        trailingBottom = w.dayChangePercent?.let { pct -> { ChangeBadge(pct) } },
                        trend = w.dayChangePercent,
                        onClick = { onOpen(w.symbol) },
                        logoUrl = stockLogoUrl(w.symbol, w.type),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(w.symbol) }) {
                        Icon(Icons.Filled.Star, contentDescription = "Ne plus suivre", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
