package com.replyfloat.ai.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferencesRepository private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("replyfloat_prefs", Context.MODE_PRIVATE)

    private val _isMasterEnabled = MutableStateFlow(prefs.getBoolean("master_enabled", true))
    val isMasterEnabled: Flow<Boolean> = _isMasterEnabled.asStateFlow()

    private val _debounceMs = MutableStateFlow(prefs.getLong("debounce_ms", 400L))
    val debounceMs: Flow<Long> = _debounceMs.asStateFlow()

    fun isAppAllowed(packageName: String): Flow<Boolean> {
        val allowed = prefs.getStringSet("whitelisted_apps", null)
        val isAllowed = allowed == null || allowed.contains(packageName)
        return MutableStateFlow(isAllowed)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppPreferencesRepository? = null

        fun getInstance(context: Context): AppPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
