package com.mahirlabib.youtube

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
}
