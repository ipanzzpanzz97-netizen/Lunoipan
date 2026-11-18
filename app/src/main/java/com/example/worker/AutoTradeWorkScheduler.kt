package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AutoTradeWorkScheduler(context: Context) {

  private val appContext = context.applicationContext

  fun syncSchedule(shouldRunInBackground: Boolean) {
    val workManager = runCatching { WorkManager.getInstance(appContext) }.getOrNull() ?: return
    if (shouldRunInBackground) {
      val constraints =
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()
      val request =
        PeriodicWorkRequestBuilder<AutoTradeWorker>(15, TimeUnit.MINUTES)
          .setConstraints(constraints)
          .build()
      workManager.enqueueUniquePeriodicWork(
        UNIQUE_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    } else {
      workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }
  }

  private companion object {
    const val UNIQUE_WORK_NAME = "quantpulse_auto_trade_periodic"
  }
}
