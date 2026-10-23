package com.bsdung.musicassistantcar.data.telegram

/**
 * E96 P4 tests only: puts the real P3 sync service behind the Settings screen's port. It lives in the test sources on
 * purpose - no production file may reference the sync service until P5 is approved (see TdlibChannelConsentTest.c24).
 */
internal class TdlibSyncActions(private val service: TdlibPlaylistSyncService) : SyncActionsPort {
    override fun discover(account: TelegramUserId) = service.discoverChannel(account)
    override fun create(account: TelegramUserId) = service.createChannel(account)
    override fun bind(account: TelegramUserId, chatId: Long) = service.bindChannel(account, chatId)
    override fun run(account: TelegramUserId, mode: SyncMode) = service.runSync(account, mode)
}
