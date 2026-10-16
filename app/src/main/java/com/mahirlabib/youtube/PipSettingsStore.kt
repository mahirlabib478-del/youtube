package com.mahirlabib.youtube

import android.content.Context

object PipSettingsStore {
    private const val PREFS = "youtube_pip_prefs"
    private const val KEY_PIP = "is_pip_enabled"

    fun isPipEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PIP, true) // Default is ON
    }

    fun setPipEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PIP, enabled)
            .apply()
    }
}
