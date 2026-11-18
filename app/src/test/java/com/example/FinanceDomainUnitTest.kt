package com.example

import com.example.domain.BudgetPeriodCalculator
import com.example.domain.MoneyFormatter
import com.example.domain.MoneyParseError
import com.example.domain.MoneyParseResult
import com.example.domain.MoneyParser
import com.example.domain.RecurrenceFrequency
import com.example.domain.RecurrenceScheduler
import com.example.domain.ReportGenerator
import com.example.security.BackupCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceDomainUnitTest {

    @Test
    fun `exact BDT parsing handles poisha, Bangla digits, invalid decimals, and overflow`() {
        assertEquals(MoneyParseResult.Valid(1L), MoneyParser.parseBdtToPoisha("0.01"))
        assertEquals(MoneyParseResult.Valid(1L), MoneyParser.parseBdtToPoisha("৳০.০১"))
        assertEquals(MoneyParseResult.Valid(125050L), MoneyParser.parseBdtToPoisha("৳১,২৫০.৫০"))
        assertEquals(MoneyParseResult.Valid(50000L), MoneyParser.parseBdtToPoisha("500"))

        assertEquals(
            MoneyParseResult.Invalid(MoneyParseError.TOO_MANY_DECIMALS),
            MoneyParser.parseBdtToPoisha("10.123")
        )
        assertEquals(
            MoneyParseResult.Invalid(MoneyParseError.INVALID_FORMAT),
            MoneyParser.parseBdtToPoisha("12.3.4")
        )
        assertEquals(
            MoneyParseResult.Invalid(MoneyParseError.OVERFLOW),
            MoneyParser.parseBdtToPoisha("99999999999999999999")
        )
        assertEquals(
            MoneyParseResult.Invalid(MoneyParseError.NON_POSITIVE),
            MoneyParser.parseBdtToPoisha("0", allowZero = false)
        )
    }

    @Test
    fun `Bangla and English formatting preserve exact numeric value`() {
        val poisha = 123456789L // 12,34,567.89
        val en = MoneyFormatter.formatBdt(poisha, isBangla = false)
        val bn = MoneyFormatter.formatBdt(poisha, isBangla = true)
        assertEquals("৳12,34,567.89", en)
        assertEquals("৳১২,৩৪,৫৬৭.৮৯", bn)
    }

    @Test
    fun `budget start day 31 clamps cleanly in February and April`() {
        // Non-leap year Feb 2025: 31 clamps to Feb 28
        val febStart = BudgetPeriodCalculator.effectiveStartDate(YearMonth.of(2025, 2), 31)
        assertEquals(LocalDate.of(2025, 2, 28), febStart)

        // Leap year Feb 2024: 31 clamps to Feb 29
        val leapFebStart = BudgetPeriodCalculator.effectiveStartDate(YearMonth.of(2024, 2), 31)
        assertEquals(LocalDate.of(2024, 2, 29), leapFebStart)

        // Date Feb 27 belongs to January period (Jan 31 .. Feb 27)
        val periodFeb27 = BudgetPeriodCalculator.periodForDate(LocalDate.of(2025, 2, 27), 31)
        assertEquals("2025-01", periodFeb27.periodKey)
        assertEquals(LocalDate.of(2025, 1, 31), periodFeb27.startDate)
        assertEquals(LocalDate.of(2025, 2, 27), periodFeb27.endDateInclusive)

        // Date Feb 28 starts the February period (Feb 28 .. Mar 30)
        val periodFeb28 = BudgetPeriodCalculator.periodForDate(LocalDate.of(2025, 2, 28), 31)
        assertEquals("2025-02", periodFeb28.periodKey)
        assertEquals(LocalDate.of(2025, 2, 28), periodFeb28.startDate)
        assertEquals(LocalDate.of(2025, 3, 30), periodFeb28.endDateInclusive)
    }

    @Test
    fun `monthly recurrence on 31st clamps in Feb and returns to 31st in March, plus leap year yearly`() {
        val start31 = LocalDate.of(2025, 1, 31)
        val febDue = RecurrenceScheduler.nextDate(start31, start31, RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2025, 2, 28), febDue)
        val marDue = RecurrenceScheduler.nextDate(febDue, start31, RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2025, 3, 31), marDue)

        val leapStart = LocalDate.of(2024, 2, 29)
        val yr2025 = RecurrenceScheduler.nextDate(leapStart, leapStart, RecurrenceFrequency.YEARLY)
        assertEquals(LocalDate.of(2025, 2, 28), yr2025)

        val dueCatchup = RecurrenceScheduler.dueDatesUpTo(
            startDate = start31,
            nextDueDate = start31,
            endDate = null,
            upToDate = LocalDate.of(2025, 4, 1),
            frequency = RecurrenceFrequency.MONTHLY
        )
        assertEquals(
            listOf(
                LocalDate.of(2025, 1, 31),
                LocalDate.of(2025, 2, 28),
                LocalDate.of(2025, 3, 31)
            ),
            dueCatchup
        )
    }

    @Test
    fun `CSV cell escaping neutralizes spreadsheet formula injection`() {
        assertEquals("\"'=CMD('calc')\"", ReportGenerator.escapeCsvCell("=CMD('calc')"))
        assertEquals("\"'+1+1\"", ReportGenerator.escapeCsvCell("+1+1"))
        assertEquals("\"'-100\"", ReportGenerator.escapeCsvCell("-100"))
        assertEquals("\"'@SUM(A1)\"", ReportGenerator.escapeCsvCell("@SUM(A1)"))
        assertEquals("\"hello \"\"world\"\"\"", ReportGenerator.escapeCsvCell("hello \"world\""))
    }

    @Test
    fun `backup encryption rejects wrong passphrase, tampered ciphertext, and corrupted headers`() {
        val payload = "{\"test\":\"হিসাবনিকাশ\"}".toByteArray(Charsets.UTF_8)
        val encrypted = BackupCryptoManager.encryptWithPassphrase(payload, "StrongPass#2026".toCharArray(), iterations = 10_000)

        val decrypted = BackupCryptoManager.decryptWithPassphrase(encrypted, "StrongPass#2026".toCharArray())
        assertTrue(decrypted.isSuccess)
        assertArrayEquals(payload, decrypted.getOrThrow())

        val wrongPass = BackupCryptoManager.decryptWithPassphrase(encrypted, "WrongPass123".toCharArray())
        assertFalse(wrongPass.isSuccess)

        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0xFF).toByte()
        assertFalse(BackupCryptoManager.decryptWithPassphrase(tampered, "StrongPass#2026".toCharArray()).isSuccess)

        val corruptedHeader = encrypted.copyOf()
        corruptedHeader[0] = 'X'.code.toByte()
        assertFalse(BackupCryptoManager.decryptWithPassphrase(corruptedHeader, "StrongPass#2026".toCharArray()).isSuccess)
    }
}
