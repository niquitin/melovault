package com.bsdung.musicassistantcar.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bsdung.musicassistantcar.MainActivity
import java.util.Locale

class PinnedDownloadService : Service() {

    companion object {
        private const val TAG = "PinnedDownloadService"
        const val CHANNEL_ID = "melovault_downloads"
        const val NOTIFICATION_ID = 2002
        const val ACTION_PROCESS_QUEUE = "com.bsdung.musicassistantcar.action.PROCESS_DOWNLOAD_QUEUE"
        const val ACTION_STOP_SERVICE = "com.bsdung.musicassistantcar.action.STOP_DOWNLOAD_SERVICE"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, PinnedDownloadService::class.java).apply {
                action = ACTION_PROCESS_QUEUE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start PinnedDownloadService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PinnedDownloadService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop PinnedDownloadService", e)
            }
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var lastNotificationUpdateMs = 0L

    private val downloadListener = object : PinnedDownloadManager.DownloadListener {
        override fun onDownloadStarted(trackId: Long) {
            val manager = PinnedDownloadManager.getDefault(applicationContext)
            val status = manager.getDownloadStatus(trackId)
            updateNotification(status.title, 0L, status.totalBytes)
        }

        override fun onDownloadProgress(trackId: Long, bytesDownloaded: Long, totalBytes: Long) {
            val now = System.currentTimeMillis()
            if (now - lastNotificationUpdateMs >= 500L) {
                lastNotificationUpdateMs = now
                val manager = PinnedDownloadManager.getDefault(applicationContext)
                val status = manager.getDownloadStatus(trackId)
                updateNotification(status.title, bytesDownloaded, totalBytes)
            }
        }

        override fun onDownloadFinalizing(trackId: Long) {
            val manager = PinnedDownloadManager.getDefault(applicationContext)
            val status = manager.getDownloadStatus(trackId)
            updateNotification(status.title, status.bytesDownloaded, status.totalBytes)
        }

        override fun onDownloadCompleted(trackId: Long, record: OfflineTrackRecord) {
            checkQueueAndStopIfIdle()
        }

        override fun onDownloadFailed(trackId: Long, error: String) {
            checkQueueAndStopIfIdle()
        }

        override fun onDownloadCancelled(trackId: Long) {
            checkQueueAndStopIfIdle()
        }

        override fun onDownloadDeleted(trackId: Long) {
            checkQueueAndStopIfIdle()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val initialNotification = buildNotification("Đang chuẩn bị tải nhạc...", 0L, 0L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground in onCreate", e)
        }

        PinnedDownloadManager.getDefault(applicationContext).addListener(downloadListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForegroundAndFinish()
            return START_NOT_STICKY
        }

        val manager = PinnedDownloadManager.getDefault(applicationContext)
        if (!manager.hasActiveJobs()) {
            stopForegroundAndFinish()
            return START_NOT_STICKY
        }

        val currentJob = manager.getCurrentActiveJob()
        if (currentJob != null) {
            updateNotification(currentJob.track.title, currentJob.bytesDownloaded, currentJob.totalBytes)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        PinnedDownloadManager.getDefault(applicationContext).removeListener(downloadListener)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkQueueAndStopIfIdle() {
        val manager = PinnedDownloadManager.getDefault(applicationContext)
        if (!manager.hasActiveJobs()) {
            stopForegroundAndFinish()
        } else {
            val nextJob = manager.getCurrentActiveJob()
            if (nextJob != null) {
                updateNotification(nextJob.track.title, nextJob.bytesDownloaded, nextJob.totalBytes)
            }
        }
    }

    private fun stopForegroundAndFinish() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop foreground", e)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tải nhạc offline",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo tiến trình tải nhạc ngoại tuyến MeloVault"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, bytesDownloaded: Long, totalBytes: Long): Notification {
        val downloadedMb = bytesDownloaded.toDouble() / (1024.0 * 1024.0)
        val progressText = if (totalBytes > 0L) {
            val totalMb = totalBytes.toDouble() / (1024.0 * 1024.0)
            val percent = ((bytesDownloaded * 100L) / totalBytes).coerceIn(0L, 100L)
            String.format(Locale.US, "%d%% · %.1f / %.1f MB", percent, downloadedMb, totalMb)
        } else {
            String.format(Locale.US, "Đã tải %.1f MB", downloadedMb)
        }

        val displayTitle = title.ifBlank { "Đang tải nhạc offline" }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Đang tải nhạc offline")
            .setContentText("$displayTitle · $progressText")
            .setSubText("MeloVault")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("Đang tải nhạc offline")
                    .setSummaryText("MeloVault")
                    .bigText("Bài hát: $displayTitle\nTiến độ: $progressText")
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (totalBytes > 0L) {
            val percent = ((bytesDownloaded * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(title: String, bytesDownloaded: Long, totalBytes: Long) {
        try {
            val notification = buildNotification(title, bytesDownloaded, totalBytes)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
}
