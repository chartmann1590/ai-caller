package com.charles.localcallagent.asr.local

import java.util.regex.Pattern

object RuleSlotExtractor {

    private val PRICE_PATTERN = Pattern.compile(
        """(?:\$?\s*([0-9]+(?:\.[0-9]{2})?)\s*(?:dollars?|bucks?|each|a tire|per tire)?)|(?:\$\s*([0-9]+(?:\.[0-9]{2})?))""",
        Pattern.CASE_INSENSITIVE
    )

    private val YES_PATTERN = Pattern.compile(
        """\b(yes|yeah|yep|sure|absolutely|certainly|we do|we can|of course|right)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val NO_PATTERN = Pattern.compile(
        """\b(no|nope|nah|we don't|we cannot|unfortunately not|we do not|negative)\b""",
        Pattern.CASE_INSENSITIVE
    )

    private val IVR_PATTERN = Pattern.compile(
        """press\s+([0-9]|one|two|three|four|five|six|seven|eight|nine|zero|\*|#)\s+(?:for|to)\s+([a-z\s]+)""",
        Pattern.CASE_INSENSITIVE
    )

    private val HOURS_PATTERN = Pattern.compile(
        """\b([0-9]{1,2}(?::[0-9]{2})?\s*(?:am|pm)?\s*(?:to|-|until)\s*[0-9]{1,2}(?::[0-9]{2})?\s*(?:am|pm))\b""",
        Pattern.CASE_INSENSITIVE
    )

    fun extractSlots(text: String): Map<String, String> {
        val slots = mutableMapOf<String, String>()

        // Check Confirmation
        val hasYes = YES_PATTERN.matcher(text).find()
        val hasNo = NO_PATTERN.matcher(text).find()
        if (hasYes && !hasNo) {
            slots["CONFIRMATION"] = "YES"
        } else if (hasNo && !hasYes) {
            slots["CONFIRMATION"] = "NO"
        }

        // Check Price
        val priceMatcher = PRICE_PATTERN.matcher(text)
        if (priceMatcher.find()) {
            val amount = priceMatcher.group(1) ?: priceMatcher.group(2)
            if (amount != null) {
                slots["PRICE"] = "$$amount"
            }
        }

        // Check IVR options
        val ivrMatcher = IVR_PATTERN.matcher(text)
        if (ivrMatcher.find()) {
            val digitWord = ivrMatcher.group(1)?.lowercase() ?: ""
            val digit = wordToDigit(digitWord)
            val department = ivrMatcher.group(2)?.trim() ?: ""
            slots["IVR_DIGIT"] = digit.toString()
            slots["IVR_DEPT"] = department
        }

        // Check Hours
        val hoursMatcher = HOURS_PATTERN.matcher(text)
        if (hoursMatcher.find()) {
            slots["HOURS"] = hoursMatcher.group(1) ?: ""
        }

        return slots
    }

    fun wordToDigit(word: String): Char {
        return when (word) {
            "0", "zero" -> '0'
            "1", "one" -> '1'
            "2", "two" -> '2'
            "3", "three" -> '3'
            "4", "four" -> '4'
            "5", "five" -> '5'
            "6", "six" -> '6'
            "7", "seven" -> '7'
            "8", "eight" -> '8'
            "9", "nine" -> '9'
            "*" -> '*'
            "#" -> '#'
            else -> '1'
        }
    }
}
