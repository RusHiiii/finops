package com.finops.app.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.domain.model.Symbol
import com.finops.app.ui.common.formatMoney
import com.finops.app.ui.common.formatSignedPercent
import com.finops.app.ui.common.label
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.InstrumentRow
import com.finops.app.ui.designsystem.MessageBox
import com.finops.app.ui.designsystem.gainLossColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onOpenInstrument: (Symbol) -> Unit,
    vm: ExploreViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text("Explorer") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Nom, ticker ou ISIN") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Effacer") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    state.showSuggestions -> {
                        item { Caption("Populaires", Modifier.padding(start = 4.dp, bottom = 4.dp)) }
                        items(state.suggestions, key = { it.symbol.toString() }) { s ->
                            InstrumentRow(
                                ticker = s.symbol.ticker,
                                title = s.name,
                                subtitle = s.symbol.display,
                                onClick = { onOpenInstrument(s.symbol) },
                            )
                        }
                    }
                    state.error != null -> item {
                        MessageBox(title = state.error!!, icon = Icons.Default.SearchOff, message = "Essayez un nom (Amundi MSCI World), un ticker (CW8), un ISIN (FR0010315770) ou PLACE:TICKER (EPA:CW8).")
                    }
                    else -> items(state.results, key = { it.symbol.toString() }) { r ->
                        InstrumentRow(
                            ticker = r.symbol.ticker,
                            title = r.name,
                            subtitle = listOfNotNull(r.symbol.display, r.type?.label).joinToString(" · "),
                            trailingTop = r.price?.formatMoney(r.currency),
                            trailingBottom = r.changePercent?.let { pct ->
                                {
                                    Text(
                                        pct.formatSignedPercent(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = gainLossColor(pct),
                                    )
                                }
                            },
                            trend = r.changePercent,
                            onClick = { onOpenInstrument(r.symbol) },
                        )
                    }
                }
            }
        }
    }
}
