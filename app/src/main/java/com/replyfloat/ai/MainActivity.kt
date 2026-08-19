package com.replyfloat.ai

import android.Manifest
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
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.replyfloat.ai.service.FloatingOverlayService
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val TAG = "ReplyFloatAI"
        private const val LOCAL_HOST = "app.local"
        private const val START_URL = "https://$LOCAL_HOST/index.html"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.i(TAG, "Notification permission result: $isGranted")
        notifyWebOfPermissions()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set dark status bar & navigation bar to match ReplyFloat theme
        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.parseColor("#090D16")
            navigationBarColor = Color.parseColor("#090D16")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }

        prefs = getSharedPreferences("replyfloat_prefs", Context.MODE_PRIVATE)

        // Request notification permission automatically on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#090D16"))
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                mediaPlaybackRequiresUserGesture = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || (url.startsWith("https://") && !url.contains(LOCAL_HOST) && !url.contains("appassets.androidplatform.net"))) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    }
                    return false
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    val uriStr = uri.toString()

                    // Intercept https://app.local/* and file:///android_asset/web/*
                    val assetSubPath: String? = when {
                        uri.scheme.equals("https", ignoreCase = true) && uri.host.equals(LOCAL_HOST, ignoreCase = true) -> {
                            val path = uri.path?.removePrefix("/") ?: ""
                            if (path.isEmpty() || path == "index.html") "web/index.html" else "web/$path"
                        }
                        uriStr.startsWith("file:///android_asset/web/") -> {
                            uriStr.removePrefix("file:///android_asset/")
                        }
                        else -> null
                    }

                    if (assetSubPath != null) {
                        try {
                            val cleanPath = assetSubPath.substringBefore("?").substringBefore("#")
                            val inputStream: InputStream = assets.open(cleanPath)
                            val mimeType = getMimeType(cleanPath)
                            val response = WebResourceResponse(mimeType, "UTF-8", inputStream)
                            val headers = HashMap<String, String>().apply {
                                put("Access-Control-Allow-Origin", "*")
                                put("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                                put("Access-Control-Allow-Headers", "*")
                                put("Cache-Control", "no-cache")
                            }
                            response.responseHeaders = headers
                            return response
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to stream asset: $assetSubPath", e)
                        }
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Log.e(TAG, "WebView error: [${error?.errorCode}] ${error?.description} at ${request?.url}")
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.i(TAG, "Page loaded successfully: $url")
                    notifyWebOfPermissions()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d(TAG, "JS [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
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

        // Load self-contained local web assets
        webView.loadUrl(START_URL)
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html", ignoreCase = true) -> "text/html"
            path.endsWith(".js", ignoreCase = true) || path.endsWith(".mjs", ignoreCase = true) -> "application/javascript"
            path.endsWith(".css", ignoreCase = true) -> "text/css"
            path.endsWith(".json", ignoreCase = true) -> "application/json"
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
            path.endsWith(".ico", ignoreCase = true) -> "image/x-icon"
            path.endsWith(".woff2", ignoreCase = true) -> "font/woff2"
            path.endsWith(".woff", ignoreCase = true) -> "font/woff"
            path.endsWith(".ttf", ignoreCase = true) -> "font/ttf"
            else -> "application/octet-stream"
        }
    }

    private fun notifyWebOfPermissions() {
        runOnUiThread {
            webView.evaluateJavascript("if (window.onAndroidResume) { window.onAndroidResume(); }", null)
        }
    }

    override fun onResume() {
        super.onResume()
        notifyWebOfPermissions()
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
                Toast.makeText(context, "Grant 'Display over other apps' to enable floating overlay", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            } else {
                Toast.makeText(context, "Overlay permission is already granted!", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(
                context,
                "Enable 'ReplyFloat AI Context Detection' in Downloaded Services / Installed Apps",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        @JavascriptInterface
        fun isNotificationPermissionGranted(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        @JavascriptInterface
        fun requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    runOnUiThread {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    Toast.makeText(context, "Notification permission is already granted!", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Notifications active", Toast.LENGTH_SHORT).show()
            }
        }

        @JavascriptInterface
        fun startFloatingService(settingsJson: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Please allow 'Display over other apps' first to show the floating bar", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
                return
            }
            FloatingOverlayService.startService(context)
            showToast("ReplyFloat AI floating overlay is now active over all apps!")
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
