package com.SmsRasti

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        findViewById<TextView>(R.id.txtHelp).text = """
RastiSMS Gateway

این برنامه گوشی اندرویدی را به SMS Gateway تبدیل می‌کند.

ارسال:
سرور Django پیام آماده را در SMSOutbox قرار می‌دهد.
اپ هر چند ثانیه endpoint poll را صدا می‌زند.
اگر پاسخ status=ok باشد، SMS با سیم‌کارت گوشی ارسال می‌شود.
پس از ارسال، اپ endpoint ack را صدا می‌زند.

دریافت:
هر پیامک ورودی گوشی توسط SmsReceiver گرفته می‌شود و به سرور ارسال می‌شود تا در SMSInbox ذخیره شود.

تنظیمات پیشنهادی:
Server URL: https://yourdomain.com
API Path: /api/sms/gateway/poll/
Device Token: توکن مخصوص همین گوشی
Poll Interval: حداقل 5 ثانیه

برای کار پایدار:
Battery Optimization را برای این برنامه خاموش کنید.
SMS Permission و Notification Permission باید فعال باشد.
Auto Start را برای اجرای بعد از ری‌استارت گوشی روشن کنید.
        """.trimIndent()
    }
}
