package com.gorilla.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private val prefs by lazy { getSharedPreferences("gorilla_profiles", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        val channel = "gorilla_companion"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(channel, "GORILLA companion", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, channel)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setContentTitle("GORILLA активен")
            .setContentText("Плавающая панель компаньона")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(41, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(41, notification)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildPanel()
    }

    private fun buildPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
            setBackgroundColor(0xEE151A20.toInt())
        }
        root.addView(TextView(this).apply {
            text = "GORILLA • QUICK PROFILES"
            textSize = 16f
            setTextColor(0xFFB7FF35.toInt())
        })
        root.addView(TextView(this).apply {
            text = "Профиль оружия"
            setTextColor(0xFFFFFFFF.toInt())
        })
        val names = listOf("Универсальный", "AKR", "M4", "AWM", "USP", "P350", "SM1014")
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@OverlayService, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(names.indexOf(prefs.getString("weapon", "Универсальный")).coerceAtLeast(0))
            onItemSelectedListener = SimpleItemSelectedListener { prefs.edit().putString("weapon", it).apply() }
        })
        root.addView(slider("Чувствительность (заметка)", "sensitivity", 1, 100, 50))
        root.addView(slider("Размер визуального прицела", "crosshair", 1, 20, 6))
        root.addView(slider("Прозрачность панели", "opacity", 30, 100, 92))
        root.addView(TextView(this).apply {
            text = "Это профили и визуальные настройки панели. Они не управляют прицелом, стрельбой или противниками в игре."
            textSize = 11f
            setTextColor(0xFFADB6C2.toInt())
        })
        root.addView(Button(this).apply { text = "Скрыть"; setOnClickListener { stopSelf() } })
        panel = root
        val lp = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.78f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = 12; y = 120 }
        wm.addView(root, lp)
    }

    private fun slider(label: String, key: String, min: Int, max: Int, initial: Int): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val value = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()) }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = prefs.getInt(key, initial).coerceIn(min, max) - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val v = progress + min
                    value.text = label + ": " + v
                    if (fromUser) prefs.edit().putInt(key, v).apply()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        value.text = label + ": " + (bar.progress + min)
        box.addView(value); box.addView(bar)
        return box
    }

    override fun onDestroy() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
