package com.bsdung.musicassistantcar.data

import android.net.Uri
import com.bsdung.musicassistantcar.playback.GainAudioProcessor
import com.bsdung.musicassistantcar.test.FakeSharedPreferences
import com.bsdung.musicassistantcar.ui.model.NavigationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaRepositoryTest {

    @Test
    fun emptySuccessAndUnavailableMediaStoreHaveDifferentReadFacts() {
        val empty = LocalMediaRepository(permissionChecker = { true }, tracksProvider = { emptyList() })
        assertTrue(empty.getAllTracks().isEmpty())
        assertFalse(empty.lastTrackReadFailed)
        val unavailable = LocalMediaRepository(permissionChecker = { true }, contentResolverSupplier = { null })
        assertTrue(unavailable.getAllTracks().isEmpty())
        assertTrue(unavailable.lastTrackReadFailed)
        // A cached empty/partial result must not silently become a successful new read.
        unavailable.getAllTracks()
        assertTrue(unavailable.lastTrackReadFailed)
    }

    @Test
    fun failedProviderKeepsExistingExceptionContractAndRetryClearsFailureFact() {
        var fail = true
        val repo = LocalMediaRepository(permissionChecker = { true }, tracksProvider = {
            if (fail) throw SecurityException("fake permission failure")
            emptyList()
        })
        try {
            repo.getAllTracks()
            org.junit.Assert.fail("provider exception must still propagate")
        } catch (_: SecurityException) { }
        assertTrue(repo.lastTrackReadFailed)
        fail = false
        repo.getAllTracks()
        assertFalse(repo.lastTrackReadFailed)
    }

    @Test
    fun absentPermissionIsNotAnAuthoritativeEmptyRead() {
        val repo = LocalMediaRepository(permissionChecker = { false })
        assertTrue(repo.getAllTracks().isEmpty())
        assertTrue(repo.lastTrackReadFailed)
    }

    private fun createTrack(
        id: Long,
        volume: String = "external_primary",
        relativePath: String = "",
        title: String = "Track $id",
        artist: String = "Artist $id",
    ): LocalMediaTrack {
        return LocalMediaTrack(
            id = id,
            volumeName = volume,
            title = title,
            artist = artist,
            album = "Album $id",
            durationMs = 180000L,
            sizeBytes = 5000000L,
            mimeType = "audio/flac",
            relativePath = relativePath,
            contentUri = Uri.parse("content://media/external/audio/media/$id"),
        )
    }

    @Test
    fun testPermissionDeniedReturnsEmptyList() {
        val repo = LocalMediaRepository(
            permissionChecker = { false },
            contentResolverSupplier = { null },
        )

        assertFalse(repo.hasPermission())
        val tracks = repo.getLocalTracks()
        assertTrue(tracks.isEmpty())
    }

    @Test
    fun testGetLocalTrackInvalidPrefixReturnsNull() {
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            contentResolverSupplier = { null },
        )

        assertNull(repo.getLocalTrack("track:11209"))
        assertNull(repo.getLocalTrack("downloaded:11209"))
        assertNull(repo.getLocalTrack("device:not_a_number"))
        assertNull(repo.getLocalTrack(""))
    }

    @Test
    fun testLocalMediaUnityGainGuarantee() {
        val processor = GainAudioProcessor()
        processor.isNormalizationEnabled = true

        val localItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId("${LocalMediaRepository.DEVICE_PREFIX}external_primary:999")
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Device Song")
                    .setArtist("Local Artist")
                    .setExtras(android.os.Bundle().apply {
                        putBoolean("is_device_local", true)
                        putLong("duration_ms", 180_000L)
                    })
                    .build()
            )
            .build()

        processor.applyMediaItem(localItem, normalizationEnabled = true)

        assertEquals(0.0f, processor.appliedGainDb, 0.001f)
        assertEquals(1.0f, processor.gainMultiplier, 0.001f)
    }

    @Test
    fun testExactHierarchyWithRequiredDataset() {
        // Dataset from Section 16:
        // external_primary:
        //   Music/
        //   Music/Vietnam/
        //   Music/Vietnam/Bolero/
        //   Music/Vietnam/Pop/
        //   Download/
        // second volume:
        //   Music/
        //   Music/Live/
        val dataset = listOf(
            createTrack(1, "external_primary", "Music/", "Track 1 Music"),
            createTrack(2, "external_primary", "Music/Vietnam/", "Track 2 Vietnam"),
            createTrack(3, "external_primary", "Music/Vietnam/Bolero/", "Track 3 Bolero"),
            createTrack(4, "external_primary", "Music/Vietnam/Pop/", "Track 4 Pop"),
            createTrack(5, "external_primary", "Download/", "Track 5 Download"),
            createTrack(6, "vol_sd", "Music/", "Track 6 SD Music"),
            createTrack(7, "vol_sd", "Music/Live/", "Track 7 SD Live"),
        )

        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { dataset },
        )

        val volumes = repo.getVolumes()
        assertEquals(2, volumes.size)
        assertEquals("external_primary", volumes[0].volumeName)
        assertEquals("Bộ nhớ trong", volumes[0].displayName)
        assertEquals(5, volumes[0].totalTrackCount)

        assertEquals("vol_sd", volumes[1].volumeName)
        assertEquals("Thiết bị lưu trữ", volumes[1].displayName)
        assertEquals(2, volumes[1].totalTrackCount)

        // 1. Root of external_primary (""): child folders = Download, Music (alphabetical)
        val extRootContents = repo.getFolderContents("external_primary", "")
        assertEquals(2, extRootContents.size)
        assertEquals("device_folder:external_primary:Download", extRootContents[0].mediaId)
        assertEquals("Download", extRootContents[0].mediaMetadata.title.toString())
        assertEquals("device_folder:external_primary:Music", extRootContents[1].mediaId)
        assertEquals("Music", extRootContents[1].mediaMetadata.title.toString())

        // 2. Inside Music: child folder Vietnam, direct track 1
        val extMusicContents = repo.getFolderContents("external_primary", "Music")
        assertEquals(2, extMusicContents.size)
        // Child folder first
        assertEquals("device_folder:external_primary:Music/Vietnam", extMusicContents[0].mediaId)
        assertEquals("Vietnam", extMusicContents[0].mediaMetadata.title.toString())
        assertTrue(extMusicContents[0].mediaMetadata.isBrowsable == true)
        // Direct track second
        assertEquals("device:external_primary:1", extMusicContents[1].mediaId)
        assertEquals("Track 1 Music", extMusicContents[1].mediaMetadata.title.toString())
        assertTrue(extMusicContents[1].mediaMetadata.isPlayable == true)

        // 3. Inside Music/Vietnam: child folders Bolero, Pop (alphabetical), direct track 2
        val extVietnamContents = repo.getFolderContents("external_primary", "Music/Vietnam")
        assertEquals(3, extVietnamContents.size)
        assertEquals("device_folder:external_primary:Music/Vietnam/Bolero", extVietnamContents[0].mediaId)
        assertEquals("Bolero", extVietnamContents[0].mediaMetadata.title.toString())
        assertEquals("device_folder:external_primary:Music/Vietnam/Pop", extVietnamContents[1].mediaId)
        assertEquals("Pop", extVietnamContents[1].mediaMetadata.title.toString())
        assertEquals("device:external_primary:2", extVietnamContents[2].mediaId)

        // 4. Inside Music/Vietnam/Bolero: only direct track 3
        val extBoleroContents = repo.getFolderContents("external_primary", "Music/Vietnam/Bolero")
        assertEquals(1, extBoleroContents.size)
        assertEquals("device:external_primary:3", extBoleroContents[0].mediaId)
        assertEquals("Track 3 Bolero", extBoleroContents[0].mediaMetadata.title.toString())

        // 5. Inside Download: only direct track 5
        val extDownloadContents = repo.getFolderContents("external_primary", "Download")
        assertEquals(1, extDownloadContents.size)
        assertEquals("device:external_primary:5", extDownloadContents[0].mediaId)

        // 6. Second volume (vol_sd) root: child folder Music
        val sdRootContents = repo.getFolderContents("vol_sd", "")
        assertEquals(1, sdRootContents.size)
        assertEquals("device_folder:vol_sd:Music", sdRootContents[0].mediaId)

        // 7. Inside vol_sd Music: child folder Live, direct track 6
        val sdMusicContents = repo.getFolderContents("vol_sd", "Music")
        assertEquals(2, sdMusicContents.size)
        assertEquals("device_folder:vol_sd:Music/Live", sdMusicContents[0].mediaId)
        assertEquals("device:vol_sd:6", sdMusicContents[1].mediaId)

        // 8. Same folder name on two volumes remains distinct
        assertTrue(repo.folderExists("external_primary", "Music"))
        assertTrue(repo.folderExists("vol_sd", "Music"))
        val itemExt = repo.getFolderItem("device_folder:external_primary:Music")
        val itemSd = repo.getFolderItem("device_folder:vol_sd:Music")
        assertNotNull(itemExt)
        assertNotNull(itemSd)
        assertEquals("device_folder:external_primary:Music", itemExt?.mediaId)
        assertEquals("device_folder:vol_sd:Music", itemSd?.mediaId)
    }

    @Test
    fun testDuplicateFilenamesAndTitles() {
        val tracks = listOf(
            createTrack(10, "external_primary", "Music", "Same Title", "Artist A"),
            createTrack(11, "external_primary", "Music", "Same Title", "Artist B"),
        )
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { tracks },
        )
        val contents = repo.getFolderContents("external_primary", "Music")
        assertEquals(2, contents.size)
        assertEquals("device:external_primary:10", contents[0].mediaId)
        assertEquals("device:external_primary:11", contents[1].mediaId)
    }

    @Test
    fun testEmptyFoldersExcludedAndDescendantsIncluded() {
        // Track only in Deep/Sub/Leaf
        val tracks = listOf(
            createTrack(20, "external_primary", "Deep/Sub/Leaf", "Leaf Song"),
        )
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { tracks },
        )
        assertTrue(repo.folderExists("external_primary", "Deep"))
        assertTrue(repo.folderExists("external_primary", "Deep/Sub"))
        assertTrue(repo.folderExists("external_primary", "Deep/Sub/Leaf"))
        assertFalse(repo.folderExists("external_primary", "UnrelatedEmptyFolder"))

        val deepContents = repo.getFolderContents("external_primary", "Deep")
        assertEquals(1, deepContents.size)
        assertEquals("device_folder:external_primary:Deep/Sub", deepContents[0].mediaId)
    }

    @Test
    fun testMalformedAndBlankRelativePathHandledSafely() {
        val tracks = listOf(
            createTrack(31, "external_primary", "", "Root Song 1"),
            createTrack(32, "external_primary", "   ", "Root Song 2"),
            createTrack(33, "external_primary", "///", "Root Song 3"),
            createTrack(34, "external_primary", "/\\/\\", "Root Song 4"),
        )
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { tracks },
        )
        val rootContents = repo.getFolderContents("external_primary", "")
        assertEquals(4, rootContents.size)
    }

    @Test
    fun testMissingArtistHandledSafely() {
        val tracks = listOf(
            createTrack(40, "external_primary", "Music", "Unknown Artist Song", ""),
        )
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { tracks },
        )
        val track = repo.getLocalTrack("device:external_primary:40")
        assertNotNull(track)
        assertEquals("Nghệ sĩ không rõ", track?.mediaMetadata?.artist?.toString())
    }

    @Test
    fun testEmptyLibraryHandled() {
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { emptyList() },
        )
        assertTrue(repo.getAllTracks().isEmpty())
        assertTrue(repo.getVolumes().isEmpty())
        val cats = repo.getDeviceMusicCategories()
        assertEquals(2, cats.size)
        assertEquals("Tất cả bài hát", cats[0].mediaMetadata.title.toString())
        assertEquals("Theo thư mục", cats[1].mediaMetadata.title.toString())
    }

    @Test
    fun testLastFolderPersistenceAndResume() {
        val fakePrefs = FakeSharedPreferences()
        val dataset = listOf(
            createTrack(50, "external_primary", "Music/Vietnam/Bolero", "Bolero Song"),
        )
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { dataset },
            sharedPrefsSupplier = { fakePrefs },
        )

        // Initial state: no last folder
        assertNull(repo.getLastBrowsedFolder())

        // Save folder
        repo.saveLastBrowsedFolder("external_primary", "Music/Vietnam/Bolero")
        val saved = repo.getLastBrowsedFolder()
        assertNotNull(saved)
        assertEquals("external_primary", saved?.first)
        assertEquals("Music/Vietnam/Bolero", saved?.second)
        assertTrue(repo.folderExists(saved!!.first, saved.second))

        // Clear folder
        repo.clearLastBrowsedFolder()
        assertNull(repo.getLastBrowsedFolder())

        // Missing/deleted folder check
        assertFalse(repo.folderExists("external_primary", "Deleted/Folder"))
        assertFalse(repo.folderExists("removed_volume", "Music"))
    }

    @Test
    fun testNavigationStackAndBreadcrumbs() {
        val nav = NavigationManager()
        nav.navigateTo("root", "Thư viện")
        nav.navigateTo(RemoteCatalog.DEVICE_LOCAL_ID, "Nhạc trên thiết bị")
        nav.navigateTo(RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID, "Theo thư mục")
        nav.navigateTo("device_folder:external_primary:", "Bộ nhớ trong")
        nav.navigateTo("device_folder:external_primary:Music", "Music")
        nav.navigateTo("device_folder:external_primary:Music/Vietnam", "Vietnam")
        nav.navigateTo("device_folder:external_primary:Music/Vietnam/Bolero", "Bolero")

        assertEquals(
            "Nhạc trên thiết bị › Theo thư mục › Bộ nhớ trong › Music › Vietnam › Bolero",
            nav.breadcrumbTrail(),
        )

        // Back one level -> Vietnam
        val prev1 = nav.goBack()
        assertEquals("Vietnam", prev1?.label)
        assertEquals("Vietnam", nav.displayTitle())

        // Back -> Music
        val prev2 = nav.goBack()
        assertEquals("Music", prev2?.label)
        assertEquals("Music", nav.displayTitle())

        // Back -> Bộ nhớ trong
        val prev3 = nav.goBack()
        assertEquals("Bộ nhớ trong", prev3?.label)
        assertEquals("Bộ nhớ trong", nav.displayTitle())

        // Back -> Theo thư mục
        val prev4 = nav.goBack()
        assertEquals("Theo thư mục", prev4?.label)
        assertEquals("Theo thư mục", nav.displayTitle())

        // Back -> Nhạc trên thiết bị
        val prev5 = nav.goBack()
        assertEquals("Nhạc trên thiết bị", prev5?.label)
        assertEquals("Nhạc trên thiết bị", nav.displayTitle())

        // Back -> Thư viện (root)
        val prev6 = nav.goBack()
        assertEquals("Thư viện", prev6?.label)
        assertEquals("Thư viện", nav.displayTitle())
        assertTrue(nav.isAtRoot)
    }

    @Test
    fun testOfflineBackendFailureDeviceMusicRemainsFunctional() {
        val dataset = listOf(
            createTrack(100, "external_primary", "Music", "Offline Song"),
        )
        val repo = LocalMediaRepository(
            permissionChecker = { true },
            tracksProvider = { dataset },
        )

        val catalog = RemoteCatalog(
            localRepoSupplier = { repo },
            downloadManagerSupplier = { null },
        )

        // Root items open with 0 network calls
        val rootChildren = catalog.rootChildren()
        assertEquals(if (com.bsdung.musicassistantcar.BuildConfig.PERSONAL_BACKEND_ENABLED) 7 else 2, rootChildren.size)
        assertTrue(rootChildren.any { it.mediaId == RemoteCatalog.DEVICE_LOCAL_ID })

        // Device local category opens
        val devCats = catalog.children(RemoteCatalog.DEVICE_LOCAL_ID, 0, 30)
        assertEquals(2, devCats.size)
        assertEquals(RemoteCatalog.DEVICE_LOCAL_ALL_ID, devCats[0].mediaId)
        assertEquals(RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID, devCats[1].mediaId)

        // Tất cả bài hát opens
        val allTracks = catalog.children(RemoteCatalog.DEVICE_LOCAL_ALL_ID, 0, 30)
        assertEquals(1, allTracks.size)
        assertEquals("device:external_primary:100", allTracks[0].mediaId)

        // Theo thư mục opens
        val folderRoots = catalog.children(RemoteCatalog.DEVICE_LOCAL_FOLDERS_ID, 0, 30)
        assertEquals(1, folderRoots.size)
        assertEquals("device_folder:external_primary:", folderRoots[0].mediaId)

        // Folder contents open
        val folderContents = catalog.children("device_folder:external_primary:Music", 0, 30)
        assertEquals(1, folderContents.size)
        assertEquals("device:external_primary:100", folderContents[0].mediaId)
    }
}
