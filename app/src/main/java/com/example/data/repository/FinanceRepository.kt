package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AccountEntity
import com.example.data.local.AccountType
import com.example.data.local.AlertHistoryEntity
import com.example.data.local.AllocationDirection
import com.example.data.local.BalanceAdjustmentEntity
import com.example.data.local.BackupMetadataEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.GoalPreset
import com.example.data.local.GoalStatus
import com.example.data.local.HisabNikashDatabase
import com.example.data.local.OccurrenceStatus
import com.example.data.local.PreferencesDataStore
import com.example.data.local.RecurringOccurrenceEntity
import com.example.data.local.RecurringTemplateEntity
import com.example.data.local.SavingsAllocationEntity
import com.example.data.local.SavingsGoalEntity
import com.example.data.local.SeedData
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionKind
import com.example.data.local.TransferEntity
import com.example.domain.AccountSummary
import com.example.domain.BudgetNotificationEvent
import com.example.domain.BudgetPeriodCalculator
import com.example.domain.BudgetProgress
import com.example.domain.LedgerItem
import com.example.domain.RecurrenceFrequency
import com.example.domain.RecurrenceScheduler
import com.example.domain.SavingsGoalSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class FinanceRepository(
    private val db: HisabNikashDatabase,
    private val prefsStore: PreferencesDataStore
) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val transferDao = db.transferDao()
    private val adjustmentDao = db.balanceAdjustmentDao()
    private val budgetDao = db.budgetDao()
    private val alertHistoryDao = db.alertHistoryDao()
    private val savingsGoalDao = db.savingsGoalDao()
    private val recurringDao = db.recurringDao()
    private val backupMetadataDao = db.backupMetadataDao()

    val accountsFlow: Flow<List<AccountSummary>> = accountDao.observeAccountBalances()
        .map { rows -> rows.map(AccountSummary::fromRow) }

    val categoriesFlow: Flow<List<CategoryEntity>> = categoryDao.observeCategories()

    val activeTransactionsFlow: Flow<List<TransactionEntity>> = transactionDao.observeActiveTransactions()

    val activeTransfersFlow: Flow<List<TransferEntity>> = transferDao.observeActiveTransfers()

    val activeAdjustmentsFlow: Flow<List<BalanceAdjustmentEntity>> = adjustmentDao.observeActiveAdjustments()

    val alertHistoryFlow: Flow<List<AlertHistoryEntity>> = alertHistoryDao.observeAlertHistory()

    val recurringTemplatesFlow: Flow<List<RecurringTemplateEntity>> = recurringDao.observeTemplates()

    val recurringOccurrencesFlow: Flow<List<RecurringOccurrenceEntity>> = recurringDao.observeOccurrences()

    val pendingOccurrencesFlow: Flow<List<RecurringOccurrenceEntity>> = recurringDao.observePendingOccurrences()

    val backupHistoryFlow: Flow<List<BackupMetadataEntity>> = backupMetadataDao.observeHistory()

    val savingsGoalsFlow: Flow<List<SavingsGoalSummary>> = combine(
        savingsGoalDao.observeGoals(),
        savingsGoalDao.observeAllocations()
    ) { goals, allocations ->
        val reservedByGoal = mutableMapOf<String, Long>()
        for (alloc in allocations) {
            val current = reservedByGoal[alloc.goalId] ?: 0L
            val delta = if (alloc.direction == AllocationDirection.ALLOCATE) alloc.amountPoisha else -alloc.amountPoisha
            reservedByGoal[alloc.goalId] = current + delta
        }
        goals.map { g ->
            SavingsGoalSummary(
                id = g.id,
                name = g.name,
                preset = g.preset,
                targetAmountPoisha = g.targetAmountPoisha,
                reservedPoisha = (reservedByGoal[g.id] ?: 0L).coerceAtLeast(0L),
                targetDateEpochDay = g.targetDateEpochDay,
                status = g.status,
                notes = g.notes
            )
        }
    }

    val savingsAllocationsFlow: Flow<List<SavingsAllocationEntity>> = savingsGoalDao.observeAllocations()

    val unifiedLedgerFlow: Flow<List<LedgerItem>> = combine(
        accountsFlow,
        categoriesFlow,
        activeTransactionsFlow,
        activeTransfersFlow,
        activeAdjustmentsFlow
    ) { accounts, categories, txs, transfers, adjustments ->
        val accountNames = accounts.associate { it.id to it.name }
        val categoryMap = categories.associateBy { it.id }

        val items = ArrayList<LedgerItem>(txs.size + transfers.size + adjustments.size)
        for (tx in txs) {
            val cat = categoryMap[tx.categoryId]
            items.add(
                LedgerItem.CashFlow(
                    id = tx.id,
                    kind = tx.kind,
                    accountId = tx.accountId,
                    accountName = accountNames[tx.accountId] ?: "Account",
                    categoryId = tx.categoryId,
                    categoryNameEn = cat?.nameEn ?: "Other",
                    categoryNameBn = cat?.nameBn ?: "অন্যান্য",
                    categoryColorHex = cat?.colorHex ?: 0xFF546E7AL,
                    categoryIconKey = cat?.iconKey ?: "category",
                    amountPoisha = tx.amountPoisha,
                    timestampUtc = tx.timestampUtc,
                    note = tx.note,
                    merchant = tx.merchant,
                    tags = tx.tags,
                    paymentMethod = tx.paymentMethod,
                    receiptUri = tx.receiptUri,
                    createdAt = tx.createdAt,
                    updatedAt = tx.updatedAt
                )
            )
        }
        for (tr in transfers) {
            items.add(
                LedgerItem.Transfer(
                    id = tr.id,
                    fromAccountId = tr.fromAccountId,
                    fromAccountName = accountNames[tr.fromAccountId] ?: "Account",
                    toAccountId = tr.toAccountId,
                    toAccountName = accountNames[tr.toAccountId] ?: "Account",
                    amountPoisha = tr.amountPoisha,
                    timestampUtc = tr.timestampUtc,
                    note = tr.note,
                    createdAt = tr.createdAt,
                    updatedAt = tr.updatedAt
                )
            )
        }
        for (adj in adjustments) {
            items.add(
                LedgerItem.Adjustment(
                    id = adj.id,
                    accountId = adj.accountId,
                    accountName = accountNames[adj.accountId] ?: "Account",
                    deltaPoisha = adj.deltaPoisha,
                    reason = adj.reason,
                    timestampUtc = adj.timestampUtc,
                    createdAt = adj.createdAt,
                    updatedAt = adj.updatedAt
                )
            )
        }
        items.sortWith(compareByDescending<LedgerItem> { it.timestampUtc }.thenBy { it.id })
        items
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeCurrentBudgets(): Flow<List<BudgetProgress>> {
        return prefsStore.preferencesFlow.flatMapLatest { prefs ->
            val period = BudgetPeriodCalculator.currentPeriod(configuredStartDay = prefs.budgetStartDay)
            observeBudgetsForPeriod(period.periodKey, prefs.budgetStartDay)
        }
    }

    fun observeBudgetsForPeriod(periodKey: String, startDay: Int): Flow<List<BudgetProgress>> {
        val period = BudgetPeriodCalculator.periodForKey(periodKey, startDay)
        return combine(
            budgetDao.observeBudgetsForPeriod(periodKey),
            transactionDao.observePeriodTotals(period.startUtcMillis, period.endUtcExclusiveMillis),
            transactionDao.observeExpenseByCategory(period.startUtcMillis, period.endUtcExclusiveMillis),
            categoriesFlow
        ) { budgets, totals, byCat, categories ->
            val spendByCat = byCat.associate { it.categoryId to it.totalPoisha }
            val catMap = categories.associateBy { it.id }
            budgets.map { b ->
                val spent = if (b.categoryId == null) {
                    totals.expensePoisha
                } else {
                    spendByCat[b.categoryId] ?: 0L
                }
                val cat = b.categoryId?.let { catMap[it] }
                BudgetProgress(
                    budgetId = b.id,
                    periodKey = periodKey,
                    categoryId = b.categoryId,
                    categoryNameEn = cat?.nameEn,
                    categoryNameBn = cat?.nameBn,
                    categoryColorHex = cat?.colorHex,
                    limitPoisha = b.limitPoisha,
                    spentPoisha = spent
                )
            }
        }
    }

    suspend fun ensureSeedCategories() {
        SeedData.seedDefaultCategories(categoryDao)
    }

    // ---- Accounts ----
    suspend fun createAccount(
        name: String,
        type: AccountType,
        openingBalancePoisha: Long,
        notes: String = "",
        includeInTotal: Boolean = true
    ): AccountEntity {
        val now = System.currentTimeMillis()
        val entity = AccountEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            type = type,
            currency = "BDT",
            openingBalancePoisha = openingBalancePoisha,
            notes = notes.trim(),
            includeInTotal = includeInTotal,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        accountDao.upsert(entity)
        val prefs = prefsStore.current()
        if (prefs.defaultAccountId.isBlank()) {
            prefsStore.setDefaultAccountId(entity.id)
        }
        return entity
    }

    suspend fun updateAccount(
        id: String,
        name: String,
        type: AccountType,
        openingBalancePoisha: Long,
        notes: String,
        includeInTotal: Boolean
    ) {
        val existing = accountDao.getById(id) ?: return
        accountDao.upsert(
            existing.copy(
                name = name.trim(),
                type = type,
                openingBalancePoisha = openingBalancePoisha,
                notes = notes.trim(),
                includeInTotal = includeInTotal,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setAccountArchived(id: String, archived: Boolean) {
        val existing = accountDao.getById(id) ?: return
        accountDao.upsert(existing.copy(isArchived = archived, updatedAt = System.currentTimeMillis()))
    }

    // ---- Transactions ----
    suspend fun saveTransaction(
        existingId: String? = null,
        kind: TransactionKind,
        accountId: String,
        categoryId: String,
        amountPoisha: Long,
        timestampUtc: Long,
        note: String = "",
        merchant: String = "",
        tags: String = "",
        paymentMethod: String = "",
        receiptUri: String? = null,
        recurringOccurrenceId: String? = null
    ): Result<Pair<TransactionEntity, List<BudgetNotificationEvent>>> {
        if (amountPoisha <= 0L) {
            return Result.failure(IllegalArgumentException("Amount must be positive"))
        }
        val account = accountDao.getById(accountId)
            ?: return Result.failure(IllegalArgumentException("Invalid account"))
        if (account.deletedAt != null) {
            return Result.failure(IllegalArgumentException("Deleted account"))
        }
        val category = categoryDao.getById(categoryId)
            ?: return Result.failure(IllegalArgumentException("Invalid category"))
        if (category.deletedAt != null) {
            return Result.failure(IllegalArgumentException("Deleted category"))
        }

        val now = System.currentTimeMillis()
        val prev = existingId?.let { transactionDao.getById(it) }
        val entity = TransactionEntity(
            id = prev?.id ?: UUID.randomUUID().toString(),
            kind = kind,
            accountId = accountId,
            categoryId = categoryId,
            amountPoisha = amountPoisha,
            currency = "BDT",
            timestampUtc = timestampUtc,
            note = note.trim(),
            merchant = merchant.trim(),
            tags = tags.trim(),
            paymentMethod = paymentMethod.trim(),
            receiptUri = receiptUri,
            recurringOccurrenceId = recurringOccurrenceId ?: prev?.recurringOccurrenceId,
            createdAt = prev?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null
        )

        val notifications = db.withTransaction {
            transactionDao.upsert(entity)
            if (kind == TransactionKind.EXPENSE) {
                evaluateBudgetThresholds(timestampUtc)
            } else {
                emptyList()
            }
        }
        return Result.success(entity to notifications)
    }

    suspend fun duplicateTransaction(transactionId: String): Result<Pair<TransactionEntity, List<BudgetNotificationEvent>>> {
        val original = transactionDao.getById(transactionId)
            ?: return Result.failure(IllegalArgumentException("Transaction not found"))
        val now = System.currentTimeMillis()
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            timestampUtc = now,
            recurringOccurrenceId = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null
        )
        val notifications = db.withTransaction {
            transactionDao.upsert(copy)
            if (copy.kind == TransactionKind.EXPENSE) {
                evaluateBudgetThresholds(now)
            } else {
                emptyList()
            }
        }
        return Result.success(copy to notifications)
    }

    suspend fun softDeleteTransaction(id: String) {
        transactionDao.softDelete(id, System.currentTimeMillis())
    }

    // ---- Atomic Transfers ----
    suspend fun saveTransfer(
        existingId: String? = null,
        fromAccountId: String,
        toAccountId: String,
        amountPoisha: Long,
        timestampUtc: Long,
        note: String = ""
    ): Result<TransferEntity> {
        if (fromAccountId == toAccountId) {
            return Result.failure(IllegalArgumentException("Source and destination accounts cannot match"))
        }
        if (amountPoisha <= 0L) {
            return Result.failure(IllegalArgumentException("Transfer amount must be positive"))
        }

        val now = System.currentTimeMillis()
        val result = db.withTransaction {
            val from = accountDao.getById(fromAccountId)
            val to = accountDao.getById(toAccountId)
            if (from == null || from.deletedAt != null || to == null || to.deletedAt != null) {
                return@withTransaction Result.failure<TransferEntity>(
                    IllegalArgumentException("Invalid source or destination account")
                )
            }
            val prev = existingId?.let { transferDao.getById(it) }
            val entity = TransferEntity(
                id = prev?.id ?: UUID.randomUUID().toString(),
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amountPoisha = amountPoisha,
                currency = "BDT",
                timestampUtc = timestampUtc,
                note = note.trim(),
                createdAt = prev?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null
            )
            transferDao.upsert(entity)
            Result.success(entity)
        }
        return result
    }

    suspend fun softDeleteTransfer(id: String) {
        transferDao.softDelete(id, System.currentTimeMillis())
    }

    // ---- Balance Adjustments ----
    suspend fun createBalanceAdjustment(
        accountId: String,
        deltaPoisha: Long,
        reason: String,
        timestampUtc: Long = System.currentTimeMillis()
    ): Result<BalanceAdjustmentEntity> {
        if (deltaPoisha == 0L) {
            return Result.failure(IllegalArgumentException("Adjustment delta cannot be zero"))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("Audit reason is required"))
        }
        val account = accountDao.getById(accountId)
            ?: return Result.failure(IllegalArgumentException("Invalid account"))
        if (account.deletedAt != null) {
            return Result.failure(IllegalArgumentException("Deleted account"))
        }

        val now = System.currentTimeMillis()
        val entity = BalanceAdjustmentEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            deltaPoisha = deltaPoisha,
            currency = "BDT",
            reason = reason.trim(),
            timestampUtc = timestampUtc,
            createdAt = now,
            updatedAt = now
        )
        adjustmentDao.upsert(entity)
        return Result.success(entity)
    }

    suspend fun softDeleteAdjustment(id: String) {
        adjustmentDao.softDelete(id, System.currentTimeMillis())
    }

    // ---- Categories ----
    suspend fun saveCategory(
        existingId: String? = null,
        type: CategoryType,
        nameEn: String,
        nameBn: String,
        iconKey: String,
        colorHex: Long
    ): CategoryEntity {
        val now = System.currentTimeMillis()
        val prev = existingId?.let { categoryDao.getById(it) }
        val entity = CategoryEntity(
            id = prev?.id ?: UUID.randomUUID().toString(),
            type = prev?.type ?: type,
            nameEn = nameEn.trim(),
            nameBn = nameBn.trim().ifBlank { nameEn.trim() },
            iconKey = iconKey,
            colorHex = colorHex,
            isSystem = prev?.isSystem ?: false,
            isArchived = prev?.isArchived ?: false,
            sortOrder = prev?.sortOrder ?: 100,
            createdAt = prev?.createdAt ?: now,
            updatedAt = now
        )
        categoryDao.upsert(entity)
        return entity
    }

    suspend fun setCategoryArchived(id: String, archived: Boolean) {
        val prev = categoryDao.getById(id) ?: return
        categoryDao.upsert(prev.copy(isArchived = archived, updatedAt = System.currentTimeMillis()))
    }

    // ---- Budgets & Thresholds ----
    suspend fun saveBudget(
        periodKey: String,
        categoryId: String?,
        limitPoisha: Long
    ): Result<Pair<BudgetEntity, List<BudgetNotificationEvent>>> {
        if (limitPoisha <= 0L) {
            return Result.failure(IllegalArgumentException("Budget limit must be positive"))
        }
        val now = System.currentTimeMillis()
        val result = db.withTransaction {
            val existing = budgetDao.findBudget(periodKey, categoryId)
            val entity = BudgetEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                periodKey = periodKey,
                categoryId = categoryId,
                limitPoisha = limitPoisha,
                currency = "BDT",
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null
            )
            budgetDao.upsert(entity)
            val prefs = prefsStore.current()
            val period = BudgetPeriodCalculator.periodForKey(periodKey, prefs.budgetStartDay)
            val notifications = evaluateBudgetThresholds(period.startUtcMillis)
            entity to notifications
        }
        return Result.success(result)
    }

    suspend fun softDeleteBudget(id: String) {
        budgetDao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun copyBudgets(fromPeriodKey: String, toPeriodKey: String): Int {
        val source = budgetDao.getBudgetsForPeriod(fromPeriodKey)
        if (source.isEmpty()) return 0
        val now = System.currentTimeMillis()
        var copied = 0
        db.withTransaction {
            for (b in source) {
                val existingTarget = budgetDao.findBudget(toPeriodKey, b.categoryId)
                val target = BudgetEntity(
                    id = existingTarget?.id ?: UUID.randomUUID().toString(),
                    periodKey = toPeriodKey,
                    categoryId = b.categoryId,
                    limitPoisha = b.limitPoisha,
                    currency = "BDT",
                    createdAt = existingTarget?.createdAt ?: now,
                    updatedAt = now
                )
                budgetDao.upsert(target)
                copied++
            }
        }
        return copied
    }

    suspend fun evaluateBudgetThresholds(referenceTimestampUtc: Long): List<BudgetNotificationEvent> {
        val prefs = prefsStore.current()
        val localDate = Instant.ofEpochMilli(referenceTimestampUtc)
            .atZone(BudgetPeriodCalculator.DHAKA_ZONE)
            .toLocalDate()
        val period = BudgetPeriodCalculator.periodForDate(localDate, prefs.budgetStartDay)
        val budgets = budgetDao.getBudgetsForPeriod(period.periodKey)
        if (budgets.isEmpty()) return emptyList()

        val totals = transactionDao.getPeriodTotals(period.startUtcMillis, period.endUtcExclusiveMillis)
        val spendByCat = transactionDao.getExpenseByCategory(period.startUtcMillis, period.endUtcExclusiveMillis)
            .associate { it.categoryId to it.totalPoisha }
        val categories = categoryDao.getAllIncludingDeleted().associateBy { it.id }

        val events = mutableListOf<BudgetNotificationEvent>()
        val now = System.currentTimeMillis()

        for (budget in budgets) {
            if (budget.limitPoisha <= 0L) continue
            val spent = if (budget.categoryId == null) {
                totals.expensePoisha
            } else {
                spendByCat[budget.categoryId] ?: 0L
            }
            val pct = (spent * 100L) / budget.limitPoisha
            val cat = budget.categoryId?.let { categories[it] }

            for (threshold in listOf(50, 80, 100)) {
                if (pct >= threshold) {
                    val alreadyLogged = alertHistoryDao.findAlert(budget.id, period.periodKey, threshold)
                    if (alreadyLogged == null) {
                        val alert = AlertHistoryEntity(
                            id = UUID.randomUUID().toString(),
                            budgetId = budget.id,
                            periodKey = period.periodKey,
                            thresholdPercent = threshold,
                            title = "${cat?.nameEn ?: "Overall"} Budget $threshold%",
                            message = "Spent $spent of ${budget.limitPoisha} poisha in ${period.periodKey}",
                            triggeredAt = now,
                            updatedAt = now
                        )
                        val rowId = alertHistoryDao.insertIgnore(alert)
                        if (rowId != -1L) {
                            events.add(
                                BudgetNotificationEvent(
                                    budgetId = budget.id,
                                    categoryNameEn = cat?.nameEn,
                                    categoryNameBn = cat?.nameBn,
                                    thresholdPercent = threshold,
                                    spentPoisha = spent,
                                    limitPoisha = budget.limitPoisha
                                )
                            )
                        }
                    }
                }
            }
        }
        return events
    }

    // ---- Savings Goals & Earmark Allocations ----
    suspend fun saveSavingsGoal(
        existingId: String? = null,
        name: String,
        preset: GoalPreset,
        targetAmountPoisha: Long,
        targetDateEpochDay: Long? = null,
        notes: String = "",
        status: GoalStatus = GoalStatus.ACTIVE
    ): Result<SavingsGoalEntity> {
        if (targetAmountPoisha <= 0L || name.isBlank()) {
            return Result.failure(IllegalArgumentException("Goal name and positive target required"))
        }
        val now = System.currentTimeMillis()
        val prev = existingId?.let { savingsGoalDao.getGoalById(it) }
        val entity = SavingsGoalEntity(
            id = prev?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            preset = preset,
            targetAmountPoisha = targetAmountPoisha,
            targetDateEpochDay = targetDateEpochDay,
            status = status,
            notes = notes.trim(),
            currency = "BDT",
            createdAt = prev?.createdAt ?: now,
            updatedAt = now
        )
        savingsGoalDao.upsertGoal(entity)
        return Result.success(entity)
    }

    /**
     * Earmarks funds ALREADY HELD in [accountId] to [goalId].
     * Does NOT change the account's real ledger balance or create income/expense.
     */
    suspend fun allocateToGoal(
        goalId: String,
        accountId: String,
        amountPoisha: Long,
        note: String = ""
    ): Result<SavingsAllocationEntity> {
        if (amountPoisha <= 0L) {
            return Result.failure(IllegalArgumentException("Allocation amount must be positive"))
        }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val goal = savingsGoalDao.getGoalById(goalId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Goal not found"))
            if (goal.deletedAt != null || goal.status != GoalStatus.ACTIVE) {
                return@withTransaction Result.failure(IllegalArgumentException("Goal is not active"))
            }
            val accountRow = accountDao.getAccountBalance(accountId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Account not found"))
            val unreserved = accountRow.ledgerBalancePoisha - accountRow.earmarkedPoisha.coerceAtLeast(0L)
            if (amountPoisha > unreserved) {
                return@withTransaction Result.failure(
                    IllegalStateException("Exceeds unreserved available balance")
                )
            }
            val allocation = SavingsAllocationEntity(
                id = UUID.randomUUID().toString(),
                goalId = goalId,
                accountId = accountId,
                direction = AllocationDirection.ALLOCATE,
                amountPoisha = amountPoisha,
                note = note.trim(),
                timestampUtc = now,
                createdAt = now,
                updatedAt = now
            )
            savingsGoalDao.upsertAllocation(allocation)
            Result.success(allocation)
        }
    }

    suspend fun releaseFromGoal(
        goalId: String,
        accountId: String,
        amountPoisha: Long,
        note: String = ""
    ): Result<SavingsAllocationEntity> {
        if (amountPoisha <= 0L) {
            return Result.failure(IllegalArgumentException("Release amount must be positive"))
        }
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val goal = savingsGoalDao.getGoalById(goalId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Goal not found"))
            val currentReserved = savingsGoalDao.getGoalReservedPoisha(goalId)
            if (amountPoisha > currentReserved) {
                return@withTransaction Result.failure(
                    IllegalStateException("Cannot release more than currently reserved")
                )
            }
            val allocation = SavingsAllocationEntity(
                id = UUID.randomUUID().toString(),
                goalId = goalId,
                accountId = accountId,
                direction = AllocationDirection.RELEASE,
                amountPoisha = amountPoisha,
                note = note.trim(),
                timestampUtc = now,
                createdAt = now,
                updatedAt = now
            )
            savingsGoalDao.upsertAllocation(allocation)
            Result.success(allocation)
        }
    }

    suspend fun softDeleteGoal(goalId: String) {
        savingsGoalDao.softDeleteGoal(goalId, System.currentTimeMillis())
    }

    // ---- Recurring Templates & Idempotent Reconciliation ----
    suspend fun saveRecurringTemplate(
        existingId: String? = null,
        title: String,
        kind: TransactionKind,
        accountId: String,
        categoryId: String,
        amountPoisha: Long,
        frequency: RecurrenceFrequency,
        startDate: LocalDate,
        endDate: LocalDate?,
        autoPost: Boolean,
        reminderEnabled: Boolean,
        isEnabled: Boolean,
        note: String = ""
    ): Result<RecurringTemplateEntity> {
        if (title.isBlank() || amountPoisha <= 0L) {
            return Result.failure(IllegalArgumentException("Title and positive amount required"))
        }
        val now = System.currentTimeMillis()
        val prev = existingId?.let { recurringDao.getTemplateById(it) }
        val entity = RecurringTemplateEntity(
            id = prev?.id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            kind = kind,
            accountId = accountId,
            categoryId = categoryId,
            amountPoisha = amountPoisha,
            frequency = frequency,
            startDateEpochDay = startDate.toEpochDay(),
            endDateEpochDay = endDate?.toEpochDay(),
            nextDueDateEpochDay = prev?.nextDueDateEpochDay ?: startDate.toEpochDay(),
            autoPost = autoPost,
            reminderEnabled = reminderEnabled,
            isEnabled = isEnabled,
            note = note.trim(),
            createdAt = prev?.createdAt ?: now,
            updatedAt = now
        )
        recurringDao.upsertTemplate(entity)
        return Result.success(entity)
    }

    suspend fun setRecurringEnabled(id: String, enabled: Boolean) {
        val prev = recurringDao.getTemplateById(id) ?: return
        recurringDao.upsertTemplate(prev.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    suspend fun softDeleteRecurringTemplate(id: String) {
        recurringDao.softDeleteTemplate(id, System.currentTimeMillis())
    }

    /**
     * Reconciles any missed or due recurring occurrences up to [today] in Asia/Dhaka.
     * Uses unique [idempotencyKey] constraint so retries or restarts never duplicate entries.
     */
    suspend fun reconcileDueRecurrences(
        today: LocalDate = LocalDate.now(BudgetPeriodCalculator.DHAKA_ZONE)
    ): List<RecurringOccurrenceEntity> {
        val createdOrUpdated = mutableListOf<RecurringOccurrenceEntity>()
        val now = System.currentTimeMillis()

        db.withTransaction {
            val templates = recurringDao.getEnabledTemplates()
            for (tpl in templates) {
                val startDate = LocalDate.ofEpochDay(tpl.startDateEpochDay)
                val nextDue = LocalDate.ofEpochDay(tpl.nextDueDateEpochDay)
                val endDate = tpl.endDateEpochDay?.let { LocalDate.ofEpochDay(it) }

                val dueDates = RecurrenceScheduler.dueDatesUpTo(
                    startDate = startDate,
                    nextDueDate = nextDue,
                    endDate = endDate,
                    upToDate = today,
                    frequency = tpl.frequency
                )
                if (dueDates.isEmpty()) continue

                var latestCursor = nextDue
                for (dueDate in dueDates) {
                    val epochDay = dueDate.toEpochDay()
                    val key = RecurrenceScheduler.idempotencyKey(tpl.id, epochDay)
                    val existing = recurringDao.findByIdempotencyKey(key)
                    if (existing == null) {
                        val occId = UUID.randomUUID().toString()
                        val scheduledInstant = dueDate.atTime(tpl.preferredHour, tpl.preferredMinute)
                            .atZone(BudgetPeriodCalculator.DHAKA_ZONE)
                            .toInstant()
                            .toEpochMilli()

                        var postedTxId: String? = null
                        val status = if (tpl.autoPost) {
                            val txId = UUID.randomUUID().toString()
                            val tx = TransactionEntity(
                                id = txId,
                                kind = tpl.kind,
                                accountId = tpl.accountId,
                                categoryId = tpl.categoryId,
                                amountPoisha = tpl.amountPoisha,
                                currency = "BDT",
                                timestampUtc = scheduledInstant,
                                note = tpl.note.ifBlank { tpl.title },
                                merchant = tpl.title,
                                recurringOccurrenceId = occId,
                                createdAt = now,
                                updatedAt = now
                            )
                            transactionDao.upsert(tx)
                            if (tpl.kind == TransactionKind.EXPENSE) {
                                evaluateBudgetThresholds(scheduledInstant)
                            }
                            postedTxId = txId
                            OccurrenceStatus.POSTED
                        } else {
                            OccurrenceStatus.PENDING_CONFIRMATION
                        }

                        val occurrence = RecurringOccurrenceEntity(
                            id = occId,
                            templateId = tpl.id,
                            scheduledDateEpochDay = epochDay,
                            idempotencyKey = key,
                            status = status,
                            transactionId = postedTxId,
                            processedAt = if (tpl.autoPost) now else null,
                            createdAt = now,
                            updatedAt = now
                        )
                        val inserted = recurringDao.insertOccurrenceIgnore(occurrence)
                        if (inserted != -1L) {
                            createdOrUpdated.add(occurrence)
                        }
                    }
                    latestCursor = RecurrenceScheduler.nextDate(dueDate, startDate, tpl.frequency)
                }

                recurringDao.upsertTemplate(
                    tpl.copy(
                        nextDueDateEpochDay = latestCursor.toEpochDay(),
                        updatedAt = now
                    )
                )
            }
        }
        return createdOrUpdated
    }

    suspend fun confirmRecurringOccurrence(occurrenceId: String): Result<TransactionEntity> {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val occ = recurringDao.getOccurrenceById(occurrenceId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Occurrence not found"))
            if (occ.status == OccurrenceStatus.POSTED && occ.transactionId != null) {
                val existingTx = transactionDao.getById(occ.transactionId)
                if (existingTx != null) return@withTransaction Result.success(existingTx)
            }
            val tpl = recurringDao.getTemplateById(occ.templateId)
                ?: return@withTransaction Result.failure(IllegalArgumentException("Template not found"))
            val dueDate = LocalDate.ofEpochDay(occ.scheduledDateEpochDay)
            val scheduledInstant = dueDate.atTime(tpl.preferredHour, tpl.preferredMinute)
                .atZone(BudgetPeriodCalculator.DHAKA_ZONE)
                .toInstant()
                .toEpochMilli()

            val tx = TransactionEntity(
                id = UUID.randomUUID().toString(),
                kind = tpl.kind,
                accountId = tpl.accountId,
                categoryId = tpl.categoryId,
                amountPoisha = tpl.amountPoisha,
                currency = "BDT",
                timestampUtc = scheduledInstant,
                note = tpl.note.ifBlank { tpl.title },
                merchant = tpl.title,
                recurringOccurrenceId = occ.id,
                createdAt = now,
                updatedAt = now
            )
            transactionDao.upsert(tx)
            recurringDao.updateOccurrence(
                occ.copy(
                    status = OccurrenceStatus.POSTED,
                    transactionId = tx.id,
                    processedAt = now,
                    updatedAt = now
                )
            )
            if (tpl.kind == TransactionKind.EXPENSE) {
                evaluateBudgetThresholds(scheduledInstant)
            }
            Result.success(tx)
        }
    }

    suspend fun skipRecurringOccurrence(occurrenceId: String) {
        val occ = recurringDao.getOccurrenceById(occurrenceId) ?: return
        val now = System.currentTimeMillis()
        recurringDao.updateOccurrence(
            occ.copy(
                status = OccurrenceStatus.SKIPPED,
                processedAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun clearAllLocalData() {
        db.withTransaction {
            alertHistoryDao.deleteAllPhysical()
            recurringDao.deleteAllOccurrencesPhysical()
            recurringDao.deleteAllTemplatesPhysical()
            savingsGoalDao.deleteAllAllocationsPhysical()
            savingsGoalDao.deleteAllGoalsPhysical()
            budgetDao.deleteAllPhysical()
            adjustmentDao.deleteAllPhysical()
            transferDao.deleteAllPhysical()
            transactionDao.deleteAllPhysical()
            accountDao.deleteAllPhysical()
            categoryDao.deleteAllPhysical()
            backupMetadataDao.deleteAllPhysical()
            SeedData.seedDefaultCategories(categoryDao)
        }
        prefsStore.setDefaultAccountId("")
    }

    suspend fun recordBackupMetadata(entry: BackupMetadataEntity) {
        backupMetadataDao.insert(entry)
    }
}
