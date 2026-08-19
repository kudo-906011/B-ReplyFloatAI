package com.replyfloat.ai.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.replyfloat.ai.MainActivity
import com.replyfloat.ai.R
import com.replyfloat.ai.ReplyFloatApplication

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayRootView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isExpanded = false

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
                if (overlayRootView == null) {
                    showNativeOverlay(detectedText)
                }
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showNativeOverlay(initialText: String) {
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
            x = 80
            y = 350
        }

        val context = this
        val rootLayout = FrameLayout(context)

        // Collapsed Pill View (State 1)
        val collapsedPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#090D16"))
                setStroke(dp(1), Color.parseColor("#EF4444"))
            }
            elevation = dp(8).toFloat()

            // Icon / Bot Dot
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    marginEnd = dp(8)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#EF4444"))
                }
            }
            addView(dot)

            val titleLabel = TextView(context).apply {
                this.text = "ReplyFloat AI"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(titleLabel)

            val badgeView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(8)
                }
                this.text = "3"
                setTextColor(Color.parseColor("#FCA5A5"))
                textSize = 11f
                setPadding(dp(6), dp(2), dp(6), dp(2))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.parseColor("#7F1D1D"))
                    setStroke(dp(1), Color.parseColor("#991B1B"))
                }
            }
            addView(badgeView)
        }

        // Expanded Panel View (State 2)
        val expandedPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = FrameLayout.LayoutParams(dp(320), FrameLayout.LayoutParams.WRAP_CONTENT)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(Color.parseColor("#090D16"))
                setStroke(dp(1), Color.parseColor("#991B1B"))
            }
            elevation = dp(12).toFloat()

            // Header
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }

                val title = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    this.text = "ReplyFloat Suggestions"
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                addView(title)

                val minimizeBtn = TextView(context).apply {
                    this.text = "Collapse"
                    setTextColor(Color.parseColor("#94A3B8"))
                    textSize = 12f
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        isExpanded = false
                        visibility = View.GONE
                        collapsedPill.visibility = View.VISIBLE
                    }
                }
                addView(minimizeBtn)
            }
            addView(header)

            // Tone Badges
            val tonesRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }

                val tones = listOf("Logical", "Humorous", "Direct", "Debate")
                for ((index, toneName) in tones.withIndex()) {
                    val pill = TextView(context).apply {
                        this.text = toneName
                        textSize = 11f
                        setTextColor(if (index == 0) Color.WHITE else Color.parseColor("#94A3B8"))
                        setPadding(dp(8), dp(4), dp(8), dp(4))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = dp(12).toFloat()
                            setColor(if (index == 0) Color.parseColor("#DC2626") else Color.parseColor("#161B22"))
                        }
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (index > 0) marginStart = dp(6)
                    }
                    addView(pill, lp)
                }
            }
            addView(tonesRow)

            // Replies
            val replies = listOf(
                "Sounds great, let's lock in tomorrow at 3 PM.",
                "Can you send over the updated files first?",
                "I'll review this shortly and get back to you with notes."
            )

            for (replyText in replies) {
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(8)
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#0D1117"))
                        setStroke(dp(1), Color.parseColor("#21262D"))
                    }

                    val rText = TextView(context).apply {
                        this.text = replyText
                        setTextColor(Color.parseColor("#E2E8F0"))
                        textSize = 13f
                    }
                    addView(rText)

                    val copyHint = TextView(context).apply {
                        this.text = "Tap to Copy"
                        setTextColor(Color.parseColor("#EF4444"))
                        textSize = 11f
                        setPadding(0, dp(4), 0, 0)
                    }
                    addView(copyHint)

                    setOnClickListener {
                        copyToClipboard(replyText)
                        vibrate(30)
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                }
                addView(card)
            }
        }

        rootLayout.addView(collapsedPill)
        rootLayout.addView(expandedPanel)

        // Drag & Touch Handling on the Collapsed Pill
        var startClickTime = 0L
        collapsedPill.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startClickTime = System.currentTimeMillis()
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(rootLayout, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val clickDuration = System.currentTimeMillis() - startClickTime
                    if (clickDuration < 250) {
                        // Click -> Expand Panel
                        isExpanded = true
                        collapsedPill.visibility = View.GONE
                        expandedPanel.visibility = View.VISIBLE
                    }
                    true
                }
                else -> false
            }
        }

        overlayRootView = rootLayout
        windowManager.addView(rootLayout, layoutParams)
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
            .setContentText("Floating overlay is running. Tap to open dashboard.")
            .setSmallIcon(R.drawable.ic_quick_tile)
            .setContentIntent(pendingIntent)
            .addAction(0, "Dismiss", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
