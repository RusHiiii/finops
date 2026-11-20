package com.finops.app.ui.feature.watchlist

import androidx.compose.foundation.layout.Column
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
import com.finops.app.ui.common.formatMoney
import com.finops.app.ui.common.formatTime
import com.finops.app.ui.common.label
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
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
                    item {
                        SectionCard(contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                            state.items.forEachIndexed { i, w ->
                                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    InstrumentRow(
                                        ticker = w.symbol.ticker,
                                        title = w.name,
                                        subtitle = listOfNotNull(w.symbol.display, w.type?.label, w.lastPriceAt?.formatTime(seconds = true)).joinToString(" · "),
                                        trailingTop = w.lastPrice?.formatMoney(w.currency),
                                        trailingBottom = w.dayChangePercent?.let { pct -> { ChangeBadge(pct) } },
                                        trend = w.dayChangePercent,
                                        onClick = { onOpenInstrument(w.symbol) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { vm.remove(w.symbol) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Ne plus suivre", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
