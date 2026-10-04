from __future__ import annotations

from contextlib import asynccontextmanager
import hmac
import logging

from fastapi import (
    Depends,
    FastAPI,
    Header,
    HTTPException,
    Query,
    Request,
    Response,
)
from fastapi.responses import StreamingResponse

from catalog import (
    get_track,
    list_playlists,
    list_tracks,
    resolve_playback_source,
)
from config import ROOT, load_config
from streaming import (
    RangeNotSatisfiable,
    guess_mime,
    iter_file,
    parse_range,
    range_headers,
)

from telegram_stream import TelegramBridge


CONFIG = load_config()
(ROOT / "logs").mkdir(parents=True, exist_ok=True)
logging.basicConfig(
    filename=ROOT / "logs" / "bridge.log",
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
)
LOGGER = logging.getLogger("music_assistant_car.bridge")
TELEGRAM = TelegramBridge(CONFIG)


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        await TELEGRAM.start()
        if TELEGRAM.client is not None:
            LOGGER.info("Telegram bridge connected")
        else:
            LOGGER.warning("Telegram bridge not configured")
    except Exception:
        LOGGER.exception("Telegram bridge startup failed")
    yield
    await TELEGRAM.stop()


app = FastAPI(
    title="Music Assistant Car Bridge",
    version="0.1.0",
    lifespan=lifespan,
)


def require_auth(
    authorization: str | None = Header(default=None),
) -> None:
    expected = f"Bearer {CONFIG.token}"
    if (
        authorization is None
        or not hmac.compare_digest(
            authorization.strip(),
            expected,
        )
    ):
        raise HTTPException(
            status_code=401,
            detail="Unauthorized",
        )


@app.get("/healthz")
def healthz() -> dict[str, bool]:
    return {"ok": True}


@app.get(
    "/v1/playlists",
    dependencies=[Depends(require_auth)],
)
def playlists() -> dict:
    return {"items": list_playlists(CONFIG)}


@app.get(
    "/v1/tracks",
    dependencies=[Depends(require_auth)],
)
def tracks(
    q: str = "",
    playlist_id: int | None = None,
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
) -> dict:
    return list_tracks(
        CONFIG,
        query=q,
        playlist_id=playlist_id,
        limit=limit,
        offset=offset,
    )


@app.get(
    "/v1/tracks/{track_id}",
    dependencies=[Depends(require_auth)],
)
def track(track_id: int) -> dict:
    item = get_track(CONFIG, track_id)
    if item is None:
        raise HTTPException(
            status_code=404,
            detail="Track not found",
        )
    return item


def _range_error(total: int) -> Response:
    return Response(
        status_code=416,
        headers={
            "Content-Range": f"bytes */{total}",
            "Accept-Ranges": "bytes",
        },
    )


async def _stream_response(
    request: Request,
    track_id: int,
    quality: str,
    provider: str,
) -> Response:
    source = resolve_playback_source(
        CONFIG,
        track_id,
        quality=quality,
        provider=provider,
    )
    if source is None:
        raise HTTPException(
            status_code=404,
            detail="No playable source",
        )

    if source["provider"] != "TELEGRAM":
        path = source["path"]
        total = int(path.stat().st_size)
        try:
            start, end, status = parse_range(
                request.headers.get("range"),
                total,
            )

        except RangeNotSatisfiable:
            return _range_error(total)

        headers = range_headers(
            total,
            start,
            end,
            status,
        )
        return StreamingResponse(
            iter_file(
                path,
                start=start,
                length=end - start + 1,
            ),
            status_code=status,
            media_type=guess_mime(path),
            headers=headers,
        )

    if TELEGRAM.client is None:
        raise HTTPException(
            status_code=503,
            detail="Telegram bridge unavailable",
        )

    try:
        media = await TELEGRAM.get_media(
            int(source["message_id"])
        )
    except FileNotFoundError as exc:
        raise HTTPException(
            status_code=404,
            detail=str(exc),
        ) from exc

    total = int(media.size_bytes)
    try:
        start, end, status = parse_range(
            request.headers.get("range"),
            total,
        )
    except RangeNotSatisfiable:
        return _range_error(total)

    headers = range_headers(
        total,
        start,
        end,
        status,
    )
    return StreamingResponse(
        TELEGRAM.iter_bytes(
            media,
            start=start,
            length=end - start + 1,
        ),
        status_code=status,
        media_type=media.mime_type,
        headers=headers,
    )


@app.get(
    "/v1/tracks/{track_id}/stream",
    dependencies=[Depends(require_auth)],
)
async def stream_track(
    request: Request,
    track_id: int,
    quality: str = Query(default="max"),
    provider: str = Query(default="auto"),
) -> Response:

    normalized = quality.casefold()
    if normalized not in {"max", "fast"}:
        raise HTTPException(
            status_code=400,
            detail="quality must be max or fast",
        )
    provider_normalized = provider.casefold()
    if provider_normalized not in {
        "auto", "local", "drive", "google_drive", "telegram"
    }:
        raise HTTPException(
            status_code=400,
            detail="invalid provider",
        )
    return await _stream_response(
        request,
        track_id,
        normalized,
        provider_normalized,
    )
