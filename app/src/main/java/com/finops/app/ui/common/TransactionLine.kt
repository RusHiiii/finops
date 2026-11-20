package com.finops.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finops.app.domain.model.TransactionEntry
import com.finops.app.domain.model.TransactionSide
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.gainLossColor

/** Ligne de transaction : sens, quantité × prix, date, portefeuille optionnel, montant signé. */
@Composable
fun TransactionLine(entry: TransactionEntry, showPortfolio: Boolean, onDelete: (() -> Unit)?) {
    val t = entry.transaction
    val ccy = entry.asset.currency
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "${t.side.label} · ${t.quantity.formatQuantity()} × ${t.price.formatMoney(ccy)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Caption(
                listOfNotNull(
                    t.date.formatShort(),
                    entry.portfolio.name.takeIf { showPortfolio },
                    t.fees.takeIf { it > 0 }?.let { "frais ${it.formatMoney(ccy)}" },
                ).joinToString(" · ")
            )
        }
        Text(
            (if (t.side == TransactionSide.BUY) -t.netAmount else t.netAmount).formatSignedMoney(ccy),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (t.side == TransactionSide.SELL) gainLossColor(1.0) else MaterialTheme.colorScheme.onSurface,
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

