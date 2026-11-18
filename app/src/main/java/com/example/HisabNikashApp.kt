package com.example

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.worker.LocalNotificationHelper
import com.example.worker.RecurringCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HisabNikashApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LocalNotificationHelper.ensureChannels(this)
        runCatching {
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(this, workManagerConfiguration)
            }
            RecurringCheckWorker.schedulePeriodic(this)
        }
        appScope.launch {
            container.financeRepository.ensureSeedCategories()
        }
    }
}
