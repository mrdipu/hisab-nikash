package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hisabnikash_prefs")

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguageMode {
    SYSTEM,
    ENGLISH,
    BANGLA
}

enum class DateFormatStyle {
    DD_MM_YYYY,
    YYYY_MM_DD,
    DD_MMM_YYYY
}

@Serializable
data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val themeMode: String = AppThemeMode.SYSTEM.name,
    val languageMode: String = AppLanguageMode.SYSTEM.name,
    val dateFormatStyle: String = DateFormatStyle.DD_MMM_YYYY.name,
    val budgetStartDay: Int = 1,
    val defaultAccountId: String = "",
    val hideBalances: Boolean = false,
    val budgetAlertsEnabled: Boolean = true,
    val recurringRemindersEnabled: Boolean = true,
    val backupNotificationsEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val driveAutoBackupEnabled: Boolean = false,
    val rememberBackupKeyOnDevice: Boolean = false,
    val driveAccountEmail: String = "",
    val lastVerifiedDriveBackupAt: Long = 0L,
    val lastVerifiedLocalBackupAt: Long = 0L,
    val lastDriveSnapshotHash: String = ""
) {
    fun resolvedTheme(): AppThemeMode =
        runCatching { AppThemeMode.valueOf(themeMode) }.getOrDefault(AppThemeMode.SYSTEM)

    fun resolvedLanguage(): AppLanguageMode =
        runCatching { AppLanguageMode.valueOf(languageMode) }.getOrDefault(AppLanguageMode.SYSTEM)

    fun resolvedDateFormat(): DateFormatStyle =
        runCatching { DateFormatStyle.valueOf(dateFormatStyle) }.getOrDefault(DateFormatStyle.DD_MMM_YYYY)
}

class PreferencesDataStore(private val context: Context) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val BUDGET_START_DAY = intPreferencesKey("budget_start_day")
        val DEFAULT_ACCOUNT_ID = stringPreferencesKey("default_account_id")
        val HIDE_BALANCES = booleanPreferencesKey("hide_balances")
        val BUDGET_ALERTS = booleanPreferencesKey("budget_alerts")
        val RECURRING_REMINDERS = booleanPreferencesKey("recurring_reminders")
        val BACKUP_NOTIFICATIONS = booleanPreferencesKey("backup_notifications")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val DRIVE_AUTO_BACKUP = booleanPreferencesKey("drive_auto_backup")
        val REMEMBER_BACKUP_KEY = booleanPreferencesKey("remember_backup_key")
        val DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
        val LAST_DRIVE_BACKUP_AT = longPreferencesKey("last_drive_backup_at")
        val LAST_LOCAL_BACKUP_AT = longPreferencesKey("last_local_backup_at")
        val LAST_DRIVE_HASH = stringPreferencesKey("last_drive_hash")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            themeMode = prefs[Keys.THEME_MODE] ?: AppThemeMode.SYSTEM.name,
            languageMode = prefs[Keys.LANGUAGE_MODE] ?: AppLanguageMode.SYSTEM.name,
            dateFormatStyle = prefs[Keys.DATE_FORMAT] ?: DateFormatStyle.DD_MMM_YYYY.name,
            budgetStartDay = (prefs[Keys.BUDGET_START_DAY] ?: 1).coerceIn(1, 31),
            defaultAccountId = prefs[Keys.DEFAULT_ACCOUNT_ID] ?: "",
            hideBalances = prefs[Keys.HIDE_BALANCES] ?: false,
            budgetAlertsEnabled = prefs[Keys.BUDGET_ALERTS] ?: true,
            recurringRemindersEnabled = prefs[Keys.RECURRING_REMINDERS] ?: true,
            backupNotificationsEnabled = prefs[Keys.BACKUP_NOTIFICATIONS] ?: true,
            appLockEnabled = prefs[Keys.APP_LOCK] ?: false,
            driveAutoBackupEnabled = prefs[Keys.DRIVE_AUTO_BACKUP] ?: false,
            rememberBackupKeyOnDevice = prefs[Keys.REMEMBER_BACKUP_KEY] ?: false,
            driveAccountEmail = prefs[Keys.DRIVE_ACCOUNT_EMAIL] ?: "",
            lastVerifiedDriveBackupAt = prefs[Keys.LAST_DRIVE_BACKUP_AT] ?: 0L,
            lastVerifiedLocalBackupAt = prefs[Keys.LAST_LOCAL_BACKUP_AT] ?: 0L,
            lastDriveSnapshotHash = prefs[Keys.LAST_DRIVE_HASH] ?: ""
        )
    }

    suspend fun current(): UserPreferences = preferencesFlow.first()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguageMode(mode: AppLanguageMode) {
        context.dataStore.edit { it[Keys.LANGUAGE_MODE] = mode.name }
    }

    suspend fun setDateFormat(style: DateFormatStyle) {
        context.dataStore.edit { it[Keys.DATE_FORMAT] = style.name }
    }

    suspend fun setBudgetStartDay(day: Int) {
        context.dataStore.edit { it[Keys.BUDGET_START_DAY] = day.coerceIn(1, 31) }
    }

    suspend fun setDefaultAccountId(accountId: String) {
        context.dataStore.edit { it[Keys.DEFAULT_ACCOUNT_ID] = accountId }
    }

    suspend fun setHideBalances(hide: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_BALANCES] = hide }
    }

    suspend fun setBudgetAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BUDGET_ALERTS] = enabled }
    }

    suspend fun setRecurringRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RECURRING_REMINDERS] = enabled }
    }

    suspend fun setBackupNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BACKUP_NOTIFICATIONS] = enabled }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK] = enabled }
    }

    suspend fun setDriveAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DRIVE_AUTO_BACKUP] = enabled }
    }

    suspend fun setRememberBackupKey(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REMEMBER_BACKUP_KEY] = enabled }
    }

    suspend fun setDriveAccountEmail(email: String) {
        context.dataStore.edit { it[Keys.DRIVE_ACCOUNT_EMAIL] = email }
    }

    suspend fun recordVerifiedLocalBackup(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_LOCAL_BACKUP_AT] = timestamp }
    }

    suspend fun recordVerifiedDriveBackup(timestamp: Long, hash: String) {
        context.dataStore.edit {
            it[Keys.LAST_DRIVE_BACKUP_AT] = timestamp
            it[Keys.LAST_DRIVE_HASH] = hash
        }
    }

    suspend fun restoreFromBackup(prefs: UserPreferences) {
        context.dataStore.edit {
            it[Keys.ONBOARDING_COMPLETED] = true
            it[Keys.THEME_MODE] = prefs.themeMode
            it[Keys.LANGUAGE_MODE] = prefs.languageMode
            it[Keys.DATE_FORMAT] = prefs.dateFormatStyle
            it[Keys.BUDGET_START_DAY] = prefs.budgetStartDay.coerceIn(1, 31)
            it[Keys.DEFAULT_ACCOUNT_ID] = prefs.defaultAccountId
            it[Keys.HIDE_BALANCES] = prefs.hideBalances
            it[Keys.BUDGET_ALERTS] = prefs.budgetAlertsEnabled
            it[Keys.RECURRING_REMINDERS] = prefs.recurringRemindersEnabled
            it[Keys.BACKUP_NOTIFICATIONS] = prefs.backupNotificationsEnabled
            // Security/device-local flags like appLock & rememberBackupKey remain device-local or conservative.
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
