package com.skkaushal.tvbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Shortcut(val name: String, val url: String)

/** Home-screen shortcuts, stored as JSON in SharedPreferences. */
object ShortcutStore {
    private const val KEY = "shortcuts_json"

    private fun defaults() = mutableListOf(
        Shortcut("YouTube", "https://www.youtube.com"),
        Shortcut("Google", "https://www.google.com"),
        Shortcut("DuckDuckGo", "https://duckduckgo.com"),
        Shortcut("Wikipedia", "https://www.wikipedia.org")
    )

    fun load(c: Context): MutableList<Shortcut> {
        val raw = Prefs.sp(c).getString(KEY, null) ?: return defaults().also { save(c, it) }
        val out = mutableListOf<Shortcut>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Shortcut(o.getString("name"), o.getString("url")))
            }
        } catch (_: Exception) {
            return defaults()
        }
        return out
    }

    fun save(c: Context, list: List<Shortcut>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        Prefs.sp(c).edit().putString(KEY, arr.toString()).apply()
    }

    /** Returns false if the site is already on the home screen. */
    fun add(c: Context, name: String, url: String): Boolean {
        val list = load(c)
        if (list.any { it.url.trimEnd('/') == url.trimEnd('/') }) return false
        list.add(Shortcut(name.ifBlank { Nav.hostOf(url) }, url))
        save(c, list)
        return true
    }

    fun reset(c: Context) {
        Prefs.sp(c).edit().remove(KEY).apply()
    }
}
