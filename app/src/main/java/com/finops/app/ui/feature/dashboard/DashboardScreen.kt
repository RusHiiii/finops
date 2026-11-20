package com.finops.app.ui.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.finops.app.ui.common.formatPercent
import com.finops.app.ui.common.formatSignedMoney
import com.finops.app.ui.common.formatUpdatedAt
import com.finops.app.ui.designsystem.AssetAvatar
import com.finops.app.ui.designsystem.ChangeBadge
import com.finops.app.ui.designsystem.WeightBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import com.finops.app.common.roundedPercents
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.model.CurrencyCode
import com.finops.app.domain.model.PortfolioOverview
import com.finops.app.ui.common.AllocationCard
import com.finops.app.ui.common.ExposureCard
import com.finops.app.ui.common.IndicatorsCard
import com.finops.app.ui.common.HistoryChartCard
import com.finops.app.ui.common.HoldingRow
import com.finops.app.ui.common.NameDialog
import com.finops.app.ui.common.PositionsCard
import com.finops.app.ui.common.SummaryCard
import com.finops.app.ui.common.ValueHeader
import com.finops.app.ui.common.formatMoney
import com.finops.app.ui.common.formatSignedPercent
import com.finops.app.ui.designsystem.AppWordmark
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.MessageBox
import com.finops.app.ui.designsystem.SectionAction
import com.finops.app.ui.designsystem.SectionCard
import com.finops.app.ui.designsystem.gainLossColor

@Composable
fun DashboardScreen(
    onOpenPortfolio: (Long) -> Unit,
    onOpenHolding: (Long) -> Unit,
    onAddTransaction: () -> Unit,
    onExplore: () -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    DashboardContent(
        state = state,
        onRefresh = vm::refresh,
        onRangeSelected = vm::selectRange,
        onCreatePortfolio = vm::createPortfolio,
        onMessageShown = vm::onMessageShown,
        onOpenPortfolio = onOpenPortfolio,
        onOpenHolding = onOpenHolding,
        onAddTransaction = onAddTransaction,
        onExplore = onExplore,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onRangeSelected: (com.finops.app.domain.model.ChartRange) -> Unit,
    onCreatePortfolio: (String) -> Unit,
    onMessageShown: () -> Unit,
    onOpenPortfolio: (Long) -> Unit,
    onOpenHolding: (Long) -> Unit,
    onAddTransaction: () -> Unit,
    onExplore: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var scrub by remember { mutableStateOf<Int?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(

        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { AppWordmark() },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser les cours")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter une transaction")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val d = state.dashboard
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.loading || d == null -> LoadingBox()
                d.isEmpty -> Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MessageBox(
                        title = "Commencez à suivre votre patrimoine",
                        message = "Créez un portefeuille (PEA, CTO…), ajoutez votre première transaction ou explorez les marchés pour trouver un ETF ou une action.",
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                    )
                    Button(onClick = onAddTransaction, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter une transaction")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Nouveau portefeuille")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Explorer les marchés")
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ValueHeader(
                            label = "Valeur totale",
                            summary = d.total,
                            chart = state.chart,
                            scrubIndex = scrub,
                            range = state.range,
                            updatedAt = d.quotesUpdatedAt?.let { "Cours mis à jour ${it.formatUpdatedAt()}" },
                        )
                    }
                    item {
                        HistoryChartCard(
                            chart = state.chart,
                            range = state.range,
                            scrubIndex = scrub,
                            onScrub = { scrub = it },
                            onRangeSelected = onRangeSelected,
                        )
                    }
                    item { SummaryCard(d.total) }
                    item {
                        PortfoliosCard(d.portfolios, onOpenPortfolio, onCreate = { creating = true })
                    }
                    if (d.allocation.byAsset.isNotEmpty()) item { AllocationCard(d.allocation, d.holdings) }
                    if (d.allocation.byAsset.isNotEmpty()) item { ExposureCard(state.exposure) }
                    if (d.allocation.byAsset.isNotEmpty()) item { IndicatorsCard(state.exposure) }
                    item { PositionsCard(d.holdings, d.usdToEur) { h -> onOpenHolding(h.asset.id) } }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Nouveau portefeuille",
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { onCreatePortfolio(it); creating = false },
        )
    }
}

/**
 * Portefeuilles : valeur, poids dans le patrimoine, variation du jour, plus-value latente,
 * nombre de positions et heure de mise à jour des cours.
 */
@Composable
private fun PortfoliosCard(portfolios: List<PortfolioOverview>, onOpen: (Long) -> Unit, onCreate: () -> Unit) {
    SectionCard(
        title = "Mes portefeuilles",
        action = { SectionAction("Nouveau", onCreate) },
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        val shares = remember(portfolios) { roundedPercents(portfolios.map { it.weight }, decimals = 0) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            portfolios.forEachIndexed { i, p -> PortfolioTile(p, shares[i]) { onOpen(p.portfolio.id) } }
        }
    }
}

@Composable
private fun PortfolioTile(p: PortfolioOverview, sharePercent: Double, onClick: () -> Unit) {
    val s = p.summary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssetAvatar(p.portfolio.name, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.portfolio.name, style = MaterialTheme.typography.titleSmall)
                Caption(
                    "${p.openPositions} position${if (p.openPositions > 1) "s" else ""} · " +
                        sharePercent.formatPercent(0) + " du patrimoine"
                )
            }
            Text(s.totalValueEur.formatMoney(CurrencyCode.EUR), style = MaterialTheme.typography.titleMedium)
        }
        WeightBar(p.weight)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChangeBadge(s.dayChangePercent, prefix = "Jour")
            Text(
                "PV " + s.unrealizedPnlEur.formatSignedMoney(CurrencyCode.EUR) + " (" + s.unrealizedPnlPercent.formatSignedPercent() + ")",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = gainLossColor(s.unrealizedPnlEur),
            )
        }
        p.quotesUpdatedAt?.let { Caption("Cours mis à jour ${it.formatUpdatedAt()}") }
    }
}
