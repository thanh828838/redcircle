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
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * Foreground service: thêm vòng tròn (không chạm được) và bảng các ô (bấm được)
 * vào WindowManager để vẽ đè lên mọi app.
 */
class CircleOverlayService : Service() {

    companion object {
        /** Action dùng cho nút TẮT trên notification. */
        const val ACTION_STOP = "com.example.redcircle.action.STOP"

        /** Extra chứa tên các ô (ngăn cách bằng ký tự xuống dòng). */
        const val EXTRA_TEXT = "com.example.redcircle.extra.TEXT"

        private const val CHANNEL_ID = "red_circle_channel"
        private const val NOTIFICATION_ID = 1001

        /** Đường kính vòng tròn: 180dp. */
        private const val CIRCLE_DIAMETER_DP = 180f

        /** Khoảng cách giữa bảng ô và vòng tròn. */
        private const val PANEL_GAP_DP = 8f
    }

    private var windowManager: WindowManager? = null
    private var circleView: CircleView? = null
    private var panelView: PanelView? = null

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

        // Lấy tên các ô từ MainActivity gửi sang
        val labels = intent?.getStringExtra(EXTRA_TEXT)
            ?.split("\n")
            .orEmpty()

        showCircle()
        showPanel(labels)
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
     * Thêm bảng các ô ngay phía trên vòng tròn.
     * Cửa sổ này bấm được (không có FLAG_NOT_TOUCHABLE); phần ngoài các ô vẫn chạm xuyên qua.
     */
    private fun showPanel(rawLabels: List<String>) {
        removePanel() // Gỡ bảng cũ nếu đang có (khi bấm BẬT lại với chữ mới)

        val labels = rawLabels
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(PanelView.MAX_BOXES)
        if (labels.isEmpty()) return

        val wm = windowManager ?: return
        val density = resources.displayMetrics.density

        // Chiều cao bảng = số ô x (chiều cao ô + 2 lề)
        val panelHeightPx =
            labels.size * (PanelView.BOX_HEIGHT_DP + 2 * PanelView.BOX_MARGIN_DP) * density
        val circleRadiusPx = CIRCLE_DIAMETER_DP / 2f * density

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            // Dịch lên trên: mép dưới bảng cách mép trên vòng tròn một khoảng nhỏ
            y = -(circleRadiusPx + PANEL_GAP_DP * density + panelHeightPx / 2f).toInt()
        }

        val view = PanelView(this, labels)
        try {
            wm.addView(view, params)
            panelView = view
        } catch (e: Exception) {
            // Không thêm được bảng -> bỏ qua, vòng tròn vẫn hoạt động
        }
    }

    private fun removePanel() {
        panelView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // View đã bị gỡ trước đó -> bỏ qua
            }
        }
        panelView = null
    }

    /** Gỡ toàn bộ vòng tròn và bảng khỏi màn hình. */
    private fun removeViews() {
        removePanel()
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
