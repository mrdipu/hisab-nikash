package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.local.AppLanguageMode
import com.example.data.local.AppThemeMode
import com.example.domain.LedgerItem
import com.example.security.AppLockManager
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AccountsScreen
import com.example.ui.screens.AddEditEntryScreen
import com.example.ui.screens.AlertsHistoryScreen
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.AppLockGateScreen
import com.example.ui.screens.BudgetScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DriveBackupScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ManualBackupScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.RecurringScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SavingsGoalsScreen
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.state.MainViewModel
import com.example.ui.theme.HisabNikashTheme
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            val isBangla = viewModel.resolveIsBangla(prefs)
            val darkTheme = when (prefs.resolvedTheme()) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, prefs.languageMode) {
                createLocalizedContext(baseContext, prefs.resolvedLanguage())
            }
            val localizedConfig = remember(localizedContext) {
                localizedContext.resources.configuration
            }

            var unlockedThisSession by rememberSaveable { mutableStateOf(false) }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig
            ) {
                HisabNikashTheme(darkTheme = darkTheme) {
                    if (prefs.appLockEnabled && !unlockedThisSession) {
                        LaunchedEffect(Unit) {
                            AppLockManager.authenticate(
                                activity = this@MainActivity,
                                title = localizedContext.getString(R.string.app_lock_prompt_title),
                                subtitle = localizedContext.getString(R.string.app_lock_prompt_subtitle),
                                onSuccess = { unlockedThisSession = true },
                                onError = { }
                            )
                        }
                        AppLockGateScreen(
                            onRequestUnlock = {
                                AppLockManager.authenticate(
                                    activity = this@MainActivity,
                                    title = localizedContext.getString(R.string.app_lock_prompt_title),
                                    subtitle = localizedContext.getString(R.string.app_lock_prompt_subtitle),
                                    onSuccess = { unlockedThisSession = true },
                                    onError = { }
                                )
                            }
                        )
                    } else {
                        HisabNikashAppRoot(
                            viewModel = viewModel,
                            isBangla = isBangla
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconcileRecurrencesOnResume()
    }

    private fun createLocalizedContext(base: Context, mode: AppLanguageMode): Context {
        if (mode == AppLanguageMode.SYSTEM) return base
        val locale = when (mode) {
            AppLanguageMode.BANGLA -> Locale.forLanguageTag("bn-BD")
            AppLanguageMode.ENGLISH -> Locale.ENGLISH
            AppLanguageMode.SYSTEM -> Locale.getDefault()
        }
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        val configContext = base.createConfigurationContext(config)
        return object : android.content.ContextWrapper(base) {
            override fun getResources(): android.content.res.Resources = configContext.resources
        }
    }
}

private data class TopLevelTab(
    val route: AppRoute,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

@Composable
private fun HisabNikashAppRoot(
    viewModel: MainViewModel,
    isBangla: Boolean
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboardState.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val ledgerItems by viewModel.ledgerItems.collectAsStateWithLifecycle()
    val currentBudgets by viewModel.currentBudgets.collectAsStateWithLifecycle()
    val selectedPeriodKey by viewModel.selectedBudgetPeriodKey.collectAsStateWithLifecycle()
    val selectedPeriodBudgets by viewModel.selectedPeriodBudgets.collectAsStateWithLifecycle()
    val goals by viewModel.savingsGoals.collectAsStateWithLifecycle()
    val templates by viewModel.recurringTemplates.collectAsStateWithLifecycle()
    val pendingOccurrences by viewModel.pendingOccurrences.collectAsStateWithLifecycle()
    val alerts by viewModel.alertHistory.collectAsStateWithLifecycle()
    val backupHistory by viewModel.backupHistory.collectAsStateWithLifecycle()
    val driveStatus by viewModel.driveStatus.collectAsStateWithLifecycle()
    val driveFiles by viewModel.driveFiles.collectAsStateWithLifecycle()
    val driveBusy by viewModel.driveBusy.collectAsStateWithLifecycle()

    val context = LocalContext.current

    if (!prefs.onboardingCompleted) {
        OnboardingScreen(
            currentLanguage = prefs.resolvedLanguage(),
            onLanguageSelected = { viewModel.setLanguageMode(it) },
            onComplete = { lang, accName, accType, openBal, budget ->
                viewModel.completeOnboarding(lang, accName, accType, openBal, budget)
            }
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val topTabs = listOf(
        TopLevelTab(AppRoute.Home, R.string.nav_home, Icons.Filled.Home, "nav_home"),
        TopLevelTab(AppRoute.Transactions, R.string.nav_transactions, Icons.AutoMirrored.Filled.ReceiptLong, "nav_transactions"),
        TopLevelTab(AppRoute.Budget, R.string.nav_budget, Icons.Filled.PieChart, "nav_budget"),
        TopLevelTab(AppRoute.Analytics, R.string.nav_analytics, Icons.Filled.Analytics, "nav_analytics"),
        TopLevelTab(AppRoute.Settings, R.string.nav_settings, Icons.Filled.Settings, "nav_settings")
    )

    val showTopNav = topTabs.any { tab ->
        currentDestination?.hasRoute(tab.route::class) == true
    }

    fun navigateTopTab(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (showTopNav && !useRail) {
                    NavigationBar {
                        topTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopNav && useRail) {
                    NavigationRail {
                        topTabs.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Home,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<AppRoute.Home> {
                        HomeScreen(
                            state = dashboard,
                            budgets = currentBudgets,
                            goals = goals,
                            isBangla = isBangla,
                            hideBalances = prefs.hideBalances,
                            dateFormatStyle = prefs.resolvedDateFormat(),
                            onToggleHideBalances = { viewModel.toggleHideBalances() },
                            onOpenAlerts = { navController.navigate(AppRoute.AlertsHistory) },
                            onOpenRecurring = { navController.navigate(AppRoute.Recurring) },
                            onQuickAddExpense = { navController.navigate(AppRoute.AddEditEntry("EXPENSE", null)) },
                            onQuickAddIncome = { navController.navigate(AppRoute.AddEditEntry("INCOME", null)) },
                            onQuickTransfer = { navController.navigate(AppRoute.AddEditEntry("TRANSFER", null)) },
                            onOpenGoals = { navController.navigate(AppRoute.SavingsGoals) },
                            onOpenAccounts = { navController.navigate(AppRoute.Accounts) },
                            onOpenBudget = { navigateTopTab(AppRoute.Budget) },
                            onOpenTransactions = { navigateTopTab(AppRoute.Transactions) },
                            onTransactionClick = { item ->
                                if (item is LedgerItem.CashFlow) {
                                    navController.navigate(AppRoute.AddEditEntry(item.kind.name, item.id))
                                } else {
                                    navigateTopTab(AppRoute.Transactions)
                                }
                            }
                        )
                    }

                    composable<AppRoute.Transactions> {
                        TransactionsScreen(
                            items = ledgerItems,
                            accounts = accounts,
                            categories = categories,
                            isBangla = isBangla,
                            hideBalances = prefs.hideBalances,
                            dateFormatStyle = prefs.resolvedDateFormat(),
                            onAddEntry = { navController.navigate(AppRoute.AddEditEntry("EXPENSE", null)) },
                            onEditTransaction = { id -> navController.navigate(AppRoute.AddEditEntry("EXPENSE", id)) },
                            onDuplicateTransaction = { id -> viewModel.duplicateTransaction(id) },
                            onDeleteItem = { item -> viewModel.deleteLedgerItem(item) }
                        )
                    }

                    composable<AppRoute.Budget> {
                        BudgetScreen(
                            periodKey = selectedPeriodKey,
                            budgetStartDay = prefs.budgetStartDay,
                            budgets = selectedPeriodBudgets,
                            categories = categories,
                            isBangla = isBangla,
                            hideBalances = prefs.hideBalances,
                            onChangePeriod = { viewModel.setSelectedBudgetPeriod(it) },
                            onSaveBudget = { pKey, catId, limit -> viewModel.saveBudget(pKey, catId, limit) },
                            onDeleteBudget = { id -> viewModel.deleteBudget(id) },
                            onCopyFromPrevious = { targetKey, onDone -> viewModel.copyBudgetsFromPrevious(targetKey, onDone) },
                            onCopyToNext = { srcKey, onDone -> viewModel.copyBudgetsToNext(srcKey, onDone) },
                            onOpenAlertHistory = { navController.navigate(AppRoute.AlertsHistory) }
                        )
                    }

                    composable<AppRoute.Analytics> {
                        AnalyticsScreen(
                            items = ledgerItems,
                            accounts = accounts,
                            categories = categories,
                            budgetStartDay = prefs.budgetStartDay,
                            isBangla = isBangla,
                            hideBalances = prefs.hideBalances,
                            onOpenReports = { navController.navigate(AppRoute.Reports) }
                        )
                    }

                    composable<AppRoute.Settings> {
                        SettingsScreen(
                            prefs = prefs,
                            accounts = accounts,
                            isBangla = isBangla,
                            onSetTheme = { viewModel.setThemeMode(it) },
                            onSetLanguage = { viewModel.setLanguageMode(it) },
                            onSetDateFormat = { viewModel.setDateFormat(it) },
                            onSetBudgetStartDay = { viewModel.setBudgetStartDay(it) },
                            onSetDefaultAccount = { viewModel.setDefaultAccountId(it) },
                            onSetBudgetAlerts = { viewModel.setBudgetAlertsEnabled(it) },
                            onSetRecurringReminders = { viewModel.setRecurringRemindersEnabled(it) },
                            onSetBackupNotifications = { viewModel.setBackupNotificationsEnabled(it) },
                            onOpenAccounts = { navController.navigate(AppRoute.Accounts) },
                            onOpenCategories = { navController.navigate(AppRoute.Categories) },
                            onOpenGoals = { navController.navigate(AppRoute.SavingsGoals) },
                            onOpenRecurring = { navController.navigate(AppRoute.Recurring) },
                            onOpenReports = { navController.navigate(AppRoute.Reports) },
                            onOpenManualBackup = { navController.navigate(AppRoute.ManualBackup) },
                            onOpenDriveBackup = { navController.navigate(AppRoute.DriveBackup) },
                            onOpenSecurity = { navController.navigate(AppRoute.Security) },
                            onOpenAbout = { navController.navigate(AppRoute.About) },
                            onClearAllData = { viewModel.clearAllData { } }
                        )
                    }

                    composable<AppRoute.AddEditEntry> { entry ->
                        val args = entry.toRoute<AppRoute.AddEditEntry>()
                        val existing = args.existingId?.let { id ->
                            ledgerItems.filterIsInstance<LedgerItem.CashFlow>().firstOrNull { it.id == id }
                        }
                        AddEditEntryScreen(
                            initialTab = args.initialTab,
                            existingCashFlow = existing,
                            accounts = accounts,
                            categories = categories,
                            defaultAccountId = prefs.defaultAccountId,
                            isBangla = isBangla,
                            onBack = { navController.popBackStack() },
                            onSaveTransaction = { exId, kind, accId, catId, amt, ts, note, merchant, tags, pm, rUri, done ->
                                viewModel.saveTransaction(exId, kind, accId, catId, amt, ts, note, merchant, tags, pm, rUri, done)
                            },
                            onSaveTransfer = { fromId, toId, amt, ts, note, done ->
                                viewModel.saveTransfer(null, fromId, toId, amt, ts, note, done)
                            },
                            onSaveAdjustment = { accId, delta, reason, done ->
                                viewModel.createBalanceAdjustment(accId, delta, reason, done)
                            }
                        )
                    }

                    composable<AppRoute.Accounts> {
                        AccountsScreen(
                            accounts = accounts,
                            isBangla = isBangla,
                            hideBalances = prefs.hideBalances,
                            onBack = { navController.popBackStack() },
                            onCreateAccount = { name, type, open, notes, inc ->
                                viewModel.createAccount(name, type, open, notes, inc)
                            },
                            onUpdateAccount = { id, name, type, open, notes, inc ->
                                viewModel.updateAccount(id, name, type, open, notes, inc)
                            },
                            onToggleArchive = { id, archived ->
                                viewModel.setAccountArchived(id, archived)
                            }
                        )
                    }

                    composable<AppRoute.Categories> {
                        CategoriesScreen(
                            categories = categories,
                            isBangla = isBangla,
                            onBack = { navController.popBackStack() },
                            onSaveCategory = { exId, type, nEn, nBn, icon, color ->
                                viewModel.saveCategory(exId, type, nEn, nBn, icon, color)
                            },
                            onToggleArchive = { id, archived ->
                                viewModel.setCategoryArchived(id, archived)
                            }
                        )
                    }

                    composable<AppRoute.SavingsGoals> {
                        SavingsGoalsScreen(
                            goals = goals,
                            accounts = accounts,
                            isBangla = isBangla,
                            hideBalances = prefs.hideBalances,
                            onBack = { navController.popBackStack() },
                            onSaveGoal = { name, preset, target, notes, done ->
                                viewModel.saveSavingsGoal(null, name, preset, target, notes, done)
                            },
                            onAllocate = { goalId, accId, amt, note, done ->
                                viewModel.allocateToGoal(goalId, accId, amt, note, done)
                            },
                            onRelease = { goalId, accId, amt, note, done ->
                                viewModel.releaseFromGoal(goalId, accId, amt, note, done)
                            },
                            onDeleteGoal = { id -> viewModel.deleteGoal(id) }
                        )
                    }

                    composable<AppRoute.Recurring> {
                        RecurringScreen(
                            templates = templates,
                            pendingOccurrences = pendingOccurrences,
                            accounts = accounts,
                            categories = categories,
                            isBangla = isBangla,
                            onBack = { navController.popBackStack() },
                            onSaveTemplate = { title, kind, accId, catId, amt, freq, start, autoPost, rem, note, done ->
                                viewModel.saveRecurringTemplate(null, title, kind, accId, catId, amt, freq, start, autoPost, rem, note, done)
                            },
                            onToggleEnabled = { id, en -> viewModel.toggleRecurringEnabled(id, en) },
                            onDeleteTemplate = { id -> viewModel.deleteRecurringTemplate(id) },
                            onConfirmOccurrence = { id -> viewModel.confirmOccurrence(id) },
                            onSkipOccurrence = { id -> viewModel.skipOccurrence(id) }
                        )
                    }

                    composable<AppRoute.Reports> {
                        ReportsScreen(
                            items = ledgerItems,
                            accounts = accounts,
                            budgets = currentBudgets,
                            budgetStartDay = prefs.budgetStartDay,
                            isBangla = isBangla,
                            hideBalances = prefs.hideBalances,
                            onBuildReportBytes = { summary, asPdf ->
                                viewModel.buildReportBytes(context, summary, asPdf)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.ManualBackup> {
                        ManualBackupScreen(
                            prefs = prefs,
                            history = backupHistory,
                            isBangla = isBangla,
                            onExportToUri = { uri, fileName, pass ->
                                viewModel.exportEncryptedBackupToUri(context, uri, fileName, pass)
                            },
                            onInspectUri = { uri, pass ->
                                viewModel.inspectBackupFromUri(context, uri, pass)
                            },
                            onApplyRestore = { prev, mode, res ->
                                viewModel.applyRestorePreview(prev, mode, res)
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.DriveBackup> {
                        DriveBackupScreen(
                            prefs = prefs,
                            driveStatus = driveStatus,
                            driveFiles = driveFiles,
                            busy = driveBusy,
                            isBangla = isBangla,
                            onCheckConnection = { viewModel.checkDriveConnection() },
                            onConnectDrive = { viewModel.connectGoogleDrive() },
                            onHandleResolutionResult = { resultCode, data ->
                                viewModel.handleDriveResolutionResult(resultCode, data)
                            },
                            onDisconnect = { viewModel.disconnectDriveAccount() },
                            onBackupNow = { pass, cb -> viewModel.backupToDriveNow(pass, cb) },
                            onDownloadAndInspect = { file, pass, cb -> viewModel.downloadAndInspectDriveBackup(file, pass, cb) },
                            onApplyRestore = { prev, mode, res -> viewModel.applyRestorePreview(prev, mode, res) },
                            onToggleRememberKey = { en, pass, cb -> viewModel.setRememberBackupKey(en, pass, cb) },
                            onToggleAutoBackup = { en -> viewModel.setDriveAutoBackupEnabled(en) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.Security> {
                        SecurityScreen(
                            appLockEnabled = prefs.appLockEnabled,
                            onToggleAppLock = { viewModel.setAppLockEnabled(it) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.About> {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }

                    composable<AppRoute.AlertsHistory> {
                        AlertsHistoryScreen(
                            alerts = alerts,
                            isBangla = isBangla,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
