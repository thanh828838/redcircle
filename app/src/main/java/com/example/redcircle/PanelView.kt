package com.example.redcircle

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bảng gồm các ô vuông xếp dọc, mỗi ô chứa một dòng chữ.
 * Bấm vào ô thì ô đổi màu xanh và hiện "Dùng thành công" một lúc rồi trở lại như cũ.
 * (Chỉ là giao diện, không làm gì khác.)
 */
class PanelView(context: Context, labels: List<String>) : LinearLayout(context) {

    companion object {
        const val MAX_BOXES = 5          // Tối đa 5 ô
        const val BOX_HEIGHT_DP = 40     // Chiều cao mỗi ô
        const val BOX_MARGIN_DP = 3      // Khoảng cách trên/dưới mỗi ô
        private const val RESET_DELAY_MS = 1500L
    }

    private val density = resources.displayMetrics.density

    private fun dp(value: Int): Int = (value * density).toInt()

    init {
        orientation = VERTICAL
        for (label in labels.take(MAX_BOXES)) {
            val params = LayoutParams(LayoutParams.MATCH_PARENT, dp(BOX_HEIGHT_DP)).apply {
                setMargins(0, dp(BOX_MARGIN_DP), 0, dp(BOX_MARGIN_DP))
            }
            addView(createBox(label), params)
        }
    }

    /** Tạo một ô: nền đen mờ, viền trắng, chữ trắng ở giữa. */
    private fun createBox(label: String): TextView {
        val normalBg = makeBackground(Color.argb(170, 0, 0, 0), Color.WHITE)
        val successBg = makeBackground(Color.argb(210, 0, 120, 40), Color.GREEN)

        val box = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            minWidth = dp(140)
            maxLines = 1
            setPadding(dp(12), 0, dp(12), 0)
            background = normalBg
        }

        // Trả ô về trạng thái ban đầu
        val reset = Runnable {
            box.text = label
            box.background = normalBg
        }

        box.setOnClickListener {
            box.removeCallbacks(reset)
            box.text = context.getString(R.string.panel_success)
            box.background = successBg
            box.postDelayed(reset, RESET_DELAY_MS)
        }
        return box
    }

    /** Nền hình chữ nhật bo góc nhẹ có viền. */
    private fun makeBackground(fillColor: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            setStroke(dp(2), strokeColor)
            cornerRadius = dp(4).toFloat()
        }
}
