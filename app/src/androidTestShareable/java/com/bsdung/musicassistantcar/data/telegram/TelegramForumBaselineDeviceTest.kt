package com.bsdung.musicassistantcar.data.telegram

import android.content.Intent
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Can run against the installed v2 APK. Reads only the app's music catalogue, never TDLib's database/files. */
@RunWith(AndroidJUnit4::class)
class TelegramForumBaselineDeviceTest {
    @Test fun snapshotExistingCatalogue() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("forumBaselineProbe") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, TelegramPreviewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val field = TelegramPreviewActivity::class.java.getDeclaredField("libraryDb").apply { isAccessible = true }
            val loaded = TelegramPreviewActivity::class.java.getDeclaredField("playlistLoaded").apply { isAccessible = true }
            val favourites = TelegramPreviewActivity::class.java.getDeclaredField("favoriteIds").apply { isAccessible = true }
            val deadline = System.currentTimeMillis() + 50_000L
            while ((ShareableTdlibOwner.authorizedAccount() == null || field.get(activity) == null || !loaded.getBoolean(activity)) &&
                System.currentTimeMillis() < deadline) Thread.sleep(100)
            val account = ShareableTdlibOwner.authorizedAccount()
            assertTrue("BLOCKED: existing account/catalogue not ready", account != null && field.get(activity) != null)
            val db = (field.get(activity) as SQLiteOpenHelper).readableDatabase
            val count = db.rawQuery("SELECT COUNT(*) FROM telegram_songs WHERE account_id=? AND is_deleted=0", arrayOf(account.toString()))
                .use { it.moveToFirst(); it.getInt(0) }
            val favouriteCount = (favourites.get(activity) as? List<*>)?.size ?: 0
            instrumentation.sendStatus(0, Bundle().apply {
                putString("forum_baseline", "version=${db.version} songs=$count favourites=$favouriteCount")
            })
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
