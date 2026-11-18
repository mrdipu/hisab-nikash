package com.example.domain

import kotlin.math.abs

object MoneyFormatter {
    private val ASCII_TO_BANGLA = mapOf(
        '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
        '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
    )

    fun toBanglaDigits(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(ASCII_TO_BANGLA[ch] ?: ch)
        }
        return sb.toString()
    }

    fun localizeDigits(text: String, isBangla: Boolean): String {
        return if (isBangla) toBanglaDigits(text) else text
    }

    /**
     * Formats signed poisha into BDT currency display with South Asian grouping (e.g., 1,25,000.50).
     * Language selection only changes digit script, never the underlying numeric value.
     */
    fun formatBdt(
        poisha: Long,
        isBangla: Boolean,
        hidden: Boolean = false,
        showPositiveSign: Boolean = false,
        alwaysShowDecimals: Boolean = false
    ): String {
        if (hidden) {
            return if (isBangla) "৳ •••••" else "৳ •••••"
        }

        val isNegative = poisha < 0L
        // Handle Long.MIN_VALUE safely
        val absTaka: Long
        val absRemPoisha: Long
        if (poisha == Long.MIN_VALUE) {
            absTaka = Long.MAX_VALUE / 100L
            absRemPoisha = 8L
        } else {
            val absVal = abs(poisha)
            absTaka = absVal / 100L
            absRemPoisha = absVal % 100L
        }

        val groupedInt = formatSouthAsianGrouping(absTaka)
        val decimalPart = if (alwaysShowDecimals || absRemPoisha != 0L) {
            "." + absRemPoisha.toString().padStart(2, '0')
        } else {
            ""
        }

        val prefix = when {
            isNegative -> "-"
            showPositiveSign && poisha > 0L -> "+"
            else -> ""
        }

        val raw = "${prefix}৳$groupedInt$decimalPart"
        return if (isBangla) toBanglaDigits(raw) else raw
    }

    fun formatPlainDecimal(poisha: Long): String {
        val isNegative = poisha < 0L
        val absVal = if (poisha == Long.MIN_VALUE) Long.MAX_VALUE else abs(poisha)
        val taka = absVal / 100L
        val rem = absVal % 100L
        val sign = if (isNegative) "-" else ""
        return "$sign$taka.${rem.toString().padStart(2, '0')}"
    }

    private fun formatSouthAsianGrouping(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val lastThree = s.substring(s.length - 3)
        val remaining = s.substring(0, s.length - 3)
        val sb = StringBuilder()
        var count = 0
        for (i in remaining.length - 1 downTo 0) {
            sb.append(remaining[i])
            count++
            if (count == 2 && i != 0) {
                sb.append(',')
                count = 0
            }
        }
        return sb.reverse().toString() + "," + lastThree
    }
}
