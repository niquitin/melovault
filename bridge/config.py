from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import os

from dotenv import dotenv_values, load_dotenv


ROOT = Path(__file__).resolve().parent
load_dotenv(ROOT / ".env")


@dataclass(frozen=True)
class BridgeConfig:
    token: str
    host: str
    port: int
    music_root: Path
    db_path: Path
    drive_roots: tuple[Path, ...]
    telegram_api_id: int
    telegram_api_hash: str
    telegram_bot_token: str
    telegram_session: Path


def _path_list(*values: str) -> tuple[Path, ...]:
    result: list[Path] = []
    for raw in values:
        for item in str(raw or "").split(";"):
            item = item.strip()
            if not item:
                continue
            path = Path(item)
            if path not in result:
                result.append(path)
    return tuple(result)


def load_config() -> BridgeConfig:
    token = os.getenv("CAR_BRIDGE_TOKEN", "").strip()
    if not token:
        raise RuntimeError("CAR_BRIDGE_TOKEN is not configured")

    music_root = Path(
        os.getenv(
            "MUSIC_ASSISTANT_ROOT",
            r"C:\Bots\MusicAssistant",
        )
    )

    music_env = dotenv_values(music_root / ".env")
    managed = str(music_env.get("GOOGLE_DRIVE_MUSIC_ROOT") or "")
    library = str(music_env.get("GOOGLE_DRIVE_LIBRARY_ROOT") or "")
    extras = str(music_env.get("GOOGLE_DRIVE_LIBRARY_ROOTS") or "")
    roots = list(_path_list(managed, library, extras))

    if managed:
        volume_root = Path(managed).parent
        if volume_root not in roots:
            roots.append(volume_root)

    api_id_raw = str(music_env.get("TELEGRAM_API_ID") or "0")
    try:
        api_id = int(api_id_raw)
    except ValueError:
        api_id = 0

    return BridgeConfig(
        token=token,
        host=os.getenv("CAR_BRIDGE_HOST", "0.0.0.0").strip(),
        port=int(os.getenv("CAR_BRIDGE_PORT", "8765")),
        music_root=music_root,
        db_path=music_root / "data" / "music.db",

        drive_roots=tuple(roots),
        telegram_api_id=api_id,
        telegram_api_hash=str(
            music_env.get("TELEGRAM_API_HASH") or ""
        ),
        telegram_bot_token=str(
            music_env.get("TELEGRAM_BOT_TOKEN") or ""
        ),
        telegram_session=ROOT / "data" / "car_mtproto",
    )
