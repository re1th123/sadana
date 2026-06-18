package com.example

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.database.AppDatabase
import com.example.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*

class WaterReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            rescheduleWaterReminders(context)
            return
        }

        // Show the notification
        showNotification(context)

        // Schedule the next alarm
        rescheduleWaterReminders(context)
    }

    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sadana_water_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Hydration Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Keeps you energized and hydrated throughout the day"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val messages = listOf(
            "Time to take a sip of water! 💧",
            "Keep your body hydrated and joints fluid today! ✨",
            "Be kind to your body: treat yourself to a refreshing glass of water! 🌊",
            "A hydrated mind is a focused mind. Ready for a quick sip? 🌸",
            "Hydration check! Drink a glass of water to recharge. 🔋"
        )
        val selectedMessage = messages.random()

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Sadana Hydration Reminder")
            .setContentText(selectedMessage)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(2002, notification)
    }

    companion object {
        fun rescheduleWaterReminders(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val profile = db.appDao.getUserProfile().firstOrNull() ?: return@launch
                
                cancelWaterAlarm(context)
                
                if (!profile.waterReminderEnabled) return@launch

                val alarmTimeMillis = calculateNextAlarmTime(profile) ?: return@launch

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
                val intent = Intent(context, WaterReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            alarmTimeMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            alarmTimeMillis,
                            pendingIntent
                        )
                    }
                } catch (e: SecurityException) {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        alarmTimeMillis,
                        pendingIntent
                    )
                }
            }
        }

        fun cancelWaterAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, WaterReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }

        private fun calculateNextAlarmTime(profile: UserProfile): Long? {
            val now = Calendar.getInstance()
            
            if (profile.waterReminderType == "INTERVAL") {
                val intervalHours = profile.waterReminderIntervalHours.coerceIn(1, 3)
                val startHour = 7
                val endHour = 22

                val currentHour = now.get(Calendar.HOUR_OF_DAY)
                
                val targetCal = now.clone() as Calendar
                targetCal.set(Calendar.SECOND, 0)
                targetCal.set(Calendar.MILLISECOND, 0)

                if (currentHour < startHour) {
                    targetCal.set(Calendar.HOUR_OF_DAY, startHour)
                    targetCal.set(Calendar.MINUTE, 0)
                } else if (currentHour >= endHour) {
                    targetCal.add(Calendar.DAY_OF_YEAR, 1)
                    targetCal.set(Calendar.HOUR_OF_DAY, startHour)
                    targetCal.set(Calendar.MINUTE, 0)
                } else {
                    targetCal.add(Calendar.HOUR, intervalHours)
                    val targetHour = targetCal.get(Calendar.HOUR_OF_DAY)
                    if (targetHour >= endHour) {
                        targetCal.add(Calendar.DAY_OF_YEAR, 1)
                        targetCal.set(Calendar.HOUR_OF_DAY, startHour)
                        targetCal.set(Calendar.MINUTE, 0)
                    }
                }
                return targetCal.timeInMillis
            } else {
                val rawTimes = profile.waterReminderCustomTimes.split(",")
                val times = rawTimes.mapNotNull { raw ->
                    val parts = raw.trim().split(":")
                    if (parts.size == 2) {
                        val h = parts[0].toIntOrNull() ?: return@mapNotNull null
                        val m = parts[1].toIntOrNull() ?: return@mapNotNull null
                        h to m
                    } else null
                }.sortedWith(compareBy({ it.first }, { it.second }))

                if (times.isEmpty()) return null

                var futureTimeMillis: Long? = null
                for ((h, m) in times) {
                    val candidate = now.clone() as Calendar
                    candidate.set(Calendar.HOUR_OF_DAY, h)
                    candidate.set(Calendar.MINUTE, m)
                    candidate.set(Calendar.SECOND, 0)
                    candidate.set(Calendar.MILLISECOND, 0)

                    if (candidate.timeInMillis > System.currentTimeMillis()) {
                        futureTimeMillis = candidate.timeInMillis
                        break
                    }
                }

                if (futureTimeMillis == null) {
                    val (h, m) = times.first()
                    val candidate = now.clone() as Calendar
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                    candidate.set(Calendar.HOUR_OF_DAY, h)
                    candidate.set(Calendar.MINUTE, m)
                    candidate.set(Calendar.SECOND, 0)
                    candidate.set(Calendar.MILLISECOND, 0)
                    futureTimeMillis = candidate.timeInMillis
                }
                return futureTimeMillis
            }
        }
    }
}
