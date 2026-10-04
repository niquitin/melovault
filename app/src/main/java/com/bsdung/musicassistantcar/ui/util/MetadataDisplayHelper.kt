package com.bsdung.musicassistantcar.ui.util

object MetadataDisplayHelper {

    private val trackNumberRegex = Regex("^[0-9]{1,3}\\s*[-._]\\s*|^[0-9]{1,3}\\s+")
    private val audioExtensionRegex = Regex("(?i)\\.(mp3|flac|m4a|wav|aac|ogg|opus)$")
    private val repeatedSpacesRegex = Regex("\\s+")
    private val numberOnlyRegex = Regex("^\\d+$")

    fun cleanTitle(rawTitle: String?): String {
        if (rawTitle.isNullOrBlank()) return "Chưa xác định"
        var result = rawTitle.trim()
        if (result.equals("null", ignoreCase = true) || result.equals("none", ignoreCase = true)) {
            return "Chưa xác định"
        }

        // Strip file extension if present at end
        result = result.replace(audioExtensionRegex, "")

        // Strip leading track number e.g. "01_", "02 - ", "03. ", "04 "
        result = result.replace(trackNumberRegex, "")

        // If title contains underscores (typical of raw filenames), replace with spaces
        if (result.contains('_')) {
            result = result.replace('_', ' ')
        }

        // Collapse repeated spaces
        result = result.replace(repeatedSpacesRegex, " ").trim()

        return result.ifBlank { rawTitle.trim() }
    }

    fun cleanArtist(rawArtist: String?): String {
        if (rawArtist.isNullOrBlank()) return ""
        val trimmed = rawArtist.trim()

        if (trimmed.equals("Nghệ sĩ không rõ", ignoreCase = true) ||
            trimmed.equals("Unknown Artist", ignoreCase = true) ||
            trimmed.equals("Unknown", ignoreCase = true) ||
            trimmed.equals("Chưa xác định", ignoreCase = true) ||
            trimmed.equals("null", ignoreCase = true) ||
            trimmed.equals("none", ignoreCase = true) ||
            numberOnlyRegex.matches(trimmed)
        ) {
            return ""
        }

        var result = trimmed
        if (result.contains('_')) {
            result = result.replace('_', ' ')
        }
        result = result.replace(repeatedSpacesRegex, " ").trim()

        return if (numberOnlyRegex.matches(result)) "" else result
    }

    fun cleanAlbum(rawAlbum: String?): String {
        if (rawAlbum.isNullOrBlank()) return ""
        val trimmed = rawAlbum.trim()
        if (trimmed.equals("Unknown Album", ignoreCase = true) ||
            trimmed.equals("Unknown", ignoreCase = true) ||
            trimmed.equals("Chưa xác định", ignoreCase = true) ||
            trimmed.equals("null", ignoreCase = true) ||
            trimmed.equals("none", ignoreCase = true)
        ) {
            return ""
        }
        var result = trimmed
        if (result.contains('_')) {
            result = result.replace('_', ' ')
        }
        return result.replace(repeatedSpacesRegex, " ").trim()
    }

    fun cleanPlaylistName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""
        var result = rawName.trim()

        while (result.isNotEmpty()) {
            val codePoint = result.codePointAt(0)
            val charCount = Character.charCount(codePoint)

            val isEmoji = (codePoint in 0x1F300..0x1F9FF) ||
                          (codePoint in 0x1FA00..0x1FAFF) ||
                          (codePoint in 0x2600..0x27BF) ||
                          (codePoint in 0x2B50..0x2B55) ||
                          (codePoint in 0x2300..0x23FF) ||
                          (codePoint == 0x200D) || // zero width joiner
                          (codePoint in 0xFE00..0xFE0F) || // variation selector
                          (Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()) ||
                          (Character.getType(codePoint) == Character.SURROGATE.toInt())

            if (isEmoji) {
                result = result.substring(charCount).trimStart()
            } else {
                break
            }
        }
        return result.ifBlank { rawName.trim() }
    }
}
