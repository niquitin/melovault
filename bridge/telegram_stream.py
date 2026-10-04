from __future__ import annotations

from dataclasses import dataclass
from typing import AsyncIterator, Any

from telethon import TelegramClient, functions, types

from config import BridgeConfig


@dataclass(frozen=True)
class TelegramMedia:
    message: Any
    document: Any
    size_bytes: int
    mime_type: str
    filename: str


class TelegramBridge:
    def __init__(self, config: BridgeConfig) -> None:
        self.config = config
        self.client: TelegramClient | None = None

    @property
    def configured(self) -> bool:
        return bool(
            self.config.telegram_api_id
            and self.config.telegram_api_hash
            and self.config.telegram_bot_token
        )

    async def start(self) -> None:
        if not self.configured:
            return
        client = TelegramClient(
            str(self.config.telegram_session),
            self.config.telegram_api_id,
            self.config.telegram_api_hash,
        )
        await client.connect()
        if not await client.is_user_authorized():
            await client.start(
                bot_token=self.config.telegram_bot_token
            )
        self.client = client

    async def stop(self) -> None:
        if self.client is not None:
            await self.client.disconnect()
            self.client = None

    async def get_media(
        self,
        message_id: int,
    ) -> TelegramMedia:
        client = self.client
        if client is None:
            raise RuntimeError("Telegram bridge is not connected")

        result = await client(
            functions.messages.GetMessagesRequest(
                id=[
                    types.InputMessageID(
                        id=int(message_id)
                    )
                ]
            )
        )
        message = next(
            (
                item
                for item in result.messages
                if isinstance(item, types.Message)
            ),
            None,
        )
        if message is None:
            raise FileNotFoundError(
                f"Telegram message {message_id} not found"
            )

        document = getattr(
            getattr(message, "media", None),
            "document",
            None,
        )
        if document is None:
            raise FileNotFoundError(
                f"Telegram message {message_id} has no document"
            )

        filename = ""
        for attr in getattr(document, "attributes", []) or []:
            if isinstance(
                attr,
                types.DocumentAttributeFilename,
            ):
                filename = str(attr.file_name or "")
                break

        return TelegramMedia(
            message=message,
            document=document,
            size_bytes=int(document.size),
            mime_type=str(
                document.mime_type
                or "application/octet-stream"
            ),
            filename=filename,
        )

    async def iter_bytes(
        self,
        media: TelegramMedia,
        *,
        start: int,
        length: int,
        chunk_size: int = 256 * 1024,
    ) -> AsyncIterator[bytes]:
        client = self.client
        if client is None:
            raise RuntimeError("Telegram bridge is not connected")

        remaining = int(length)
        async for chunk in client.iter_download(
            media.document,
            offset=int(start),
            chunk_size=chunk_size,
            request_size=chunk_size,
        ):
            if remaining <= 0:
                break
            data = bytes(chunk)
            if len(data) > remaining:
                yield data[:remaining]
                break
            yield data
            remaining -= len(data)
