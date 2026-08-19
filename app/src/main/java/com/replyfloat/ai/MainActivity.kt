package com.replyfloat.ai

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.replyfloat.ai.service.FloatingOverlayService
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set dark status bar & navigation bar to match ReplyFloat theme
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.parseColor("#090D16")
            navigationBarColor = Color.parseColor("#090D16")
            decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }

        prefs = getSharedPreferences("replyfloat_prefs", Context.MODE_PRIVATE)

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#090D16"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    }
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            addJavascriptInterface(AndroidBridge(this@MainActivity), "Android")
        }

        setContentView(webView)

        // Handle hardware back navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Load offline embedded ReplyFloat AI bundle
        webView.loadUrl("file:///android_asset/web/index.html")
    }

    override fun onResume() {
        super.onResume()
        // Notify web UI of updated permission states
        webView.evaluateJavascript("if (window.onAndroidResume) { window.onAndroidResume(); }", null)
    }

    inner class AndroidBridge(private val context: Context) {

        @JavascriptInterface
        fun isAndroid(): Boolean = true

        @JavascriptInterface
        fun isOverlayPermissionGranted(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        @JavascriptInterface
        fun requestOverlayPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }

        @JavascriptInterface
        fun isAccessibilityPermissionGranted(): Boolean {
            val expectedService = "${context.packageName}/com.replyfloat.ai.service.ReplyFloatAccessibilityService"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(expectedService)
        }

        @JavascriptInterface
        fun requestAccessibilityPermission() {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        @JavascriptInterface
        fun startFloatingService(settingsJson: String) {
            FloatingOverlayService.startService(context)
            showToast("ReplyFloat AI floating overlay started")
        }

        @JavascriptInterface
        fun stopFloatingService() {
            FloatingOverlayService.stopService(context)
            showToast("ReplyFloat AI floating overlay stopped")
        }

        @JavascriptInterface
        fun isFloatingServiceRunning(): Boolean {
            return FloatingOverlayService.isRunning
        }

        @JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ReplyFloat AI", text)
            clipboard.setPrimaryClip(clip)
            vibrate(40)
            showToast("Copied to clipboard!")
        }

        @JavascriptInterface
        fun vibrate(durationMs: Long) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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

        @JavascriptInterface
        fun showToast(message: String) {
            runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun savePreference(key: String, value: String) {
            prefs.edit().putString(key, value).apply()
        }

        @JavascriptInterface
        fun getPreference(key: String, defaultValue: String): String {
            return prefs.getString(key, defaultValue) ?: defaultValue
        }

        @JavascriptInterface
        fun getInstalledAppsJson(): String {
            return try {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val jsonArray = JSONArray()
                for (app in apps) {
                    // Filter non-system apps or common messaging apps
                    val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val label = pm.getApplicationLabel(app).toString()
                    val pkg = app.packageName
                    if (!isSystem || pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messaging") || pkg.contains("discord") || pkg.contains("reddit")) {
                        val obj = JSONObject().apply {
                            put("packageName", pkg)
                            put("name", label)
                        }
                        jsonArray.put(obj)
                    }
                }
                jsonArray.toString()
            } catch (e: Exception) {
                "[]"
            }
        }
    }
}
