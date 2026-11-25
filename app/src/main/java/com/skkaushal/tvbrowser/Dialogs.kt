package com.skkaushal.tvbrowser

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object Dialogs {
    /** Add / edit dialog used by the home screen and by Settings -> Add site. */
    fun editShortcut(ctx: Context, title: String, old: Shortcut?, onSave: (Shortcut) -> Unit) {
        val pad = (20 * ctx.resources.displayMetrics.density).toInt()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val name = EditText(ctx).apply {
            hint = "Name (optional)"
            setSingleLine()
            setText(old?.name ?: "")
        }
        val url = EditText(ctx).apply {
            hint = "Website, e.g. example.com"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(old?.url ?: "")
        }
        box.addView(name)
        box.addView(url)
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val u = Nav.asSite(url.text.toString())
                if (u == null) {
                    Toast.makeText(ctx, "Enter a valid website address", Toast.LENGTH_LONG).show()
                } else {
                    onSave(Shortcut(name.text.toString().trim().ifBlank { Nav.hostOf(u) }, u))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
