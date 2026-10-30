package com.example.util

import java.util.regex.Pattern

/**
 * Advanced multi-layer contact sharing and external link prevention filter
 * for Bondly to protect users from off-platform scams, harassment, and privacy leaks.
 */
object ContactSharingFilter {

    const val WARNING_MESSAGE =
        "We care about your privacy and safety. To help protect your personal information, sharing phone numbers, contact details, usernames, or external social media links is restricted on Bondly. Please continue your conversations safely within the app. We always strive to protect your contact information, but we cannot guarantee safety if you choose to share personal details through other means."

    data class CheckResult(
        val isBlocked: Boolean,
        val reason: String = ""
    )

    // Standard Indian phone numbers (+91, 91, 0, or bare 10 digits starting with 6, 7, 8, 9)
    private val PHONE_NUMBER_REGEX = Pattern.compile(
        """(?:\+?91[\s.-]?)?0?[6-9]\d{2}[\s.-]?\d{3}[\s.-]?\d{4}"""
    )

    // Spaced out digits pattern across spaces, tabs, dots, dashes, underscores
    private val SPACED_DIGITS_REGEX = Pattern.compile(
        """(?:\b[0-9][\s.,_\-~*#|/\\:;^+=]{1,3}){7,11}[0-9]\b"""
    )

    // Email regex (standard or obfuscated "user at domain dot com")
    private val EMAIL_REGEX = Pattern.compile(
        """(?i)\b[A-Za-z0-9._%+-]+(?:\s*@\s*|\s+at\s+)[A-Za-z0-9.-]+(?:\s*\.\s*|\s+dot\s+)[A-Za-z]{2,}\b"""
    )

    // Social media indicators, handles, usernames
    private val SOCIAL_HANDLES_REGEX = Pattern.compile(
        """(?i)\b(?:insta(?:gram)?|ig|snap(?:chat)?|wa\.me|whatsapp|tele(?:gram)?|tg|fb|facebook|twitter|x\.com|discord|tiktok|wechat|imo)\s*[:=/@\-]?\s*@?[a-zA-Z0-9._]{3,30}\b|@[a-zA-Z0-9._]{4,30}"""
    )

    // External URLs and URL shorteners
    private val URL_SHORTENER_REGEX = Pattern.compile(
        """(?i)\b(?:https?://|www\.)\S+\b|\b(?:bit\.ly|tinyurl\.com|t\.me|wa\.me|wa\.link|linktr\.ee|cutt\.ly|is\.gd|goo\.gl|t\.co|youtu\.be)\S*\b"""
    )

    // Word representation of numbers in English and Hindi/Hinglish
    private val NUMBER_WORDS = mapOf(
        // English
        "zero" to '0', "one" to '1', "two" to '2', "three" to '3', "four" to '4',
        "five" to '5', "six" to '6', "seven" to '7', "eight" to '8', "nine" to '9',
        // Hindi / Hinglish transliteration
        "shunya" to '0', "ek" to '1', "do" to '2', "teen" to '3', "char" to '4',
        "chaar" to '4', "paanch" to '5', "panch" to '5', "chhah" to '6', "che" to '6',
        "chhe" to '6', "saat" to '7', "sat" to '7', "aath" to '8', "ath" to '8',
        "nau" to '9', "das" to '0'
    )

    /**
     * Normalizes Unicode digit representations (e.g. Devanagari, Arabic, Fullwidth, circled)
     * to standard ASCII digits '0'-'9'.
     */
    private fun normalizeUnicodeDigits(input: String): String {
        val sb = StringBuilder()
        for (ch in input) {
            when (ch) {
                // Devanagari numerals
                in '०'..'९' -> sb.append(ch - '०' + 48)
                // Fullwidth numerals
                in '０'..'９' -> sb.append(ch - '０' + 48)
                // Arabic-Indic numerals
                in '٠'..'٩' -> sb.append(ch - '٠' + 48)
                // Circled digits
                in '①'..'⑨' -> sb.append(ch - '①' + 49)
                '⓪' -> sb.append('0')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Checks whether the given text violates contact sharing rules.
     */
    fun checkText(text: String): CheckResult {
        if (text.isBlank()) return CheckResult(isBlocked = false)

        val normalized = normalizeUnicodeDigits(text).trim()

        // 1. Direct phone regex check
        if (PHONE_NUMBER_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, reason = WARNING_MESSAGE)
        }

        // 2. Spaced digits check
        if (SPACED_DIGITS_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, reason = WARNING_MESSAGE)
        }

        // 3. Email address check
        if (EMAIL_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, reason = WARNING_MESSAGE)
        }

        // 4. Social media handle check
        if (SOCIAL_HANDLES_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, reason = WARNING_MESSAGE)
        }

        // 5. Shortened / external URL check
        if (URL_SHORTENER_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, reason = WARNING_MESSAGE)
        }

        // 6. Multi-line or whitespace-separated digit extraction
        // Handles patterns like:
        // 6
        // 5
        // 2
        // 3
        // 5
        // 8
        // or "6 - 5 - 2 - 3 - 5 - 8..."
        val allDigits = normalized.filter { it.isDigit() }
        if (allDigits.length in 10..12) {
            val candidate = when {
                allDigits.length == 12 && allDigits.startsWith("91") -> allDigits.substring(2)
                allDigits.length == 11 && allDigits.startsWith("0") -> allDigits.substring(1)
                allDigits.length == 10 -> allDigits
                else -> ""
            }
            if (candidate.length == 10 && candidate[0] in listOf('6', '7', '8', '9')) {
                return CheckResult(isBlocked = true, reason = WARNING_MESSAGE)
            }
        }

        // 7. Number words detection (e.g. "nine eight seven six...")
        val tokens = normalized.lowercase().split(Regex("[\\s,.-]+"))
        var consecutiveWords = 0
        var totalNumberWords = 0
        for (token in tokens) {
            if (NUMBER_WORDS.containsKey(token)) {
                consecutiveWords++
                totalNumberWords++
                if (consecutiveWords >= 4 || totalNumberWords >= 6) {
                    return CheckResult(isBlocked = true, reason = WARNING_MESSAGE)
                }
            } else {
                consecutiveWords = 0
            }
        }

        // 8. Mixed word and digit sequence detection
        val mixedDigitsBuilder = StringBuilder()
        for (token in tokens) {
            val wordVal = NUMBER_WORDS[token]
            if (wordVal != null) {
                mixedDigitsBuilder.append(wordVal)
            } else {
                val d = token.filter { it.isDigit() }
                if (d.isNotEmpty()) {
                    mixedDigitsBuilder.append(d)
                }
            }
        }
        val mixedSeq = mixedDigitsBuilder.toString()
        if (mixedSeq.length in 10..12) {
            val candidate = when {
                mixedSeq.length == 12 && mixedSeq.startsWith("91") -> mixedSeq.substring(2)
                mixedSeq.length == 11 && mixedSeq.startsWith("0") -> mixedSeq.substring(1)
                mixedSeq.length == 10 -> mixedSeq
                else -> ""
            }
            if (candidate.length == 10 && candidate[0] in listOf('6', '7', '8', '9')) {
                return CheckResult(isBlocked = true, reason = WARNING_MESSAGE)
            }
        }

        return CheckResult(isBlocked = false)
    }

    /**
     * Masks any phone numbers for safe display on public profiles.
     */
    fun maskMobile(mobile: String): String {
        val digits = mobile.filter { it.isDigit() }
        if (digits.length >= 10) {
            val last4 = digits.takeLast(4)
            return "+91 ••••• $last4"
        }
        return "+91 ••••• ••••"
    }
}
