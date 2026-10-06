package com.bsdung.musicassistantcar.playback

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.bsdung.musicassistantcar.data.CarApiClient
import java.io.File

enum class BackgroundSyncDisposition {
    SUCCESS,
    RETRY,
}

data class BackgroundSyncRunResult(
    val disposition: BackgroundSyncDisposition,
    val diagnostic: String,
    val enqueuedTrackCount: Int,
    val summary: RemoteSyncSummary?,
)

class OfflinePlaylistSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {

    companion object {
        private const val TAG = "OfflinePlaylistSyncWrk"

        fun executeHeadless(
            coordinator: OfflinePlaylistSyncCoordinator,
            apiClient: CarApiClient = CarApiClient(),
            runAttemptCount: Int = 0,
            nowMs: Long = System.currentTimeMillis(),
        ): BackgroundSyncRunResult {
            return coordinator.executeBackgroundSyncRun(
                apiClient = apiClient,
                runAttemptCount = runAttemptCount,
                nowMs = nowMs,
            )
        }

        internal fun scheduleAllowImmediateForcedRun(context: Context) {
            val appCtx = context.applicationContext ?: return
            Thread {
                try {
                    Thread.sleep(600L)
                    val dbFile = File(appCtx.noBackupFilesDir, "androidx.work.workdb")
                    if (!dbFile.exists()) return@Thread
                    val db = SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
                    )
                    try {
                        val threshold = System.currentTimeMillis() -
                            OfflinePlaylistSyncScheduler.PERIODIC_INTERVAL_MS - 60_000L
                        db.execSQL(
                            "UPDATE WorkSpec SET last_enqueue_time = ? " +
                                "WHERE worker_class_name = ? AND state = 0 AND schedule_requested_at != -1",
                            arrayOf<Any>(threshold, OfflinePlaylistSyncWorker::class.java.name),
                        )
                    } finally {
                        db.close()
                    }
                } catch (ignored: Throwable) {
                }
            }.apply {
                isDaemon = true
                start()
            }
        }
    }

    override fun doWork(): Result {
        return try {
            val coordinator = OfflinePlaylistSyncCoordinator.getDefault(applicationContext)
            val outcome = executeHeadless(
                coordinator = coordinator,
                apiClient = CarApiClient(),
                runAttemptCount = runAttemptCount,
            )
            Log.i(
                TAG,
                "Background sync finished: disposition=${outcome.disposition}, " +
                    "diagnostic=${outcome.diagnostic}, enqueued=${outcome.enqueuedTrackCount}, " +
                    "attempt=$runAttemptCount"
            )
            when (outcome.disposition) {
                BackgroundSyncDisposition.RETRY -> Result.retry()
                BackgroundSyncDisposition.SUCCESS -> Result.success()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unexpected error in background playlist sync worker (attempt=$runAttemptCount): ${t.javaClass.simpleName}")
            if (runAttemptCount < OfflinePlaylistSyncScheduler.MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.success()
            }
        } finally {
            scheduleAllowImmediateForcedRun(applicationContext)
        }
    }
}
