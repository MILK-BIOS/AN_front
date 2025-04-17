package com.example.an_front

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint().apply {
        color = 0xFFFF0000.toInt() // 红色
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val boxes = mutableListOf<RectF>()

    fun setBoxes(newBoxes: List<RectF>) {
        boxes.clear()
        boxes.addAll(newBoxes)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (box in boxes) {
            canvas.drawRect(box, paint)
        }
    }
}