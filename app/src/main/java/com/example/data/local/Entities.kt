package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.RecurrenceFrequency
import java.util.UUID

enum class AccountType {
    CASH,
    BANK,
    BKASH,
    NAGAD,
    ROCKET,
    SAVINGS,
    OTHER
}

enum class CategoryType {
    INCOME,
    EXPENSE
}

enum class TransactionKind {
    INCOME,
    EXPENSE
}

enum class GoalPreset {
    EMERGENCY_FUND,
    PHONE,
    LAPTOP,
    EDUCATION,
    TRAVEL,
    HOME,
    CUSTOM
}

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED
}

enum class AllocationDirection {
    ALLOCATE,
    RELEASE
}

enum class OccurrenceStatus {
    PENDING_CONFIRMATION,
    POSTED,
    SKIPPED
}

enum class BackupProvider {
    LOCAL_FILE,
    GOOGLE_DRIVE
}

enum class BackupRecordStatus {
    VERIFIED,
    FAILED
}

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["isArchived"]),
        Index(value = ["deletedAt"])
    ]
)
data class AccountEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AccountType,
    val currency: String = "BDT",
    val openingBalancePoisha: Long,
    val notes: String = "",
    val includeInTotal: Boolean = true,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["type", "isArchived"]),
        Index(value = ["deletedAt"])
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: CategoryType,
    val nameEn: String,
    val nameBn: String,
    val iconKey: String,
    val colorHex: Long,
    val isSystem: Boolean = false,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["timestampUtc"]),
        Index(value = ["kind", "timestampUtc"]),
        Index(value = ["deletedAt"]),
        Index(value = ["recurringOccurrenceId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: TransactionKind,
    val accountId: String,
    val categoryId: String,
    val amountPoisha: Long,
    val currency: String = "BDT",
    val timestampUtc: Long,
    val note: String = "",
    val merchant: String = "",
    val tags: String = "",
    val paymentMethod: String = "",
    val receiptUri: String? = null,
    val recurringOccurrenceId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromAccountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["fromAccountId"]),
        Index(value = ["toAccountId"]),
        Index(value = ["timestampUtc"]),
        Index(value = ["deletedAt"])
    ]
)
data class TransferEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val fromAccountId: String,
    val toAccountId: String,
    val amountPoisha: Long,
    val currency: String = "BDT",
    val timestampUtc: Long,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "balance_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["timestampUtc"]),
        Index(value = ["deletedAt"])
    ]
)
data class BalanceAdjustmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val accountId: String,
    val deltaPoisha: Long,
    val currency: String = "BDT",
    val reason: String,
    val timestampUtc: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["periodKey"]),
        Index(value = ["categoryId"]),
        Index(value = ["deletedAt"])
    ]
)
data class BudgetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val periodKey: String,
    val categoryId: String?, // null = Overall monthly budget
    val limitPoisha: Long,
    val currency: String = "BDT",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "savings_goals",
    indices = [
        Index(value = ["status"]),
        Index(value = ["deletedAt"])
    ]
)
data class SavingsGoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val preset: GoalPreset,
    val targetAmountPoisha: Long,
    val targetDateEpochDay: Long? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val notes: String = "",
    val currency: String = "BDT",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "savings_allocations",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["goalId"]),
        Index(value = ["accountId"]),
        Index(value = ["timestampUtc"]),
        Index(value = ["deletedAt"])
    ]
)
data class SavingsAllocationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val goalId: String,
    val accountId: String,
    val direction: AllocationDirection,
    val amountPoisha: Long,
    val note: String = "",
    val timestampUtc: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "recurring_templates",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["categoryId"]),
        Index(value = ["isEnabled", "nextDueDateEpochDay"]),
        Index(value = ["deletedAt"])
    ]
)
data class RecurringTemplateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: TransactionKind,
    val accountId: String,
    val categoryId: String,
    val amountPoisha: Long,
    val frequency: RecurrenceFrequency,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val nextDueDateEpochDay: Long,
    val preferredHour: Int = 9,
    val preferredMinute: Int = 0,
    val autoPost: Boolean = false,
    val reminderEnabled: Boolean = true,
    val isEnabled: Boolean = true,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "recurring_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["templateId"]),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["status"]),
        Index(value = ["deletedAt"])
    ]
)
data class RecurringOccurrenceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val templateId: String,
    val scheduledDateEpochDay: Long,
    val idempotencyKey: String,
    val status: OccurrenceStatus,
    val transactionId: String? = null,
    val processedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "alert_history",
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budgetId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["budgetId", "periodKey", "thresholdPercent"], unique = true),
        Index(value = ["triggeredAt"]),
        Index(value = ["deletedAt"])
    ]
)
data class AlertHistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val budgetId: String,
    val periodKey: String,
    val thresholdPercent: Int, // 50, 80, 100
    val title: String,
    val message: String,
    val triggeredAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "backup_metadata",
    indices = [Index(value = ["createdAt"])]
)
data class BackupMetadataEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val provider: BackupProvider,
    val fileName: String,
    val remoteFileId: String? = null,
    val schemaVersion: Int,
    val recordCount: Int,
    val status: BackupRecordStatus,
    val errorMessage: String? = null,
    val createdAt: Long
)
