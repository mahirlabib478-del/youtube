package com.mahirlabib.youtube

import android.content.Context

object PipSettingsStore {
    private const val PREFS = "youtube_pip_prefs"
    private const val KEY_MODE = "background_playback_mode"
    private const val OLD_KEY_PIP = "is_pip_enabled"

    const val MODE_VIDEO = "video"     // Floating Video Window (PiP)
    const val MODE_AUDIO = "audio"     // Background Audio Only (Screen Off / Other Apps)
    const val MODE_DISABLED = "disabled" // Off

    fun getPlaybackMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_MODE)) {
            return prefs.getString(KEY_MODE, MODE_VIDEO) ?: MODE_VIDEO
        }
        // Backward compatibility migration from boolean
        val legacy = prefs.getBoolean(OLD_KEY_PIP, true)
        return if (legacy) MODE_VIDEO else MODE_DISABLED
    }

    fun setPlaybackMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode)
            .putBoolean(OLD_KEY_PIP, mode != MODE_DISABLED)
            .apply()
    }

    fun isPipVideoEnabled(context: Context): Boolean {
        return getPlaybackMode(context) == MODE_VIDEO
    }

    fun isAudioOnlyEnabled(context: Context): Boolean {
        return getPlaybackMode(context) == MODE_AUDIO
    }

    fun isPipEnabled(context: Context): Boolean {
        return getPlaybackMode(context) != MODE_DISABLED
    }

    fun setPipEnabled(context: Context, enabled: Boolean) {
        setPlaybackMode(context, if (enabled) MODE_VIDEO else MODE_DISABLED)
    }
}
