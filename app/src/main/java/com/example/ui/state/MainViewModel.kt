package com.example.ui.state

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.HisabNikashApp
import com.example.R
import com.example.cloud.drive.DriveBackupFile
import com.example.cloud.drive.DriveConnectionStatus
import com.example.data.local.AccountType
import com.example.data.local.AlertHistoryEntity
import com.example.data.local.AppLanguageMode
import com.example.data.local.AppThemeMode
import com.example.data.local.BackupMetadataEntity
import com.example.data.local.BackupProvider
import com.example.data.local.BackupRecordStatus
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.DateFormatStyle
import com.example.data.local.GoalPreset
import com.example.data.local.RecurringOccurrenceEntity
import com.example.data.local.RecurringTemplateEntity
import com.example.data.local.SavingsAllocationEntity
import com.example.data.local.TransactionKind
import com.example.data.local.UserPreferences
import com.example.domain.AccountSummary
import com.example.domain.BudgetNotificationEvent
import com.example.domain.BudgetPeriod
import com.example.domain.BudgetPeriodCalculator
import com.example.domain.BudgetProgress
import com.example.domain.LedgerItem
import com.example.domain.MoneyFormatter
import com.example.domain.PeriodBarPoint
import com.example.domain.RecurrenceFrequency
import com.example.domain.ReportGenerator
import com.example.domain.ReportSummary
import com.example.domain.SavingsGoalSummary
import com.example.security.BackupCryptoManager
import com.example.security.ConflictResolution
import com.example.security.RestoreMode
import com.example.security.RestorePreview
import com.example.worker.AutomaticBackupWorker
import com.example.worker.LocalNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

data class DashboardState(
    val accounts: List<AccountSummary> = emptyList(),
    val activeAccounts: List<AccountSummary> = emptyList(),
    val totalBalancePoisha: Long = 0L,
    val totalEarmarkedPoisha: Long = 0L,
    val unreservedTotalPoisha: Long = 0L,
    val currentPeriod: BudgetPeriod = BudgetPeriodCalculator.currentPeriod(),
    val periodIncomePoisha: Long = 0L,
    val periodExpensePoisha: Long = 0L,
    val netCashFlowPoisha: Long = 0L,
    val recentItems: List<LedgerItem> = emptyList(),
    val topExpenseCategoryName: String? = null,
    val topExpenseCategoryPoisha: Long = 0L,
    val monthlyBars: List<PeriodBarPoint> = emptyList(),
    val pendingRecurringCount: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as HisabNikashApp).container
    private val repo = container.financeRepository
    private val prefsStore = container.prefsStore
    private val restoreManager = container.backupRestoreManager

    val preferences: StateFlow<UserPreferences> = prefsStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val accounts: StateFlow<List<AccountSummary>> = repo.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = repo.categoriesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ledgerItems: StateFlow<List<LedgerItem>> = repo.unifiedLedgerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentBudgets: StateFlow<List<BudgetProgress>> = repo.observeCurrentBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingsGoals: StateFlow<List<SavingsGoalSummary>> = repo.savingsGoalsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingsAllocations: StateFlow<List<SavingsAllocationEntity>> = repo.savingsAllocationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringTemplates: StateFlow<List<RecurringTemplateEntity>> = repo.recurringTemplatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recurringOccurrences: StateFlow<List<RecurringOccurrenceEntity>> = repo.recurringOccurrencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingOccurrences: StateFlow<List<RecurringOccurrenceEntity>> = repo.pendingOccurrencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val alertHistory: StateFlow<List<AlertHistoryEntity>> = repo.alertHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val backupHistory: StateFlow<List<BackupMetadataEntity>> = repo.backupHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedBudgetPeriodKey = MutableStateFlow(BudgetPeriodCalculator.currentPeriod().periodKey)
    val selectedBudgetPeriodKey: StateFlow<String> = _selectedBudgetPeriodKey.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedPeriodBudgets: StateFlow<List<BudgetProgress>> = combine(
        _selectedBudgetPeriodKey,
        preferences
    ) { key, prefs -> key to prefs.budgetStartDay }
        .flatMapLatest { (key, startDay) -> repo.observeBudgetsForPeriod(key, startDay) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardState: StateFlow<DashboardState> = combine(
        accounts,
        ledgerItems,
        preferences,
        pendingOccurrences
    ) { accList, items, prefs, pending ->
        val isBn = resolveIsBangla(prefs)
        val activeAccs = accList.filter { !it.isArchived }
        val includedAccs = activeAccs.filter { it.includeInTotal }
        val totalBal = includedAccs.sumOf { it.ledgerBalancePoisha }
        val totalEarmarked = includedAccs.sumOf { it.earmarkedPoisha }
        val unreservedTotal = totalBal - totalEarmarked

        val currentPeriod = BudgetPeriodCalculator.currentPeriod(configuredStartDay = prefs.budgetStartDay)
        val cashFlows = items.filterIsInstance<LedgerItem.CashFlow>()
        val inPeriod = cashFlows.filter {
            it.timestampUtc >= currentPeriod.startUtcMillis && it.timestampUtc < currentPeriod.endUtcExclusiveMillis
        }
        val periodIncome = inPeriod.filter { it.kind == TransactionKind.INCOME }.sumOf { it.amountPoisha }
        val periodExpense = inPeriod.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amountPoisha }

        val topCat = inPeriod
            .filter { it.kind == TransactionKind.EXPENSE }
            .groupBy { if (isBn) it.categoryNameBn else it.categoryNameEn }
            .mapValues { (_, list) -> list.sumOf { it.amountPoisha } }
            .maxByOrNull { it.value }

        val bars = (3 downTo 0).map { offset ->
            val ym = YearMonth.parse(currentPeriod.periodKey).minusMonths(offset.toLong())
            val p = BudgetPeriodCalculator.periodForKey(ym.toString(), prefs.budgetStartDay)
            val rows = cashFlows.filter { it.timestampUtc >= p.startUtcMillis && it.timestampUtc < p.endUtcExclusiveMillis }
            PeriodBarPoint(
                label = ym.month.name.take(3),
                incomePoisha = rows.filter { it.kind == TransactionKind.INCOME }.sumOf { it.amountPoisha },
                expensePoisha = rows.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amountPoisha }
            )
        }

        DashboardState(
            accounts = accList,
            activeAccounts = activeAccs,
            totalBalancePoisha = totalBal,
            totalEarmarkedPoisha = totalEarmarked,
            unreservedTotalPoisha = unreservedTotal,
            currentPeriod = currentPeriod,
            periodIncomePoisha = periodIncome,
            periodExpensePoisha = periodExpense,
            netCashFlowPoisha = periodIncome - periodExpense,
            recentItems = items.take(8),
            topExpenseCategoryName = topCat?.key,
            topExpenseCategoryPoisha = topCat?.value ?: 0L,
            monthlyBars = bars,
            pendingRecurringCount = pending.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents = _toastEvents.asSharedFlow()

    // Drive UI state
    private val _driveStatus = MutableStateFlow<DriveConnectionStatus>(DriveConnectionStatus.NotAuthorized)
    val driveStatus: StateFlow<DriveConnectionStatus> = _driveStatus.asStateFlow()

    private val _driveFiles = MutableStateFlow<List<DriveBackupFile>>(emptyList())
    val driveFiles: StateFlow<List<DriveBackupFile>> = _driveFiles.asStateFlow()

    private val _driveBusy = MutableStateFlow(false)
    val driveBusy: StateFlow<Boolean> = _driveBusy.asStateFlow()

    init {
        viewModelScope.launch {
            repo.ensureSeedCategories()
            reconcileRecurrencesOnResume()
        }
    }

    fun resolveIsBangla(prefs: UserPreferences = preferences.value): Boolean {
        return when (prefs.resolvedLanguage()) {
            AppLanguageMode.BANGLA -> true
            AppLanguageMode.ENGLISH -> false
            AppLanguageMode.SYSTEM -> Locale.getDefault().language.equals("bn", ignoreCase = true)
        }
    }

    fun reconcileRecurrencesOnResume() {
        viewModelScope.launch {
            runCatching { repo.reconcileDueRecurrences() }
        }
    }

    // ---- Preferences ----
    fun completeOnboarding(
        language: AppLanguageMode,
        initialAccountName: String?,
        initialAccountType: AccountType,
        openingBalancePoisha: Long,
        optionalBudgetPoisha: Long?
    ) {
        viewModelScope.launch {
            prefsStore.setLanguageMode(language)
            if (!initialAccountName.isNullOrBlank()) {
                repo.createAccount(
                    name = initialAccountName,
                    type = initialAccountType,
                    openingBalancePoisha = openingBalancePoisha
                )
            }
            if (optionalBudgetPoisha != null && optionalBudgetPoisha > 0L) {
                val period = BudgetPeriodCalculator.currentPeriod(configuredStartDay = preferences.value.budgetStartDay)
                repo.saveBudget(period.periodKey, null, optionalBudgetPoisha)
            }
            prefsStore.setOnboardingCompleted(true)
        }
    }

    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch { prefsStore.setThemeMode(mode) }
    fun setLanguageMode(mode: AppLanguageMode) = viewModelScope.launch { prefsStore.setLanguageMode(mode) }
    fun setDateFormat(style: DateFormatStyle) = viewModelScope.launch { prefsStore.setDateFormat(style) }
    fun setBudgetStartDay(day: Int) = viewModelScope.launch { prefsStore.setBudgetStartDay(day) }
    fun setDefaultAccountId(id: String) = viewModelScope.launch { prefsStore.setDefaultAccountId(id) }
    fun toggleHideBalances() = viewModelScope.launch { prefsStore.setHideBalances(!preferences.value.hideBalances) }
    fun setBudgetAlertsEnabled(enabled: Boolean) = viewModelScope.launch { prefsStore.setBudgetAlertsEnabled(enabled) }
    fun setRecurringRemindersEnabled(enabled: Boolean) = viewModelScope.launch { prefsStore.setRecurringRemindersEnabled(enabled) }
    fun setBackupNotificationsEnabled(enabled: Boolean) = viewModelScope.launch { prefsStore.setBackupNotificationsEnabled(enabled) }
    fun setAppLockEnabled(enabled: Boolean) = viewModelScope.launch { prefsStore.setAppLockEnabled(enabled) }

    // ---- Accounts ----
    fun createAccount(name: String, type: AccountType, openingBalancePoisha: Long, notes: String, includeInTotal: Boolean) {
        viewModelScope.launch {
            repo.createAccount(name, type, openingBalancePoisha, notes, includeInTotal)
        }
    }

    fun updateAccount(id: String, name: String, type: AccountType, openingBalancePoisha: Long, notes: String, includeInTotal: Boolean) {
        viewModelScope.launch {
            repo.updateAccount(id, name, type, openingBalancePoisha, notes, includeInTotal)
        }
    }

    fun setAccountArchived(id: String, archived: Boolean) {
        viewModelScope.launch { repo.setAccountArchived(id, archived) }
    }

    fun createBalanceAdjustment(accountId: String, deltaPoisha: Long, reason: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repo.createBalanceAdjustment(accountId, deltaPoisha, reason).map { }
            onDone(res)
        }
    }

    // ---- Transactions & Transfers ----
    fun saveTransaction(
        existingId: String?,
        kind: TransactionKind,
        accountId: String,
        categoryId: String,
        amountPoisha: Long,
        timestampUtc: Long,
        note: String,
        merchant: String,
        tags: String,
        paymentMethod: String,
        receiptUri: String?,
        onDone: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repo.saveTransaction(
                existingId = existingId,
                kind = kind,
                accountId = accountId,
                categoryId = categoryId,
                amountPoisha = amountPoisha,
                timestampUtc = timestampUtc,
                note = note,
                merchant = merchant,
                tags = tags,
                paymentMethod = paymentMethod,
                receiptUri = receiptUri
            )
            if (res.isSuccess) {
                val events = res.getOrThrow().second
                notifyBudgetThresholds(events)
            }
            onDone(res.map { })
        }
    }

    fun duplicateTransaction(id: String) {
        viewModelScope.launch {
            val res = repo.duplicateTransaction(id)
            if (res.isSuccess) {
                notifyBudgetThresholds(res.getOrThrow().second)
            }
        }
    }

    fun deleteLedgerItem(item: LedgerItem) {
        viewModelScope.launch {
            when (item) {
                is LedgerItem.CashFlow -> repo.softDeleteTransaction(item.id)
                is LedgerItem.Transfer -> repo.softDeleteTransfer(item.id)
                is LedgerItem.Adjustment -> repo.softDeleteAdjustment(item.id)
            }
        }
    }

    fun saveTransfer(
        existingId: String?,
        fromAccountId: String,
        toAccountId: String,
        amountPoisha: Long,
        timestampUtc: Long,
        note: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repo.saveTransfer(existingId, fromAccountId, toAccountId, amountPoisha, timestampUtc, note).map { }
            onDone(res)
        }
    }

    // ---- Categories ----
    fun saveCategory(existingId: String?, type: CategoryType, nameEn: String, nameBn: String, iconKey: String, colorHex: Long) {
        viewModelScope.launch {
            repo.saveCategory(existingId, type, nameEn, nameBn, iconKey, colorHex)
        }
    }

    fun setCategoryArchived(id: String, archived: Boolean) {
        viewModelScope.launch { repo.setCategoryArchived(id, archived) }
    }

    // ---- Budgets ----
    fun setSelectedBudgetPeriod(periodKey: String) {
        _selectedBudgetPeriodKey.value = periodKey
    }

    fun saveBudget(periodKey: String, categoryId: String?, limitPoisha: Long, onDone: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = repo.saveBudget(periodKey, categoryId, limitPoisha)
            if (res.isSuccess) {
                notifyBudgetThresholds(res.getOrThrow().second)
            }
            onDone(res.map { })
        }
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch { repo.softDeleteBudget(id) }
    }

    fun copyBudgetsFromPrevious(targetPeriodKey: String, onCopied: (Int) -> Unit) {
        viewModelScope.launch {
            val prevKey = YearMonth.parse(targetPeriodKey).minusMonths(1).toString()
            val count = repo.copyBudgets(prevKey, targetPeriodKey)
            onCopied(count)
        }
    }

    fun copyBudgetsToNext(sourcePeriodKey: String, onCopied: (Int) -> Unit) {
        viewModelScope.launch {
            val nextKey = YearMonth.parse(sourcePeriodKey).plusMonths(1).toString()
            val count = repo.copyBudgets(sourcePeriodKey, nextKey)
            onCopied(count)
        }
    }

    private fun notifyBudgetThresholds(events: List<BudgetNotificationEvent>) {
        if (!preferences.value.budgetAlertsEnabled || events.isEmpty()) return
        val ctx = getApplication<Application>()
        val isBn = resolveIsBangla()
        events.forEachIndexed { idx, ev ->
            val catLabel = if (isBn) (ev.categoryNameBn ?: "সামগ্রিক") else (ev.categoryNameEn ?: "Overall")
            val spentFmt = MoneyFormatter.formatBdt(ev.spentPoisha, isBn)
            val limitFmt = MoneyFormatter.formatBdt(ev.limitPoisha, isBn)
            val title = if (isBn) {
                "বাজেট সতর্কতা (${MoneyFormatter.toBanglaDigits(ev.thresholdPercent.toString())}%)"
            } else {
                "Budget Alert (${ev.thresholdPercent}%)"
            }
            val body = if (isBn) {
                "$catLabel: $limitFmt এর মধ্যে $spentFmt ব্যয় হয়েছে"
            } else {
                "$catLabel: Spent $spentFmt of $limitFmt"
            }
            LocalNotificationHelper.postNotification(
                context = ctx,
                channelId = LocalNotificationHelper.CHANNEL_BUDGET,
                notificationId = 1000 + idx + (ev.budgetId.hashCode() and 0xFF),
                title = title,
                body = body
            )
        }
    }

    // ---- Savings Goals ----
    fun saveSavingsGoal(
        existingId: String?,
        name: String,
        preset: GoalPreset,
        targetPoisha: Long,
        notes: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repo.saveSavingsGoal(existingId, name, preset, targetPoisha, null, notes).map { }
            onDone(res)
        }
    }

    fun allocateToGoal(goalId: String, accountId: String, amountPoisha: Long, note: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repo.allocateToGoal(goalId, accountId, amountPoisha, note).map { }
            onDone(res)
        }
    }

    fun releaseFromGoal(goalId: String, accountId: String, amountPoisha: Long, note: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = repo.releaseFromGoal(goalId, accountId, amountPoisha, note).map { }
            onDone(res)
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch { repo.softDeleteGoal(goalId) }
    }

    // ---- Recurring ----
    fun saveRecurringTemplate(
        existingId: String?,
        title: String,
        kind: TransactionKind,
        accountId: String,
        categoryId: String,
        amountPoisha: Long,
        frequency: RecurrenceFrequency,
        startDate: LocalDate,
        autoPost: Boolean,
        reminderEnabled: Boolean,
        note: String,
        onDone: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val res = repo.saveRecurringTemplate(
                existingId, title, kind, accountId, categoryId, amountPoisha,
                frequency, startDate, null, autoPost, reminderEnabled, true, note
            )
            if (res.isSuccess) {
                repo.reconcileDueRecurrences()
            }
            onDone(res.map { })
        }
    }

    fun toggleRecurringEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repo.setRecurringEnabled(id, enabled) }
    }

    fun deleteRecurringTemplate(id: String) {
        viewModelScope.launch { repo.softDeleteRecurringTemplate(id) }
    }

    fun confirmOccurrence(id: String) {
        viewModelScope.launch { repo.confirmRecurringOccurrence(id) }
    }

    fun skipOccurrence(id: String) {
        viewModelScope.launch { repo.skipRecurringOccurrence(id) }
    }

    // ---- Reports (CSV & PDF) ----
    suspend fun buildReportBytes(
        context: Context,
        summary: ReportSummary,
        asPdf: Boolean
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val isBn = resolveIsBangla()
        if (asPdf) {
            ReportGenerator.generatePdfBytes(context, summary, isBn)
        } else {
            Result.success(ReportGenerator.generateCsvBytes(summary.transactions, isBn))
        }
    }

    // ---- Manual Encrypted Backup / Restore ----
    suspend fun exportEncryptedBackupToUri(
        context: Context,
        uri: Uri,
        fileName: String,
        passphrase: CharArray
    ): Result<Int> = withContext(Dispatchers.IO) {
        val exportResult = restoreManager.exportEncrypted(passphrase)
        if (exportResult.isFailure) {
            return@withContext Result.failure(exportResult.exceptionOrNull()!!)
        }
        val (bytes, snapshot) = exportResult.getOrThrow()
        try {
            val os = context.contentResolver.openOutputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Could not open destination URI for writing"))
            os.use {
                it.write(bytes)
                it.flush()
            }
            val now = System.currentTimeMillis()
            prefsStore.recordVerifiedLocalBackup(now)
            val totalCount = snapshot.counts.accounts + snapshot.counts.transactions + snapshot.counts.transfers
            repo.recordBackupMetadata(
                BackupMetadataEntity(
                    provider = BackupProvider.LOCAL_FILE,
                    fileName = fileName,
                    schemaVersion = snapshot.roomSchemaVersion,
                    recordCount = totalCount,
                    status = BackupRecordStatus.VERIFIED,
                    createdAt = now
                )
            )
            Result.success(bytes.size)
        } catch (t: Throwable) {
            repo.recordBackupMetadata(
                BackupMetadataEntity(
                    provider = BackupProvider.LOCAL_FILE,
                    fileName = fileName,
                    schemaVersion = snapshot.roomSchemaVersion,
                    recordCount = 0,
                    status = BackupRecordStatus.FAILED,
                    errorMessage = t.message,
                    createdAt = System.currentTimeMillis()
                )
            )
            Result.failure(t)
        }
    }

    suspend fun inspectBackupFromUri(context: Context, uri: Uri, passphrase: CharArray): Result<RestorePreview> =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(IllegalStateException("Cannot read selected file"))
                val bytes = inputStream.use { it.readBytes() }
                val snapshot = restoreManager.decryptAndValidate(bytes, passphrase).getOrThrow()
                val preview = restoreManager.previewMerge(snapshot)
                Result.success(preview)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun applyRestorePreview(
        preview: RestorePreview,
        mode: RestoreMode,
        conflictResolution: ConflictResolution
    ): Result<Unit> = withContext(Dispatchers.IO) {
        restoreManager.applyRestore(preview.snapshot, mode, conflictResolution)
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.clearAllLocalData()
            onDone()
        }
    }

    // ---- Google Drive Backup (Optional) ----
    fun checkDriveConnection() {
        viewModelScope.launch {
            _driveBusy.value = true
            val status = container.googleAuthorizationManager.checkOrRequestAuthorization(
                savedEmail = preferences.value.driveAccountEmail,
                interactive = false
            )
            applyDriveStatus(status)
            _driveBusy.value = false
        }
    }

    fun connectGoogleDrive() {
        viewModelScope.launch {
            _driveBusy.value = true
            val status = container.googleAuthorizationManager.checkOrRequestAuthorization(
                savedEmail = preferences.value.driveAccountEmail,
                interactive = true
            )
            applyDriveStatus(status)
            _driveBusy.value = false
        }
    }

    fun handleDriveResolutionResult(resultCode: Int, data: android.content.Intent?) {
        viewModelScope.launch {
            _driveBusy.value = true
            val status = container.googleAuthorizationManager.handleResolutionActivityResult(
                resultCode = resultCode,
                data = data,
                savedEmail = preferences.value.driveAccountEmail
            )
            applyDriveStatus(status)
            _driveBusy.value = false
        }
    }

    private suspend fun applyDriveStatus(status: DriveConnectionStatus) {
        _driveStatus.value = status
        if (status is DriveConnectionStatus.Authorized) {
            if (status.accountEmail.isNotBlank()) {
                prefsStore.setDriveAccountEmail(status.accountEmail)
            }
            refreshDriveBackups(status.accessToken)
        } else if (
            status is DriveConnectionStatus.NotAuthorized ||
            status is DriveConnectionStatus.ConsentCancelled ||
            status is DriveConnectionStatus.SetupRequired ||
            status is DriveConnectionStatus.PlayServicesUnavailable
        ) {
            _driveFiles.value = emptyList()
        }
    }

    fun disconnectDriveAccount() {
        viewModelScope.launch {
            prefsStore.setDriveAccountEmail("")
            prefsStore.setDriveAutoBackupEnabled(false)
            AutomaticBackupWorker.scheduleOrCancel(getApplication(), false)
            _driveFiles.value = emptyList()
            _driveStatus.value = DriveConnectionStatus.NotAuthorized
        }
    }

    private fun isUnauthorizedError(t: Throwable?): Boolean {
        val msg = t?.message.orEmpty()
        return msg.contains("(401)") || msg.contains("Authorization expired", ignoreCase = true)
    }

    private suspend fun trySilentTokenRefresh(): DriveConnectionStatus.Authorized? {
        val refreshed = container.googleAuthorizationManager.checkOrRequestAuthorization(
            savedEmail = preferences.value.driveAccountEmail,
            interactive = false
        )
        _driveStatus.value = refreshed
        if (refreshed is DriveConnectionStatus.Authorized) {
            if (refreshed.accountEmail.isNotBlank()) {
                prefsStore.setDriveAccountEmail(refreshed.accountEmail)
            }
            return refreshed
        }
        return null
    }

    private suspend fun refreshDriveBackups(accessToken: String) {
        val listResult = container.driveBackupRepository.listBackups(accessToken)
        if (listResult.isSuccess) {
            _driveFiles.value = listResult.getOrThrow()
        } else if (isUnauthorizedError(listResult.exceptionOrNull())) {
            val refreshed = trySilentTokenRefresh()
            if (refreshed != null) {
                val retry = container.driveBackupRepository.listBackups(refreshed.accessToken)
                if (retry.isSuccess) {
                    _driveFiles.value = retry.getOrThrow()
                }
            } else {
                _driveFiles.value = emptyList()
            }
        }
    }

    fun backupToDriveNow(passphrase: CharArray, onResult: (Result<DriveBackupFile>) -> Unit) {
        viewModelScope.launch {
            val status = _driveStatus.value
            if (status !is DriveConnectionStatus.Authorized) {
                onResult(Result.failure(IllegalStateException("Google Drive is not authorized")))
                return@launch
            }
            _driveBusy.value = true
            val res = withContext(Dispatchers.IO) {
                val exportRes = restoreManager.exportEncrypted(passphrase)
                if (exportRes.isFailure) return@withContext Result.failure<DriveBackupFile>(exportRes.exceptionOrNull()!!)
                val (bytes, snapshot) = exportRes.getOrThrow()
                val fileName = "hisabnikash_${System.currentTimeMillis()}.hnkb"
                var activeToken = status.accessToken
                var uploadRes = container.driveBackupRepository.uploadEncryptedBackup(activeToken, fileName, bytes)
                if (uploadRes.isFailure && isUnauthorizedError(uploadRes.exceptionOrNull())) {
                    val refreshed = trySilentTokenRefresh()
                    if (refreshed != null) {
                        activeToken = refreshed.accessToken
                        uploadRes = container.driveBackupRepository.uploadEncryptedBackup(activeToken, fileName, bytes)
                    }
                }
                if (uploadRes.isSuccess) {
                    val uploaded = uploadRes.getOrThrow()
                    val now = System.currentTimeMillis()
                    prefsStore.recordVerifiedDriveBackup(now, restoreManager.computeCanonicalHash(snapshot))
                    repo.recordBackupMetadata(
                        BackupMetadataEntity(
                            provider = BackupProvider.GOOGLE_DRIVE,
                            fileName = uploaded.name,
                            remoteFileId = uploaded.id,
                            schemaVersion = snapshot.roomSchemaVersion,
                            recordCount = snapshot.counts.transactions + snapshot.counts.accounts,
                            status = BackupRecordStatus.VERIFIED,
                            createdAt = now
                        )
                    )
                    refreshDriveBackups(activeToken)
                    Result.success(uploaded)
                } else {
                    Result.failure(uploadRes.exceptionOrNull()!!)
                }
            }
            _driveBusy.value = false
            onResult(res)
        }
    }

    fun downloadAndInspectDriveBackup(
        file: DriveBackupFile,
        passphrase: CharArray,
        onResult: (Result<RestorePreview>) -> Unit
    ) {
        viewModelScope.launch {
            val status = _driveStatus.value
            if (status !is DriveConnectionStatus.Authorized) {
                onResult(Result.failure(IllegalStateException("Google Drive is not authorized")))
                return@launch
            }
            _driveBusy.value = true
            val res = withContext(Dispatchers.IO) {
                var dl = container.driveBackupRepository.downloadBackup(status.accessToken, file.id)
                if (dl.isFailure && isUnauthorizedError(dl.exceptionOrNull())) {
                    val refreshed = trySilentTokenRefresh()
                    if (refreshed != null) {
                        dl = container.driveBackupRepository.downloadBackup(refreshed.accessToken, file.id)
                    }
                }
                if (dl.isFailure) return@withContext Result.failure<RestorePreview>(dl.exceptionOrNull()!!)
                val snapshot = restoreManager.decryptAndValidate(dl.getOrThrow(), passphrase)
                if (snapshot.isFailure) return@withContext Result.failure<RestorePreview>(snapshot.exceptionOrNull()!!)
                Result.success(restoreManager.previewMerge(snapshot.getOrThrow()))
            }
            _driveBusy.value = false
            onResult(res)
        }
    }

    fun setRememberBackupKey(enabled: Boolean, passphrase: CharArray?, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            if (!enabled) {
                BackupCryptoManager.clearRememberedBackupKey(getApplication())
                prefsStore.setRememberBackupKey(false)
                prefsStore.setDriveAutoBackupEnabled(false)
                AutomaticBackupWorker.scheduleOrCancel(getApplication(), false)
                onResult(Result.success(Unit))
            } else {
                if (passphrase == null || passphrase.size < 8) {
                    onResult(Result.failure(IllegalArgumentException(getApplication<Application>().getString(R.string.err_passphrase_short))))
                    return@launch
                }
                val res = withContext(Dispatchers.IO) {
                    BackupCryptoManager.saveRememberedBackupKey(getApplication(), passphrase)
                }
                if (res.isSuccess) {
                    prefsStore.setRememberBackupKey(true)
                }
                onResult(res)
            }
        }
    }

    fun setDriveAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.setDriveAutoBackupEnabled(enabled)
            AutomaticBackupWorker.scheduleOrCancel(getApplication(), enabled)
        }
    }
}
