package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.TransactionKind
import com.example.domain.AccountSummary
import com.example.domain.BudgetPeriodCalculator
import com.example.domain.BudgetProgress
import com.example.domain.LedgerItem
import com.example.domain.MoneyFormatter
import com.example.domain.ReportSummary
import com.example.ui.theme.BrandExpenseRed
import com.example.ui.theme.BrandIncomeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    items: List<LedgerItem>,
    accounts: List<AccountSummary>,
    budgets: List<BudgetProgress>,
    budgetStartDay: Int,
    isBangla: Boolean,
    hideBalances: Boolean,
    onBuildReportBytes: suspend (ReportSummary, Boolean) -> Result<ByteArray>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isAnnual by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val zone = BudgetPeriodCalculator.DHAKA_ZONE
    val today = LocalDate.now(zone)

    val reportSummary = remember(items, accounts, budgets, isAnnual, budgetStartDay, isBangla, today) {
        val (label, startUtc, endUtc) = if (!isAnnual) {
            val p = BudgetPeriodCalculator.periodForDate(today, budgetStartDay)
            Triple(p.periodKey, p.startUtcMillis, p.endUtcExclusiveMillis)
        } else {
            val start = today.withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.plusYears(1).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
            Triple(today.year.toString(), start, end)
        }
        val txs = items.filterIsInstance<LedgerItem.CashFlow>()
            .filter { it.timestampUtc >= startUtc && it.timestampUtc < endUtc }
        val income = txs.filter { it.kind == TransactionKind.INCOME }.sumOf { it.amountPoisha }
        val expense = txs.filter { it.kind == TransactionKind.EXPENSE }.sumOf { it.amountPoisha }
        val catTotals = txs.filter { it.kind == TransactionKind.EXPENSE }
            .groupBy { if (isBangla) it.categoryNameBn else it.categoryNameEn }
            .map { (k, v) -> k to v.sumOf { it.amountPoisha } }
            .sortedByDescending { it.second }
        val leading = catTotals.firstOrNull()
        ReportSummary(
            periodLabel = label,
            totalIncomePoisha = income,
            totalExpensePoisha = expense,
            netCashFlowPoisha = income - expense,
            leadingCategoryName = leading?.first,
            leadingCategoryPoisha = leading?.second ?: 0L,
            accountBalances = accounts,
            categoryTotals = catTotals,
            budgetRows = if (isAnnual) emptyList() else budgets,
            transactions = txs
        )
    }

    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFileName by remember { mutableStateOf("") }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(bytes)
                            it.flush()
                        } ?: error("Cannot open URI for writing")
                    }
                }
                if (res.isSuccess) {
                    statusMessage = "Saved $pendingFileName (${bytes.size} bytes) to $uri"
                    errorMessage = null
                } else {
                    errorMessage = res.exceptionOrNull()?.message ?: "Export write failed"
                }
            }
        }
    }

    fun exportAndSave(asPdf: Boolean) {
        scope.launch {
            val res = onBuildReportBytes(reportSummary, asPdf)
            if (res.isSuccess) {
                val ext = if (asPdf) "pdf" else "csv"
                val fileName = "hisabnikash_report_${reportSummary.periodLabel}.$ext"
                pendingBytes = res.getOrThrow()
                pendingFileName = fileName
                saveLauncher.launch(fileName)
            } else {
                errorMessage = res.exceptionOrNull()?.message ?: "Report generation failed"
            }
        }
    }

    fun shareReport(asPdf: Boolean) {
        scope.launch {
            val res = onBuildReportBytes(reportSummary, asPdf)
            if (res.isFailure) {
                errorMessage = res.exceptionOrNull()?.message ?: "Report generation failed"
                return@launch
            }
            val bytes = res.getOrThrow()
            val ext = if (asPdf) "pdf" else "csv"
            val mime = if (asPdf) "application/pdf" else "text/csv"
            val outFile = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "reports").apply { mkdirs() }
                File(dir, "hisabnikash_${reportSummary.periodLabel}.$ext").apply { writeBytes(bytes) }
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, outFile.name))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isAnnual,
                        onClick = { isAnnual = false },
                        label = { Text(stringResource(R.string.report_period_monthly)) }
                    )
                    FilterChip(
                        selected = isAnnual,
                        onClick = { isAnnual = true },
                        label = { Text(stringResource(R.string.report_period_annual)) }
                    )
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${stringResource(R.string.budget_period_label)}: ${MoneyFormatter.localizeDigits(reportSummary.periodLabel, isBangla)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${stringResource(R.string.period_income)}: ${MoneyFormatter.formatBdt(reportSummary.totalIncomePoisha, isBangla, hidden = hideBalances)}",
                            color = BrandIncomeGreen
                        )
                        Text(
                            "${stringResource(R.string.period_expense)}: ${MoneyFormatter.formatBdt(reportSummary.totalExpensePoisha, isBangla, hidden = hideBalances)}",
                            color = BrandExpenseRed
                        )
                        Text(
                            "${stringResource(R.string.net_cash_flow)}: ${MoneyFormatter.formatBdt(reportSummary.netCashFlowPoisha, isBangla, hidden = hideBalances, showPositiveSign = true)}",
                            fontWeight = FontWeight.Bold
                        )
                        reportSummary.leadingCategoryName?.let {
                            Text("${stringResource(R.string.top_expense_category)}: $it (${MoneyFormatter.formatBdt(reportSummary.leadingCategoryPoisha, isBangla, hidden = hideBalances)})")
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportAndSave(asPdf = false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.TableChart, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.export_csv))
                    }
                    Button(onClick = { exportAndSave(asPdf = true) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.export_pdf))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { shareReport(asPdf = false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${stringResource(R.string.share_report)} CSV")
                    }
                    OutlinedButton(onClick = { shareReport(asPdf = true) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${stringResource(R.string.share_report)} PDF")
                    }
                }
            }

            statusMessage?.let { msg ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Text(msg, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            errorMessage?.let { err ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(err, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (reportSummary.categoryTotals.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.spending_by_category), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            reportSummary.categoryTotals.forEach { (cat, amt) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(cat)
                                    Text(MoneyFormatter.formatBdt(amt, isBangla, hidden = hideBalances), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
