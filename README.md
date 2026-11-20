# FinOps

> *La finance pour les nuls, par des nuls.*

Application Android de suivi de portefeuilles boursiers (ETF et actions) : valorisation en temps réel, historique, répartition, exposition par transparence, indicateurs de diversification et fiches détaillées des instruments.

Données de marché : **Google Finance** (cours, historiques, recherche) et **justETF** (fiches des ETF). Aucun compte ni serveur : tout est stocké sur le téléphone.

> ⚠️ Données fournies à titre indicatif, sans garantie d'exactitude. FinOps ne constitue pas un conseil en investissement.

---

## Fonctionnalités

### Patrimoine (accueil)
- Valeur totale, variation du jour, plus-value latente, heure de mise à jour des cours (HH:mm:ss).
- Courbe d'historique reconstituée (valeur et montant investi) sur 1J, 5J, 1M, 6M, YTD, 1A, 5A, MAX, avec lecture au doigt.
- Portefeuilles : valeur, part du patrimoine, variation du jour, plus-value, nombre de positions.
- **Répartition** par ligne, type (ETF / actions) et devise.
- **Exposition** par secteur et par pays, calculée par transparence (composition des ETF).
- **Indicateurs** : diversité géographique et sectorielle, notées de 1 à 10 (vert / orange / rouge).
- Positions groupées ETF / Actions, avec indicateur vert / rouge de performance.

### Portefeuilles et transactions
- Plusieurs portefeuilles (PEA, CTO…), création, renommage, suppression.
- Achats / ventes avec frais ; prix de revient unitaire (PRU) par la méthode du coût moyen pondéré ; plus-values latente et réalisée.
- Contrôle des ventes (impossible de vendre plus que la quantité détenue à la date).
- Historique des transactions groupé par mois, filtrable par portefeuille.
- Devises gérées : EUR et USD (conversion au taux USD/EUR courant).

### Fiches ETF / actions
- Cours, variation, graphique sur 8 périodes, tableau des performances.
- Labels d'identification (max. 3) et description courte.
- **Informations complémentaires** (ETF, via justETF) : taille du fonds, frais courants (TER), réplication, durabilité, couverture de change, date de création, distribution, domiciliation, émetteur, éligibilité PEA.
- **Composition** (ETF) : 10 principales positions, principaux secteurs et pays.
- **Aperçu des risques** : indicateur 1 à 7, volatilité et perte maximale sur 1, 3 et 5 ans (justETF, ou calculés sur l'historique Google pour les actions).

### Explorer et watchlist
- Recherche par **nom, ticker ou ISIN** (ex. `Amundi MSCI World`, `CW8`, `FR0010315770`, `EPA:CW8`).
- Watchlist des instruments suivis (étoile sur la fiche).

### Paramètres
- Thème clair / sombre / système et 4 couleurs d'accent (Bleu, Émeraude, Violet, Corail).
- Période par défaut des graphiques, actualisation des cours au lancement.
- **Export / import** des données (JSON).

---

## Installation

### APK de test
1. Générer l'APK (voir *Build*) ou récupérer `app/build/outputs/apk/debug/app-debug.apk`.
2. L'installer : `adb install -r app-debug.apk`, ou copier le fichier sur le téléphone et l'ouvrir (autoriser les sources inconnues).

### Données de démonstration
Le fichier [`samples/finops-demo.json`](samples/finops-demo.json) contient deux portefeuilles fictifs (PEA, CTO), 12 transactions et une watchlist sur de vrais instruments.
Import : **Paramètres → Données → Importer**. ⚠️ L'import remplace toutes les données existantes.

---

## Build

Prérequis :
- **Android Studio** récent (compatible AGP 9.5), JDK 21 (le JBR d'Android Studio convient).
- **Android SDK** plateforme 36.

```bash
./gradlew assembleDebug        # APK de debug
./gradlew compileDebugKotlin   # compilation seule
```

| Élément | Version |
|---|---|
| Gradle | 9.9.2 |
| Android Gradle Plugin | 9.5.1 (Kotlin intégré) |
| Kotlin | 2.4.21 |
| KSP | 2.3.14 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Jetpack Compose (BOM) | 2024.12.01 |
| Room | 2.8.5 |
| Hilt | 2.60.1 |

> Si Android Studio signale *« Incompatible Gradle JVM version »*, choisir le JDK 21 dans **Settings → Build Tools → Gradle → Gradle JDK**.

---

## Architecture

Architecture recommandée par Google (*Guide to app architecture*), en trois couches, avec flux de données unidirectionnel et injection de dépendances **Hilt**.

```
com.finops.app
├── common/            Utilitaires transverses (dispatchers, arrondis de %, Result)
├── di/                Modules Hilt (liaisons interfaces → implémentations)
├── domain/            Kotlin pur, sans Android
│   ├── model/         Modèles métier (Asset, Holding, Transaction, FundProfile…)
│   ├── calculator/    Calculs (PRU, historique, performance, risque, diversification…)
│   ├── repository/    Interfaces des dépôts (contrats)
│   └── usecase/       Cas d'usage (un par action métier)
├── data/
│   ├── local/         Room (entités, DAO, migrations), stockage de documents
│   ├── remote/        Clients Google Finance / justETF et décodeurs
│   └── repository/    Implémentations des dépôts (offline-first, caches)
└── ui/
    ├── designsystem/  Thème, composants, graphiques, logo
    ├── common/        Composants partagés entre écrans, formatage
    ├── navigation/    Routes typées et barre de navigation
    └── feature/       Un dossier par écran : Screen (Compose) + ViewModel
```

Principes :
- **Source de vérité locale** : les données utilisateur vivent dans Room ; l'UI observe des `Flow` et se met à jour seule.
- Les **ViewModels** n'accèdent qu'aux cas d'usage et exposent un `StateFlow<UiState>` immuable.
- Le **domaine** ne dépend ni d'Android ni de la couche données.

### Base de données
Room, version 4. Toute évolution de schéma passe par une migration (`data/local/Migrations.kt`) et les schémas exportés dans `app/schemas/` sont versionnés.

| Version | Changement |
|---|---|
| 2 | Clôture précédente des actifs (variation du jour) |
| 3 | Watchlist |
| 4 | Correspondance symbole → ISIN |

---

## Sources de données et limites

Aucune des deux sources ne propose d'API officielle : l'app décode leurs pages et appels internes. **Si un site change de format, la fonctionnalité concernée peut cesser de fonctionner** (le reste de l'app continue de marcher).

| Source | Usage |
|---|---|
| Google Finance (pages `quote`) | Cours, clôture précédente, caractéristiques, secteur / pays des actions |
| Google Finance (`batchexecute`, RPC `c2u4wc`) | Historiques sur 8 périodes |
| Google Finance (`batchexecute`, RPC `mKsvE`) | Recherche (nom, ticker, ISIN) |
| justETF (page de profil par ISIN) | Fiche, composition et risques des ETF |

Limites connues :
- **ISIN** : justETF fonctionne par ISIN, que Google ne fournit pas. Il est mémorisé automatiquement lors d'une recherche par ISIN, sinon saisi une fois via « Associer un ISIN » sur la fiche.
- **Composition** : justETF publie les 4 premiers secteurs / pays ; le reste est regroupé en « Autre ».
- **Change** : la courbe d'historique applique le taux USD/EUR courant à toute la période.
- **Devises** : seules EUR et USD sont gérées pour les positions.

---

## Tests

Les tests seront ajoutés dans une étape ultérieure.
