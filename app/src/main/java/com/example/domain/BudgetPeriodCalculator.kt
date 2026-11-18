package com.example.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.min

data class BudgetPeriod(
    val periodKey: String,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val startUtcMillis: Long,
    val endUtcExclusiveMillis: Long
)

object BudgetPeriodCalculator {
    val DHAKA_ZONE: ZoneId = ZoneId.of("Asia/Dhaka")

    fun clampStartDay(day: Int): Int = day.coerceIn(1, 31)

    fun effectiveStartDate(yearMonth: YearMonth, configuredStartDay: Int): LocalDate {
        val clamped = clampStartDay(configuredStartDay)
        val actualDay = min(clamped, yearMonth.lengthOfMonth())
        return yearMonth.atDay(actualDay)
    }

    fun periodForDate(
        date: LocalDate,
        configuredStartDay: Int,
        zoneId: ZoneId = DHAKA_ZONE
    ): BudgetPeriod {
        val ym = YearMonth.from(date)
        val currentMonthStart = effectiveStartDate(ym, configuredStartDay)

        val anchorYm = if (!date.isBefore(currentMonthStart)) ym else ym.minusMonths(1)
        val nextYm = anchorYm.plusMonths(1)

        val startDate = effectiveStartDate(anchorYm, configuredStartDay)
        val nextStartDate = effectiveStartDate(nextYm, configuredStartDay)
        val endDateInclusive = nextStartDate.minusDays(1)

        val startUtcMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endUtcExclusiveMillis = nextStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli()

        return BudgetPeriod(
            periodKey = anchorYm.toString(),
            startDate = startDate,
            endDateInclusive = endDateInclusive,
            startUtcMillis = startUtcMillis,
            endUtcExclusiveMillis = endUtcExclusiveMillis
        )
    }

    fun periodForKey(
        periodKey: String,
        configuredStartDay: Int,
        zoneId: ZoneId = DHAKA_ZONE
    ): BudgetPeriod {
        val ym = YearMonth.parse(periodKey)
        val startDate = effectiveStartDate(ym, configuredStartDay)
        val nextStartDate = effectiveStartDate(ym.plusMonths(1), configuredStartDay)
        return BudgetPeriod(
            periodKey = ym.toString(),
            startDate = startDate,
            endDateInclusive = nextStartDate.minusDays(1),
            startUtcMillis = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            endUtcExclusiveMillis = nextStartDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }

    fun currentPeriod(
        nowMillis: Long = System.currentTimeMillis(),
        configuredStartDay: Int = 1,
        zoneId: ZoneId = DHAKA_ZONE
    ): BudgetPeriod {
        val localDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        return periodForDate(localDate, configuredStartDay, zoneId)
    }
}
