package com.example.domain

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

object RecurrenceScheduler {

    /**
     * Computes the next scheduled date anchored to [startDate] so that monthly schedules
     * starting on the 31st return to 31st in 31-day months after clamping to 28/29/30.
     */
    fun nextDate(
        currentDue: LocalDate,
        startDate: LocalDate,
        frequency: RecurrenceFrequency
    ): LocalDate {
        return when (frequency) {
            RecurrenceFrequency.DAILY -> currentDue.plusDays(1)
            RecurrenceFrequency.WEEKLY -> currentDue.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> {
                val targetYm = YearMonth.from(currentDue).plusMonths(1)
                val targetDay = min(startDate.dayOfMonth, targetYm.lengthOfMonth())
                targetYm.atDay(targetDay)
            }
            RecurrenceFrequency.YEARLY -> {
                val targetYear = currentDue.year + 1
                val targetYm = YearMonth.of(targetYear, startDate.month)
                val targetDay = min(startDate.dayOfMonth, targetYm.lengthOfMonth())
                targetYm.atDay(targetDay)
            }
        }
    }

    fun dueDatesUpTo(
        startDate: LocalDate,
        nextDueDate: LocalDate,
        endDate: LocalDate?,
        upToDate: LocalDate,
        frequency: RecurrenceFrequency,
        maxOccurrences: Int = 366
    ): List<LocalDate> {
        val effectiveCap = if (endDate != null && endDate.isBefore(upToDate)) endDate else upToDate
        if (nextDueDate.isAfter(effectiveCap)) return emptyList()

        val result = mutableListOf<LocalDate>()
        var cursor = nextDueDate
        while (!cursor.isAfter(effectiveCap) && result.size < maxOccurrences) {
            result.add(cursor)
            cursor = nextDate(cursor, startDate, frequency)
        }
        return result
    }

    fun idempotencyKey(templateId: String, scheduledDateEpochDay: Long): String {
        return "${templateId}_${scheduledDateEpochDay}"
    }
}
