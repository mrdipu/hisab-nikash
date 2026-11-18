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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CategoryEntity
import com.example.data.local.TransactionKind
import com.example.domain.AccountSummary
import com.example.domain.AnalyticsRangePreset
import com.example.domain.BudgetPeriodCalculator
import com.example.domain.CategoryBreakdown
import com.example.domain.DailyPoint
import com.example.domain.LedgerItem
import com.example.domain.MoneyFormatter
import com.example.domain.PeriodBarPoint
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.DailySpendingTrendChart
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.theme.BrandExpenseRed
import com.example.ui.theme.BrandIncomeGreen
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AnalyticsScreen(
    items: List<LedgerItem>,
    accounts: List<AccountSummary>,
    categories: List<CategoryEntity>,
    budgetStartDay: Int,
    isBangla: Boolean,
    hideBalances: Boolean,
    onOpenReports: () -> Unit
) {
    var preset by remember { mutableStateOf(AnalyticsRangePreset.THIS_MONTH) }
    var customDaysBack by remember { mutableIntStateOf(14) }

    val zone = BudgetPeriodCalculator.DHAKA_ZONE
    val today = LocalDate.now(zone)

    val (startUtc, endUtc) = remember(preset, customDaysBack, budgetStartDay, today) {
        when (preset) {
            AnalyticsRangePreset.LAST_7_DAYS -> {
                val start = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
            AnalyticsRangePreset.LAST_30_DAYS -> {
                val start = today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
            AnalyticsRangePreset.THIS_MONTH -> {
                val p = BudgetPeriodCalculator.periodForDate(today, budgetStartDay)
                p.startUtcMillis to p.endUtcExclusiveMillis
            }
            AnalyticsRangePreset.LAST_MONTH -> {
                val cur = BudgetPeriodCalculator.periodForDate(today, budgetStartDay)
                val prevYm = YearMonth.parse(cur.periodKey).minusMonths(1)
                val p = BudgetPeriodCalculator.periodForKey(prevYm.toString(), budgetStartDay)
                p.startUtcMillis to p.endUtcExclusiveMillis
            }
            AnalyticsRangePreset.LAST_3_MONTHS -> {
                val start = today.minusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
            AnalyticsRangePreset.THIS_YEAR -> {
                val start = today.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
            AnalyticsRangePreset.CUSTOM -> {
                val start = today.minusDays(customDaysBack.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                start to end
            }
        }
    }

    // Transfers & Adjustments are excluded from spending/income analytics
    val cashFlows = remember(items, startUtc, endUtc) {
        items.filterIsInstance<LedgerItem.CashFlow>()
            .filter { it.timestampUtc >= startUtc && it.timestampUtc < endUtc }
    }

    val totalIncome = remember(cashFlows) {
        cashFlows.filter { it.kind == TransactionKind.INCOME }.sumOf { it.amountPoisha }
    }
    val totalExpense = remember(cashFlows) {
        cashFlows.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amountPoisha }
    }

    val categoryBreakdowns = remember(cashFlows, categories, totalExpense) {
        val catMap = categories.associateBy { it.id }
        cashFlows
            .filter { it.kind == TransactionKind.EXPENSE }
            .groupBy { it.categoryId }
            .mapNotNull { (catId, list) ->
                val cat = catMap[catId] ?: return@mapNotNull null
                val sum = list.sumOf { it.amountPoisha }
                val pct = if (totalExpense > 0L) (sum.toFloat() / totalExpense.toFloat()) * 100f else 0f
                CategoryBreakdown(cat, sum, pct)
            }
            .sortedByDescending { it.totalPoisha }
    }

    val dailyPoints = remember(cashFlows, startUtc, endUtc) {
        val byDay = cashFlows
            .filter { it.kind == TransactionKind.EXPENSE }
            .groupBy { Instant.ofEpochMilli(it.timestampUtc).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.amountPoisha } }
        val startDate = Instant.ofEpochMilli(startUtc).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(endUtc - 1).atZone(zone).toLocalDate()
        val days = mutableListOf<DailyPoint>()
        var cursor = startDate
        var guard = 0
        while (!cursor.isAfter(endDate) && guard < 90) {
            days.add(DailyPoint(cursor, byDay[cursor] ?: 0L))
            cursor = cursor.plusDays(1)
            guard++
        }
        days
    }

    val monthlyBars = remember(items, budgetStartDay) {
        val allCf = items.filterIsInstance<LedgerItem.CashFlow>()
        val curYm = YearMonth.parse(BudgetPeriodCalculator.currentPeriod(configuredStartDay = budgetStartDay).periodKey)
        (5 downTo 0).map { offset ->
            val ym = curYm.minusMonths(offset.toLong())
            val p = BudgetPeriodCalculator.periodForKey(ym.toString(), budgetStartDay)
            val rows = allCf.filter { it.timestampUtc >= p.startUtcMillis && it.timestampUtc < p.endUtcExclusiveMillis }
            PeriodBarPoint(
                label = ym.month.name.take(3),
                incomePoisha = rows.filter { it.kind == TransactionKind.INCOME }.sumOf { it.amountPoisha },
                expensePoisha = rows.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amountPoisha }
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.analytics_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onOpenReports) {
                    Icon(Icons.Filled.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.reports_title))
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = preset == AnalyticsRangePreset.LAST_7_DAYS, onClick = { preset = AnalyticsRangePreset.LAST_7_DAYS }, label = { Text(stringResource(R.string.filter_7d)) })
                FilterChip(selected = preset == AnalyticsRangePreset.LAST_30_DAYS, onClick = { preset = AnalyticsRangePreset.LAST_30_DAYS }, label = { Text(stringResource(R.string.filter_30d)) })
                FilterChip(selected = preset == AnalyticsRangePreset.THIS_MONTH, onClick = { preset = AnalyticsRangePreset.THIS_MONTH }, label = { Text(stringResource(R.string.filter_this_month)) })
                FilterChip(selected = preset == AnalyticsRangePreset.LAST_MONTH, onClick = { preset = AnalyticsRangePreset.LAST_MONTH }, label = { Text(stringResource(R.string.filter_last_month)) })
                FilterChip(selected = preset == AnalyticsRangePreset.LAST_3_MONTHS, onClick = { preset = AnalyticsRangePreset.LAST_3_MONTHS }, label = { Text(stringResource(R.string.filter_3m)) })
                FilterChip(selected = preset == AnalyticsRangePreset.THIS_YEAR, onClick = { preset = AnalyticsRangePreset.THIS_YEAR }, label = { Text(stringResource(R.string.filter_this_year)) })
                FilterChip(selected = preset == AnalyticsRangePreset.CUSTOM, onClick = { preset = AnalyticsRangePreset.CUSTOM }, label = { Text(stringResource(R.string.filter_custom)) })
            }
            if (preset == AnalyticsRangePreset.CUSTOM) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(14, 45, 60, 180).forEach { d ->
                        FilterChip(
                            selected = customDaysBack == d,
                            onClick = { customDaysBack = d },
                            label = { Text("${MoneyFormatter.localizeDigits(d.toString(), isBangla)}d") }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.period_income), style = MaterialTheme.typography.labelMedium)
                        Text(
                            MoneyFormatter.formatBdt(totalIncome, isBangla, hidden = hideBalances),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandIncomeGreen
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.period_expense), style = MaterialTheme.typography.labelMedium)
                        Text(
                            MoneyFormatter.formatBdt(totalExpense, isBangla, hidden = hideBalances),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BrandExpenseRed
                        )
                    }
                }
            }
        }

        if (cashFlows.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.BarChart,
                    title = stringResource(R.string.empty_transactions),
                    subtitle = stringResource(R.string.net_cash_flow_explainer)
                )
            }
        } else {
            if (categoryBreakdowns.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = stringResource(R.string.spending_by_category),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            CategoryDonutChart(
                                items = categoryBreakdowns,
                                isBangla = isBangla,
                                hidden = hideBalances
                            )
                            categoryBreakdowns.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isBangla) item.category.nameBn else item.category.nameEn,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = MoneyFormatter.formatBdt(item.totalPoisha, isBangla, hidden = hideBalances),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { (item.sharePercent / 100f).coerceIn(0f, 1f) },
                                        color = Color(item.category.colorHex),
                                        modifier = Modifier.fillMaxWidth().height(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.daily_spending),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        DailySpendingTrendChart(points = dailyPoints)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.monthly_trends),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IncomeExpenseBarChart(points = monthlyBars, isBangla = isBangla)
                }
            }
        }

        if (accounts.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.accounts_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        accounts.filter { !it.isArchived }.forEach { acc ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(acc.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    MoneyFormatter.formatBdt(acc.ledgerBalancePoisha, isBangla, hidden = hideBalances),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(onClick = onOpenReports, modifier = Modifier.fillMaxWidth()) {
                            Text("${stringResource(R.string.export_csv)} / ${stringResource(R.string.export_pdf)}")
                        }
                    }
                }
            }
        }
    }
}
