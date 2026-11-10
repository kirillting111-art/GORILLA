package com.gorilla.companion

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainActivity : Activity() {
    private val themeName: String get() = prefsUi.getString("theme", "Тёмная") ?: "Тёмная"
    private val lightTheme: Boolean get() = themeName == "Светлая"
    private val bg: Int get() = when (themeName) { "Светлая" -> Color.rgb(246,247,251); "Неоновая" -> Color.rgb(7,10,24); "Лавандовая" -> Color.rgb(28,22,43); else -> Color.rgb(15,19,30) }
    private val panel: Int get() = when (themeName) { "Светлая" -> Color.WHITE; "Неоновая" -> Color.rgb(15,23,48); "Лавандовая" -> Color.rgb(48,38,68); else -> Color.rgb(31,39,58) }
    private val tile: Int get() = when (themeName) { "Светлая" -> Color.rgb(235,237,246); "Неоновая" -> Color.rgb(23,37,72); "Лавандовая" -> Color.rgb(66,52,91); else -> Color.rgb(49,60,87) }
    private val accent: Int get() = when (themeName) { "Светлая" -> Color.rgb(111,86,190); "Неоновая" -> Color.rgb(70,235,220); "Лавандовая" -> Color.rgb(211,184,255); else -> Color.rgb(199,181,255) }
    private val primaryText: Int get() = if (lightTheme) Color.rgb(30,32,45) else Color.WHITE
    private val secondaryText: Int get() = if (lightTheme) Color.rgb(93,98,116) else Color.rgb(190,190,208)
    private val composerColor: Int get() = if (lightTheme) Color.rgb(232,234,243) else tile
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private var authSession: SupabaseApi.Session? = null
    private var accountProfile: SupabaseApi.Profile? = null
    private var selectedAvatarUri: Uri? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var listeningForSpeech = false
    private var currentChatId: String = UUID.randomUUID().toString()
    private var welcomeVisible = true
    private val prefsUi by lazy { getSharedPreferences("gorilla_ui", MODE_PRIVATE) }
    private val uiLanguage: String get() = prefsUi.getString("language", "ru") ?: "ru"
    private fun tr(ru: String, en: String, zh: String, uk: String): String = when (uiLanguage) { "en" -> en; "zh" -> zh; "uk" -> uk; else -> ru }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val prefs = getSharedPreferences("gorilla_auth", MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        val userId = prefs.getString("user_id", null)
        if (token != null && userId != null) {
            authSession = SupabaseApi.Session(token, prefs.getString("refresh_token", "") ?: "", userId)
            Thread {
                try {
                    val profile = SupabaseApi.profile(token, userId)
                    runOnUiThread {
                        accountProfile = profile
                        if (profile.status == "approved") buildScreen() else showAuthScreen()
                    }
                } catch (_: Exception) {
                    runOnUiThread { showAuthScreen() }
                }
            }.start()
        } else showAuthScreen()
    }

    private fun showAuthScreen() {
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (lightTheme) Color.rgb(246, 247, 251) else bg)
        }
        val scroller = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(22), dp(22), dp(22), dp(28))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroller.addView(content)

        content.addView(text("GORILLA AI", 29f, accent, true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        })
        content.addView(text(tr("ТВОЙ ИИ. ТВОИ ПРАВИЛА.", "YOUR AI. YOUR RULES.", "你的 AI，你的规则。", "ТВІЙ ШІ. ТВОЇ ПРАВИЛА."), 11f, 0xFF9FA8C2.toInt(), true).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setPadding(0, dp(5), 0, dp(20))
        })

        val languageChip = TextView(this).apply {
            text = "◎   " + when (uiLanguage) { "en" -> "English"; "zh" -> "中文"; "uk" -> "Українська"; else -> "Русский" } + "   ▾"
            textSize = 14f
            setTextColor(accent)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = rounded(if (lightTheme) 0xFFE9E5F8.toInt() else 0xFF25243A.toInt(), 22)
            setOnClickListener { chooseLanguage() }
        }
        content.addView(languageChip, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(20) })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(if (lightTheme) Color.WHITE else panel, 26)
            setPadding(dp(20), dp(22), dp(20), dp(22))
            elevation = dp(3).toFloat()
        }
        content.addView(card, LinearLayout.LayoutParams(-1, -2))

        val avatarFrame = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(if (lightTheme) 0xFFF0ECFF.toInt() else 0xFF39314F.toInt(), 30)
            setOnClickListener { pickAvatar() }
        }
        val avatarPreview = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { pickAvatar() }
        }
        avatarFrame.addView(avatarPreview, LinearLayout.LayoutParams(dp(76), dp(76)))
        card.addView(avatarFrame, LinearLayout.LayoutParams(dp(88), dp(88)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(8)
        })
        card.addView(text(tr("Добавить фото профиля · по желанию", "Add a profile photo · optional", "添加头像 · 可选", "Додати фото профілю · за бажанням"), 12f, 0xFFAAAFC1.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
            setOnClickListener { pickAvatar() }
        })

        card.addView(text(tr("Добро пожаловать", "Welcome", "欢迎", "Ласкаво просимо"), 25f, if (lightTheme) 0xFF202333.toInt() else Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(5))
        })
        card.addView(text(tr("Создай аккаунт или войди, чтобы продолжить", "Create an account or sign in to continue", "创建账户或登录以继续", "Створи акаунт або увійди, щоб продовжити"), 13f, 0xFF9FA8BC.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        })

        fun field(hintText: String, type: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText {
            return EditText(this).apply {
                hint = hintText
                setTextColor(if (lightTheme) 0xFF202333.toInt() else Color.WHITE)
                setHintTextColor(0xFF929AAF.toInt())
                inputType = type
                setSingleLine(true)
                textSize = 15f
                background = rounded(if (lightTheme) 0xFFF1F2F7.toInt() else 0xFF171D2B.toInt(), 15)
                setPadding(dp(16), dp(13), dp(16), dp(13))
            }
        }
        val name = field(tr("Имя · необязательно", "Name · optional", "姓名 · 可选", "Ім’я · необов’язково"))
        val email = field(tr("Электронная почта", "Email address", "电子邮箱", "Електронна пошта"), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field(tr("Пароль · минимум 8 символов", "Password · at least 8 characters", "密码 · 至少 8 个字符", "Пароль · мінімум 8 символів"), android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
        listOf(name, email, password).forEachIndexed { index, edit ->
            card.addView(edit, LinearLayout.LayoutParams(-1, dp(54)).apply {
                bottomMargin = if (index == 2) dp(18) else dp(10)
            })
        }

        fun actionButton(label: String, primary: Boolean, action: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (primary) Color.rgb(22, 20, 34) else if (lightTheme) 0xFF37334A.toInt() else accent)
                background = rounded(if (primary) accent else if (lightTheme) 0xFFF0EDFA.toInt() else 0xFF29283D.toInt(), 16)
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { action() }
            }
        }
        card.addView(actionButton(tr("Создать аккаунт", "Create account", "创建账户", "Створити акаунт"), true) {
            val n = name.text.toString().trim()
            val e = email.text.toString().trim()
            val p = password.text.toString()
            if (e.isBlank() || p.length < 8) { toast(tr("Введи почту и пароль от 8 символов", "Enter an email and a password of at least 8 characters", "请输入邮箱和至少 8 位密码", "Введи пошту й пароль від 8 символів")); return@actionButton }
            runAuth {
                val created = SupabaseApi.signUp(e, p, n.ifBlank { e.substringBefore("@") })
                if (created == null) runOnUiThread { toast(tr("Аккаунт создан. Подтверди почту из письма, затем войди.", "Account created. Verify your email, then sign in.", "账户已创建。请先验证邮箱再登录。", "Акаунт створено. Підтверди пошту з листа, потім увійди.")) }
                else acceptSession(created)
            }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) })
        card.addView(actionButton(tr("Уже есть аккаунт? Войти", "Already have an account? Sign in", "已有账户？登录", "Вже маєш акаунт? Увійти"), false) {
            val e = email.text.toString().trim()
            val p = password.text.toString()
            if (e.isBlank() || p.isBlank()) { toast(tr("Введи почту и пароль", "Enter your email and password", "请输入邮箱和密码", "Введи пошту й пароль")); return@actionButton }
            runAuth { acceptSession(SupabaseApi.signIn(e, p)) }
        }, LinearLayout.LayoutParams(-1, dp(52)))

        content.addView(text(tr("🔒  Твои личные чаты доступны только тебе. Доступ к GORILLA AI подтверждается владельцем.", "🔒  Your chats are private to you. GORILLA AI access is approved by the owner.", "🔒  你的聊天仅自己可见。GORILLA AI 访问权限由所有者批准。", "🔒  Твої чати бачиш лише ти. Доступ до GORILLA AI підтверджує власник."), 12f, 0xFF969DB1.toInt()).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(18), dp(10), dp(8))
        })
        page.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun runAuth(action: () -> Unit) {
        Toast.makeText(this, "Подключаемся к серверу…", Toast.LENGTH_SHORT).show()
        Thread {
            try { action() }
            catch (e: Exception) { runOnUiThread { toast("Ошибка: ${e.message ?: "не удалось подключиться"}") } }
        }.start()
    }

    private fun acceptSession(session: SupabaseApi.Session) {
        val profile = SupabaseApi.profile(session.accessToken, session.userId)
        getSharedPreferences("gorilla_auth", MODE_PRIVATE).edit()
            .putString("access_token", session.accessToken)
            .putString("refresh_token", session.refreshToken)
            .putString("user_id", session.userId).apply()
        authSession = session
        accountProfile = profile
        runOnUiThread {
            when (profile.status) {
                "approved" -> buildScreen()
                "pending" -> showCodeDialog()
                else -> toast("Доступ к аккаунту заблокирован. Свяжись с владельцем.")
            }
        }
    }

    private fun showCodeDialog() {
        val field = EditText(this).apply {
            hint = "6-значный код"; inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(Color.WHITE); setHintTextColor(0xFF9DA4B4.toInt())
        }
        AlertDialog.Builder(this).setTitle("Ожидание подтверждения")
            .setMessage("Владелец GORILLA AI должен выдать тебе одноразовый код. Введи его здесь.")
            .setView(field)
            .setPositiveButton("Подтвердить", null)
            .setNegativeButton("Позже") { _, _ -> showAuthScreen() }
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val code=field.text.toString().trim()
                        val session=authSession
                        if (code.length != 6 || session == null) { toast("Нужен код из 6 цифр"); return@setOnClickListener }
                        Thread {
                            try {
                                val id=SupabaseApi.redeemCode(session.accessToken, code)
                                runOnUiThread {
                                    accountProfile=SupabaseApi.Profile(id, "approved", "user")
                                    dialog.dismiss()
                                    toast("Доступ открыт! Твой ID: $id")
                                    buildScreen()
                                }
                            } catch (e: Exception) { runOnUiThread { toast("Код не подошёл или истёк: ${e.message}") } }
                        }.start()
                    }
                }
                dialog.show()
            }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun buildScreen() {
        // Keep the composer in the visible area when the Android IME opens.
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.decorView.systemUiVisibility = 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(if (lightTheme) Color.rgb(246,247,251) else bg)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            clipChildren = false
            clipToPadding = false
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF171B2B.toInt(), 0xFF25203D.toInt())).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), 0x447F69C9)
            }
            setPadding(dp(5), dp(3), dp(5), dp(3))
            elevation = dp(4).toFloat()
        }
        top.addView(text("☰", 27f, primaryText).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showNavigation() }
        }, LinearLayout.LayoutParams(dp(44), dp(48)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(text("GORILLA AI", 19f, primaryText, true))
        heading.addView(text("Твой ИИ · ID ${accountProfile?.publicId ?: "—"}", 11f, 0xFFB7B4C8.toInt()))
        top.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text("⋮", 28f, primaryText).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        root.addView(top)

        scroll = ScrollView(this).apply {
            isFillViewport = false
            isSmoothScrollingEnabled = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
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
            background = rounded(composerColor, 28)
            elevation = dp(8).toFloat()
            clipToPadding = false
        }
        val plus = text("+", 31f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setOnClickListener { showAttachments() }
        }
        composer.addView(plus, LinearLayout.LayoutParams(dp(38), dp(48)))
        composer.addView(text("🎙", 20f, accent).apply {
            gravity = Gravity.CENTER
            contentDescription = tr("Голосовой ввод", "Voice input", "语音输入", "Голосове введення")
            setOnClickListener { toggleSpeechInput() }
        }, LinearLayout.LayoutParams(dp(38), dp(44)))
        input = EditText(this).apply {
            hint = tr("Спросить GORILLA AI", "Ask GORILLA AI", "向 GORILLA AI 提问", "Запитати GORILLA AI")
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, event -> if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND || (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP)) { sendMessage(); true } else false }
            setHintTextColor(if (lightTheme) 0xFF777D91.toInt() else 0xFFB8BED0.toInt())
            setTextColor(if (lightTheme) Color.rgb(25, 28, 42) else Color.WHITE)
            setHintTextColor(if (lightTheme) Color.rgb(92, 98, 116) else Color.rgb(190, 196, 214))
            setLinkTextColor(accent)
            highlightColor = accent
            textSize = 17f
            maxLines = 4
            minLines = 1
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            background = null
            setPadding(dp(8), dp(8), dp(4), dp(8))
        }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(text("▣", 23f, accent).apply {
            gravity = Gravity.CENTER
            setOnClickListener { pickImage() }
        }, LinearLayout.LayoutParams(dp(38), dp(44)))
        composer.addView(text("➤", 24f, accent).apply {
            gravity = Gravity.CENTER
            setOnClickListener { sendMessage() }
        }, LinearLayout.LayoutParams(dp(42), dp(44)))
        root.addView(composer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        setContentView(root)

        // Keep the composer above the on-screen keyboard, including Android 15 edge-to-edge.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val imeOverlap = if (android.os.Build.VERSION.SDK_INT >= 30) {
                val ime = insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
                val navigation = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                (ime - navigation).coerceAtLeast(0)
            } else 0
            view.setPadding(dp(16), dp(10), dp(16), dp(10) + imeOverlap)
            insets
        }

        purgeExpiredChats()
        welcomeVisible = true
        showWelcome()
    }

    private fun showWelcome() {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(28), dp(12), dp(24))
        }
        val emblem = TextView(this).apply {
            text = "◉"
            textSize = 68f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF302052.toInt(), 0xFF11182C.toInt(), 0xFF40246A.toInt())).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), 0xFF9B72FF.toInt())
            }
            elevation = dp(18).toFloat()
        }
        hero.addView(emblem, LinearLayout.LayoutParams(dp(116), dp(116)).apply { bottomMargin = dp(18) })
        emblem.animate().alpha(0.72f).setDuration(1500).withEndAction {
            if (!isFinishing) emblem.animate().alpha(1f).setDuration(1500).start()
        }.start()
        hero.addView(text("GORILLA", 15f, accent, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.28f })
        hero.addView(text("GORILLA AI", 32f, primaryText, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(4))
            letterSpacing = 0.02f
        })
        hero.addView(text(tr("Твой ИИ. Твои идеи. Твой мир.", "Your AI. Your ideas. Your world.", "你的人工智能，你的想法，你的世界。", "Твій ШІ. Твої ідеї. Твій світ."), 15f, secondaryText).apply { gravity = Gravity.CENTER })
        messages.addView(hero)

        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(panel, tile)).apply {
                cornerRadius = dp(26).toFloat()
                setStroke(dp(1), 0x557F69C9.toInt())
            }
            setPadding(dp(19), dp(19), dp(19), dp(19))
            elevation = dp(5).toFloat()
        }
        val greetingName = accountProfile?.displayName?.takeIf { it.isNotBlank() } ?: "друг"
        welcome.addView(text(tr("Приветствуем, $greetingName!", "Welcome, $greetingName!", "欢迎你，$greetingName！", "Вітаємо, $greetingName!"), 21f, primaryText, true))
        welcome.addView(text(tr("Я GORILLA — твой AI-помощник. Спроси что угодно или выбери идею ниже.", "I'm GORILLA, your AI assistant. Ask anything or choose an idea below.", "我是 GORILLA，你的 AI 助手。你可以提问或选择下面的灵感。", "Я GORILLA — твій AI-помічник. Запитай про що завгодно або обери ідею нижче."), 14f, secondaryText).apply { setPadding(0, dp(8), 0, 0) })
        messages.addView(welcome, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        val ideas = listOf(
            tr("✦  Придумай идею", "✦  Give me an idea", "✦  给我一个想法", "✦  Запропонуй ідею"),
            tr("⌘  Помоги с учёбой", "⌘  Help me study", "⌘  帆助我学习", "⌘  Допоможи з навчанням"),
            tr("⚡  Напиши текст", "⚡  Write something", "⚡  写一段文字", "⚡  Напиши текст")
        )
        ideas.forEach { label ->
            val chip = TextView(this).apply {
                text = label
                textSize = 14f
                setTextColor(primaryText)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = GradientDrawable().apply { setColor(tile); cornerRadius = dp(17).toFloat(); setStroke(dp(1), 0x557F69C9) }
                setOnClickListener { input.setText(label.substringAfter("  ")); input.setSelection(input.text.length); input.requestFocus() }
            }
            messages.addView(chip, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
            chip.alpha = 0f
            chip.translationY = dp(12).toFloat()
            chip.animate().alpha(1f).translationY(0f).setDuration(360).start()
        }
        welcome.alpha = 0f
        welcome.translationY = dp(14).toFloat()
        welcome.animate().alpha(1f).translationY(0f).setDuration(420).start()
    }

    private fun historyKey(): String = "chats_${authSession?.userId ?: "local"}"
    private val chatRetentionMs = 30L * 24 * 60 * 60 * 1000

    private fun savedChats(): JSONArray {
        val prefs = getSharedPreferences("gorilla_history", MODE_PRIVATE)
        val key = historyKey()
        val chats = try { JSONArray(prefs.getString(key, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val cutoff = System.currentTimeMillis() - chatRetentionMs
        val valid = JSONArray()
        for (i in 0 until chats.length()) {
            val item = chats.optJSONObject(i) ?: continue
            if (item.optLong("updated", 0L) >= cutoff) valid.put(item)
        }
        if (valid.length() != chats.length()) prefs.edit().putString(key, valid.toString()).apply()
        return valid
    }

    private fun saveChatMessage(value: String, mine: Boolean) {
        val prefs = getSharedPreferences("gorilla_history", MODE_PRIVATE)
        val chats = savedChats()
        var chat: JSONObject? = null
        for (i in 0 until chats.length()) {
            val item = chats.optJSONObject(i) ?: continue
            if (item.optString("id") == currentChatId) { chat = item; break }
        }
        if (chat == null) {
            chat = JSONObject().put("id", currentChatId)
                .put("title", value.take(36).ifBlank { "Новый чат" })
                .put("messages", JSONArray())
        }
        chat.getJSONArray("messages").put(JSONObject().put("text", value).put("mine", mine))
        chat.put("updated", System.currentTimeMillis())
        var replaced = false
        val updated = JSONArray()
        for (i in 0 until chats.length()) {
            val item = chats.optJSONObject(i) ?: continue
            if (item.optString("id") == currentChatId) { updated.put(chat); replaced = true } else updated.put(item)
        }
        if (!replaced) updated.put(chat)
        prefs.edit().putString(historyKey(), updated.toString()).apply()
    }


    private fun toggleSpeechInput() {
        if (listeningForSpeech) {
            speechRecognizer?.stopListening()
            listeningForSpeech = false
            toast(tr("Прослушивание остановлено", "Listening stopped", "已停止聆听", "Прослуховування зупинено"))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast(tr("На устройстве недоступно распознавание речи. Проверь системный сервис Google.", "Speech recognition is unavailable on this device.", "此设备无法使用语音识别。", "На пристрої недоступне розпізнавання мовлення."))
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 210)
            return
        }
        startSpeechRecognition()
    }

    private fun startSpeechRecognition() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listeningForSpeech = true
                    runOnUiThread { toast(tr("Слушаю тебя…", "I'm listening…", "我在听…", "Слухаю тебе…")) }
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listeningForSpeech = false }
                override fun onError(error: Int) {
                    listeningForSpeech = false
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tr("Не расслышал. Попробуй ещё раз.", "I didn't catch that. Try again.", "没有听清，请再试一次。", "Не розчув. Спробуй ще раз.")
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> tr("Нужно разрешение на микрофон.", "Microphone permission is required.", "需要麦克风权限。", "Потрібен дозвіл на мікрофон.")
                        else -> tr("Не удалось распознать речь. Попробуй ещё раз.", "Couldn't recognize speech. Please try again.", "无法识别语音，请重试。", "Не вдалося розпізнати мовлення. Спробуй ще раз.")
                    }
                    runOnUiThread { toast(message) }
                }
                override fun onResults(results: Bundle?) {
                    listeningForSpeech = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) runOnUiThread {
                        input.setText(spoken)
                        input.setSelection(input.text.length)
                        input.requestFocus()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val spoken = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) runOnUiThread {
                        input.setText(spoken)
                        input.setSelection(input.text.length)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, when (uiLanguage) { "en" -> "en-US"; "zh" -> "zh-CN"; "uk" -> "uk-UA"; else -> "ru-RU" })
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            listeningForSpeech = false
            toast(tr("Не удалось запустить микрофон.", "Couldn't start the microphone.", "无法启动麦克风。", "Не вдалося запустити мікрофон."))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 210 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startSpeechRecognition()
        else if (requestCode == 210) toast(tr("Без доступа к микрофону голосовой ввод не работает.", "Voice input needs microphone access.", "语音输入需要麦克风权限。", "Голосове введення потребує доступу до мікрофона."))
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun sendMessage() {
        val value = input.text.toString().trim()
        if (value.isEmpty()) return
        if (welcomeVisible) { messages.removeAllViews(); welcomeVisible = false }
        addBubble(value, true)
        saveChatMessage(value, true)
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        val reply = tr("Интерфейс чата готов. Подключение языковой модели и серверной обработки добавим следующим этапом.", "The chat interface is ready. Connecting the AI model and server processing is the next step.", "聊天界面已准备就绪。下一步将接入语言模型和服务器处理。", "Інтерфейс чату готовий. Підключення мовної моделі та серверної обробки додамо наступним етапом.")
        addBubble(reply, false)
        saveChatMessage(reply, false)
    }

    private fun addBubble(value: String, mine: Boolean) {
        val bubble = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(if (mine) Color.WHITE else primaryText)
            setLineSpacing(dp(3).toFloat(), 1.02f)
            setPadding(dp(17), dp(13), dp(17), dp(13))
            background = if (mine) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accentBubbleColor(), 0xFF49376F.toInt())).apply {
                cornerRadii = floatArrayOf(dp(21).toFloat(),dp(21).toFloat(),dp(7).toFloat(),dp(7).toFloat(),dp(21).toFloat(),dp(21).toFloat(),dp(21).toFloat(),dp(21).toFloat())
            } else GradientDrawable().apply {
                setColor(panel); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0x337F69C9)
            }
            elevation = dp(2).toFloat()
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(dp(3), dp(7), dp(3), dp(7))
            addView(bubble, LinearLayout.LayoutParams(-2, -2).apply { width = (resources.displayMetrics.widthPixels * 0.80f).toInt() })
        }
        messages.addView(line)
        line.alpha = 0f
        line.translationY = dp(10).toFloat()
        line.animate().alpha(1f).translationY(0f).setDuration(240).start()
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun showAttachments() {
        val dialog = Dialog(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = rounded(if (lightTheme) Color.WHITE else panel, 24)
        }
        box.addView(text(tr("Добавить вложение", "Add attachment", "添加附件", "Додати вкладення"), 20f, if (lightTheme) Color.BLACK else Color.WHITE, true))
        fun choice(icon: String, title: String, action: () -> Unit) {
            box.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(12), dp(8), dp(12))
                addView(text(icon, 24f, accent), LinearLayout.LayoutParams(dp(48), -2))
                addView(text(title, 16f, if (lightTheme) Color.BLACK else Color.WHITE))
                setOnClickListener { dialog.dismiss(); action() }
            }, LinearLayout.LayoutParams(-1, dp(54)))
        }
        choice("▧", tr("Фото из галереи", "Photo library", "相册照片", "Фото з галереї")) { pickImage() }
        choice("▤", tr("Файл", "File", "文件", "Файл")) { pickFile() }
        choice("◎", tr("Камера", "Camera", "相机", "Камера")) {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            if (intent.resolveActivity(packageManager) != null) startActivityForResult(intent, 103) else toast("Камера недоступна")
        }
        dialog.setContentView(box)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * .88f).toInt(), -2)
    }

    private fun pickAvatar() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        startActivityForResult(intent, 104)
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
        if (requestCode == 105 && uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            prefsUi.edit().putString("chat_background_uri", uri.toString()).apply()
            toast("Фон чата сохранён")
            return
        }
        if (uri != null) {
            if (requestCode == 104) {
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                getSharedPreferences("gorilla_profile", MODE_PRIVATE).edit().putString("avatar_uri", uri.toString()).apply()
                toast("Аватар сохранён на этом устройстве")
                if (authSession != null) buildScreen() else showAuthScreen()
                return
            }
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

    private fun purgeExpiredChats() {
        savedChats()
        val prefs = getSharedPreferences("gorilla_history", MODE_PRIVATE)
        val lastNoticeKey = "retention_notice_${authSession?.userId ?: "local"}"
        if (!prefs.getBoolean(lastNoticeKey, false)) {
            prefs.edit().putBoolean(lastNoticeKey, true).apply()
            AlertDialog.Builder(this)
                .setTitle("Важно: срок хранения чатов")
                .setMessage("Твои чаты хранятся только на этом устройстве в течение 30 дней с момента последнего сообщения. После этого они автоматически удаляются. История не доступна администраторам.")
                .setPositiveButton("Понятно", null).show()
        }
    }

    private fun startNewChat() {
        currentChatId = UUID.randomUUID().toString()
        messages.removeAllViews()
        welcomeVisible = true
        showWelcome()
        input.setText("")
    }

    private fun openSavedChat(chat: JSONObject) {
        currentChatId = chat.optString("id", UUID.randomUUID().toString())
        messages.removeAllViews()
        welcomeVisible = false
        val items = chat.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            addBubble(item.optString("text"), item.optBoolean("mine"))
        }
    }

    private fun showNavigation() {
        val dialog = Dialog(this)
        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(16), dp(16))
            background = rounded(bg, 0)
        }
        drawer.addView(text("GORILLA AI", 24f, primaryText, true).apply { setPadding(0, 0, 0, dp(24)) })
        fun item(label: String, action: () -> Unit) {
            drawer.addView(TextView(this).apply {
                text = label
                textSize = 18f
                setTextColor(primaryText)
                background = rounded(tile, 14)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { dialog.dismiss(); action() }
            }, LinearLayout.LayoutParams(-1, dp(52)))
        }
        item("✎   " + tr("Новый чат", "New chat", "新建聊天", "Новий чат")) { startNewChat() }
        item("▧   " + tr("Изображения", "Images", "图片", "Зображення")) { pickImage() }
        drawer.addView(text(tr("Недавние", "Recent", "最近", "Недавні"), 16f, secondaryText, true).apply { setPadding(dp(8), dp(24), 0, dp(8)) })
        val recent = (0 until savedChats().length()).mapNotNull { savedChats().optJSONObject(it) }
            .sortedByDescending { it.optLong("updated") }
        if (recent.isEmpty()) drawer.addView(text(tr("Пока нет чатов", "No chats yet", "暂无聊天", "Поки немає чатів"), 14f, secondaryText).apply { setPadding(dp(8), dp(12), 0, dp(12)) })
        recent.take(30).forEach { chat -> item("  " + chat.optString("title", "Новый чат")) { openSavedChat(chat) } }
        drawer.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        item("Мой профиль") {
            val p = accountProfile
            AlertDialog.Builder(this).setTitle("Мой профиль")
                .setMessage("ID: ${p?.publicId ?: "—"}\nИмя: ${p?.displayName ?: "—"}")
                .setPositiveButton("ОК", null).show()
        }
        item(tr("Настройки", "Settings", "设置", "Налаштування")) { showSettings() }
        if (accountProfile?.role == "owner") item("✦   Панель владельца") { showAdminPanel() }
        item("Выйти") {
            getSharedPreferences("gorilla_auth", MODE_PRIVATE).edit().clear().apply()
            authSession = null
            accountProfile = null
            showAuthScreen()
        }
        dialog.setContentView(drawer)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.82f).toInt(), -1)
        dialog.window?.setGravity(Gravity.START)
    }

    private fun showAdminPanel() {
        val session = authSession ?: return
        Thread {
            try {
                val pending = SupabaseApi.pendingAccounts(session.accessToken)
                runOnUiThread {
                    if (pending.length() == 0) {
                        AlertDialog.Builder(this).setTitle("Админ-панель").setMessage("Новых заявок пока нет.")
                            .setPositiveButton("ОК", null).show()
                    } else {
                        val labels = Array(pending.length()) { i ->
                            val row=pending.getJSONObject(i)
                            "#${row.optLong("public_id")} · ${row.optString("display_name")}\n${row.optString("user_id")}"
                        }
                        AlertDialog.Builder(this).setTitle("Заявки на доступ").setItems(labels) { _, which ->
                            val target=pending.getJSONObject(which).getString("user_id")
                            Thread {
                                try {
                                    val code=SupabaseApi.issueCode(session.accessToken,target)
                                    runOnUiThread {
                                        AlertDialog.Builder(this).setTitle("Код доступа")
                                            .setMessage("Передай человеку этот код: $code\n\nОн действует 15 минут и используется один раз.")
                                            .setPositiveButton("Готово", null).show()
                                    }
                                } catch (e: Exception) { runOnUiThread { toast("Не удалось выдать код: ${e.message}") } }
                            }.start()
                        }.setNegativeButton("Закрыть", null).show()
                    }
                }
            } catch (e: Exception) { runOnUiThread { toast("Ошибка админ-панели: ${e.message}") } }
        }.start()
    }

    private fun showSettings() {
        val dialog = Dialog(this)
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(18)); background = rounded(panel, 26) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text(tr("Настройки", "Settings", "设置", "Налаштування"), 23f, primaryText, true)); addView(text("GORILLA AI · " + tr("Персонализация", "Personalization", "个性化", "Персоналізація"), 12f, secondaryText)) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("✕", 22f, secondaryText).apply { gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(dp(38), dp(42)))
        shell.addView(header); shell.addView(View(this), LinearLayout.LayoutParams(1, dp(14)))
        fun settingRow(icon: String, title: String, subtitle: String, action: () -> Unit) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(tile, 17); setPadding(dp(14), dp(12), dp(12), dp(12)); isClickable = true; isFocusable = true; setOnClickListener { dialog.dismiss(); action() } }
            row.addView(text(icon, 22f, accent, true), LinearLayout.LayoutParams(dp(40), dp(42)))
            row.addView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(text(title, 15f, primaryText, true)); addView(text(subtitle, 11f, secondaryText)) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text("›", 25f, secondaryText)); shell.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
        settingRow("◎", tr("Язык приложения", "App language", "应用语言", "Мова застосунку"), tr("Русский · English · 中文 · Українська", "Choose interface language", "选择界面语言", "Оберіть мову інтерфейсу")) { chooseLanguage() }
        settingRow("◐", tr("Внешний вид", "Appearance", "外观", "Зовнішній вигляд"), tr("Тёмная · Светлая · Неоновая · Лавандовая", "Choose a color theme", "选择颜色主题", "Оберіть кольорову тему")) { chooseTheme() }
        settingRow("Aa", tr("Шрифт", "Font", "字体", "Шрифт"), tr("Стиль текста и читаемость", "Text style and readability", "文字样式与可读性", "Стиль тексту та читабельність")) { chooseFont() }
        settingRow("⌨", tr("Клавиатура Android", "Android keyboard", "安卓键盘", "Клавіатура Android"), tr("Выбрать Gboard или другую клавиатуру", "Choose Gboard or another keyboard", "选择 Gboard 或其他键盘", "Обрати Gboard або іншу клавіатуру")) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        settingRow("▧", tr("Фон чата", "Chat background", "聊天背景", "Тло чату"), tr("Установить своё изображение", "Use your own image", "使用自己的图片", "Встановити власне зображення")) { chooseChatBackground() }
        settingRow("ⓘ", tr("О приложении", "About", "关于应用", "Про застосунок"), "GORILLA AI · 1.4") { AlertDialog.Builder(this).setMessage("GORILLA AI · 1.4").setPositiveButton("OK", null).show() }
        dialog.setContentView(shell); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90f).toInt(), -2)
    }
    private fun chooseLanguage() {
        val labels = arrayOf("🇷🇺 Русский", "🇬🇧 English", "🇨🇳 中文", "🇺🇦 Українська")
        val codes = arrayOf("ru", "en", "zh", "uk")
        AlertDialog.Builder(this).setTitle("Выбери язык / Choose language").setSingleChoiceItems(labels, codes.indexOf(uiLanguage)) { d, which ->
            prefsUi.edit().putString("language", codes[which]).apply()
            d.dismiss()
            recreate()
        }.setNegativeButton("Отмена", null).show()
    }

    private fun chooseTheme() {
        val labels = arrayOf("Тёмная", "Светлая", "Неоновая", "Лавандовая")
        AlertDialog.Builder(this).setTitle("Тема оформления").setItems(labels) { _, which ->
            prefsUi.edit().putString("theme", labels[which]).putBoolean("light_theme", which == 1).apply()
            recreate()
        }.show()
    }

    private fun chooseFont() {
        val labels = arrayOf("Стандартный", "С засечками", "Моноширинный", "Округлый")
        AlertDialog.Builder(this).setTitle("Шрифт").setItems(labels) { _, which ->
            prefsUi.edit().putInt("font_style", which).apply()
            recreate()
        }.show()
    }

    private fun chooseChatBackground() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 105)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(if (color == Color.WHITE) primaryText else if (lightTheme && color == 0xFFC5C0D6.toInt()) secondaryText else color)
        typeface = when (prefsUi.getInt("font_style", 0)) {
            1 -> Typeface.SERIF
            2 -> Typeface.MONOSPACE
            3 -> Typeface.create("sans-serif-rounded", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun accentBubbleColor(): Int = when (themeName) {
        "Светлая" -> Color.rgb(91, 72, 157)
        "Неоновая" -> Color.rgb(26, 104, 112)
        "Лавандовая" -> Color.rgb(112, 78, 151)
        else -> Color.rgb(81, 69, 112)
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
