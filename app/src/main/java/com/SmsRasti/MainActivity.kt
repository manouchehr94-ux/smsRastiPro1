package com.SmsRasti

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsHelper
    private lateinit var txtStatus: TextView
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsHelper(this)
        txtStatus = findViewById(R.id.txtStatus)

        requestPermissions()
        bindSettings()
        refreshServiceButtons()
    }

    private fun bindSettings() {
        val etServerUrl = findViewById<EditText>(R.id.etServerUrl)
        val etApiPath = findViewById<EditText>(R.id.etApiPath)
        val etUser = findViewById<EditText>(R.id.etUser)
        val etPass = findViewById<EditText>(R.id.etPass)
        val etDeviceToken = findViewById<EditText>(R.id.etDeviceToken)
        val etPollInterval = findViewById<EditText>(R.id.etPollInterval)
        val swEnableSending = findViewById<Switch>(R.id.swEnableSending)
        val swEnableReceiving = findViewById<Switch>(R.id.swEnableReceiving)
        val swAutoStart = findViewById<Switch>(R.id.swAutoStart)

        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)

        etServerUrl.setText(prefs.serverUrl())
        etApiPath.setText(prefs.apiPath())
        etUser.setText(prefs.user())
        etPass.setText(prefs.pass())
        etDeviceToken.setText(prefs.deviceToken())
        etPollInterval.setText(prefs.pollIntervalSeconds().toString())
        swEnableSending.isChecked = prefs.enableSending()
        swEnableReceiving.isChecked = prefs.enableReceiving()
        swAutoStart.isChecked = prefs.autoStart()

        setStatus("وضعیت: آماده\nبرای کار پایدار، Battery Optimization را خاموش کنید و سپس Start Service را بزنید.")

        btnSave.setOnClickListener {
            val ok = saveSettings(etServerUrl, etApiPath, etUser, etPass, etDeviceToken, etPollInterval, swEnableSending, swEnableReceiving, swAutoStart)
            if (ok) {
                btnSave.text = "✅ Saved"
                setStatus("✅ تنظیمات ذخیره شد\n${prefs.pollUrl()}")
                toast("Settings saved")
            }
        }

        btnTest.setOnClickListener {
            if (saveSettings(etServerUrl, etApiPath, etUser, etPass, etDeviceToken, etPollInterval, swEnableSending, swEnableReceiving, swAutoStart)) {
                btnTest.text = "⏳ Testing..."
                setStatus("⏳ در حال تست اتصال به سرور...")
                testConnection()
            }
        }

        btnStart.setOnClickListener {
            if (saveSettings(etServerUrl, etApiPath, etUser, etPass, etDeviceToken, etPollInterval, swEnableSending, swEnableReceiving, swAutoStart)) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, SmsService::class.java).apply { action = SmsService.ACTION_START }
                )
                prefs.setServiceRunning(true)
                btnStart.text = "✅ Service Running"
                btnStop.text = "Stop Service"
                setStatus("✅ سرویس پایدار شروع شد\nهر ${prefs.pollIntervalSeconds()} ثانیه سرور چک می‌شود\nNotification برنامه را نبندید.")
                toast("Service started")
                refreshServiceButtons()
            }
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, SmsService::class.java).apply { action = SmsService.ACTION_STOP }
            ContextCompat.startForegroundService(this, intent)
            prefs.setServiceRunning(false)
            btnStop.text = "⛔ Stopped"
            btnStart.text = "Start Service"
            setStatus("⛔ سرویس متوقف شد\nتا وقتی Start Service را نزنید، Poll انجام نمی‌شود.")
            toast("Service stopped")
            refreshServiceButtons()
        }

        findViewById<Button>(R.id.btnSendTest).setOnClickListener {
            setStatus("ℹ️ برای تست ارسال، یک پیام pending در Django/SMSOutbox یا سرور تست قرار بدهید. اپ در Poll بعدی آن را دریافت می‌کند.")
            toast("Put a pending SMS on server")
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            setStatus("⚙️ صفحه Battery باز شد. برای RastiSMS گزینه Don't optimize / Unrestricted را انتخاب کنید.")
            openBatterySettings()
        }

        findViewById<Button>(R.id.btnNotificationSettings).setOnClickListener {
            setStatus("⚙️ صفحه Notification باز شد. اعلان RastiSMS را روشن نگه دارید.")
            openNotificationSettings()
        }

        findViewById<Button>(R.id.btnAppSettings).setOnClickListener {
            setStatus("⚙️ صفحه تنظیمات برنامه باز شد. SMS و Notification را Allow کنید.")
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnAutoStartSettings).setOnClickListener {
            setStatus("⚙️ اگر گوشی Xiaomi/Huawei/Oppo/Vivo است، Auto Start را برای RastiSMS روشن کنید.")
            openManufacturerSettings()
        }

        findViewById<Button>(R.id.btnHelp).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
    }

    private fun saveSettings(
        etServerUrl: EditText,
        etApiPath: EditText,
        etUser: EditText,
        etPass: EditText,
        etDeviceToken: EditText,
        etPollInterval: EditText,
        swEnableSending: Switch,
        swEnableReceiving: Switch,
        swAutoStart: Switch
    ): Boolean {
        val serverUrl = etServerUrl.text.toString().trim().trimEnd('/')
        val apiPath = etApiPath.text.toString().trim()
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()
        val token = etDeviceToken.text.toString().trim()
        val interval = etPollInterval.text.toString().toIntOrNull() ?: 7

        if (serverUrl.isEmpty() || (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://"))) {
            setStatus("❌ Server URL باید با http:// یا https:// شروع شود")
            toast("Invalid Server URL")
            return false
        }
        if (apiPath.isEmpty()) {
            setStatus("❌ API Path خالی است")
            toast("API Path required")
            return false
        }
        if (token.isEmpty()) {
            setStatus("❌ Device Token خالی است")
            toast("Device Token required")
            return false
        }

        prefs.save(
            serverUrl,
            apiPath,
            user,
            pass,
            token,
            interval.coerceAtLeast(5),
            swEnableSending.isChecked,
            swEnableReceiving.isChecked,
            swAutoStart.isChecked
        )
        return true
    }

    private fun testConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = buildPollUrl()
                val req = Request.Builder().url(url).header("User-Agent", "RastiSMS-Gateway/1.0").build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string() ?: ""
                    runOnUiThread {
                        btnTest.text = "✅ Connection OK"
                        setStatus("✅ اتصال موفق\nHTTP ${res.code}\n$body")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnTest.text = "❌ Test Failed"
                    setStatus("❌ خطا در اتصال: ${e.message}")
                }
            }
        }
    }

    private fun buildPollUrl(): String {
        val sep = if (prefs.pollUrl().contains("?")) "&" else "?"
        return prefs.pollUrl() + sep +
            "user=${enc(prefs.user())}" +
            "&pass=${enc(prefs.pass())}" +
            "&token=${enc(prefs.deviceToken())}" +
            "&type=receive"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 200)
    }

    private fun openBatterySettings() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                toast("Battery optimization already ignored")
                startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            }
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openManufacturerSettings() {
        val intents = listOf(
            Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshServiceButtons() {
        if (!::btnStart.isInitialized || !::btnStop.isInitialized) return
        if (prefs.serviceRunning()) {
            btnStart.text = "✅ Service Running"
            btnStop.text = "Stop Service"
        } else {
            btnStart.text = "Start Service"
            btnStop.text = "⛔ Service Stopped"
        }
    }

    private fun setStatus(text: String) {
        txtStatus.text = text
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
