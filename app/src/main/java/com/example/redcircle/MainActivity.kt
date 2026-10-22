package com.example.redcircle

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Màn hình chính: kiểm tra quyền overlay, nhập tiêu đề + tên các nút chức năng,
 * và có 2 nút BẬT / TẮT.
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        const val PREFS_NAME = "red_circle_prefs"
        const val KEY_TEXT = "overlay_text"
        const val KEY_TITLE = "overlay_title"
    }

    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvHint: TextView
    private lateinit var groupInputs: View
    private lateinit var edtTitle: EditText
    private lateinit var edtText: EditText

    // Mở màn hình cấp quyền overlay; khi quay lại thì cập nhật giao diện
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateUi()
        }

    // Xin quyền thông báo (Android 13+); không bắt buộc để service chạy
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvHint = findViewById(R.id.tvHint)
        groupInputs = findViewById(R.id.groupInputs)
        edtTitle = findViewById(R.id.edtTitle)
        edtText = findViewById(R.id.edtText)

        // Nạp lại nội dung đã nhập lần trước
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        edtTitle.setText(prefs.getString(KEY_TITLE, ""))
        edtText.setText(prefs.getString(KEY_TEXT, ""))

        btnStart.setOnClickListener { onStartClicked() }
        btnStop.setOnClickListener {
            // Dừng service -> onDestroy sẽ gỡ vòng tròn và menu khỏi màn hình
            stopService(Intent(this, CircleOverlayService::class.java))
        }

        // Lần mở đầu tiên: nếu chưa có quyền thì mở Settings để người dùng cấp
        if (savedInstanceState == null && !hasOverlayPermission()) {
            requestOverlayPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    /** Đã được cấp quyền "Hiển thị trên các ứng dụng khác" chưa? */
    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    /** Mở trang Settings cấp quyền overlay cho app này. */
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            overlayPermissionLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_cannot_open_settings, Toast.LENGTH_LONG).show()
        }
    }

    private fun onStartClicked() {
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
            return
        }

        // Android 13+: xin quyền thông báo để thấy notification của service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Lưu nội dung đã nhập để lần sau mở app còn sẵn
        val title = edtTitle.text.toString()
        val text = edtText.text.toString()
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_TEXT, text)
            .apply()

        // Khởi động foreground service và gửi kèm tiêu đề + tên các nút
        val intent = Intent(this, CircleOverlayService::class.java)
            .putExtra(CircleOverlayService.EXTRA_TITLE, title)
            .putExtra(CircleOverlayService.EXTRA_TEXT, text)
        ContextCompat.startForegroundService(this, intent)
    }

    /** Có quyền -> hiện khung nhập và 2 nút; chưa có quyền -> hiện lời nhắc. */
    private fun updateUi() {
        val granted = hasOverlayPermission()
        val state = if (granted) View.VISIBLE else View.GONE
        groupInputs.visibility = state
        btnStart.visibility = state
        btnStop.visibility = state
        tvHint.visibility = if (granted) View.GONE else View.VISIBLE
    }
}
