package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.BandwidthQuotaAlert
import com.example.data.model.QuotaAlertSeverity
import com.example.data.model.ResourceAlertEvent
import com.example.data.model.ResourceMetricType
import com.example.data.model.ThroughputAlertEvent
import com.example.data.model.formatBytes
import java.util.Locale

object QuotaNotificationHelper {

    const val CHANNEL_ID = "erlkim_quota_alerts"
    private const val CHANNEL_NAME = "Peringatan Kuota Bandwidth"
    private const val CHANNEL_DESC = "Notifikasi saat penggunaan bandwidth user hotspot mendekati atau melebihi batas kuota."

    const val THROUGHPUT_CHANNEL_ID = "erlkim_throughput_alerts"
    private const val THROUGHPUT_CHANNEL_NAME = "Peringatan Lonjakan Trafik"
    private const val THROUGHPUT_CHANNEL_DESC = "Notifikasi real-time saat total throughput jaringan melebihi batas Mbps yang diatur."

    const val RESOURCE_CHANNEL_ID = "erlkim_resource_alerts"
    private const val RESOURCE_CHANNEL_NAME = "Peringatan CPU & Memori Router"
    private const val RESOURCE_CHANNEL_DESC = "Notifikasi push saat beban CPU atau penggunaan Memori router melebihi ambang batas persentase."

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
            if (manager.getNotificationChannel(THROUGHPUT_CHANNEL_ID) == null) {
                val throughputChannel = NotificationChannel(
                    THROUGHPUT_CHANNEL_ID,
                    THROUGHPUT_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = THROUGHPUT_CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(throughputChannel)
            }
            if (manager.getNotificationChannel(RESOURCE_CHANNEL_ID) == null) {
                val resourceChannel = NotificationChannel(
                    RESOURCE_CHANNEL_ID,
                    RESOURCE_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = RESOURCE_CHANNEL_DESC
                    enableVibration(true)
                }
                manager.createNotificationChannel(resourceChannel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showQuotaAlert(context: Context, alert: BandwidthQuotaAlert) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.username.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (alert.severity == QuotaAlertSeverity.EXCEEDED) {
            "Kuota Terlampaui: ${alert.username}"
        } else {
            "Mendekati Batas Kuota: ${alert.username}"
        }
        val usedStr = formatBytes(alert.usedBytes)
        val limitStr = formatBytes(alert.limitBytes)
        val ipSuffix = if (alert.ipAddress.isNotBlank()) " • IP ${alert.ipAddress}" else ""
        val detail = "Pemakaian $usedStr / $limitStr (${alert.usagePercent}%) • Profil ${alert.profile}$ipSuffix"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("$usedStr / $limitStr (${alert.usagePercent}%)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(
                if (alert.severity == QuotaAlertSeverity.EXCEEDED) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                2000 + (alert.username.hashCode() and 0x0FFF),
                notification,
            )
        }
    }

    fun showThroughputAlert(context: Context, event: ThroughputAlertEvent) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            4091,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val totalStr = String.format(Locale.US, "%.1f Mbps", event.totalMbps)
        val limitStr = String.format(Locale.US, "%.0f Mbps", event.thresholdMbps)
        val dlStr = String.format(Locale.US, "%.1f", event.downloadMbps)
        val ulStr = String.format(Locale.US, "%.1f", event.uploadMbps)
        val iface = event.interfaceName.ifBlank { "WAN" }
        val detail = "Total throughput $totalStr melebihi batas $limitStr pada $iface (Unduh $dlStr / Unggah $ulStr Mbps)."

        val notification = NotificationCompat.Builder(context, THROUGHPUT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Lonjakan Trafik Tinggi: $totalStr")
            .setContentText("Batas $limitStr terlampaui pada $iface")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(4091, notification)
        }
    }

    fun showResourceAlert(context: Context, event: ResourceAlertEvent) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val notifId = if (event.metricType == ResourceMetricType.CPU) 4092 else 4093
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val metricLabel = if (event.metricType == ResourceMetricType.CPU) "CPU" else "Memori"
        val title = "$metricLabel Router Tinggi: ${event.currentPercent}%"
        val shortText = "${event.routerIdentity} melebihi batas $metricLabel ${event.thresholdPercent}%"
        val bigText = "Penggunaan $metricLabel pada ${event.routerIdentity} mencapai ${event.currentPercent}% (batas ${event.thresholdPercent}%). ${event.detail}"

        val notification = NotificationCompat.Builder(context, RESOURCE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}
