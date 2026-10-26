package com.cleantv.wifilauncher

import android.content.Context
import android.content.SharedPreferences

class LauncherPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("cleantv_wifi_prefs", Context.MODE_PRIVATE)

    fun getSavedStateJson(): String {
        return prefs.getString("launcher_state_json", "{}") ?: "{}"
    }

    fun saveStateJson(json: String) {
        prefs.edit().putString("launcher_state_json", json).apply()
    }
}
