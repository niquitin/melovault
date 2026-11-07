package com.bsdung.musicassistantcar.data.telegram

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * Explicit live QA of the two preliminary-review fixes, on the signed-in account's existing music.
 * No Telegram writes, playlist changes, or session/cache file reads. Run with widgetFixesProbe=true.
 */
@RunWith(AndroidJUnit4::class)
class MeloWidgetFixesDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T> ui(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
    private fun report(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("widget_fixes", value) })
    private fun await(label: String, timeout: Long = 60_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (ui(condition)) return
            Thread.sleep(100)
        }
        fail("BLOCKED/FAIL: $label")
    }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)
    private fun setField(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(owner, value)
    private fun activityCall(activity: TelegramPreviewActivity, name: String, vararg args: Any) {
        val method = TelegramPreviewActivity::class.java.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        method.isAccessible = true
        method.invoke(activity, *args)
    }
    private fun service(): ShareablePlaybackService? {
        val reference = ShareablePlaybackService::class.java.getDeclaredField("serviceRef")
            .apply { isAccessible = true }.get(null) as java.lang.ref.WeakReference<*>?
        return reference?.get() as ShareablePlaybackService?
    }

    private fun start(): TelegramPreviewActivity {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TelegramPreviewActivity
        await("account, catalogue and controller ready") {
            ShareableTdlibOwner.authorizedAccount() != null && field(activity, "libraryDb") != null && field(activity, "player") != null
        }
        return activity
    }

    /** Opens the largest ordinary source and returns its listed songs. */
    private fun songsOf(activity: TelegramPreviewActivity, owner: Long): List<Any> {
        val db = field(activity, "libraryDb") as TelegramLibraryDao
        val source = db.getSources(owner).filter { it.trackCount > 1 }.maxByOrNull { it.trackCount }
        assertNotNull("BLOCKED: no music source with two songs", source)
        val host = field(activity, "meloHost") as MeloScreenHost
        ui { host.openSource(source!!.chatId) }
        await("source songs listed") {
            !host.snapshot().forumLoading && host.snapshot().browseLoadState == MeloBrowseUiPolicy.LoadState.READY &&
                (field(activity, "shownSongs") as List<*>).size > 1
        }
        @Suppress("UNCHECKED_CAST")
        return ui { (field(activity, "shownSongs") as List<Any>).filterNot { field(it, "placeholder") == true } }
    }

    /** A song chosen with no service yet. Without the hand-over the service has no queue and the widget reads empty. */
    @Test fun songChosenBeforeServiceExistsReachesWidgetWithOwnerAndQueue() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("widgetFixesProbe") == "true")
        val activity = start()
        val owner = requireNotNull(ShareableTdlibOwner.authorizedAccount())
        val song = songsOf(activity, owner).first()
        val songId = field(song, "messageId") as Long

        ui {
            val controller = field(activity, "player") as MediaController
            controller.stop()
            setField(activity, "player", null)
            MediaController.releaseFuture(field(activity, "controllerFuture") as com.google.common.util.concurrent.ListenableFuture<MediaController>)
            activity.stopService(Intent(activity, ShareablePlaybackService::class.java))
        }
        await("playback service gone before the song is chosen") { service() == null }

        ui { activityCall(activity, "playSongFromBrowse", song) }
        await("song waits for the controller", 90_000L) { field(activity, "pendingPlayback") != null }
        assertNull("service must still be absent for this path to be exercised", ui { service() })
        assertTrue("queue was not kept for the hand-over",
            ui { ShareablePlaybackService.currentTelegramQueue(owner).isEmpty() })
        report("pending_without_service=true")

        ui { activityCall(activity, "connectPlaybackService") }
        await("pending song started") { field(activity, "pendingPlayback") == null && field(activity, "player") != null }
        val controller = ui { field(activity, "player") as MediaController }
        await("controller reports the chosen song") { controller.currentMediaItem?.mediaId == "telegram:$songId" }
        assertTrue("service queue misses the chosen song",
            ui { ShareablePlaybackService.currentTelegramQueue(owner).any { it.messageId == songId } })
        await("widget accepts the item instead of reading empty") { ShareablePlaybackService.widgetCanOpenPlayer() }
        report("handoff_ok=true queue=${ui { ShareablePlaybackService.currentTelegramQueue(owner).size }} state=${ui { controller.playbackState }}")
        ui { controller.pause() }
    }

    /** Needs a widget on the launcher: without one the session stays inactive by design. */
    @Test fun sameSongMetadataUpdateDoesNotReopenNextInsideTheWindow() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("widgetFixesProbe") == "true")
        val activity = start()
        val owner = requireNotNull(ShareableTdlibOwner.authorizedAccount())
        assumeTrue("BLOCKED: add the widget first", MeloNowPlayingWidgetProvider.widgetIds(activity).isNotEmpty())
        val songs = songsOf(activity, owner)
        ui { activityCall(activity, "playSongFromBrowse", songs.first()) }
        await("song A playing with a queue", 90_000L) {
            val id = (field(activity, "player") as MediaController).currentMediaItem?.mediaId
            id?.startsWith("telegram:") == true && ShareablePlaybackService.currentTelegramQueue(owner).size > 1 &&
                ShareablePlaybackService.widgetCanOpenPlayer()
        }
        val outcome = ui {
            val service = requireNotNull(service())
            val session = field(service, "widgetSession") as MeloWidgetSession
            val generation = field(session, "generation") as MeloWidgetPolicy.Generation
            val exo = field(service, "exoPlayer") as ExoPlayer
            session.refreshNow()
            val idA = exo.currentMediaItem!!.mediaId
            val first = generation.token
            // One main-thread block, so a fast TDLib reply cannot move A to B between the presses.
            ShareablePlaybackService.dispatchWidget(MeloWidgetPolicy.Action.NEXT_ITEM, first)
            val firstPress = field(generation, "nextAt") as Long?
            Thread.sleep(25)
            val item = exo.currentMediaItem!!
            exo.replaceMediaItem(exo.currentMediaItemIndex, item.buildUpon().setMediaMetadata(
                item.mediaMetadata.buildUpon().setTitle("${item.mediaMetadata.title} ").build()).build())
            session.refreshNow()
            val redrawn = generation.token
            ShareablePlaybackService.dispatchWidget(MeloWidgetPolicy.Action.NEXT_ITEM, first)
            val afterOld = field(generation, "nextAt") as Long?
            ShareablePlaybackService.dispatchWidget(MeloWidgetPolicy.Action.NEXT_ITEM, redrawn)
            val afterNew = field(generation, "nextAt") as Long?
            listOf(idA == exo.currentMediaItem?.mediaId, first != redrawn, firstPress != null,
                afterOld == firstPress, afterNew == firstPress)
        }
        assertEquals("sameSong, tokenRotated, pressTaken, oldTokenRejected, newTokenFenced",
            listOf(true, true, true, true, true), outcome)
        report("next_fence_kept_across_metadata=true")
        val controller = ui { field(activity, "player") as MediaController }
        ui { if (controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) controller.pause() }
    }
}
