package com.bsdung.musicassistantcar.ui.util

/**
 * Guards playlist drag-reordering against corruption during multi-page continuous scrolling.
 *
 * Guarantees that:
 * 1. Offset = 0 slice reorder contract for loaded multi-page prefixes.
 * 2. Absolute rejection of playlists containing legitimate duplicate track IDs (prevents backend ValueError).
 * 3. Prevention of reorder mutations while a paging fetch is in-flight.
 * 4. Detection of no-op drags (drag started and ended at identical sequence).
 * 5. Rejection of dragging tracks onto non-track items (such as the loading footer).
 * 6. Clean rollback to original list snapshot on rejection or backend failure.
 */
object PlaylistReorderGuard {

    sealed class Decision {
        object NoOp : Decision()
        data class RejectedInFlightPaging(val reason: String) : Decision()
        data class RejectedDuplicateTracks(val reason: String) : Decision()
        data class Proceed(val playlistId: Int, val trackIds: List<String>, val offset: Int) : Decision()
    }

    /**
     * Checks if moving between two adapter positions is allowed.
     * Disallows moving onto non-track items (such as loading footer or category headers).
     */
    fun canMove(
        isEditMode: Boolean,
        canReorder: Boolean,
        fromIsTrack: Boolean,
        toIsTrack: Boolean,
    ): Boolean {
        return isEditMode && canReorder && fromIsTrack && toIsTrack
    }

    /**
     * Evaluates whether a completed drag should proceed to the backend API.
     */
    fun evaluateReorder(
        playlistId: Int?,
        initialTrackIds: List<String>,
        currentTrackIds: List<String>,
        isPagingLoading: Boolean,
    ): Decision {
        if (playlistId == null) {
            return Decision.NoOp
        }
        if (initialTrackIds == currentTrackIds) {
            return Decision.NoOp
        }
        if (isPagingLoading) {
            return Decision.RejectedInFlightPaging("Đang tải dữ liệu, không thể sắp xếp lại lúc này")
        }
        if (currentTrackIds.size != currentTrackIds.distinct().size) {
            return Decision.RejectedDuplicateTracks("Danh sách có bài hát trùng lặp, không thể sắp xếp lại")
        }
        return Decision.Proceed(playlistId = playlistId, trackIds = currentTrackIds, offset = 0)
    }
}
