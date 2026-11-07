package com.bsdung.musicassistantcar.data.telegram

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.bsdung.musicassistantcar.R

/** Same-process, explicit broadcasts only. Never starts or binds a playback service. */
class MeloNowPlayingWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        refresh(context)

    override fun onEnabled(context: Context) = refresh(context)
    override fun onDisabled(context: Context) = refresh(context)
    override fun onDeleted(context: Context, ids: IntArray) = refresh(context)
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.removePrefix(ACTION_PREFIX)
            ?.let { name -> MeloWidgetPolicy.Action.entries.firstOrNull { it.name == name } }
        if (action == null || intent.action != ACTION_PREFIX + action.name) {
            super.onReceive(context, intent)
            return
        }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        if (id !in widgetIds(context) ||
            intent.dataString != MeloWidgetPolicy.intentIdentity(id, action, token)) return
        if (!ShareablePlaybackService.dispatchWidget(action, token)) renderEmpty(context)
    }

    companion object {
        private const val ACTION_PREFIX = "app.melovault.widget."
        private const val EXTRA_TOKEN = "widget_generation"

        internal fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, MeloNowPlayingWidgetProvider::class.java))

        private fun refresh(context: Context) {
            if (!ShareablePlaybackService.refreshWidget()) renderEmpty(context)
        }

        internal fun renderEmpty(context: Context) = render(context,
            MeloWidgetPolicy.view(MeloWidgetPolicy.Input()), "", null)

        internal fun render(context: Context, state: MeloWidgetPolicy.View, token: String, cover: Bitmap?) {
            val manager = AppWidgetManager.getInstance(context)
            for (id in widgetIds(context)) {
                val narrow = manager.getAppWidgetOptions(id)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220) < 240
                val views = RemoteViews(context.packageName, if (narrow)
                    R.layout.melo_now_playing_widget_compact else R.layout.melo_now_playing_widget)
                views.setTextViewText(R.id.widget_title, state.title)
                views.setTextViewText(R.id.widget_performer, state.performer)
                if (cover == null) views.setImageViewResource(R.id.widget_cover, R.drawable.ic_music_note)
                else views.setImageViewBitmap(R.id.widget_cover, cover)
                views.setContentDescription(R.id.widget_header, context.getString(
                    if (state.open == MeloWidgetPolicy.Open.PLAYER) R.string.widget_open_player else R.string.widget_open_library))
                val open = Intent(context, TelegramPreviewActivity::class.java)
                    .setAction(ACTION_PREFIX + "OPEN")
                    .setData(Uri.parse("melovault-widget://open/$id"))
                    .putExtra(MeloWidgetPolicy.EXTRA_OPEN_PLAYER, state.open == MeloWidgetPolicy.Open.PLAYER)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                views.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                fun control(view: Int, action: MeloWidgetPolicy.Action, enabled: Boolean, label: Int) {
                    views.setBoolean(view, "setEnabled", enabled)
                    views.setFloat(view, "setAlpha", if (enabled) 1f else .28f)
                    views.setContentDescription(view, context.getString(label))
                    val pending = if (!enabled) null else PendingIntent.getBroadcast(context, id,
                        Intent(context, MeloNowPlayingWidgetProvider::class.java)
                            .setAction(ACTION_PREFIX + action.name)
                            .setData(Uri.parse(MeloWidgetPolicy.intentIdentity(id, action, token)))
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            .putExtra(EXTRA_TOKEN, token),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    // A full render must explicitly clear a formerly enabled click.
                    views.setOnClickPendingIntent(view, pending)
                }
                control(R.id.widget_previous, MeloWidgetPolicy.Action.PREVIOUS_ITEM, state.previous, R.string.widget_previous)
                control(R.id.widget_next, MeloWidgetPolicy.Action.NEXT_ITEM, state.next, R.string.widget_next)
                val pause = state.center == MeloWidgetPolicy.Center.PAUSE
                views.setImageViewResource(R.id.widget_play_pause, if (pause) R.drawable.ic_pause else R.drawable.ic_play_arrow)
                control(R.id.widget_play_pause, if (pause) MeloWidgetPolicy.Action.PAUSE else MeloWidgetPolicy.Action.PLAY,
                    state.center != MeloWidgetPolicy.Center.NONE, when (state.center) {
                        MeloWidgetPolicy.Center.PAUSE -> R.string.widget_pause
                        MeloWidgetPolicy.Center.REPLAY -> R.string.widget_replay
                        else -> R.string.widget_play
                    })
                manager.updateAppWidget(id, views)
            }
        }
    }
}
