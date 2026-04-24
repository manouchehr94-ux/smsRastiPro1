package com.SmsRasti

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val smsId = intent.getStringExtra(SmsService.EXTRA_SMS_ID) ?: ""
        val ackBaseUrl = intent.getStringExtra(SmsService.EXTRA_ACK_URL) ?: ""
        if (smsId.isEmpty() || ackBaseUrl.isEmpty()) return

        val status: String
        val error: String
        when (resultCode) {
            Activity.RESULT_OK -> {
                status = "sent"
                error = ""
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                status = "failed"
                error = "No service"
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                status = "failed"
                error = "Null PDU"
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                status = "failed"
                error = "Radio off"
            }
            else -> {
                status = "failed"
                error = "SMS send failed code=$resultCode"
            }
        }

        Thread {
            try {
                val url = ackBaseUrl +
                    "&id=${URLEncoder.encode(smsId, "UTF-8")}" +
                    "&status=${URLEncoder.encode(status, "UTF-8")}" +
                    if (error.isNotEmpty()) "&error=${URLEncoder.encode(error, "UTF-8")}" else ""
                OkHttpClient().newCall(Request.Builder().url(url).build()).execute().close()
                Log.d("RASTISMS", "Ack sent: $status for $smsId")
            } catch (e: Exception) {
                Log.e("RASTISMS", "Ack error: ${e.message}")
            }
        }.start()
    }
}
