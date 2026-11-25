package com.skkaushal.tvbrowser

import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Lists downloads made by this app, with progress; click to open, hold OK to delete. */
class DownloadsActivity : AppCompatActivity() {

    private data class Row(
        val id: Long, val title: String, val status: Int,
        val done: Long, val total: Long, val mime: String?
    )

    private val rows = ArrayList<Row>()
    private var shownLabels: List<String> = emptyList()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var empty: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }
    private val dm: DownloadManager get() = getSystemService(DOWNLOAD_SERVICE) as DownloadManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_downloads)
        empty = findViewById(R.id.empty)
        val list = findViewById<ListView>(R.id.list)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        list.adapter = adapter

        list.setOnItemClickListener { _, _, pos, _ -> rows.getOrNull(pos)?.let { open(it) } }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            val r = rows.getOrNull(pos) ?: return@setOnItemLongClickListener true
            AlertDialog.Builder(this)
                .setTitle(r.title)
                .setItems(arrayOf("Open", "Delete (removes the file)")) { _, which ->
                    if (which == 0) open(r) else {
                        dm.remove(r.id)
                        refresh()
                    }
                }
                .show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun refresh() {
        rows.clear()
        dm.query(DownloadManager.Query())?.use { c ->
            while (c.moveToNext()) {
                rows.add(
                    Row(
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)),
                        c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "file",
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                        c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
                    )
                )
            }
        }
        val labels = rows.map { label(it) }
        if (labels != shownLabels) {
            shownLabels = labels
            adapter.clear()
            adapter.addAll(labels)
        }
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun label(r: Row): String {
        val state = when (r.status) {
            DownloadManager.STATUS_SUCCESSFUL -> "Done"
            DownloadManager.STATUS_FAILED -> "Failed"
            DownloadManager.STATUS_PAUSED -> "Paused"
            DownloadManager.STATUS_PENDING -> "Waiting..."
            else -> if (r.total > 0) "Downloading ${r.done * 100 / r.total}%" else "Downloading..."
        }
        return "${r.title}\n$state"
    }

    private fun open(r: Row) {
        if (r.status != DownloadManager.STATUS_SUCCESSFUL) {
            Toast.makeText(this, "Download is not finished", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = dm.getUriForDownloadedFile(r.id)
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, r.mime ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_LONG).show()
        }
    }
}
