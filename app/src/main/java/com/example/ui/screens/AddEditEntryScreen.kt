package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.TransactionKind
import com.example.domain.AccountSummary
import com.example.domain.LedgerItem
import com.example.domain.MoneyFormatter
import com.example.domain.MoneyParseResult
import com.example.domain.MoneyParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEntryScreen(
    initialTab: String,
    existingCashFlow: LedgerItem.CashFlow?,
    accounts: List<AccountSummary>,
    categories: List<CategoryEntity>,
    defaultAccountId: String,
    isBangla: Boolean,
    onBack: () -> Unit,
    onSaveTransaction: (
        existingId: String?,
        kind: TransactionKind,
        accountId: String,
        categoryId: String,
        amountPoisha: Long,
        timestampUtc: Long,
        note: String,
        merchant: String,
        tags: String,
        paymentMethod: String,
        receiptUri: String?,
        onDone: (Result<Unit>) -> Unit
    ) -> Unit,
    onSaveTransfer: (
        fromId: String,
        toId: String,
        amountPoisha: Long,
        timestampUtc: Long,
        note: String,
        onDone: (Result<Unit>) -> Unit
    ) -> Unit,
    onSaveAdjustment: (
        accountId: String,
        deltaPoisha: Long,
        reason: String,
        onDone: (Result<Unit>) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }

    var selectedTab by remember(initialTab, existingCashFlow) {
        mutableIntStateOf(
            when {
                existingCashFlow?.kind == TransactionKind.INCOME -> 1
                existingCashFlow?.kind == TransactionKind.EXPENSE -> 0
                initialTab == "INCOME" -> 1
                initialTab == "TRANSFER" -> 2
                initialTab == "ADJUSTMENT" -> 3
                else -> 0
            }
        )
    }

    var amountText by remember(existingCashFlow) {
        mutableStateOf(
            existingCashFlow?.let { MoneyFormatter.formatPlainDecimal(it.amountPoisha) } ?: ""
        )
    }
    var accountId by remember(existingCashFlow, activeAccounts, defaultAccountId) {
        mutableStateOf(
            existingCashFlow?.accountId
                ?: activeAccounts.firstOrNull { it.id == defaultAccountId }?.id
                ?: activeAccounts.firstOrNull()?.id.orEmpty()
        )
    }
    var toAccountId by remember(activeAccounts) {
        mutableStateOf(activeAccounts.getOrNull(1)?.id ?: activeAccounts.firstOrNull()?.id.orEmpty())
    }

    val targetCategoryType = if (selectedTab == 1) CategoryType.INCOME else CategoryType.EXPENSE
    val availableCategories = remember(categories, targetCategoryType) {
        categories.filter { !it.isArchived && it.type == targetCategoryType }
    }
    var categoryId by remember(existingCashFlow, availableCategories) {
        mutableStateOf(
            existingCashFlow?.categoryId?.takeIf { id -> availableCategories.any { it.id == id } }
                ?: availableCategories.firstOrNull()?.id.orEmpty()
        )
    }

    var note by remember(existingCashFlow) { mutableStateOf(existingCashFlow?.note ?: "") }
    var merchant by remember(existingCashFlow) { mutableStateOf(existingCashFlow?.merchant ?: "") }
    var tags by remember(existingCashFlow) { mutableStateOf(existingCashFlow?.tags ?: "") }
    var paymentMethod by remember(existingCashFlow) { mutableStateOf(existingCashFlow?.paymentMethod ?: "") }
    var receiptUri by remember(existingCashFlow) { mutableStateOf(existingCashFlow?.receiptUri) }
    var timestampUtc by remember(existingCashFlow) {
        mutableLongStateOf(existingCashFlow?.timestampUtc ?: System.currentTimeMillis())
    }
    var adjustmentIsPositive by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            receiptUri = uri.toString()
        }
    }

    val errInvalidAmount = stringResource(R.string.err_invalid_amount)
    val errSameAccount = stringResource(R.string.err_same_account)
    val errRequired = stringResource(R.string.required_field)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingCashFlow != null) stringResource(R.string.edit_transaction)
                        else stringResource(R.string.add_transaction)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (existingCashFlow == null) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; errorMsg = null }, text = { Text(stringResource(R.string.tx_type_expense), maxLines = 1) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; errorMsg = null }, text = { Text(stringResource(R.string.tx_type_income), maxLines = 1) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2; errorMsg = null }, text = { Text(stringResource(R.string.tx_type_transfer), maxLines = 1) })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3; errorMsg = null }, text = { Text(stringResource(R.string.tx_type_adjustment), maxLines = 1) })
                }
            }

            if (activeAccounts.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_home_desc),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                return@Column
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    errorMsg = null
                },
                label = { Text(stringResource(R.string.amount_bdt)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_amount_input")
            )

            // Account Selection
            Text(
                text = if (selectedTab == 2) stringResource(R.string.from_account) else stringResource(R.string.select_account),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeAccounts.forEach { acc ->
                    FilterChip(
                        selected = accountId == acc.id,
                        onClick = { accountId = acc.id },
                        label = {
                            Text("${acc.name} (${MoneyFormatter.formatBdt(acc.ledgerBalancePoisha, isBangla)})")
                        }
                    )
                }
            }

            if (selectedTab == 2) {
                Text(text = stringResource(R.string.to_account), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeAccounts.forEach { acc ->
                        FilterChip(
                            selected = toAccountId == acc.id,
                            onClick = { toAccountId = acc.id },
                            label = {
                                Text("${acc.name} (${MoneyFormatter.formatBdt(acc.ledgerBalancePoisha, isBangla)})")
                            }
                        )
                    }
                }
            }

            if (selectedTab == 0 || selectedTab == 1) {
                Text(text = stringResource(R.string.select_category), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableCategories.forEach { cat ->
                        FilterChip(
                            selected = categoryId == cat.id,
                            onClick = { categoryId = cat.id },
                            label = { Text(if (isBangla) cat.nameBn else cat.nameEn) }
                        )
                    }
                }

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.merchant_payee)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.tags_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = { paymentMethod = it },
                    label = { Text(stringResource(R.string.payment_method)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (receiptUri == null) stringResource(R.string.attach_receipt) else stringResource(R.string.receipt_attached))
                    }
                    if (receiptUri != null) {
                        IconButton(onClick = { receiptUri = null }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_receipt))
                        }
                    }
                }
            }

            if (selectedTab == 3) {
                Text(
                    text = stringResource(R.string.adjustment_explainer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = adjustmentIsPositive,
                        onClick = { adjustmentIsPositive = true },
                        label = { Text(stringResource(R.string.adjustment_increase)) }
                    )
                    FilterChip(
                        selected = !adjustmentIsPositive,
                        onClick = { adjustmentIsPositive = false },
                        label = { Text(stringResource(R.string.adjustment_decrease)) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it; errorMsg = null },
                label = {
                    Text(
                        if (selectedTab == 3) stringResource(R.string.adjustment_reason)
                        else stringResource(R.string.note_optional)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("entry_note_input")
            )

            errorMsg?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    val parsed = MoneyParser.parseBdtToPoisha(amountText, allowZero = false)
                    if (parsed !is MoneyParseResult.Valid) {
                        errorMsg = errInvalidAmount
                        return@Button
                    }
                    when (selectedTab) {
                        0, 1 -> {
                            if (categoryId.isBlank()) {
                                errorMsg = errRequired
                                return@Button
                            }
                            val kind = if (selectedTab == 1) TransactionKind.INCOME else TransactionKind.EXPENSE
                            onSaveTransaction(
                                existingCashFlow?.id,
                                kind,
                                accountId,
                                categoryId,
                                parsed.poisha,
                                timestampUtc,
                                note,
                                merchant,
                                tags,
                                paymentMethod,
                                receiptUri
                            ) { res ->
                                if (res.isSuccess) onBack()
                                else errorMsg = res.exceptionOrNull()?.message
                            }
                        }
                        2 -> {
                            if (accountId == toAccountId) {
                                errorMsg = errSameAccount
                                return@Button
                            }
                            onSaveTransfer(accountId, toAccountId, parsed.poisha, timestampUtc, note) { res ->
                                if (res.isSuccess) onBack()
                                else errorMsg = res.exceptionOrNull()?.message
                            }
                        }
                        3 -> {
                            if (note.isBlank()) {
                                errorMsg = errRequired
                                return@Button
                            }
                            val delta = if (adjustmentIsPositive) parsed.poisha else -parsed.poisha
                            onSaveAdjustment(accountId, delta, note) { res ->
                                if (res.isSuccess) onBack()
                                else errorMsg = res.exceptionOrNull()?.message
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("entry_save_btn")
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}
