package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class DcaReminderReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val pairCode = intent.getStringExtra(EXTRA_PAIR) ?: "XBTMYR"
    val amountText = intent.getStringExtra(EXTRA_AMOUNT) ?: "RM 500"
    showDcaNotification(
      context = context,
      title = "DCA Execution Window: $pairCode",
      message = "Disciplined recurring investment reminder ($amountText). Check RSI & 1–2% risk limits before ordering on Luno.",
    )
  }

  companion object {
    const val CHANNEL_ID = "quantpulse_dca_reminders"
    const val EXTRA_PAIR = "extra_pair"
    const val EXTRA_AMOUNT = "extra_amount"
    const val NOTIFICATION_ID_DCA = 7001
    const val NOTIFICATION_ID_AUTOPILOT = 7002

    fun ensureChannel(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager =
          context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
          val channel =
            NotificationChannel(
              CHANNEL_ID,
              context.getString(R.string.dca_channel_name),
              NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
              description = context.getString(R.string.dca_channel_desc)
            }
          manager.createNotificationChannel(channel)
        }
      }
    }

    fun showDcaNotification(
      context: Context,
      title: String,
      message: String,
      notificationId: Int = NOTIFICATION_ID_DCA,
    ) {
      ensureChannel(context)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted =
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
      }

      val openAppIntent =
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
      val pendingIntent =
        PendingIntent.getActivity(
          context,
          0,
          openAppIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_launcher_foreground)
          .setContentTitle(title)
          .setContentText(message)
          .setStyle(NotificationCompat.BigTextStyle().bigText(message))
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .setAutoCancel(true)
          .setContentIntent(pendingIntent)
          .build()

      NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
  }
}
