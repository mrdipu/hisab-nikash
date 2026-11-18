package com.example.domain

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.local.TransactionKind
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

data class ReportSummary(
    val periodLabel: String,
    val totalIncomePoisha: Long,
    val totalExpensePoisha: Long,
    val netCashFlowPoisha: Long,
    val leadingCategoryName: String?,
    val leadingCategoryPoisha: Long,
    val accountBalances: List<AccountSummary>,
    val categoryTotals: List<Pair<String, Long>>,
    val budgetRows: List<BudgetProgress>,
    val transactions: List<LedgerItem.CashFlow>
)

object ReportGenerator {
    private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(BudgetPeriodCalculator.DHAKA_ZONE)

    /**
     * Escapes CSV cell per RFC-4180 and neutralizes spreadsheet formula injection (=, +, -, @, \t, \r).
     */
    fun escapeCsvCell(raw: String): String {
        val trimmedForCheck = raw.trimStart()
        val formulaSafe = if (trimmedForCheck.isNotEmpty() &&
            trimmedForCheck[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')
        ) {
            "'" + raw
        } else {
            raw
        }
        val escaped = formulaSafe.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun generateCsvBytes(
        transactions: List<LedgerItem.CashFlow>,
        isBangla: Boolean
    ): ByteArray {
        val sb = StringBuilder()
        // UTF-8 BOM helps spreadsheet apps decode Bangla UTF-8 cleanly
        sb.append('\uFEFF')
        sb.append("ID,Date (Asia/Dhaka),Type,Account,Category,Amount (BDT),Merchant/Payee,Tags,Note\n")
        for (tx in transactions) {
            val dateStr = DATE_FMT.format(Instant.ofEpochMilli(tx.timestampUtc))
            val catName = if (isBangla) tx.categoryNameBn else tx.categoryNameEn
            val amountStr = MoneyFormatter.formatPlainDecimal(tx.amountPoisha)
            val row = listOf(
                escapeCsvCell(tx.id),
                escapeCsvCell(dateStr),
                escapeCsvCell(tx.kind.name),
                escapeCsvCell(tx.accountName),
                escapeCsvCell(catName),
                escapeCsvCell(amountStr),
                escapeCsvCell(tx.merchant),
                escapeCsvCell(tx.tags),
                escapeCsvCell(tx.note)
            ).joinToString(",")
            sb.append(row).append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Renders an A4 PDF report locally using Android PdfDocument + StaticLayout + bundled Noto Sans Bengali
     * so Bangla conjuncts and numeric glyphs shape properly on-device.
     */
    fun generatePdfBytes(
        context: Context,
        summary: ReportSummary,
        isBangla: Boolean
    ): Result<ByteArray> {
        return try {
            val bengaliTypeface = ResourcesCompat.getFont(context, R.font.noto_sans_bengali) ?: Typeface.DEFAULT
            val latinTypeface = ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) ?: Typeface.DEFAULT
            val bodyTypeface = if (isBangla) bengaliTypeface else latinTypeface

            val pdf = PdfDocument()
            try {
                val pageWidth = 595 // A4 @ 72dpi
                val pageHeight = 842
                val margin = 40f
                val contentWidth = (pageWidth - margin * 2).toInt()

                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(bengaliTypeface, Typeface.BOLD)
                    textSize = 18f
                    color = Color.parseColor("#123D34")
                }
                val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = bodyTypeface
                    textSize = 10.5f
                    color = Color.parseColor("#455A64")
                }
                val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(bodyTypeface, Typeface.BOLD)
                    textSize = 13f
                    color = Color.parseColor("#064AA0")
                }
                val rowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = bodyTypeface
                    textSize = 10f
                    color = Color.parseColor("#101916")
                }
                val linePaint = Paint().apply {
                    color = Color.parseColor("#CFD8DC")
                    strokeWidth = 1f
                }

                var pageNum = 1
                var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                var canvas = page.canvas
                var y = margin

                fun newPageIfNeeded(requiredHeight: Float) {
                    if (y + requiredHeight > pageHeight - margin) {
                        pdf.finishPage(page)
                        pageNum++
                        page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                        canvas = page.canvas
                        y = margin
                    }
                }

                fun drawStaticText(text: String, paint: TextPaint, width: Int = contentWidth) {
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .build()
                    newPageIfNeeded(layout.height.toFloat() + 6f)
                    canvas.save()
                    canvas.translate(margin, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + 6f
                }

                val headerTitle = if (isBangla) "হিসাবনিকাশ — আর্থিক প্রতিবেদন" else "হিসাবনিকাশ — Financial Report"
                drawStaticText(headerTitle, titlePaint)
                drawStaticText("${summary.periodLabel}  |  Powered by DZ Ai-STUDIO", subPaint)
                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
                y += 12f

                val summaryHeading = if (isBangla) "সারসংক্ষেপ" else "Summary"
                drawStaticText(summaryHeading, sectionPaint)

                val incLabel = if (isBangla) "মোট আয়" else "Total Income"
                val expLabel = if (isBangla) "মোট ব্যয়" else "Total Expenses"
                val netLabel = if (isBangla) "নিট ক্যাশ ফ্লো" else "Net Cash Flow"
                drawStaticText(
                    "$incLabel: ${MoneyFormatter.formatBdt(summary.totalIncomePoisha, isBangla)}\n" +
                        "$expLabel: ${MoneyFormatter.formatBdt(summary.totalExpensePoisha, isBangla)}\n" +
                        "$netLabel: ${MoneyFormatter.formatBdt(summary.netCashFlowPoisha, isBangla)}",
                    rowPaint
                )
                summary.leadingCategoryName?.let {
                    val topLbl = if (isBangla) "সর্বোচ্চ ব্যয়ের খাত" else "Top Expense Category"
                    drawStaticText("$topLbl: $it (${MoneyFormatter.formatBdt(summary.leadingCategoryPoisha, isBangla)})", rowPaint)
                }
                y += 8f

                if (summary.accountBalances.isNotEmpty()) {
                    drawStaticText(if (isBangla) "অ্যাকাউন্ট ব্যালেন্স" else "Account Balances", sectionPaint)
                    for (acc in summary.accountBalances.filter { !it.isArchived }) {
                        drawStaticText(
                            "• ${acc.name}: ${MoneyFormatter.formatBdt(acc.ledgerBalancePoisha, isBangla)}",
                            rowPaint
                        )
                    }
                    y += 8f
                }

                if (summary.categoryTotals.isNotEmpty()) {
                    drawStaticText(if (isBangla) "ক্যাটাগরি অনুযায়ী ব্যয়" else "Category Breakdown", sectionPaint)
                    for ((catName, amount) in summary.categoryTotals) {
                        drawStaticText("• $catName: ${MoneyFormatter.formatBdt(amount, isBangla)}", rowPaint)
                    }
                    y += 8f
                }

                if (summary.budgetRows.isNotEmpty()) {
                    drawStaticText(if (isBangla) "বাজেট তুলনা" else "Budget Comparison", sectionPaint)
                    for (b in summary.budgetRows) {
                        val label = if (isBangla) (b.categoryNameBn ?: "সামগ্রিক") else (b.categoryNameEn ?: "Overall")
                        drawStaticText(
                            "• $label: ${MoneyFormatter.formatBdt(b.spentPoisha, isBangla)} / ${MoneyFormatter.formatBdt(b.limitPoisha, isBangla)}",
                            rowPaint
                        )
                    }
                    y += 8f
                }

                if (summary.transactions.isNotEmpty()) {
                    drawStaticText(if (isBangla) "লেনদেনের তালিকা" else "Transactions", sectionPaint)
                    for (tx in summary.transactions.take(250)) {
                        val dateStr = DATE_FMT.format(Instant.ofEpochMilli(tx.timestampUtc))
                        val sign = if (tx.kind == TransactionKind.INCOME) "+" else "-"
                        val cat = if (isBangla) tx.categoryNameBn else tx.categoryNameEn
                        val amt = MoneyFormatter.formatBdt(tx.amountPoisha, isBangla)
                        val noteSuffix = if (tx.note.isNotBlank()) " (${tx.note})" else ""
                        drawStaticText("$dateStr | $cat | $sign$amt | ${tx.accountName}$noteSuffix", rowPaint)
                    }
                }

                pdf.finishPage(page)
                val bos = ByteArrayOutputStream()
                pdf.writeTo(bos)
                val bytes = bos.toByteArray()
                if (bytes.isEmpty()) {
                    Result.failure(IllegalStateException("Generated PDF was empty"))
                } else {
                    Result.success(bytes)
                }
            } finally {
                runCatching { pdf.close() }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
