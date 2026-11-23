package com.finops.app.ui.feature.transaction

import androidx.compose.foundation.layout.Arrangement
import com.finops.app.domain.model.Symbol
import com.finops.app.ui.common.stockLogoUrl
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.model.AssetType
import com.finops.app.domain.model.Portfolio
import com.finops.app.domain.model.TransactionSide
import com.finops.app.ui.common.formatLong
import com.finops.app.ui.common.formatMoney
import com.finops.app.ui.common.label
import com.finops.app.ui.common.toUtcMillis
import com.finops.app.ui.common.utcMillisToLocalDate
import com.finops.app.ui.designsystem.AssetAvatar
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.PillSelector
import com.finops.app.ui.designsystem.SectionCard
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onDone: () -> Unit,
    vm: AddTransactionViewModel = hiltViewModel(),
) {
    val f by vm.form.collectAsStateWithLifecycle()
    val portfolios by vm.portfolios.collectAsStateWithLifecycle()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(f.saved) { if (f.saved) onDone() }

    Scaffold(

        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (f.editing) "Modifier la transaction" else "Nouvelle transaction") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Fermer") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedChoice(
                options = TransactionSide.entries,
                selected = f.side,
                label = { it.label },
                onSelect = { s -> vm.edit { copy(side = s) } },
                activeColors = { side -> side.pastel },
            )

            if (portfolios.size > 1) {
                PortfolioPicker(portfolios, f.portfolioId) { id -> vm.edit { copy(portfolioId = id) } }
            }

            SectionCard(title = "Instrument") {
                if (f.assetLocked) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssetAvatar(f.ticker, logoUrl = stockLogoUrl(Symbol(f.ticker, f.exchange), f.type))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(f.name.ifBlank { f.ticker }, style = MaterialTheme.typography.bodyLarge)
                            Caption("${f.exchange}:${f.ticker} · ${f.type.label} · ${f.currency}")
                        }
                    }
                } else {
                    AssetFields(f, vm)
                }
            }

            SectionCard(title = "Détails") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(f.quantity, { v -> vm.edit { copy(quantity = v) } }, "Quantité", Modifier.weight(1f))
                        DecimalField(f.price, { v -> vm.edit { copy(price = v) } }, "Prix unitaire (${f.currency})", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DecimalField(f.fees, { v -> vm.edit { copy(fees = v) } }, "Frais (${f.currency})", Modifier.weight(1f), if (f.needsFx) ImeAction.Next else ImeAction.Done)
                        OutlinedTextField(
                            value = f.date.formatLong(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Date") },
                            trailingIcon = {
                                IconButton(onClick = { pickDate = true }) { Icon(Icons.Default.CalendarMonth, contentDescription = "Choisir la date") }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (f.needsFx) {
                        DecimalField(
                            f.fxRate,
                            { v -> vm.edit { copy(fxRate = v, fxFor = currency) } },
                            "Taux ${f.currency} → ${f.displayCurrency}",
                            Modifier.fillMaxWidth(),
                            ImeAction.Done,
                        )
                        Caption("1 ${f.currency} = ${f.fxRate.ifBlank { "?" }} ${f.displayCurrency} à la date de la transaction (pré-rempli au taux actuel)")
                    }
                }
            }

            f.total?.let { total ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (f.side == TransactionSide.BUY) "Montant décaissé" else "Montant encaissé",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(total.formatMoney(f.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            f.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Button(
                onClick = vm::save,
                enabled = !f.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (f.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (f.editing) "Enregistrer les modifications" else "Enregistrer ${f.side.label.lowercase()}")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = f.date.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !utcTimeMillis.utcMillisToLocalDate().isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> vm.edit { copy(date = ms.utcMillisToLocalDate()) } }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun AssetFields(f: TransactionForm, vm: AddTransactionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = f.ticker,
                onValueChange = { v -> vm.edit { copy(ticker = v.uppercase().trim()) } },
                label = { Text("Ticker") },
                placeholder = { Text("CW8") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = f.exchange,
                onValueChange = { v -> vm.edit { copy(exchange = v.uppercase().trim()) } },
                label = { Text("Place") },
                placeholder = { Text("EPA") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = vm::lookup, enabled = !f.lookingUp) {
                if (f.lookingUp) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Search, contentDescription = "Compléter depuis Google Finance")
            }
        }
        Caption("Places Google Finance : EPA (Paris), AMS, ETR, NASDAQ, NYSE, NYSEARCA…")
        OutlinedTextField(
            value = f.name,
            onValueChange = { v -> vm.edit { copy(name = v) } },
            label = { Text("Nom (facultatif)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SegmentedChoice(AssetType.entries, f.type, { it.label }) { t -> vm.edit { copy(type = t) } }
        val currencies by vm.currencies.collectAsStateWithLifecycle()
        if (currencies.size > 1) SegmentedChoice(currencies, f.currency, { it.name }) { c -> vm.edit { copy(currency = c) } }
        else Caption("Devise : ${f.currency} (seule devise suivie)")
    }
}

@Composable
private fun PortfolioPicker(portfolios: List<Portfolio>, selected: Long, onSelect: (Long) -> Unit) {
    Column {
        Caption("Portefeuille", Modifier.padding(bottom = 6.dp))
        PillSelector(portfolios.map { it.id }, selected, { id -> portfolios.first { it.id == id }.name }, onSelect)
    }
}

/** Achat vert pastel, vente rouge pastel (fond, texte) ; teintes sourdes en thème sombre. */
private val TransactionSide.pastel: Pair<Color, Color>
    @Composable get() {
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return when (this) {
            TransactionSide.BUY -> if (dark) Color(0xFF1F4D3A) to Color(0xFFB7F0D2) else Color(0xFFCDEFDC) to Color(0xFF0B5D3F)
            TransactionSide.SELL -> if (dark) Color(0xFF5A2A32) to Color(0xFFFFCCD3) else Color(0xFFFAD4D9) to Color(0xFF8E1B2C)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    /** Couleurs (fond, texte) de l'option sélectionnée ; null = couleurs par défaut. */
    activeColors: (@Composable (T) -> Pair<Color, Color>)? = null,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            val colors = activeColors?.invoke(o)?.let { (bg, fg) ->
                SegmentedButtonDefaults.colors(activeContainerColor = bg, activeContentColor = fg)
            } ?: SegmentedButtonDefaults.colors()
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = colors,
            ) { Text(label(o)) }
        }
    }
}

@Composable
private fun DecimalField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}
