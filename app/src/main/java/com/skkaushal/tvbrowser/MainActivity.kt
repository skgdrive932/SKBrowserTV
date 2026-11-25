package com.skkaushal.tvbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Home screen: search box, shortcut tiles, "Add site" tile, VPN / Downloads / Settings buttons. */
class MainActivity : AppCompatActivity() {

    private val shortcuts = mutableListOf<Shortcut>()
    private lateinit var adapter: TileAdapter
    private lateinit var btnVpn: Button

    private val vpnLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK) VpnHelper.start(this)
            refreshVpnButton()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1
            )
        }

        val search = findViewById<EditText>(R.id.searchBox)
        search.setOnEditorActionListener { v, actionId, event ->
            val isGo = actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN)
            val text = v.text.toString()
            if (isGo && text.isNotBlank()) {
                Nav.open(this, Nav.toUrl(this, text))
                v.text = ""
                true
            } else false
        }

        btnVpn = findViewById(R.id.btnVpn)
        btnVpn.setOnClickListener {
            if (VpnHelper.isOn(this)) {
                VpnHelper.stop(this)
                refreshVpnButton()
            } else {
                val i = VpnService.prepare(this)
                if (i == null) {
                    VpnHelper.start(this)
                    refreshVpnButton()
                } else {
                    vpnLauncher.launch(i)
                }
            }
        }
        findViewById<Button>(R.id.btnDownloads).setOnClickListener {
            startActivity(Intent(this, DownloadsActivity::class.java))
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        adapter = TileAdapter()
        val rv = findViewById<RecyclerView>(R.id.grid)
        rv.layoutManager = GridLayoutManager(this, 5)
        rv.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
        refreshVpnButton()
    }

    private fun reload() {
        shortcuts.clear()
        shortcuts.addAll(ShortcutStore.load(this))
        adapter.notifyDataSetChanged()
    }

    private fun refreshVpnButton() {
        btnVpn.text = if (VpnHelper.isOn(this)) "VPN: ON" else "VPN: OFF"
    }

    private fun addSite() {
        Dialogs.editShortcut(this, "Add site", null) { s ->
            if (!ShortcutStore.add(this, s.name, s.url)) {
                Toast.makeText(this, "Already on the home screen", Toast.LENGTH_SHORT).show()
            }
            reload()
        }
    }

    private fun colorFor(n: String): Int {
        val p = intArrayOf(
            0xFFE53935.toInt(), 0xFF8E24AA.toInt(), 0xFF3949AB.toInt(),
            0xFF00897B.toInt(), 0xFFF4511E.toInt(), 0xFF1E88E5.toInt()
        )
        return p[(n.hashCode() and 0x7FFFFFFF) % p.size]
    }

    private inner class TileAdapter : RecyclerView.Adapter<TileAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val letter: TextView = v.findViewById(R.id.tileLetter)
            val name: TextView = v.findViewById(R.id.tileName)
        }

        override fun getItemCount() = shortcuts.size + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false))

        override fun onBindViewHolder(h: VH, position: Int) {
            val isAdd = position == shortcuts.size
            val s = if (isAdd) null else shortcuts[position]
            h.name.text = s?.name ?: "Add site"
            h.letter.text = if (isAdd) "+" else s!!.name.take(1).uppercase()
            h.letter.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isAdd) 0xFF424242.toInt() else colorFor(s!!.name))
            }

            h.itemView.setOnClickListener {
                val p = h.bindingAdapterPosition
                if (p == RecyclerView.NO_POSITION) return@setOnClickListener
                if (p == shortcuts.size) addSite() else Nav.open(this@MainActivity, shortcuts[p].url)
            }
            h.itemView.setOnLongClickListener {
                val p = h.bindingAdapterPosition
                if (p == RecyclerView.NO_POSITION || p == shortcuts.size) return@setOnLongClickListener false
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(shortcuts[p].name)
                    .setItems(arrayOf("Open", "Edit", "Delete")) { _, which ->
                        when (which) {
                            0 -> Nav.open(this@MainActivity, shortcuts[p].url)
                            1 -> Dialogs.editShortcut(this@MainActivity, "Edit site", shortcuts[p]) { n ->
                                shortcuts[p] = n
                                ShortcutStore.save(this@MainActivity, shortcuts)
                                notifyDataSetChanged()
                            }
                            2 -> {
                                shortcuts.removeAt(p)
                                ShortcutStore.save(this@MainActivity, shortcuts)
                                notifyDataSetChanged()
                            }
                        }
                    }
                    .show()
                true
            }
        }
    }
}
