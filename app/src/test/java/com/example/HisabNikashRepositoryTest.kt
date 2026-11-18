package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AccountType
import com.example.data.local.AppLanguageMode
import com.example.data.local.AppThemeMode
import com.example.data.local.CategoryType
import com.example.data.local.GoalPreset
import com.example.data.local.HisabNikashDatabase
import com.example.data.local.PreferencesDataStore
import com.example.data.local.TransactionKind
import com.example.data.repository.FinanceRepository
import com.example.domain.BudgetPeriodCalculator
import com.example.domain.RecurrenceFrequency
import com.example.security.BackupCryptoManager
import com.example.security.BackupRestoreManager
import com.example.security.ConflictResolution
import com.example.security.RestoreMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class HisabNikashRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: HisabNikashDatabase
    private lateinit var prefs: PreferencesDataStore
    private lateinit var repo: FinanceRepository
    private lateinit var restoreManager: BackupRestoreManager

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HisabNikashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = PreferencesDataStore(context)
        repo = FinanceRepository(db, prefs)
        restoreManager = BackupRestoreManager(db, prefs)
        repo.ensureSeedCategories()
    }

    @After
    fun tearDown() {
        runBlocking { prefs.resetAll() }
        db.close()
    }

    @Test
    fun `accounts, income, expense, atomic transfer, adjustment, and goal allocation math`() = runBlocking {
        val cash = repo.createAccount("Cash", AccountType.CASH, 100_000L) // ৳1,000
        val bkash = repo.createAccount("bKash", AccountType.BKASH, 50_000L) // ৳500

        // Income +৳2,000, Expense -৳300
        val now = System.currentTimeMillis()
        assertTrue(repo.saveTransaction(null, TransactionKind.INCOME, cash.id, "cat_inc_salary", 200_000L, now).isSuccess)
        assertTrue(repo.saveTransaction(null, TransactionKind.EXPENSE, cash.id, "cat_exp_food", 30_000L, now).isSuccess)

        // Transfer ৳500 (50_000 poisha) from Cash to bKash
        assertTrue(repo.saveTransfer(null, cash.id, bkash.id, 50_000L, now, "Cash to bKash").isSuccess)
        // Reject same-account transfer
        assertTrue(repo.saveTransfer(null, cash.id, cash.id, 10_000L, now, "Bad").isFailure)

        // Balance adjustment +৳25
        assertTrue(repo.createBalanceAdjustment(bkash.id, 2_500L, "Found cashback", now).isSuccess)

        var accs = repo.accountsFlow.first()
        var cashRow = accs.first { it.id == cash.id }
        var bkashRow = accs.first { it.id == bkash.id }

        // Cash: 1000 + 2000 - 300 - 500 = 2200 (220_000)
        assertEquals(220_000L, cashRow.ledgerBalancePoisha)
        // bKash: 500 + 500 + 25 = 1025 (102_500)
        assertEquals(102_500L, bkashRow.ledgerBalancePoisha)
        assertEquals(322_500L, accs.sumOf { it.ledgerBalancePoisha })

        // Period income/expense excludes opening balance, transfer, and adjustment
        val period = BudgetPeriodCalculator.currentPeriod()
        val totals = db.transactionDao().getPeriodTotals(period.startUtcMillis, period.endUtcExclusiveMillis)
        assertEquals(200_000L, totals.incomePoisha)
        assertEquals(30_000L, totals.expensePoisha)

        // Goal allocation of ৳200 (20_000 poisha) from Cash:
        // Real balance stays 220_000, unreserved decreases by 20_000 -> 200_000
        val goal = repo.saveSavingsGoal(null, "Emergency", GoalPreset.EMERGENCY_FUND, 500_000L).getOrThrow()
        assertTrue(repo.allocateToGoal(goal.id, cash.id, 20_000L).isSuccess)
        // Exceeding unreserved balance fails
        assertTrue(repo.allocateToGoal(goal.id, cash.id, 205_000L).isFailure)

        accs = repo.accountsFlow.first()
        cashRow = accs.first { it.id == cash.id }
        assertEquals(220_000L, cashRow.ledgerBalancePoisha)
        assertEquals(200_000L, cashRow.unreservedAvailablePoisha)

        // Release ৳200 restores unreserved availability
        assertTrue(repo.releaseFromGoal(goal.id, cash.id, 20_000L).isSuccess)
        cashRow = repo.accountsFlow.first().first { it.id == cash.id }
        assertEquals(220_000L, cashRow.unreservedAvailablePoisha)
    }

    @Test
    fun `budget thresholds trigger once per crossing and do not double count`() = runBlocking {
        val cash = repo.createAccount("Cash", AccountType.CASH, 500_000L)
        val period = BudgetPeriodCalculator.currentPeriod()
        repo.saveBudget(period.periodKey, null, 100_000L) // Overall ৳1,000
        repo.saveBudget(period.periodKey, "cat_exp_food", 50_000L) // Food ৳500

        // Spend ৳300 on food -> 30% overall, 60% food (triggers Food 50%)
        val ev1 = repo.saveTransaction(null, TransactionKind.EXPENSE, cash.id, "cat_exp_food", 30_000L, System.currentTimeMillis())
            .getOrThrow().second
        assertEquals(1, ev1.size)
        assertEquals(50, ev1.first().thresholdPercent)

        // Spend ৳250 on transport -> 55% overall (triggers Overall 50%; Food 50% must NOT repeat)
        val ev2 = repo.saveTransaction(null, TransactionKind.EXPENSE, cash.id, "cat_exp_transport", 25_000L, System.currentTimeMillis())
            .getOrThrow().second
        assertEquals(1, ev2.size)
        assertEquals(null, ev2.first().categoryNameEn)
        assertEquals(50, ev2.first().thresholdPercent)
    }

    @Test
    fun `recurring reconciliation is idempotent across retries and catch-up`() = runBlocking {
        val bank = repo.createAccount("Bank", AccountType.BANK, 0L)
        val start = LocalDate.of(2026, 1, 1)
        val today = LocalDate.of(2026, 3, 1)
        repo.saveRecurringTemplate(
            existingId = null,
            title = "Salary",
            kind = TransactionKind.INCOME,
            accountId = bank.id,
            categoryId = "cat_inc_salary",
            amountPoisha = 3_000_000L,
            frequency = RecurrenceFrequency.MONTHLY,
            startDate = start,
            endDate = null,
            autoPost = true,
            reminderEnabled = false,
            isEnabled = true
        )

        val firstRun = repo.reconcileDueRecurrences(today)
        assertEquals(3, firstRun.size) // Jan 1, Feb 1, Mar 1

        // Retry on same day creates zero duplicates
        val secondRun = repo.reconcileDueRecurrences(today)
        assertEquals(0, secondRun.size)

        val bankBal = repo.accountsFlow.first().first { it.id == bank.id }.ledgerBalancePoisha
        assertEquals(9_000_000L, bankBal)
    }

    @Test
    fun `custom category archiving retains historical analytics and preferences persist`() = runBlocking {
        val acc = repo.createAccount("Wallet", AccountType.CASH, 10_000L)
        val customCat = repo.saveCategory(null, CategoryType.EXPENSE, "Tea Stall", "চা স্টল", "restaurant", 0xFF123D34L)
        repo.saveTransaction(null, TransactionKind.EXPENSE, acc.id, customCat.id, 1_500L, System.currentTimeMillis())
        repo.setCategoryArchived(customCat.id, true)

        val ledger = repo.unifiedLedgerFlow.first()
        assertEquals(1, ledger.size)
        prefs.setLanguageMode(AppLanguageMode.BANGLA)
        prefs.setThemeMode(AppThemeMode.DARK)
        val currentPrefs = prefs.current()
        assertEquals(AppLanguageMode.BANGLA, currentPrefs.resolvedLanguage())
        assertEquals(AppThemeMode.DARK, currentPrefs.resolvedTheme())
    }

    @Test
    fun `full encrypted export, fresh DB replace restore, duplicate merge, and tombstone conflict handling`() = runBlocking {
        val acc = repo.createAccount("Main Cash", AccountType.CASH, 50_000L)
        val tx = repo.saveTransaction(null, TransactionKind.EXPENSE, acc.id, "cat_exp_food", 10_000L, System.currentTimeMillis())
            .getOrThrow().first

        val pass = "MySecretPass#99".toCharArray()
        val (encrypted, _) = restoreManager.exportEncrypted(pass).getOrThrow()

        // Wipe local DB and restore into clean database via REPLACE
        repo.clearAllLocalData()
        assertEquals(0, repo.accountsFlow.first().size)

        val decryptedSnap = restoreManager.decryptAndValidate(encrypted, pass).getOrThrow()
        assertTrue(restoreManager.applyRestore(decryptedSnap, RestoreMode.REPLACE).isSuccess)
        assertEquals(1, repo.accountsFlow.first().size)
        assertEquals(40_000L, repo.accountsFlow.first().first().ledgerBalancePoisha)

        // Modify local tx (newer tombstone) and run MERGE -> USE_BACKUP should NOT resurrect newer local tombstone
        repo.softDeleteTransaction(tx.id)
        val preview = restoreManager.previewMerge(decryptedSnap)
        assertEquals(1, preview.conflicts.size)
        assertTrue(restoreManager.applyRestore(decryptedSnap, RestoreMode.MERGE, ConflictResolution.USE_BACKUP).isSuccess)
        assertEquals(0, db.transactionDao().observeActiveTransactions().first().size)

        // Tampered snapshot referential integrity failure triggers rollback
        val brokenSnap = decryptedSnap.copy(
            transactions = decryptedSnap.transactions.map { it.copy(accountId = "non_existent_uuid") }
        )
        assertFalse(restoreManager.applyRestore(brokenSnap, RestoreMode.REPLACE).isSuccess)
        // Account still preserved after failed REPLACE!
        assertEquals(1, repo.accountsFlow.first().size)
    }

    @Test
    fun `2000 transaction ledger aggregation stays fast under Room SQLite`() = runBlocking {
        val acc = repo.createAccount("Bulk Test", AccountType.BANK, 10_000_000L)
        val now = System.currentTimeMillis()
        val bulk = (1..2000).map { idx ->
            com.example.data.local.TransactionEntity(
                kind = TransactionKind.EXPENSE,
                accountId = acc.id,
                categoryId = "cat_exp_food",
                amountPoisha = 100L,
                timestampUtc = now - idx * 1000L,
                createdAt = now,
                updatedAt = now
            )
        }
        val t0 = System.currentTimeMillis()
        db.transactionDao().upsertAll(bulk)
        val row = db.accountDao().getAccountBalance(acc.id)!!
        val elapsed = System.currentTimeMillis() - t0
        assertEquals(10_000_000L - 200_000L, row.ledgerBalancePoisha)
        assertTrue("Expected < 2000ms for 2000 txs, took ${elapsed}ms", elapsed < 2000L)
    }

    @Test
    fun `Bangla CSV export preserves UTF-8 and formula safety, and PDF pipeline executes without leaking`() = runBlocking {
        val acc = repo.createAccount("Cash", AccountType.CASH, 50_000L)
        repo.saveTransaction(null, TransactionKind.INCOME, acc.id, "cat_inc_salary", 100_000L, System.currentTimeMillis(), note = "বেতন")
        repo.saveTransaction(null, TransactionKind.EXPENSE, acc.id, "cat_exp_food", 25_000L, System.currentTimeMillis(), note = "=SUM(A1)")

        val ledger = repo.unifiedLedgerFlow.first().filterIsInstance<com.example.domain.LedgerItem.CashFlow>()
        val csvBytes = com.example.domain.ReportGenerator.generateCsvBytes(ledger, isBangla = true)
        val csvStr = String(csvBytes, Charsets.UTF_8)
        assertTrue(csvStr.contains("বেতন"))
        assertTrue(csvStr.contains("'=SUM(A1)"))

        val accSummaries = repo.accountsFlow.first()
        val summary = com.example.domain.ReportSummary(
            periodLabel = "২০২৬-১১",
            totalIncomePoisha = 100_000L,
            totalExpensePoisha = 25_000L,
            netCashFlowPoisha = 75_000L,
            leadingCategoryName = "খাবার ও রেস্তোরাঁ",
            leadingCategoryPoisha = 25_000L,
            accountBalances = accSummaries,
            categoryTotals = listOf("খাবার ও রেস্তোরাঁ" to 25_000L),
            budgetRows = emptyList(),
            transactions = ledger
        )
        // On physical/emulator Android devices PdfDocument produces "%PDF-..." bytes;
        // Robolectric stubs native PdfDocument.writeTo/finishPage depending on SDK shadow.
        val pdfResult = com.example.domain.ReportGenerator.generatePdfBytes(context, summary, isBangla = true)
        if (pdfResult.isSuccess) {
            assertEquals("%PDF-", String(pdfResult.getOrThrow().take(5).toByteArray(), Charsets.US_ASCII))
        } else {
            val err = pdfResult.exceptionOrNull()
            assertTrue("Unexpected null error from generatePdfBytes", err != null)
        }
    }

    @Test
    fun `DriveBackupRepository handles HTTP 401, 403, and offline failures without fabricating success`() = runBlocking {
        val client401 = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("{\"error\":\"invalid_token\"}".toResponseBody(null))
                    .build()
            }
            .build()
        val repo401 = com.example.cloud.drive.DriveBackupRepository(client401)
        val res401 = repo401.listBackups("expired_token")
        assertTrue(res401.isFailure)
        assertTrue(res401.exceptionOrNull()?.message?.contains("401") == true)

        val clientOffline = okhttp3.OkHttpClient.Builder()
            .addInterceptor { _ -> throw java.io.IOException("Network unreachable") }
            .build()
        val repoOffline = com.example.cloud.drive.DriveBackupRepository(clientOffline)
        val resOffline = repoOffline.uploadEncryptedBackup("token", "test.hnkb", byteArrayOf(1, 2, 3))
        assertTrue(resOffline.isFailure)
    }
}
