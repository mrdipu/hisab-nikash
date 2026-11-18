package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.domain.BudgetPeriodCalculator
import com.example.domain.BudgetProgress
import com.example.domain.MoneyFormatter
import com.example.domain.MoneyParseResult
import com.example.domain.MoneyParser
import com.example.ui.components.BadgeChip
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.BrandExpenseRed
import com.example.ui.theme.BrandSecondaryGreen
import java.time.YearMonth

@Composable
fun BudgetScreen(
    periodKey: String,
    budgetStartDay: Int,
    budgets: List<BudgetProgress>,
    categories: List<CategoryEntity>,
    isBangla: Boolean,
    hideBalances: Boolean,
    onChangePeriod: (String) -> Unit,
    onSaveBudget: (periodKey: String, categoryId: String?, limitPoisha: Long) -> Unit,
    onDeleteBudget: (String) -> Unit,
    onCopyFromPrevious: (String, (Int) -> Unit) -> Unit,
    onCopyToNext: (String, (Int) -> Unit) -> Unit,
    onOpenAlertHistory: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var dialogCategoryId by remember { mutableStateOf<String?>(null) }
    var dialogLimitText by remember { mutableStateOf("") }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var catMenuExpanded by remember { mutableStateOf(false) }

    val period = remember(periodKey, budgetStartDay) {
        BudgetPeriodCalculator.periodForKey(periodKey, budgetStartDay)
    }
    val expenseCategories = remember(categories) {
        categories.filter { !it.isArchived && it.type == CategoryType.EXPENSE }
    }
    val errInvalidAmount = stringResource(R.string.err_invalid_amount)
    val copiedFormat = stringResource(R.string.budget_copied_count)
    val copyNoneMsg = stringResource(R.string.budget_copy_none)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    dialogCategoryId = null
                    dialogLimitText = ""
                    dialogError = null
                    showDialog = true
                },
                modifier = Modifier.testTag("add_budget_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.set_budget))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 84.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.budget_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onOpenAlertHistory) {
                        Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.budget_alert_history))
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    onChangePeriod(YearMonth.parse(periodKey).minusMonths(1).toString())
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = MoneyFormatter.localizeDigits(periodKey, isBangla),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = MoneyFormatter.localizeDigits("${period.startDate} – ${period.endDateInclusive}", isBangla),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    onChangePeriod(YearMonth.parse(periodKey).plusMonths(1).toString())
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onCopyFromPrevious(periodKey) { count ->
                                        scope.launch {
                                            val msg = if (count > 0) String.format(copiedFormat, count) else copyNoneMsg
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.copy_prev_month), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = {
                                    onCopyToNext(periodKey) { count ->
                                        scope.launch {
                                            val msg = if (count > 0) String.format(copiedFormat, count) else copyNoneMsg
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.copy_to_next_month), maxLines = 1)
                            }
                        }
                    }
                }
            }

            if (budgets.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.PieChart,
                        title = stringResource(R.string.budget_empty),
                        subtitle = stringResource(R.string.onboarding_desc_2)
                    )
                }
            } else {
                items(budgets, key = { it.budgetId }) { b ->
                    BudgetCard(
                        budget = b,
                        isBangla = isBangla,
                        hideBalances = hideBalances,
                        onEdit = {
                            dialogCategoryId = b.categoryId
                            dialogLimitText = MoneyFormatter.formatPlainDecimal(b.limitPoisha)
                            dialogError = null
                            showDialog = true
                        },
                        onDelete = { onDeleteBudget(b.budgetId) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        val selectedCatLabel = if (dialogCategoryId == null) {
            stringResource(R.string.overall_budget)
        } else {
            val c = expenseCategories.firstOrNull { it.id == dialogCategoryId }
            if (isBangla) c?.nameBn ?: "" else c?.nameEn ?: ""
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.set_budget)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { catMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedCatLabel)
                    }
                    DropdownMenu(expanded = catMenuExpanded, onDismissRequest = { catMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overall_budget)) },
                            onClick = {
                                dialogCategoryId = null
                                catMenuExpanded = false
                            }
                        )
                        expenseCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(if (isBangla) cat.nameBn else cat.nameEn) },
                                onClick = {
                                    dialogCategoryId = cat.id
                                    catMenuExpanded = false
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = dialogLimitText,
                        onValueChange = { dialogLimitText = it; dialogError = null },
                        label = { Text(stringResource(R.string.amount_bdt)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    dialogError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = MoneyParser.parseBdtToPoisha(dialogLimitText, allowZero = false)
                        if (parsed !is MoneyParseResult.Valid) {
                            dialogError = errInvalidAmount
                            return@TextButton
                        }
                        onSaveBudget(periodKey, dialogCategoryId, parsed.poisha)
                        showDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun BudgetCard(
    budget: BudgetProgress,
    isBangla: Boolean,
    hideBalances: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val label = if (budget.categoryId == null) {
        stringResource(R.string.overall_budget)
    } else {
        if (isBangla) budget.categoryNameBn ?: "" else budget.categoryNameEn ?: ""
    }
    val pct = budget.percentUsed
    val barColor = when {
        budget.isOverspent -> BrandExpenseRed
        pct >= 80f -> Color(0xFFE67E22)
        pct >= 50f -> Color(0xFF064AA0)
        else -> BrandSecondaryGreen
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onEdit
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BadgeChip(
                        label = MoneyFormatter.localizeDigits("${pct.toInt()}%", isBangla),
                        containerColor = barColor.copy(alpha = 0.14f),
                        contentColor = barColor
                    )
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }

            LinearProgressIndicator(
                progress = { (pct / 100f).coerceIn(0f, 1f) },
                color = barColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${stringResource(R.string.budget_spent)}: ${MoneyFormatter.formatBdt(budget.spentPoisha, isBangla, hidden = hideBalances)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (budget.isOverspent) {
                        "${stringResource(R.string.budget_overspent)} ${MoneyFormatter.formatBdt(-budget.remainingPoisha, isBangla, hidden = hideBalances)}"
                    } else {
                        "${stringResource(R.string.budget_remaining)}: ${MoneyFormatter.formatBdt(budget.remainingPoisha, isBangla, hidden = hideBalances)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (budget.isOverspent) BrandExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
