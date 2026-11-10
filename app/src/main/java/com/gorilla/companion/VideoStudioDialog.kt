package com.gorilla.companion

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * First UI layer for the video studio.
 * Provider calls intentionally remain disabled until a server-side video provider is configured.
 */
class VideoStudioDialog(private val activity: Activity) {
    private val bg = Color.rgb(16, 19, 32)
    private val panel = Color.rgb(31, 37, 57)
    private val tile = Color.rgb(44, 51, 76)
    private val accent = Color.rgb(199, 181, 255)
    private var mode = 0
    private var duration = "5 секунд"
    private var aspect = "16:9 · горизонтальное"
    private var quality = "Стандартное"

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
        content.addView(label("Опиши сцену — настройки можно изменить ниже.", 13f, 0xFFB8BED0.toInt()).apply {
            setPadding(0, dp(7), 0, dp(17))
        })

        val modeRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val textMode = modeButton("Текст → видео", true)
        val imageMode = modeButton("Фото → видео", false)
        modeRow.addView(textMode, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
        modeRow.addView(imageMode, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        content.addView(modeRow)
        val prompt = EditText(activity).apply {
            hint = "Например: кинематографичный пролёт камеры над ночным городом, неоновые огни, дождь…"
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

        val settings = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(settings, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        fun refreshSettings() {
            settings.removeAllViews()
            setting(settings, "Длительность", duration) {
                choose("Длительность", arrayOf("5 секунд", "8 секунд", "10 секунд"), duration) { duration = it; refreshSettings() }
            }
            setting(settings, "Формат кадра", aspect) {
                choose("Формат кадра", arrayOf("16:9 · горизонтальное", "9:16 · вертикальное", "1:1 · квадрат"), aspect) { aspect = it; refreshSettings() }
            }
            setting(settings, "Качество", quality) {
                choose("Качество", arrayOf("Стандартное", "Высокое"), quality) { quality = it; refreshSettings() }
            }
        }
        refreshSettings()

        val status = label("Провайдер видео ещё не подключён. Поэтому генерация пока выключена — это не имитация готового результата.", 12f, 0xFFFFD28A.toInt()).apply {
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(0xFF493A2C, 14)
        }
        content.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val generate = TextView(activity).apply {
            text = "Подключение видеогенератора"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFF242033.toInt())
            background = rounded(accent, 17)
            alpha = 0.55f
            setPadding(dp(12), 0, dp(12), 0)
            isEnabled = false
        }
        content.addView(generate, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14) })
        content.addView(label("Когда серверный провайдер будет подключён, здесь появятся очередь задач, прогресс, предпросмотр и сохранение готовых роликов.", 12f, 0xFF9DA6BC.toInt()).apply {
            setPadding(dp(3), dp(12), dp(3), 0)
        })
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(shell)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), (activity.resources.displayMetrics.heightPixels * .86f).toInt())
    }

    private fun setting(parent: LinearLayout, title: String, value: String, action: () -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(tile, 14)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener { action() }
        }
        row.addView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 12f, 0xFF9DA6BC.toInt()))
            addView(label(value, 14f, Color.WHITE, true))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("⌄", 21f, accent))
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(58)).apply { bottomMargin = dp(7) })
    }

    private fun choose(title: String, options: Array<String>, current: String, onSelected: (String) -> Unit) {
        AlertDialog.Builder(activity).setTitle(title).setSingleChoiceItems(options, options.indexOf(current)) { d, which ->
            onSelected(options[which]); d.dismiss()
        }.setNegativeButton("Отмена", null).show()
    }

    private fun modeButton(title: String, selected: Boolean) = TextView(activity).apply {
        text = title
        gravity = Gravity.CENTER
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (selected) Color.rgb(27, 24, 40) else Color.WHITE)
        background = rounded(if (selected) accent else tile, 14)
        setOnClickListener {
            AlertDialog.Builder(activity).setTitle("Режим генерации")
                .setItems(arrayOf("Текст → видео", "Фото → видео")) { _, which ->
                    mode = which
                    // Keep the selected mode explicit until image upload is wired to the provider.
                    show()
                }.show()
        }
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
