package com.bsdung.musicassistantcar.playback

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class ScheduledPeriodicWorkSpec(
    val uniqueWorkName: String = OfflinePlaylistSyncScheduler.UNIQUE_WORK_NAME,
    val repeatIntervalMillis: Long = OfflinePlaylistSyncScheduler.PERIODIC_INTERVAL_MS,
    val requiredNetworkType: NetworkType = NetworkType.CONNECTED,
    val existingWorkPolicy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
    val backoffPolicy: BackoffPolicy = BackoffPolicy.EXPONENTIAL,
    val backoffDelayMillis: Long = OfflinePlaylistSyncScheduler.BACKOFF_DELAY_MS,
    val workerClassName: String = OfflinePlaylistSyncWorker::class.java.name,
) {
    val repeatIntervalMs: Long
        get() = repeatIntervalMillis
}

interface WorkSchedulerBackend {
    fun enqueueUniquePeriodicWork(spec: ScheduledPeriodicWorkSpec): String
    fun cancelUniquePeriodicWork(uniqueWorkName: String)
    fun isUniquePeriodicWorkScheduled(uniqueWorkName: String): Boolean
    fun getActivePeriodicWorkCount(): Int
    fun getScheduledSpec(uniqueWorkName: String): ScheduledPeriodicWorkSpec?
    fun getScheduledWorkId(uniqueWorkName: String): String?
}

open class InMemoryWorkSchedulerBackend : WorkSchedulerBackend {
    private val seq = AtomicLong(0L)
    private val activeWorks = LinkedHashMap<String, Pair<String, ScheduledPeriodicWorkSpec>>()
    @Volatile
    var cancelCount: Int = 0
        private set
    @Volatile
    var replacedExistingCount: Int = 0
        private set

    @Synchronized
    override fun enqueueUniquePeriodicWork(spec: ScheduledPeriodicWorkSpec): String {
        val existing = activeWorks[spec.uniqueWorkName]
        if (existing != null) {
            if (spec.existingWorkPolicy == ExistingPeriodicWorkPolicy.KEEP) {
                return existing.first
            }
            replacedExistingCount++
        }
        val workId = "periodic_work:${spec.uniqueWorkName}:${seq.incrementAndGet()}"
        activeWorks[spec.uniqueWorkName] = Pair(workId, spec)
        return workId
    }

    @Synchronized
    override fun cancelUniquePeriodicWork(uniqueWorkName: String) {
        cancelCount++
        activeWorks.remove(uniqueWorkName)
    }

    @Synchronized
    override fun isUniquePeriodicWorkScheduled(uniqueWorkName: String): Boolean {
        return activeWorks.containsKey(uniqueWorkName)
    }

    @Synchronized
    override fun getActivePeriodicWorkCount(): Int {
        return activeWorks.size
    }

    @Synchronized
    fun getScheduledWorkCount(): Int = getActivePeriodicWorkCount()

    @Synchronized
    override fun getScheduledSpec(uniqueWorkName: String): ScheduledPeriodicWorkSpec? {
        return activeWorks[uniqueWorkName]?.second
    }

    @Synchronized
    override fun getScheduledWorkId(uniqueWorkName: String): String? {
        return activeWorks[uniqueWorkName]?.first
    }
}

class AndroidWorkManagerBackend(
    private val context: Context,
) : InMemoryWorkSchedulerBackend() {

    @Synchronized
    override fun enqueueUniquePeriodicWork(spec: ScheduledPeriodicWorkSpec): String {
        val workId = super.enqueueUniquePeriodicWork(spec)
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(spec.requiredNetworkType)
                .build()
            val request = PeriodicWorkRequestBuilder<OfflinePlaylistSyncWorker>(
                spec.repeatIntervalMillis,
                TimeUnit.MILLISECONDS,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    spec.backoffPolicy,
                    spec.backoffDelayMillis,
                    TimeUnit.MILLISECONDS,
                )
                .addTag(spec.uniqueWorkName)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                spec.uniqueWorkName,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            OfflinePlaylistSyncWorker.scheduleAllowImmediateForcedRun(context.applicationContext)
        } catch (_: Exception) {
        }
        return workId
    }

    @Synchronized
    override fun cancelUniquePeriodicWork(uniqueWorkName: String) {
        super.cancelUniquePeriodicWork(uniqueWorkName)
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName)
        } catch (_: Exception) {
        }
    }
}

class OfflinePlaylistSyncScheduler(
    private val backend: WorkSchedulerBackend = InMemoryWorkSchedulerBackend(),
    private val watchedCountProvider: () -> Int,
) {
    companion object {
        const val UNIQUE_WORK_NAME = "melovault_offline_playlist_sync"
        const val PERIODIC_INTERVAL_HOURS = 6L
        const val PERIODIC_INTERVAL_MS = 6L * 60L * 60L * 1000L
        const val BACKOFF_DELAY_MS = 30_000L
        const val MAX_RETRY_ATTEMPTS = 2
    }

    @Synchronized
    fun ensureScheduled(): Boolean {
        val count = watchedCountProvider()
        return if (count > 0) {
            val spec = ScheduledPeriodicWorkSpec(
                uniqueWorkName = UNIQUE_WORK_NAME,
                repeatIntervalMillis = PERIODIC_INTERVAL_MS,
                requiredNetworkType = NetworkType.CONNECTED,
                backoffPolicy = BackoffPolicy.EXPONENTIAL,
                backoffDelayMillis = BACKOFF_DELAY_MS,
                workerClassName = OfflinePlaylistSyncWorker::class.java.name,
            )
            backend.enqueueUniquePeriodicWork(spec)
            true
        } else {
            backend.cancelUniquePeriodicWork(UNIQUE_WORK_NAME)
            false
        }
    }

    @Synchronized
    fun isPeriodicWorkScheduled(): Boolean {
        return backend.isUniquePeriodicWorkScheduled(UNIQUE_WORK_NAME)
    }

    @Synchronized
    fun isScheduled(): Boolean = isPeriodicWorkScheduled()

    @Synchronized
    fun getActivePeriodicWorkCount(): Int {
        return backend.getActivePeriodicWorkCount()
    }

    @Synchronized
    fun getScheduledWorkSpec(): ScheduledPeriodicWorkSpec? {
        return backend.getScheduledSpec(UNIQUE_WORK_NAME)
    }

    @Synchronized
    fun getScheduledSpec(): ScheduledPeriodicWorkSpec? = getScheduledWorkSpec()

    @Synchronized
    fun getScheduledWorkId(): String? {
        return backend.getScheduledWorkId(UNIQUE_WORK_NAME)
    }
}
