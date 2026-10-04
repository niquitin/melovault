from __future__ import annotations

from pathlib import Path
from typing import Iterator
import mimetypes


class RangeNotSatisfiable(ValueError):
    pass


def parse_range(
    header: str | None,
    total: int,
) -> tuple[int, int, int]:
    if total <= 0:
        raise RangeNotSatisfiable("Empty media")

    if not header:
        return 0, total - 1, 200

    value = header.strip()
    if not value.startswith("bytes="):
        raise RangeNotSatisfiable("Unsupported range unit")

    spec = value[6:].strip()
    if "," in spec or "-" not in spec:
        raise RangeNotSatisfiable("Multiple ranges are unsupported")

    left, right = spec.split("-", 1)
    try:
        if not left:
            suffix = int(right)
            if suffix <= 0:
                raise ValueError
            start = max(0, total - suffix)
            end = total - 1
        else:
            start = int(left)
            end = int(right) if right else total - 1
    except ValueError as exc:
        raise RangeNotSatisfiable("Invalid byte range") from exc

    if start < 0 or start >= total:
        raise RangeNotSatisfiable("Range start outside media")
    end = min(end, total - 1)
    if end < start:
        raise RangeNotSatisfiable("Range end before start")

    return start, end, 206


def range_headers(
    total: int,
    start: int,
    end: int,
    status: int,
) -> dict[str, str]:

    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(end - start + 1),
        "Cache-Control": "private, max-age=3600",
    }
    if status == 206:
        headers["Content-Range"] = (
            f"bytes {start}-{end}/{total}"
        )
    return headers


def guess_mime(
    path: Path | None = None,
    fallback: str = "application/octet-stream",
) -> str:
    if path is None:
        return fallback
    return mimetypes.guess_type(path.name)[0] or fallback


def iter_file(
    path: Path,
    *,
    start: int,
    length: int,
    chunk_size: int = 256 * 1024,
) -> Iterator[bytes]:
    remaining = int(length)

    with path.open("rb") as handle:
        handle.seek(int(start))
        while remaining > 0:
            data = handle.read(
                min(chunk_size, remaining)
            )
            if not data:
                break
            remaining -= len(data)
            yield data
