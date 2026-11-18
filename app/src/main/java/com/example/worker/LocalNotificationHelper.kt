package com.example.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

object LocalNotificationHelper {
    const val CHANNEL_BUDGET = "hisabnikash_budget_alerts"
    const val CHANNEL_RECURRING = "hisabnikash_recurring_reminders"
    const val CHANNEL_BACKUP = "hisabnikash_backup_status"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val budgetChannel = NotificationChannel(
            CHANNEL_BUDGET,
            context.getString(R.string.channel_budget_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_budget_desc)
        }
        val recurringChannel = NotificationChannel(
            CHANNEL_RECURRING,
            context.getString(R.string.channel_recurring_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_recurring_desc)
        }
        val backupChannel = NotificationChannel(
            CHANNEL_BACKUP,
            context.getString(R.string.channel_backup_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_backup_desc)
        }
        manager.createNotificationChannels(listOf(budgetChannel, recurringChannel, backupChannel))
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun postNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_symbol)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
