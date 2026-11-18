package com.example.security

import com.example.data.local.UserPreferences
import kotlinx.serialization.Serializable

@Serializable
data class BackupSnapshot(
    val formatVersion: Int = 1,
    val roomSchemaVersion: Int,
    val appVersionName: String,
    val exportedAtUtc: Long,
    val counts: SnapshotCounts,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val transfers: List<BackupTransfer>,
    val adjustments: List<BackupAdjustment>,
    val budgets: List<BackupBudget>,
    val alertHistory: List<BackupAlertHistory>,
    val savingsGoals: List<BackupSavingsGoal>,
    val savingsAllocations: List<BackupSavingsAllocation>,
    val recurringTemplates: List<BackupRecurringTemplate>,
    val recurringOccurrences: List<BackupRecurringOccurrence>,
    val preferences: UserPreferences
)

@Serializable
data class SnapshotCounts(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val transfers: Int,
    val adjustments: Int,
    val budgets: Int,
    val alertHistory: Int,
    val savingsGoals: Int,
    val savingsAllocations: Int,
    val recurringTemplates: Int,
    val recurringOccurrences: Int
)

@Serializable
data class BackupAccount(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val openingBalancePoisha: Long,
    val notes: String,
    val includeInTotal: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupCategory(
    val id: String,
    val type: String,
    val nameEn: String,
    val nameBn: String,
    val iconKey: String,
    val colorHex: Long,
    val isSystem: Boolean,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupTransaction(
    val id: String,
    val kind: String,
    val accountId: String,
    val categoryId: String,
    val amountPoisha: Long,
    val currency: String,
    val timestampUtc: Long,
    val note: String,
    val merchant: String,
    val tags: String,
    val paymentMethod: String,
    val receiptUri: String? = null,
    val recurringOccurrenceId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupTransfer(
    val id: String,
    val fromAccountId: String,
    val toAccountId: String,
    val amountPoisha: Long,
    val currency: String,
    val timestampUtc: Long,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupAdjustment(
    val id: String,
    val accountId: String,
    val deltaPoisha: Long,
    val currency: String,
    val reason: String,
    val timestampUtc: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupBudget(
    val id: String,
    val periodKey: String,
    val categoryId: String? = null,
    val limitPoisha: Long,
    val currency: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupAlertHistory(
    val id: String,
    val budgetId: String,
    val periodKey: String,
    val thresholdPercent: Int,
    val title: String,
    val message: String,
    val triggeredAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupSavingsGoal(
    val id: String,
    val name: String,
    val preset: String,
    val targetAmountPoisha: Long,
    val targetDateEpochDay: Long? = null,
    val status: String,
    val notes: String,
    val currency: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupSavingsAllocation(
    val id: String,
    val goalId: String,
    val accountId: String,
    val direction: String,
    val amountPoisha: Long,
    val note: String,
    val timestampUtc: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupRecurringTemplate(
    val id: String,
    val title: String,
    val kind: String,
    val accountId: String,
    val categoryId: String,
    val amountPoisha: Long,
    val frequency: String,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val nextDueDateEpochDay: Long,
    val preferredHour: Int,
    val preferredMinute: Int,
    val autoPost: Boolean,
    val reminderEnabled: Boolean,
    val isEnabled: Boolean,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Serializable
data class BackupRecurringOccurrence(
    val id: String,
    val templateId: String,
    val scheduledDateEpochDay: Long,
    val idempotencyKey: String,
    val status: String,
    val transactionId: String? = null,
    val processedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

enum class RestoreMode {
    REPLACE,
    MERGE
}

enum class ConflictResolution {
    KEEP_LOCAL,
    USE_BACKUP,
    SKIP
}

data class MergeConflict(
    val recordKey: String,
    val tableName: String,
    val summary: String,
    val localUpdatedAt: Long,
    val backupUpdatedAt: Long
)

data class RestorePreview(
    val snapshot: BackupSnapshot,
    val totalRecordsInBackup: Int,
    val newRecordsCount: Int,
    val conflicts: List<MergeConflict>
)
