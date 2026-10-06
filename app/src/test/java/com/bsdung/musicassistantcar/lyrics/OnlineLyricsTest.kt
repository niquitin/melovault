package com.bsdung.musicassistantcar.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class OnlineLyricsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // 49. ANDROID UNIT TESTS — LRC PARSER
    // =========================================================================

    @Test
    fun `lrcParser parses standard mm ss xx format`() {
        val raw = """
            [00:12.34]Dòng đầu tiên
            [00:18.90]Dòng thứ hai
        """.trimIndent()
        val lines = LrcParser.parse(raw)
        assertEquals(2, lines.size)
        assertEquals(12_340L, lines[0].startMs)
        assertEquals("Dòng đầu tiên", lines[0].text)
        assertEquals(18_900L, lines[1].startMs)
        assertEquals("Dòng thứ hai", lines[1].text)
    }

    @Test
    fun `lrcParser parses mm ss xxx millisecond format`() {
        val raw = """
            [00:12.345]Millisecond line one
            [01:05.007]Millisecond line two
        """.trimIndent()
        val lines = LrcParser.parse(raw)
        assertEquals(2, lines.size)
        assertEquals(12_345L, lines[0].startMs)
        assertEquals("Millisecond line one", lines[0].text)
        assertEquals(65_007L, lines[1].startMs)
        assertEquals("Millisecond line two", lines[1].text)
    }

    @Test
    fun `lrcParser expands multiple timestamps on a single line`() {
        val raw = "[00:10.00][01:10.00]Repeated chorus line"
        val lines = LrcParser.parse(raw)
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].startMs)
        assertEquals("Repeated chorus line", lines[0].text)
        assertEquals(70_000L, lines[1].startMs)
        assertEquals("Repeated chorus line", lines[1].text)
    }

    @Test
    fun `lrcParser applies positive and negative offset tags and clamps to zero`() {
        val rawPositive = """
            [offset:+500]
            [00:01.00]Shifted forward
        """.trimIndent()
        val posLines = LrcParser.parse(rawPositive)
        assertEquals(1, posLines.size)
        assertEquals(1_500L, posLines[0].startMs)

        val rawNegative = """
            [offset:-300]
            [00:00.20]Clamped to zero
            [00:02.00]Shifted backward
        """.trimIndent()
        val negLines = LrcParser.parse(rawNegative)
        assertEquals(2, negLines.size)
        assertEquals(0L, negLines[0].startMs)
        assertEquals(1_700L, negLines[1].startMs)
    }

    @Test
    fun `lrcParser strips metadata tags and blank lines`() {
        val raw = """
            [ar:Sơn Tùng M-TP]
            [ti:Chúng Ta Của Hiện Tại]
            [al:Chúng Ta]
            [by:MeloVault]
            [length:05:01]

            [00:15.00]   
            [00:20.00]Lời bài hát thực sự
        """.trimIndent()
        val lines = LrcParser.parse(raw)
        assertEquals(1, lines.size)
        assertEquals(20_000L, lines[0].startMs)
        assertEquals("Lời bài hát thực sự", lines[0].text)
    }

    @Test
    fun `lrcParser ignores malformed lines without crashing`() {
        val raw = """
            not a timestamp line
            [99:99.99]Invalid seconds > 59
            [abc:def]Invalid tag
            [00:10.00]Valid line 1
            [00:bad]Broken timestamp
            [00:20.50]Valid line 2
        """.trimIndent()
        val lines = LrcParser.parse(raw)
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].startMs)
        assertEquals("Valid line 1", lines[0].text)
        assertEquals(20_500L, lines[1].startMs)
        assertEquals("Valid line 2", lines[1].text)
    }

    @Test
    fun `lrcParser sorts out of order lines and deduplicates identical timestamp and text`() {
        val raw = """
            [00:30.00]Line C
            [00:10.00]Line A
            [00:20.00]Line B
            [00:10.00]Line A
        """.trimIndent()
        val lines = LrcParser.parse(raw)
        assertEquals(3, lines.size)
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lines.map { it.startMs })
        assertEquals(listOf("Line A", "Line B", "Line C"), lines.map { it.text })
    }

    @Test
    fun `lrcParser preserves Vietnamese Unicode diacritics`() {
        val raw = "[00:05.25]Dòng thử nghiệm tiếng Việt có dấu đầy đủ"
        val lines = LrcParser.parse(raw)
        assertEquals(1, lines.size)
        assertEquals("Dòng thử nghiệm tiếng Việt có dấu đầy đủ", lines[0].text)
    }

    @Test
    fun `lrcParser returns empty list for empty or timestamp-less input`() {
        assertTrue(LrcParser.parse(null).isEmpty())
        assertTrue(LrcParser.parse("").isEmpty())
        assertTrue(LrcParser.parse("   \n  ").isEmpty())
        assertTrue(LrcParser.parse("Just plain lyrics\nWithout any timestamps").isEmpty())
    }

    // =========================================================================
    // 50. ANDROID UNIT TESTS — SYNCED POSITION LOOKUP
    // =========================================================================

    @Test
    fun `findActiveLineIndex handles before first, exact, between, after last, and rapid seeks`() {
        val lines = listOf(
            LyricsLine(startMs = 5_000L, text = "Line 0"),
            LyricsLine(startMs = 10_000L, text = "Line 1"),
            LyricsLine(startMs = 15_500L, text = "Line 2"),
            LyricsLine(startMs = 22_000L, text = "Line 3"),
        )

        // Position before first line -> -1
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 0L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 4_999L))

        // Exact timestamp match
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 5_000L))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 10_000L))
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 15_500L))
        assertEquals(3, LrcParser.findActiveLineIndex(lines, 22_000L))

        // Position between two lines -> earlier line active
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 7_500L))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 15_499L))
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 19_000L))

        // Position after last line -> last line active
        assertEquals(3, LrcParser.findActiveLineIndex(lines, 22_001L))
        assertEquals(3, LrcParser.findActiveLineIndex(lines, 180_000L))

        // Rapid seek forward/backward updates active index deterministically
        assertEquals(3, LrcParser.findActiveLineIndex(lines, 30_000L))
        assertEquals(0, LrcParser.findActiveLineIndex(lines, 5_100L))
        assertEquals(2, LrcParser.findActiveLineIndex(lines, 16_000L))
        assertEquals(-1, LrcParser.findActiveLineIndex(lines, 1_000L))
        assertEquals(1, LrcParser.findActiveLineIndex(lines, 12_000L))
    }

    // =========================================================================
    // 51. ANDROID UNIT TESTS — UI STATE / CONTROLLER
    // =========================================================================

    @Test
    fun `uiController transitions through Loading Synced Plain Instrumental Ambiguous NotFound TemporaryError`() {
        val cacheDir = tempFolder.newFolder("lyrics_ui_states")
        val cache = LyricsCache(cacheDir = cacheDir)
        var nextResult = LyricsLookupResult(status = LyricsStatus.NOT_FOUND)

        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ -> nextResult },
        )
        val seekTargets = mutableListOf<Long>()
        val controller = LyricsSessionController(
            repository = repo,
            onSeekRequested = { seekTargets.add(it) },
        )

        val meta = LyricsTrackMetadata(
            trackKey = "track:101",
            title = "Synthetic Track One",
            artist = "Synthetic Artist",
            album = "Single",
            durationMs = 260_000L,
        )

        // 1. Loading state shown during lookup
        val token = controller.startLookup(meta)
        assertTrue(controller.uiState is LyricsUiState.Loading)
        assertEquals("Synthetic Track One", (controller.uiState as LyricsUiState.Loading).title)

        // 2. Synced state renders parsed lines
        nextResult = LyricsLookupResult(
            status = LyricsStatus.SYNCED,
            syncedLyrics = "[00:05.00]Line A\n[00:10.00]Line B",
        )
        assertTrue(controller.applyResultIfCurrent(token, nextResult, currentPositionMs = 6_000L))
        val syncedState = controller.uiState as LyricsUiState.Synced
        assertEquals(2, syncedState.lines.size)
        assertEquals(0, syncedState.activeLineIndex)
        assertTrue(syncedState.autoFollow)

        // Manual scroll disables auto-follow; "Theo nhạc" re-enables auto-follow
        controller.onUserManualScroll()
        assertFalse(controller.autoFollow)
        assertFalse((controller.uiState as LyricsUiState.Synced).autoFollow)

        controller.onResumeAutoFollow(currentPositionMs = 12_000L)
        assertTrue(controller.autoFollow)
        assertTrue((controller.uiState as LyricsUiState.Synced).autoFollow)
        assertEquals(1, controller.activeLineIndex)
        assertEquals(1, controller.scrollPositionIndex)

        // Tap-to-seek passes target startMs
        controller.onLineTapped(syncedState.lines[0])
        assertEquals(listOf(5_000L), seekTargets)
        assertEquals(0, controller.activeLineIndex)

        // 3. Plain state
        val plainMeta = meta.copy(trackKey = "track:102", title = "Plain Song")
        nextResult = LyricsLookupResult(
            status = LyricsStatus.PLAIN,
            plainLyrics = "Lời bài hát dạng văn bản\nDòng thứ hai",
        )
        val plainState = controller.loadSynchronously(plainMeta) as LyricsUiState.Plain
        assertEquals("Lời bài hát dạng văn bản\nDòng thứ hai", plainState.text)

        // 4. Instrumental state
        val instMeta = meta.copy(trackKey = "track:103", title = "Instrumental Piece")
        nextResult = LyricsLookupResult(
            status = LyricsStatus.INSTRUMENTAL,
            instrumental = true,
        )
        val instState = controller.loadSynchronously(instMeta)
        assertTrue(instState is LyricsUiState.Instrumental)

        // 5. Ambiguous state renders candidate chooser
        val ambMeta = meta.copy(trackKey = "track:104", title = "Ambiguous Song")
        nextResult = LyricsLookupResult(
            status = LyricsStatus.AMBIGUOUS,
            candidates = listOf(
                LyricsCandidate(
                    provider = "lrclib",
                    recordId = "9001",
                    title = "Ambiguous Song (Live)",
                    artist = "Sơn Tùng M-TP",
                    hasSynced = true,
                    confidence = 0.82,
                ),
            ),
        )
        val ambState = controller.loadSynchronously(ambMeta) as LyricsUiState.Ambiguous
        assertEquals(1, ambState.candidates.size)
        assertEquals("9001", ambState.candidates[0].recordId)

        // 6. NotFound state
        val nfMeta = meta.copy(trackKey = "track:105", title = "Unknown Track XYZ")
        nextResult = LyricsLookupResult(status = LyricsStatus.NOT_FOUND)
        val nfState = controller.loadSynchronously(nfMeta)
        assertTrue(nfState is LyricsUiState.NotFound)

        // 7. TemporaryError state
        val errMeta = meta.copy(trackKey = "track:106", title = "Error Track")
        nextResult = LyricsLookupResult(
            status = LyricsStatus.TEMPORARY_ERROR,
            diagnostic = "timeout",
        )
        val errState = controller.loadSynchronously(errMeta) as LyricsUiState.TemporaryError
        assertEquals("Không thể tải lời bài hát", errState.message)
    }

    @Test
    fun `forceRefresh clears manual override and cached entry`() {
        val cacheDir = tempFolder.newFolder("lyrics_force_refresh")
        val cache = LyricsCache(cacheDir = cacheDir)
        val seenForceRefresh = mutableListOf<Boolean>()
        val seenPreferredRecordId = mutableListOf<String?>()

        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, forceRefresh, _, preferredRecordId ->
                seenForceRefresh.add(forceRefresh)
                seenPreferredRecordId.add(preferredRecordId)
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    providerRecordId = preferredRecordId ?: "auto_100",
                    syncedLyrics = "[00:01.00]Line from ${preferredRecordId ?: "auto"}",
                )
            },
        )

        val meta = LyricsTrackMetadata(
            trackKey = "track:200",
            title = "Chạy Ngay Đi",
            artist = "Sơn Tùng M-TP",
            album = "Single",
            durationMs = 248_000L,
        )

        // User chooses candidate "cand_777" -> persists manual override and caches result
        val chosen = repo.resolveLyrics(
            metadata = meta,
            forceRefresh = false,
            preferredProvider = "lrclib",
            preferredRecordId = "cand_777",
        )
        assertEquals("cand_777", chosen.providerRecordId)
        assertNotNull(cache.getManualOverride(repo.signatureFor(meta)))

        // Next normal load uses cache without network call
        val cachedLoad = repo.resolveLyrics(metadata = meta, forceRefresh = false)
        assertTrue(cachedLoad.fromCache)
        assertEquals("cand_777", cachedLoad.providerRecordId)
        assertEquals(1, seenForceRefresh.size)

        // "Tìm lại lời" (forceRefresh = true) clears manual override & cache and re-queries provider
        val refreshed = repo.resolveLyrics(metadata = meta, forceRefresh = true)
        assertEquals(2, seenForceRefresh.size)
        assertTrue(seenForceRefresh.last())
        assertNull(seenPreferredRecordId.last())
        assertNull(cache.getManualOverride(repo.signatureFor(meta)))
        assertEquals("auto_100", refreshed.providerRecordId)
    }

    // =========================================================================
    // 52. ANDROID UNIT TESTS — TRACK TRANSITION RACE GUARD
    // =========================================================================

    @Test
    fun `late response from Track A is discarded after transition to Track B`() {
        val cacheDir = tempFolder.newFolder("lyrics_race_guard")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(status = LyricsStatus.NOT_FOUND)
            },
        )
        val controller = LyricsSessionController(repository = repo)

        val trackA = LyricsTrackMetadata(
            trackKey = "track:1",
            title = "Track A",
            artist = "Artist A",
            durationMs = 200_000L,
        )
        val trackB = LyricsTrackMetadata(
            trackKey = "track:2",
            title = "Track B",
            artist = "Artist B",
            durationMs = 210_000L,
        )

        // 1. Track A lookup starts
        val tokenA = controller.startLookup(trackA)
        assertTrue(repo.isTokenCurrent(tokenA))

        // 2. Player transitions to Track B before Track A completes
        val tokenB = controller.startLookup(trackB)
        assertFalse(repo.isTokenCurrent(tokenA))
        assertTrue(repo.isTokenCurrent(tokenB))

        // 3. Track B completes
        val resultB = LyricsLookupResult(
            status = LyricsStatus.SYNCED,
            syncedLyrics = "[00:02.00]Lyrics for Track B",
        )
        assertTrue(controller.applyResultIfCurrent(tokenB, resultB, currentPositionMs = 2_500L))
        assertEquals("Lyrics for Track B", (controller.uiState as LyricsUiState.Synced).lines[0].text)

        // 4. Track A response arrives late -> must be discarded!
        val lateResultA = LyricsLookupResult(
            status = LyricsStatus.SYNCED,
            syncedLyrics = "[00:01.00]Stale Lyrics for Track A",
        )
        assertFalse(controller.applyResultIfCurrent(tokenA, lateResultA, currentPositionMs = 1_500L))

        // Track B state remains intact
        val finalState = controller.uiState as LyricsUiState.Synced
        assertEquals("Lyrics for Track B", finalState.lines[0].text)
    }

    // =========================================================================
    // 53. ANDROID UNIT TESTS — LOCAL LYRICS CACHE
    // =========================================================================

    @Test
    fun `metadata signature distinguishes two Intro tracks and unifies playback sources`() {
        val intro1 = LyricsCache.computeMetadataSignature(
            title = "Intro",
            artist = "Đen Vâu",
            album = "Show Của Đen",
            durationMs = 120_000L,
        )
        val intro2 = LyricsCache.computeMetadataSignature(
            title = "Intro",
            artist = "Ngọt",
            album = "Gieo",
            durationMs = 94_000L,
        )
        assertNotEquals(intro1, intro2)

        // Same track across REMOTE_STREAM (240_100 ms), OFFLINE_READY (240_000 ms), STREAM_CACHE (239_900 ms)
        val sigRemote = LyricsCache.computeMetadataSignature(
            title = "Mang Tiền Về Cho Mẹ (Official Video)",
            artist = "Đen Vâu",
            album = "Single",
            durationMs = 240_100L,
        )
        val sigOffline = LyricsCache.computeMetadataSignature(
            title = "Mang Tiền Về Cho Mẹ",
            artist = "Đen Vâu",
            album = "Single",
            durationMs = 240_000L,
        )
        val sigStreamCache = LyricsCache.computeMetadataSignature(
            title = "mang tiền về cho mẹ",
            artist = "Đen Vâu",
            album = "Single",
            durationMs = 239_900L,
        )
        assertEquals(sigRemote, sigOffline)
        assertEquals(sigOffline, sigStreamCache)
    }

    @Test
    fun `positive cache write and read avoids duplicate network call`() {
        val cacheDir = tempFolder.newFolder("lyrics_pos_cache")
        val nowMs = 1_700_000_000_000L
        val cache = LyricsCache(cacheDir = cacheDir, clockMs = { nowMs })
        val networkCalls = AtomicInteger(0)

        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                networkCalls.incrementAndGet()
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = "555",
                    confidence = 0.96,
                    syncedLyrics = "[00:04.00]Cached synced line",
                )
            },
        )

        val meta = LyricsTrackMetadata(
            trackKey = "track:555",
            title = "Bài Này Chill Phết",
            artist = "Đen",
            album = "Single",
            durationMs = 270_000L,
        )

        val first = repo.resolveLyrics(meta)
        assertEquals(1, networkCalls.get())
        assertFalse(first.fromCache)
        assertEquals(LyricsStatus.SYNCED, first.status)

        val second = repo.resolveLyrics(meta)
        assertEquals(1, networkCalls.get()) // No second network call!
        assertTrue(second.fromCache)
        assertEquals(LyricsStatus.SYNCED, second.status)
        assertEquals(1, second.syncedLines.size)
        assertEquals("Cached synced line", second.syncedLines[0].text)
    }

    @Test
    fun `negative cache TTL respected for 24 hours and expires afterwards`() {
        val cacheDir = tempFolder.newFolder("lyrics_neg_ttl")
        var currentTimeMs = 1_700_000_000_000L
        val cache = LyricsCache(cacheDir = cacheDir, clockMs = { currentTimeMs })
        val networkCalls = AtomicInteger(0)

        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                networkCalls.incrementAndGet()
                LyricsLookupResult(status = LyricsStatus.NOT_FOUND)
            },
        )

        val meta = LyricsTrackMetadata(
            trackKey = "track:404",
            title = "Rare Track No Lyrics",
            artist = "Indie Artist",
            durationMs = 180_000L,
        )

        // Initial miss -> writes negative cache
        val first = repo.resolveLyrics(meta)
        assertEquals(LyricsStatus.NOT_FOUND, first.status)
        assertEquals(1, networkCalls.get())

        // 12 hours later -> still within 24h TTL -> served from cache without network call
        currentTimeMs += 12L * 60L * 60L * 1000L
        val withinTtl = repo.resolveLyrics(meta)
        assertEquals(LyricsStatus.NOT_FOUND, withinTtl.status)
        assertTrue(withinTtl.fromCache)
        assertEquals(1, networkCalls.get())

        // 25 hours after initial write -> negative cache expired -> queries network again
        currentTimeMs += 13L * 60L * 60L * 1000L
        val afterExpiry = repo.resolveLyrics(meta)
        assertEquals(LyricsStatus.NOT_FOUND, afterExpiry.status)
        assertEquals(2, networkCalls.get())
    }

    @Test
    fun `temporaryError is never cached as NOT_FOUND`() {
        val cacheDir = tempFolder.newFolder("lyrics_temp_err")
        val cache = LyricsCache(cacheDir = cacheDir)
        var shouldFail = true
        val networkCalls = AtomicInteger(0)

        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                networkCalls.incrementAndGet()
                if (shouldFail) {
                    LyricsLookupResult(
                        status = LyricsStatus.TEMPORARY_ERROR,
                        diagnostic = "provider_503",
                    )
                } else {
                    LyricsLookupResult(
                        status = LyricsStatus.PLAIN,
                        plainLyrics = "Recovered plain lyrics",
                    )
                }
            },
        )

        val meta = LyricsTrackMetadata(
            trackKey = "track:503",
            title = "Transient Track",
            artist = "Artist",
            durationMs = 200_000L,
        )

        val failed = repo.resolveLyrics(meta)
        assertEquals(LyricsStatus.TEMPORARY_ERROR, failed.status)
        assertNull(cache.read(repo.signatureFor(meta)))

        shouldFail = false
        val recovered = repo.resolveLyrics(meta)
        assertEquals(LyricsStatus.PLAIN, recovered.status)
        assertEquals("Recovered plain lyrics", recovered.plainLyrics)
        assertEquals(2, networkCalls.get())
    }

    @Test
    fun `cache prunes oldest entries when maxEntries or maxTotalBytes exceeded and recovers from corrupt file`() {
        val cacheDir = tempFolder.newFolder("lyrics_prune")
        var clock = 1_700_000_000_000L
        val cache = LyricsCache(
            cacheDir = cacheDir,
            maxEntries = 3,
            maxTotalBytes = 1024L * 1024L,
            clockMs = { clock },
        )

        val sigs = (1..5).map { idx ->
            clock += 1_000L
            val sig = LyricsCache.computeMetadataSignature("Song $idx", "Artist", "Album", 200_000L)
            cache.write(
                sig,
                LyricsLookupResult(
                    status = LyricsStatus.PLAIN,
                    plainLyrics = "Lyrics for song $idx",
                ),
            )
            sig
        }

        // Only the 3 newest entries (songs 3, 4, 5) should remain; songs 1 and 2 pruned
        assertNull(cache.read(sigs[0]))
        assertNull(cache.read(sigs[1]))
        assertNotNull(cache.read(sigs[2]))
        assertNotNull(cache.read(sigs[3]))
        assertNotNull(cache.read(sigs[4]))

        // Corrupt a cache file and verify read() deletes it cleanly and returns null without throwing
        val corruptSig = sigs[2]
        val corruptFile = File(cacheDir, "$corruptSig.json")
        corruptFile.writeText("{corrupt_json_payload", Charsets.UTF_8)
        assertNull(cache.read(corruptSig))
        assertFalse(corruptFile.exists())
    }

    @Test
    fun `offline cached display serves soft-expired positive cache when network is unavailable`() {
        val cacheDir = tempFolder.newFolder("lyrics_offline_soft_expired")
        var currentTimeMs = 1_700_000_000_000L
        val cache = LyricsCache(cacheDir = cacheDir, clockMs = { currentTimeMs })
        var networkOffline = false
        val networkCalls = AtomicInteger(0)

        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                networkCalls.incrementAndGet()
                if (networkOffline) {
                    LyricsLookupResult(status = LyricsStatus.TEMPORARY_ERROR, diagnostic = "offline")
                } else {
                    LyricsLookupResult(
                        status = LyricsStatus.SYNCED,
                        provider = "lrclib",
                        providerRecordId = "700",
                        confidence = 0.95,
                        syncedLyrics = "[00:03.00]Line A\n[00:08.00]Line B",
                    )
                }
            },
        )

        val meta = LyricsTrackMetadata(
            trackKey = "track:700",
            title = "Offline Track",
            artist = "Artist",
            durationMs = 200_000L,
        )

        val initial = repo.resolveLyrics(meta)
        assertEquals(LyricsStatus.SYNCED, initial.status)
        assertEquals(1, networkCalls.get())

        // Advance clock by 35 days (> 30d soft TTL) while device is offline
        currentTimeMs += 35L * 24L * 60L * 60L * 1000L
        networkOffline = true

        val offlineResult = repo.resolveLyrics(meta)
        assertEquals(LyricsStatus.SYNCED, offlineResult.status)
        assertTrue(offlineResult.fromCache)
        assertEquals(2, offlineResult.syncedLines.size)
        assertEquals("Line A", offlineResult.syncedLines[0].text)
    }

    @Test
    fun `metadata change invalidates manual override and path traversal is impossible`() {
        val cacheDir = tempFolder.newFolder("lyrics_override_and_traversal")
        val cache = LyricsCache(cacheDir = cacheDir)

        val metaV1 = LyricsTrackMetadata(
            trackKey = "track:800",
            title = "Song Title V1",
            artist = "Artist",
            durationMs = 200_000L,
        )
        val metaV2 = metaV1.copy(title = "Song Title V2")

        val sigV1 = LyricsCache.computeMetadataSignature(metaV1.title, metaV1.artist, metaV1.album, metaV1.durationMs)
        val sigV2 = LyricsCache.computeMetadataSignature(metaV2.title, metaV2.artist, metaV2.album, metaV2.durationMs)
        assertNotEquals(sigV1, sigV2)

        cache.saveManualOverride(sigV1, "lrclib", "999")
        assertNotNull(cache.getManualOverride(sigV1))
        assertNull(cache.getManualOverride(sigV2))

        // Attempt path traversal with malicious signature string
        val maliciousSig = "../../outside_secret_file"
        cache.write(
            maliciousSig,
            LyricsLookupResult(status = LyricsStatus.PLAIN, plainLyrics = "Line A"),
        )
        val createdFiles = cacheDir.listFiles()?.map { it.name }.orEmpty()
        assertEquals(2, createdFiles.size) // <sigV1>.override.json + <sha256(maliciousSig)>.json
        assertTrue(createdFiles.all { it.matches(Regex("^[0-9a-f]{64}(\\.override)?\\.json$")) })
        assertFalse(File(cacheDir.parentFile, "outside_secret_file.json").exists())
    }

    // =========================================================================
    // 54. PLAYBACK REGRESSION / ISOLATION TESTS
    // =========================================================================

    @Test
    fun `lyrics open close and lookup do not mutate track audio source or playback state`() {
        val cacheDir = tempFolder.newFolder("lyrics_playback_isolation")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    syncedLyrics = "[00:01.00]Line A\n[00:05.00]Line B",
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)

        val originalStreamUrl = "http://127.0.0.1:8790/car/stream/11200"
        val originalSourceType = "OFFLINE_READY"
        var isPlaying = true
        var playbackPositionMs = 5_200L

        val meta = LyricsTrackMetadata(
            trackKey = "11200",
            title = "Track 11200",
            artist = "Artist 11200",
            durationMs = 240_000L,
        )

        // Open lyrics, resolve, update position, close lyrics
        controller.loadSynchronously(meta, currentPositionMs = playbackPositionMs)
        assertEquals(1, controller.activeLineIndex)
        playbackPositionMs = 1_500L
        controller.onPositionUpdated(playbackPositionMs)
        assertEquals(0, controller.activeLineIndex)

        // Verify audio source and playback flags remain untouched
        assertEquals("http://127.0.0.1:8790/car/stream/11200", originalStreamUrl)
        assertEquals("OFFLINE_READY", originalSourceType)
        assertTrue(isPlaying)
        assertEquals(1_500L, playbackPositionMs)
    }
}

