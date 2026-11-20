package com.finops.app.ui.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.model.TransactionEntry
import com.finops.app.ui.common.ConfirmDialog
import com.finops.app.ui.common.TransactionLine
import com.finops.app.ui.common.formatQuantity
import com.finops.app.ui.common.formatShort
import com.finops.app.ui.common.label
import com.finops.app.ui.designsystem.AssetAvatar
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.LoadingBox
import com.finops.app.ui.designsystem.MessageBox
import com.finops.app.ui.designsystem.SectionCard
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onAddTransaction: () -> Unit,
    onOpenHolding: (portfolioId: Long, assetId: Long) -> Unit,
    vm: TransactionsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<TransactionEntry?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.onMessageShown() }
    }

    Scaffold(

        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Default.Add, contentDescription = "Ajouter une transaction") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.months.isEmpty() && state.filter == null -> MessageBox(
                    title = "Aucune transaction",
                    message = "Vos achats et ventes apparaîtront ici.",
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    actionLabel = "Ajouter une transaction",
                    onAction = onAddTransaction,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.portfolios.size > 1) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item { FilterChip(selected = state.filter == null, onClick = { vm.setFilter(null) }, label = { Text("Tous") }) }
                                items(state.portfolios, key = { it.id }) { p ->
                                    FilterChip(selected = state.filter == p.id, onClick = { vm.setFilter(p.id) }, label = { Text(p.name) })
                                }
                            }
                        }
                    }
                    state.months.forEach { group ->
                        item(key = group.month.toString()) {
                            SectionCard(
                                title = group.month.format(MONTH).replaceFirstChar { it.titlecase(Locale.FRANCE) },
                                contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                            ) {
                                group.entries.forEachIndexed { i, e ->
                                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(
                                        Modifier.clickable { onOpenHolding(e.portfolio.id, e.asset.id) },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AssetAvatar(e.asset.symbol.ticker, size = 36.dp)
                                        Box(Modifier.padding(start = 12.dp).weight(1f)) {
                                            androidx.compose.foundation.layout.Column {
                                                Caption(e.asset.name, Modifier.padding(top = 8.dp))
                                                TransactionLine(e, showPortfolio = state.portfolios.size > 1, onDelete = { toDelete = e })
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
