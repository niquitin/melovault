package com.bsdung.musicassistantcar.data.telegram

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/** App-wide foreground ownership, including Settings. No timer survives the last resumed Activity. */
internal object PlaylistSyncForeground {
    private var installed = false

    @Synchronized fun install(application: Application, root: File) {
        if (installed || !TelegramPlaylistFlags.SYNC_ENABLED) return
        installed = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private val resumed = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
            override fun onActivityResumed(activity: Activity) {
                resumed.add(activity)
                SyncRuntime.setForeground(true, root)
            }
            override fun onActivityPaused(activity: Activity) {
                resumed.remove(activity)
                SyncRuntime.setForeground(resumed.isNotEmpty(), root)
            }
            override fun onActivityDestroyed(activity: Activity) {
                resumed.remove(activity)
                SyncRuntime.setForeground(resumed.isNotEmpty(), root)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        })
    }
}
