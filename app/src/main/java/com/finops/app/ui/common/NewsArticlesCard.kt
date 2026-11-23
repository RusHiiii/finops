package com.finops.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finops.app.domain.model.NewsArticle
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.SectionCard

/** Carte listant des articles ; un appui ouvre l'article dans le navigateur. */
@Composable
fun NewsArticlesCard(articles: List<NewsArticle>, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val now = System.currentTimeMillis()
    SectionCard(modifier, contentPadding = PaddingValues(vertical = 4.dp)) {
        articles.forEachIndexed { i, a ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { runCatching { uri.openUri(a.url) } }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(a.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Caption(listOfNotNull(a.source, a.publishedAt?.formatRelative(now)).joinToString(" · "), Modifier.padding(top = 4.dp))
            }
        }
    }
}
