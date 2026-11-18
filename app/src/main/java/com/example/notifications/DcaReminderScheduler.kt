package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.model.DcaFrequency
import com.example.data.model.TradingPair
import java.util.concurrent.TimeUnit

class DcaReminderScheduler(context: Context) {

  private val appContext = context.applicationContext
  private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

  fun schedulePeriodicReminder(
    pair: TradingPair,
    amountFormatted: String,
    frequency: DcaFrequency,
  ) {
    DcaReminderReceiver.ensureChannel(appContext)
    val intervalMs = TimeUnit.DAYS.toMillis(frequency.intervalDays)
    val triggerAtMs = System.currentTimeMillis() + intervalMs
    val pendingIntent = buildPendingIntent(pair.pairCode, amountFormatted)

    alarmManager.cancel(pendingIntent)
    alarmManager.setInexactRepeating(
      AlarmManager.RTC_WAKEUP,
      triggerAtMs,
      intervalMs,
      pendingIntent,
    )
  }

  fun cancelReminder() {
    val pendingIntent = buildPendingIntent("XBTMYR", "")
    alarmManager.cancel(pendingIntent)
  }

  fun sendImmediateTestReminder(pair: TradingPair, amountFormatted: String) {
    DcaReminderReceiver.showDcaNotification(
      context = appContext,
      title = "DCA Alert Active (${pair.pairCode})",
      message = "Scheduled $amountFormatted DCA reminder verified. Stay disciplined and respect your 1%–2% risk rule.",
    )
  }

  fun sendAutoPilotSignalNotification(title: String, message: String) {
    DcaReminderReceiver.showDcaNotification(
      context = appContext,
      title = title,
      message = message,
      notificationId = DcaReminderReceiver.NOTIFICATION_ID_AUTOPILOT,
    )
  }

  private fun buildPendingIntent(pairCode: String, amountFormatted: String): PendingIntent {
    val intent =
      Intent(appContext, DcaReminderReceiver::class.java).apply {
        putExtra(DcaReminderReceiver.EXTRA_PAIR, pairCode)
        putExtra(DcaReminderReceiver.EXTRA_AMOUNT, amountFormatted)
      }
    return PendingIntent.getBroadcast(
      appContext,
      REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }

  private companion object {
    const val REQUEST_CODE = 7101
  }
}
