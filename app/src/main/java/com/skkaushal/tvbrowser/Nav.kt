package com.skkaushal.tvbrowser

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object Nav {
    fun hostOf(url: String): String =
        try { Uri.parse(url).host?.removePrefix("www.") ?: url } catch (_: Exception) { url }

    /** Returns a full URL if [input] looks like a website address, otherwise null. */
    fun asSite(input: String): String? {
        val t = input.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        if (t.startsWith("http://") || t.startsWith("https://")) return t
        if (t.contains('.') || t.startsWith("localhost")) return "https://$t"
        return null
    }

    /** Website -> URL, anything else -> search using the chosen engine. */
    fun toUrl(c: Context, input: String): String =
        asSite(input) ?: (Prefs.searchUrl(c) + Uri.encode(input.trim()))

    /** Opens a site. YouTube links go to the YouTube app first if it is installed. */
    fun open(c: Context, url: String) {
        val host = (Uri.parse(url).host ?: "").lowercase()
        if (Prefs.youtubeApp(c) && (host.endsWith("youtube.com") || host == "youtu.be")) {
            for (pkg in listOf("com.google.android.youtube.tv", "com.google.android.youtube")) {
                try {
                    c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))
                    return
                } catch (_: ActivityNotFoundException) {
                }
            }
        }
        c.startActivity(Intent(c, BrowserActivity::class.java).putExtra("url", url))
    }
}
