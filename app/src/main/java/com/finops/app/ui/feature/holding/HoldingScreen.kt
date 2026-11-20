package com.finops.app.ui.feature.holding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.model.Symbol
import com.finops.app.domain.model.TransactionEntry
import com.finops.app.domain.model.TransactionSide
import com.finops.app.ui.common.ConfirmDialog
import com.finops.app.ui.common.InstrumentHeroHeader
import com.finops.app.ui.common.PriceChartCard
import com.finops.app.ui.common.TransactionLine
import com.finops.app.ui.common.formatMoney
import com.finops.app.ui.common.formatPercent
import com.finops.app.ui.common.formatQuantity
import com.finops.app.ui.common.formatShort
import com.finops.app.ui.common.formatSignedMoney
import com.finops.app.ui.common.formatSignedPercent
import com.finops.app.ui.common.formatUpdatedAt
import com.finops.app.ui.common.label
import com.finops.app.ui.designsystem.AssetAvatar
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.HeroAmountStyle
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.MessageBox
import com.finops.app.ui.designsystem.SectionAction
import com.finops.app.ui.designsystem.SectionCard
import com.finops.app.ui.designsystem.SignedAmount
import com.finops.app.ui.designsystem.StatGrid
import com.finops.app.ui.designsystem.gainLossColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingScreen(
    onBack: () -> Unit,
    onOpenInstrument: (Symbol) -> Unit,
    onAddTransaction: (portfolioId: Long?, assetId: Long) -> Unit,
    vm: HoldingViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var scrub by remember { mutableStateOf<Int?>(null) }
    var toDelete by remember { mutableStateOf<TransactionEntry?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.onMessageShown() }
    }
    val d = state.detail

    Scaffold(

        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(d?.holding?.asset?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                actions = {
                    d?.let {
                        IconButton(onClick = { onOpenInstrument(it.holding.asset.symbol) }) {
                            Icon(Icons.Outlined.Info, contentDescription = "Fiche de l'instrument")
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
                onClick = { onAddTransaction(vm.portfolioId, vm.assetId) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Transaction") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox()
                d == null -> MessageBox("Position soldée", message = "Plus aucune transaction sur cet actif.")
                else -> {
                    val h = d.holding
                    val a = h.asset
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            InstrumentHeroHeader(
                                ticker = a.symbol.ticker,
                                name = a.name,
                                subtitle = "${a.symbol.display}  ·  ${a.type.label}  ·  ${a.currency}",
                                amount = h.marketValue.formatMoney(a.currency),
                                changePercent = h.unrealizedPnlPercent.takeIf { h.isOpen },
                                changeText = "Plus-value latente " + h.unrealizedPnl.formatSignedMoney(a.currency),
                                footer = listOfNotNull(
                                    "${h.quantity.formatQuantity()} parts · PRU ${h.averagePrice.formatMoney(a.currency)}",
                                    a.lastPriceAt?.let { "Cours mis à jour ${it.formatUpdatedAt()}" },
                                ).joinToString("\n"),
                            )
                        }
                        item {
                            PriceChartCard(
                                chart = state.chart,
                                range = state.range,
                                scrubIndex = scrub,
                                onScrub = { scrub = it },
                                onRangeSelected = vm::selectRange,
                                currency = a.currency.name,
                                baseline = h.averagePrice.takeIf { h.isOpen },
                                baselineLabel = "PRU",
                            )
                        }
                        item {
                            SectionCard(title = "Ma position") {
                                StatGrid(
                                    listOf(
                                        "Quantité" to h.quantity.formatQuantity(),
                                        "Prix de revient (PRU)" to h.averagePrice.formatMoney(a.currency),
                                        "Dernier cours" to a.lastPrice?.formatMoney(a.currency),
                                        "Variation du jour" to h.dayChange?.formatSignedMoney(a.currency),
                                        "Montant investi" to h.costBasis.formatMoney(a.currency),
                                        "Plus-value latente" to h.unrealizedPnl.formatSignedMoney(a.currency) + " (" + h.unrealizedPnlPercent.formatSignedPercent() + ")",
                                        "Plus-value réalisée" to h.realizedPnl.formatSignedMoney(a.currency),
                                        "Poids" to d.weight?.let { (it * 100).formatPercent() },
                                    )
                                )
                            }
                        }
                        item {
                            SectionCard(
                                title = "Transactions",
                                action = { SectionAction("Fiche") { onOpenInstrument(a.symbol) } },
                                contentPadding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            ) {
                                d.transactions.forEachIndexed { i, entry ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    TransactionLine(entry, showPortfolio = vm.portfolioId == null, onDelete = { toDelete = entry })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { entry ->
        ConfirmDialog(
            title = "Supprimer la transaction ?",
            message = "${entry.transaction.side.label} de ${entry.transaction.quantity.formatQuantity()} ${entry.asset.symbol.ticker} du ${entry.transaction.date.formatShort()}.",
            confirmLabel = "Supprimer",
            onDismiss = { toDelete = null },
            onConfirm = { vm.delete(entry.transaction); toDelete = null },
        )
    }
}
