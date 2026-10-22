package com.example.redcircle

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Menu nổi gồm một bong bóng tròn và bảng các nút chức năng xổ ra bên dưới.
 * - Bong bóng ([bubble]) do Service gắn bộ kéo thả; chạm nhẹ thì gọi [toggle] để mở/gập menu.
 * - Mỗi nút chức năng là một hàng có trạng thái TẮT/BẬT. Bấm vào thì đổi trạng thái
 *   và hiện thông báo "Dùng thành công" một lúc.
 * (Chỉ là giao diện, không làm gì khác.)
 *
 * @param onExpandedChanged được gọi khi menu mở/gập để Service chỉnh lại cửa sổ.
 */
class FloatingMenuView(
    context: Context,
    private val title: String,
    labels: List<String>,
    private val onExpandedChanged: (Boolean) -> Unit
) : LinearLayout(context) {

    companion object {
        const val MAX_ITEMS = 6              // Tối đa số nút chức năng
        private const val BUBBLE_SIZE_DP = 48
        private const val ROW_HEIGHT_DP = 40
        private const val MENU_MIN_WIDTH_DP = 200
        private const val MESSAGE_DELAY_MS = 1500L
    }

    private val density = resources.displayMetrics.density

    private fun dp(value: Int): Int = (value * density).toInt()

    // Bảng màu
    private val colorRed = Color.parseColor("#FF1744")
    private val colorGreen = Color.parseColor("#00E676")
    private val colorGray = Color.parseColor("#8A8A99")
    private val colorPanel = Color.argb(240, 15, 15, 22)
    private val colorRow = Color.argb(255, 27, 27, 36)
    private val colorRowOn = Color.argb(255, 12, 48, 30)
    private val colorLine = Color.argb(255, 60, 60, 75)

    /** Bong bóng tròn: chữ cái đầu của tiêu đề, viền đỏ. */
    val bubble: TextView = TextView(context).apply {
        text = title.take(1).uppercase()
        setTextColor(colorRed)
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(235, 15, 15, 20))
            setStroke(dp(2), colorRed)
        }
    }

    // Bảng chứa tiêu đề + các nút, mặc định gập lại (ẩn)
    private val menuCard = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        minimumWidth = dp(MENU_MIN_WIDTH_DP)
        setPadding(dp(10), dp(8), dp(10), dp(10))
        background = makeBackground(colorPanel, colorRed, 12, 1)
    }

    // Dòng thông báo ở cuối bảng ("Dùng thành công"); ẩn nhưng vẫn giữ chỗ
    private val statusView = TextView(context).apply {
        setTextColor(colorGreen)
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        visibility = View.INVISIBLE
        text = " "
    }

    private val hideStatus = Runnable { statusView.visibility = View.INVISIBLE }

    private var expanded = false

    init {
        orientation = VERTICAL

        addView(bubble, LinearLayout.LayoutParams(dp(BUBBLE_SIZE_DP), dp(BUBBLE_SIZE_DP)))

        menuCard.addView(createHeader())
        for (label in labels.take(MAX_ITEMS)) {
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(ROW_HEIGHT_DP)
            ).apply { topMargin = dp(4) }
            menuCard.addView(createRow(label), rowParams)
        }
        menuCard.addView(
            statusView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        addView(
            menuCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )
    }

    /** Mở/gập bảng chức năng. */
    fun toggle() {
        expanded = !expanded
        menuCard.visibility = if (expanded) View.VISIBLE else View.GONE
        onExpandedChanged(expanded)
    }

    /** Dòng tiêu đề của bảng. */
    private fun createHeader(): TextView = TextView(context).apply {
        text = title.uppercase()
        setTextColor(colorRed)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        letterSpacing = 0.15f
        setPadding(0, dp(2), 0, dp(4))
    }

    /** Một hàng chức năng: tên bên trái, trạng thái TẮT/BẬT bên phải. */
    private fun createRow(label: String): LinearLayout {
        var enabled = false

        val nameView = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }

        val stateView = TextView(context).apply {
            text = context.getString(R.string.state_off)
            setTextColor(colorGray)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            background = makeBackground(colorRow, colorLine, 8, 1)
            addView(
                nameView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            )
            addView(
                stateView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { marginStart = dp(12) }
            )
        }

        row.setOnClickListener {
            enabled = !enabled
            if (enabled) {
                stateView.text = context.getString(R.string.state_on)
                stateView.setTextColor(colorGreen)
                row.background = makeBackground(colorRowOn, colorGreen, 8, 1)
                showMessage(context.getString(R.string.panel_success))
            } else {
                stateView.text = context.getString(R.string.state_off)
                stateView.setTextColor(colorGray)
                row.background = makeBackground(colorRow, colorLine, 8, 1)
                showMessage(context.getString(R.string.panel_off))
            }
        }
        return row
    }

    /** Hiện thông báo ở cuối bảng rồi tự ẩn sau một lúc. */
    private fun showMessage(message: String) {
        statusView.removeCallbacks(hideStatus)
        statusView.text = message
        statusView.visibility = View.VISIBLE
        statusView.postDelayed(hideStatus, MESSAGE_DELAY_MS)
    }

    /** Nền hình chữ nhật bo góc ([radiusDp]) có viền ([strokeDp]). */
    private fun makeBackground(
        fillColor: Int,
        strokeColor: Int,
        radiusDp: Int,
        strokeDp: Int
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fillColor)
        setStroke(dp(strokeDp), strokeColor)
        cornerRadius = dp(radiusDp).toFloat()
    }
}
