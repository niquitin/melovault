package com.bsdung.musicassistantcar.data.telegram

import android.content.SharedPreferences

/**
 * Rules for controlling playback from outside the app (lock screen, notification, headset,
 * Bluetooth, steering wheel, physical player keys), docs/ui-spec.md step 8. Pure JVM except
 * the preference helpers.
 */
internal object MeloMediaControlPolicy {

    const val KEY_NOTIFICATION_ASKED = "notif_asked"
    /** First Android version where notifications need a runtime permission. */
    const val NOTIFICATION_PERMISSION_SDK = 33

    /**
     * Ask once, after sign-in, on Android 13+ when the permission is not granted: without it the
     * playback notification (lock screen / watch / headset controls on many devices) is hidden.
     */
    fun shouldAskNotificationPermission(sdk: Int, granted: Boolean, alreadyAsked: Boolean, authorized: Boolean): Boolean =
        authorized && sdk >= NOTIFICATION_PERMISSION_SDK && !granted && !alreadyAsked

    /** Next must stay enabled for external controllers when the queue has a next song not loaded yet. */
    fun nextAvailable(loadedHasNext: Boolean, queueHasNextNotLoaded: Boolean): Boolean =
        loadedHasNext || queueHasNextNotLoaded

    /**
     * When a track ends and the next one was never loaded (the screen is off, prefetch was off or
     * too slow), the service continues by itself, but only if no screen is doing it already and the
     * owner did not ask to stop at the end of this track.
     */
    fun shouldContinueAfterEnd(uiAttached: Boolean, stopAtEndOfTrack: Boolean, queueHasNextNotLoaded: Boolean): Boolean =
        !uiAttached && !stopAtEndOfTrack && queueHasNextNotLoaded

    /** A user press on Next may start a download even when background prefetch is off or a screen is attached. */
    fun mayDownloadNext(userRequestedThis: Boolean, prefetchEnabled: Boolean, uiAttached: Boolean): Boolean =
        userRequestedThis || (prefetchEnabled && !uiAttached)

    /** The channel name is a truthful fallback artist for lock screen and headsets; tags in the file still win. */
    fun fallbackArtist(sourceTitle: String?): String? = sourceTitle?.trim()?.takeIf { it.isNotEmpty() }

    fun notificationAsked(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)

    fun markNotificationAsked(prefs: SharedPreferences) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply()
    }
}
