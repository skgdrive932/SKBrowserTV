package com.skkaushal.tvbrowser

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat() {

    private val vpnLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val p = findPreference<SwitchPreferenceCompat>("vpn_enabled")
            if (r.resultCode == Activity.RESULT_OK) {
                VpnHelper.start(requireContext())
                p?.isChecked = true
            } else {
                p?.isChecked = false
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        val ctx = requireContext()

        // VPN (DNS shield) switch
        findPreference<SwitchPreferenceCompat>("vpn_enabled")?.apply {
            isChecked = VpnHelper.isOn(ctx)
            setOnPreferenceChangeListener { _, value ->
                if (value == true) {
                    val i = VpnService.prepare(ctx)
                    if (i == null) {
                        VpnHelper.start(ctx)
                        true
                    } else {
                        vpnLauncher.launch(i)
                        false
                    }
                } else {
                    VpnHelper.stop(ctx)
                    true
                }
            }
        }

        // Add the page that is open in the browser to the home shortcuts
        val curUrl = requireActivity().intent.getStringExtra("current_url")
        val curTitle = requireActivity().intent.getStringExtra("current_title")
        findPreference<Preference>("add_current")?.apply {
            if (curUrl.isNullOrEmpty() || !curUrl.startsWith("http")) {
                summary = "Open a website in the browser first, then come back here"
                isEnabled = false
            } else {
                summary = curUrl
                setOnPreferenceClickListener {
                    val name = (curTitle?.takeIf { it.isNotBlank() } ?: Nav.hostOf(curUrl)).take(24)
                    val ok = ShortcutStore.add(ctx, name, curUrl)
                    Toast.makeText(
                        ctx,
                        if (ok) "Added to home shortcuts" else "Already on the home screen",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
        }

        // Add any site by typing its address
        findPreference<Preference>("add_site")?.setOnPreferenceClickListener {
            Dialogs.editShortcut(ctx, "Add site to home", null) { s ->
                val ok = ShortcutStore.add(ctx, s.name, s.url)
                Toast.makeText(
                    ctx,
                    if (ok) "Added to home shortcuts" else "Already on the home screen",
                    Toast.LENGTH_SHORT
                ).show()
            }
            true
        }

        findPreference<Preference>("reset_shortcuts")?.setOnPreferenceClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Reset shortcuts?")
                .setMessage("This restores the default home screen shortcuts.")
                .setPositiveButton("Reset") { _, _ ->
                    ShortcutStore.reset(ctx)
                    Toast.makeText(ctx, "Shortcuts reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            true
        }

        findPreference<Preference>("clear_whitelist")?.setOnPreferenceClickListener {
            Prefs.saveWhitelist(ctx, emptySet())
            Toast.makeText(ctx, "Ad blocker exceptions cleared", Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<Preference>("clear_data")?.setOnPreferenceClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            WebView(ctx).apply { clearCache(true); clearHistory(); destroy() }
            Toast.makeText(ctx, "Cookies, cache and site data cleared", Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<Preference>("about")?.setOnPreferenceClickListener {
            val lic = try {
                ctx.assets.open("LICENSE.txt").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                "MIT License"
            }
            AlertDialog.Builder(ctx)
                .setTitle("SK Browser TV 1.0.0")
                .setMessage(
                    "Author: Sk Kaushal\nContact: +91 97793 71866\nEmail: skgdrive932@gmail.com\n\n$lic"
                )
                .setPositiveButton("OK", null)
                .show()
            true
        }
    }
}
