package com.SmsRasti

import android.content.Context

class PrefsHelper(context: Context) {
    private val prefs = context.getSharedPreferences("rastisms_gateway_prefs", Context.MODE_PRIVATE)

    fun save(
        serverUrl: String,
        apiPath: String,
        user: String,
        pass: String,
        deviceToken: String,
        pollIntervalSeconds: Int,
        enableSending: Boolean,
        enableReceiving: Boolean,
        autoStart: Boolean
    ) {
        prefs.edit().apply {
            putString("serverUrl", serverUrl.trim().trimEnd('/'))
            putString("apiPath", normalizeApi(apiPath))
            putString("user", user.trim())
            putString("pass", pass)
            putString("deviceToken", deviceToken.trim())
            putInt("pollIntervalSeconds", pollIntervalSeconds.coerceAtLeast(5))
            putBoolean("enableSending", enableSending)
            putBoolean("enableReceiving", enableReceiving)
            putBoolean("autoStart", autoStart)
            apply()
        }
    }

    fun setServiceRunning(value: Boolean) {
        prefs.edit().putBoolean("serviceRunning", value).apply()
    }

    fun serverUrl() = prefs.getString("serverUrl", "") ?: ""
    fun apiPath() = prefs.getString("apiPath", "/api/sms/gateway/poll/") ?: "/api/sms/gateway/poll/"
    fun user() = prefs.getString("user", "") ?: ""
    fun pass() = prefs.getString("pass", "") ?: ""
    fun deviceToken() = prefs.getString("deviceToken", "smsrasti_test_123") ?: "smsrasti_test_123"
    fun pollIntervalSeconds() = prefs.getInt("pollIntervalSeconds", 7).coerceAtLeast(5)
    fun enableSending() = prefs.getBoolean("enableSending", true)
    fun enableReceiving() = prefs.getBoolean("enableReceiving", true)
    fun autoStart() = prefs.getBoolean("autoStart", false)
    fun serviceRunning() = prefs.getBoolean("serviceRunning", false)

    fun pollUrl(): String {
        val base = serverUrl().trimEnd('/')
        val path = normalizeApi(apiPath())
        return base + path
    }

    private fun normalizeApi(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "/"
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }
}
