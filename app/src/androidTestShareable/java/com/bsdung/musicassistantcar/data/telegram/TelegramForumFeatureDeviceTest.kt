package com.bsdung.musicassistantcar.data.telegram

import android.content.Intent
import android.content.pm.ActivityInfo
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Explicit live QA: existing music only, no Telegram writes, playlist changes, new clients or session/cache file reads. */
@RunWith(AndroidJUnit4::class)
class TelegramForumFeatureDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T> ui(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
    private fun report(value: String) = instrumentation.sendStatus(0, Bundle().apply { putString("forum_device", value) })
    private fun stage(value: String) {
        instrumentation.waitForIdleSync(); Thread.sleep(500)
        instrumentation.sendStatus(0, Bundle().apply { putString("forum_stage", value) })
        Thread.sleep(2200) // host captures a PNG with adb exec-out while this real UI is on screen
    }
    private fun await(label: String, timeout: Long = 45_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("BLOCKED/FAIL: $label")
    }
    private fun field(activity: TelegramPreviewActivity, name: String): Any? = TelegramPreviewActivity::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(activity)

    @Test fun browsePlayAndRestoreExistingForum() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("forumFeatureProbe") == "true")
        var activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as TelegramPreviewActivity
        val originalOrientation = ui { activity.requestedOrientation }
        var host: MeloScreenHost? = null
        var originalShuffle = false
        val hub = TdlibSyncHub(ShareableTdlibOwner)
        try {
            await("existing account and music catalogue ready") { ShareableTdlibOwner.authorizedAccount() != null && field(activity, "libraryDb") != null }
            val owner = requireNotNull(ShareableTdlibOwner.authorizedAccount())
            val epoch = ShareableTdlibOwner.authEpoch()
            val db = field(activity, "libraryDb") as TelegramLibraryDao
            val helper = db as SQLiteOpenHelper
            assertEquals(3, helper.readableDatabase.version)
            val before = args.getString("baselineSongs")?.toIntOrNull() ?: 0
            assertTrue("migration reduced the live song count", db.getSongCount(owner) >= before)
            val retained = db.getSongs(owner, limit = 200).map { it.songKey }
            host = field(activity, "meloHost") as MeloScreenHost
            fun snap() = ui { host!!.snapshot() }
            fun ready() = !snap().forumLoading && snap().browseLoadState == MeloBrowseUiPolicy.LoadState.READY
            originalShuffle = ui { host!!.nowPlaying()?.shuffle == true }
            assertTrue("BLOCKED: owner sink busy; never replaces it", hub.open())
            var requests = 0
            fun call(json: JSONObject, optional: Boolean = false): JSONObject? {
                assertTrue("bounded discovery budget exceeded", ++requests <= 120)
                val type = json.getString("@type")
                return when (val reply = hub.call(owner, epoch, type, 30_000) { target ->
                    json.keys().forEach { key -> if (key != "@type") target.put(key, json.get(key)) }
                }) {
                    is TdlibSyncHub.Reply.Ok -> reply.json
                    is TdlibSyncHub.Reply.Err -> if (optional && reply.code in setOf(400, 403, 404)) null else error("BLOCKED: read code=${reply.code}")
                    else -> error("BLOCKED: discovery read unavailable")
                }
            }
            var regular: Long? = null
            var forum: Long? = null
            var samples: List<Pair<Int, Int>> = emptyList()
            for (source in db.getSources(owner).take(35)) {
                val chat = call(JSONObject().put("@type", "getChat").put("chat_id", source.chatId), true) ?: continue
                val type = chat.getJSONObject("type")
                val isForum = if (type.getString("@type") != "chatTypeSupergroup") false else
                    call(JSONObject().put("@type", "getSupergroup").put("supergroup_id", type.getLong("supergroup_id")), true)?.optBoolean("is_forum") == true
                if (!isForum) { if (regular == null && source.trackCount > 0) regular = source.chatId; continue }
                val topics = mutableListOf<TelegramForumContract.Topic>()
                var cursor = TelegramForumContract.ListCursor()
                for (page in 0 until 3) {
                    val response = call(TelegramForumContract.topicsRequest(source.chatId, cursor), true) ?: break
                    val parsed = TelegramForumContract.topicPage(response, source.chatId, cursor)
                    topics.addAll(parsed.topics.filterNot { it.isHidden })
                    cursor = parsed.next
                    if (parsed.complete || parsed.stalled) break
                }
                val music = mutableListOf<Pair<Int, Int>>()
                for (topic in topics.distinctBy { it.id }.take(12)) {
                    val history = call(TelegramForumContract.historyRequest(source.chatId, topic.id), true) ?: continue
                    val messages = history.getJSONArray("messages")
                    val found = (0 until messages.length()).mapNotNull { TelegramSongMessageParser.parse(messages.getJSONObject(it), owner, owner,
                        source.chatId, requestedTopicId = topic.id) }
                    if (found.isNotEmpty()) music.add(topic.id to found.size)
                    if (music.size >= 3) break
                }
                if (music.size >= 2) { forum = source.chatId; samples = music.sortedByDescending { it.second }.take(2); break }
            }
            hub.close()
            assertTrue("BLOCKED: no existing indexed forum with two music topics within budget", forum != null && samples.size == 2)
            assertTrue("BLOCKED: no ordinary music source", regular != null)
            val chat = requireNotNull(forum)
            val a = samples[0].first; val b = samples[1].first
            ui { host!!.openSource(regular) }
            await("ordinary source opens songs") { ready() && snap().browseScope.kind == MeloBrowseUiPolicy.Kind.CHANNEL && snap().rows.isNotEmpty() }
            stage("Q1_normal")
            ui { host!!.openSource(chat) }
            await("forum catalogue ready") { ready() && snap().forumTopics != null }
            repeat(5) {
                if (snap().forumTopics.orEmpty().map { it.id }.containsAll(listOf(a, b)) || !snap().forumCanLoadMore) return@repeat
                ui { host!!.loadMoreForum() }; await("catalogue next page") { ready() }
            }
            assertTrue(snap().forumTopics.orEmpty().map { it.id }.containsAll(listOf(a, b)))
            stage("Q2_catalogue")
            val groupBefore = db.getSongCount(owner, chat)
            ui { host!!.openAllForumMusic(); host!!.setQuery("") }
            await("all group music includes legacy rows") { ready() && snap().browseScope.kind == MeloBrowseUiPolicy.Kind.CHANNEL }
            assertTrue((snap().total ?: 0) >= groupBefore)
            stage("Q3_all_group")
            ui { assertTrue(host!!.backWithinForum()) }
            await("back to catalogue") { ready() && snap().forumTopics != null }
            ui { host!!.openForumTopic(a) }
            await("topic A music") { ready() && snap().browseScope.forumTopicId == a && snap().rows.isNotEmpty() }
            repeat(3) {
                if (db.getSongCount(owner, chat, forumTopicId = a) > 200 || !snap().forumCanLoadMore) return@repeat
                ui { host!!.loadMoreForum() }; await("topic A older pages") { ready() }
            }
            val indexedBeforePlay = db.getSongCount(owner, chat, forumTopicId = a)
            assertTrue(snap().rows.all { row -> db.getSongByKey(owner, row.songKey)?.forumTopicId == a })
            ui { host!!.playAll(false) }
            fun queue() = ui { ShareablePlaybackService.currentTelegramQueue(owner).map { it.messageId } }
            await("topic A queue configured", 90_000) {
                val keys = queue(); val expected = db.getSongs(owner, chat, limit = Int.MAX_VALUE, forumTopicId = a).map { it.songKey }.toSet()
                keys.isNotEmpty() && keys.size >= indexedBeforePlay && keys.all { it in expected }
            }
            report("queue indexed=$indexedBeforePlay configured=${queue().size} outside_first_page=${indexedBeforePlay > 200} discovery_requests=$requests")
            stage("Q4_play_all_A")
            val aKeys = db.getSongs(owner, chat, limit = Int.MAX_VALUE, forumTopicId = a).map { it.songKey }.toSet()
            val current = ui { host!!.nowPlaying()?.songKey }
            if (queue().size > 1) {
                ui { host!!.next() }
                await("Next remains in A", 60_000) { ui { host!!.nowPlaying()?.songKey?.let { it in aKeys && it != current } == true } }
            }
            val queueA = queue()
            ui { assertTrue(host!!.backWithinForum()) }
            await("catalogue after playback") { ready() && snap().forumTopics != null }
            ui { host!!.openForumTopic(b) }
            await("topic B browse") { ready() && snap().browseScope.forumTopicId == b && snap().rows.isNotEmpty() }
            assertEquals("browsing B changed A queue", queueA, queue())
            assertTrue(snap().rows.all { row -> db.getSongByKey(owner, row.songKey)?.forumTopicId == b })
            val atB = ui { host!!.nowPlaying() }
            report("browse_B queue_unchanged=true playing=${atB?.playing == true} position_ms=${atB?.positionMs ?: 0}")
            stage("Q5_B_queue_A")
            ui { (field(activity, "player") as? MediaController)?.pause() }
            ui { host!!.filterInSource(MeloCompactPolicy.Mode.DOWNLOADED) }
            await("Downloaded stays in B") { ready() && snap().browseScope.forumTopicId == b && snap().browseScope.downloadedOnly }
            assertTrue(snap().rows.all { it.downloaded })
            report("downloaded_B rows=${snap().rows.size}")
            stage("Q6_downloaded_B")
            ui { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            await("landscape retains B filter") { ready() && snap().browseScope.forumTopicId == b && snap().browseScope.downloadedOnly &&
                ui { activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE } }
            stage("Q7_landscape_B")
            val old = activity
            ui { activity.recreate() }
            await("Activity recreated") {
                val fresh = ui { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<TelegramPreviewActivity>().firstOrNull { it !== old } }
                if (fresh == null) false else { activity = fresh; host = field(activity, "meloHost") as MeloScreenHost; true }
            }
            await("restored B owner/filter/query", 60_000) { ready() && snap().browseOwnerId == owner && snap().browseScope.forumTopicId == b && snap().browseScope.downloadedOnly }
            val afterDb = field(activity, "libraryDb") as TelegramLibraryDao
            assertTrue("legacy sample no longer resolves after migration/recreation", afterDb.getSongsByKeys(owner, retained).size == retained.size)
            assertEquals("recreation changed queue", queueA, queue())
            report("result restored=true legacy_sample=${retained.size} db_version=3 songs=${afterDb.getSongCount(owner)} audio_actual=unconfirmed")
            stage("Q8_restored_B")
        } finally {
            hub.close()
            ui {
                (field(activity, "player") as? MediaController)?.pause()
                if (host?.nowPlaying()?.shuffle != originalShuffle) host?.toggleShuffle()
                activity.requestedOrientation = originalOrientation
            }
        }
    }
}
