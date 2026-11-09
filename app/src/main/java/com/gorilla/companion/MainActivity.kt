package com.gorilla.companion

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val gamePackage = "com.axlebolt.standoff2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
            setBackgroundColor(0xFF101318.toInt())
        }
        root.addView(TextView(this).apply {
            text = "GORILLA"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(0xFFB7FF35.toInt())
        })
        root.addView(TextView(this).apply {
            text = "STANDOFF 2 COMPANION"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFB8C0CC.toInt())
        })
        root.addView(action("Разрешить окно поверх игры") {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            }
        })
        root.addView(action("Запустить панель GORILLA") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Сначала разреши показ поверх других окон", Toast.LENGTH_LONG).show()
            } else {
                val service = Intent(this, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            }
        })
        root.addView(action("Открыть оригинальный Standoff 2") {
            val launch = packageManager.getLaunchIntentForPackage(gamePackage)
            if (launch != null) startActivity(launch)
            else Toast.makeText(this, "Оригинальный Standoff 2 не найден на устройстве", Toast.LENGTH_LONG).show()
        })
        root.addView(action("Остановить панель") { stopService(Intent(this, OverlayService::class.java)) })
        root.addView(TextView(this).apply {
            text = "Настройки компаньона сохраняются локально. Приложение не вмешивается в механику игры."
            textSize = 12f
            setTextColor(0xFF8993A2.toInt())
            setPadding(4, 24, 4, 0)
        })
        setContentView(root)
    }

    private fun action(label: String, run: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { run() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 }
    }
}
