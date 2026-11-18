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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.GoalPreset
import com.example.data.local.RecurringOccurrenceEntity
import com.example.data.local.RecurringTemplateEntity
import com.example.data.local.TransactionKind
import com.example.domain.AccountSummary
import com.example.domain.MoneyFormatter
import com.example.domain.MoneyParseResult
import com.example.domain.MoneyParser
import com.example.domain.RecurrenceFrequency
import com.example.domain.SavingsGoalSummary
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.goalIconForPreset
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalsScreen(
    goals: List<SavingsGoalSummary>,
    accounts: List<AccountSummary>,
    isBangla: Boolean,
    hideBalances: Boolean,
    onBack: () -> Unit,
    onSaveGoal: (name: String, preset: GoalPreset, targetPoisha: Long, notes: String, onDone: (Result<Unit>) -> Unit) -> Unit,
    onAllocate: (goalId: String, accountId: String, amountPoisha: Long, note: String, onDone: (Result<Unit>) -> Unit) -> Unit,
    onRelease: (goalId: String, accountId: String, amountPoisha: Long, note: String, onDone: (Result<Unit>) -> Unit) -> Unit,
    onDeleteGoal: (String) -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    var showCreate by remember { mutableStateOf(false) }
    var newGoalName by remember { mutableStateOf("") }
    var newGoalPreset by remember { mutableStateOf(GoalPreset.EMERGENCY_FUND) }
    var newGoalTarget by remember { mutableStateOf("") }
    var createErr by remember { mutableStateOf<String?>(null) }

    var actionGoal by remember { mutableStateOf<SavingsGoalSummary?>(null) }
    var actionIsRelease by remember { mutableStateOf(false) }
    var selectedAccId by remember(activeAccounts) { mutableStateOf(activeAccounts.firstOrNull()?.id.orEmpty()) }
    var allocAmountText by remember { mutableStateOf("") }
    var allocErr by remember { mutableStateOf<String?>(null) }

    val errInvalidAmount = stringResource(R.string.err_invalid_amount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.savings_goals_title)) },
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
                    newGoalName = ""
                    newGoalTarget = ""
                    createErr = null
                    showCreate = true
                },
                modifier = Modifier.testTag("add_goal_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_goal))
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
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        text = stringResource(R.string.goal_allocation_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (goals.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Savings,
                        title = stringResource(R.string.goal_empty),
                        subtitle = stringResource(R.string.goal_allocation_explainer)
                    )
                }
            } else {
                items(goals, key = { it.id }) { goal ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(goalIconForPreset(goal.preset), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { onDeleteGoal(goal.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                            LinearProgressIndicator(
                                progress = { goal.progressFraction },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${stringResource(R.string.goal_saved_reserved)}: ${MoneyFormatter.formatBdt(goal.reservedPoisha, isBangla, hidden = hideBalances)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${stringResource(R.string.goal_target)}: ${MoneyFormatter.formatBdt(goal.targetAmountPoisha, isBangla, hidden = hideBalances)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        actionGoal = goal
                                        actionIsRelease = false
                                        allocAmountText = ""
                                        allocErr = null
                                    },
                                    enabled = activeAccounts.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.goal_allocate))
                                }
                                OutlinedButton(
                                    onClick = {
                                        actionGoal = goal
                                        actionIsRelease = true
                                        allocAmountText = ""
                                        allocErr = null
                                    },
                                    enabled = goal.reservedPoisha > 0L && activeAccounts.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.goal_release))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.add_goal)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GoalPreset.entries.forEach { p ->
                            FilterChip(
                                selected = newGoalPreset == p,
                                onClick = {
                                    newGoalPreset = p
                                    if (newGoalName.isBlank()) newGoalName = p.name.replace("_", " ")
                                },
                                label = { Text(p.name.replace("_", " ")) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newGoalName,
                        onValueChange = { newGoalName = it; createErr = null },
                        label = { Text(stringResource(R.string.goal_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newGoalTarget,
                        onValueChange = { newGoalTarget = it; createErr = null },
                        label = { Text(stringResource(R.string.goal_target)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    createErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = MoneyParser.parseBdtToPoisha(newGoalTarget, allowZero = false)
                        if (parsed !is MoneyParseResult.Valid) {
                            createErr = errInvalidAmount
                            return@TextButton
                        }
                        onSaveGoal(newGoalName, newGoalPreset, parsed.poisha, "") { res ->
                            if (res.isSuccess) showCreate = false
                            else createErr = res.exceptionOrNull()?.message
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    actionGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { actionGoal = null },
            title = {
                Text(if (actionIsRelease) stringResource(R.string.goal_release) else stringResource(R.string.goal_allocate))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeAccounts.forEach { acc ->
                            FilterChip(
                                selected = selectedAccId == acc.id,
                                onClick = { selectedAccId = acc.id },
                                label = {
                                    Text("${acc.name} (${MoneyFormatter.formatBdt(acc.unreservedAvailablePoisha, isBangla)})")
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = allocAmountText,
                        onValueChange = { allocAmountText = it; allocErr = null },
                        label = { Text(stringResource(R.string.amount_bdt)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    allocErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = MoneyParser.parseBdtToPoisha(allocAmountText, allowZero = false)
                        if (parsed !is MoneyParseResult.Valid) {
                            allocErr = errInvalidAmount
                            return@TextButton
                        }
                        if (actionIsRelease) {
                            onRelease(goal.id, selectedAccId, parsed.poisha, "") { res ->
                                if (res.isSuccess) actionGoal = null
                                else allocErr = res.exceptionOrNull()?.message
                            }
                        } else {
                            onAllocate(goal.id, selectedAccId, parsed.poisha, "") { res ->
                                if (res.isSuccess) actionGoal = null
                                else allocErr = res.exceptionOrNull()?.message
                            }
                        }
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { actionGoal = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    templates: List<RecurringTemplateEntity>,
    pendingOccurrences: List<RecurringOccurrenceEntity>,
    accounts: List<AccountSummary>,
    categories: List<CategoryEntity>,
    isBangla: Boolean,
    onBack: () -> Unit,
    onSaveTemplate: (
        title: String,
        kind: TransactionKind,
        accountId: String,
        categoryId: String,
        amountPoisha: Long,
        frequency: RecurrenceFrequency,
        startDate: LocalDate,
        autoPost: Boolean,
        reminderEnabled: Boolean,
        note: String,
        onDone: (Result<Unit>) -> Unit
    ) -> Unit,
    onToggleEnabled: (id: String, enabled: Boolean) -> Unit,
    onDeleteTemplate: (id: String) -> Unit,
    onConfirmOccurrence: (id: String) -> Unit,
    onSkipOccurrence: (id: String) -> Unit
) {
    val activeAccounts = remember(accounts) { accounts.filter { !it.isArchived } }
    var showCreate by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(TransactionKind.EXPENSE) }
    var accountId by remember(activeAccounts) { mutableStateOf(activeAccounts.firstOrNull()?.id.orEmpty()) }
    var categoryId by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf(RecurrenceFrequency.MONTHLY) }
    var autoPost by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf(true) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    val availableCats = remember(categories, kind) {
        val t = if (kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        categories.filter { !it.isArchived && it.type == t }
    }
    LaunchedEffect(availableCats) {
        if (categoryId.isBlank() || availableCats.none { it.id == categoryId }) {
            categoryId = availableCats.firstOrNull()?.id.orEmpty()
        }
    }

    val errInvalidAmount = stringResource(R.string.err_invalid_amount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title)) },
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
                    title = ""
                    amountText = ""
                    errMsg = null
                    showCreate = true
                },
                modifier = Modifier.testTag("add_recurring_fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_recurring))
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
            if (pendingOccurrences.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.pending_recurring_banner),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(pendingOccurrences, key = { it.id }) { occ ->
                    val tpl = templates.firstOrNull { it.id == occ.templateId }
                    if (tpl != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tpl.title, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${MoneyFormatter.formatBdt(tpl.amountPoisha, isBangla)} • ${LocalDate.ofEpochDay(occ.scheduledDateEpochDay)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                IconButton(onClick = { onConfirmOccurrence(occ.id) }) {
                                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.recurring_post_now))
                                }
                                IconButton(onClick = { onSkipOccurrence(occ.id) }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recurring_skip_occurrence))
                                }
                            }
                        }
                    }
                }
            }

            if (templates.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Repeat,
                        title = stringResource(R.string.recurring_empty),
                        subtitle = stringResource(R.string.onboarding_desc_1)
                    )
                }
            } else {
                items(templates, key = { it.id }) { tpl ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tpl.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "${MoneyFormatter.formatBdt(tpl.amountPoisha, isBangla)} • ${frequencyLabel(tpl.frequency)} • ${stringResource(R.string.recurring_next_due)}: ${LocalDate.ofEpochDay(tpl.nextDueDateEpochDay)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = tpl.isEnabled, onCheckedChange = { onToggleEnabled(tpl.id, it) })
                            IconButton(onClick = { onDeleteTemplate(tpl.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.add_recurring)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == TransactionKind.EXPENSE, onClick = { kind = TransactionKind.EXPENSE }, label = { Text(stringResource(R.string.tx_type_expense)) })
                        FilterChip(selected = kind == TransactionKind.INCOME, onClick = { kind = TransactionKind.INCOME }, label = { Text(stringResource(R.string.tx_type_income)) })
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; errMsg = null },
                        label = { Text(stringResource(R.string.recurring_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; errMsg = null },
                        label = { Text(stringResource(R.string.amount_bdt)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RecurrenceFrequency.entries.forEach { f ->
                            FilterChip(selected = frequency == f, onClick = { frequency = f }, label = { Text(frequencyLabel(f)) })
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeAccounts.forEach { acc ->
                            FilterChip(selected = accountId == acc.id, onClick = { accountId = acc.id }, label = { Text(acc.name) })
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableCats.forEach { cat ->
                            FilterChip(selected = categoryId == cat.id, onClick = { categoryId = cat.id }, label = { Text(if (isBangla) cat.nameBn else cat.nameEn) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoPost, onCheckedChange = { autoPost = it })
                        Text(stringResource(R.string.recurring_auto_post), style = MaterialTheme.typography.bodyMedium)
                    }
                    errMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = MoneyParser.parseBdtToPoisha(amountText, allowZero = false)
                        if (parsed !is MoneyParseResult.Valid) {
                            errMsg = errInvalidAmount
                            return@TextButton
                        }
                        onSaveTemplate(
                            title, kind, accountId, categoryId, parsed.poisha,
                            frequency, LocalDate.now(), autoPost, reminder, ""
                        ) { res ->
                            if (res.isSuccess) showCreate = false
                            else errMsg = res.exceptionOrNull()?.message
                        }
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun frequencyLabel(frequency: RecurrenceFrequency): String = when (frequency) {
    RecurrenceFrequency.DAILY -> stringResource(R.string.freq_daily)
    RecurrenceFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
    RecurrenceFrequency.MONTHLY -> stringResource(R.string.freq_monthly)
    RecurrenceFrequency.YEARLY -> stringResource(R.string.freq_yearly)
}
