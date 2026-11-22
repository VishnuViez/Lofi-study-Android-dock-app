package com.vishnu.lofidock.timer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.vishnu.lofidock.MainActivity
import com.vishnu.lofidock.R
import com.vishnu.lofidock.core.TimeFormatting

/**
 * Exact alarms so the notification arrives on time even in Doze. On Android
 * 13+ `USE_EXACT_ALARM` is granted to timer apps; if exact alarms are ever
 * refused, an inexact one still delivers within a few minutes.
 */
class AndroidTimerAlarms(private val context: Context) : TimerAlarms {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(endsAt: Long, lengthSeconds: Double, muted: Boolean) {
        val intent = pendingIntent(lengthSeconds, muted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent(0.0, false))   // extras don't affect matching
        NotificationManagerCompat.from(context).cancel(TimerNotifications.ID)
    }

    private fun pendingIntent(lengthSeconds: Double, muted: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, TimerAlarmReceiver::class.java)
            .putExtra(TimerAlarmReceiver.EXTRA_LENGTH, lengthSeconds)
            .putExtra(TimerAlarmReceiver.EXTRA_MUTED, muted),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // In front, the app rings itself; the notification is for when you've left.
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        TimerNotifications.showFinished(
            context,
            lengthSeconds = intent.getDoubleExtra(EXTRA_LENGTH, 0.0),
            muted = intent.getBooleanExtra(EXTRA_MUTED, false),
        )
    }

    companion object {
        const val EXTRA_LENGTH = "lengthSeconds"
        const val EXTRA_MUTED = "muted"
    }
}

object TimerNotifications {
    const val ID = 41
    private const val CHANNEL = "timer"
    private const val CHANNEL_SILENT = "timer_silent"

    /**
     * A channel's sound is fixed once created, so "muted" is a second channel
     * without one rather than a per-notification setting.
     */
    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val chime = Uri.parse("android.resource://${context.packageName}/${R.raw.lofi_chime}")
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build()
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL, context.getString(R.string.timer_channel), NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(chime, attributes); enableVibration(true) },
                NotificationChannel(CHANNEL_SILENT, context.getString(R.string.timer_channel_silent), NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(null, null); enableVibration(true) },
            ),
        )
    }

    @SuppressLint("MissingPermission")   // checked just below
    fun showFinished(context: Context, lengthSeconds: Double, muted: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, if (muted) CHANNEL_SILENT else CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Time's up")
            .setContentText("Your ${TimeFormatting.countdown(lengthSeconds)} timer has finished.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }
} 
