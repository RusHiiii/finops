package com.finops.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finops.app.domain.model.TransactionEntry
import com.finops.app.domain.model.TransactionSide
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.gainLossColor

/** Ligne de transaction : sens, quantité × prix, date, portefeuille optionnel, montant signé, menu modifier / supprimer. */
@Composable
fun TransactionLine(entry: TransactionEntry, showPortfolio: Boolean, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val t = entry.transaction
    val ccy = entry.asset.currency
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${t.side.label} · ${t.quantity.formatQuantity()} × ${t.price.money(ccy)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Caption(
                listOfNotNull(
                    t.date.formatShort(),
                    entry.portfolio.name.takeIf { showPortfolio },
                    t.fees.takeIf { it > 0 }?.let { "frais ${it.money(ccy)}" },
                ).joinToString(" · ")
            )
        }
        Text(
            (if (t.side == TransactionSide.BUY) -t.netAmount else t.netAmount).signedMoney(ccy),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (t.side == TransactionSide.SELL) gainLossColor(1.0) else MaterialTheme.colorScheme.onSurface,
        )
        if (onEdit != null || onDelete != null) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    onEdit?.let {
                        DropdownMenuItem(
                            text = { Text("Modifier") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { menu = false; it() },
                        )
                    }
                    onDelete?.let {
                        DropdownMenuItem(
                            text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; it() },
                        )
                    }
                }
            }
        }
    }
}

