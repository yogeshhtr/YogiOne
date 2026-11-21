package com.yogi.one

import android.Manifest
import android.app.AlarmManager
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
import java.util.Calendar
import java.util.Locale

/** Notifications: the end of a focus round, and one daily habit reminder. */
object Reminders {
    const val ACTION_FOCUS = "com.yogi.one.FOCUS_DONE"
    const val ACTION_DAILY = "com.yogi.one.DAILY"
    private const val CH_FOCUS = "focus"
    private const val CH_DAILY = "daily"
    const val ID_FOCUS = 11
    const val ID_DAILY = 12

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_FOCUS, "Focus timer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "When a focus round or break ends"
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_DAILY, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Your daily habit check"
        })
    }

    private fun pending(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun alarm(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Exact when allowed, otherwise "about then". */
    private fun at(ctx: Context, time: Long, pi: PendingIntent) {
        val am = alarm(ctx)
        try {
            when {
                Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms() ->
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                else -> am.setExact(AlarmManager.RTC_WAKEUP, time, pi)
            }
        } catch (_: Exception) {
            am.set(AlarmManager.RTC_WAKEUP, time, pi)
        }
    }

    fun scheduleFocus(ctx: Context, end: Long) {
        at(ctx, end, pending(ctx, ACTION_FOCUS, ID_FOCUS))
    }

    fun cancelFocus(ctx: Context) {
        alarm(ctx).cancel(pending(ctx, ACTION_FOCUS, ID_FOCUS))
        NotificationManagerCompat.from(ctx).cancel(ID_FOCUS)
    }

    /** Minutes after midnight, or -1 = off. */
    fun dailyMinutes(): Int = Store.int("rem_min", -1)

    fun dailyText(): String {
        val m = dailyMinutes()
        if (m < 0) return "Off"
        val h = m / 60
        return String.format(Locale.US, "%d:%02d %s", if (h % 12 == 0) 12 else h % 12, m % 60, if (h < 12) "AM" else "PM")
    }

    fun setDaily(ctx: Context, minutes: Int) {
        Store.setInt("rem_min", minutes)
        scheduleDaily(ctx)
    }

    fun scheduleDaily(ctx: Context) {
        val m = dailyMinutes()
        val pi = pending(ctx, ACTION_DAILY, ID_DAILY)
        if (m < 0) {
            alarm(ctx).cancel(pi)
            return
        }
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, m / 60)
        c.set(Calendar.MINUTE, m % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        at(ctx, c.timeInMillis, pi)
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(ctx: Context, id: Int, focus: Boolean, title: String, text: String) {
        if (!canNotify(ctx)) return
        channels(ctx)
        val open = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, if (focus) CH_FOCUS else CH_DAILY)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(C.ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(if (focus) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        when (intent.action) {
            Reminders.ACTION_FOCUS -> {
                val work = Focus.mode() == "work"
                Reminders.notify(
                    ctx, Reminders.ID_FOCUS, true,
                    if (work) "🍅 Focus round done!" else "☕ Break is over",
                    if (work) "Great work. Time for a ${Focus.rest()}-minute break." else "Ready for the next focus round?"
                )
            }
            Reminders.ACTION_DAILY -> {
                val (d, t) = Habits.today()
                Reminders.notify(
                    ctx, Reminders.ID_DAILY, false, "✅ Daily check-in",
                    when {
                        t == 0 -> "Take a minute to plan your day."
                        d == t -> "All $t habits done today. Amazing! 🎉"
                        else -> "$d of $t habits done today. You've got this! 💪"
                    }
                )
                Reminders.scheduleDaily(ctx)
            }
        }
    }
}

/** After a restart or an update, Android forgets alarms: set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        Reminders.scheduleDaily(ctx)
        val end = Store.long("f_end")
        if (end > System.currentTimeMillis()) Reminders.scheduleFocus(ctx, end)
    }
}
