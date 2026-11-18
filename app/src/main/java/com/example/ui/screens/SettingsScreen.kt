package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AppLanguageMode
import com.example.data.local.AppThemeMode
import com.example.data.local.DateFormatStyle
import com.example.data.local.UserPreferences
import com.example.domain.AccountSummary
import com.example.domain.MoneyFormatter
import com.example.ui.components.OfficialLogoImage
import com.example.worker.LocalNotificationHelper
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    prefs: UserPreferences,
    accounts: List<AccountSummary>,
    isBangla: Boolean,
    onSetTheme: (AppThemeMode) -> Unit,
    onSetLanguage: (AppLanguageMode) -> Unit,
    onSetDateFormat: (DateFormatStyle) -> Unit,
    onSetBudgetStartDay: (Int) -> Unit,
    onSetDefaultAccount: (String) -> Unit,
    onSetBudgetAlerts: (Boolean) -> Unit,
    onSetRecurringReminders: (Boolean) -> Unit,
    onSetBackupNotifications: (Boolean) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenManualBackup: () -> Unit,
    onOpenDriveBackup: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenAbout: () -> Unit,
    onClearAllData: () -> Unit
) {
    val context = LocalContext.current
    var showAccountMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearConfirmText by remember { mutableStateOf("") }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !LocalNotificationHelper.hasNotificationPermission(context)
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                OfficialLogoImage(size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stringResource(R.string.app_name)} • ${stringResource(R.string.powered_by)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Preferences
        item {
            SettingsSectionCard(title = stringResource(R.string.section_preferences)) {
                Text(stringResource(R.string.pref_theme), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.resolvedTheme() == AppThemeMode.SYSTEM,
                        onClick = { onSetTheme(AppThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.theme_system)) }
                    )
                    FilterChip(
                        selected = prefs.resolvedTheme() == AppThemeMode.LIGHT,
                        onClick = { onSetTheme(AppThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.theme_light)) }
                    )
                    FilterChip(
                        selected = prefs.resolvedTheme() == AppThemeMode.DARK,
                        onClick = { onSetTheme(AppThemeMode.DARK) },
                        label = { Text(stringResource(R.string.theme_dark)) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(stringResource(R.string.pref_language), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.resolvedLanguage() == AppLanguageMode.ENGLISH,
                        onClick = { onSetLanguage(AppLanguageMode.ENGLISH) },
                        label = { Text(stringResource(R.string.lang_english)) },
                        modifier = Modifier.testTag("lang_en_chip")
                    )
                    FilterChip(
                        selected = prefs.resolvedLanguage() == AppLanguageMode.BANGLA,
                        onClick = { onSetLanguage(AppLanguageMode.BANGLA) },
                        label = { Text(stringResource(R.string.lang_bangla)) },
                        modifier = Modifier.testTag("lang_bn_chip")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(stringResource(R.string.currency_bdt_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(stringResource(R.string.pref_date_format), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateFormatStyle.entries.forEach { style ->
                        FilterChip(
                            selected = prefs.resolvedDateFormat() == style,
                            onClick = { onSetDateFormat(style) },
                            label = {
                                Text(
                                    when (style) {
                                        DateFormatStyle.DD_MMM_YYYY -> "dd MMM yyyy"
                                        DateFormatStyle.DD_MM_YYYY -> "dd/MM/yyyy"
                                        DateFormatStyle.YYYY_MM_DD -> "yyyy-MM-dd"
                                    }
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    "${stringResource(R.string.pref_budget_start_day)}: ${MoneyFormatter.localizeDigits(prefs.budgetStartDay.toString(), isBangla)}",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = prefs.budgetStartDay.toFloat(),
                    onValueChange = { onSetBudgetStartDay(it.roundToInt().coerceIn(1, 31)) },
                    valueRange = 1f..31f,
                    steps = 29
                )

                val defaultAccName = accounts.firstOrNull { it.id == prefs.defaultAccountId }?.name
                    ?: stringResource(R.string.none_selected)
                OutlinedButton(onClick = { showAccountMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${stringResource(R.string.pref_default_account)}: $defaultAccName")
                }
                DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                    accounts.filter { !it.isArchived }.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.name) },
                            onClick = {
                                onSetDefaultAccount(acc.id)
                                showAccountMenu = false
                            }
                        )
                    }
                }

                SettingsSwitchRow(
                    title = stringResource(R.string.pref_budget_alerts),
                    checked = prefs.budgetAlertsEnabled,
                    onCheckedChange = {
                        if (it) ensureNotificationPermissionIfNeeded()
                        onSetBudgetAlerts(it)
                    }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.pref_recurring_reminders),
                    checked = prefs.recurringRemindersEnabled,
                    onCheckedChange = {
                        if (it) ensureNotificationPermissionIfNeeded()
                        onSetRecurringReminders(it)
                    }
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.pref_backup_notifications),
                    checked = prefs.backupNotificationsEnabled,
                    onCheckedChange = {
                        if (it) ensureNotificationPermissionIfNeeded()
                        onSetBackupNotifications(it)
                    }
                )
            }
        }

        // Finance & Tools
        item {
            SettingsSectionCard(title = stringResource(R.string.section_finance)) {
                SettingsNavRow(Icons.Filled.AccountBalanceWallet, stringResource(R.string.accounts_title), onOpenAccounts)
                SettingsNavRow(Icons.Filled.Category, stringResource(R.string.categories_title), onOpenCategories)
                SettingsNavRow(Icons.Filled.Savings, stringResource(R.string.savings_goals_title), onOpenGoals)
                SettingsNavRow(Icons.Filled.Repeat, stringResource(R.string.recurring_title), onOpenRecurring)
                SettingsNavRow(Icons.Filled.Description, stringResource(R.string.reports_title), onOpenReports)
            }
        }

        // Backup & Security
        item {
            SettingsSectionCard(title = stringResource(R.string.section_backup_security)) {
                SettingsNavRow(Icons.Filled.Backup, stringResource(R.string.manual_backup_title), onOpenManualBackup)
                SettingsNavRow(Icons.Filled.CloudUpload, stringResource(R.string.drive_backup_title), onOpenDriveBackup)
                SettingsNavRow(Icons.Filled.Lock, stringResource(R.string.security_title), onOpenSecurity)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            clearConfirmText = ""
                            showClearDialog = true
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.clear_all_data),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // About
        item {
            SettingsSectionCard(title = stringResource(R.string.section_info)) {
                SettingsNavRow(Icons.Filled.Info, stringResource(R.string.about_title), onOpenAbout)
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_all_data)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.clear_all_confirm))
                    OutlinedTextField(
                        value = clearConfirmText,
                        onValueChange = { clearConfirmText = it },
                        label = { Text(stringResource(R.string.clear_all_type_confirm)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = clearConfirmText.trim().equals("DELETE", ignoreCase = false),
                    onClick = {
                        onClearAllData()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
