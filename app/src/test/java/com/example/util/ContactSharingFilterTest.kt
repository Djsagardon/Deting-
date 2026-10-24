package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSharingFilterTest {

    @Test
    fun regularText_isNotBlocked() {
        val result = ContactSharingFilter.checkText("Hey! How are you doing today? Loved your hiking photo.")
        assertFalse(result.isBlocked)
    }

    @Test
    fun standardIndianPhoneNumber_isBlocked() {
        val result = ContactSharingFilter.checkText("Call me on 9876543210 tonight")
        assertTrue(result.isBlocked)
        assertTrue(result.violation is ContactSharingFilter.ViolationType.PhoneNumber)
    }

    @Test
    fun indianPhoneNumberWithPrefix_isBlocked() {
        val r1 = ContactSharingFilter.checkText("My number is +91 98765 43210")
        assertTrue(r1.isBlocked)

        val r2 = ContactSharingFilter.checkText("WhatsApp me on +91-8765432109")
        assertTrue(r2.isBlocked)

        val r3 = ContactSharingFilter.checkText("Call 09123456789")
        assertTrue(r3.isBlocked)
    }

    @Test
    fun spacedDigitsPhoneNumber_isBlocked() {
        val result = ContactSharingFilter.checkText("Reach me: 9 8 7 6 5 4 3 2 1 0")
        assertTrue(result.isBlocked)
    }

    @Test
    fun dotSeparatedDigitsPhoneNumber_isBlocked() {
        val result = ContactSharingFilter.checkText("Ping 9.8.7.6.5.4.3.2.1.0")
        assertTrue(result.isBlocked)
    }

    @Test
    fun wordsAsDigits_isBlocked() {
        val r1 = ContactSharingFilter.checkText("My number is nine eight seven six five four three two one zero")
        assertTrue(r1.isBlocked)

        val r2 = ContactSharingFilter.checkText("Dial nau aath saat chhah paanch char teen do ek")
        assertTrue(r2.isBlocked)
    }

    @Test
    fun emailAddress_isBlocked() {
        val r1 = ContactSharingFilter.checkText("Email me at priya.sharma@gmail.com")
        assertTrue(r1.isBlocked)

        val r2 = ContactSharingFilter.checkText("Send details to rahul at yahoo dot com")
        assertTrue(r2.isBlocked)
    }

    @Test
    fun socialMediaHandles_isBlocked() {
        val r1 = ContactSharingFilter.checkText("Add me on insta: @priyasharma_98")
        assertTrue(r1.isBlocked)

        val r2 = ContactSharingFilter.checkText("Text me on telegram: t.me/rahulverma")
        assertTrue(r2.isBlocked)

        val r3 = ContactSharingFilter.checkText("My snapchat: rahul_snap24")
        assertTrue(r3.isBlocked)
    }

    @Test
    fun maskMobile_masksCorrectly() {
        val masked = ContactSharingFilter.maskMobile("+919876543210")
        assertTrue(masked.contains("3210"))
        assertTrue(masked.contains("•••••"))
    }
}
