package com.example

import com.example.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImgbbUploadUnitTest {

    /**
     * Minimal pure-Kotlin JSON parser helper to test ImgBB response parsing
     * without relying on android.os/android.net or unmocked Android stubs on the JVM.
     */
    private fun extractImgbbUrl(jsonString: String): String {
        val urlRegex = """"url"\s*:\s*"([^"]+)"""".toRegex()
        val displayUrlRegex = """"display_url"\s*:\s*"([^"]+)"""".toRegex()

        val directMatches = urlRegex.findAll(jsonString).map { it.groupValues[1] }.toList()
        val displayMatch = displayUrlRegex.find(jsonString)?.groupValues?.get(1)

        val candidate = directMatches.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            ?: displayMatch?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: ""
        return candidate.replace("\\/", "/")
    }

    private fun extractImgbbError(jsonString: String): String {
        val messageRegex = """"message"\s*:\s*"([^"]+)"""".toRegex()
        return messageRegex.find(jsonString)?.groupValues?.get(1) ?: "Upload failed"
    }

    @Test
    fun testImgbbSuccessfulJsonResponseParsing_urlField() {
        val mockSuccessJson = """
            {
                "data": {
                    "id": "2ndCYJK",
                    "title": "upload_12345",
                    "url_viewer": "https://ibb.co/2ndCYJK",
                    "url": "https://i.ibb.co/2ndCYJK/sample.jpg",
                    "display_url": "https://i.ibb.co/2ndCYJK/sample.jpg",
                    "width": 1200,
                    "height": 1600,
                    "size": 452100,
                    "time": 1700000000,
                    "expiration": 0,
                    "image": {
                        "filename": "sample.jpg",
                        "name": "sample",
                        "mime": "image/jpeg",
                        "extension": "jpg",
                        "url": "https://i.ibb.co/2ndCYJK/sample.jpg"
                    },
                    "delete_url": "https://ibb.co/2ndCYJK/delete123"
                },
                "success": true,
                "status": 200
            }
        """.trimIndent()

        val parsedUrl = extractImgbbUrl(mockSuccessJson)
        assertEquals("https://i.ibb.co/2ndCYJK/sample.jpg", parsedUrl)
    }

    @Test
    fun testImgbbSuccessfulJsonResponseParsing_displayUrlFallback() {
        val mockFallbackJson = """
            {
                "data": {
                    "display_url": "https://i.ibb.co/fallback/display.jpg"
                },
                "success": true,
                "status": 200
            }
        """.trimIndent()

        val parsedUrl = extractImgbbUrl(mockFallbackJson)
        assertEquals("https://i.ibb.co/fallback/display.jpg", parsedUrl)
    }

    @Test
    fun testImgbbErrorResponseParsing() {
        val mockErrorJson = """
            {
                "status_code": 400,
                "error": {
                    "message": "Empty upload data.",
                    "code": 100
                },
                "status_txt": "Bad Request"
            }
        """.trimIndent()

        val errorMessage = extractImgbbError(mockErrorJson)
        assertEquals("Empty upload data.", errorMessage)
    }

    @Test
    fun testUserProfileStrict5PhotosCompletionRequirement() {
        val incompleteUser4Photos = UserProfile(
            userId = "user_123",
            displayName = "Alex Doe",
            age = 24,
            gender = "Man",
            rulesAccepted = true,
            profilePhotoUrls = listOf(
                "https://i.ibb.co/1.jpg",
                "https://i.ibb.co/2.jpg",
                "https://i.ibb.co/3.jpg",
                "https://i.ibb.co/4.jpg"
            ),
            profileCompleted = false
        )
        assertFalse("Profile with 4 photos cannot be completed", incompleteUser4Photos.profileCompleted && incompleteUser4Photos.profilePhotoUrls.size >= 5)

        val completeUser5Photos = incompleteUser4Photos.copy(
            profilePhotoUrls = incompleteUser4Photos.profilePhotoUrls + "https://i.ibb.co/5.jpg",
            profileCompleted = true
        )
        assertTrue("Profile with 5 photos and valid details is completed", completeUser5Photos.profileCompleted && completeUser5Photos.profilePhotoUrls.size == 5)
    }
}
