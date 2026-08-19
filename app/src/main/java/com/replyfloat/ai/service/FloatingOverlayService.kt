package com.replyfloat.ai.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.replyfloat.ai.MainActivity
import com.replyfloat.ai.R
import com.replyfloat.ai.ReplyFloatApplication
import com.replyfloat.ai.ai.GeminiProvider
import com.replyfloat.ai.model.AIProviderConfig
import com.replyfloat.ai.model.ReplyRequest
import com.replyfloat.ai.model.ReplySuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayRootView: FrameLayout? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var generateJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isExpanded = false
    private var touchSlop = 16

    // State matching React simulation
    private var currentDetectedText = ""
    private var currentAppName = "Global"
    private var selectedStyle = "Logical"
    private var currentSuggestions = mutableListOf<ReplySuggestion>()
    private var isLoading = false

    // Views references for dynamic update
    private var collapsedPillView: LinearLayout? = null
    private var expandedPanelView: LinearLayout? = null
    private var badgeCountView: TextView? = null
    private var appNameBadgeView: TextView? = null
    private var activeStyleTagView: TextView? = null
    private var expandedStyleBadge: TextView? = null
    private var contextSnippetView: TextView? = null
    private var suggestionsContainer: LinearLayout? = null
    private var loadingProgressBar: ProgressBar? = null
    private var loadingStatusText: TextView? = null
    private var stylesHorizontalContainer: LinearLayout? = null

    private val styleOptions = listOf(
        "Logical", "Short", "Casual", "Formal", "Funny", "Debate", "Respectful", "Counterargument", "Detailed"
    )

    companion object {
        const val ACTION_START = "ACTION_START_OVERLAY"
        const val ACTION_STOP = "ACTION_STOP_OVERLAY"
        const val EXTRA_DETECTED_TEXT = "EXTRA_DETECTED_TEXT"
        const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startService(context: Context, text: String = "", pkg: String = "") {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DETECTED_TEXT, text)
                putExtra(EXTRA_PACKAGE_NAME, pkg)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("replyfloat_prefs", Context.MODE_PRIVATE)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeOverlay()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
                return START_NOT_STICKY
            }
            else -> {
                startForeground(1001, createNotification())
                val detectedText = intent?.getStringExtra(EXTRA_DETECTED_TEXT) ?: ""
                val pkg = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

                if (pkg.isNotBlank()) {
                    currentAppName = resolveAppName(pkg)
                }
                if (detectedText.isNotBlank()) {
                    currentDetectedText = detectedText
                }

                if (overlayRootView == null) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                        showNativeOverlay()
                    }
                } else {
                    updateContextViews()
                    if (detectedText.isNotBlank()) {
                        generateRepliesForCurrentContext()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun resolveAppName(pkg: String): String {
        return when {
            pkg.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
            pkg.contains("telegram", ignoreCase = true) -> "Telegram"
            pkg.contains("discord", ignoreCase = true) -> "Discord"
            pkg.contains("reddit", ignoreCase = true) -> "Reddit"
            pkg.contains("messaging", ignoreCase = true) || pkg.contains("mms", ignoreCase = true) -> "SMS Messages"
            pkg.contains("instagram", ignoreCase = true) -> "Instagram"
            pkg.contains("twitter", ignoreCase = true) || pkg.contains("x.android", ignoreCase = true) -> "X / Twitter"
            pkg.contains("chrome", ignoreCase = true) -> "Chrome"
            else -> "App"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showNativeOverlay() {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(140)
        }

        val rootLayout = FrameLayout(this)

        // 1. Build the Compact Floating Pill (Matches simulation pill)
        val pill = buildCollapsedPill()
        collapsedPillView = pill

        // 2. Build the Expanded Panel (Matches simulation full modal)
        val panel = buildExpandedPanel()
        expandedPanelView = panel

        rootLayout.addView(pill)
        rootLayout.addView(panel)

        // Setup Drag & Click Listener on Collapsed Pill
        setupPillTouchListener(pill, rootLayout)

        overlayRootView = rootLayout
        try {
            windowManager.addView(rootLayout, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize default replies or generate from initial context
        if (currentDetectedText.isNotBlank()) {
            generateRepliesForCurrentContext()
        } else {
            loadDefaultSuggestions()
        }
    }

    /**
     * Builds the exact Compact Floating Bar (Matching FloatingOverlay.tsx in simulation)
     */
    private fun buildCollapsedPill(): LinearLayout {
        val context = this
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = createCardBackground("#090D16", "#EF4444", dp(24), 2)
            elevation = dp(10).toFloat()

            // Red AI Sparkle Circle Badge
            val aiBadge = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply {
                    marginEnd = dp(8)
                }
                background = createCardBackground("#7F1D1D", "#EF4444", dp(13), 1)

                val sparkDot = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#EF4444"))
                    }
                }
                addView(sparkDot)
            }
            addView(aiBadge)

            // Title: ReplyFloat AI
            val titleText = TextView(context).apply {
                this.text = "ReplyFloat AI"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(titleText)

            // App Name Pill (e.g. WhatsApp / Context)
            val appBadge = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(6)
                }
                this.text = currentAppName
                setTextColor(Color.parseColor("#FCA5A5"))
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = createCardBackground("#1C1114", "#EF4444", dp(8), 1)
            }
            appNameBadgeView = appBadge
            addView(appBadge)

            // Active Style Chip
            val styleTag = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(6)
                }
                this.text = "⚡ $selectedStyle"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = createCardBackground("#2A1215", "#DC2626", dp(8), 1)
            }
            activeStyleTagView = styleTag
            addView(styleTag)

            // Suggestion Count Badge (3)
            val countBadge = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(6)
                }
                this.text = "3"
                setTextColor(Color.parseColor("#FEE2E2"))
                textSize = 10.5f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(5), dp(2), dp(5), dp(2))
                background = createCardBackground("#991B1B", "#DC2626", dp(10), 1)
            }
            badgeCountView = countBadge
            addView(countBadge)

            // Action: Expand Icon Button
            val expandBtn = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(8)
                }
                this.text = " ⤢ "
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setOnClickListener {
                    expandPanel()
                }
            }
            addView(expandBtn)
        }
    }

    /**
     * Builds the exact Expanded Floating Reply Panel (Matching simulation panel)
     */
    private fun buildExpandedPanel(): LinearLayout {
        val context = this
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(335), FrameLayout.LayoutParams.WRAP_CONTENT)
            background = createCardBackground("#090D16", "#EF4444", dp(18), 2)
            elevation = dp(14).toFloat()
            setPadding(dp(14), dp(12), dp(14), dp(12))

            // 1. Top Header Bar
            val headerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }

                // AI Icon Dot
                val dot = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply {
                        marginEnd = dp(6)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#EF4444"))
                    }
                }
                addView(dot)

                // Title
                val title = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    this.text = "ReplyFloat AI"
                    setTextColor(Color.WHITE)
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(title)

                // Active Style Pill
                val styleBadge = TextView(context).apply {
                    this.text = selectedStyle
                    setTextColor(Color.parseColor("#FCA5A5"))
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    background = createCardBackground("#7F1D1D", "#DC2626", dp(8), 1)
                }
                expandedStyleBadge = styleBadge
                addView(styleBadge)

                // Settings Button (Opens App)
                val settingsBtn = TextView(context).apply {
                    this.text = " ⚙ "
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 14f
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    setOnClickListener {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(intent)
                    }
                }
                addView(settingsBtn)

                // Collapse Button
                val collapseBtn = TextView(context).apply {
                    this.text = " ▲ "
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 14f
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    setOnClickListener {
                        collapsePanel()
                    }
                }
                addView(collapseBtn)

                // Close Button
                val closeBtn = TextView(context).apply {
                    this.text = " ✕ "
                    setTextColor(Color.parseColor("#EF4444"))
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(4), dp(2), dp(4), dp(2))
                    setOnClickListener {
                        removeOverlay()
                    }
                }
                addView(closeBtn)
            }
            addView(headerRow)

            // 2. Detected Context Card Preview
            val contextCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
                background = createCardBackground("#161B22", "#30363D", dp(10), 1)

                val ctxHeader = TextView(context).apply {
                    this.text = "CONTEXT DETECTED ($currentAppName)"
                    setTextColor(Color.parseColor("#EF4444"))
                    textSize = 9.5f
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(ctxHeader)

                val snippet = TextView(context).apply {
                    this.text = currentDetectedText.ifBlank { "Awaiting message context from active chat..." }
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 11.5f
                    maxLines = 2
                }
                contextSnippetView = snippet
                addView(snippet)
            }
            addView(contextCard)

            // 3. Style Selector Scrollable Chips
            val styleScrollView = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            }

            val stylesRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            stylesHorizontalContainer = stylesRow
            styleScrollView.addView(stylesRow)
            addView(styleScrollView)
            populateStyleChips()

            // 4. Loading State View
            val loadingLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                visibility = View.GONE
                setPadding(dp(16), dp(16), dp(16), dp(16))

                val progressBar = ProgressBar(context).apply {
                    isIndeterminate = true
                }
                loadingProgressBar = progressBar
                addView(progressBar)

                val loadText = TextView(context).apply {
                    this.text = "Generating replies with Gemini AI..."
                    setTextColor(Color.parseColor("#FCA5A5"))
                    textSize = 11.5f
                    setPadding(0, dp(8), 0, 0)
                }
                loadingStatusText = loadText
                addView(loadText)
            }
            addView(loadingLayout)

            // 5. Scrollable Suggestions Container
            val scrollWrapper = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(210)
                )
            }

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            suggestionsContainer = container
            scrollWrapper.addView(container)
            addView(scrollWrapper)

            // 6. Bottom Action Footer
            val footerRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)

                // Regenerate Button
                val regenBtn = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(10), dp(6), dp(12), dp(6))
                    background = createCardBackground("#DC2626", "#EF4444", dp(10), 1)

                    val btnText = TextView(context).apply {
                        this.text = "↻ Regenerate"
                        setTextColor(Color.WHITE)
                        textSize = 11.5f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    addView(btnText)

                    setOnClickListener {
                        generateRepliesForCurrentContext()
                    }
                }
                addView(regenBtn)

                // Spacer
                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                }
                addView(spacer)

                // Auto-Detect Status Tag
                val autoTag = TextView(context).apply {
                    this.text = "● Auto-Detect ON"
                    setTextColor(Color.parseColor("#4ADE80"))
                    textSize = 10.5f
                }
                addView(autoTag)
            }
            addView(footerRow)
        }

        return panel
    }

    private fun populateStyleChips() {
        val row = stylesHorizontalContainer ?: return
        row.removeAllViews()

        for ((index, styleName) in styleOptions.withIndex()) {
            val isSelected = styleName.equals(selectedStyle, ignoreCase = true)
            val chip = TextView(this).apply {
                this.text = styleName
                textSize = 11f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#94A3B8"))
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = if (isSelected) {
                    createCardBackground("#DC2626", "#EF4444", dp(12), 1)
                } else {
                    createCardBackground("#161B22", "#30363D", dp(12), 1)
                }
                setOnClickListener {
                    selectedStyle = styleName
                    activeStyleTagView?.text = "⚡ $selectedStyle"
                    expandedStyleBadge?.text = selectedStyle
                    populateStyleChips()
                    generateRepliesForCurrentContext()
                }
            }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) marginStart = dp(6)
            }
            row.addView(chip, lp)
        }
    }

    private fun renderSuggestionCards() {
        val container = suggestionsContainer ?: return
        container.removeAllViews()

        if (currentSuggestions.isEmpty()) {
            val emptyNotice = TextView(this).apply {
                this.text = "Tap 'Regenerate' or type a message to generate contextual replies."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setPadding(dp(8), dp(16), dp(8), dp(16))
                gravity = Gravity.CENTER
            }
            container.addView(emptyNotice)
            return
        }

        for ((index, suggestion) in currentSuggestions.withIndex()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
                background = createCardBackground("#161B22", "#30363D", dp(12), 1)

                // Top row: Number Badge + Tone Tag
                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(6)
                    }

                    // Number circle
                    val numBadge = TextView(context).apply {
                        this.text = "${index + 1}"
                        setTextColor(Color.parseColor("#FCA5A5"))
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(5), dp(1), dp(5), dp(1))
                        background = createCardBackground("#7F1D1D", "#DC2626", dp(8), 1)
                    }
                    addView(numBadge)

                    // Tone Label
                    val toneLabel = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = dp(6)
                        }
                        this.text = suggestion.tone.ifBlank { selectedStyle }
                        setTextColor(Color.parseColor("#EF4444"))
                        textSize = 10.5f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    addView(toneLabel)

                    // Confidence tag
                    val confLabel = TextView(context).apply {
                        this.text = "${(suggestion.confidence * 100).toInt()}% Match"
                        setTextColor(Color.parseColor("#86EFAC"))
                        textSize = 9.5f
                    }
                    addView(confLabel)
                }
                addView(topRow)

                // Main Reply Text
                val replyText = TextView(context).apply {
                    this.text = suggestion.text
                    setTextColor(Color.parseColor("#E2E8F0"))
                    textSize = 13f
                    setLineSpacing(dp(2).toFloat(), 1.15f)
                }
                addView(replyText)

                // Action Row: Copy & Insert Buttons
                val actionRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    setPadding(0, dp(8), 0, 0)

                    // Copy Action Button
                    val copyBtn = TextView(context).apply {
                        this.text = " 📋 Copy "
                        setTextColor(Color.parseColor("#FCA5A5"))
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(10), dp(4), dp(10), dp(4))
                        background = createCardBackground("#2A1215", "#EF4444", dp(8), 1)

                        setOnClickListener {
                            copyToClipboard(suggestion.text)
                            vibrate(35)
                            this.text = " ✓ Copied! "
                            this.setTextColor(Color.parseColor("#86EFAC"))
                            mainHandler.postDelayed({
                                this.text = " 📋 Copy "
                                this.setTextColor(Color.parseColor("#FCA5A5"))
                            }, 1500)
                            Toast.makeText(context, "Copied reply to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    addView(copyBtn)
                }
                addView(actionRow)
            }
            container.addView(card)
        }
    }

    private fun generateRepliesForCurrentContext() {
        val text = currentDetectedText.ifBlank { "What did Gandhi do for India's freedom?" }

        generateJob?.cancel()
        setLoading(true)

        generateJob = serviceScope.launch {
            try {
                val apiKey = prefs.getString("gemini_api_key", "") ?: ""
                val provider = GeminiProvider(
                    AIProviderConfig(
                        id = "gemini",
                        name = "Google Gemini",
                        apiKey = apiKey,
                        model = "gemini-2.5-flash"
                    )
                )

                val result = provider.generateReplies(
                    ReplyRequest(
                        conversationText = text,
                        style = selectedStyle,
                        count = 3,
                        length = "short",
                        contextApp = currentAppName
                    )
                )

                if (result.isSuccess) {
                    val list = result.getOrNull()
                    if (!list.isNullOrEmpty()) {
                        currentSuggestions.clear()
                        currentSuggestions.addAll(list)
                    } else {
                        fallbackSuggestions(text)
                    }
                } else {
                    fallbackSuggestions(text)
                }
            } catch (e: Exception) {
                fallbackSuggestions(text)
            } finally {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    renderSuggestionCards()
                }
            }
        }
    }

    private fun fallbackSuggestions(contextText: String) {
        currentSuggestions.clear()
        when (selectedStyle.lowercase()) {
            "debate" -> {
                currentSuggestions.add(
                    ReplySuggestion("1", "Debate", "While that's a common perspective, the foundational evidence points to a completely different conclusion.", "Counterargument", 0.96f)
                )
                currentSuggestions.add(
                    ReplySuggestion("2", "Debate", "Let's examine the premise: if that were true, we would see direct contradictions in the outcome.", "Rhetorical", 0.94f)
                )
                currentSuggestions.add(
                    ReplySuggestion("3", "Debate", "There are two critical flaws with that reasoning that we should clarify before proceeding.", "Analytical", 0.92f)
                )
            }
            "funny" -> {
                currentSuggestions.add(
                    ReplySuggestion("1", "Funny", "Bold of you to assume I have my life together enough to answer this right now! 😂", "Witty", 0.98f)
                )
                currentSuggestions.add(
                    ReplySuggestion("2", "Funny", "10/10 plot twist, did not see that coming on a Tuesday.", "Humorous", 0.95f)
                )
                currentSuggestions.add(
                    ReplySuggestion("3", "Funny", "I'm going to pretend I didn't read that so my day stays peaceful. 🍿", "Sarcastic", 0.92f)
                )
            }
            "formal" -> {
                currentSuggestions.add(
                    ReplySuggestion("1", "Formal", "Thank you for the update. I have reviewed the details and agree with proceeding on this basis.", "Professional", 0.98f)
                )
                currentSuggestions.add(
                    ReplySuggestion("2", "Formal", "Understood. I will coordinate the necessary adjustments and follow up shortly.", "Polite", 0.95f)
                )
                currentSuggestions.add(
                    ReplySuggestion("3", "Formal", "Could you kindly clarify the proposed timeline so we can align our resources accordingly?", "Structured", 0.93f)
                )
            }
            "short" -> {
                currentSuggestions.add(
                    ReplySuggestion("1", "Short", "Sounds good, let's do it.", "Concise", 0.99f)
                )
                currentSuggestions.add(
                    ReplySuggestion("2", "Short", "Will check and confirm in a few minutes.", "Direct", 0.97f)
                )
                currentSuggestions.add(
                    ReplySuggestion("3", "Short", "Perfect, thank you!", "Brief", 0.96f)
                )
            }
            else -> {
                // Logical / Default
                currentSuggestions.add(
                    ReplySuggestion("1", "Logical", "He spearheaded nonviolent civil disobedience movements including the Salt March and Quit India, uniting millions to dismantle colonial governance.", "Historical & Analytical", 0.98f)
                )
                currentSuggestions.add(
                    ReplySuggestion("2", "Logical", "His core strategy of Satyagraha demonstrated how nonviolent collective resistance could politically pressure imperial authorities.", "Objective", 0.95f)
                )
                currentSuggestions.add(
                    ReplySuggestion("3", "Logical", "Beyond political mobilization, his focus on self-reliance (Swadeshi) challenged the economic foundation of colonial rule.", "Comprehensive", 0.93f)
                )
            }
        }
    }

    private fun loadDefaultSuggestions() {
        fallbackSuggestions(currentDetectedText)
        renderSuggestionCards()
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        loadingProgressBar?.visibility = if (loading) View.VISIBLE else View.GONE
        loadingStatusText?.visibility = if (loading) View.VISIBLE else View.GONE
        suggestionsContainer?.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun updateContextViews() {
        appNameBadgeView?.text = currentAppName
        contextSnippetView?.text = currentDetectedText.ifBlank { "Awaiting message context from active chat..." }
    }

    private fun expandPanel() {
        isExpanded = true
        collapsedPillView?.visibility = View.GONE
        expandedPanelView?.visibility = View.VISIBLE
        updateContextViews()
        renderSuggestionCards()
    }

    private fun collapsePanel() {
        isExpanded = false
        expandedPanelView?.visibility = View.GONE
        collapsedPillView?.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPillTouchListener(pill: View, root: FrameLayout) {
        var startClickTime = 0L
        var isDragging = false

        pill.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startClickTime = System.currentTimeMillis()
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams.x = (initialX + deltaX).toInt()
                        layoutParams.y = (initialY + deltaY).toInt()
                        windowManager.updateViewLayout(root, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - startClickTime
                    if (!isDragging && clickDuration < 300) {
                        expandPanel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun createCardBackground(bgColorHex: String, strokeColorHex: String, cornerRadiusPx: Int, strokeWidthDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx.toFloat()
            setColor(Color.parseColor(bgColorHex))
            setStroke(dp(strokeWidthDp), Color.parseColor(strokeColorHex))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ReplyFloat AI", text)
        clipboard.setPrimaryClip(clip)
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        overlayRootView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayRootView = null
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ReplyFloatApplication.CHANNEL_ID)
            .setContentTitle("ReplyFloat AI Active")
            .setContentText("Floating overlay is active over your apps. Tap to open dashboard.")
            .setSmallIcon(R.drawable.ic_quick_tile)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Assistant", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        generateJob?.cancel()
        removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
