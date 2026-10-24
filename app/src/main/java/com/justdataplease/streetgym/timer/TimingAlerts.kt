package com.justdataplease.streetgym.timer

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import com.justdataplease.streetgym.MainActivity
import com.justdataplease.streetgym.R

/** Toolbox's AlarmManager approach, adapted for one countdown and one clock alarm. */
object TimingAlerts {
    const val OPEN_TIMER = "open_timer"
    private const val CHANNEL = "training_time_alerts"
    private const val TIMER_ID = 710
    private const val ALARM_ID = 711

    fun exact(context: Context) = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun notifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        return true
    }

    fun update(context: Context, change: (TimingState) -> TimingState): TimingState {
        val store = TimingStore(context)
        val next = change(store.load())
        store.save(next)
        schedule(context, next)
        return next
    }

    fun schedule(context: Context, state: TimingState = TimingStore(context).load()) {
        val manager = context.getSystemService(AlarmManager::class.java)
        for (id in listOf(TIMER_ID, ALARM_ID)) manager.cancel(pending(context, id))
        if (state.countdown.running) {
            val at = state.countdown.endsAt.coerceAtLeast(SystemClock.elapsedRealtime())
            try {
                if (exact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(context, TIMER_ID))
                else manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(context, TIMER_ID))
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(context, TIMER_ID))
            }
        }
        if (state.alarmAt > 0) {
            try {
                if (exact(context)) manager.setAlarmClock(AlarmManager.AlarmClockInfo(state.alarmAt, open(context)), pending(context, ALARM_ID))
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.alarmAt, pending(context, ALARM_ID))
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.alarmAt, pending(context, ALARM_ID))
            }
        }
    }

    fun checkDue(context: Context): TimingState {
        val store = TimingStore(context); val before = store.load()
        val timerDue = before.countdown.running && before.countdown.remaining(SystemClock.elapsedRealtime()) == 0L
        val alarmDue = before.alarmAt > 0 && before.alarmAt <= System.currentTimeMillis()
        if (!timerDue && !alarmDue) return before
        val next = before.copy(countdown = if (timerDue) before.countdown.finish() else before.countdown,
            alarmAt = if (alarmDue) 0 else before.alarmAt,
            timerRinging = before.timerRinging || timerDue, alarmRinging = before.alarmRinging || alarmDue)
        store.save(next)
        schedule(context, next)
        if (timerDue) notify(context, TIMER_ID, "Time’s up")
        if (alarmDue) notify(context, ALARM_ID, "Alarm")
        return next
    }

    fun stop(context: Context) {
        update(context) { it.copy(timerRinging = false, alarmRinging = false) }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(TIMER_ID); manager.cancel(ALARM_ID)
    }

    private fun pending(context: Context, id: Int) = PendingIntent.getBroadcast(context, id,
        Intent(context, TimingReceiver::class.java).setAction("${context.packageName}.TIME_DUE"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun open(context: Context) = PendingIntent.getActivity(context, 710,
        Intent(context, MainActivity::class.java).putExtra(OPEN_TIMER, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Suppress("DEPRECATION")
    private fun notify(context: Context, id: Int, title: String) {
        if (!notifications(context)) return
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
            "Timers and alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, attributes); enableVibration(true)
        })
        val stop = PendingIntent.getBroadcast(context, 712,
            Intent(context, TimingReceiver::class.java).setAction("${context.packageName}.STOP_TIME_ALERT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        val notification = builder.setSmallIcon(R.drawable.ic_nav_timer).setColor(0xFF1E7056.toInt())
            .setContentTitle(title).setContentText(context.getString(R.string.app_name))
            .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_HIGH)
            .setSound(sound, attributes).setVibrate(longArrayOf(0, 400, 200, 400))
            .setContentIntent(open(context)).setDeleteIntent(stop).setAutoCancel(false)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        try { manager.notify(id, notification) } catch (_: SecurityException) { /* Permission revoked in flight. */ }
    }
}

class TimingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "${context.packageName}.STOP_TIME_ALERT" -> TimingAlerts.stop(context)
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> TimingAlerts.update(context) {
                if (it.alarmAt > 0) it.copy(alarmAt = nextAlarm(it.alarmHour, it.alarmMinute, System.currentTimeMillis())) else it
            }
            else -> {
                val state = TimingAlerts.checkDue(context)
                TimingAlerts.schedule(context, state)
            }
        }
    }
}
