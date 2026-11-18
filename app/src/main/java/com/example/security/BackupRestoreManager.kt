package com.example.security

import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.data.local.AccountEntity
import com.example.data.local.AccountType
import com.example.data.local.AlertHistoryEntity
import com.example.data.local.AllocationDirection
import com.example.data.local.BalanceAdjustmentEntity
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
import com.example.domain.RecurrenceFrequency
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

class BackupRestoreManager(
    private val db: HisabNikashDatabase,
    private val prefsStore: PreferencesDataStore
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    suspend fun buildSnapshot(): Pair<BackupSnapshot, String> {
        val prefs = prefsStore.current()
        val snapshot = db.withTransaction {
            val accounts = db.accountDao().getAllIncludingDeleted().map {
                BackupAccount(
                    id = it.id, name = it.name, type = it.type.name, currency = it.currency,
                    openingBalancePoisha = it.openingBalancePoisha, notes = it.notes,
                    includeInTotal = it.includeInTotal, isArchived = it.isArchived,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val categories = db.categoryDao().getAllIncludingDeleted().map {
                BackupCategory(
                    id = it.id, type = it.type.name, nameEn = it.nameEn, nameBn = it.nameBn,
                    iconKey = it.iconKey, colorHex = it.colorHex, isSystem = it.isSystem,
                    isArchived = it.isArchived, sortOrder = it.sortOrder,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val transactions = db.transactionDao().getAllIncludingDeleted().map {
                BackupTransaction(
                    id = it.id, kind = it.kind.name, accountId = it.accountId,
                    categoryId = it.categoryId, amountPoisha = it.amountPoisha, currency = it.currency,
                    timestampUtc = it.timestampUtc, note = it.note, merchant = it.merchant,
                    tags = it.tags, paymentMethod = it.paymentMethod, receiptUri = it.receiptUri,
                    recurringOccurrenceId = it.recurringOccurrenceId,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val transfers = db.transferDao().getAllIncludingDeleted().map {
                BackupTransfer(
                    id = it.id, fromAccountId = it.fromAccountId, toAccountId = it.toAccountId,
                    amountPoisha = it.amountPoisha, currency = it.currency, timestampUtc = it.timestampUtc,
                    note = it.note, createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val adjustments = db.balanceAdjustmentDao().getAllIncludingDeleted().map {
                BackupAdjustment(
                    id = it.id, accountId = it.accountId, deltaPoisha = it.deltaPoisha,
                    currency = it.currency, reason = it.reason, timestampUtc = it.timestampUtc,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val budgets = db.budgetDao().getAllIncludingDeleted().map {
                BackupBudget(
                    id = it.id, periodKey = it.periodKey, categoryId = it.categoryId,
                    limitPoisha = it.limitPoisha, currency = it.currency,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val alerts = db.alertHistoryDao().getAllIncludingDeleted().map {
                BackupAlertHistory(
                    id = it.id, budgetId = it.budgetId, periodKey = it.periodKey,
                    thresholdPercent = it.thresholdPercent, title = it.title, message = it.message,
                    triggeredAt = it.triggeredAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val goals = db.savingsGoalDao().getAllGoalsIncludingDeleted().map {
                BackupSavingsGoal(
                    id = it.id, name = it.name, preset = it.preset.name,
                    targetAmountPoisha = it.targetAmountPoisha, targetDateEpochDay = it.targetDateEpochDay,
                    status = it.status.name, notes = it.notes, currency = it.currency,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val allocations = db.savingsGoalDao().getAllAllocationsIncludingDeleted().map {
                BackupSavingsAllocation(
                    id = it.id, goalId = it.goalId, accountId = it.accountId,
                    direction = it.direction.name, amountPoisha = it.amountPoisha, note = it.note,
                    timestampUtc = it.timestampUtc, createdAt = it.createdAt,
                    updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val templates = db.recurringDao().getAllTemplatesIncludingDeleted().map {
                BackupRecurringTemplate(
                    id = it.id, title = it.title, kind = it.kind.name,
                    accountId = it.accountId, categoryId = it.categoryId,
                    amountPoisha = it.amountPoisha, frequency = it.frequency.name,
                    startDateEpochDay = it.startDateEpochDay, endDateEpochDay = it.endDateEpochDay,
                    nextDueDateEpochDay = it.nextDueDateEpochDay,
                    preferredHour = it.preferredHour, preferredMinute = it.preferredMinute,
                    autoPost = it.autoPost, reminderEnabled = it.reminderEnabled,
                    isEnabled = it.isEnabled, note = it.note,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val occurrences = db.recurringDao().getAllOccurrencesIncludingDeleted().map {
                BackupRecurringOccurrence(
                    id = it.id, templateId = it.templateId,
                    scheduledDateEpochDay = it.scheduledDateEpochDay,
                    idempotencyKey = it.idempotencyKey, status = it.status.name,
                    transactionId = it.transactionId, processedAt = it.processedAt,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }

            BackupSnapshot(
                formatVersion = 1,
                roomSchemaVersion = HisabNikashDatabase.CURRENT_SCHEMA_VERSION,
                appVersionName = BuildConfig.VERSION_NAME,
                exportedAtUtc = System.currentTimeMillis(),
                counts = SnapshotCounts(
                    accounts = accounts.size,
                    categories = categories.size,
                    transactions = transactions.size,
                    transfers = transfers.size,
                    adjustments = adjustments.size,
                    budgets = budgets.size,
                    alertHistory = alerts.size,
                    savingsGoals = goals.size,
                    savingsAllocations = allocations.size,
                    recurringTemplates = templates.size,
                    recurringOccurrences = occurrences.size
                ),
                accounts = accounts,
                categories = categories,
                transactions = transactions,
                transfers = transfers,
                adjustments = adjustments,
                budgets = budgets,
                alertHistory = alerts,
                savingsGoals = goals,
                savingsAllocations = allocations,
                recurringTemplates = templates,
                recurringOccurrences = occurrences,
                preferences = prefs
            )
        }
        val encoded = json.encodeToString(snapshot)
        return snapshot to encoded
    }

    fun computeCanonicalHash(snapshot: BackupSnapshot): String {
        val stableCopy = snapshot.copy(exportedAtUtc = 0L)
        val raw = json.encodeToString(stableCopy).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(raw)
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun exportEncrypted(passphrase: CharArray): Result<Pair<ByteArray, BackupSnapshot>> {
        return try {
            val (snapshot, encodedJson) = buildSnapshot()
            val encrypted = BackupCryptoManager.encryptWithPassphrase(
                plaintextUtf8 = encodedJson.toByteArray(Charsets.UTF_8),
                passphrase = passphrase
            )
            Result.success(encrypted to snapshot)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun exportWithKeyBytes(keyBytes: ByteArray, salt: ByteArray): Result<Pair<ByteArray, BackupSnapshot>> {
        return try {
            val (snapshot, encodedJson) = buildSnapshot()
            val encrypted = BackupCryptoManager.encryptWithRawKey(
                plaintextUtf8 = encodedJson.toByteArray(Charsets.UTF_8),
                keyBytes = keyBytes,
                salt = salt
            )
            Result.success(encrypted to snapshot)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun decryptAndValidate(envelopeBytes: ByteArray, passphrase: CharArray): Result<BackupSnapshot> {
        val plainResult = BackupCryptoManager.decryptWithPassphrase(envelopeBytes, passphrase)
        if (plainResult.isFailure) {
            return Result.failure(plainResult.exceptionOrNull()!!)
        }
        return try {
            val rawJson = plainResult.getOrThrow().toString(Charsets.UTF_8)
            val snapshot = json.decodeFromString<BackupSnapshot>(rawJson)
            validateSnapshot(snapshot).getOrThrow()
            Result.success(snapshot)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun validateSnapshot(snapshot: BackupSnapshot): Result<Unit> {
        if (snapshot.formatVersion != 1) {
            return Result.failure(IllegalArgumentException("Unsupported snapshot version ${snapshot.formatVersion}"))
        }
        if (snapshot.roomSchemaVersion > HisabNikashDatabase.CURRENT_SCHEMA_VERSION) {
            return Result.failure(
                IllegalArgumentException("Backup is from a newer schema v${snapshot.roomSchemaVersion}")
            )
        }
        val c = snapshot.counts
        if (c.accounts != snapshot.accounts.size ||
            c.categories != snapshot.categories.size ||
            c.transactions != snapshot.transactions.size ||
            c.transfers != snapshot.transfers.size ||
            c.adjustments != snapshot.adjustments.size ||
            c.budgets != snapshot.budgets.size ||
            c.savingsGoals != snapshot.savingsGoals.size ||
            c.savingsAllocations != snapshot.savingsAllocations.size ||
            c.recurringTemplates != snapshot.recurringTemplates.size ||
            c.recurringOccurrences != snapshot.recurringOccurrences.size
        ) {
            return Result.failure(IllegalArgumentException("Backup record counts do not match payload"))
        }

        val accountIds = snapshot.accounts.map { it.id }.toSet()
        val categoryIds = snapshot.categories.map { it.id }.toSet()
        val budgetIds = snapshot.budgets.map { it.id }.toSet()
        val goalIds = snapshot.savingsGoals.map { it.id }.toSet()
        val templateIds = snapshot.recurringTemplates.map { it.id }.toSet()

        fun validUuidOrSeed(id: String): Boolean {
            if (id.startsWith("cat_")) return true
            return runCatching { UUID.fromString(id) }.isSuccess
        }

        snapshot.accounts.forEach {
            if (!validUuidOrSeed(it.id)) return Result.failure(IllegalArgumentException("Invalid account ID"))
        }
        snapshot.categories.forEach {
            if (!validUuidOrSeed(it.id)) return Result.failure(IllegalArgumentException("Invalid category ID"))
        }
        snapshot.transactions.forEach {
            if (!validUuidOrSeed(it.id) || it.amountPoisha <= 0L) {
                return Result.failure(IllegalArgumentException("Invalid transaction record"))
            }
            if (it.accountId !in accountIds || it.categoryId !in categoryIds) {
                return Result.failure(IllegalArgumentException("Referential integrity error in transaction ${it.id}"))
            }
        }
        snapshot.transfers.forEach {
            if (!validUuidOrSeed(it.id) || it.amountPoisha <= 0L || it.fromAccountId == it.toAccountId) {
                return Result.failure(IllegalArgumentException("Invalid transfer record ${it.id}"))
            }
            if (it.fromAccountId !in accountIds || it.toAccountId !in accountIds) {
                return Result.failure(IllegalArgumentException("Referential integrity error in transfer ${it.id}"))
            }
        }
        snapshot.adjustments.forEach {
            if (!validUuidOrSeed(it.id) || it.deltaPoisha == 0L || it.accountId !in accountIds) {
                return Result.failure(IllegalArgumentException("Invalid adjustment record ${it.id}"))
            }
        }
        snapshot.budgets.forEach {
            if (!validUuidOrSeed(it.id) || it.limitPoisha <= 0L) {
                return Result.failure(IllegalArgumentException("Invalid budget record ${it.id}"))
            }
            if (it.categoryId != null && it.categoryId !in categoryIds) {
                return Result.failure(IllegalArgumentException("Referential integrity error in budget ${it.id}"))
            }
        }
        snapshot.alertHistory.forEach {
            if (it.budgetId !in budgetIds) {
                return Result.failure(IllegalArgumentException("Referential integrity error in alert ${it.id}"))
            }
        }
        snapshot.savingsAllocations.forEach {
            if (it.amountPoisha <= 0L || it.goalId !in goalIds || it.accountId !in accountIds) {
                return Result.failure(IllegalArgumentException("Referential integrity error in allocation ${it.id}"))
            }
        }
        snapshot.recurringTemplates.forEach {
            if (it.amountPoisha <= 0L || it.accountId !in accountIds || it.categoryId !in categoryIds) {
                return Result.failure(IllegalArgumentException("Referential integrity error in recurring template ${it.id}"))
            }
        }
        snapshot.recurringOccurrences.forEach {
            if (it.templateId !in templateIds) {
                return Result.failure(IllegalArgumentException("Referential integrity error in occurrence ${it.id}"))
            }
        }
        return Result.success(Unit)
    }

    suspend fun previewMerge(snapshot: BackupSnapshot): RestorePreview {
        val localAccounts = db.accountDao().getAllIncludingDeleted().associateBy { it.id }
        val localTx = db.transactionDao().getAllIncludingDeleted().associateBy { it.id }
        val localTransfers = db.transferDao().getAllIncludingDeleted().associateBy { it.id }
        val localBudgets = db.budgetDao().getAllIncludingDeleted().associateBy { it.id }
        val localGoals = db.savingsGoalDao().getAllGoalsIncludingDeleted().associateBy { it.id }

        val conflicts = mutableListOf<MergeConflict>()
        var newRecords = 0

        snapshot.accounts.forEach { b ->
            val l = localAccounts[b.id]
            if (l == null) newRecords++
            else if (l.updatedAt != b.updatedAt || l.deletedAt != b.deletedAt || l.openingBalancePoisha != b.openingBalancePoisha || l.name != b.name) {
                conflicts.add(MergeConflict(b.id, "accounts", b.name, l.updatedAt, b.updatedAt))
            }
        }
        snapshot.transactions.forEach { b ->
            val l = localTx[b.id]
            if (l == null) newRecords++
            else if (l.updatedAt != b.updatedAt || l.deletedAt != b.deletedAt || l.amountPoisha != b.amountPoisha || l.note != b.note) {
                conflicts.add(MergeConflict(b.id, "transactions", b.note.ifBlank { b.kind }, l.updatedAt, b.updatedAt))
            }
        }
        snapshot.transfers.forEach { b ->
            val l = localTransfers[b.id]
            if (l == null) newRecords++
            else if (l.updatedAt != b.updatedAt || l.deletedAt != b.deletedAt || l.amountPoisha != b.amountPoisha) {
                conflicts.add(MergeConflict(b.id, "transfers", b.note.ifBlank { "Transfer" }, l.updatedAt, b.updatedAt))
            }
        }
        snapshot.budgets.forEach { b ->
            val l = localBudgets[b.id]
            if (l == null) newRecords++
            else if (l.updatedAt != b.updatedAt || l.deletedAt != b.deletedAt || l.limitPoisha != b.limitPoisha) {
                conflicts.add(MergeConflict(b.id, "budgets", b.periodKey, l.updatedAt, b.updatedAt))
            }
        }
        snapshot.savingsGoals.forEach { b ->
            val l = localGoals[b.id]
            if (l == null) newRecords++
            else if (l.updatedAt != b.updatedAt || l.deletedAt != b.deletedAt || l.targetAmountPoisha != b.targetAmountPoisha) {
                conflicts.add(MergeConflict(b.id, "savings_goals", b.name, l.updatedAt, b.updatedAt))
            }
        }

        val c = snapshot.counts
        val total = c.accounts + c.categories + c.transactions + c.transfers + c.adjustments +
            c.budgets + c.savingsGoals + c.savingsAllocations + c.recurringTemplates + c.recurringOccurrences

        return RestorePreview(
            snapshot = snapshot,
            totalRecordsInBackup = total,
            newRecordsCount = newRecords,
            conflicts = conflicts
        )
    }

    suspend fun applyRestore(
        snapshot: BackupSnapshot,
        mode: RestoreMode,
        conflictResolution: ConflictResolution = ConflictResolution.USE_BACKUP
    ): Result<Unit> {
        return try {
            validateSnapshot(snapshot).getOrThrow()

            val accounts = snapshot.accounts.map {
                AccountEntity(
                    id = it.id, name = it.name, type = AccountType.valueOf(it.type),
                    currency = it.currency, openingBalancePoisha = it.openingBalancePoisha,
                    notes = it.notes, includeInTotal = it.includeInTotal, isArchived = it.isArchived,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val categories = snapshot.categories.map {
                CategoryEntity(
                    id = it.id, type = CategoryType.valueOf(it.type), nameEn = it.nameEn,
                    nameBn = it.nameBn, iconKey = it.iconKey, colorHex = it.colorHex,
                    isSystem = it.isSystem, isArchived = it.isArchived, sortOrder = it.sortOrder,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val txs = snapshot.transactions.map {
                TransactionEntity(
                    id = it.id, kind = TransactionKind.valueOf(it.kind), accountId = it.accountId,
                    categoryId = it.categoryId, amountPoisha = it.amountPoisha, currency = it.currency,
                    timestampUtc = it.timestampUtc, note = it.note, merchant = it.merchant,
                    tags = it.tags, paymentMethod = it.paymentMethod, receiptUri = it.receiptUri,
                    recurringOccurrenceId = it.recurringOccurrenceId,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val transfers = snapshot.transfers.map {
                TransferEntity(
                    id = it.id, fromAccountId = it.fromAccountId, toAccountId = it.toAccountId,
                    amountPoisha = it.amountPoisha, currency = it.currency,
                    timestampUtc = it.timestampUtc, note = it.note,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val adjustments = snapshot.adjustments.map {
                BalanceAdjustmentEntity(
                    id = it.id, accountId = it.accountId, deltaPoisha = it.deltaPoisha,
                    currency = it.currency, reason = it.reason, timestampUtc = it.timestampUtc,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val budgets = snapshot.budgets.map {
                BudgetEntity(
                    id = it.id, periodKey = it.periodKey, categoryId = it.categoryId,
                    limitPoisha = it.limitPoisha, currency = it.currency,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val alerts = snapshot.alertHistory.map {
                AlertHistoryEntity(
                    id = it.id, budgetId = it.budgetId, periodKey = it.periodKey,
                    thresholdPercent = it.thresholdPercent, title = it.title, message = it.message,
                    triggeredAt = it.triggeredAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val goals = snapshot.savingsGoals.map {
                SavingsGoalEntity(
                    id = it.id, name = it.name, preset = GoalPreset.valueOf(it.preset),
                    targetAmountPoisha = it.targetAmountPoisha, targetDateEpochDay = it.targetDateEpochDay,
                    status = GoalStatus.valueOf(it.status), notes = it.notes, currency = it.currency,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val allocations = snapshot.savingsAllocations.map {
                SavingsAllocationEntity(
                    id = it.id, goalId = it.goalId, accountId = it.accountId,
                    direction = AllocationDirection.valueOf(it.direction),
                    amountPoisha = it.amountPoisha, note = it.note,
                    timestampUtc = it.timestampUtc, createdAt = it.createdAt,
                    updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val templates = snapshot.recurringTemplates.map {
                RecurringTemplateEntity(
                    id = it.id, title = it.title, kind = TransactionKind.valueOf(it.kind),
                    accountId = it.accountId, categoryId = it.categoryId,
                    amountPoisha = it.amountPoisha, frequency = RecurrenceFrequency.valueOf(it.frequency),
                    startDateEpochDay = it.startDateEpochDay, endDateEpochDay = it.endDateEpochDay,
                    nextDueDateEpochDay = it.nextDueDateEpochDay,
                    preferredHour = it.preferredHour, preferredMinute = it.preferredMinute,
                    autoPost = it.autoPost, reminderEnabled = it.reminderEnabled,
                    isEnabled = it.isEnabled, note = it.note,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }
            val occurrences = snapshot.recurringOccurrences.map {
                RecurringOccurrenceEntity(
                    id = it.id, templateId = it.templateId,
                    scheduledDateEpochDay = it.scheduledDateEpochDay,
                    idempotencyKey = it.idempotencyKey, status = OccurrenceStatus.valueOf(it.status),
                    transactionId = it.transactionId, processedAt = it.processedAt,
                    createdAt = it.createdAt, updatedAt = it.updatedAt, deletedAt = it.deletedAt
                )
            }

            db.withTransaction {
                if (mode == RestoreMode.REPLACE) {
                    // Delete children before parents to obey RESTRICT foreign keys
                    db.alertHistoryDao().deleteAllPhysical()
                    db.recurringDao().deleteAllOccurrencesPhysical()
                    db.recurringDao().deleteAllTemplatesPhysical()
                    db.savingsGoalDao().deleteAllAllocationsPhysical()
                    db.savingsGoalDao().deleteAllGoalsPhysical()
                    db.budgetDao().deleteAllPhysical()
                    db.balanceAdjustmentDao().deleteAllPhysical()
                    db.transferDao().deleteAllPhysical()
                    db.transactionDao().deleteAllPhysical()
                    db.accountDao().deleteAllPhysical()
                    db.categoryDao().deleteAllPhysical()

                    db.categoryDao().upsertAll(categories)
                    SeedData.seedDefaultCategories(db.categoryDao())
                    db.accountDao().upsertAll(accounts)
                    db.transactionDao().upsertAll(txs)
                    db.transferDao().upsertAll(transfers)
                    db.balanceAdjustmentDao().upsertAll(adjustments)
                    db.budgetDao().upsertAll(budgets)
                    db.alertHistoryDao().upsertAll(alerts)
                    db.savingsGoalDao().upsertAllGoals(goals)
                    db.savingsGoalDao().upsertAllAllocations(allocations)
                    db.recurringDao().upsertAllTemplates(templates)
                    db.recurringDao().upsertAllOccurrences(occurrences)
                } else {
                    // MERGE mode with UUID deduplication, tombstone awareness, and conflict policy
                    fun <T> resolveMerge(
                        localList: List<T>,
                        backupList: List<T>,
                        idOf: (T) -> String,
                        updatedAtOf: (T) -> Long,
                        deletedAtOf: (T) -> Long?,
                        equalContent: (T, T) -> Boolean
                    ): List<T> {
                        val localMap = localList.associateBy(idOf)
                        val result = mutableListOf<T>()
                        for (b in backupList) {
                            val l = localMap[idOf(b)]
                            if (l == null) {
                                result.add(b)
                            } else if (!equalContent(l, b)) {
                                when (conflictResolution) {
                                    ConflictResolution.KEEP_LOCAL, ConflictResolution.SKIP -> result.add(l)
                                    ConflictResolution.USE_BACKUP -> {
                                        // Tombstone-aware: never accidentally resurrect a newer local tombstone
                                        if (l.let(deletedAtOf) != null && deletedAtOf(b) == null && updatedAtOf(l) > updatedAtOf(b)) {
                                            result.add(l)
                                        } else {
                                            result.add(b)
                                        }
                                    }
                                }
                            }
                        }
                        return result
                    }

                    val mergedCats = resolveMerge(
                        db.categoryDao().getAllIncludingDeleted(), categories,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedAccounts = resolveMerge(
                        db.accountDao().getAllIncludingDeleted(), accounts,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedTxs = resolveMerge(
                        db.transactionDao().getAllIncludingDeleted(), txs,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedTransfers = resolveMerge(
                        db.transferDao().getAllIncludingDeleted(), transfers,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedAdj = resolveMerge(
                        db.balanceAdjustmentDao().getAllIncludingDeleted(), adjustments,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedBudgets = resolveMerge(
                        db.budgetDao().getAllIncludingDeleted(), budgets,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedGoals = resolveMerge(
                        db.savingsGoalDao().getAllGoalsIncludingDeleted(), goals,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedAlloc = resolveMerge(
                        db.savingsGoalDao().getAllAllocationsIncludingDeleted(), allocations,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedTpl = resolveMerge(
                        db.recurringDao().getAllTemplatesIncludingDeleted(), templates,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )
                    val mergedOcc = resolveMerge(
                        db.recurringDao().getAllOccurrencesIncludingDeleted(), occurrences,
                        { it.id }, { it.updatedAt }, { it.deletedAt }, { a, b -> a == b }
                    )

                    db.categoryDao().upsertAll(mergedCats)
                    SeedData.seedDefaultCategories(db.categoryDao())
                    db.accountDao().upsertAll(mergedAccounts)
                    db.transactionDao().upsertAll(mergedTxs)
                    db.transferDao().upsertAll(mergedTransfers)
                    db.balanceAdjustmentDao().upsertAll(mergedAdj)
                    db.budgetDao().upsertAll(mergedBudgets)
                    db.alertHistoryDao().upsertAll(alerts)
                    db.savingsGoalDao().upsertAllGoals(mergedGoals)
                    db.savingsGoalDao().upsertAllAllocations(mergedAlloc)
                    db.recurringDao().upsertAllTemplates(mergedTpl)
                    db.recurringDao().upsertAllOccurrences(mergedOcc)
                }
            }

            if (mode == RestoreMode.REPLACE) {
                prefsStore.restoreFromBackup(snapshot.preferences)
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
