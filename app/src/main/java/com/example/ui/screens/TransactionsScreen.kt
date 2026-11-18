package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.local.DateFormatStyle
import com.example.data.local.TransactionKind
import com.example.domain.AccountSummary
import com.example.domain.LedgerItem
import com.example.domain.MoneyFormatter
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatUtcDate

enum class LedgerTypeFilter { ALL, INCOME, EXPENSE, TRANSFER, ADJUSTMENT }
enum class LedgerSortMode { NEWEST, OLDEST, AMOUNT_HIGH, AMOUNT_LOW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    items: List<LedgerItem>,
    accounts: List<AccountSummary>,
    categories: List<CategoryEntity>,
    isBangla: Boolean,
    hideBalances: Boolean,
    dateFormatStyle: DateFormatStyle,
    onAddEntry: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onDuplicateTransaction: (String) -> Unit,
    onDeleteItem: (LedgerItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf(LedgerTypeFilter.ALL) }
    var accountFilterId by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(LedgerSortMode.NEWEST) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var inspectedItem by remember { mutableStateOf<LedgerItem?>(null) }
    var itemToDelete by remember { mutableStateOf<LedgerItem?>(null) }

    val filtered = remember(items, query, typeFilter, accountFilterId, sortMode) {
        val q = query.trim().lowercase()
        val list = items.filter { item ->
            val matchesType = when (typeFilter) {
                LedgerTypeFilter.ALL -> true
                LedgerTypeFilter.INCOME -> item is LedgerItem.CashFlow && item.kind == TransactionKind.INCOME
                LedgerTypeFilter.EXPENSE -> item is LedgerItem.CashFlow && item.kind == TransactionKind.EXPENSE
                LedgerTypeFilter.TRANSFER -> item is LedgerItem.Transfer
                LedgerTypeFilter.ADJUSTMENT -> item is LedgerItem.Adjustment
            }
            val matchesAccount = if (accountFilterId == null) true else when (item) {
                is LedgerItem.CashFlow -> item.accountId == accountFilterId
                is LedgerItem.Transfer -> item.fromAccountId == accountFilterId || item.toAccountId == accountFilterId
                is LedgerItem.Adjustment -> item.accountId == accountFilterId
            }
            val matchesQuery = if (q.isEmpty()) true else when (item) {
                is LedgerItem.CashFlow ->
                    item.note.lowercase().contains(q) ||
                        item.merchant.lowercase().contains(q) ||
                        item.tags.lowercase().contains(q) ||
                        item.categoryNameEn.lowercase().contains(q) ||
                        item.categoryNameBn.lowercase().contains(q) ||
                        item.accountName.lowercase().contains(q)
                is LedgerItem.Transfer ->
                    item.note.lowercase().contains(q) ||
                        item.fromAccountName.lowercase().contains(q) ||
                        item.toAccountName.lowercase().contains(q)
                is LedgerItem.Adjustment ->
                    item.reason.lowercase().contains(q) ||
                        item.accountName.lowercase().contains(q)
            }
            matchesType && matchesAccount && matchesQuery
        }

        when (sortMode) {
            LedgerSortMode.NEWEST -> list.sortedByDescending { it.timestampUtc }
            LedgerSortMode.OLDEST -> list.sortedBy { it.timestampUtc }
            LedgerSortMode.AMOUNT_HIGH -> list.sortedByDescending { kotlin.math.abs(it.amountPoisha) }
            LedgerSortMode.AMOUNT_LOW -> list.sortedBy { kotlin.math.abs(it.amountPoisha) }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddEntry,
                modifier = Modifier.testTag("add_entry_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_transaction))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.transactions_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_transactions_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tx_search_input")
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = typeFilter == LedgerTypeFilter.ALL,
                    onClick = { typeFilter = LedgerTypeFilter.ALL },
                    label = { Text(stringResource(R.string.filter_all)) }
                )
                FilterChip(
                    selected = typeFilter == LedgerTypeFilter.EXPENSE,
                    onClick = { typeFilter = LedgerTypeFilter.EXPENSE },
                    label = { Text(stringResource(R.string.tx_type_expense)) }
                )
                FilterChip(
                    selected = typeFilter == LedgerTypeFilter.INCOME,
                    onClick = { typeFilter = LedgerTypeFilter.INCOME },
                    label = { Text(stringResource(R.string.tx_type_income)) }
                )
                FilterChip(
                    selected = typeFilter == LedgerTypeFilter.TRANSFER,
                    onClick = { typeFilter = LedgerTypeFilter.TRANSFER },
                    label = { Text(stringResource(R.string.tx_type_transfer)) }
                )
                FilterChip(
                    selected = typeFilter == LedgerTypeFilter.ADJUSTMENT,
                    onClick = { typeFilter = LedgerTypeFilter.ADJUSTMENT },
                    label = { Text(stringResource(R.string.tx_type_adjustment)) }
                )

                FilterChip(
                    selected = accountFilterId != null,
                    onClick = { showAccountMenu = true },
                    label = {
                        val accName = accounts.firstOrNull { it.id == accountFilterId }?.name
                            ?: stringResource(R.string.select_account)
                        Text(accName)
                    }
                )
                DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.all_accounts)) },
                        onClick = {
                            accountFilterId = null
                            showAccountMenu = false
                        }
                    )
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.name) },
                            onClick = {
                                accountFilterId = acc.id
                                showAccountMenu = false
                            }
                        )
                    }
                }

                FilterChip(
                    selected = sortMode != LedgerSortMode.NEWEST,
                    onClick = { showSortMenu = true },
                    label = { Text(stringResource(R.string.sort)) }
                )
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_newest)) },
                        onClick = { sortMode = LedgerSortMode.NEWEST; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_oldest)) },
                        onClick = { sortMode = LedgerSortMode.OLDEST; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_amount_high)) },
                        onClick = { sortMode = LedgerSortMode.AMOUNT_HIGH; showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_amount_low)) },
                        onClick = { sortMode = LedgerSortMode.AMOUNT_LOW; showSortMenu = false }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = stringResource(R.string.empty_transactions),
                    subtitle = stringResource(R.string.empty_home_desc)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        LedgerItemRow(
                            item = item,
                            isBangla = isBangla,
                            hideBalances = hideBalances,
                            dateFormatStyle = dateFormatStyle,
                            onClick = { inspectedItem = item }
                        )
                    }
                }
            }
        }
    }

    inspectedItem?.let { item ->
        ModalBottomSheet(onDismissRequest = { inspectedItem = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.transaction_details),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                when (item) {
                    is LedgerItem.CashFlow -> {
                        Text("${stringResource(R.string.select_category)}: ${if (isBangla) item.categoryNameBn else item.categoryNameEn}")
                        Text("${stringResource(R.string.select_account)}: ${item.accountName}")
                        Text("${stringResource(R.string.amount_bdt)}: ${MoneyFormatter.formatBdt(item.amountPoisha, isBangla, hidden = hideBalances)}")
                        if (item.merchant.isNotBlank()) Text("${stringResource(R.string.merchant_payee)}: ${item.merchant}")
                        if (item.tags.isNotBlank()) Text("${stringResource(R.string.tags_optional)}: ${item.tags}")
                        if (item.note.isNotBlank()) Text("${stringResource(R.string.note_optional)}: ${item.note}")
                        Text("${stringResource(R.string.date_time)}: ${formatUtcDate(item.timestampUtc, dateFormatStyle, isBangla)}")
                    }
                    is LedgerItem.Transfer -> {
                        Text("${stringResource(R.string.from_account)}: ${item.fromAccountName}")
                        Text("${stringResource(R.string.to_account)}: ${item.toAccountName}")
                        Text("${stringResource(R.string.amount_bdt)}: ${MoneyFormatter.formatBdt(item.amountPoisha, isBangla, hidden = hideBalances)}")
                        if (item.note.isNotBlank()) Text("${stringResource(R.string.note_optional)}: ${item.note}")
                    }
                    is LedgerItem.Adjustment -> {
                        Text("${stringResource(R.string.select_account)}: ${item.accountName}")
                        Text("${stringResource(R.string.amount_bdt)}: ${MoneyFormatter.formatBdt(item.deltaPoisha, isBangla, hidden = hideBalances, showPositiveSign = true)}")
                        Text("${stringResource(R.string.adjustment_reason)}: ${item.reason}")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item is LedgerItem.CashFlow) {
                        OutlinedButton(
                            onClick = {
                                inspectedItem = null
                                onEditTransaction(item.id)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.edit))
                        }
                        OutlinedButton(
                            onClick = {
                                inspectedItem = null
                                onDuplicateTransaction(item.id)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.duplicate))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            itemToDelete = item
                            inspectedItem = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_tx)) },
            text = { Text(stringResource(R.string.confirm_delete_tx_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteItem(item)
                        itemToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
