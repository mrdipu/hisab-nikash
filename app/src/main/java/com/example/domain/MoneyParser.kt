package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode

sealed class MoneyParseResult {
    data class Valid(val poisha: Long) : MoneyParseResult()
    data class Invalid(val reason: MoneyParseError) : MoneyParseResult()
}

enum class MoneyParseError {
    EMPTY,
    INVALID_FORMAT,
    TOO_MANY_DECIMALS,
    OVERFLOW,
    NON_POSITIVE
}

object MoneyParser {
    private val MAX_BDT_POISHA = BigDecimal(Long.MAX_VALUE)
    private val MIN_BDT_POISHA = BigDecimal(Long.MIN_VALUE)
    private val VALID_PATTERN = Regex("^[+-]?\\d+(?:\\.\\d+)?$")

    private val BANGLA_TO_ASCII = mapOf(
        '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
        '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
    )

    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(BANGLA_TO_ASCII[ch] ?: ch)
        }
        return sb.toString()
    }

    fun parseBdtToPoisha(
        rawInput: String,
        allowZero: Boolean = false,
        allowNegative: Boolean = false
    ): MoneyParseResult {
        val cleaned = normalizeDigits(rawInput)
            .replace("৳", "")
            .replace(",", "")
            .replace(" ", "")
            .trim()

        if (cleaned.isEmpty()) {
            return MoneyParseResult.Invalid(MoneyParseError.EMPTY)
        }
        if (!VALID_PATTERN.matches(cleaned)) {
            return MoneyParseResult.Invalid(MoneyParseError.INVALID_FORMAT)
        }

        val bd = try {
            BigDecimal(cleaned)
        } catch (_: NumberFormatException) {
            return MoneyParseResult.Invalid(MoneyParseError.INVALID_FORMAT)
        }

        val dotIndex = cleaned.indexOf('.')
        if (dotIndex >= 0 && cleaned.length - dotIndex - 1 > 2) {
            return MoneyParseResult.Invalid(MoneyParseError.TOO_MANY_DECIMALS)
        }

        val poishaBd = try {
            bd.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
        } catch (_: ArithmeticException) {
            return MoneyParseResult.Invalid(MoneyParseError.TOO_MANY_DECIMALS)
        }

        if (poishaBd > MAX_BDT_POISHA || poishaBd < MIN_BDT_POISHA) {
            return MoneyParseResult.Invalid(MoneyParseError.OVERFLOW)
        }

        val poisha = poishaBd.longValueExact()
        if (!allowNegative && poisha < 0L) {
            return MoneyParseResult.Invalid(MoneyParseError.NON_POSITIVE)
        }
        if (!allowZero && poisha == 0L) {
            return MoneyParseResult.Invalid(MoneyParseError.NON_POSITIVE)
        }
        return MoneyParseResult.Valid(poisha)
    }

    fun safeAdd(a: Long, b: Long): Long {
        return Math.addExact(a, b)
    }

    fun safeSubtract(a: Long, b: Long): Long {
        return Math.subtractExact(a, b)
    }
}
