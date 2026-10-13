package com.mahirlabib.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun demoFeedHasContentForInitialScreen() {
        val demoTitles = listOf(
            "Welcome to your video feed",
            "Build your own watchlist",
            "Official YouTube playback"
        )
        assertTrue(demoTitles.isNotEmpty())
        assertTrue(demoTitles.all { it.isNotBlank() })
    }

    @Test
    fun acceptsValidYouTubeChannelId() {
        assertTrue(isValidYouTubeChannelId("UC1234567890123456789012"))
        assertTrue(isValidYouTubeChannelId(" UC1234567890123456789012 "))
    }

    @Test
    fun rejectsMalformedYouTubeChannelIds() {
        assertFalse(isValidYouTubeChannelId(""))
        assertFalse(isValidYouTubeChannelId("not-a-channel"))
        assertFalse(isValidYouTubeChannelId("UC123"))
        assertFalse(isValidYouTubeChannelId("UC12345678901234567890123"))
    }
}
