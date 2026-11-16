package com.example.managers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.R
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

data class CalendarEventSummary(
    val title: String,
    val startTimeFormatted: String
)

class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val title = inputData.getString("title") ?: "NOVA Reminder"
        val channelId = "nova_reminders_channel"
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "NOVA Reminders", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(ch)
        }
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            val notification = NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("NOVA Reminder")
                .setContentText(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), notification)
        }
        return Result.success()
    }
}

class ReminderManager(private val context: Context) {

    fun setAlarm(hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            String.format("Alarm requested for %02d:%02d (%s).", hour, minute, label)
        }.getOrElse {
            "Unable to set alarm: ${it.localizedMessage}"
        }
    }

    fun setTimer(seconds: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "System timer set for $seconds seconds."
        }.getOrElse {
            "System Clock unavailable; use in-app countdown timer in Productivity."
        }
    }

    fun scheduleWorkReminder(title: String, delayMinutes: Long): String {
        val data = Data.Builder().putString("title", title).build()
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(req)
        return "Reminder scheduled for $delayMinutes min from now: '$title'."
    }

    fun openCalendarInsert(title: String, startMillis: Long = System.currentTimeMillis() + 3_600_000L): String {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 3_600_000L)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened Calendar event composer for '$title'."
        }.getOrElse {
            "No Calendar app available."
        }
    }

    fun readUpcomingEvents(): List<CalendarEventSummary> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val now = System.currentTimeMillis()
        val end = now + 7L * 24 * 60 * 60 * 1000
        val results = mutableListOf<CalendarEventSummary>()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN
        )
        runCatching {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                val titleIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                while (c.moveToNext() && results.size < 15) {
                    val title = c.getString(titleIdx) ?: "Event"
                    val begin = c.getLong(beginIdx)
                    results.add(CalendarEventSummary(title, fmt.format(Date(begin))))
                }
            }
        }
        return results
    }
}
