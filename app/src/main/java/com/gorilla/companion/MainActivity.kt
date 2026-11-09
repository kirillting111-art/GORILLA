package com.gorilla.companion

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val bg = Color.rgb(15, 19, 30)
    private val panel = Color.rgb(31, 39, 58)
    private val tile = Color.rgb(49, 60, 87)
    private val accent = Color.rgb(199, 181, 255)
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(text("☰", 27f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showNavigation() }
        }, LinearLayout.LayoutParams(dp(44), dp(48)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("GORILLA AI", 19f, Color.WHITE, true))
        heading.addView(text("Твой ИИ · Твои идеи · Твой мир", 11f, 0xFFB7B4C8.toInt()))
        top.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text("⋮", 28f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        root.addView(top)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(12), 0, dp(8))
        }
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(12))
        }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = rounded(0xFF202737.toInt(), 28)
        }
        val plus = text("+", 31f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showAttachments() }
        }
        composer.addView(plus, LinearLayout.LayoutParams(dp(42), dp(48)))
        input = EditText(this).apply {
            hint = "Спросить GORILLA AI"
            setHintTextColor(0xFF9DA4B4.toInt())
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 4
            minLines = 1
            background = null
            setPadding(dp(8), dp(8), dp(4), dp(8))
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(text("▧", 23f, accent).apply {
            gravity = Gravity.CENTER
            setOnClickListener { pickImage() }
        }, LinearLayout.LayoutParams(dp(38), dp(44)))
        composer.addView(text("➤", 24f, accent).apply {
            gravity = Gravity.CENTER
            setOnClickListener { sendMessage() }
        }, LinearLayout.LayoutParams(dp(42), dp(44)))
        root.addView(composer, LinearLayout.LayoutParams(-1, -2))

        setContentView(root)
        showWelcome()
    }

    private fun showWelcome() {
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(30), dp(8), dp(26))
        }
        val logo = TextView(this).apply {
            text = "✳"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = rounded(0xFF302747.toInt(), 28)
        }
        brand.addView(logo, LinearLayout.LayoutParams(dp(88), dp(88)))
        brand.addView(text("GORILLA AI", 30f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        })
        brand.addView(text("Твой ИИ. Твои идеи. Твой мир.", 15f, 0xFFC5C0D6.toInt()).apply { gravity = Gravity.CENTER })
        messages.addView(brand)

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(panel, 24)
            setPadding(dp(16), dp(18), dp(16), dp(16))
        }
        welcome.addView(text("Привет! Чем займёмся сегодня?", 21f, Color.WHITE, true))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(suggestion("⌂", "Помощь с ДЗ") { setPrompt("Помоги решить домашнее задание: ") }, LinearLayout.LayoutParams(0, dp(104), 1f))
        row1.addView(space(dp(8)))
        row1.addView(suggestion("▧", "Создать фото") { setPrompt("Создай изображение: ") }, LinearLayout.LayoutParams(0, dp(104), 1f))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(suggestion("♧", "Совет по стилю") { setPrompt("Помоги подобрать образ: ") }, LinearLayout.LayoutParams(0, dp(104), 1f))
        row2.addView(space(dp(8)))
        row2.addView(suggestion("♡", "Поговорить") { setPrompt("Я хочу поговорить по душам. ") }, LinearLayout.LayoutParams(0, dp(104), 1f))
        grid.addView(row1)
        grid.addView(space( dp(8)))
        grid.addView(row2)
        welcome.addView(grid)
        messages.addView(welcome)
        messages.addView(text("Прикрепляй фото или файлы прямо в чате.", 12f, 0xFF9DA4B4.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        })
    }

    private fun suggestion(icon: String, label: String, action: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = rounded(tile, 18)
            addView(text(icon, 24f, Color.WHITE))
            addView(text(label, 15f, Color.WHITE).apply { setPadding(0, dp(8), 0, 0) })
            setOnClickListener { action() }
        }
    }

    private fun setPrompt(value: String) {
        input.setText(value)
        input.setSelection(input.text.length)
        input.requestFocus()
    }

    private fun sendMessage() {
        val value = input.text.toString().trim()
        if (value.isEmpty()) return
        addBubble(value, true)
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        addBubble("Интерфейс чата готов. Подключение языковой модели и серверной обработки добавим следующим этапом.", false)
    }

    private fun addBubble(value: String, mine: Boolean) {
        val bubble = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(15), dp(12), dp(15), dp(12))
            background = rounded(if (mine) 0xFF51456F.toInt() else panel, 20)
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(dp(4), dp(6), dp(4), dp(6))
            addView(bubble, LinearLayout.LayoutParams(-2, -2).apply { width = (resources.displayMetrics.widthPixels * 0.78f).toInt() })
        }
        messages.addView(line)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showAttachments() {
        val items = arrayOf("Фото из галереи", "Файл", "Камера")
        AlertDialog.Builder(this).setTitle("Добавить вложение").setItems(items) { _, which ->
            when (which) {
                0 -> pickImage()
                1 -> pickFile()
                2 -> {
                    val intent = Intent("android.media.action.IMAGE_CAPTURE")
                    if (intent.resolveActivity(packageManager) != null) startActivityForResult(intent, 103)
                    else Toast.makeText(this, "Камера недоступна", Toast.LENGTH_SHORT).show()
                }
            }
        }.show()
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(Intent.createChooser(intent, "Выбрать фото"), 101)
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(intent, 102)
    }

    @Deprecated("Deprecated in Android API; kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri: Uri? = data?.data
        if (uri != null) {
            val label = if (requestCode == 101) "Прикреплено изображение" else "Прикреплён файл"
            addBubble("$label: ${uri.lastPathSegment ?: "вложение"}", true)
            if (requestCode == 101) {
                val preview = ImageView(this).apply {
                    setImageURI(uri)
                    adjustViewBounds = true
                    maxHeight = dp(220)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                messages.addView(preview)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        } else if (requestCode == 103) {
            addBubble("Фото с камеры добавлено (предпросмотр доступен после подключения обработки вложений).", true)
        }
    }

    private fun showNavigation() {
        AlertDialog.Builder(this).setTitle("GORILLA AI")
            .setItems(arrayOf("Новый чат", "История чатов", "Мой профиль", "Настройки")) { _, which ->
                when (which) {
                    0 -> { messages.removeAllViews(); showWelcome() }
                    1 -> Toast.makeText(this, "История чатов появится после подключения аккаунтов", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Профиль и личный ID добавим на следующем этапе", Toast.LENGTH_SHORT).show()
                    3 -> showSettings()
                }
            }.show()
    }

    private fun showSettings() {
        AlertDialog.Builder(this).setTitle("Настройки GORILLA AI")
            .setItems(arrayOf("Внешний вид", "Аккаунт", "О приложении")) { _, which ->
                val message = when (which) {
                    0 -> "Тёмная тема включена"
                    1 -> "Регистрация и авторизация будут подключены к серверу"
                    else -> "GORILLA AI — твой ИИ-помощник"
                }
                AlertDialog.Builder(this).setMessage(message).setPositiveButton("ОК", null).show()
            }.show()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(width: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(width, 1)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
