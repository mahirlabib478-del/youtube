package com.mahirlabib.youtube

import android.content.Context
import org.json.JSONArray

object SearchHistoryStore {
    private const val PREFS = "youtube_search_history"
    private const val KEY = "recent_queries"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, query: String): List<String> {
        val q = query.trim()
        if (q.isBlank()) return load(context)
        val current = load(context).filterNot { it.equals(q, ignoreCase = true) }.toMutableList()
        current.add(0, q)
        val trimmed = current.take(20)
        save(context, trimmed)
        return trimmed
    }

    fun remove(context: Context, query: String): List<String> {
        val current = load(context).filterNot { it.equals(query, ignoreCase = true) }
        save(context, current)
        return current
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }

    private fun save(context: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }
}
