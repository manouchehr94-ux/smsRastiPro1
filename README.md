# RastiSMS Gateway Pro

نسخه پایدارتر برای کار با Django/SMSOutbox و SMSInbox.

## تغییرات مهم این نسخه

- سرویس به صورت Foreground Service اجرا می‌شود.
- از START_STICKY استفاده می‌شود تا اگر اندروید سرویس را بست، دوباره بالا بیاید.
- WakeLock اضافه شده تا در حالت قفل بودن صفحه، CPU خاموش نشود و Poll ادامه پیدا کند.
- BootReceiver اضافه شده تا بعد از ری‌استارت گوشی، در صورت روشن بودن Auto Start، سرویس دوباره اجرا شود.
- دکمه‌های داخل اپ بعد از کلیک وضعیت واضح نشان می‌دهند: Saved, Testing, Running, Stopped.
- دکمه‌های تنظیمات اضافه شده‌اند:
  - Battery Optimization
  - Notification Settings
  - Auto Start / Manufacturer Settings
  - App Permission Settings
- لوگوی RastiSMS به عنوان آیکن برنامه استفاده شده است، نه تصویر داخل صفحه اصلی.
- Theme تاریک برای مصرف باتری کمتر روی صفحه‌های OLED/AMOLED.

## نکته مهم Android

هیچ اپی نمی‌تواند روی همه گوشی‌ها 100٪ تضمین کند که سازنده گوشی آن را نبندد. برای پایداری واقعی باید روی گوشی:

1. Battery Optimization برای RastiSMS خاموش شود.
2. Notification برنامه روشن بماند.
3. در گوشی‌های Xiaomi/Huawei/Oppo/Vivo گزینه Auto Start برای برنامه فعال شود.
4. برنامه از Recent Apps قفل شود، اگر گوشی چنین امکانی دارد.
5. اینترنت گوشی همیشه فعال باشد.

## تنظیم پیشنهادی

Server URL:
https://yourdomain.com

API Path:
/api/sms/gateway/poll/

Device Token:
smsrasti_test_123

Poll Interval:
7

Enable Sending: ON
Enable Receiving: ON
Auto Start: ON

