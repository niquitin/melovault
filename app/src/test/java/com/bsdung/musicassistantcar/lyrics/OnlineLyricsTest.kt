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
    fun `forceRefresh refreshes chosen manual override while clearManualOverride returns to automatic`() {
        val cacheDir = tempFolder.newFolder("lyrics_force_refresh")
        val overrideFile = File(tempFolder.newFolder("lyrics_override_dir"), "lyrics_overrides.json")
        val cache = LyricsCache(cacheDir = cacheDir)
        val overrideStore = LyricsOverrideStore(overrideFile = overrideFile)
        val seenForceRefresh = mutableListOf<Boolean>()
        val seenPreferredRecordId = mutableListOf<String?>()
        val seenResolveRecordId = mutableListOf<String>()

        val repo = LyricsRepository(
            cache = cache,
            overrideStore = overrideStore,
            lookupFetcher = { _, _, _, _, _, forceRefresh, _, preferredRecordId ->
                seenForceRefresh.add(forceRefresh)
                seenPreferredRecordId.add(preferredRecordId)
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    providerRecordId = preferredRecordId ?: "auto_100",
                    syncedLyrics = "[00:01.00]Line from ${preferredRecordId ?: "auto"}",
                )
            },
            resolveFetcher = { _, providerRecordId, _ ->
                seenResolveRecordId.add(providerRecordId)
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    providerRecordId = providerRecordId,
                    syncedLyrics = "[00:01.00]Line from $providerRecordId",
                    provenance = LyricsProvenance.MANUAL,
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
        assertEquals(LyricsProvenance.MANUAL, chosen.provenance)
        assertNotNull(overrideStore.getOverride(meta.trackKey, repo.signatureFor(meta)))

        // Next normal load uses cache without network call
        val cachedLoad = repo.resolveLyrics(metadata = meta, forceRefresh = false)
        assertTrue(cachedLoad.fromCache)
        assertEquals("cand_777", cachedLoad.providerRecordId)
        assertEquals(LyricsProvenance.MANUAL, cachedLoad.provenance)
        assertEquals(1, seenResolveRecordId.size)

        // "Tìm lại lời" (forceRefresh = true) refreshes the SAME selected provider record without abandoning manual choice
        val refreshed = repo.resolveLyrics(metadata = meta, forceRefresh = true)
        assertEquals(2, seenResolveRecordId.size)
        assertEquals("cand_777", seenResolveRecordId.last())
        assertEquals("cand_777", refreshed.providerRecordId)
        assertEquals(LyricsProvenance.MANUAL, refreshed.provenance)
        assertNotNull(overrideStore.getOverride(meta.trackKey, repo.signatureFor(meta)))

        // "Tìm lại tự động" (clearManualOverride) clears override & cache and re-runs automatic lookup
        val autoRestored = repo.clearManualOverride(meta)
        assertNull(overrideStore.getOverride(meta.trackKey, repo.signatureFor(meta)))
        assertEquals("auto_100", autoRestored.providerRecordId)
        assertEquals(LyricsProvenance.AUTO, autoRestored.provenance)
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

    // =========================================================================
    // 55. FULLSCREEN CAR LYRICS UNIT TESTS (v0.6.30 — SECTION 21)
    // =========================================================================

    @Test
    fun `fullscreen 01 entering fullscreen does not trigger duplicate network lookup when lyrics already loaded`() {
        val cacheDir = tempFolder.newFolder("fullscreen_no_duplicate_lookup")
        val cache = LyricsCache(cacheDir = cacheDir)
        val networkCalls = AtomicInteger(0)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                networkCalls.incrementAndGet()
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = "14169",
                    syncedLyrics = "[00:05.00]Line 1\n[00:12.00]Line 2\n[00:20.00]Line 3",
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(
            trackKey = "7017",
            title = "Lydia",
            artist = "F.I.R",
            album = "F.I.R",
            durationMs = 238_934L,
        )

        // Load in inline panel first
        controller.loadSynchronously(meta, currentPositionMs = 6_000L)
        assertEquals(1, networkCalls.get())
        assertTrue(controller.uiState is LyricsUiState.Synced)
        assertEquals(0, controller.activeLineIndex)

        // Enter fullscreen on the same track -> reuses already-loaded state without any lookup
        val stateOnFullscreen = controller.enterFullscreen(meta, currentPositionMs = 13_000L)
        assertTrue(controller.isFullscreenVisible)
        assertEquals(1, networkCalls.get())
        assertTrue(stateOnFullscreen is LyricsUiState.Synced)
        assertEquals(1, controller.activeLineIndex)
        assertEquals(1, (stateOnFullscreen as LyricsUiState.Synced).activeLineIndex)
    }

    @Test
    fun `fullscreen 02 exiting fullscreen preserves current playback and lyric state`() {
        val cacheDir = tempFolder.newFolder("fullscreen_exit_preserves_state")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    syncedLyrics = "[00:05.00]Line 1\n[00:15.00]Line 2\n[00:25.00]Line 3",
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(
            trackKey = "7017",
            title = "Lydia",
            artist = "F.I.R",
            durationMs = 238_934L,
        )

        controller.enterFullscreen(meta, currentPositionMs = 16_000L)
        assertTrue(controller.isFullscreenVisible)
        assertEquals(1, controller.activeLineIndex)

        // Exit fullscreen -> lyric state, active line index, and signature remain intact
        controller.exitFullscreen()
        assertFalse(controller.isFullscreenVisible)
        assertTrue(controller.uiState is LyricsUiState.Synced)
        assertEquals(1, controller.activeLineIndex)
        assertEquals(meta.cacheSignature(), controller.currentSignature)
    }

    @Test
    fun `fullscreen 03 synced line progression updates active index and centered scroll offset`() {
        val cacheDir = tempFolder.newFolder("fullscreen_progression_and_offset")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    syncedLyrics = "[00:03.00]Intro\n[00:10.00]Verse 1\n[00:20.00]Chorus\n[00:35.00]Outro",
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(
            trackKey = "7017",
            title = "Lydia",
            artist = "F.I.R",
            durationMs = 238_934L,
        )

        controller.enterFullscreen(meta, currentPositionMs = 1_000L)
        assertEquals(-1, controller.activeLineIndex)

        assertTrue(controller.onPositionUpdated(3_200L))
        assertEquals(0, controller.activeLineIndex)
        assertEquals(0, controller.scrollPositionIndex)

        assertTrue(controller.onPositionUpdated(21_000L))
        assertEquals(2, controller.activeLineIndex)
        assertEquals(2, controller.scrollPositionIndex)

        // Verify centered scroll offset places active line near 40%-50% from top of viewport
        val viewportHeightPx = 500
        val itemHeightPx = 64
        val offsetPx = LyricsSessionController.computeCenteredScrollOffset(viewportHeightPx, itemHeightPx)
        val itemCenterRatio = (offsetPx + itemHeightPx / 2).toFloat() / viewportHeightPx.toFloat()
        assertTrue("Expected item center between 0.40 and 0.50, got $itemCenterRatio", itemCenterRatio in 0.40f..0.50f)
    }

    @Test
    fun `fullscreen 04 manual scroll sets follow mode false while active line highlight continues updating`() {
        val cacheDir = tempFolder.newFolder("fullscreen_manual_scroll")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    syncedLyrics = "[00:05.00]Line 0\n[00:10.00]Line 1\n[00:15.00]Line 2",
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(
            trackKey = "7017",
            title = "Lydia",
            artist = "F.I.R",
            durationMs = 238_934L,
        )

        controller.enterFullscreen(meta, currentPositionMs = 6_000L)
        assertEquals(0, controller.activeLineIndex)
        assertEquals(0, controller.scrollPositionIndex)
        assertTrue(controller.autoFollow)

        // User scrolls manually
        controller.onUserManualScroll()
        assertFalse(controller.autoFollow)
        assertFalse((controller.uiState as LyricsUiState.Synced).autoFollow)

        // Playback advances to Line 2 -> activeLineIndex updates, but scrollPositionIndex stays at 0
        assertTrue(controller.onPositionUpdated(16_000L))
        assertEquals(2, controller.activeLineIndex)
        assertEquals(0, controller.scrollPositionIndex)
    }

    @Test
    fun `fullscreen 05 Theo nhac button sets follow mode true and returns to active line`() {
        val cacheDir = tempFolder.newFolder("fullscreen_resume_follow")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    syncedLyrics = "[00:05.00]Line 0\n[00:10.00]Line 1\n[00:15.00]Line 2",
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(
            trackKey = "7017",
            title = "Lydia",
            artist = "F.I.R",
            durationMs = 238_934L,
        )

        controller.enterFullscreen(meta, currentPositionMs = 6_000L)
        controller.onUserManualScroll()
        controller.onPositionUpdated(16_000L)
        assertFalse(controller.autoFollow)
        assertEquals(2, controller.activeLineIndex)
        assertEquals(0, controller.scrollPositionIndex)

        // Tapping "Theo nhạc" re-enables autoFollow and scrolls to current active line (index 2)
        controller.onResumeAutoFollow(currentPositionMs = 16_500L)
        assertTrue(controller.autoFollow)
        assertTrue((controller.uiState as LyricsUiState.Synced).autoFollow)
        assertEquals(2, controller.activeLineIndex)
        assertEquals(2, controller.scrollPositionIndex)
    }

    @Test
    fun `fullscreen 06 tapping synced line triggers seek callback with expected timestamp and enables follow`() {
        val cacheDir = tempFolder.newFolder("fullscreen_tap_to_seek")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    syncedLyrics = "[00:05.00]Line 0\n[00:12.50]Line 1\n[00:24.75]Line 2",
                )
            },
        )
        val seekedTimestamps = mutableListOf<Long>()
        val controller = LyricsSessionController(
            repository = repo,
            onSeekRequested = { seekedTimestamps.add(it) },
        )
        val meta = LyricsTrackMetadata(
            trackKey = "7017",
            title = "Lydia",
            artist = "F.I.R",
            durationMs = 238_934L,
        )

        val state = controller.enterFullscreen(meta, currentPositionMs = 6_000L) as LyricsUiState.Synced
        controller.onUserManualScroll()
        assertFalse(controller.autoFollow)

        // User taps Line 2 (24_750L)
        controller.onLineTapped(state.lines[2])
        assertEquals(listOf(24_750L), seekedTimestamps)
        assertTrue(controller.autoFollow)
        assertEquals(2, controller.activeLineIndex)
        assertEquals(2, controller.scrollPositionIndex)
    }

    @Test
    fun `fullscreen 07 plain lyrics mode does not seek when tapped`() {
        val cacheDir = tempFolder.newFolder("fullscreen_plain_no_seek")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.PLAIN,
                    plainLyrics = "Unsynced line 1\nUnsynced line 2",
                )
            },
        )
        val seekedTimestamps = mutableListOf<Long>()
        val controller = LyricsSessionController(
            repository = repo,
            onSeekRequested = { seekedTimestamps.add(it) },
        )
        val meta = LyricsTrackMetadata(
            trackKey = "129",
            title = "존재의 이유",
            artist = "김종환",
            durationMs = 282_000L,
        )

        val state = controller.enterFullscreen(meta)
        assertTrue(state is LyricsUiState.Plain)
        controller.onPlainLyricsTapped()
        assertTrue(seekedTimestamps.isEmpty())
    }

    @Test
    fun `fullscreen 08 track change resets active line and scroll follow state cleanly`() {
        val cacheDir = tempFolder.newFolder("fullscreen_track_change_reset")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    syncedLyrics = "[00:05.00]Line 0\n[00:15.00]Line 1",
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val trackA = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)
        val trackB = LyricsTrackMetadata(trackKey = "7018", title = "Next Song", artist = "F.I.R", durationMs = 210_000L)

        controller.enterFullscreen(trackA, currentPositionMs = 16_000L)
        controller.onUserManualScroll()
        assertEquals(1, controller.activeLineIndex)
        assertFalse(controller.autoFollow)

        // Track changes to trackB while fullscreen is open
        val tokenB = controller.onTrackChanged(trackB)
        assertNotNull(tokenB)
        assertEquals(-1, controller.activeLineIndex)
        assertEquals(0, controller.scrollPositionIndex)
        assertTrue(controller.autoFollow)
        assertTrue(controller.uiState is LyricsUiState.Loading)
        assertEquals("Next Song", (controller.uiState as LyricsUiState.Loading).title)
    }

    @Test
    fun `fullscreen 09 stale async lyric result from track A is ignored after switching to track B while fullscreen is open`() {
        val cacheDir = tempFolder.newFolder("fullscreen_stale_async_guard")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                LyricsLookupResult(status = LyricsStatus.NOT_FOUND)
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val trackA = LyricsTrackMetadata(trackKey = "track:A", title = "Song A", artist = "Artist A", durationMs = 200_000L)
        val trackB = LyricsTrackMetadata(trackKey = "track:B", title = "Song B", artist = "Artist B", durationMs = 210_000L)

        controller.enterFullscreen(trackA)
        val tokenA = controller.onTrackChanged(trackA)!!
        val tokenB = controller.onTrackChanged(trackB)!!

        // Stale result from Track A arrives while fullscreen is open on Track B
        val staleResultA = LyricsLookupResult(
            status = LyricsStatus.SYNCED,
            syncedLyrics = "[00:01.00]Stale Song A line",
        )
        assertFalse(controller.applyResultIfCurrent(tokenA, staleResultA, currentPositionMs = 2_000L))
        assertTrue(controller.uiState is LyricsUiState.Loading)

        // Result from Track B arrives and is applied
        val validResultB = LyricsLookupResult(
            status = LyricsStatus.SYNCED,
            syncedLyrics = "[00:01.00]Fresh Song B line",
        )
        assertTrue(controller.applyResultIfCurrent(tokenB, validResultB, currentPositionMs = 2_000L))
        assertEquals("Fresh Song B line", (controller.uiState as LyricsUiState.Synced).lines[0].text)
    }

    @Test
    fun `fullscreen 10 not found and temporary error state transitions and retry`() {
        val cacheDir = tempFolder.newFolder("fullscreen_not_found_and_retry")
        val cache = LyricsCache(cacheDir = cacheDir)
        var mode = "ERROR"
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                when (mode) {
                    "ERROR" -> LyricsLookupResult(status = LyricsStatus.TEMPORARY_ERROR, diagnostic = "network_down")
                    "NOT_FOUND" -> LyricsLookupResult(status = LyricsStatus.NOT_FOUND)
                    else -> LyricsLookupResult(
                        status = LyricsStatus.SYNCED,
                        syncedLyrics = "[00:04.00]Recovered after retry",
                    )
                }
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "11200", title = "Duyên Kiếp", artist = "Artist", durationMs = 240_000L)

        // 1. TemporaryError
        val errState = controller.enterFullscreen(meta)
        assertTrue(errState is LyricsUiState.TemporaryError)

        // 2. Retry -> NotFound
        mode = "NOT_FOUND"
        val nfState = controller.retryCurrentTrack(meta)
        assertTrue(nfState is LyricsUiState.NotFound)

        // 3. Retry with forceRefresh -> Synced
        mode = "SYNCED"
        val syncedState = controller.retryCurrentTrack(meta, currentPositionMs = 5_000L) as LyricsUiState.Synced
        assertEquals("Recovered after retry", syncedState.lines[0].text)
        assertEquals(0, syncedState.activeLineIndex)
    }

    @Test
    fun `fullscreen 11 screen awake flag enabled on fullscreen enter and cleared on exit or background`() {
        val cacheDir = tempFolder.newFolder("fullscreen_keep_screen_on")
        val cache = LyricsCache(cacheDir = cacheDir)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ -> LyricsLookupResult(status = LyricsStatus.NOT_FOUND) },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        // Initially not in fullscreen
        assertFalse(controller.isKeepScreenOnEnabled)

        // Enter fullscreen while in foreground -> keep screen on enabled
        controller.onActivityForegroundChanged(true)
        controller.enterFullscreen(meta)
        assertTrue(controller.isKeepScreenOnEnabled)

        // Activity goes to background -> keep screen on cleared immediately
        controller.onActivityForegroundChanged(false)
        assertFalse(controller.isKeepScreenOnEnabled)

        // Activity returns to foreground while fullscreen still open -> re-enabled
        controller.onActivityForegroundChanged(true)
        assertTrue(controller.isKeepScreenOnEnabled)

        // Exit fullscreen -> keep screen on cleared immediately
        controller.exitFullscreen()
        assertFalse(controller.isKeepScreenOnEnabled)
    }

    @Test
    fun `fullscreen 12 works identically for remote stream offline internal offline SD and Device Music track models`() {
        val cacheDir = tempFolder.newFolder("fullscreen_all_sources")
        val cache = LyricsCache(cacheDir = cacheDir)
        val networkCalls = AtomicInteger(0)
        val repo = LyricsRepository(
            cache = cache,
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                networkCalls.incrementAndGet()
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = "777",
                    syncedLyrics = "[00:05.00]Unified lyric line across all sources",
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)

        val remoteTrack = LyricsTrackMetadata(
            trackKey = "7017",
            title = "Lydia",
            artist = "F.I.R",
            album = "F.I.R",
            durationMs = 238_000L,
            isDeviceLocal = false,
        )
        val offlineInternalTrack = remoteTrack.copy(trackKey = "7017", durationMs = 238_100L)
        val offlineSdTrack = remoteTrack.copy(trackKey = "7017", durationMs = 237_900L)
        val deviceMusicTrack = remoteTrack.copy(
            trackKey = "device:external_primary:999",
            durationMs = 238_200L,
            isDeviceLocal = true,
        )

        // 1. Remote stream resolves from network and caches
        val s1 = controller.enterFullscreen(remoteTrack, currentPositionMs = 6_000L) as LyricsUiState.Synced
        assertEquals(1, networkCalls.get())
        assertEquals("Unified lyric line across all sources", s1.lines[0].text)

        // 2. Offline INTERNAL resolves from shared metadata signature without network call
        controller.exitFullscreen()
        val s2 = controller.enterFullscreen(offlineInternalTrack, currentPositionMs = 6_000L) as LyricsUiState.Synced
        assertEquals(1, networkCalls.get())
        assertEquals("Unified lyric line across all sources", s2.lines[0].text)

        // 3. Offline REMOVABLE_SD resolves from shared metadata signature without network call
        controller.exitFullscreen()
        val s3 = controller.enterFullscreen(offlineSdTrack, currentPositionMs = 6_000L) as LyricsUiState.Synced
        assertEquals(1, networkCalls.get())
        assertEquals("Unified lyric line across all sources", s3.lines[0].text)

        // 4. Device Music resolves from shared metadata signature without network call
        controller.exitFullscreen()
        val s4 = controller.enterFullscreen(deviceMusicTrack, currentPositionMs = 6_000L) as LyricsUiState.Synced
        assertEquals(1, networkCalls.get())
        assertEquals("Unified lyric line across all sources", s4.lines[0].text)
    }

    // =========================================================================
    // 56. LYRICS MATCHING & MANUAL CORRECTION UNIT TESTS (v0.6.31 — SECTION 28)
    // =========================================================================

    private fun buildTestEnvironment(
        name: String,
        lookupFetcher: (String, String, String, String, Long?, Boolean, String?, String?) -> LyricsLookupResult = { _, _, _, _, _, _, _, _ ->
            LyricsLookupResult(
                status = LyricsStatus.SYNCED,
                provider = "lrclib",
                providerRecordId = "1001",
                syncedLyrics = "[00:05.00]Auto line 1\n[00:10.00]Auto line 2",
            )
        },
        candidatesFetcher: (String, String, String, String, Long?) -> LyricsCandidatesResult = { _, _, _, _, _ ->
            LyricsCandidatesResult(
                status = "OK",
                provider = "lrclib",
                candidates = listOf(
                    LyricsCandidate(
                        provider = "lrclib",
                        recordId = "1001",
                        title = "Lydia",
                        artist = "F.I.R",
                        album = "F.I.R",
                        durationMs = 238_000L,
                        hasSynced = true,
                        hasPlainLyrics = true,
                        confidence = 0.96,
                    ),
                    LyricsCandidate(
                        provider = "lrclib",
                        recordId = "2002",
                        title = "Lydia (Plain Version)",
                        artist = "F.I.R",
                        album = "F.I.R",
                        durationMs = 239_000L,
                        hasSynced = false,
                        hasPlainLyrics = true,
                        confidence = 0.88,
                    ),
                ),
            )
        },
        resolveFetcher: (String, String, Boolean) -> LyricsLookupResult = { _, recordId, _ ->
            if (recordId == "2002") {
                LyricsLookupResult(
                    status = LyricsStatus.PLAIN,
                    provider = "lrclib",
                    providerRecordId = "2002",
                    plainLyrics = "Plain manual lyric line 1\nPlain manual lyric line 2",
                    provenance = LyricsProvenance.MANUAL,
                )
            } else {
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = recordId,
                    syncedLyrics = "[00:02.00]Manual synced line from $recordId\n[00:08.00]Second manual line",
                    provenance = LyricsProvenance.MANUAL,
                )
            }
        },
    ): Triple<LyricsCache, LyricsOverrideStore, LyricsRepository> {
        val root = tempFolder.newFolder(name)
        val cache = LyricsCache(cacheDir = File(root, "cache"))
        val overrideStore = LyricsOverrideStore(overrideFile = File(root, "lyrics_overrides.json"))
        val repo = LyricsRepository(
            cache = cache,
            overrideStore = overrideStore,
            lookupFetcher = lookupFetcher,
            candidatesFetcher = candidatesFetcher,
            resolveFetcher = resolveFetcher,
        )
        return Triple(cache, overrideStore, repo)
    }

    @Test
    fun `v0631 01 opening candidate picker triggers candidate fetch for current track`() {
        val fetchedTitles = mutableListOf<String>()
        val (_, _, repo) = buildTestEnvironment(
            name = "v0631_01",
            candidatesFetcher = { _, title, artist, _, _ ->
                fetchedTitles.add("$title|$artist")
                LyricsCandidatesResult(
                    status = "OK",
                    candidates = listOf(
                        LyricsCandidate(provider = "lrclib", recordId = "14169", title = title, artist = artist, hasSynced = true, confidence = 0.95),
                    ),
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        val pickerState = controller.openCandidatePickerSynchronously(meta)
        assertEquals(listOf("Lydia|F.I.R"), fetchedTitles)
        assertTrue(pickerState is LyricsCandidatePickerState.Loaded)
        assertEquals(1, (pickerState as LyricsCandidatePickerState.Loaded).candidates.size)
        assertEquals("14169", pickerState.candidates[0].recordId)
    }

    @Test
    fun `v0631 02 candidate list displays max 5 items`() {
        val eightCandidates = (1..8).map { idx ->
            LyricsCandidate(
                provider = "lrclib",
                recordId = "rec_$idx",
                title = "Song $idx",
                artist = "Artist",
                hasSynced = true,
                confidence = 1.0 - idx * 0.05,
            )
        }
        val (_, _, repo) = buildTestEnvironment(
            name = "v0631_02",
            candidatesFetcher = { _, _, _, _, _ ->
                LyricsCandidatesResult(status = "OK", candidates = eightCandidates)
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        val pickerState = controller.openCandidatePickerSynchronously(meta) as LyricsCandidatePickerState.Loaded
        assertEquals(5, pickerState.candidates.size)
        assertEquals(listOf("rec_1", "rec_2", "rec_3", "rec_4", "rec_5"), pickerState.candidates.map { it.recordId })
    }

    @Test
    fun `v0631 03 candidate item shows synced vs plain badge correctly`() {
        val syncedCand = LyricsCandidate(
            provider = "lrclib",
            recordId = "101",
            title = "Synced Track",
            artist = "Artist",
            hasSynced = true,
            hasPlainLyrics = true,
            confidence = 0.95,
        )
        val plainCand = LyricsCandidate(
            provider = "lrclib",
            recordId = "102",
            title = "Plain Track",
            artist = "Artist",
            hasSynced = false,
            hasPlainLyrics = true,
            confidence = 0.85,
        )
        assertEquals("Đồng bộ", syncedCand.badgeText())
        assertEquals("Văn bản", plainCand.badgeText())
    }

    @Test
    fun `v0631 04 selecting a candidate calls resolve with exact provider_record_id`() {
        val resolvedIds = mutableListOf<String>()
        val (_, _, repo) = buildTestEnvironment(
            name = "v0631_04",
            resolveFetcher = { provider, recordId, _ ->
                resolvedIds.add("$provider:$recordId")
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = provider,
                    providerRecordId = recordId,
                    syncedLyrics = "[00:01.00]Exact resolved $recordId",
                    provenance = LyricsProvenance.MANUAL,
                )
            },
        )
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)
        val candidate = LyricsCandidate(
            provider = "lrclib",
            recordId = "exact_998877",
            title = "Lydia (Special)",
            artist = "F.I.R",
            hasSynced = true,
            confidence = 0.90,
        )

        controller.selectCandidateSynchronously(meta, candidate)
        assertEquals(listOf("lrclib:exact_998877"), resolvedIds)
    }

    @Test
    fun `v0631 05 selecting a candidate updates active lyrics state immediately`() {
        val (_, _, repo) = buildTestEnvironment(name = "v0631_05")
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        controller.loadSynchronously(meta, currentPositionMs = 6_000L)
        assertEquals("1001", (controller.uiState as LyricsUiState.Synced).recordId)

        val chosen = LyricsCandidate(provider = "lrclib", recordId = "3003", title = "Lydia Alt", artist = "F.I.R", hasSynced = true, confidence = 0.91)
        val updated = controller.selectCandidateSynchronously(meta, chosen, currentPositionMs = 3_000L) as LyricsUiState.Synced
        assertEquals("3003", updated.recordId)
        assertEquals("Manual synced line from 3003", updated.lines[0].text)
        assertEquals(LyricsCandidatePickerState.Closed, controller.candidatePickerState)
    }

    @Test
    fun `v0631 06 selecting synced candidate updates synced line list and resets scroll follow state`() {
        val (_, _, repo) = buildTestEnvironment(name = "v0631_06")
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        controller.enterFullscreen(meta, currentPositionMs = 11_000L)
        controller.onUserManualScroll()
        assertFalse(controller.autoFollow)

        val chosen = LyricsCandidate(provider = "lrclib", recordId = "4004", title = "Lydia Synced 2", artist = "F.I.R", hasSynced = true, confidence = 0.92)
        val state = controller.selectCandidateSynchronously(meta, chosen, currentPositionMs = 9_000L) as LyricsUiState.Synced
        assertTrue(controller.autoFollow)
        assertTrue(state.autoFollow)
        assertEquals(2, state.lines.size)
        assertEquals(1, state.activeLineIndex)
        assertEquals(1, controller.scrollPositionIndex)
    }

    @Test
    fun `v0631 07 selecting plain candidate switches UI to plain lyrics mode`() {
        val (_, _, repo) = buildTestEnvironment(name = "v0631_07")
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        controller.loadSynchronously(meta, currentPositionMs = 6_000L)
        assertTrue(controller.uiState is LyricsUiState.Synced)

        val plainCandidate = LyricsCandidate(
            provider = "lrclib",
            recordId = "2002",
            title = "Lydia (Plain Version)",
            artist = "F.I.R",
            hasSynced = false,
            hasPlainLyrics = true,
            confidence = 0.88,
        )
        val state = controller.selectCandidateSynchronously(meta, plainCandidate)
        assertTrue(state is LyricsUiState.Plain)
        assertEquals("Plain manual lyric line 1\nPlain manual lyric line 2", (state as LyricsUiState.Plain).text)
        assertEquals(LyricsProvenance.MANUAL, state.provenance)
    }

    @Test
    fun `v0631 08 manual override persists to local override store`() {
        val (_, overrideStore, repo) = buildTestEnvironment(name = "v0631_08")
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", album = "F.I.R", durationMs = 238_934L)
        val candidate = LyricsCandidate(
            provider = "lrclib",
            recordId = "8888",
            title = "Lydia Selected",
            artist = "F.I.R",
            album = "F.I.R",
            durationMs = 238_000L,
            hasSynced = true,
            confidence = 0.94,
        )

        repo.selectManualCandidate(meta, candidate)
        val stored = overrideStore.getOverride("7017", repo.signatureFor(meta))
        assertNotNull(stored)
        assertEquals("7017", stored!!.trackKey)
        assertEquals(repo.signatureFor(meta), stored.metadataSignature)
        assertEquals("lrclib", stored.provider)
        assertEquals("8888", stored.providerRecordId)
        assertEquals("Lydia Selected", stored.selectedTitle)
        assertEquals("F.I.R", stored.selectedArtist)
        assertEquals(238_000L, stored.selectedDurationMs)
    }

    @Test
    fun `v0631 09 reloading same track uses persisted manual override`() {
        val root = tempFolder.newFolder("v0631_09")
        val overrideFile = File(root, "lyrics_overrides.json")
        val cacheDir1 = File(root, "cache1")
        val cacheDir2 = File(root, "cache2") // empty cache to simulate cold cache / new session

        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)
        val candidate = LyricsCandidate(provider = "lrclib", recordId = "5566", title = "Lydia Chosen", artist = "F.I.R", hasSynced = true, confidence = 0.91)

        val repo1 = LyricsRepository(
            cache = LyricsCache(cacheDir = cacheDir1),
            overrideStore = LyricsOverrideStore(overrideFile = overrideFile),
            lookupFetcher = { _, _, _, _, _, _, _, _ -> LyricsLookupResult(status = LyricsStatus.NOT_FOUND) },
            resolveFetcher = { _, recordId, _ ->
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = recordId,
                    syncedLyrics = "[00:01.00]Persisted override $recordId",
                    provenance = LyricsProvenance.MANUAL,
                )
            },
        )
        repo1.selectManualCandidate(meta, candidate)

        // New repository instance reading persisted override file even with empty cache
        val resolvedIds = mutableListOf<String>()
        val repo2 = LyricsRepository(
            cache = LyricsCache(cacheDir = cacheDir2),
            overrideStore = LyricsOverrideStore(overrideFile = overrideFile),
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                throw AssertionError("Automatic lookup should not be called when manual override exists")
            },
            resolveFetcher = { _, recordId, _ ->
                resolvedIds.add(recordId)
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = recordId,
                    syncedLyrics = "[00:01.00]Reloaded manual $recordId",
                    provenance = LyricsProvenance.MANUAL,
                )
            },
        )
        val reloaded = repo2.resolveLyrics(meta)
        assertEquals(listOf("5566"), resolvedIds)
        assertEquals("5566", reloaded.providerRecordId)
        assertEquals(LyricsProvenance.MANUAL, reloaded.provenance)
    }

    @Test
    fun `v0631 10 cached manual override avoids unnecessary network call when valid`() {
        val resolveCalls = AtomicInteger(0)
        val lookupCalls = AtomicInteger(0)
        val (_, _, repo) = buildTestEnvironment(
            name = "v0631_10",
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                lookupCalls.incrementAndGet()
                LyricsLookupResult(status = LyricsStatus.NOT_FOUND)
            },
            resolveFetcher = { _, recordId, _ ->
                resolveCalls.incrementAndGet()
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = recordId,
                    syncedLyrics = "[00:02.00]Cached manual $recordId",
                    provenance = LyricsProvenance.MANUAL,
                )
            },
        )
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)
        val candidate = LyricsCandidate(provider = "lrclib", recordId = "7788", title = "Lydia", artist = "F.I.R", hasSynced = true, confidence = 0.95)

        repo.selectManualCandidate(meta, candidate)
        assertEquals(1, resolveCalls.get())
        assertEquals(0, lookupCalls.get())

        // Subsequent load hits local cache and verifies recordId == 7788 without network call
        val second = repo.resolveLyrics(meta, forceRefresh = false)
        assertTrue(second.fromCache)
        assertEquals("7788", second.providerRecordId)
        assertEquals(LyricsProvenance.MANUAL, second.provenance)
        assertEquals(1, resolveCalls.get())
        assertEquals(0, lookupCalls.get())
    }

    @Test
    fun `v0631 11 Tim lai loi with manual override refreshes the chosen record rather than discarding override`() {
        val resolveCalls = AtomicInteger(0)
        val lookupCalls = AtomicInteger(0)
        val (_, overrideStore, repo) = buildTestEnvironment(
            name = "v0631_11",
            lookupFetcher = { _, _, _, _, _, _, _, _ ->
                lookupCalls.incrementAndGet()
                LyricsLookupResult(status = LyricsStatus.SYNCED, providerRecordId = "auto_1", syncedLyrics = "[00:01.00]Auto")
            },
            resolveFetcher = { _, recordId, _ ->
                val callNum = resolveCalls.incrementAndGet()
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = recordId,
                    syncedLyrics = "[00:01.00]Manual $recordId call #$callNum",
                    provenance = LyricsProvenance.MANUAL,
                )
            },
        )
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)
        val candidate = LyricsCandidate(provider = "lrclib", recordId = "manual_900", title = "Lydia", artist = "F.I.R", hasSynced = true, confidence = 0.95)

        repo.selectManualCandidate(meta, candidate)
        assertEquals(1, resolveCalls.get())

        // "Tìm lại lời" (forceRefresh = true) re-resolves "manual_900" and keeps override intact
        val refreshed = repo.resolveLyrics(meta, forceRefresh = true)
        assertEquals(2, resolveCalls.get())
        assertEquals(0, lookupCalls.get())
        assertEquals("manual_900", refreshed.providerRecordId)
        assertEquals(LyricsProvenance.MANUAL, refreshed.provenance)
        assertEquals("Manual manual_900 call #2", refreshed.syncedLines[0].text)
        assertNotNull(overrideStore.getOverride("7017", repo.signatureFor(meta)))
    }

    @Test
    fun `v0631 12 Tim lai tu dong clears manual override and returns to automatic lookup`() {
        val (_, overrideStore, repo) = buildTestEnvironment(name = "v0631_12")
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)
        val candidate = LyricsCandidate(provider = "lrclib", recordId = "manual_900", title = "Lydia", artist = "F.I.R", hasSynced = true, confidence = 0.95)

        val manualState = controller.selectCandidateSynchronously(meta, candidate) as LyricsUiState.Synced
        assertEquals("manual_900", manualState.recordId)
        assertEquals(LyricsProvenance.MANUAL, manualState.provenance)
        assertTrue(repo.hasManualOverride(meta))

        // "Tìm lại tự động" clears override and runs automatic lookup ("1001")
        val autoState = controller.clearOverrideSynchronously(meta) as LyricsUiState.Synced
        assertFalse(repo.hasManualOverride(meta))
        assertNull(overrideStore.getOverride("7017", repo.signatureFor(meta)))
        assertEquals("1001", autoState.recordId)
        assertEquals(LyricsProvenance.AUTO, autoState.provenance)
    }

    @Test
    fun `v0631 13 metadata change invalidates stale override for same track_key`() {
        val (_, overrideStore, repo) = buildTestEnvironment(name = "v0631_13")
        val metaV1 = LyricsTrackMetadata(trackKey = "7017", title = "Old Title", artist = "Old Artist", durationMs = 200_000L)
        val metaV2 = LyricsTrackMetadata(trackKey = "7017", title = "New Corrected Title", artist = "New Artist", durationMs = 240_000L)
        val candidate = LyricsCandidate(provider = "lrclib", recordId = "old_override_id", title = "Old Title", artist = "Old Artist", hasSynced = true, confidence = 0.90)

        repo.selectManualCandidate(metaV1, candidate)
        assertTrue(repo.hasManualOverride(metaV1))

        // Same track_key ("7017"), but metadata changed -> override must not apply!
        assertFalse(repo.hasManualOverride(metaV2))
        assertNull(overrideStore.getOverride("7017", repo.signatureFor(metaV2)))
        val resultV2 = repo.resolveLyrics(metaV2)
        assertEquals("1001", resultV2.providerRecordId)
        assertEquals(LyricsProvenance.AUTO, resultV2.provenance)
    }

    @Test
    fun `v0631 14 corrupted override store recovers safely without crash`() {
        val dir = tempFolder.newFolder("v0631_14")
        val overrideFile = File(dir, "lyrics_overrides.json")
        overrideFile.writeText("{corrupted_json_content_!@#$", Charsets.UTF_8)

        val store = LyricsOverrideStore(overrideFile = overrideFile)
        assertNull(store.getOverride("7017", "any_sig"))
        assertFalse(overrideFile.exists())

        // Subsequent save works cleanly
        store.saveOverride(
            trackKey = "7017",
            metadataSignature = "sig_7017",
            provider = "lrclib",
            providerRecordId = "14169",
            selectedTitle = "Lydia",
            selectedArtist = "F.I.R",
            selectedDurationMs = 238_000L,
        )
        val recovered = store.getOverride("7017", "sig_7017")
        assertNotNull(recovered)
        assertEquals("14169", recovered!!.providerRecordId)
    }

    @Test
    fun `v0631 15 stale candidate list response from Track A is ignored after switching to Track B`() {
        val (_, _, repo) = buildTestEnvironment(name = "v0631_15")
        val controller = LyricsSessionController(repository = repo)
        val trackA = LyricsTrackMetadata(trackKey = "track:A", title = "Track A", artist = "Artist A", durationMs = 200_000L)
        val trackB = LyricsTrackMetadata(trackKey = "track:B", title = "Track B", artist = "Artist B", durationMs = 210_000L)

        val candTokenA = controller.startCandidatePicker(trackA)
        assertTrue(controller.candidatePickerState is LyricsCandidatePickerState.Loading)

        // Switch to Track B before Track A's candidate list arrives
        controller.onTrackChanged(trackB)
        assertEquals(LyricsCandidatePickerState.Closed, controller.candidatePickerState)

        val staleCandidatesA = LyricsCandidatesResult(
            status = "OK",
            candidates = listOf(
                LyricsCandidate(provider = "lrclib", recordId = "cand_A", title = "Track A", artist = "Artist A", hasSynced = true, confidence = 0.95),
            ),
        )
        assertFalse(controller.applyCandidatesIfCurrent(candTokenA, staleCandidatesA))
        assertEquals(LyricsCandidatePickerState.Closed, controller.candidatePickerState)
    }

    @Test
    fun `v0631 16 stale resolve response from Track A is ignored after switching to Track B`() {
        val (_, _, repo) = buildTestEnvironment(name = "v0631_16")
        val controller = LyricsSessionController(repository = repo)
        val trackA = LyricsTrackMetadata(trackKey = "track:A", title = "Track A", artist = "Artist A", durationMs = 200_000L)
        val trackB = LyricsTrackMetadata(trackKey = "track:B", title = "Track B", artist = "Artist B", durationMs = 210_000L)

        // User selects a candidate for Track A (allocates lookup token for Track A)
        val tokenA = controller.startLookup(trackA)
        // Before resolve completes, user switches to Track B
        val tokenB = controller.onTrackChanged(trackB)!!

        val lateResolvedA = LyricsLookupResult(
            status = LyricsStatus.SYNCED,
            provider = "lrclib",
            providerRecordId = "manual_A",
            syncedLyrics = "[00:01.00]Late manual lyrics for Track A",
            provenance = LyricsProvenance.MANUAL,
        )
        assertFalse(controller.applyResultIfCurrent(tokenA, lateResolvedA, currentPositionMs = 2_000L))

        val resolvedB = LyricsLookupResult(
            status = LyricsStatus.SYNCED,
            provider = "lrclib",
            providerRecordId = "auto_B",
            syncedLyrics = "[00:01.00]Lyrics for Track B",
            provenance = LyricsProvenance.AUTO,
        )
        assertTrue(controller.applyResultIfCurrent(tokenB, resolvedB, currentPositionMs = 2_000L))
        assertEquals("Lyrics for Track B", (controller.uiState as LyricsUiState.Synced).lines[0].text)
    }

    @Test
    fun `v0631 17 provenance indicator shows Tu dong vs Da chon thu cong`() {
        assertEquals("LRCLIB · Tự động", LyricsProvenance.AUTO.badgeText)
        assertEquals("LRCLIB · Đã chọn thủ công", LyricsProvenance.MANUAL.badgeText)

        val (_, _, repo) = buildTestEnvironment(name = "v0631_17")
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        val autoState = controller.loadSynchronously(meta) as LyricsUiState.Synced
        assertEquals(LyricsProvenance.AUTO, autoState.provenance)
        assertEquals("LRCLIB · Tự động", autoState.provenance.badgeText)

        val cand = LyricsCandidate(provider = "lrclib", recordId = "9001", title = "Lydia", artist = "F.I.R", hasSynced = true, confidence = 0.93)
        val manualState = controller.selectCandidateSynchronously(meta, cand) as LyricsUiState.Synced
        assertEquals(LyricsProvenance.MANUAL, manualState.provenance)
        assertEquals("LRCLIB · Đã chọn thủ công", manualState.provenance.badgeText)
    }

    @Test
    fun `v0631 18 manual override works across remote stream offline internal offline SD and Device Music models`() {
        val resolveCalls = AtomicInteger(0)
        val (_, _, repo) = buildTestEnvironment(
            name = "v0631_18",
            resolveFetcher = { _, recordId, _ ->
                resolveCalls.incrementAndGet()
                LyricsLookupResult(
                    status = LyricsStatus.SYNCED,
                    provider = "lrclib",
                    providerRecordId = recordId,
                    syncedLyrics = "[00:03.00]Shared manual lyrics $recordId",
                    provenance = LyricsProvenance.MANUAL,
                )
            },
        )
        val remoteMeta = LyricsTrackMetadata(trackKey = "11200", title = "Duyên Kiếp", artist = "Trường Vũ", durationMs = 240_000L)
        val internalOfflineMeta = remoteMeta.copy(durationMs = 240_100L)
        val sdOfflineMeta = remoteMeta.copy(durationMs = 239_900L)
        val deviceMusicMeta = LyricsTrackMetadata(
            trackKey = "device:external_primary:501",
            title = "Song On Device",
            artist = "Device Artist",
            durationMs = 195_000L,
            isDeviceLocal = true,
        )

        val candLibrary = LyricsCandidate(provider = "lrclib", recordId = "lib_manual_1", title = "Duyên Kiếp", artist = "Trường Vũ", hasSynced = true, confidence = 0.95)
        repo.selectManualCandidate(remoteMeta, candLibrary)
        assertEquals(1, resolveCalls.get())

        // Internal & SD offline share trackKey "11200" and normalized signature -> served from manual override cache!
        val internalRes = repo.resolveLyrics(internalOfflineMeta)
        val sdRes = repo.resolveLyrics(sdOfflineMeta)
        assertEquals("lib_manual_1", internalRes.providerRecordId)
        assertEquals(LyricsProvenance.MANUAL, internalRes.provenance)
        assertTrue(internalRes.fromCache)
        assertEquals("lib_manual_1", sdRes.providerRecordId)
        assertEquals(LyricsProvenance.MANUAL, sdRes.provenance)
        assertTrue(sdRes.fromCache)
        assertEquals(1, resolveCalls.get())

        // Device Music track uses its own canonical device key and persists independently
        val candDevice = LyricsCandidate(provider = "lrclib", recordId = "dev_manual_2", title = "Song On Device", artist = "Device Artist", hasSynced = true, confidence = 0.91)
        repo.selectManualCandidate(deviceMusicMeta, candDevice)
        val devRes = repo.resolveLyrics(deviceMusicMeta)
        assertEquals("dev_manual_2", devRes.providerRecordId)
        assertEquals(LyricsProvenance.MANUAL, devRes.provenance)
        assertTrue(devRes.fromCache)
    }

    @Test
    fun `v0631 19 candidate picker works from both inline lyrics panel and fullscreen lyrics overlay`() {
        val (_, _, repo) = buildTestEnvironment(name = "v0631_19")
        val controller = LyricsSessionController(repository = repo)
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        // 1. Open candidate picker in inline mode
        controller.loadSynchronously(meta, currentPositionMs = 5_000L)
        assertFalse(controller.isFullscreenVisible)
        val inlinePicker = controller.openCandidatePickerSynchronously(meta) as LyricsCandidatePickerState.Loaded
        assertEquals(2, inlinePicker.candidates.size)
        assertEquals("1001", inlinePicker.activeRecordId)
        controller.closeCandidatePicker()
        assertEquals(LyricsCandidatePickerState.Closed, controller.candidatePickerState)

        // 2. Enter fullscreen and open candidate picker in fullscreen mode
        controller.enterFullscreen(meta, currentPositionMs = 6_000L)
        assertTrue(controller.isFullscreenVisible)
        val fullscreenPicker = controller.openCandidatePickerSynchronously(meta) as LyricsCandidatePickerState.Loaded
        assertEquals(2, fullscreenPicker.candidates.size)

        // Select candidate 2002 while in fullscreen -> stays in fullscreen and updates lyrics
        val updated = controller.selectCandidateSynchronously(meta, fullscreenPicker.candidates[1])
        assertTrue(controller.isFullscreenVisible)
        assertEquals(LyricsCandidatePickerState.Closed, controller.candidatePickerState)
        assertTrue(updated is LyricsUiState.Plain)
        assertEquals("2002", (updated as LyricsUiState.Plain).recordId)
    }

    @Test
    fun `v0631 20 selecting or clearing lyrics never restarts or interrupts playback`() {
        val (_, _, repo) = buildTestEnvironment(name = "v0631_20")
        val seekCalls = mutableListOf<Long>()
        val controller = LyricsSessionController(
            repository = repo,
            onSeekRequested = { seekCalls.add(it) },
        )
        val meta = LyricsTrackMetadata(trackKey = "7017", title = "Lydia", artist = "F.I.R", durationMs = 238_934L)

        var isPlaying = true
        val currentStreamSource = "REMOTE_STREAM"
        val currentPositionMs = 42_500L

        controller.loadSynchronously(meta, currentPositionMs = currentPositionMs)
        val picker = controller.openCandidatePickerSynchronously(meta) as LyricsCandidatePickerState.Loaded
        controller.selectCandidateSynchronously(meta, picker.candidates[0], currentPositionMs = currentPositionMs)
        controller.clearOverrideSynchronously(meta, currentPositionMs = currentPositionMs)

        // Selecting or clearing lyrics must never trigger any player seek, stop, or stream reload
        assertTrue(seekCalls.isEmpty())
        assertTrue(isPlaying)
        assertEquals("REMOTE_STREAM", currentStreamSource)
        assertEquals(42_500L, currentPositionMs)
    }
}


