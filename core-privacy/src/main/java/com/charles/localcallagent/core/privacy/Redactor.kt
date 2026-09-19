package com.charles.localcallagent.core.privacy

import java.util.regex.Pattern

object Redactor {
    // Match credit cards: formatted (e.g., 4532-0150-1234-5678, 4532 0150 1234 5678) or unformatted 13-19 digit cards
    private val CREDIT_CARD_PATTERN = Pattern.compile(
        "\\b(?:\\d{4}[- ]\\d{4}[- ]\\d{4}[- ]\\d{1,4}|3[47]\\d{2}[- ]\\d{6}[- ]\\d{5}|4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11}|\\d{13,19})\\b"
    )
    // Match SSN (XXX-XX-XXXX)
    private val SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")
    // Match phone numbers with standard delimiters or international prefix
    private val PHONE_PATTERN = Pattern.compile("\\b(?:\\+?1[-.\\s]?)?(?:\\([0-9]{3}\\)|[0-9]{3})[-.\\s][0-9]{3}[-.\\s][0-9]{4}\\b")

    /**
     * Checks if a string of digits passes the Luhn check algorithm.
     */
    fun isValidLuhn(candidate: String): Boolean {
        val digits = candidate.filter { it.isDigit() }
        if (digits.length !in 13..19) return false
        var sum = 0
        var alternate = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    fun redact(input: String?): String {
        if (input == null) return ""
        var result = CREDIT_CARD_PATTERN.matcher(input).replaceAll("[REDACTED_CARD]")
        result = SSN_PATTERN.matcher(result).replaceAll("[REDACTED_SSN]")
        result = PHONE_PATTERN.matcher(result).replaceAll("[REDACTED_PHONE]")
        return result
    }
}
