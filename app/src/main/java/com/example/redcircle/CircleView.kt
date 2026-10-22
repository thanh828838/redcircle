package com.example.redcircle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.View

/**
 * View tự vẽ một vòng tròn viền đỏ (không nền) và vài dòng chữ phía trên vòng tròn.
 *
 * Bố cục: View cao đối xứng (vùng chữ phía trên = vùng trống phía dưới)
 * nên khi đặt Gravity.CENTER thì vòng tròn vẫn nằm đúng giữa màn hình.
 */
class CircleView(context: Context) : View(context) {

    companion object {
        private const val CIRCLE_DIAMETER_DP = 180f
        private const val TEXT_SIZE_SP = 16f
        private const val TEXT_GAP_DP = 8f       // Khoảng cách giữa chữ và vòng tròn
        private const val MAX_LINES = 5          // Tối đa số dòng chữ
    }

    // Các dòng chữ cần hiển thị phía trên vòng tròn
    private var lines: List<String> = emptyList()

    private val density = resources.displayMetrics.density
    private val circleDiameterPx = CIRCLE_DIAMETER_DP * density
    private val textGapPx = TEXT_GAP_DP * density

    // Bút vẽ vòng tròn: màu đỏ, chỉ vẽ viền, độ dày 10px
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    // Bút vẽ chữ: màu trắng, căn giữa
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP, resources.displayMetrics
        )
    }

    // Bút viền chữ: viền đen để chữ dễ đọc trên mọi nền
    private val textOutlinePaint = Paint(textPaint).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    /** Cập nhật các dòng chữ. Bỏ dòng trống, giới hạn tối đa MAX_LINES dòng. */
    fun setLines(newLines: List<String>) {
        lines = newLines
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_LINES)
        requestLayout() // Kích thước View thay đổi theo số dòng chữ
        invalidate()
    }

    /** Chiều cao của một dòng chữ. */
    private fun lineHeight(): Float = textPaint.fontSpacing

    /** Chiều cao vùng chữ phía trên vòng tròn (0 nếu không có chữ). */
    private fun textBlockHeight(): Float =
        if (lines.isEmpty()) 0f else lines.size * lineHeight() + textGapPx

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Rộng: lấy giá trị lớn hơn giữa đường kính và dòng chữ dài nhất
        val maxTextWidth = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val w = maxOf(circleDiameterPx, maxTextWidth + 24f * density)

        // Cao đối xứng: vùng chữ ở trên, vùng trống bằng nhau ở dưới
        val h = circleDiameterPx + 2f * textBlockHeight()

        setMeasuredDimension(w.toInt(), h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Tâm vòng tròn nằm giữa View
        val cx = width / 2f
        val cy = height / 2f

        // Trừ nửa độ dày viền để nét vẽ không bị cắt ở mép
        val radius = (circleDiameterPx - circlePaint.strokeWidth) / 2f
        canvas.drawCircle(cx, cy, radius, circlePaint)

        // Vẽ chữ phía trên vòng tròn, dòng cuối nằm sát vòng tròn nhất
        if (lines.isNotEmpty()) {
            val circleTop = cy - circleDiameterPx / 2f
            val bottomBaseline = circleTop - textGapPx - textPaint.descent()
            val lh = lineHeight()
            for ((index, text) in lines.withIndex()) {
                val fromBottom = lines.size - 1 - index
                val y = bottomBaseline - fromBottom * lh
                canvas.drawText(text, cx, y, textOutlinePaint) // viền đen
                canvas.drawText(text, cx, y, textPaint)        // chữ trắng
            }
        }
    }
}
