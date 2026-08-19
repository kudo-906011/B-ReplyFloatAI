package com.replyfloat.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.replyfloat.ai.data.AppPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ReplyFloatAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var debounceJob: Job? = null
    private var lastCapturedText = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 300
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        
        serviceScope.launch {
            val repository = AppPreferencesRepository.getInstance(applicationContext)
            val isMasterEnabled = repository.isMasterEnabled.first()
            if (!isMasterEnabled) return@launch

            val isAllowed = repository.isAppAllowed(packageName).first()
            if (!isAllowed) return@launch

            val debounceMs = repository.debounceMs.first()

            debounceJob?.cancel()
            debounceJob = launch {
                delay(debounceMs)
                val extractedText = extractVisibleText(rootInActiveWindow)
                if (extractedText.isNotBlank() && extractedText != lastCapturedText) {
                    lastCapturedText = extractedText
                    withContext(Dispatchers.Main) {
                        FloatingOverlayService.startService(
                            applicationContext,
                            text = extractedText,
                            pkg = packageName
                        )
                    }
                }
            }
        }
    }

    private fun extractVisibleText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val builder = StringBuilder()
        fun traverse(n: AccessibilityNodeInfo) {
            val text = n.text?.toString()?.trim()
            if (!text.isNullOrBlank() && text.length > 2) {
                builder.append(text).append("\n")
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                traverse(child)
                child.recycle()
            }
        }
        try {
            traverse(node)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return builder.toString().trim()
    }

    override fun onInterrupt() {
        debounceJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
