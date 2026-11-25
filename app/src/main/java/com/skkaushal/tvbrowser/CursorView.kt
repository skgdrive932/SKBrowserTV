package com.skkaushal.tvbrowser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** On-screen pointer moved with the TV remote D-pad (works like a mouse cursor). */
class CursorView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var cx = 0f
    var cy = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFF3D5AFE.toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (cx == 0f && cy == 0f) {
            cx = w / 2f
            cy = h / 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, 14f, fill)
        canvas.drawCircle(cx, cy, 14f, stroke)
    }
}
