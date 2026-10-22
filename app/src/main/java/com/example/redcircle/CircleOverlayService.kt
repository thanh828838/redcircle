package com.example.redcircle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * Foreground service: thêm vòng tròn (không chạm được) và bong bóng menu (kéo thả được)
 * vào WindowManager để vẽ đè lên mọi app.
 */
class CircleOverlayService : Service() {

    companion object {
        /** Action dùng cho nút TẮT trên notification. */
        const val ACTION_STOP = "com.example.redcircle.action.STOP"

        /** Extra chứa tên các nút chức năng (ngăn cách bằng ký tự xuống dòng). */
        const val EXTRA_TEXT = "com.example.redcircle.extra.TEXT"

        /** Extra chứa tiêu đề menu. */
        const val EXTRA_TITLE = "com.example.redcircle.extra.TITLE"

        private const val DEFAULT_TITLE = "MENU"
        private const val CHANNEL_ID = "red_circle_channel"
        private const val NOTIFICATION_ID = 1001

        /** Đường kính vòng tròn: 110dp (cỡ trung bình). */
        private const val CIRCLE_DIAMETER_DP = 110f

        /** Vị trí ban đầu của bong bóng menu (tính từ góc trên trái màn hình). */
        private const val MENU_START_X_DP = 16f
        private const val MENU_START_Y_DP = 140f
    }

    private var windowManager: WindowManager? = null
    private var circleView: CircleView? = null
    private var menuView: FloatingMenuView? = null
    private var menuParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Người dùng bấm TẮT trên notification
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Phải gọi startForeground sớm sau startForegroundService (trong vòng 5 giây)
        startInForeground()

        // Nếu quyền overlay bị thu hồi thì không thể vẽ -> dừng service
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Lấy tiêu đề và tên các nút từ MainActivity gửi sang
        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?.trim()
            .orEmpty()
            .ifEmpty { DEFAULT_TITLE }
        val labels = intent?.getStringExtra(EXTRA_TEXT)
            ?.split("\n")
            .orEmpty()

        showCircle()
        showMenu(title, labels)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeViews()
        super.onDestroy()
    }

    /** Chạy service ở chế độ foreground kèm notification. */
    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: bắt buộc khai báo loại foreground service
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Tạo notification channel (chỉ cần từ Android 8.0). */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /** Dựng notification "Đang vẽ vòng tròn" có nút TẮT. */
    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CircleOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** API 26+ dùng TYPE_APPLICATION_OVERLAY, thấp hơn dùng TYPE_PHONE. */
    @Suppress("DEPRECATION")
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

    /** Thêm vòng tròn vào giữa màn hình, không chặn thao tác chạm. */
    private fun showCircle() {
        if (circleView != null) return // Đã hiển thị rồi

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val sizePx = (CIRCLE_DIAMETER_DP * resources.displayMetrics.density).toInt()

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER // Chính giữa màn hình
        }

        val view = CircleView(this)
        try {
            wm.addView(view, params)
            circleView = view
        } catch (e: Exception) {
            // Không thêm được view (ví dụ thiếu quyền) -> dừng service
            stopSelf()
        }
    }

    /**
     * Thêm bong bóng menu. Cửa sổ này bấm được (không có FLAG_NOT_TOUCHABLE),
     * nhưng chỉ chiếm đúng vùng của bong bóng/bảng nên phần còn lại vẫn chạm xuyên qua.
     */
    private fun showMenu(title: String, rawLabels: List<String>) {
        removeMenu() // Gỡ menu cũ nếu đang có (khi bấm BẬT lại với nội dung mới)

        val labels = rawLabels
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(FloatingMenuView.MAX_ITEMS)
        if (labels.isEmpty()) return

        val wm = windowManager ?: return
        val density = resources.displayMetrics.density

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        // Gốc toạ độ ở góc trên trái màn hình để dễ kéo thả
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (MENU_START_X_DP * density).toInt()
            y = (MENU_START_Y_DP * density).toInt()
        }

        val view = FloatingMenuView(this, title, labels) {
            onMenuExpandedChanged()
        }
        view.bubble.setOnTouchListener(DragListener(view, params))

        try {
            wm.addView(view, params)
            menuView = view
            menuParams = params
        } catch (e: Exception) {
            // Không thêm được menu -> bỏ qua, vòng tròn vẫn hoạt động
        }
    }

    /** Khi menu mở/gập: chờ View đo lại kích thước rồi kéo cả menu vào trong màn hình. */
    private fun onMenuExpandedChanged() {
        val view = menuView ?: return
        val params = menuParams ?: return
        view.post {
            clampToScreen(view, params)
            updateMenuLayout(view, params)
        }
    }

    /** Giữ menu không trượt ra ngoài mép màn hình. */
    private fun clampToScreen(view: View, params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - view.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - view.height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun updateMenuLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager?.updateViewLayout(view, params)
        } catch (e: Exception) {
            // View đã bị gỡ -> bỏ qua
        }
    }

    /**
     * Bộ xử lý kéo thả cho bong bóng:
     * - Vuốt quá ngưỡng -> di chuyển cả menu theo ngón tay.
     * - Chạm nhẹ (không kéo) -> mở/gập menu.
     */
    private inner class DragListener(
        private val view: FloatingMenuView,
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(this@CircleOverlayService).scaledTouchSlop
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt()
                        clampToScreen(view, params)
                        updateMenuLayout(view, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) view.toggle() // Chạm nhẹ -> mở/gập menu
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    private fun removeMenu() {
        menuView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // View đã bị gỡ trước đó -> bỏ qua
            }
        }
        menuView = null
        menuParams = null
    }

    /** Gỡ toàn bộ vòng tròn và menu khỏi màn hình. */
    private fun removeViews() {
        removeMenu()
        circleView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // View đã bị gỡ trước đó -> bỏ qua
            }
        }
        circleView = null
    }
}
