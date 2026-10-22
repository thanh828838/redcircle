package com.example.redcircle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/**
 * View tự vẽ một vòng tròn viền đỏ, không có nền.
 */
class CircleView(context: Context) : View(context) {

    // Bút vẽ: màu đỏ, chỉ vẽ viền, độ dày 10px
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Tâm vòng tròn nằm giữa View
        val cx = width / 2f
        val cy = height / 2f

        // Trừ nửa độ dày viền để nét vẽ không bị cắt ở mép View
        val radius = (minOf(width, height) - paint.strokeWidth) / 2f

        canvas.drawCircle(cx, cy, radius, paint)
    }
}
