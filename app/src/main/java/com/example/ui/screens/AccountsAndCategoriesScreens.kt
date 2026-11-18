package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AccountType
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.domain.AccountSummary
import com.example.domain.MoneyFormatter
import com.example.domain.MoneyParseResult
import com.example.domain.MoneyParser
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.accountIconForType
import com.example.ui.components.accountTypeLabel
import com.example.ui.components.categoryIconForKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    accounts: List<AccountSummary>,
    isBangla: Boolean,
    hideBalances: Boolean,
    onBack: () -> Unit,
    onCreateAccount: (name: String, type: AccountType, openingPoisha: Long, notes: String, includeInTotal: Boolean) -> Unit,
    onUpdateAccount: (id: String, name: String, type: AccountType, openingPoisha: Long, notes: String, includeInTotal: Boolean) -> Unit,
    onToggleArchive: (id: String, archived: Boolean) -> Unit
) {
    var showEditor by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountSummary?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.CASH) }
    var openingText by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var includeInTotal by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val errInvalidAmount = stringResource(R.string.err_invalid_amount)
    val errRequired = stringResource(R.string.required_field)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingAccount = null
                    name = ""
                    type = AccountType.CASH
                    openingText = "0"
                    notes = ""
                    includeInTotal = true
                    errorMsg = null
                    showEditor = true
                },
                modifier = Modifier.testTag("add_account_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_account))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 84.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.manual_tracking_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (accounts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.AccountBalanceWallet,
                        title = stringResource(R.string.empty_home_title),
                        subtitle = stringResource(R.string.empty_home_desc)
                    )
                }
            } else {
                items(accounts, key = { it.id }) { acc ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = accountIconForType(acc.type),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${accountTypeLabel(acc.type)}${if (acc.isArchived) " • ${stringResource(R.string.archived_badge)}" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${stringResource(R.string.current_ledger_balance)}: ${MoneyFormatter.formatBdt(acc.ledgerBalancePoisha, isBangla, hidden = hideBalances)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (acc.earmarkedPoisha > 0L) {
                                    Text(
                                        "${stringResource(R.string.available_unreserved_balance)}: ${MoneyFormatter.formatBdt(acc.unreservedAvailablePoisha, isBangla, hidden = hideBalances)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    editingAccount = acc
                                    name = acc.name
                                    type = acc.type
                                    openingText = MoneyFormatter.formatPlainDecimal(acc.openingBalancePoisha)
                                    notes = acc.notes
                                    includeInTotal = acc.includeInTotal
                                    errorMsg = null
                                    showEditor = true
                                }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_account))
                            }
                            IconButton(onClick = { onToggleArchive(acc.id, !acc.isArchived) }) {
                                Icon(
                                    imageVector = if (acc.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                    contentDescription = stringResource(R.string.archive)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = {
                Text(if (editingAccount == null) stringResource(R.string.add_account) else stringResource(R.string.edit_account))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMsg = null },
                        label = { Text(stringResource(R.string.account_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("account_name_input")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AccountType.entries.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(accountTypeLabel(t)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = openingText,
                        onValueChange = { openingText = it; errorMsg = null },
                        label = { Text(stringResource(R.string.opening_balance)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("account_opening_input")
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(stringResource(R.string.note_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { includeInTotal = !includeInTotal }
                    ) {
                        Checkbox(checked = includeInTotal, onCheckedChange = { includeInTotal = it })
                        Text(stringResource(R.string.include_in_total))
                    }
                    errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isBlank()) {
                            errorMsg = errRequired
                            return@TextButton
                        }
                        val parsed = MoneyParser.parseBdtToPoisha(openingText.ifBlank { "0" }, allowZero = true, allowNegative = true)
                        if (parsed !is MoneyParseResult.Valid) {
                            errorMsg = errInvalidAmount
                            return@TextButton
                        }
                        val existing = editingAccount
                        if (existing == null) {
                            onCreateAccount(name, type, parsed.poisha, notes, includeInTotal)
                        } else {
                            onUpdateAccount(existing.id, name, type, parsed.poisha, notes, includeInTotal)
                        }
                        showEditor = false
                    },
                    modifier = Modifier.testTag("account_save_btn")
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    categories: List<CategoryEntity>,
    isBangla: Boolean,
    onBack: () -> Unit,
    onSaveCategory: (existingId: String?, type: CategoryType, nameEn: String, nameBn: String, iconKey: String, colorHex: Long) -> Unit,
    onToggleArchive: (id: String, archived: Boolean) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDialog by remember { mutableStateOf(false) }
    var editingCat by remember { mutableStateOf<CategoryEntity?>(null) }
    var nameEn by remember { mutableStateOf("") }
    var nameBn by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf("category") }
    var colorHex by remember { mutableLongStateOf(0xFF064AA0L) }

    val targetType = if (selectedTab == 0) CategoryType.EXPENSE else CategoryType.INCOME
    val filtered = remember(categories, targetType) { categories.filter { it.type == targetType } }
    val palette = listOf(0xFF123D34L, 0xFF064AA0L, 0xFFD95757L, 0xFF16875DL, 0xFFE67E22L, 0xFF8E44ADL)
    val iconChoices = listOf("category", "restaurant", "shopping_bag", "directions_bus", "home", "bolt", "school", "medical_services", "payments", "laptop_mac")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCat = null
                    nameEn = ""
                    nameBn = ""
                    iconKey = "category"
                    colorHex = palette.first()
                    showDialog = true
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_category))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.tx_type_expense)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.tx_type_income)) })
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { cat ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(cat.colorHex).copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(categoryIconForKey(cat.iconKey), contentDescription = null, tint = Color(cat.colorHex))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (isBangla) cat.nameBn else cat.nameEn, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${if (isBangla) cat.nameEn else cat.nameBn}${if (cat.isArchived) " • ${stringResource(R.string.archived_badge)}" else ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingCat = cat
                                    nameEn = cat.nameEn
                                    nameBn = cat.nameBn
                                    iconKey = cat.iconKey
                                    colorHex = cat.colorHex
                                    showDialog = true
                                }
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_category))
                            }
                            IconButton(onClick = { onToggleArchive(cat.id, !cat.isArchived) }) {
                                Icon(
                                    if (cat.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                    contentDescription = stringResource(R.string.archive)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingCat == null) stringResource(R.string.add_category) else stringResource(R.string.edit_category)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = { nameEn = it },
                        label = { Text(stringResource(R.string.category_name_en)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameBn,
                        onValueChange = { nameBn = it },
                        label = { Text(stringResource(R.string.category_name_bn)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        palette.forEach { c ->
                            FilterChip(
                                selected = colorHex == c,
                                onClick = { colorHex = c },
                                label = {
                                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(c)))
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        iconChoices.forEach { key ->
                            FilterChip(
                                selected = iconKey == key,
                                onClick = { iconKey = key },
                                label = { Icon(categoryIconForKey(key), contentDescription = key, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameEn.isNotBlank() || nameBn.isNotBlank()) {
                            onSaveCategory(
                                editingCat?.id,
                                targetType,
                                nameEn.ifBlank { nameBn },
                                nameBn.ifBlank { nameEn },
                                iconKey,
                                colorHex
                            )
                            showDialog = false
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
