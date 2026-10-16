package com.bsdung.musicassistantcar.data.telegram

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramAudioResolverPolicyTest {

    @Test
    fun parseMessage_messageAudio_returnsResolvedWithFreshFileId() {
        val json = JSONObject().apply {
            put("@type", "message")
            put("id", 12345L)
            put("chat_id", -1001234567890L)
            put("content", JSONObject().apply {
                put("@type", "messageAudio")
                put("audio", JSONObject().apply {
                    put("file_name", "Chill_Track.mp3")
                    put("title", "Chill Track")
                    put("mime_type", "audio/mpeg")
                    put("audio", JSONObject().apply {
                        put("id", 8888)
                        put("size", 1234567L)
                        put("local", JSONObject().apply {
                            put("is_downloading_completed", false)
                            put("path", "")
                        })
                    })
                })
            })
        }

        val result = TelegramAudioResolverPolicy.parseMessage(json)
        assertTrue(result is TelegramAudioResolverPolicy.Result.Resolved)
        val resolved = result as TelegramAudioResolverPolicy.Result.Resolved
        assertEquals(8888, resolved.freshFileId)
        assertFalse(resolved.isDownloadingCompleted)
        assertEquals("", resolved.localPath)
        assertEquals(1234567L, resolved.size)
        assertEquals("Chill Track", resolved.title)
        assertEquals("Chill_Track.mp3", resolved.filename)
        assertEquals("audio/mpeg", resolved.mimeType)
    }

    @Test
    fun parseMessage_messageDocument_withAudioExtension_returnsResolved() {
        val json = JSONObject().apply {
            put("@type", "message")
            put("id", 54321L)
            put("content", JSONObject().apply {
                put("@type", "messageDocument")
                put("document", JSONObject().apply {
                    put("file_name", "Audiophile_Mix.flac")
                    put("title", "Audiophile Mix")
                    put("mime_type", "audio/flac")
                    put("document", JSONObject().apply {
                        put("id", 9999)
                        put("size", 50000000L)
                        put("local", JSONObject().apply {
                            put("is_downloading_completed", true)
                            put("path", "/data/user/0/app.melovault.player/files/audiophile.flac")
                        })
                    })
                })
            })
        }

        val result = TelegramAudioResolverPolicy.parseMessage(json)
        assertTrue(result is TelegramAudioResolverPolicy.Result.Resolved)
        val resolved = result as TelegramAudioResolverPolicy.Result.Resolved
        assertEquals(9999, resolved.freshFileId)
        assertTrue(resolved.isDownloadingCompleted)
        assertEquals("/data/user/0/app.melovault.player/files/audiophile.flac", resolved.localPath)
        assertEquals(50000000L, resolved.size)
        assertEquals("Audiophile Mix", resolved.title)
    }

    @Test
    fun parseMessage_messageDocument_withNonAudioExtension_returnsError() {
        val json = JSONObject().apply {
            put("@type", "message")
            put("content", JSONObject().apply {
                put("@type", "messageDocument")
                put("document", JSONObject().apply {
                    put("file_name", "Report.pdf")
                    put("title", "Annual Report")
                    put("document", JSONObject().apply {
                        put("id", 1111)
                        put("size", 2048L)
                    })
                })
            })
        }

        val result = TelegramAudioResolverPolicy.parseMessage(json)
        assertTrue(result is TelegramAudioResolverPolicy.Result.Error)
        val err = (result as TelegramAudioResolverPolicy.Result.Error).message
        assertTrue(err.contains("không phải định dạng âm thanh"))
    }

    @Test
    fun parseMessage_messagePhoto_returnsError() {
        val json = JSONObject().apply {
            put("@type", "message")
            put("content", JSONObject().apply {
                put("@type", "messagePhoto")
                put("photo", JSONObject().apply {
                    put("id", 1762)
                })
            })
        }

        val result = TelegramAudioResolverPolicy.parseMessage(json)
        assertTrue(result is TelegramAudioResolverPolicy.Result.Error)
        val err = (result as TelegramAudioResolverPolicy.Result.Error).message
        assertTrue(err.contains("không phải âm thanh hay tài liệu"))
    }

    @Test
    fun parseMessage_nonMessageTDLibObject_returnsError() {
        val json = JSONObject().apply {
            put("@type", "updateFile")
            put("file", JSONObject().apply {
                put("id", 1762)
            })
        }

        val result = TelegramAudioResolverPolicy.parseMessage(json)
        assertTrue(result is TelegramAudioResolverPolicy.Result.Error)
        val err = (result as TelegramAudioResolverPolicy.Result.Error).message
        assertTrue(err.contains("không phải tin nhắn"))
    }

    @Test
    fun parseMessage_invalidFileId_returnsError() {
        val json = JSONObject().apply {
            put("@type", "message")
            put("content", JSONObject().apply {
                put("@type", "messageAudio")
                put("audio", JSONObject().apply {
                    put("file_name", "test.mp3")
                    put("audio", JSONObject().apply {
                        put("id", 0) // Invalid
                    })
                })
            })
        }

        val result = TelegramAudioResolverPolicy.parseMessage(json)
        assertTrue(result is TelegramAudioResolverPolicy.Result.Error)
        val err = (result as TelegramAudioResolverPolicy.Result.Error).message
        assertTrue(err.contains("file_id của phiên hiện tại không hợp lệ"))
    }

    @Test
    fun staleFileIdResolution_preventsPlaybackCollision() {
        // Scenario from Beta 74 bug:
        // Stale file_id = 1762 was saved in SQLite from a prior session.
        // In current session, file_id 1762 belongs to a JPEG profile photo.
        // Calling getMessage resolves the message to the real fresh audio file_id = 4521.
        val staleFileId = 1762
        val freshAudioFileId = 4521

        val messageJson = JSONObject().apply {
            put("@type", "message")
            put("id", 888000L)
            put("chat_id", -100987654321L)
            put("content", JSONObject().apply {
                put("@type", "messageAudio")
                put("audio", JSONObject().apply {
                    put("file_name", "Track_01.mp3")
                    put("title", "Track 01")
                    put("mime_type", "audio/mpeg")
                    put("audio", JSONObject().apply {
                        put("id", freshAudioFileId) // Fresh session ID
                        put("size", 8450000L)
                        put("local", JSONObject().apply {
                            put("is_downloading_completed", false)
                            put("path", "")
                        })
                    })
                })
            })
        }

        val result = TelegramAudioResolverPolicy.parseMessage(messageJson)
        assertTrue(result is TelegramAudioResolverPolicy.Result.Resolved)
        val resolved = result as TelegramAudioResolverPolicy.Result.Resolved

        // Verify fresh ID replaces stale ID
        assertTrue(resolved.freshFileId != staleFileId)
        assertEquals(freshAudioFileId, resolved.freshFileId)
    }

    @Test
    fun parseMessage_getMessageError_deletedOrInaccessible_returnsCleanError() {
        val errorJson = JSONObject().apply {
            put("@type", "error")
            put("code", 404)
            put("message", "Message not found")
        }

        val result = TelegramAudioResolverPolicy.parseMessage(errorJson)
        assertTrue(result is TelegramAudioResolverPolicy.Result.Error)
        val err = (result as TelegramAudioResolverPolicy.Result.Error).message
        assertTrue(err.contains("không phải tin nhắn"))
    }

    @Test
    fun parseMessage_nextAndPreviousTrack_eachResolvesFreshSessionIds() {
        // Track 1 (Current)
        val track1Json = JSONObject().apply {
            put("@type", "message")
            put("id", 101L)
            put("content", JSONObject().apply {
                put("@type", "messageAudio")
                put("audio", JSONObject().apply {
                    put("file_name", "Track1.mp3")
                    put("audio", JSONObject().apply {
                        put("id", 2001)
                        put("size", 5000L)
                    })
                })
            })
        }

        // Track 2 (Next track in queue)
        val track2Json = JSONObject().apply {
            put("@type", "message")
            put("id", 102L)
            put("content", JSONObject().apply {
                put("@type", "messageAudio")
                put("audio", JSONObject().apply {
                    put("file_name", "Track2.mp3")
                    put("audio", JSONObject().apply {
                        put("id", 2002)
                        put("size", 6000L)
                    })
                })
            })
        }

        val res1 = TelegramAudioResolverPolicy.parseMessage(track1Json) as TelegramAudioResolverPolicy.Result.Resolved
        val res2 = TelegramAudioResolverPolicy.parseMessage(track2Json) as TelegramAudioResolverPolicy.Result.Resolved

        assertEquals(2001, res1.freshFileId)
        assertEquals(2002, res2.freshFileId)
        assertTrue(res1.freshFileId != res2.freshFileId)
    }
}
