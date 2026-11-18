package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.local.HisabNikashDatabase
import com.example.data.local.OccurrenceStatus
import com.example.data.local.PreferencesDataStore
import com.example.data.repository.FinanceRepository
import java.util.concurrent.TimeUnit

class RecurringCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = HisabNikashDatabase.getInstance(applicationContext)
        val prefsStore = PreferencesDataStore(applicationContext)
        val repo = FinanceRepository(db, prefsStore)

        return try {
            val prefs = prefsStore.current()
            val newlyCreated = repo.reconcileDueRecurrences()
            if (prefs.recurringRemindersEnabled && newlyCreated.isNotEmpty()) {
                val pendingCount = newlyCreated.count { it.status == OccurrenceStatus.PENDING_CONFIRMATION }
                val postedCount = newlyCreated.count { it.status == OccurrenceStatus.POSTED }
                if (pendingCount > 0 || postedCount > 0) {
                    LocalNotificationHelper.postNotification(
                        context = applicationContext,
                        channelId = LocalNotificationHelper.CHANNEL_RECURRING,
                        notificationId = 2001,
                        title = "হিসাবনিকাশ",
                        body = if (pendingCount > 0) {
                            "$pendingCount recurring bill(s)/income due for confirmation"
                        } else {
                            "$postedCount recurring entry(s) posted"
                        }
                    )
                }
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "hisabnikash_recurring_reconcile"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<RecurringCheckWorker>(12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
