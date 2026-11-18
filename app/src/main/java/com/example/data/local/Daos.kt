package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class AccountBalanceRow(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    val openingBalancePoisha: Long,
    val notes: String,
    val includeInTotal: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val ledgerBalancePoisha: Long,
    val earmarkedPoisha: Long
)

data class PeriodTotalsRow(
    val incomePoisha: Long,
    val expensePoisha: Long
)

data class CategorySpendRow(
    val categoryId: String,
    val totalPoisha: Long
)

@Dao
interface AccountDao {
    @Query(
        """
        SELECT 
            a.id, a.name, a.type, a.currency, a.openingBalancePoisha, a.notes,
            a.includeInTotal, a.isArchived, a.createdAt, a.updatedAt,
            (
                a.openingBalancePoisha
                + COALESCE((SELECT SUM(t.amountPoisha) FROM transactions t WHERE t.accountId = a.id AND t.kind = 'INCOME' AND t.deletedAt IS NULL), 0)
                - COALESCE((SELECT SUM(t.amountPoisha) FROM transactions t WHERE t.accountId = a.id AND t.kind = 'EXPENSE' AND t.deletedAt IS NULL), 0)
                + COALESCE((SELECT SUM(tr.amountPoisha) FROM transfers tr WHERE tr.toAccountId = a.id AND tr.deletedAt IS NULL), 0)
                - COALESCE((SELECT SUM(tr.amountPoisha) FROM transfers tr WHERE tr.fromAccountId = a.id AND tr.deletedAt IS NULL), 0)
                + COALESCE((SELECT SUM(adj.deltaPoisha) FROM balance_adjustments adj WHERE adj.accountId = a.id AND adj.deletedAt IS NULL), 0)
            ) AS ledgerBalancePoisha,
            (
                COALESCE((SELECT SUM(sa.amountPoisha) FROM savings_allocations sa JOIN savings_goals sg ON sg.id = sa.goalId WHERE sa.accountId = a.id AND sa.direction = 'ALLOCATE' AND sa.deletedAt IS NULL AND sg.deletedAt IS NULL AND sg.status = 'ACTIVE'), 0)
                - COALESCE((SELECT SUM(sa.amountPoisha) FROM savings_allocations sa JOIN savings_goals sg ON sg.id = sa.goalId WHERE sa.accountId = a.id AND sa.direction = 'RELEASE' AND sa.deletedAt IS NULL AND sg.deletedAt IS NULL AND sg.status = 'ACTIVE'), 0)
            ) AS earmarkedPoisha
        FROM accounts a
        WHERE a.deletedAt IS NULL
        ORDER BY a.isArchived ASC, a.createdAt ASC
        """
    )
    fun observeAccountBalances(): Flow<List<AccountBalanceRow>>

    @Query(
        """
        SELECT 
            a.id, a.name, a.type, a.currency, a.openingBalancePoisha, a.notes,
            a.includeInTotal, a.isArchived, a.createdAt, a.updatedAt,
            (
                a.openingBalancePoisha
                + COALESCE((SELECT SUM(t.amountPoisha) FROM transactions t WHERE t.accountId = a.id AND t.kind = 'INCOME' AND t.deletedAt IS NULL), 0)
                - COALESCE((SELECT SUM(t.amountPoisha) FROM transactions t WHERE t.accountId = a.id AND t.kind = 'EXPENSE' AND t.deletedAt IS NULL), 0)
                + COALESCE((SELECT SUM(tr.amountPoisha) FROM transfers tr WHERE tr.toAccountId = a.id AND tr.deletedAt IS NULL), 0)
                - COALESCE((SELECT SUM(tr.amountPoisha) FROM transfers tr WHERE tr.fromAccountId = a.id AND tr.deletedAt IS NULL), 0)
                + COALESCE((SELECT SUM(adj.deltaPoisha) FROM balance_adjustments adj WHERE adj.accountId = a.id AND adj.deletedAt IS NULL), 0)
            ) AS ledgerBalancePoisha,
            (
                COALESCE((SELECT SUM(sa.amountPoisha) FROM savings_allocations sa JOIN savings_goals sg ON sg.id = sa.goalId WHERE sa.accountId = a.id AND sa.direction = 'ALLOCATE' AND sa.deletedAt IS NULL AND sg.deletedAt IS NULL AND sg.status = 'ACTIVE'), 0)
                - COALESCE((SELECT SUM(sa.amountPoisha) FROM savings_allocations sa JOIN savings_goals sg ON sg.id = sa.goalId WHERE sa.accountId = a.id AND sa.direction = 'RELEASE' AND sa.deletedAt IS NULL AND sg.deletedAt IS NULL AND sg.status = 'ACTIVE'), 0)
            ) AS earmarkedPoisha
        FROM accounts a
        WHERE a.id = :accountId AND a.deletedAt IS NULL
        """
    )
    suspend fun getAccountBalance(accountId: String): AccountBalanceRow?

    @Query("SELECT * FROM accounts")
    suspend fun getAllIncludingDeleted(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllPhysical()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY isArchived ASC, sortOrder ASC, nameEn ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAllIncludingDeleted(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun deleteAllPhysical()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY timestampUtc DESC, createdAt DESC")
    fun observeActiveTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL AND timestampUtc >= :startUtc AND timestampUtc < :endUtc ORDER BY timestampUtc DESC")
    fun observeTransactionsInRange(startUtc: Long, endUtc: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amountPoisha ELSE 0 END), 0) AS incomePoisha,
            COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amountPoisha ELSE 0 END), 0) AS expensePoisha
        FROM transactions
        WHERE deletedAt IS NULL AND timestampUtc >= :startUtc AND timestampUtc < :endUtc
        """
    )
    fun observePeriodTotals(startUtc: Long, endUtc: Long): Flow<PeriodTotalsRow>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amountPoisha ELSE 0 END), 0) AS incomePoisha,
            COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amountPoisha ELSE 0 END), 0) AS expensePoisha
        FROM transactions
        WHERE deletedAt IS NULL AND timestampUtc >= :startUtc AND timestampUtc < :endUtc
        """
    )
    suspend fun getPeriodTotals(startUtc: Long, endUtc: Long): PeriodTotalsRow

    @Query(
        """
        SELECT categoryId, COALESCE(SUM(amountPoisha), 0) AS totalPoisha
        FROM transactions
        WHERE deletedAt IS NULL AND kind = 'EXPENSE' AND timestampUtc >= :startUtc AND timestampUtc < :endUtc
        GROUP BY categoryId
        ORDER BY totalPoisha DESC
        """
    )
    fun observeExpenseByCategory(startUtc: Long, endUtc: Long): Flow<List<CategorySpendRow>>

    @Query(
        """
        SELECT categoryId, COALESCE(SUM(amountPoisha), 0) AS totalPoisha
        FROM transactions
        WHERE deletedAt IS NULL AND kind = 'EXPENSE' AND timestampUtc >= :startUtc AND timestampUtc < :endUtc
        GROUP BY categoryId
        """
    )
    suspend fun getExpenseByCategory(startUtc: Long, endUtc: Long): List<CategorySpendRow>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions")
    suspend fun getAllIncludingDeleted(): List<TransactionEntity>

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllPhysical()
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers WHERE deletedAt IS NULL ORDER BY timestampUtc DESC, createdAt DESC")
    fun observeActiveTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getById(id: String): TransferEntity?

    @Query("SELECT * FROM transfers")
    suspend fun getAllIncludingDeleted(): List<TransferEntity>

    @Upsert
    suspend fun upsert(transfer: TransferEntity)

    @Upsert
    suspend fun upsertAll(transfers: List<TransferEntity>)

    @Query("UPDATE transfers SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("DELETE FROM transfers")
    suspend fun deleteAllPhysical()
}

@Dao
interface BalanceAdjustmentDao {
    @Query("SELECT * FROM balance_adjustments WHERE deletedAt IS NULL ORDER BY timestampUtc DESC")
    fun observeActiveAdjustments(): Flow<List<BalanceAdjustmentEntity>>

    @Query("SELECT * FROM balance_adjustments WHERE id = :id")
    suspend fun getById(id: String): BalanceAdjustmentEntity?

    @Query("SELECT * FROM balance_adjustments")
    suspend fun getAllIncludingDeleted(): List<BalanceAdjustmentEntity>

    @Upsert
    suspend fun upsert(adjustment: BalanceAdjustmentEntity)

    @Upsert
    suspend fun upsertAll(adjustments: List<BalanceAdjustmentEntity>)

    @Query("UPDATE balance_adjustments SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("DELETE FROM balance_adjustments")
    suspend fun deleteAllPhysical()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL AND periodKey = :periodKey ORDER BY categoryId IS NOT NULL, createdAt ASC")
    fun observeBudgetsForPeriod(periodKey: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL AND periodKey = :periodKey")
    suspend fun getBudgetsForPeriod(periodKey: String): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL AND periodKey = :periodKey AND ((:categoryId IS NULL AND categoryId IS NULL) OR categoryId = :categoryId) LIMIT 1")
    suspend fun findBudget(periodKey: String, categoryId: String?): BudgetEntity?

    @Query("SELECT * FROM budgets")
    suspend fun getAllIncludingDeleted(): List<BudgetEntity>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Upsert
    suspend fun upsertAll(budgets: List<BudgetEntity>)

    @Query("UPDATE budgets SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllPhysical()
}

@Dao
interface AlertHistoryDao {
    @Query("SELECT * FROM alert_history WHERE deletedAt IS NULL ORDER BY triggeredAt DESC")
    fun observeAlertHistory(): Flow<List<AlertHistoryEntity>>

    @Query("SELECT * FROM alert_history WHERE budgetId = :budgetId AND periodKey = :periodKey AND thresholdPercent = :thresholdPercent AND deletedAt IS NULL LIMIT 1")
    suspend fun findAlert(budgetId: String, periodKey: String, thresholdPercent: Int): AlertHistoryEntity?

    @Query("SELECT * FROM alert_history")
    suspend fun getAllIncludingDeleted(): List<AlertHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(alert: AlertHistoryEntity): Long

    @Upsert
    suspend fun upsertAll(alerts: List<AlertHistoryEntity>)

    @Query("DELETE FROM alert_history")
    suspend fun deleteAllPhysical()
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE deletedAt IS NULL ORDER BY status ASC, createdAt DESC")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_allocations WHERE deletedAt IS NULL ORDER BY timestampUtc DESC")
    fun observeAllocations(): Flow<List<SavingsAllocationEntity>>

    @Query(
        """
        SELECT 
            COALESCE(SUM(CASE WHEN direction = 'ALLOCATE' THEN amountPoisha ELSE -amountPoisha END), 0)
        FROM savings_allocations
        WHERE goalId = :goalId AND deletedAt IS NULL
        """
    )
    suspend fun getGoalReservedPoisha(goalId: String): Long

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getGoalById(id: String): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals")
    suspend fun getAllGoalsIncludingDeleted(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_allocations")
    suspend fun getAllAllocationsIncludingDeleted(): List<SavingsAllocationEntity>

    @Upsert
    suspend fun upsertGoal(goal: SavingsGoalEntity)

    @Upsert
    suspend fun upsertAllocation(allocation: SavingsAllocationEntity)

    @Upsert
    suspend fun upsertAllGoals(goals: List<SavingsGoalEntity>)

    @Upsert
    suspend fun upsertAllAllocations(allocations: List<SavingsAllocationEntity>)

    @Query("UPDATE savings_goals SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteGoal(id: String, deletedAt: Long)

    @Query("DELETE FROM savings_allocations")
    suspend fun deleteAllAllocationsPhysical()

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllGoalsPhysical()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_templates WHERE deletedAt IS NULL ORDER BY isEnabled DESC, nextDueDateEpochDay ASC")
    fun observeTemplates(): Flow<List<RecurringTemplateEntity>>

    @Query("SELECT * FROM recurring_templates WHERE deletedAt IS NULL AND isEnabled = 1")
    suspend fun getEnabledTemplates(): List<RecurringTemplateEntity>

    @Query("SELECT * FROM recurring_occurrences WHERE deletedAt IS NULL ORDER BY scheduledDateEpochDay DESC, createdAt DESC")
    fun observeOccurrences(): Flow<List<RecurringOccurrenceEntity>>

    @Query("SELECT * FROM recurring_occurrences WHERE deletedAt IS NULL AND status = 'PENDING_CONFIRMATION' ORDER BY scheduledDateEpochDay ASC")
    fun observePendingOccurrences(): Flow<List<RecurringOccurrenceEntity>>

    @Query("SELECT * FROM recurring_occurrences WHERE idempotencyKey = :key LIMIT 1")
    suspend fun findByIdempotencyKey(key: String): RecurringOccurrenceEntity?

    @Query("SELECT * FROM recurring_templates WHERE id = :id")
    suspend fun getTemplateById(id: String): RecurringTemplateEntity?

    @Query("SELECT * FROM recurring_occurrences WHERE id = :id")
    suspend fun getOccurrenceById(id: String): RecurringOccurrenceEntity?

    @Query("SELECT * FROM recurring_templates")
    suspend fun getAllTemplatesIncludingDeleted(): List<RecurringTemplateEntity>

    @Query("SELECT * FROM recurring_occurrences")
    suspend fun getAllOccurrencesIncludingDeleted(): List<RecurringOccurrenceEntity>

    @Upsert
    suspend fun upsertTemplate(template: RecurringTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrenceIgnore(occurrence: RecurringOccurrenceEntity): Long

    @Update
    suspend fun updateOccurrence(occurrence: RecurringOccurrenceEntity)

    @Upsert
    suspend fun upsertAllTemplates(templates: List<RecurringTemplateEntity>)

    @Upsert
    suspend fun upsertAllOccurrences(occurrences: List<RecurringOccurrenceEntity>)

    @Query("UPDATE recurring_templates SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteTemplate(id: String, deletedAt: Long)

    @Query("DELETE FROM recurring_occurrences")
    suspend fun deleteAllOccurrencesPhysical()

    @Query("DELETE FROM recurring_templates")
    suspend fun deleteAllTemplatesPhysical()
}

@Dao
interface BackupMetadataDao {
    @Query("SELECT * FROM backup_metadata ORDER BY createdAt DESC LIMIT 50")
    fun observeHistory(): Flow<List<BackupMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BackupMetadataEntity)

    @Query("DELETE FROM backup_metadata")
    suspend fun deleteAllPhysical()
}
