package com.SmsRasti

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class SmsService : Service() {

    companion object {
        const val ACTION_START = "com.SmsRasti.ACTION_START"
        const val ACTION_STOP = "com.SmsRasti.ACTION_STOP"
        const val ACTION_RESTART = "com.SmsRasti.ACTION_RESTART"
        const val ACTION_SMS_SENT = "com.SmsRasti.ACTION_SMS_SENT"
        const val EXTRA_SMS_ID = "sms_id"
        const val EXTRA_ACK_URL = "ack_url"
        const val CHANNEL_ID = "rastisms_gateway_service"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG = "RastiSMS:GatewayWakeLock"
    }

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopGatewayByUser()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("در حال اجرا - منتظر اتصال"))
        PrefsHelper(this).setServiceRunning(true)
        acquireWakeLock()
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return

        loopJob = scope.launch {
            while (isActive) {
                val prefs = PrefsHelper(this@SmsService)
                try {
                    if (prefs.enableSending()) {
                        updateNotification("در حال بررسی سرور...")
                        pollServer(prefs)
                    } else {
                        updateNotification("ارسال خاموش است - سرویس زنده است")
                    }
                } catch (e: Exception) {
                    Log.e("RASTISMS", "Poll error: ${e.message}")
                    updateNotification("خطا در اتصال: ${e.message ?: "unknown"}")
                }

                val intervalMs = (prefs.pollIntervalSeconds() * 1000L).coerceAtLeast(5000L)
                delay(intervalMs)
            }
        }
    }

    private fun pollServer(prefs: PrefsHelper) {
        val url = buildPollUrl(prefs)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "RastiSMS-Gateway/1.0")
            .build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            Log.d("RASTISMS", "Poll HTTP ${res.code}: $body")

            if (!res.isSuccessful) {
                updateNotification("HTTP ${res.code} از سرور")
                return
            }

            val json = JsonParser.parseString(body).asJsonObject
            val status = json.getStringSafe("status")

            if (status == "ok") {
                val smsId = json.getStringSafe("id").ifEmpty { json.getStringSafe("sms_id") }
                val phone = json.getStringSafe("phone")
                val message = json.getStringSafe("message")

                if (phone.isNotEmpty() && message.isNotEmpty()) {
                    updateNotification("ارسال پیامک به $phone")
                    sendSms(phone, message, smsId, buildAckBaseUrl(prefs))
                } else {
                    updateNotification("پاسخ ok ناقص بود")
                }
            } else {
                updateNotification("سرویس فعال - پیام جدیدی نیست")
            }
        }
    }

    private fun sendSms(phone: String, message: String, smsId: String, ackBaseUrl: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            val sentIntents = ArrayList<PendingIntent>()

            for (i in parts.indices) {
                val sentIntent = Intent(this, SmsSentReceiver::class.java).apply {
                    action = ACTION_SMS_SENT
                    putExtra(EXTRA_SMS_ID, smsId)
                    putExtra(EXTRA_ACK_URL, ackBaseUrl)
                }
                sentIntents.add(
                    PendingIntent.getBroadcast(
                        this,
                        (System.currentTimeMillis() + i).toInt(),
                        sentIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }

            smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            Log.d("RASTISMS", "SMS handed to Android telephony: $phone")
            updateNotification("پیامک به سیستم ارسال گوشی تحویل شد")
        } catch (e: Exception) {
            if (smsId.isNotEmpty()) ack(ackBaseUrl, smsId, "failed", e.message ?: "send exception")
            Log.e("RASTISMS", "SMS send failed: ${e.message}")
            updateNotification("خطا در ارسال SMS: ${e.message ?: "unknown"}")
        }
    }

    private fun buildPollUrl(prefs: PrefsHelper): String {
        val sep = if (prefs.pollUrl().contains("?")) "&" else "?"
        return prefs.pollUrl() + sep +
            "user=${enc(prefs.user())}" +
            "&pass=${enc(prefs.pass())}" +
            "&token=${enc(prefs.deviceToken())}" +
            "&type=receive"
    }

    private fun buildAckBaseUrl(prefs: PrefsHelper): String {
        val base = prefs.serverUrl().trimEnd('/')
        return "$base/api/sms/gateway/ack/?pass=${enc(prefs.deviceToken())}"
    }

    private fun ack(baseUrl: String, smsId: String, status: String, error: String = "") {
        try {
            val url = baseUrl +
                "&id=${enc(smsId)}" +
                "&status=${enc(status)}" +
                if (error.isNotEmpty()) "&error=${enc(error)}" else ""
            client.newCall(Request.Builder().url(url).build()).execute().close()
        } catch (e: Exception) {
            Log.e("RASTISMS", "Ack failed: ${e.message}")
        }
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "RastiSMS Gateway", NotificationManager.IMPORTANCE_LOW)
            channel.description = "RastiSMS foreground gateway service"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            10,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, SmsService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this,
            11,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("RastiSMS Gateway روشن است")
            .setContentText(text)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                setReferenceCounted(false)
                acquire()
            }
            Log.d("RASTISMS", "WakeLock acquired")
        } catch (e: Exception) {
            Log.e("RASTISMS", "WakeLock error: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun scheduleRestart() {
        try {
            val prefs = PrefsHelper(this)
            if (!prefs.serviceRunning()) return

            val restartIntent = Intent(this, SmsService::class.java).apply { action = ACTION_RESTART }
            val pending = PendingIntent.getService(
                this,
                99,
                restartIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerAt = System.currentTimeMillis() + 10_000L
            if (Build.VERSION.SDK_INT >= 23) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (e: Exception) {
            Log.e("RASTISMS", "Restart schedule error: ${e.message}")
        }
    }

    private fun stopGatewayByUser() {
        PrefsHelper(this).setServiceRunning(false)
        loopJob?.cancel()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        loopJob?.cancel()
        serviceJob.cancel()
        val shouldRestart = PrefsHelper(this).serviceRunning()
        releaseWakeLock()
        if (shouldRestart) scheduleRestart()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun JsonObject.getStringSafe(name: String): String {
        return try {
            if (has(name) && !get(name).isJsonNull) get(name).asString else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
