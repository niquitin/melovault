from pathlib import Path
import sqlite3
import tempfile
import unittest

from catalog import (
    list_playlists,
    list_tracks,
    resolve_playback_source,
)
from config import BridgeConfig


SCHEMA = """
CREATE TABLE tracks(
    id INTEGER PRIMARY KEY,
    title TEXT,
    artist TEXT,
    album TEXT,
    duration REAL,
    status TEXT,
    cover_path TEXT,
    updated_at TEXT
);
CREATE TABLE drive_files(
    id INTEGER PRIMARY KEY,
    track_id INTEGER,
    drive_path TEXT,
    name TEXT,
    format TEXT,
    mime_type TEXT,
    size_bytes INTEGER,
    role TEXT,
    is_available INTEGER
);
CREATE TABLE audio_files(
    id INTEGER PRIMARY KEY,
    track_id INTEGER,
    file_path TEXT,
    filename TEXT,
    format TEXT,
    size_bytes INTEGER,
    storage_type TEXT,
    telegram_message_id INTEGER,
    playback_message_id INTEGER,
    playback_format TEXT
);
CREATE TABLE playlists(
    id INTEGER PRIMARY KEY,
    name TEXT,
    description TEXT
);
CREATE TABLE playlist_tracks(
    playlist_id INTEGER,
    track_id INTEGER,
    position INTEGER
);
"""


class CatalogTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.drive = root / "drive"
        self.drive.mkdir()
        self.song = self.drive / "Music" / "Artist - Song.flac"
        self.song.parent.mkdir()
        self.song.write_bytes(b"fLaC" + b"x" * 128)

        db = root / "music.db"
        conn = sqlite3.connect(db)
        conn.executescript(SCHEMA)
        conn.execute(
            """
            INSERT INTO tracks(
                id,title,artist,album,duration,status,updated_at
            ) VALUES(1,'Song','Artist','Album',180,'OWNED_AUDIO','2026')
            """
        )
        conn.execute(
            """
            INSERT INTO drive_files(
                id,track_id,drive_path,name,format,
                mime_type,size_bytes,role,is_available
            ) VALUES(1,1,?,?,?,?,?,?,1)
            """,
            (
                "Music/Artist - Song.flac",
                "Artist - Song.flac",
                "FLAC",
                "audio/flac",
                self.song.stat().st_size,
                "MASTER",
            ),
        )
        conn.execute(
            "INSERT INTO playlists VALUES(1,'Driving','Car music')"
        )
        conn.execute(
            "INSERT INTO playlist_tracks VALUES(1,1,1)"
        )
        conn.commit()
        conn.close()

        self.config = BridgeConfig(
            token="test",
            host="127.0.0.1",
            port=8765,
            music_root=root,
            db_path=db,
            drive_roots=(self.drive,),
            telegram_api_id=0,
            telegram_api_hash="",
            telegram_bot_token="",
            telegram_session=root / "tg",
        )


    def tearDown(self):
        self.temp.cleanup()

    def test_lists_playable_track(self):
        result = list_tracks(
            self.config,
            limit=10,
            offset=0,
        )
        self.assertEqual(1, result["total"])
        self.assertEqual("Song", result["items"][0]["title"])
        self.assertTrue(result["items"][0]["has_drive"])

    def test_playlist_filter(self):
        result = list_tracks(
            self.config,
            playlist_id=1,
            limit=10,
            offset=0,
        )
        self.assertEqual(1, result["total"])
        playlists = list_playlists(self.config)
        self.assertEqual(1, playlists[0]["track_count"])

    def test_resolves_drive_file(self):
        source = resolve_playback_source(
            self.config,
            1,
        )
        self.assertIsNotNone(source)

        self.assertEqual(
            "GOOGLE_DRIVE",
            source["provider"],
        )
        self.assertEqual(
            self.song,
            source["path"],
        )


if __name__ == "__main__":
    unittest.main()
