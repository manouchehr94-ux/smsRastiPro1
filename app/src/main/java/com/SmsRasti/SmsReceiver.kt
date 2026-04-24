package com.SmsRasti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PrefsHelper(context)
        if (!prefs.enableReceiving()) return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return

                val phone = messages[0].originatingAddress ?: ""
                val body = messages.joinToString("") { it.messageBody ?: "" }
                val url = buildInboxUrl(prefs, phone, body)

                Thread {
                    try {
                        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().close()
                        Log.d("RASTISMS", "Incoming SMS forwarded")
                    } catch (e: Exception) {
                        Log.e("RASTISMS", "Incoming forward error: ${e.message}")
                    }
                }.start()
            } catch (e: Exception) {
                Log.e("RASTISMS", "Receiver error: ${e.message}")
            }
        }
    }

    private fun buildInboxUrl(prefs: PrefsHelper, phone: String, message: String): String {
        val sep = if (prefs.pollUrl().contains("?")) "&" else "?"
        return prefs.pollUrl() + sep +
            "user=${enc(prefs.user())}" +
            "&pass=${enc(prefs.pass())}" +
            "&token=${enc(prefs.deviceToken())}" +
            "&type=send" +
            "&phone=${enc(phone)}" +
            "&message=${enc(message)}"
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
