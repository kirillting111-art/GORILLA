package com.gorilla.companion

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

class VideoStudioDialog(private val activity: Activity, private val accessToken: String?) {
    private val bg = Color.rgb(16, 19, 32)
    private val panel = Color.rgb(31, 37, 57)
    private val tile = Color.rgb(44, 51, 76)
    private val accent = Color.rgb(199, 181, 255)
    private var duration = "15"
    private var aspect = "16:9"

    fun show() {
        val dialog = Dialog(activity)
        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = rounded(panel, 26)
        }
        val scroll = ScrollView(activity)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("ВИДЕОСТУДИЯ", 11f, accent, true))
            addView(label("Создать видео", 25f, Color.WHITE, true))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(label("✕", 23f, Color.LTGRAY).apply {
            gravity = Gravity.CENTER
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(38), dp(44)))
        content.addView(header)
        content.addView(label("Опиши сцену. Сейчас подключена генерация отдельных коротких фрагментов с нативным звуком.", 13f, 0xFFB8BED0.toInt()).apply {
            setPadding(0, dp(7), 0, dp(17))
        })

        val prompt = EditText(activity).apply {
            hint = "Например: кинематографичный пролёт над ночным городом, неон, дождь…"
            setHintTextColor(0xFF9DA6BC.toInt())
            setTextColor(Color.WHITE)
            textSize = 15f
            minLines = 5
            maxLines = 8
            gravity = Gravity.TOP
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(tile, 17)
        }
        content.addView(prompt, LinearLayout.LayoutParams(-1, dp(142)).apply { topMargin = dp(14) })

        setting(content, "Длительность фрагмента", "15 секунд") { valueView ->
            AlertDialog.Builder(activity).setTitle("Длительность фрагмента")
                .setSingleChoiceItems(arrayOf("5 секунд", "10 секунд", "15 секунд"), arrayOf("5", "10", "15").indexOf(duration)) { d, which ->
                    duration = arrayOf("5", "10", "15")[which]
                    valueView.text = "$duration секунд"
                    d.dismiss()
                }.show()
        }
        setting(content, "Формат кадра", aspect) { valueView ->
            AlertDialog.Builder(activity).setTitle("Формат кадра")
                .setSingleChoiceItems(arrayOf("16:9", "9:16", "1:1"), arrayOf("16:9", "9:16", "1:1").indexOf(aspect)) { d, which ->
                    aspect = arrayOf("16:9", "9:16", "1:1")[which]
                    valueView.text = aspect
                    d.dismiss()
                }.show()
        }

        val status = label("Звук включён. Задача отправляется в fal через защищённый Supabase-сервер.", 12f, 0xFFB8BED0.toInt()).apply {
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(0xFF29364A.toInt(), 14)
        }
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val generate = TextView(activity).apply {
            text = "Сгенерировать фрагмент"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFF242033.toInt())
            background = rounded(accent, 17)
            setPadding(dp(12), 0, dp(12), 0)
            isEnabled = accessToken != null
            alpha = if (isEnabled) 1f else .5f
            setOnClickListener {
                val description = prompt.text.toString().trim()
                if (description.length < 8) { prompt.error = "Добавь описание (минимум 8 символов)"; return@setOnClickListener }
                val token = accessToken ?: return@setOnClickListener
                isEnabled = false
                text = "Отправляем в очередь…"
                Thread {
                    try {
                        val submitted = SupabaseApi.submitVideo(token, description, duration, aspect)
                        val id = submitted.optString("request_id")
                        activity.runOnUiThread {
                            text = "Задача отправлена"
                            status.text = "ID задачи: $id\nСтатус: ${submitted.optString("status", "IN_QUEUE")}"
                        }
                        poll(token, id, status, this)
                    } catch (e: Exception) {
                        activity.runOnUiThread {
                            isEnabled = true
                            text = "Повторить"
                            status.text = "Ошибка: ${e.message}"
                        }
                    }
                }.start()
            }
        }
        content.addView(generate, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
        content.addView(label("Важно: один запрос создаёт максимум 15 секунд. Видео около 4 минут потребует генерации множества сцен и отдельного монтажа в единый MP4 — сборка длинного ролика ещё не подключена.", 12f, 0xFFFFD28A.toInt()).apply {
            setPadding(dp(3), dp(12), dp(3), 0)
        })
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(shell)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), (activity.resources.displayMetrics.heightPixels * .86f).toInt())
    }

    private fun poll(token: String, id: String, status: TextView, button: TextView) {
        Thread {
            try {
                var state = "IN_QUEUE"
                var tries = 0
                while (tries < 60 && state != "COMPLETED" && state != "FAILED") {
                    Thread.sleep(5000)
                    val data = SupabaseApi.videoStatus(token, id)
                    state = data.optString("status", state)
                    val current = state
                    activity.runOnUiThread { status.text = "Генерация: $current" }
                    tries++
                }
                if (state == "COMPLETED") {
                    val result = SupabaseApi.videoStatus(token, id, true)
                    val url = result.optJSONObject("video")?.optString("url") ?: result.optJSONObject("video")?.optString("video_url") ?: result.optString("video_url", "")
                    activity.runOnUiThread {
                        status.text = if (url.isNotBlank()) "Готово! Видео создано." else "Готово, но ссылка не найдена."
                        if (url.isNotBlank()) {
                            button.text = "Открыть видео"
                            button.isEnabled = true
                            button.setOnClickListener {
                                activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }
                        } else button.isEnabled = true
                    }
                } else activity.runOnUiThread { status.text = "Статус: $state. Проверь задачу позже."; button.isEnabled = true }
            } catch (e: Exception) {
                activity.runOnUiThread { status.text = "Ошибка проверки: ${e.message}"; button.isEnabled = true }
            }
        }.start()
    }

    private fun setting(parent: LinearLayout, title: String, value: String, action: (TextView) -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(tile, 14)
            setPadding(dp(14), dp(9), dp(14), dp(9))
        }
        val valueView = label(value, 14f, Color.WHITE, true)
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 12f, 0xFF9DA6BC.toInt()))
            addView(valueView)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("⌄", 21f, accent))
        row.setOnClickListener { action(valueView) }
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(8) })
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
