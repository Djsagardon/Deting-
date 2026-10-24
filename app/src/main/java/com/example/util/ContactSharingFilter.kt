package com.example.util

import java.util.regex.Pattern

/**
 * Strict contact sharing filter to protect real Indian dating app users from
 * harassment, fraud, off-platform exploitation, and scamming.
 *
 * Scans for:
 * 1. Indian phone numbers (10 digits starting with 6-9, with or without +91, 0, or 91,
 *    and with any combination of spaces, hyphens, periods, or punctuation).
 * 2. Spaced digits (e.g. "9 8 7 6 5 4 3 2 1 0" or "9.8.7.6.5.4.3.2.1.0").
 * 3. Words representing digits in English ("nine eight seven six...") and Hindi/Hinglish ("nau aath saat...").
 * 4. Email addresses (standard or obfuscated like "user at gmail dot com").
 * 5. Social media handles (Instagram, Snapchat, WhatsApp, Telegram, Facebook, Twitter/X).
 */
object ContactSharingFilter {

    sealed class ViolationType(val reason: String) {
        object PhoneNumber : ViolationType("Sharing phone numbers is strictly prohibited to protect your safety and privacy.")
        object SpacedDigits : ViolationType("Obfuscated or spaced phone numbers are not allowed.")
        object WordDigits : ViolationType("Spelling out phone numbers in words is not allowed.")
        object EmailAddress : ViolationType("Sharing email addresses is not permitted in chats.")
        object SocialMediaHandle : ViolationType("Sharing social media handles (Instagram, Snapchat, WhatsApp, Telegram) is not allowed.")
    }

    data class CheckResult(
        val isBlocked: Boolean,
        val violation: ViolationType? = null
    )

    // Standard and obfuscated Indian phone numbers (+91, 91, 0, or bare 10 digits starting with 6, 7, 8, or 9)
    private val INDIAN_PHONE_REGEX = Pattern.compile(
        """(?:\+?91[\s.-]?)?0?[6-9]\d{2}[\s.-]?\d{3}[\s.-]?\d{4}"""
    )

    // Spaced out digits pattern: looks for 8 to 11 single digits separated by spaces or punctuation
    private val SPACED_DIGITS_REGEX = Pattern.compile(
        """(?:\b[0-9][\s.,_\-~*#]{1,3}){7,11}[0-9]\b"""
    )

    // Email pattern (standard and obfuscated)
    private val EMAIL_REGEX = Pattern.compile(
        """(?i)\b[A-Za-z0-9._%+-]+(?:\s*@\s*|\s+at\s+)[A-Za-z0-9.-]+(?:\s*\.\s*|\s+dot\s+)[A-Za-z]{2,}\b"""
    )

    // Social media indicators & handles
    private val SOCIAL_HANDLES_REGEX = Pattern.compile(
        """(?i)\b(?:insta(?:gram)?|ig|snap(?:chat)?|wa\.me|whatsapp|tele(?:gram)?|tg|fb|facebook|twitter|x\.com)\s*[:=/@-]?\s*@?[a-zA-Z0-9._]{3,30}\b|@[a-zA-Z0-9._]{4,30}"""
    )

    // Words representing numbers (English and Hinglish)
    private val NUMBER_WORDS = setOf(
        // English
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        // Hindi / Hinglish transliterated
        "shunya", "ek", "do", "teen", "char", "chaar", "paanch", "panch", "chhah", "che", "chhe", "saat", "aath", "ath", "nau", "das"
    )

    /**
     * Checks whether the provided text contains any prohibited contact details.
     */
    fun checkText(text: String): CheckResult {
        if (text.isBlank()) return CheckResult(isBlocked = false)

        val normalized = text.trim()

        // 1. Check Indian Phone Number Regex
        if (INDIAN_PHONE_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, violation = ViolationType.PhoneNumber)
        }

        // 2. Check Spaced Digits Regex
        if (SPACED_DIGITS_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, violation = ViolationType.SpacedDigits)
        }

        // 3. Check Email Regex
        if (EMAIL_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, violation = ViolationType.EmailAddress)
        }

        // 4. Check Social Media Handles Regex
        if (SOCIAL_HANDLES_REGEX.matcher(normalized).find()) {
            return CheckResult(isBlocked = true, violation = ViolationType.SocialMediaHandle)
        }

        // 5. Check if consecutive number words exist (5 or more number words in text)
        val words = normalized.lowercase().split(Regex("[\\s,.-]+"))
        var consecutiveCount = 0
        var totalNumberWords = 0
        for (word in words) {
            if (NUMBER_WORDS.contains(word)) {
                consecutiveCount++
                totalNumberWords++
                if (consecutiveCount >= 4 || totalNumberWords >= 6) {
                    return CheckResult(isBlocked = true, violation = ViolationType.WordDigits)
                }
            } else {
                consecutiveCount = 0
            }
        }

        // 6. Check continuous digit extraction (stripping non-digits to see if a 10-digit number is formed)
        val digitsOnly = normalized.filter { it.isDigit() }
        if (digitsOnly.length in 10..12) {
            val candidate = if (digitsOnly.length == 12 && digitsOnly.startsWith("91")) {
                digitsOnly.substring(2)
            } else if (digitsOnly.length == 11 && digitsOnly.startsWith("0")) {
                digitsOnly.substring(1)
            } else {
                digitsOnly
            }
            if (candidate.length == 10 && candidate[0] in listOf('6', '7', '8', '9')) {
                return CheckResult(isBlocked = true, violation = ViolationType.PhoneNumber)
            }
        }

        return CheckResult(isBlocked = false)
    }

    /**
     * Masks any partially matched contact details for public profile display.
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
