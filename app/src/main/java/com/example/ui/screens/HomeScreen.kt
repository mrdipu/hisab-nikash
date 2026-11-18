package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DateFormatStyle
import com.example.data.local.TransactionKind
import com.example.domain.BudgetPeriodCalculator
import com.example.domain.BudgetProgress
import com.example.domain.LedgerItem
import com.example.domain.MoneyFormatter
import com.example.domain.SavingsGoalSummary
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.OfficialLogoImage
import com.example.ui.components.SectionHeader
import com.example.ui.components.categoryIconForKey
import com.example.ui.components.formatUtcDate
import com.example.ui.state.DashboardState
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandExpenseRed
import com.example.ui.theme.BrandIncomeGreen
import com.example.ui.theme.BrandPrimaryGreen
import com.example.ui.theme.BrandSecondaryGreen
import java.time.ZonedDateTime

@Composable
fun HomeScreen(
    state: DashboardState,
    budgets: List<BudgetProgress>,
    goals: List<SavingsGoalSummary>,
    isBangla: Boolean,
    hideBalances: Boolean,
    dateFormatStyle: DateFormatStyle,
    onToggleHideBalances: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenRecurring: () -> Unit,
    onQuickAddExpense: () -> Unit,
    onQuickAddIncome: () -> Unit,
    onQuickTransfer: () -> Unit,
    onOpenGoals: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenTransactions: () -> Unit,
    onTransactionClick: (LedgerItem) -> Unit
) {
    val nowDhaka = ZonedDateTime.now(BudgetPeriodCalculator.DHAKA_ZONE)
    val greeting = when (nowDhaka.hour) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..16 -> stringResource(R.string.greeting_afternoon)
        in 17..20 -> stringResource(R.string.greeting_evening)
        else -> stringResource(R.string.greeting_night)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OfficialLogoImage(size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "$greeting!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${stringResource(R.string.app_name)} • ${MoneyFormatter.localizeDigits(state.currentPeriod.periodKey, isBangla)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = onToggleHideBalances,
                        modifier = Modifier.testTag("toggle_balance_visibility")
                    ) {
                        Icon(
                            imageVector = if (hideBalances) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.toggle_visibility_desc)
                        )
                    }
                    IconButton(
                        onClick = onOpenAlerts,
                        modifier = Modifier.testTag("alerts_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.alerts_shortcut_desc)
                        )
                    }
                }
            }
        }

        if (state.pendingRecurringCount > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${stringResource(R.string.pending_recurring_banner)} (${MoneyFormatter.localizeDigits(state.pendingRecurringCount.toString(), isBangla)})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenRecurring) {
                            Text(stringResource(R.string.review_now))
                        }
                    }
                }
            }
        }

        // Hero Balance Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandPrimaryGreen, Color(0xFF19574B), BrandBlue)
                        )
                    )
                    .clickable { onOpenAccounts() }
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.total_balance),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${MoneyFormatter.localizeDigits(state.activeAccounts.size.toString(), isBangla)} ${stringResource(R.string.accounts_title)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = MoneyFormatter.formatBdt(state.totalBalancePoisha, isBangla, hidden = hideBalances),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.testTag("home_total_balance")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.available_unreserved_balance),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Text(
                                text = MoneyFormatter.formatBdt(state.unreservedTotalPoisha, isBangla, hidden = hideBalances),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.reserved_for_goals),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                            Text(
                                text = MoneyFormatter.formatBdt(state.totalEarmarkedPoisha, isBangla, hidden = hideBalances),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.22f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PeriodMetricColumn(
                            label = stringResource(R.string.period_income),
                            value = MoneyFormatter.formatBdt(state.periodIncomePoisha, isBangla, hidden = hideBalances),
                            accent = Color(0xFF6EE7B7)
                        )
                        PeriodMetricColumn(
                            label = stringResource(R.string.period_expense),
                            value = MoneyFormatter.formatBdt(state.periodExpensePoisha, isBangla, hidden = hideBalances),
                            accent = Color(0xFFFCA5A5)
                        )
                        PeriodMetricColumn(
                            label = stringResource(R.string.net_cash_flow),
                            value = MoneyFormatter.formatBdt(state.netCashFlowPoisha, isBangla, hidden = hideBalances, showPositiveSign = true),
                            accent = Color.White
                        )
                    }
                }
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionTile(
                    icon = Icons.Filled.ArrowUpward,
                    label = stringResource(R.string.quick_add_expense),
                    tint = BrandExpenseRed,
                    tag = "quick_expense_btn",
                    onClick = onQuickAddExpense,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                QuickActionTile(
                    icon = Icons.Filled.ArrowDownward,
                    label = stringResource(R.string.quick_add_income),
                    tint = BrandIncomeGreen,
                    tag = "quick_income_btn",
                    onClick = onQuickAddIncome,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                QuickActionTile(
                    icon = Icons.Filled.SwapHoriz,
                    label = stringResource(R.string.quick_transfer),
                    tint = BrandBlue,
                    tag = "quick_transfer_btn",
                    onClick = onQuickTransfer,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                QuickActionTile(
                    icon = Icons.Filled.Savings,
                    label = stringResource(R.string.quick_allocate_goal),
                    tint = BrandPrimaryGreen,
                    tag = "quick_goal_btn",
                    onClick = onOpenGoals,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.accounts.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.AccountBalanceWallet,
                    title = stringResource(R.string.empty_home_title),
                    subtitle = stringResource(R.string.empty_home_desc),
                    action = {
                        Button(onClick = onOpenAccounts, modifier = Modifier.testTag("home_create_account_btn")) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.add_account))
                        }
                    }
                )
            }
        }

        // Income vs Expense Chart
        if (state.monthlyBars.any { it.incomePoisha > 0L || it.expensePoisha > 0L }) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(title = stringResource(R.string.income_vs_expense_title))
                        Spacer(modifier = Modifier.height(8.dp))
                        IncomeExpenseBarChart(points = state.monthlyBars, isBangla = isBangla)
                        state.topExpenseCategoryName?.let { topName ->
                            Spacer(modifier = Modifier.height(10.dp))
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        "${stringResource(R.string.top_expense_category)}: $topName (${MoneyFormatter.formatBdt(state.topExpenseCategoryPoisha, isBangla, hidden = hideBalances)})",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Overall Budget card
        val overallBudget = budgets.firstOrNull { it.categoryId == null } ?: budgets.firstOrNull()
        if (overallBudget != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.clickable { onOpenBudget() }
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.budget_overview_title),
                            actionLabel = stringResource(R.string.see_all),
                            onAction = onOpenBudget
                        )
                        val progress = (overallBudget.percentUsed / 100f).coerceIn(0f, 1f)
                        val barColor = when {
                            overallBudget.isOverspent -> BrandExpenseRed
                            overallBudget.percentUsed >= 80f -> Color(0xFFE67E22)
                            else -> BrandSecondaryGreen
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            color = barColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "${stringResource(R.string.budget_spent)}: ${MoneyFormatter.formatBdt(overallBudget.spentPoisha, isBangla, hidden = hideBalances)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = MoneyFormatter.formatBdt(overallBudget.limitPoisha, isBangla, hidden = hideBalances),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // Savings Goals preview
        if (goals.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.clickable { onOpenGoals() }
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.savings_progress_title),
                            actionLabel = stringResource(R.string.see_all),
                            onAction = onOpenGoals
                        )
                        goals.take(2).forEach { goal ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(goal.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${MoneyFormatter.formatBdt(goal.reservedPoisha, isBangla, hidden = hideBalances)} / ${MoneyFormatter.formatBdt(goal.targetAmountPoisha, isBangla, hidden = hideBalances)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { goal.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Activity
        item {
            SectionHeader(
                title = stringResource(R.string.recent_transactions),
                actionLabel = if (state.recentItems.isNotEmpty()) stringResource(R.string.see_all) else null,
                onAction = onOpenTransactions
            )
        }

        items(state.recentItems, key = { it.id }) { item ->
            LedgerItemRow(
                item = item,
                isBangla = isBangla,
                hideBalances = hideBalances,
                dateFormatStyle = dateFormatStyle,
                onClick = { onTransactionClick(item) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun PeriodMetricColumn(label: String, value: String, accent: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() }
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LedgerItemRow(
    item: LedgerItem,
    isBangla: Boolean,
    hideBalances: Boolean,
    dateFormatStyle: DateFormatStyle,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint, bgColor) = when (item) {
                is LedgerItem.CashFlow -> Triple(
                    categoryIconForKey(item.categoryIconKey),
                    Color(item.categoryColorHex),
                    Color(item.categoryColorHex).copy(alpha = 0.14f)
                )
                is LedgerItem.Transfer -> Triple(
                    Icons.Filled.SwapHoriz,
                    BrandBlue,
                    BrandBlue.copy(alpha = 0.14f)
                )
                is LedgerItem.Adjustment -> Triple(
                    Icons.Filled.Tune,
                    BrandPrimaryGreen,
                    BrandPrimaryGreen.copy(alpha = 0.14f)
                )
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val title = when (item) {
                    is LedgerItem.CashFlow -> if (isBangla) item.categoryNameBn else item.categoryNameEn
                    is LedgerItem.Transfer -> "${item.fromAccountName} → ${item.toAccountName}"
                    is LedgerItem.Adjustment -> "${stringResource(R.string.tx_type_adjustment)} (${item.accountName})"
                }
                val subtitle = when (item) {
                    is LedgerItem.CashFlow -> listOfNotNull(
                        item.accountName,
                        item.merchant.takeIf { it.isNotBlank() },
                        item.note.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    is LedgerItem.Transfer -> item.note.ifBlank { stringResource(R.string.tx_type_transfer) }
                    is LedgerItem.Adjustment -> item.reason
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$subtitle • ${formatUtcDate(item.timestampUtc, dateFormatStyle, isBangla)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            val amountText = when (item) {
                is LedgerItem.CashFlow -> {
                    val sign = if (item.kind == TransactionKind.INCOME) "+" else "-"
                    val formatted = MoneyFormatter.formatBdt(item.amountPoisha, isBangla, hidden = hideBalances)
                    if (hideBalances) formatted else "$sign$formatted"
                }
                is LedgerItem.Transfer -> MoneyFormatter.formatBdt(item.amountPoisha, isBangla, hidden = hideBalances)
                is LedgerItem.Adjustment -> MoneyFormatter.formatBdt(
                    item.deltaPoisha,
                    isBangla,
                    hidden = hideBalances,
                    showPositiveSign = true
                )
            }
            val amountColor = when (item) {
                is LedgerItem.CashFlow -> if (item.kind == TransactionKind.INCOME) BrandIncomeGreen else BrandExpenseRed
                is LedgerItem.Transfer -> BrandBlue
                is LedgerItem.Adjustment -> if (item.deltaPoisha >= 0L) BrandIncomeGreen else BrandExpenseRed
            }

            Text(
                text = amountText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}
