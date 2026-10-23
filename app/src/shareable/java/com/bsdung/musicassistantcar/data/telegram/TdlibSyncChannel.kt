package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import java.io.File

/**
 * E96 P3 consent. Everything is OFF until a person turns it on, per Telegram account, and a build-time kill switch
 * ([TelegramPlaylistFlags.SYNC_ENABLED]) sits above it. Three separate permissions:
 *  (a) [enabled]            - sync may run at all (needs the cloud-storage warning to have been shown and accepted),
 *  (b) [mayCreateChannel]   - a NEW private channel may be created,
 *  (c) [mayTransmitInitial] - playlist metadata may be written to the channel.
 * The consent screen itself is P4; here there is only the model, the gate and a store. A file that cannot be read or
 * does not parse means everything OFF.
 */
internal data class SyncConsent(
    val enabled: Boolean = false,
    val mayCreateChannel: Boolean = false,
    val mayTransmitInitial: Boolean = false,
    /** The version of the warning ("Telegram cloud storage, NOT end-to-end encrypted; playlist metadata") that was accepted. 0 = never. */
    val acceptedWarningVersion: Int = 0,
) {
    companion object {
        const val WARNING_VERSION = 1
    }

    val warningAccepted: Boolean get() = acceptedWarningVersion == WARNING_VERSION
}

internal interface SyncConsentStore {
    fun load(account: TelegramUserId): SyncConsent
    fun save(account: TelegramUserId, consent: SyncConsent): Boolean
}

internal class FileSyncConsentStore(private val root: File, private val io: PlaylistFileIo = AtomicPlaylistFileIo) : SyncConsentStore {
    private fun file(account: TelegramUserId) =
        File(File(root, "tg_sync_consent_v1"), TelegramAccountStoragePolicy.directoryName(account) + ".txt")

    override fun load(account: TelegramUserId): SyncConsent = try {
        val values = io.read(file(account)).toString(Charsets.UTF_8).lines().drop(1).mapNotNull {
            val p = it.split(' ')
            if (p.size == 2) p[0] to p[1] else null
        }.toMap()
        fun flag(key: String) = values[key] == "1"
        SyncConsent(flag("enabled"), flag("create"), flag("transmit"), values["warning"]?.toIntOrNull() ?: 0)
    } catch (_: Exception) {
        SyncConsent()
    }

    override fun save(account: TelegramUserId, consent: SyncConsent): Boolean = try {
        val target = file(account)
        target.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
        val text = "mvconsent v1\nenabled ${if (consent.enabled) 1 else 0}\ncreate ${if (consent.mayCreateChannel) 1 else 0}\n" +
            "transmit ${if (consent.mayTransmitInitial) 1 else 0}\nwarning ${consent.acceptedWarningVersion}\n"
        io.write(target, text.toByteArray(Charsets.UTF_8))
        true
    } catch (_: Exception) {
        false
    }
}

internal class InMemorySyncConsentStore : SyncConsentStore {
    private val map = HashMap<Long, SyncConsent>()
    @Synchronized override fun load(account: TelegramUserId): SyncConsent = map[account.value] ?: SyncConsent()
    @Synchronized override fun save(account: TelegramUserId, consent: SyncConsent): Boolean {
        map[account.value] = consent
        return true
    }
}

/** The moves a consent screen (P4) will make. Each one is explicit; nothing is implied by another except that revoking clears all. */
internal class SyncConsentActions(private val store: SyncConsentStore) {
    /** Needs the person to have SEEN and accepted the warning in the same action. */
    fun enable(account: TelegramUserId, warningShownAndAccepted: Boolean): Boolean {
        if (!warningShownAndAccepted) return false
        return store.save(account, store.load(account).copy(enabled = true, acceptedWarningVersion = SyncConsent.WARNING_VERSION))
    }

    fun allowChannelCreation(account: TelegramUserId): Boolean {
        val c = store.load(account)
        if (!c.enabled || !c.warningAccepted) return false
        return store.save(account, c.copy(mayCreateChannel = true))
    }

    fun allowInitialTransmit(account: TelegramUserId): Boolean {
        val c = store.load(account)
        if (!c.enabled || !c.warningAccepted) return false
        return store.save(account, c.copy(mayTransmitInitial = true))
    }

    /** Logout or "turn sync off": everything back to OFF. The channel and the stored playlist are not touched. */
    fun revoke(account: TelegramUserId): Boolean = store.save(account, SyncConsent())
}

internal enum class SyncDenied {
    BUILD_OFF, NOT_ENABLED, WARNING_NOT_ACCEPTED, CREATE_NOT_ALLOWED, TRANSMIT_NOT_ALLOWED,
    NOT_AUTHORIZED, NOT_OPEN, SINK_BUSY, BUSY,
}

internal class SyncGate(
    private val buildEnabled: Boolean = TelegramPlaylistFlags.SYNC_ENABLED,
    private val store: SyncConsentStore,
) {
    fun mayRun(account: TelegramUserId): SyncDenied? {
        if (!buildEnabled) return SyncDenied.BUILD_OFF
        val c = store.load(account)
        if (!c.enabled) return SyncDenied.NOT_ENABLED
        if (!c.warningAccepted) return SyncDenied.WARNING_NOT_ACCEPTED
        return null
    }

    fun mayCreate(account: TelegramUserId): SyncDenied? =
        mayRun(account) ?: if (store.load(account).mayCreateChannel) null else SyncDenied.CREATE_NOT_ALLOWED

    fun mayTransmit(account: TelegramUserId): SyncDenied? =
        mayRun(account) ?: if (store.load(account).mayTransmitInitial) null else SyncDenied.TRANSMIT_NOT_ALLOWED
}

/** What makes a Telegram chat THE sync channel of an account: a protocol marker in the description, not the title. */
internal object SyncChannelSpec {
    const val TITLE = "MeloVault Sync"

    fun markerOf(accountId: Long): String = "melovault-sync v1 ${PlaylistSyncProtocol.scopeOf(accountId)}"

    /** At most 255 characters (TDLib's limit). Says what the channel is and that it is NOT end-to-end encrypted. */
    fun descriptionOf(accountId: Long): String =
        markerOf(accountId) + "\nPrivate MeloVault playlist log in Telegram cloud (not end-to-end encrypted). Do not edit, delete or add members."
}

internal enum class UnsafeChannel { NOT_A_CHANNEL, PUBLIC, NOT_OWNER, HAS_OTHER_MEMBERS, AUTO_DELETE_ON, LINKED_CHAT }

internal sealed class ChannelDiscovery {
    object NotFound : ChannelDiscovery()
    data class Found(val chatId: Long) : ChannelDiscovery()
    /** More than one chat claims to be the sync channel: a person decides, nothing is chosen or created. */
    data class Ambiguous(val count: Int) : ChannelDiscovery()
    /** The chat carries the marker but is not a private channel of this account's own: not trusted. */
    data class Unsafe(val reason: UnsafeChannel) : ChannelDiscovery()
    data class Failed(val kind: SyncErrorKind, val retryAfterSeconds: Int = 0) : ChannelDiscovery()
}

internal sealed class CreateOutcome {
    data class Created(val chatId: Long) : CreateOutcome()
    /** A channel (or several) already exists: nothing is created. */
    data class Exists(val discovery: ChannelDiscovery) : CreateOutcome()
    /** TDLib made something that does not verify, or two devices created one at the same moment. Nothing is bound. */
    data class NeedsPerson(val discovery: ChannelDiscovery) : CreateOutcome()
    /** The request went out and no answer came: it may exist. Discover again later; NEVER create again automatically. */
    object Unknown : CreateOutcome()
    data class Failed(val kind: SyncErrorKind, val retryAfterSeconds: Int = 0) : CreateOutcome()
}

internal sealed class BindResult {
    object Bound : BindResult()
    /** The checkpoint already names a different channel: never replaced, never reset. */
    object ChannelConflict : BindResult()
    object NotVerified : BindResult()
    object Stale : BindResult()
    object NotSaved : BindResult()
}

/**
 * Finds, creates and verifies the private sync channel through TDLib. Every method is a blocking, bounded sequence of
 * requests for a worker thread. It never runs without being called with a gate decision already taken by
 * [TdlibPlaylistSyncService]; it creates a channel ONLY in [create], which refuses when anything is found.
 */
internal class TdlibChannelDirectory(
    private val hub: TdlibSyncHub,
    private val account: Long,
    private val epoch: Long,
    private val requestMs: Long = 20_000L,
) {
    private sealed class Verified {
        object NotOurs : Verified()
        object Ours : Verified()
        class Unsafe(val reason: UnsafeChannel) : Verified()
        class Failed(val failure: ChannelDiscovery.Failed) : Verified()
    }

    private fun failure(reply: TdlibSyncHub.Reply): ChannelDiscovery.Failed = when (reply) {
        is TdlibSyncHub.Reply.Err -> TdlibSyncErrors.kindOf(reply.code, reply.message).let { ChannelDiscovery.Failed(it.first, it.second) }
        TdlibSyncHub.Reply.Timeout -> ChannelDiscovery.Failed(SyncErrorKind.TIMEOUT)
        else -> ChannelDiscovery.Failed(hub.staleKind(account))
    }

    private fun call(type: String, configure: (JSONObject) -> Unit): TdlibSyncHub.Reply = hub.call(account, epoch, type, requestMs, configure)

    fun discover(): ChannelDiscovery {
        // Best effort: makes TDLib load its chat list. "Already loaded" comes back as an error and is fine.
        call("loadChats") { it.put("chat_list", JSONObject().put("@type", "chatListMain")).put("limit", 100) }
        val found = call("searchChats") { it.put("query", SyncChannelSpec.TITLE).put("limit", 50) }
        if (found !is TdlibSyncHub.Reply.Ok) return failure(found)
        val ids = found.json.optJSONArray("chat_ids") ?: return ChannelDiscovery.Failed(SyncErrorKind.NETWORK)
        var ours = 0
        var oursId = 0L
        var unsafe: UnsafeChannel? = null
        var unsafeCount = 0
        for (i in 0 until minOf(ids.length(), 50)) {
            when (val v = verify(ids.optLong(i))) {
                Verified.NotOurs -> Unit
                Verified.Ours -> { ours++; oursId = ids.optLong(i) }
                is Verified.Unsafe -> { unsafeCount++; unsafe = unsafe ?: v.reason }
                is Verified.Failed -> return v.failure
            }
        }
        return when {
            ours + unsafeCount >= 2 -> ChannelDiscovery.Ambiguous(ours + unsafeCount)
            ours == 1 -> ChannelDiscovery.Found(oursId)
            unsafeCount == 1 -> ChannelDiscovery.Unsafe(unsafe!!)
            else -> ChannelDiscovery.NotFound
        }
    }

    /** Is [chatId] a private channel that carries this account's marker, is owned by it and has nobody else in it? */
    private fun verify(chatId: Long): Verified {
        if (chatId == 0L) return Verified.NotOurs
        val chat = call("getChat") { it.put("chat_id", chatId) }
        if (chat !is TdlibSyncHub.Reply.Ok) return Verified.Failed(failure(chat))
        val type = chat.json.optJSONObject("type") ?: return Verified.NotOurs
        if (type.optString("@type") != "chatTypeSupergroup") return Verified.NotOurs // also: Saved Messages is a private chat
        val supergroupId = type.optLong("supergroup_id")
        val full = call("getSupergroupFullInfo") { it.put("supergroup_id", supergroupId) }
        if (full !is TdlibSyncHub.Reply.Ok) return Verified.Failed(failure(full))
        val firstLine = full.json.optString("description").substringBefore('\n')
        if (firstLine != SyncChannelSpec.markerOf(account)) return Verified.NotOurs // title alone is never trusted
        val group = call("getSupergroup") { it.put("supergroup_id", supergroupId) }
        if (group !is TdlibSyncHub.Reply.Ok) return Verified.Failed(failure(group))
        val g = group.json
        val usernames = g.optJSONObject("usernames")?.optJSONArray("active_usernames")
        return when {
            !type.optBoolean("is_channel") || !g.optBoolean("is_channel") -> Verified.Unsafe(UnsafeChannel.NOT_A_CHANNEL)
            (usernames != null && usernames.length() > 0) || g.optString("username").isNotEmpty() -> Verified.Unsafe(UnsafeChannel.PUBLIC)
            g.optJSONObject("status")?.optString("@type") != "chatMemberStatusCreator" -> Verified.Unsafe(UnsafeChannel.NOT_OWNER)
            maxOf(g.optInt("member_count"), full.json.optInt("member_count")) > 1 -> Verified.Unsafe(UnsafeChannel.HAS_OTHER_MEMBERS)
            chat.json.optInt("message_auto_delete_time") != 0 -> Verified.Unsafe(UnsafeChannel.AUTO_DELETE_ON)
            g.optBoolean("has_linked_chat") || full.json.optLong("linked_chat_id") != 0L -> Verified.Unsafe(UnsafeChannel.LINKED_CHAT)
            else -> Verified.Ours
        }
    }

    /** True only if [chatId] verifies as this account's channel right now. */
    fun isOurs(chatId: Long): Boolean = verify(chatId) == Verified.Ours

    /**
     * The ONLY place a channel is created: a private broadcast channel (is_channel = true, not a forum, no location,
     * zero auto-delete so the history never disappears). The caller must already have the person's consent. It
     * discovers first and refuses if anything is found.
     */
    fun create(): CreateOutcome {
        when (val before = discover()) {
            ChannelDiscovery.NotFound -> Unit
            is ChannelDiscovery.Failed -> return CreateOutcome.Failed(before.kind, before.retryAfterSeconds)
            else -> return CreateOutcome.Exists(before)
        }
        val reply = call("createNewSupergroupChat") {
            it.put("title", SyncChannelSpec.TITLE)
            it.put("is_forum", false)
            it.put("is_channel", true)
            it.put("description", SyncChannelSpec.descriptionOf(account))
            it.put("message_auto_delete_time", 0)
            it.put("for_import", false)
        }
        val chat = when (reply) {
            is TdlibSyncHub.Reply.Ok -> reply.json
            is TdlibSyncHub.Reply.Err -> return TdlibSyncErrors.kindOf(reply.code, reply.message).let { CreateOutcome.Failed(it.first, it.second) }
            else -> return CreateOutcome.Unknown
        }
        val chatId = chat.optLong("id")
        if (chat.optString("@type") != "chat" || chatId == 0L) return CreateOutcome.Unknown
        // Never trust what was just made: look again, and look for a second one (two devices at the same moment).
        return when (val after = discover()) {
            is ChannelDiscovery.Found -> if (after.chatId == chatId) CreateOutcome.Created(chatId) else CreateOutcome.NeedsPerson(after)
            else -> CreateOutcome.NeedsPerson(after)
        }
    }

    /** Persists the channel in the checkpoint only after it verifies. Never replaces another channel and never resets the cursor. */
    fun bind(host: PlaylistSyncHost, user: TelegramUserId, chatId: Long): BindResult {
        // Verified AND the only one: with a second claimant around, binding would pick a side silently.
        if (chatId == 0L || discover() != ChannelDiscovery.Found(chatId)) return BindResult.NotVerified
        val view = host.view(user) ?: return BindResult.Stale
        val current = view.state.checkpoint.channelId
        if (current == chatId) return BindResult.Bound
        if (current != 0L) return BindResult.ChannelConflict
        if (!hub.identityHolds(account, epoch)) return BindResult.Stale
        return when (host.update(user, view.epoch) { s ->
            if (s.checkpoint.channelId == 0L) s.copy(checkpoint = s.checkpoint.copy(channelId = chatId)) else s
        }) {
            is UpdateResult.Committed -> if (host.view(user)?.state?.checkpoint?.channelId == chatId) BindResult.Bound else BindResult.ChannelConflict
            UpdateResult.Stale, UpdateResult.NotOpen -> BindResult.Stale
            UpdateResult.Failed -> BindResult.NotSaved
        }
    }
}
