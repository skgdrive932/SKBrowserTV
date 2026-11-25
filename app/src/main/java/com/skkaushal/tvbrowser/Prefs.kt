package com.skkaushal.tvbrowser

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Central place for every user setting. Keys match res/xml/preferences.xml. */
object Prefs {
    const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    fun sp(c: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(c)

    fun homeUrl(c: Context): String = sp(c).getString("home_url", "")?.trim() ?: ""

    fun searchUrl(c: Context): String = when (sp(c).getString("search_engine", "google")) {
        "duckduckgo" -> "https://duckduckgo.com/?q="
        "bing" -> "https://www.bing.com/search?q="
        else -> "https://www.google.com/search?q="
    }

    fun adBlock(c: Context) = sp(c).getBoolean("adblock", true)
    fun cosmetic(c: Context) = sp(c).getBoolean("cosmetic", true)
    fun desktop(c: Context) = sp(c).getBoolean("desktop_mode", false)
    fun javascript(c: Context) = sp(c).getBoolean("javascript", true)
    fun askDownload(c: Context) = sp(c).getBoolean("ask_download", true)
    fun youtubeApp(c: Context) = sp(c).getBoolean("youtube_app", true)
    fun cursorMode(c: Context) = sp(c).getBoolean("cursor_mode", true)
    fun upstreamDns(c: Context): String = sp(c).getString("upstream_dns", "1.1.1.1") ?: "1.1.1.1"

    fun whitelist(c: Context): MutableSet<String> =
        HashSet(sp(c).getStringSet("adblock_whitelist", emptySet()) ?: emptySet())

    fun saveWhitelist(c: Context, set: Set<String>) {
        sp(c).edit().putStringSet("adblock_whitelist", set).apply()
    }
}
