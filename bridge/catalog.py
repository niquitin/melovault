from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path
import sqlite3
from typing import Any

from config import BridgeConfig


def connect_readonly(db_path: Path) -> sqlite3.Connection:
    uri = "file:" + db_path.as_posix() + "?mode=ro"
    conn = sqlite3.connect(
        uri,
        uri=True,
        timeout=5,
        check_same_thread=False,
    )
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA query_only = ON")
    return conn


@contextmanager
def db_session(db_path: Path):
    conn = connect_readonly(db_path)
    try:
        yield conn
    finally:
        conn.close()


def list_playlists(config: BridgeConfig) -> list[dict[str, Any]]:
    with db_session(config.db_path) as conn:
        rows = conn.execute(
            """
            SELECT p.id, p.name, p.description,
                   COUNT(pt.track_id) AS track_count
            FROM playlists p
            LEFT JOIN playlist_tracks pt ON pt.playlist_id=p.id
            GROUP BY p.id
            ORDER BY p.id
            """
        ).fetchall()
    return [dict(row) for row in rows]


def _playable_clause() -> str:
    return """
    (
        EXISTS(
            SELECT 1 FROM drive_files d
            WHERE d.track_id=t.id AND d.is_available=1
        )
        OR EXISTS(
            SELECT 1 FROM audio_files a
            WHERE a.track_id=t.id
              AND a.storage_type IN (
                  'LOCAL','LOCAL_CACHE','CACHE','TELEGRAM'
              )
        )
    )
    """


def list_tracks(
    config: BridgeConfig,
    *,
    query: str = "",
    playlist_id: int | None = None,
    limit: int = 100,
    offset: int = 0,
) -> dict[str, Any]:
    where = [_playable_clause()]
    params: list[Any] = []

    query = query.strip()
    if query:
        where.append(
            "(lower(t.title) LIKE ? OR lower(t.artist) LIKE ?)"
        )
        pattern = f"%{query.casefold()}%"
        params.extend([pattern, pattern])

    if playlist_id is not None:
        where.append(
            """
            EXISTS(
                SELECT 1 FROM playlist_tracks pt
                WHERE pt.track_id=t.id
                  AND pt.playlist_id=?
            )
            """
        )
        params.append(int(playlist_id))

    where_sql = " AND ".join(where)

    with db_session(config.db_path) as conn:
        total = int(
            conn.execute(
                f"SELECT COUNT(*) FROM tracks t WHERE {where_sql}",
                params,
            ).fetchone()[0]
        )
        rows = conn.execute(
            f"""
            SELECT
                t.id, t.title, t.artist, t.album,
                t.duration, t.status,
                EXISTS(
                    SELECT 1 FROM drive_files d
                    WHERE d.track_id=t.id
                      AND d.is_available=1
                ) AS has_drive,
                EXISTS(
                    SELECT 1 FROM audio_files a
                    WHERE a.track_id=t.id
                      AND a.storage_type='TELEGRAM'
                ) AS has_telegram
            FROM tracks t
            WHERE {where_sql}
            ORDER BY
                lower(COALESCE(t.artist, '')),
                lower(t.title),
                t.id
            LIMIT ? OFFSET ?
            """,
            [*params, int(limit), int(offset)],
        ).fetchall()

    items = []
    for row in rows:
        item = dict(row)
        item["has_drive"] = bool(item["has_drive"])
        item["has_telegram"] = bool(item["has_telegram"])
        item["stream_url"] = f"/v1/tracks/{item['id']}/stream"
        items.append(item)
    return {
        "total": total,
        "limit": int(limit),
        "offset": int(offset),
        "items": items,
    }


def get_track(
    config: BridgeConfig,
    track_id: int,
) -> dict[str, Any] | None:
    with db_session(config.db_path) as conn:
        row = conn.execute(
            """
            SELECT id, title, artist, album,
                   duration, status, cover_path
            FROM tracks
            WHERE id=?
            """,
            (int(track_id),),
        ).fetchone()
    if row is None:
        return None
    item = dict(row)
    item["stream_url"] = f"/v1/tracks/{item['id']}/stream"
    return item


def _find_existing(
    roots: tuple[Path, ...],
    relative: str,
    name: str,
) -> Path | None:
    relative_path = Path(relative) if relative else None
    for root in roots:
        candidates: list[Path] = []
        if relative_path is not None:
            candidates.append(root / relative_path)
        if name:
            candidates.append(root / name)
        for candidate in candidates:
            if candidate.is_file():
                return candidate
    return None


def resolve_playback_source(
    config: BridgeConfig,
    track_id: int,
    *,
    quality: str = "max",
    provider: str = "auto",
) -> dict[str, Any] | None:
    provider = provider.strip().upper()
    with db_session(config.db_path) as conn:
        local_rows = conn.execute(
            """
            SELECT id, file_path, filename, format,
                   mime_type, size_bytes
            FROM (
                SELECT a.*,
                       NULL AS mime_type
                FROM audio_files a
            )
            WHERE track_id=?
              AND storage_type IN (
                  'LOCAL','LOCAL_CACHE','CACHE'
              )
            ORDER BY id DESC
            """,
            (int(track_id),),
        ).fetchall()
        for row in local_rows:
            if provider not in {"AUTO", "LOCAL"}:
                break
            data = dict(row)
            path = Path(str(data.get("file_path") or ""))
            if path.is_file():
                return {
                    "provider": "LOCAL",
                    "path": path,
                    "format": data.get("format") or "",
                    "size_bytes": data.get("size_bytes"),
                }

        drive_rows = conn.execute(
            """
            SELECT id, drive_path, name, format,
                   mime_type, size_bytes, role
            FROM drive_files
            WHERE track_id=? AND is_available=1
            ORDER BY
                CASE role
                    WHEN 'MASTER' THEN 0
                    WHEN 'PLAYBACK' THEN 1
                    ELSE 2
                END,
                CASE upper(COALESCE(format, ''))
                    WHEN 'WAV' THEN 0
                    WHEN 'FLAC' THEN 1
                    WHEN 'ALAC' THEN 2
                    WHEN 'M4A' THEN 3
                    WHEN 'AAC' THEN 4
                    WHEN 'MP3' THEN 5
                    ELSE 9
                END,
                id DESC
            """,
            (int(track_id),),
        ).fetchall()

        for row in drive_rows:
            if provider not in {
                "AUTO", "DRIVE", "GOOGLE_DRIVE"
            }:
                break
            data = dict(row)
            path = _find_existing(
                config.drive_roots,
                str(data.get("drive_path") or ""),
                str(data.get("name") or ""),
            )
            if path is not None:
                return {
                    "provider": "GOOGLE_DRIVE",
                    "path": path,
                    "format": data.get("format") or "",
                    "mime_type": data.get("mime_type") or "",
                    "size_bytes": data.get("size_bytes"),
                    "role": data.get("role") or "MASTER",
                }

        telegram_rows = conn.execute(
            """
            SELECT id, filename, format, size_bytes,
                   telegram_message_id,
                   playback_message_id,
                   playback_format
            FROM audio_files
            WHERE track_id=? AND storage_type='TELEGRAM'
            ORDER BY id DESC
            """,
            (int(track_id),),
        ).fetchall()

        for row in telegram_rows:
            if provider not in {"AUTO", "TELEGRAM"}:
                break
            data = dict(row)
            master_id = data.get("telegram_message_id")
            playback_id = data.get("playback_message_id")

            if quality.casefold() == "fast":
                message_id = playback_id or master_id
                fmt = (
                    data.get("playback_format")
                    if playback_id else data.get("format")
                )
            else:
                message_id = master_id or playback_id
                fmt = (
                    data.get("format")
                    if master_id else data.get("playback_format")
                )

            if message_id:
                return {
                    "provider": "TELEGRAM",
                    "message_id": int(message_id),
                    "format": str(fmt or ""),
                    "size_bytes": data.get("size_bytes"),
                    "filename": data.get("filename") or "",
                    "role": (
                        "MASTER"
                        if message_id == master_id
                        else "PLAYBACK"
                    ),
                }

    return None
