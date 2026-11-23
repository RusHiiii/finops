package com.finops.app.ui.feature.settings

import android.Manifest
import androidx.fragment.app.FragmentActivity
import com.finops.app.ui.security.Biometrics
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import com.finops.app.domain.model.CurrencyCode
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.finops.app.ui.common.ConfirmDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.finops.app.domain.model.AccentColor
import com.finops.app.ui.designsystem.accentSwatch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finops.app.R
import com.finops.app.domain.model.ThemeMode
import com.finops.app.ui.common.longLabel
import com.finops.app.ui.designsystem.AppLogo
import com.finops.app.ui.designsystem.Caption
import com.finops.app.ui.designsystem.KeyValueRow
import com.finops.app.ui.designsystem.RangeSelector
import com.finops.app.ui.designsystem.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.onMessageShown() }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.export(it.toString()) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingImport = it.toString() }
    }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setNotifyEarnings(true) else vm.onNotificationPermissionDenied()
    }
    // Activer comme désactiver le verrou exige de s'authentifier (sauf si l'appareil n'a plus de verrouillage).
    val onBiometricLock: (Boolean) -> Unit = { enabled ->
        val activity = context as FragmentActivity
        when {
            Biometrics.isAvailable(activity) -> Biometrics.authenticate(
                activity,
                title = if (enabled) "Activer le verrouillage" else "Désactiver le verrouillage",
                onSuccess = { vm.setBiometricLock(enabled) },
                onError = vm::onBiometricError,
            )
            enabled -> vm.onBiometricUnavailable()
            // Verrouillage d'écran retiré du téléphone : désactivation sans authentification possible.
            else -> vm.setBiometricLock(false)
        }
    }
    val onNotifyEarnings: (Boolean) -> Unit = { enabled ->
        val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setNotifyEarnings(enabled)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = "Apparence") {
                    Caption("Thème", Modifier.padding(bottom = 6.dp))
                    val options = listOf(ThemeMode.SYSTEM to "Système", ThemeMode.LIGHT to "Clair", ThemeMode.DARK to "Sombre")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = settings.theme == mode,
                                onClick = { vm.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text(label) }
                        }
                    }
                    Caption("Couleur", Modifier.padding(top = 16.dp, bottom = 8.dp))
                    AccentPicker(settings.accent, vm::setAccent)
                }
            }
            item {
                SectionCard(title = "Graphiques") {
                    Caption("Période affichée par défaut (patrimoine et portefeuilles)", Modifier.padding(bottom = 6.dp))
                    RangeSelector(settings.defaultRange, vm::setDefaultRange)
                    Caption("Actuellement : ${settings.defaultRange.longLabel.lowercase()}", Modifier.padding(top = 6.dp))
                }
            }
            item {
                SectionCard(title = "Devises") {
                    Text("Devise par défaut", style = MaterialTheme.typography.bodyLarge)
                    Caption("Tous les montants sont affichés dans cette devise, au taux du jour. Les fiches indiquent aussi le montant dans la devise d'origine.")
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CurrencyCode.entries.forEachIndexed { i, c ->
                            SegmentedButton(
                                selected = settings.displayCurrency == c,
                                onClick = { vm.setDisplayCurrency(c) },
                                shape = SegmentedButtonDefaults.itemShape(i, CurrencyCode.entries.size),
                            ) { Text(c.name) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Devises suivies", style = MaterialTheme.typography.bodyLarge)
                    Caption("Seuls les ETF et actions dans ces devises apparaissent dans la recherche et peuvent être ajoutés à un portefeuille.")
                    Spacer(Modifier.height(4.dp))
                    CurrencyCode.entries.forEach { c ->
                        val checked = c in settings.currencies
                        val isLast = checked && settings.currencies.size == 1
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isLast) { vm.setCurrency(c, !checked) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { vm.setCurrency(c, it) }, enabled = !isLast)
                            Text(CURRENCY_NAMES[c] ?: c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Caption(c.name)
                        }
                    }
                    Caption("Au moins une devise doit rester cochée. Les positions déjà détenues restent affichées.")
                }
            }
            item {
                SectionCard(title = "Sécurité") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Activer le verrouillage biométrique", style = MaterialTheme.typography.bodyLarge)
                            Caption("Empreinte, visage ou code du téléphone à l'ouverture de l'app et après 30 s en arrière-plan.")
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = settings.biometricLock, onCheckedChange = onBiometricLock)
                    }
                }
            }
            item {
                SectionCard(title = "Notifications") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Me notifier des résultats", style = MaterialTheme.typography.bodyLarge)
                            Caption("La veille vers 18 h, pour les actions américaines de la watchlist qui publient leurs résultats (le vendredi pour le lundi).")
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = settings.notifyEarnings, onCheckedChange = onNotifyEarnings)
                    }
                }
            }
            item {
                SectionCard(title = "Données") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Actualiser les cours au lancement", style = MaterialTheme.typography.bodyLarge)
                            Caption("Sinon, tirez l'accueil vers le bas pour actualiser.")
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = settings.refreshOnLaunch, onCheckedChange = vm::setRefreshOnLaunch)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Sauvegarde", style = MaterialTheme.typography.bodyLarge)
                    Caption("Portefeuilles, transactions, watchlist et ISIN associés, dans un fichier JSON.")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch(vm.backupFileName()) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Exporter")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Importer")
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Supprimer les données", style = MaterialTheme.typography.bodyLarge)
                    Caption("Efface portefeuilles, transactions, watchlist et ISIN associés. Les paramètres sont conservés.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Supprimer toutes les données")
                    }
                }
            }
            item { AboutCard() }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Supprimer toutes les données ?",
            message = "Portefeuilles, transactions, watchlist et ISIN associés seront définitivement effacés. " +
                "Cette action est irréversible : exportez d'abord vos données si besoin.",
            confirmLabel = "Tout supprimer",
            onDismiss = { confirmDelete = false },
            onConfirm = { vm.deleteAll(); confirmDelete = false },
        )
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Importer la sauvegarde ?",
            message = "Toutes vos données actuelles (portefeuilles, transactions, watchlist) seront remplacées par celles du fichier. " +
                "Pensez à exporter d'abord si besoin.",
            confirmLabel = "Remplacer",
            onDismiss = { pendingImport = null },
            onConfirm = { vm.import(uri); pendingImport = null },
        )
    }
}

private val CURRENCY_NAMES = mapOf(CurrencyCode.EUR to "Euro", CurrencyCode.USD to "Dollar américain")

private val ACCENTS = listOf(
    AccentColor.BLUE to "Bleu", AccentColor.EMERALD to "Émeraude", AccentColor.VIOLET to "Violet", AccentColor.CORAL to "Corail",
)

/** 4 pastilles de couleur ; la sélection est cerclée et cochée. */
@Composable
private fun AccentPicker(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ACCENTS.forEach { (accent, label) ->
            val isSelected = accent == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(accent) }
                    .padding(6.dp)
                    .semantics { contentDescription = "Couleur $label" + if (isSelected) ", sélectionnée" else "" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(accentSwatch(accent)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.height(4.dp))
                Caption(label)
            }
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    SectionCard(title = "À propos") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppLogo(size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Caption(stringResource(R.string.app_tagline))
            }
        }
        Spacer(Modifier.height(12.dp))
        version?.let { KeyValueRow("Version", it) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        KeyValueRow("Cours et historiques", "Google Finance")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        KeyValueRow("Fiches des ETF", "justETF")
        Spacer(Modifier.height(8.dp))
        Caption(
            "Données fournies à titre indicatif, sans garantie d'exactitude ni de mise à jour en temps réel. " +
                "FinOps ne constitue pas un conseil en investissement."
        )
    }
}
