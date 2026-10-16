package com.mahirlabib.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun demoFeedHasContentForInitialScreen() {
        assertEquals("M7lc1UVf-VE", DEMO_VIDEO.videoId)
        assertTrue(DEMO_VIDEO.title.isNotBlank())
        assertTrue(DEMO_VIDEO.channelTitle.isNotBlank())
    }

    @Test
    fun acceptsValidYouTubeChannelId() {
        assertTrue(isValidYouTubeChannelId("UC1234567890123456789012"))
        assertTrue(isValidYouTubeChannelId(" UC1234567890123456789012 "))
        assertTrue(isValidYouTubeChannelId("UC_x5XG1OV2P6uZZ5FSM9Ttw"))
        assertTrue(isValidYouTubeChannelId("UCVHFbqXqoYvEWM1Ddxl0QDg"))
    }

    @Test
    fun rejectsMalformedYouTubeChannelIds() {
        assertFalse(isValidYouTubeChannelId(""))
        assertFalse(isValidYouTubeChannelId("not-a-channel"))
        assertFalse(isValidYouTubeChannelId("UC123"))
        assertFalse(isValidYouTubeChannelId("UC12345678901234567890123"))
        assertFalse(isValidYouTubeChannelId("AB1234567890123456789012"))
    }

    @Test
    fun apiKeyConfigurationCheckDetectsPlaceholders() {
        assertFalse(isApiKeyConfigured(""))
        assertFalse(isApiKeyConfigured("   "))
        assertFalse(isApiKeyConfigured("your_api_key_here"))
        assertFalse(isApiKeyConfigured("YOUR_API_KEY_HERE"))
        assertTrue(isApiKeyConfigured("AIzaSyD-abc12345xyz_real_key"))
    }

    @Test
    fun formatRelativeTimeHandlesEmptyGracefully() {
        assertEquals("", formatRelativeTime(""))
    }

    @Test
    fun defaultChannelsAreConfiguredAndValid() {
        assertTrue(ApprovedChannelStore.DEFAULT_CHANNELS.isNotEmpty())
        for (channel in ApprovedChannelStore.DEFAULT_CHANNELS) {
            assertTrue(isValidYouTubeChannelId(channel.channelId))
            assertTrue(channel.title.isNotBlank())
        }
    }

    @Test
    fun watchedVideoDataModelHoldsRequiredFields() {
        val watched = WatchedVideo(
            videoId = "M7lc1UVf-VE",
            title = "Test Video",
            channel = "Test Channel",
            watchedAt = 123456789L,
            description = "Test Description"
        )
        assertEquals("M7lc1UVf-VE", watched.videoId)
        assertEquals("Test Video", watched.title)
        assertEquals(123456789L, watched.watchedAt)
    }

    @Test
    fun shortsPreviewListContainsValidEmbedIds() {
        assertTrue(SHORTS_PREVIEW_LIST.isNotEmpty())
        for (short in SHORTS_PREVIEW_LIST) {
            assertTrue(short.videoId.matches(Regex("^[A-Za-z0-9_-]{11}$")))
            assertTrue(short.title.isNotBlank())
        }
    }
}
