package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

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

    /** Strict: anything that is not exactly a file this class wrote means everything OFF (never a partial grant). */
    override fun load(account: TelegramUserId): SyncConsent = try {
        parse(io.read(file(account)))
    } catch (_: Exception) {
        SyncConsent()
    }

    private fun parse(bytes: ByteArray): SyncConsent {
        val off = SyncConsent()
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return off
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            return off
        }
        // The last line must be complete (a cut-off file does not end with a newline), and there is exactly one of each line.
        if (!text.endsWith("\n")) return off
        val lines = text.dropLast(1).split('\n')
        if (lines.size != 1 + KEYS.size || lines[0] != HEADER) return off
        val values = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val space = line.indexOf(' ')
            if (space <= 0) return off
            val key = line.substring(0, space)
            if (key !in KEYS || values.put(key, line.substring(space + 1)) != null) return off
        }
        fun flag(key: String): Boolean? = when (values[key]) { "0" -> false; "1" -> true; else -> null }
        val enabled = flag("enabled") ?: return off
        val create = flag("create") ?: return off
        val transmit = flag("transmit") ?: return off
        val warning = values["warning"]?.takeIf { WARNING.matches(it) }?.toIntOrNull() ?: return off
        if (warning > SyncConsent.WARNING_VERSION) return off // a version this build does not know
        // The actions never grant (b) or (c) without (a): a file that does was not written by them.
        if (!enabled && (create || transmit)) return off
        return SyncConsent(enabled, create, transmit, warning)
    }

    private companion object {
        const val HEADER = "mvconsent v1"
        const val MAX_BYTES = 256
        val KEYS = setOf("enabled", "create", "transmit", "warning")
        val WARNING = Regex("0|[1-9][0-9]{0,8}")
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
    /** The build does not unlock channel creation (see [TelegramPlaylistFlags.SYNC_CREATE_ALLOWED]): find and bind only. */
    CREATE_LOCKED,
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

internal enum class UnsafeChannel { NOT_A_CHANNEL, PUBLIC, NOT_OWNER, HAS_OTHER_MEMBERS, AUTO_DELETE_ON, LINKED_CHAT, MARKER_MISSING }

internal enum class IncompleteReason { LOAD_NOT_FINISHED, TOO_MANY_CHATS, BUDGET }

/** Hard bounds of the exhaustive scan. Nothing here ever loops without end. */
internal data class DiscoveryLimits(
    val maxLoadRounds: Int = 60,
    val loadChunk: Int = 100,
    /** getChats is asked for this many; an answer that FILLS it may have been cut, so it proves nothing. */
    val getChatsLimit: Int = 1000,
    val maxRequests: Int = 3000,
)

internal sealed class ChannelDiscovery {
    object NotFound : ChannelDiscovery()
    data class Found(val chatId: Long) : ChannelDiscovery()
    /** More than one chat claims to be the sync channel: a person decides, nothing is chosen or created. */
    data class Ambiguous(val count: Int) : ChannelDiscovery()
    /** The chat carries the marker but is not a private channel of this account's own: not trusted. */
    data class Unsafe(val reason: UnsafeChannel) : ChannelDiscovery()
    /** The scan could not PROVE the absence (or uniqueness): never treated as NotFound, never a reason to create. */
    data class Incomplete(val reason: IncompleteReason) : ChannelDiscovery()
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
    /** Discovery could not prove that no channel exists: nothing is created. */
    data class DiscoveryIncomplete(val discovery: ChannelDiscovery.Incomplete) : CreateOutcome()
    /** The checkpoint already names a channel: never create another. */
    data class AlreadyBound(val chatId: Long) : CreateOutcome()
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
 * [TdlibPlaylistSyncService]; it creates a channel ONLY in [create], which refuses unless absence was PROVEN.
 *
 * Discovery (R01). `searchChats` is an offline TITLE search among chats that are already known, so it can say nothing
 * about a channel that was renamed, archived or not loaded yet. Absence is therefore proven only by an exhaustive scan:
 *  1. for the main list AND the archive: `loadChats` until TDLib answers 404 ("all chats are loaded") - bounded by
 *     [DiscoveryLimits.maxLoadRounds], else [IncompleteReason.LOAD_NOT_FINISHED];
 *  2. `getChats` for the list; an answer that fills the asked limit may have been cut: [IncompleteReason.TOO_MANY_CHATS];
 *  3. every supergroup the account owns or administers is read in full and judged by the protocol marker in its
 *     description (never the title); chats merely followed are skipped without a request;
 *  4. a total request budget, else [IncompleteReason.BUDGET].
 * Any error, timeout or limit means "unknown", never "not found". With a bound chat id the chat is verified directly
 * and nothing is scanned. [ChannelDiscovery.NotFound] is returned only when every step above completed.
 */
internal class TdlibChannelDirectory(
    private val hub: TdlibSyncHub,
    private val account: Long,
    private val epoch: Long,
    private val requestMs: Long = 20_000L,
    private val limits: DiscoveryLimits = DiscoveryLimits(),
) {
    private sealed class Verified {
        object NotOurs : Verified()
        object Ours : Verified()
        class Unsafe(val reason: UnsafeChannel) : Verified()
        class Failed(val failure: ChannelDiscovery.Failed) : Verified()
    }

    private class BudgetSpent : RuntimeException()

    private var used = 0

    private fun failure(reply: TdlibSyncHub.Reply): ChannelDiscovery.Failed = when (reply) {
        is TdlibSyncHub.Reply.Err -> TdlibSyncErrors.kindOf(reply.code, reply.message).let { ChannelDiscovery.Failed(it.first, it.second) }
        TdlibSyncHub.Reply.Timeout -> ChannelDiscovery.Failed(SyncErrorKind.TIMEOUT)
        else -> ChannelDiscovery.Failed(hub.staleKind(account))
    }

    private fun call(type: String, configure: (JSONObject) -> Unit): TdlibSyncHub.Reply {
        if (used >= limits.maxRequests) throw BudgetSpent()
        used++
        return hub.call(account, epoch, type, requestMs, configure)
    }

    /**
     * [boundChatId] is the chat the checkpoint already names (0 = none): it is verified directly. Otherwise the chat
     * lists are scanned exhaustively. See the class comment for what each result means.
     */
    fun discover(boundChatId: Long = 0L): ChannelDiscovery {
        used = 0
        return try {
            if (boundChatId != 0L) discoverBound(boundChatId) else scanEverything()
        } catch (_: BudgetSpent) {
            ChannelDiscovery.Incomplete(IncompleteReason.BUDGET)
        }
    }

    private fun discoverBound(chatId: Long): ChannelDiscovery = when (val v = verify(chatId, bound = true)) {
        Verified.Ours -> ChannelDiscovery.Found(chatId)
        // A bound chat that does not verify is a problem to show a person, never "absent".
        Verified.NotOurs -> ChannelDiscovery.Unsafe(UnsafeChannel.MARKER_MISSING)
        is Verified.Unsafe -> ChannelDiscovery.Unsafe(v.reason)
        is Verified.Failed -> v.failure
    }

    private fun scanEverything(): ChannelDiscovery {
        val chats = LinkedHashSet<Long>()
        for (list in listOf("chatListMain", "chatListArchive")) {
            var rounds = 0
            while (true) {
                if (rounds++ >= limits.maxLoadRounds) return ChannelDiscovery.Incomplete(IncompleteReason.LOAD_NOT_FINISHED)
                val loaded = call("loadChats") { it.put("chat_list", JSONObject().put("@type", list)).put("limit", limits.loadChunk) }
                if (loaded is TdlibSyncHub.Reply.Err && loaded.code == 404) break // TDLib: every chat of this list is loaded
                if (loaded !is TdlibSyncHub.Reply.Ok) return failure(loaded)
            }
            val listed = call("getChats") { it.put("chat_list", JSONObject().put("@type", list)).put("limit", limits.getChatsLimit) }
            if (listed !is TdlibSyncHub.Reply.Ok) return failure(listed)
            val ids = listed.json.optJSONArray("chat_ids") ?: return ChannelDiscovery.Failed(SyncErrorKind.NETWORK)
            // An answer that fills the limit may have been cut: it proves nothing about the rest.
            if (ids.length() >= limits.getChatsLimit) return ChannelDiscovery.Incomplete(IncompleteReason.TOO_MANY_CHATS)
            for (i in 0 until ids.length()) chats.add(ids.optLong(i))
        }
        var ours = 0
        var oursId = 0L
        var unsafe: UnsafeChannel? = null
        var unsafeCount = 0
        for (id in chats) {
            when (val v = verify(id, bound = false)) {
                Verified.NotOurs -> Unit
                Verified.Ours -> { ours++; oursId = id }
                is Verified.Unsafe -> { unsafeCount++; unsafe = unsafe ?: v.reason }
                is Verified.Failed -> return v.failure
            }
        }
        return when {
            ours + unsafeCount >= 2 -> ChannelDiscovery.Ambiguous(ours + unsafeCount)
            ours == 1 -> ChannelDiscovery.Found(oursId)
            unsafeCount == 1 -> ChannelDiscovery.Unsafe(unsafe!!)
            else -> ChannelDiscovery.NotFound // every list loaded to its end, every candidate read: PROVEN absent
        }
    }

    /**
     * Is [chatId] a private channel that carries this account's marker, is owned by it and has nobody else in it?
     * In a scan ([bound] false) chats that cannot be the account's own channel are skipped cheaply: not a supergroup, or
     * one the account neither owns nor administers. For the bound chat the same facts are reported as problems.
     */
    private fun verify(chatId: Long, bound: Boolean): Verified {
        if (chatId == 0L) return if (bound) Verified.Unsafe(UnsafeChannel.NOT_A_CHANNEL) else Verified.NotOurs
        val notOurs: Verified = if (bound) Verified.Unsafe(UnsafeChannel.MARKER_MISSING) else Verified.NotOurs
        val chat = call("getChat") { it.put("chat_id", chatId) }
        if (chat !is TdlibSyncHub.Reply.Ok) return Verified.Failed(failure(chat))
        val type = chat.json.optJSONObject("type")
        if (type?.optString("@type") != "chatTypeSupergroup") { // also: Saved Messages is a private chat
            return if (bound) Verified.Unsafe(UnsafeChannel.NOT_A_CHANNEL) else Verified.NotOurs
        }
        val supergroupId = type.optLong("supergroup_id")
        val group = call("getSupergroup") { it.put("supergroup_id", supergroupId) }
        if (group !is TdlibSyncHub.Reply.Ok) return Verified.Failed(failure(group))
        val g = group.json
        val status = g.optJSONObject("status")?.optString("@type")
        if (status != "chatMemberStatusCreator" && status != "chatMemberStatusAdministrator") {
            return if (bound) Verified.Unsafe(UnsafeChannel.NOT_OWNER) else Verified.NotOurs
        }
        val full = call("getSupergroupFullInfo") { it.put("supergroup_id", supergroupId) }
        if (full !is TdlibSyncHub.Reply.Ok) return Verified.Failed(failure(full))
        val firstLine = full.json.optString("description").substringBefore('\n')
        if (firstLine != SyncChannelSpec.markerOf(account)) return notOurs // the title is never trusted, nor needed
        val usernames = g.optJSONObject("usernames")?.optJSONArray("active_usernames")
        return when {
            !type.optBoolean("is_channel") || !g.optBoolean("is_channel") -> Verified.Unsafe(UnsafeChannel.NOT_A_CHANNEL)
            (usernames != null && usernames.length() > 0) || g.optString("username").isNotEmpty() -> Verified.Unsafe(UnsafeChannel.PUBLIC)
            status != "chatMemberStatusCreator" -> Verified.Unsafe(UnsafeChannel.NOT_OWNER)
            maxOf(g.optInt("member_count"), full.json.optInt("member_count")) > 1 -> Verified.Unsafe(UnsafeChannel.HAS_OTHER_MEMBERS)
            chat.json.optInt("message_auto_delete_time") != 0 -> Verified.Unsafe(UnsafeChannel.AUTO_DELETE_ON)
            g.optBoolean("has_linked_chat") || full.json.optLong("linked_chat_id") != 0L -> Verified.Unsafe(UnsafeChannel.LINKED_CHAT)
            else -> Verified.Ours
        }
    }

    /**
     * The ONLY place a channel is created: a private broadcast channel (is_channel = true, not a forum, no location,
     * zero auto-delete so the history never disappears). The caller must already have the person's consent. It
     * refuses when the checkpoint names a channel, and otherwise scans exhaustively first and refuses unless absence
     * was PROVEN (found, ambiguous, unsafe, failed and incomplete all refuse).
     */
    fun create(boundChatId: Long = 0L): CreateOutcome {
        if (boundChatId != 0L) return CreateOutcome.AlreadyBound(boundChatId)
        when (val before = discover(0L)) {
            ChannelDiscovery.NotFound -> Unit
            is ChannelDiscovery.Failed -> return CreateOutcome.Failed(before.kind, before.retryAfterSeconds)
            is ChannelDiscovery.Incomplete -> return CreateOutcome.DiscoveryIncomplete(before)
            else -> return CreateOutcome.Exists(before)
        }
        used = 0
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
        return when (val after = discover(0L)) {
            is ChannelDiscovery.Found -> if (after.chatId == chatId) CreateOutcome.Created(chatId) else CreateOutcome.NeedsPerson(after)
            else -> CreateOutcome.NeedsPerson(after)
        }
    }

    /** Persists the channel in the checkpoint only after it verifies. Never replaces another channel and never resets the cursor. */
    fun bind(host: PlaylistSyncHost, user: TelegramUserId, chatId: Long): BindResult {
        if (chatId == 0L) return BindResult.NotVerified
        val view = host.view(user) ?: return BindResult.Stale
        val current = view.state.checkpoint.channelId
        if (current == chatId) return if (discover(chatId) == ChannelDiscovery.Found(chatId)) BindResult.Bound else BindResult.NotVerified
        if (current != 0L) return BindResult.ChannelConflict
        // Verified AND the only one: with a second claimant around, binding would pick a side silently.
        if (discover(0L) != ChannelDiscovery.Found(chatId)) return BindResult.NotVerified
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
