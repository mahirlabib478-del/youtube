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
    fun subscribedFeedSortsNewestFirstAcrossChannelsAndDeduplicates() {
        val videos = listOf(
            YouTubeVideo("older-a", "Older A", "Channel A", "2025-01-01T10:00:00Z", channelId = "A"),
            YouTubeVideo("newest-b", "Newest B", "Channel B", "2025-03-01T10:00:00Z", channelId = "B"),
            YouTubeVideo("middle-a", "Middle A", "Channel A", "2025-02-01T10:00:00Z", channelId = "A"),
            YouTubeVideo("newest-b", "Duplicate newest B", "Channel B", "2025-03-01T10:00:00Z", channelId = "B")
        )

        val sorted = sortVideosNewestFirst(videos)

        assertEquals(listOf("newest-b", "middle-a", "older-a"), sorted.map { it.videoId })
        assertEquals(listOf("B", "A", "A"), sorted.map { it.channelId })
    }

    @Test
    fun subscribedFeedBalancesChannelsBeforeShowingSecondVideoFromAnyChannel() {
        val videos = listOf(
            YouTubeVideo("a-new", "A newest", "Channel A", "2025-03-01T10:00:00Z", channelId = "A"),
            YouTubeVideo("a-mid", "A middle", "Channel A", "2025-02-01T10:00:00Z", channelId = "A"),
            YouTubeVideo("a-old", "A old", "Channel A", "2025-01-01T10:00:00Z", channelId = "A"),
            YouTubeVideo("b-new", "B newest", "Channel B", "2025-02-20T10:00:00Z", channelId = "B"),
            YouTubeVideo("b-old", "B old", "Channel B", "2024-12-01T10:00:00Z", channelId = "B"),
            YouTubeVideo("c-new", "C newest", "Channel C", "2025-02-10T10:00:00Z", channelId = "C")
        )

        val balanced = balanceVideosAcrossChannels(videos)

        assertEquals(listOf("A", "B", "C", "A", "B", "A"), balanced.map { it.channelId })
        assertEquals(listOf("a-new", "b-new", "c-new", "a-mid", "b-old", "a-old"), balanced.map { it.videoId })
    }

    @Test
    fun subscribedFeedSortKeepsStableOrderForEqualTimestamps() {
        val videos = listOf(
            YouTubeVideo("video-z", "Z", "Channel Z", "2025-01-01T10:00:00Z"),
            YouTubeVideo("video-a", "A", "Channel A", "2025-01-01T10:00:00Z")
        )

        assertEquals(
            listOf("video-a", "video-z"),
            sortVideosNewestFirst(videos).map { it.videoId }
        )
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

    @Test
    fun rssParsingPatternValidatesStructure() {
        val sampleXml = """
            <feed>
                <title>Test Feed</title>
                <author><name>Test Channel</name></author>
                <entry>
                    <yt:videoId>M7lc1UVf-VE</yt:videoId>
                    <title>Sample Video Title</title>
                    <published>2024-01-01T00:00:00Z</published>
                    <media:thumbnail url="https://i.ytimg.com/vi/M7lc1UVf-VE/hqdefault.jpg"/>
                    <media:description>Sample description</media:description>
                </entry>
            </feed>
        """.trimIndent()
        val entryRegex = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
        val videoIdRegex = Regex("<(?:yt:)?videoId>([^<]+)</(?:yt:)?videoId>")
        val titleRegex = Regex("<title>([^<]+)</title>")
        val match = entryRegex.find(sampleXml)
        assertTrue(match != null)
        val vid = videoIdRegex.find(match!!.value)?.groupValues?.getOrNull(1)
        val title = titleRegex.find(match.value)?.groupValues?.getOrNull(1)
        assertEquals("M7lc1UVf-VE", vid)
        assertEquals("Sample Video Title", title)
    }

    @Test
    fun refreshRotatesUnchangedChannelVideosButKeepsChannelBalance() {
        val videos = listOf(
            YouTubeVideo("a-new", "A newest", "Channel A", "2025-03-01T10:00:00Z", channelId = "A"),
            YouTubeVideo("a-old", "A old", "Channel A", "2025-02-01T10:00:00Z", channelId = "A"),
            YouTubeVideo("b-new", "B newest", "Channel B", "2025-02-20T10:00:00Z", channelId = "B"),
            YouTubeVideo("b-old", "B old", "Channel B", "2025-01-01T10:00:00Z", channelId = "B")
        )

        val refreshed = refreshBalancedVideos(videos, videos, rotationStep = 1)

        assertEquals(listOf("a-old", "b-old", "a-new", "b-new"), refreshed.map { it.videoId })
        assertEquals(listOf("A", "B", "A", "B"), refreshed.map { it.channelId })
    }

    @Test
    fun refreshKeepsNewUploadsAtTopInsteadOfRotatingThatChannel() {
        val previous = listOf(
            YouTubeVideo("a-old", "A old", "Channel A", "2025-02-01T10:00:00Z", channelId = "A"),
            YouTubeVideo("b-old", "B old", "Channel B", "2025-01-01T10:00:00Z", channelId = "B")
        )
        val fresh = listOf(
            YouTubeVideo("a-new", "A new", "Channel A", "2025-03-01T10:00:00Z", channelId = "A"),
            previous[0],
            previous[1]
        )

        val refreshed = refreshBalancedVideos(fresh, previous, rotationStep = 1)

        assertEquals("a-new", refreshed.first { it.channelId == "A" }.videoId)
    }
}
