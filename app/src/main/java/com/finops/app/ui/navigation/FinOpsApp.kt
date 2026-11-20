package com.finops.app.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.finops.app.ui.designsystem.AppBackground
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.finops.app.ui.feature.dashboard.DashboardScreen
import com.finops.app.ui.feature.explore.ExploreScreen
import com.finops.app.ui.feature.holding.HoldingScreen
import com.finops.app.ui.feature.instrument.InstrumentScreen
import com.finops.app.ui.feature.portfolio.PortfolioScreen
import com.finops.app.ui.feature.transaction.AddTransactionScreen
import com.finops.app.ui.feature.settings.SettingsScreen
import com.finops.app.ui.feature.transactions.TransactionsScreen
import com.finops.app.ui.feature.watchlist.WatchlistScreen
import kotlin.reflect.KClass

private enum class TopLevel(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    // Ordre d'affichage : Patrimoine au centre, Paramètres tout à droite.
    WATCHLIST(WatchlistRoute, WatchlistRoute::class, "Watchlist", Icons.Filled.Star, Icons.Outlined.StarBorder),
    EXPLORE(ExploreRoute, ExploreRoute::class, "Explorer", Icons.Filled.Explore, Icons.Outlined.Explore),
    WEALTH(DashboardRoute, DashboardRoute::class, "Patrimoine", Icons.Filled.PieChart, Icons.Outlined.PieChart),
    TRANSACTIONS(TransactionsRoute, TransactionsRoute::class, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    SETTINGS(SettingsRoute, SettingsRoute::class, "Paramètres", Icons.Filled.Settings, Icons.Outlined.Settings),
}

/** Racine de l'UI : barre de navigation (onglets principaux) + graphe de navigation. */
@Composable
fun FinOpsApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val current = TopLevel.entries.firstOrNull { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }

    Scaffold(
        modifier = Modifier.background(AppBackground),
        contentWindowInsets = WindowInsets(0),
        containerColor = Color.Transparent,
        bottomBar = {
            if (current != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f)) {
                    TopLevel.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == current,
                            onClick = { nav.navigateTopLevel(t.route) },
                            icon = { Icon(if (t == current) t.selectedIcon else t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = DashboardRoute, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable<DashboardRoute> {
                DashboardScreen(
                    onOpenPortfolio = { nav.navigate(PortfolioRoute(it)) },
                    onOpenHolding = { nav.navigate(HoldingRoute(assetId = it)) },
                    onAddTransaction = { nav.navigate(AddTransactionRoute()) },
                    onExplore = { nav.navigateTopLevel(ExploreRoute) },
                )
            }
            composable<WatchlistRoute> {
                WatchlistScreen(
                    onOpenInstrument = { nav.navigate(InstrumentRoute(it.ticker, it.exchange)) },
                    onExplore = { nav.navigateTopLevel(ExploreRoute) },
                )
            }
            composable<TransactionsRoute> {
                TransactionsScreen(
                    onAddTransaction = { nav.navigate(AddTransactionRoute()) },
                    onOpenHolding = { pid, aid -> nav.navigate(HoldingRoute(assetId = aid, portfolioId = pid)) },
                )
            }
            composable<ExploreRoute> {
                ExploreScreen(onOpenInstrument = { nav.navigate(InstrumentRoute(it.ticker, it.exchange)) })
            }
            composable<SettingsRoute> { SettingsScreen() }
            composable<PortfolioRoute> {
                PortfolioScreen(
                    onBack = { nav.popBackStack() },
                    onOpenHolding = { pid, aid -> nav.navigate(HoldingRoute(assetId = aid, portfolioId = pid)) },
                    onAddTransaction = { pid -> nav.navigate(AddTransactionRoute(portfolioId = pid)) },
                )
            }
            composable<HoldingRoute> {
                HoldingScreen(
                    onBack = { nav.popBackStack() },
                    onOpenInstrument = { nav.navigate(InstrumentRoute(it.ticker, it.exchange)) },
                    onAddTransaction = { pid, aid -> nav.navigate(AddTransactionRoute(portfolioId = pid ?: RouteArgs.ALL, assetId = aid)) },
                )
            }
            composable<InstrumentRoute> {
                InstrumentScreen(
                    onBack = { nav.popBackStack() },
                    onAddTransaction = { req ->
                        val q = req.snapshot.quote
                        nav.navigate(
                            AddTransactionRoute(
                                portfolioId = req.portfolioId,
                                ticker = req.snapshot.symbol.ticker,
                                exchange = req.snapshot.symbol.exchange,
                                name = q.name,
                                type = q.type?.name,
                                currency = q.currencyCode?.name,
                                price = q.price.toString(),
                            )
                        )
                    },
                    onOpenHolding = { aid -> nav.navigate(HoldingRoute(assetId = aid)) },
                )
            }
            composable<AddTransactionRoute> {
                AddTransactionScreen(onDone = { nav.popBackStack() })
            }
        }
    }
}

/** Navigation entre onglets : une seule instance par onglet, état conservé. */
private fun NavHostController.navigateTopLevel(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
