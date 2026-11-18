package com.example

import android.content.Context
import com.example.cloud.drive.DriveBackupRepository
import com.example.cloud.drive.GoogleAccountManager
import com.example.cloud.drive.GoogleAuthorizationManager
import com.example.data.local.HisabNikashDatabase
import com.example.data.local.PreferencesDataStore
import com.example.data.repository.FinanceRepository
import com.example.security.BackupRestoreManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: HisabNikashDatabase by lazy {
        HisabNikashDatabase.getInstance(appContext)
    }

    val prefsStore: PreferencesDataStore by lazy {
        PreferencesDataStore(appContext)
    }

    val financeRepository: FinanceRepository by lazy {
        FinanceRepository(database, prefsStore)
    }

    val backupRestoreManager: BackupRestoreManager by lazy {
        BackupRestoreManager(database, prefsStore)
    }

    val googleAccountManager: GoogleAccountManager by lazy {
        GoogleAccountManager(appContext)
    }

    val googleAuthorizationManager: GoogleAuthorizationManager by lazy {
        GoogleAuthorizationManager(appContext, googleAccountManager)
    }

    val driveBackupRepository: DriveBackupRepository by lazy {
        DriveBackupRepository()
    }
}
