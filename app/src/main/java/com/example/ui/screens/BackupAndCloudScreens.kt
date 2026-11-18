package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.cloud.drive.DriveBackupFile
import com.example.cloud.drive.DriveConnectionStatus
import com.example.data.local.BackupMetadataEntity
import com.example.data.local.DateFormatStyle
import com.example.data.local.UserPreferences
import com.example.security.ConflictResolution
import com.example.security.RestoreMode
import com.example.security.RestorePreview
import com.example.ui.components.formatUtcDate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBackupScreen(
    prefs: UserPreferences,
    history: List<BackupMetadataEntity>,
    isBangla: Boolean,
    onExportToUri: suspend (Uri, String, CharArray) -> Result<Int>,
    onInspectUri: suspend (Uri, CharArray) -> Result<RestorePreview>,
    onApplyRestore: suspend (RestorePreview, RestoreMode, ConflictResolution) -> Result<Unit>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showExportPassDialog by remember { mutableStateOf(false) }
    var exportPass by remember { mutableStateOf("") }
    var exportConfirmPass by remember { mutableStateOf("") }
    var exportPassChars by remember { mutableStateOf<CharArray?>(null) }
    var exportFileName by remember { mutableStateOf("") }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportPassDialog by remember { mutableStateOf(false) }
    var importPass by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<RestorePreview?>(null) }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var conflictResolution by remember { mutableStateOf(ConflictResolution.USE_BACKUP) }

    var statusMsg by remember { mutableStateOf<String?>(null) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    var exportDialogErr by remember { mutableStateOf<String?>(null) }
    var importDialogErr by remember { mutableStateOf<String?>(null) }

    val errPassShort = stringResource(R.string.err_passphrase_short)
    val errPassMismatch = stringResource(R.string.err_passphrase_mismatch)

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val chars = exportPassChars
        if (uri != null && chars != null) {
            scope.launch {
                val res = onExportToUri(uri, exportFileName, chars)
                chars.fill('\u0000')
                exportPassChars = null
                if (res.isSuccess) {
                    statusMsg = "Verified encrypted backup written (${res.getOrThrow()} bytes) to $exportFileName"
                    errMsg = null
                } else {
                    errMsg = res.exceptionOrNull()?.message ?: "Backup export failed"
                }
            }
        } else {
            chars?.fill('\u0000')
            exportPassChars = null
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            importPass = ""
            importDialogErr = null
            showImportPassDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manual_backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        text = stringResource(R.string.passphrase_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        exportPass = ""
                        exportConfirmPass = ""
                        exportDialogErr = null
                        showExportPassDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("export_backup_btn")
                ) {
                    Text(stringResource(R.string.export_backup))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openDocLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("import_backup_btn")
                ) {
                    Text(stringResource(R.string.import_backup))
                }
            }

            if (prefs.lastVerifiedLocalBackupAt > 0L) {
                item {
                    Text(
                        text = "Last verified local backup: ${formatUtcDate(prefs.lastVerifiedLocalBackupAt, DateFormatStyle.DD_MMM_YYYY, isBangla)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            statusMsg?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(msg, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            errMsg?.let { err ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Text("Backup History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                items(history.take(10), key = { it.id }) { rec ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${rec.fileName} (${rec.provider.name})", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${rec.status.name} • ${rec.recordCount} records • ${formatUtcDate(rec.createdAt, DateFormatStyle.DD_MMM_YYYY, isBangla)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            rec.errorMessage?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportPassDialog) {
        AlertDialog(
            onDismissRequest = { showExportPassDialog = false },
            title = { Text(stringResource(R.string.export_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.passphrase_warning), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = exportPass,
                        onValueChange = { exportPass = it; exportDialogErr = null },
                        label = { Text(stringResource(R.string.backup_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = exportConfirmPass,
                        onValueChange = { exportConfirmPass = it; exportDialogErr = null },
                        label = { Text(stringResource(R.string.backup_passphrase_confirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    exportDialogErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (exportPass.length < 8) {
                            exportDialogErr = errPassShort
                            return@TextButton
                        }
                        if (exportPass != exportConfirmPass) {
                            exportDialogErr = errPassMismatch
                            return@TextButton
                        }
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportFileName = "hisabnikash_backup_$stamp.hnkb"
                        exportPassChars = exportPass.toCharArray()
                        exportPass = ""
                        exportConfirmPass = ""
                        exportDialogErr = null
                        showExportPassDialog = false
                        createDocLauncher.launch(exportFileName)
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportPassDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showImportPassDialog) {
        AlertDialog(
            onDismissRequest = { showImportPassDialog = false },
            title = { Text(stringResource(R.string.import_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importPass,
                        onValueChange = { importPass = it; importDialogErr = null },
                        label = { Text(stringResource(R.string.backup_passphrase)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    importDialogErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri ?: return@TextButton
                        val chars = importPass.toCharArray()
                        importPass = ""
                        scope.launch {
                            val res = onInspectUri(uri, chars)
                            chars.fill('\u0000')
                            if (res.isSuccess) {
                                preview = res.getOrThrow()
                                showImportPassDialog = false
                                importDialogErr = null
                                errMsg = null
                            } else {
                                importDialogErr = res.exceptionOrNull()?.message ?: "Decryption failed"
                            }
                        }
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportPassDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    preview?.let { p ->
        RestorePreviewDialog(
            preview = p,
            restoreMode = restoreMode,
            conflictResolution = conflictResolution,
            onModeChange = { restoreMode = it },
            onResolutionChange = { conflictResolution = it },
            onConfirm = {
                scope.launch {
                    val res = onApplyRestore(p, restoreMode, conflictResolution)
                    preview = null
                    if (res.isSuccess) {
                        statusMsg = "Restore completed (${p.totalRecordsInBackup} records processed)"
                        errMsg = null
                    } else {
                        errMsg = res.exceptionOrNull()?.message ?: "Restore rolled back due to error"
                    }
                }
            },
            onDismiss = { preview = null }
        )
    }
}

@Composable
fun RestorePreviewDialog(
    preview: RestorePreview,
    restoreMode: RestoreMode,
    conflictResolution: ConflictResolution,
    onModeChange: (RestoreMode) -> Unit,
    onResolutionChange: (ConflictResolution) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_backup)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Schema v${preview.snapshot.roomSchemaVersion} • ${preview.totalRecordsInBackup} records in backup")
                Text("New records: ${preview.newRecordsCount} • Conflicts: ${preview.conflicts.size}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = restoreMode == RestoreMode.MERGE,
                        onClick = { onModeChange(RestoreMode.MERGE) },
                        label = { Text(stringResource(R.string.restore_mode_merge)) }
                    )
                    FilterChip(
                        selected = restoreMode == RestoreMode.REPLACE,
                        onClick = { onModeChange(RestoreMode.REPLACE) },
                        label = { Text(stringResource(R.string.restore_mode_replace)) }
                    )
                }
                if (restoreMode == RestoreMode.MERGE && preview.conflicts.isNotEmpty()) {
                    Text(stringResource(R.string.conflict_resolution_label), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = conflictResolution == ConflictResolution.KEEP_LOCAL,
                            onClick = { onResolutionChange(ConflictResolution.KEEP_LOCAL) },
                            label = { Text(stringResource(R.string.conflict_keep_local)) }
                        )
                        FilterChip(
                            selected = conflictResolution == ConflictResolution.USE_BACKUP,
                            onClick = { onResolutionChange(ConflictResolution.USE_BACKUP) },
                            label = { Text(stringResource(R.string.conflict_use_backup)) }
                        )
                        FilterChip(
                            selected = conflictResolution == ConflictResolution.SKIP,
                            onClick = { onResolutionChange(ConflictResolution.SKIP) },
                            label = { Text(stringResource(R.string.conflict_skip)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DriveBackupScreen(
    prefs: UserPreferences,
    driveStatus: DriveConnectionStatus,
    driveFiles: List<DriveBackupFile>,
    busy: Boolean,
    isBangla: Boolean,
    onCheckConnection: () -> Unit,
    onConnectDrive: () -> Unit,
    onHandleResolutionResult: (Int, Intent?) -> Unit,
    onDisconnect: () -> Unit,
    onBackupNow: (CharArray, (Result<DriveBackupFile>) -> Unit) -> Unit,
    onDownloadAndInspect: (DriveBackupFile, CharArray, (Result<RestorePreview>) -> Unit) -> Unit,
    onApplyRestore: suspend (RestorePreview, RestoreMode, ConflictResolution) -> Result<Unit>,
    onToggleRememberKey: (Boolean, CharArray?, (Result<Unit>) -> Unit) -> Unit,
    onToggleAutoBackup: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBackupPassDialog by remember { mutableStateOf(false) }
    var showRememberPassDialog by remember { mutableStateOf(false) }
    var restoreTargetFile by remember { mutableStateOf<DriveBackupFile?>(null) }
    var passInput by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<RestorePreview?>(null) }
    var restoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    var conflictResolution by remember { mutableStateOf(ConflictResolution.USE_BACKUP) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    var lastLaunchedRequestId by rememberSaveable { mutableLongStateOf(-1L) }

    val resolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        onHandleResolutionResult(res.resultCode, res.data)
    }

    LaunchedEffect(Unit) {
        onCheckConnection()
    }

    LaunchedEffect(driveStatus) {
        if (driveStatus is DriveConnectionStatus.ResolutionRequired &&
            driveStatus.requestId != lastLaunchedRequestId
        ) {
            lastLaunchedRequestId = driveStatus.requestId
            errMsg = null
            statusMsg = null
            runCatching {
                resolutionLauncher.launch(
                    IntentSenderRequest.Builder(driveStatus.pendingIntent.intentSender).build()
                )
            }.onFailure { errMsg = it.message }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drive_backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (driveStatus) {
                            is DriveConnectionStatus.Authorized -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.drive_authorized_title), fontWeight = FontWeight.Bold)
                                }
                                if (driveStatus.accountEmail.isNotBlank()) {
                                    Text(driveStatus.accountEmail, style = MaterialTheme.typography.bodyMedium)
                                }
                                OutlinedButton(
                                    onClick = onDisconnect,
                                    modifier = Modifier.testTag("drive_disconnect_btn")
                                ) {
                                    Text(stringResource(R.string.drive_disconnect))
                                }
                            }
                            is DriveConnectionStatus.PlayServicesUnavailable -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(stringResource(R.string.drive_play_services_unavailable), fontWeight = FontWeight.Bold)
                                }
                                Text(stringResource(R.string.drive_setup_required_desc), style = MaterialTheme.typography.bodyMedium)
                            }
                            is DriveConnectionStatus.ConsentCancelled -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(stringResource(R.string.drive_consent_cancelled_title), fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = stringResource(R.string.drive_consent_cancelled_desc, context.packageName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!driveStatus.detail.isNullOrBlank()) {
                                    Text(
                                        text = driveStatus.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Button(
                                    onClick = onConnectDrive,
                                    enabled = !busy,
                                    modifier = Modifier.testTag("drive_retry_btn")
                                ) {
                                    Text(stringResource(R.string.drive_retry_connect))
                                }
                            }
                            is DriveConnectionStatus.SetupRequired -> {
                                val pkgName = driveStatus.packageName.ifBlank { context.packageName }
                                val sha1 = driveStatus.sha1Fingerprint
                                val copiedMsg = stringResource(R.string.drive_copied_oauth_details)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(stringResource(R.string.drive_setup_required_title), fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = stringResource(R.string.drive_unregistered_summary),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${stringResource(R.string.drive_package_name_label)}: $pkgName",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (!sha1.isNullOrBlank()) {
                                            Text(
                                                text = "${stringResource(R.string.drive_sha1_label)}: $sha1",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = driveStatus.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.drive_offline_fallback_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipText = buildString {
                                                append("Package: ").append(pkgName)
                                                if (!sha1.isNullOrBlank()) {
                                                    append("\nSHA-1: ").append(sha1)
                                                }
                                            }
                                            val cb = context.getSystemService(ClipboardManager::class.java)
                                            cb?.setPrimaryClip(ClipData.newPlainText("HisabNikash OAuth Info", clipText))
                                            statusMsg = copiedMsg
                                            errMsg = null
                                        },
                                        modifier = Modifier.testTag("drive_copy_oauth_btn")
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.drive_copy_oauth_details))
                                    }
                                    Button(
                                        onClick = onConnectDrive,
                                        enabled = !busy,
                                        modifier = Modifier.testTag("drive_retry_btn")
                                    ) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                            is DriveConnectionStatus.ResolutionRequired -> {
                                Text(
                                    text = stringResource(R.string.drive_connect),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            is DriveConnectionStatus.Error -> {
                                Text(driveStatus.message, color = MaterialTheme.colorScheme.error)
                                Button(
                                    onClick = onConnectDrive,
                                    enabled = !busy,
                                    modifier = Modifier.testTag("drive_retry_btn")
                                ) {
                                    Text(stringResource(R.string.drive_retry_connect))
                                }
                            }
                            is DriveConnectionStatus.NotAuthorized -> {
                                Text(stringResource(R.string.drive_not_connected_title), fontWeight = FontWeight.Bold)
                                Text(
                                    text = stringResource(R.string.drive_not_connected_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (prefs.driveAccountEmail.isNotBlank()) {
                                    Text(
                                        text = prefs.driveAccountEmail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Button(
                                    onClick = onConnectDrive,
                                    enabled = !busy,
                                    modifier = Modifier.testTag("drive_connect_btn")
                                ) {
                                    Text(stringResource(R.string.drive_connect))
                                }
                            }
                        }
                        if (busy) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            statusMsg?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(msg, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            errMsg?.let { err ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (driveStatus is DriveConnectionStatus.Authorized) {
                item {
                    Button(
                        onClick = { passInput = ""; showBackupPassDialog = true },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drive_backup_now_btn")
                    ) {
                        Text(stringResource(R.string.drive_backup_now))
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.drive_remember_key_toggle), modifier = Modifier.weight(1f))
                            Switch(
                                checked = prefs.rememberBackupKeyOnDevice,
                                onCheckedChange = { wantEnable ->
                                    if (!wantEnable) {
                                        onToggleRememberKey(false, null) { }
                                    } else {
                                        passInput = ""
                                        showRememberPassDialog = true
                                    }
                                }
                            )
                        }
                        Text(
                            text = stringResource(R.string.drive_remember_key_warning),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.drive_auto_backup_toggle), modifier = Modifier.weight(1f))
                            Switch(
                                checked = prefs.driveAutoBackupEnabled,
                                enabled = prefs.rememberBackupKeyOnDevice && driveStatus is DriveConnectionStatus.Authorized,
                                onCheckedChange = onToggleAutoBackup
                            )
                        }
                    }
                }
            }

            if (prefs.lastVerifiedDriveBackupAt > 0L) {
                item {
                    Text(
                        text = "Last verified Drive backup: ${formatUtcDate(prefs.lastVerifiedDriveBackupAt, DateFormatStyle.DD_MMM_YYYY, isBangla)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (driveFiles.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.drive_remote_backups_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(driveFiles, key = { it.id }) { file ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold)
                                Text("${file.sizeBytes} bytes • ${file.modifiedTimeIso}", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(onClick = { passInput = ""; restoreTargetFile = file }) {
                                Text(stringResource(R.string.drive_restore))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBackupPassDialog) {
        PassphrasePromptDialog(
            title = stringResource(R.string.drive_backup_now),
            pass = passInput,
            onPassChange = { passInput = it },
            onConfirm = {
                val chars = passInput.toCharArray()
                passInput = ""
                showBackupPassDialog = false
                onBackupNow(chars) { res ->
                    chars.fill('\u0000')
                    if (res.isSuccess) {
                        statusMsg = "Uploaded & verified: ${res.getOrThrow().name}"
                        errMsg = null
                    } else {
                        errMsg = res.exceptionOrNull()?.message
                    }
                }
            },
            onDismiss = { showBackupPassDialog = false }
        )
    }

    if (showRememberPassDialog) {
        PassphrasePromptDialog(
            title = stringResource(R.string.drive_remember_key_toggle),
            pass = passInput,
            onPassChange = { passInput = it },
            onConfirm = {
                val chars = passInput.toCharArray()
                passInput = ""
                showRememberPassDialog = false
                onToggleRememberKey(true, chars) { res ->
                    chars.fill('\u0000')
                    if (res.isFailure) errMsg = res.exceptionOrNull()?.message
                    else statusMsg = "Backup key wrapped with Android Keystore for unattended backups"
                }
            },
            onDismiss = { showRememberPassDialog = false }
        )
    }

    restoreTargetFile?.let { file ->
        PassphrasePromptDialog(
            title = stringResource(R.string.drive_restore),
            pass = passInput,
            onPassChange = { passInput = it },
            onConfirm = {
                val chars = passInput.toCharArray()
                passInput = ""
                restoreTargetFile = null
                onDownloadAndInspect(file, chars) { res ->
                    chars.fill('\u0000')
                    if (res.isSuccess) preview = res.getOrThrow()
                    else errMsg = res.exceptionOrNull()?.message
                }
            },
            onDismiss = { restoreTargetFile = null }
        )
    }

    preview?.let { p ->
        RestorePreviewDialog(
            preview = p,
            restoreMode = restoreMode,
            conflictResolution = conflictResolution,
            onModeChange = { restoreMode = it },
            onResolutionChange = { conflictResolution = it },
            onConfirm = {
                scope.launch {
                    val res = onApplyRestore(p, restoreMode, conflictResolution)
                    preview = null
                    if (res.isSuccess) statusMsg = "Drive restore completed"
                    else errMsg = res.exceptionOrNull()?.message
                }
            },
            onDismiss = { preview = null }
        )
    }
}

@Composable
private fun PassphrasePromptDialog(
    title: String,
    pass: String,
    onPassChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pass,
                onValueChange = onPassChange,
                label = { Text(stringResource(R.string.backup_passphrase)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = pass.length >= 8, onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
