package com.skkaushal.tvbrowser

import android.content.Context
import org.json.JSONObject

/** Domain based ad/tracker blocker shared by the browser and the DNS VPN. */
object AdBlocker {
    private val hosts = HashSet<String>()
    @Volatile private var loaded = false
    private var cosmeticCss: String = ""

    fun load(c: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                c.applicationContext.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        val l = raw.substringBefore('#').trim().lowercase()
                        if (l.isNotEmpty()) {
                            // accepts plain domains and hosts-file lines ("0.0.0.0 domain")
                            val parts = l.split(Regex("\\s+"))
                            val d = if (parts.size >= 2) parts[1] else parts[0]
                            if (d != "localhost" && d != "0.0.0.0" && d.contains('.')) hosts.add(d)
                        }
                    }
                }
                cosmeticCss = c.applicationContext.assets.open("cosmetic.css")
                    .bufferedReader().use { it.readText() }
            } catch (_: Exception) {
            }
            loaded = true
        }
    }

    /** True if [host] or any parent domain is in the blocklist. */
    fun isBlocked(host: String?): Boolean {
        var h = host?.lowercase()?.trimEnd('.') ?: return false
        while (true) {
            if (hosts.contains(h)) return true
            val i = h.indexOf('.')
            if (i < 0) return false
            h = h.substring(i + 1)
        }
    }

    /** JavaScript that hides common ad containers on the current page. */
    fun cosmeticScript(): String? {
        if (cosmeticCss.isBlank()) return null
        return "(function(){try{var s=document.createElement('style');s.textContent=" +
            JSONObject.quote(cosmeticCss) +
            ";(document.head||document.documentElement).appendChild(s);}catch(e){}})();"
    }
}
