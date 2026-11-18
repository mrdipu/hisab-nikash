package com.example.domain

import com.example.data.local.AccountBalanceRow
import com.example.data.local.AccountType
import com.example.data.local.CategoryEntity
import com.example.data.local.GoalPreset
import com.example.data.local.GoalStatus
import com.example.data.local.TransactionKind
import java.time.LocalDate

data class AccountSummary(
    val id: String,
    val name: String,
    val type: AccountType,
    val openingBalancePoisha: Long,
    val ledgerBalancePoisha: Long,
    val earmarkedPoisha: Long,
    val notes: String,
    val includeInTotal: Boolean,
    val isArchived: Boolean
) {
    val unreservedAvailablePoisha: Long
        get() = ledgerBalancePoisha - earmarkedPoisha

    companion object {
        fun fromRow(row: AccountBalanceRow): AccountSummary = AccountSummary(
            id = row.id,
            name = row.name,
            type = row.type,
            openingBalancePoisha = row.openingBalancePoisha,
            ledgerBalancePoisha = row.ledgerBalancePoisha,
            earmarkedPoisha = row.earmarkedPoisha.coerceAtLeast(0L),
            notes = row.notes,
            includeInTotal = row.includeInTotal,
            isArchived = row.isArchived
        )
    }
}

sealed class LedgerItem {
    abstract val id: String
    abstract val timestampUtc: Long
    abstract val amountPoisha: Long
    abstract val note: String

    data class CashFlow(
        override val id: String,
        val kind: TransactionKind,
        val accountId: String,
        val accountName: String,
        val categoryId: String,
        val categoryNameEn: String,
        val categoryNameBn: String,
        val categoryColorHex: Long,
        val categoryIconKey: String,
        override val amountPoisha: Long,
        override val timestampUtc: Long,
        override val note: String,
        val merchant: String,
        val tags: String,
        val paymentMethod: String,
        val receiptUri: String?,
        val createdAt: Long,
        val updatedAt: Long
    ) : LedgerItem()

    data class Transfer(
        override val id: String,
        val fromAccountId: String,
        val fromAccountName: String,
        val toAccountId: String,
        val toAccountName: String,
        override val amountPoisha: Long,
        override val timestampUtc: Long,
        override val note: String,
        val createdAt: Long,
        val updatedAt: Long
    ) : LedgerItem()

    data class Adjustment(
        override val id: String,
        val accountId: String,
        val accountName: String,
        val deltaPoisha: Long,
        val reason: String,
        override val timestampUtc: Long,
        val createdAt: Long,
        val updatedAt: Long
    ) : LedgerItem() {
        override val amountPoisha: Long get() = deltaPoisha
        override val note: String get() = reason
    }
}

data class BudgetProgress(
    val budgetId: String,
    val periodKey: String,
    val categoryId: String?,
    val categoryNameEn: String?,
    val categoryNameBn: String?,
    val categoryColorHex: Long?,
    val limitPoisha: Long,
    val spentPoisha: Long
) {
    val remainingPoisha: Long get() = limitPoisha - spentPoisha
    val percentUsed: Float get() = if (limitPoisha > 0L) (spentPoisha.toFloat() / limitPoisha.toFloat()) * 100f else 0f
    val isOverspent: Boolean get() = spentPoisha > limitPoisha
}

data class SavingsGoalSummary(
    val id: String,
    val name: String,
    val preset: GoalPreset,
    val targetAmountPoisha: Long,
    val reservedPoisha: Long,
    val targetDateEpochDay: Long?,
    val status: GoalStatus,
    val notes: String
) {
    val remainingPoisha: Long get() = (targetAmountPoisha - reservedPoisha).coerceAtLeast(0L)
    val progressFraction: Float
        get() = if (targetAmountPoisha > 0L) (reservedPoisha.toFloat() / targetAmountPoisha.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class AnalyticsRangePreset {
    LAST_7_DAYS,
    LAST_30_DAYS,
    THIS_MONTH,
    LAST_MONTH,
    LAST_3_MONTHS,
    THIS_YEAR,
    CUSTOM
}

data class CategoryBreakdown(
    val category: CategoryEntity,
    val totalPoisha: Long,
    val sharePercent: Float
)

data class PeriodBarPoint(
    val label: String,
    val incomePoisha: Long,
    val expensePoisha: Long
)

data class DailyPoint(
    val date: LocalDate,
    val expensePoisha: Long
)

data class BudgetNotificationEvent(
    val budgetId: String,
    val categoryNameEn: String?,
    val categoryNameBn: String?,
    val thresholdPercent: Int,
    val spentPoisha: Long,
    val limitPoisha: Long
)
