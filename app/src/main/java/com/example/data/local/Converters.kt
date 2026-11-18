package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.RecurrenceFrequency

class Converters {
    @TypeConverter fun fromAccountType(v: AccountType): String = v.name
    @TypeConverter fun toAccountType(v: String): AccountType = AccountType.valueOf(v)

    @TypeConverter fun fromCategoryType(v: CategoryType): String = v.name
    @TypeConverter fun toCategoryType(v: String): CategoryType = CategoryType.valueOf(v)

    @TypeConverter fun fromTransactionKind(v: TransactionKind): String = v.name
    @TypeConverter fun toTransactionKind(v: String): TransactionKind = TransactionKind.valueOf(v)

    @TypeConverter fun fromGoalPreset(v: GoalPreset): String = v.name
    @TypeConverter fun toGoalPreset(v: String): GoalPreset = GoalPreset.valueOf(v)

    @TypeConverter fun fromGoalStatus(v: GoalStatus): String = v.name
    @TypeConverter fun toGoalStatus(v: String): GoalStatus = GoalStatus.valueOf(v)

    @TypeConverter fun fromAllocationDirection(v: AllocationDirection): String = v.name
    @TypeConverter fun toAllocationDirection(v: String): AllocationDirection = AllocationDirection.valueOf(v)

    @TypeConverter fun fromRecurrenceFrequency(v: RecurrenceFrequency): String = v.name
    @TypeConverter fun toRecurrenceFrequency(v: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(v)

    @TypeConverter fun fromOccurrenceStatus(v: OccurrenceStatus): String = v.name
    @TypeConverter fun toOccurrenceStatus(v: String): OccurrenceStatus = OccurrenceStatus.valueOf(v)

    @TypeConverter fun fromBackupProvider(v: BackupProvider): String = v.name
    @TypeConverter fun toBackupProvider(v: String): BackupProvider = BackupProvider.valueOf(v)

    @TypeConverter fun fromBackupRecordStatus(v: BackupRecordStatus): String = v.name
    @TypeConverter fun toBackupRecordStatus(v: String): BackupRecordStatus = BackupRecordStatus.valueOf(v)
}
