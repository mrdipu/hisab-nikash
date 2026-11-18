package com.example.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.cloud.drive.DriveBackupRepository
import com.example.cloud.drive.DriveConnectionStatus
import com.example.cloud.drive.GoogleAccountManager
import com.example.cloud.drive.GoogleAuthorizationManager
import com.example.data.local.BackupMetadataEntity
import com.example.data.local.BackupProvider
import com.example.data.local.BackupRecordStatus
import com.example.data.local.HisabNikashDatabase
import com.example.data.local.PreferencesDataStore
import com.example.security.BackupCryptoManager
import com.example.security.BackupRestoreManager
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AutomaticBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = HisabNikashDatabase.getInstance(applicationContext)
        val prefsStore = PreferencesDataStore(applicationContext)
        val prefs = prefsStore.current()

        if (!prefs.driveAutoBackupEnabled) {
            return Result.success()
        }

        if (!prefs.rememberBackupKeyOnDevice) {
            // Never upload unencrypted data or pretend automatic backup succeeded without key material.
            if (prefs.backupNotificationsEnabled) {
                LocalNotificationHelper.postNotification(
                    context = applicationContext,
                    channelId = LocalNotificationHelper.CHANNEL_BACKUP,
                    notificationId = 3001,
                    title = "হিসাবনিকাশ",
                    body = "Automatic backup paused: enable 'Remember backup key on this device' or back up manually."
                )
            }
            return Result.success()
        }

        val keyPair = BackupCryptoManager.loadRememberedBackupKey(applicationContext)
        if (keyPair == null) {
            if (prefs.backupNotificationsEnabled) {
                LocalNotificationHelper.postNotification(
                    context = applicationContext,
                    channelId = LocalNotificationHelper.CHANNEL_BACKUP,
                    notificationId = 3002,
                    title = "হিসাবনিকাশ",
                    body = "Automatic backup paused: remembered device key unavailable."
                )
            }
            return Result.success()
        }

        val (keyBytes, salt) = keyPair
        try {
            val authManager = GoogleAuthorizationManager(
                applicationContext,
                GoogleAccountManager(applicationContext)
            )
            val status = authManager.checkOrRequestAuthorization(prefs.driveAccountEmail)
            if (status !is DriveConnectionStatus.Authorized) {
                if (prefs.backupNotificationsEnabled) {
                    LocalNotificationHelper.postNotification(
                        context = applicationContext,
                        channelId = LocalNotificationHelper.CHANNEL_BACKUP,
                        notificationId = 3003,
                        title = "হিসাবনিকাশ",
                        body = "Automatic Drive backup needs interactive sign-in / authorization."
                    )
                }
                return Result.success()
            }

            val restoreManager = BackupRestoreManager(db, prefsStore)
            val (snapshot, _) = restoreManager.buildSnapshot()
            val hash = restoreManager.computeCanonicalHash(snapshot)
            if (hash == prefs.lastDriveSnapshotHash && prefs.lastVerifiedDriveBackupAt > 0L) {
                // Coalesce: no changes since last verified Drive backup.
                return Result.success()
            }

            val exportResult = restoreManager.exportWithKeyBytes(keyBytes, salt)
            if (exportResult.isFailure) {
                return Result.retry()
            }
            val (encryptedBytes, exportedSnapshot) = exportResult.getOrThrow()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val fileName = "hisabnikash_auto_$stamp.hnkb"

            val driveRepo = DriveBackupRepository()
            val uploadResult = driveRepo.uploadEncryptedBackup(status.accessToken, fileName, encryptedBytes)
            return if (uploadResult.isSuccess) {
                val uploaded = uploadResult.getOrThrow()
                val now = System.currentTimeMillis()
                prefsStore.recordVerifiedDriveBackup(now, hash)
                db.backupMetadataDao().insert(
                    BackupMetadataEntity(
                        provider = BackupProvider.GOOGLE_DRIVE,
                        fileName = uploaded.name,
                        remoteFileId = uploaded.id,
                        schemaVersion = exportedSnapshot.roomSchemaVersion,
                        recordCount = exportedSnapshot.counts.transactions + exportedSnapshot.counts.accounts,
                        status = BackupRecordStatus.VERIFIED,
                        createdAt = now
                    )
                )
                if (prefs.backupNotificationsEnabled) {
                    LocalNotificationHelper.postNotification(
                        context = applicationContext,
                        channelId = LocalNotificationHelper.CHANNEL_BACKUP,
                        notificationId = 3004,
                        title = "হিসাবনিকাশ",
                        body = "Encrypted Drive backup verified: ${uploaded.name}"
                    )
                }
                Result.success()
            } else {
                db.backupMetadataDao().insert(
                    BackupMetadataEntity(
                        provider = BackupProvider.GOOGLE_DRIVE,
                        fileName = fileName,
                        schemaVersion = exportedSnapshot.roomSchemaVersion,
                        recordCount = exportedSnapshot.counts.transactions,
                        status = BackupRecordStatus.FAILED,
                        errorMessage = uploadResult.exceptionOrNull()?.message,
                        createdAt = System.currentTimeMillis()
                    )
                )
                Result.retry()
            }
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    companion object {
        private const val UNIQUE_AUTO_BACKUP = "hisabnikash_auto_drive_backup"

        fun scheduleOrCancel(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(UNIQUE_AUTO_BACKUP)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_AUTO_BACKUP,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
