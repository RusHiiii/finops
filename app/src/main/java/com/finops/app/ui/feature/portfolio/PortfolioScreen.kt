package com.finops.app.ui.feature.portfolio

import com.finops.app.ui.common.signedMoney
import com.finops.app.ui.common.stockLogoUrl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.ui.common.AllocationCard
import com.finops.app.ui.common.ExposureCard
import com.finops.app.ui.common.IndicatorsCard
import com.finops.app.ui.common.ConfirmDialog
import com.finops.app.ui.common.HistoryChartCard
import com.finops.app.ui.common.HoldingRow
import com.finops.app.ui.common.NameDialog
import com.finops.app.ui.common.PositionsCard
import com.finops.app.ui.common.SummaryCard
import com.finops.app.ui.common.ValueHeader
import com.finops.app.ui.common.formatUpdatedAt
import com.finops.app.ui.designsystem.InstrumentRow
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.MessageBox
import com.finops.app.ui.designsystem.SectionCard
import com.finops.app.ui.designsystem.gainLossColor

private enum class PortfolioDialog { RENAME, DELETE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onBack: () -> Unit,
    onOpenHolding: (portfolioId: Long, assetId: Long) -> Unit,
    onAddTransaction: (portfolioId: Long) -> Unit,
    vm: PortfolioViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var scrub by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<PortfolioDialog?>(null) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.onMessageShown() }
    }
    val d = state.detail

    Scaffold(

        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(d?.portfolio?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Renommer") }, onClick = { menu = false; dialog = PortfolioDialog.RENAME })
                            DropdownMenuItem(text = { Text("Supprimer") }, onClick = { menu = false; dialog = PortfolioDialog.DELETE })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { onAddTransaction(vm.portfolioId) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Transaction") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox()
                d == null -> MessageBox("Portefeuille introuvable")
                d.holdings.isEmpty() && d.closedHoldings.isEmpty() -> MessageBox(
                    title = "Portefeuille vide",
                    message = "Ajoutez une première transaction pour suivre sa performance.",
                    icon = Icons.Default.Inventory2,
                    actionLabel = "Ajouter une transaction",
                    onAction = { onAddTransaction(vm.portfolioId) },
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ValueHeader("Valeur du portefeuille", d.summary, state.chart, scrub, state.range, updatedAt = d.quotesUpdatedAt?.let { "Cours mis à jour ${it.formatUpdatedAt()}" })
                    }
                    item {
                        HistoryChartCard(state.chart, state.range, scrub, { scrub = it }, vm::selectRange)
                    }
                    item { SummaryCard(d.summary) }
                    if (d.holdings.isNotEmpty()) {
                        item { PositionsCard(d.holdings, d.usdToEur) { h -> onOpenHolding(vm.portfolioId, h.asset.id) } }
                        if (d.allocation.byAsset.isNotEmpty()) item { AllocationCard(d.allocation, d.holdings) }
                        if (d.allocation.byAsset.isNotEmpty()) item { ExposureCard(state.exposure) }
                        if (d.allocation.byAsset.isNotEmpty()) item { IndicatorsCard(state.exposure) }
                    }
                    if (d.closedHoldings.isNotEmpty()) {
                        item {
                            SectionCard(title = "Positions soldées", contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                                d.closedHoldings.forEach { h ->
                                    InstrumentRow(
                                        ticker = h.asset.symbol.ticker,
                                        title = h.asset.name,
                                        subtitle = "Plus-value réalisée",
                                        trailingBottom = {
                                            Text(
                                                h.realizedPnl.signedMoney(h.asset.currency),
                                                color = gainLossColor(h.realizedPnl),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        trend = h.realizedPnl,
                                        logoUrl = stockLogoUrl(h.asset.symbol, h.asset.type),
                                        onClick = { onOpenHolding(vm.portfolioId, h.asset.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        null -> Unit
        PortfolioDialog.RENAME -> NameDialog(
            title = "Renommer",
            initial = d?.portfolio?.name.orEmpty(),
            onDismiss = { dialog = null },
            onConfirm = { vm.rename(it); dialog = null },
        )
        PortfolioDialog.DELETE -> ConfirmDialog(
            title = "Supprimer « ${d?.portfolio?.name.orEmpty()} » ?",
            message = "Toutes ses transactions seront supprimées définitivement.",
            confirmLabel = "Supprimer",
            onDismiss = { dialog = null },
            onConfirm = { vm.delete(); dialog = null },
        )
    }
}

