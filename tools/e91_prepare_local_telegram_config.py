"""Local-only binding of developer app ID/hash to ignored Android local.properties.

Does not print, return, or upload credential values. App-level API hash may be
recoverable from preview APK; never bundle any Telegram user's session/OTP or
the owner's private backend credentials.
"""
from __future__ import annotations
import json
from pathlib import Path
from e91_telegram_app_config_gui import ENCRYPTED, transform_dpapi, valid_credentials

LOCAL = Path("C:/Bots/MeloVault-E91-Shareable/local.properties")
KEY_ID = "telegram.api.id"
KEY_HASH = "telegram.api.hash"

def main() -> None:
    if not ENCRYPTED.is_file() or not LOCAL.is_file():
        raise RuntimeError("Local encrypted app credentials or local.properties absent")
    obj = json.loads(transform_dpapi(ENCRYPTED.read_bytes(), False))
    number = str(obj.get("api_id", ""))
    app_hash = str(obj.get("api_hash", ""))
    if not valid_credentials(number, app_hash):
        raise RuntimeError("Encrypted app credentials invalid")
    raw = LOCAL.read_text(encoding="utf-8")
    if any(line.strip().startswith((KEY_ID + "=", KEY_HASH + "="))
           for line in raw.splitlines()):
        # Do not overwrite an existing credential or accidentally print one.
        raise RuntimeError("Credentials already set; refusing overwrite")
    with LOCAL.open("a", encoding="utf-8") as f:
        f.write("\n# MeloVault internal Shareable preview developer app identity\n")
        f.write(KEY_ID + "=" + number + "\n")
        f.write(KEY_HASH + "=" + app_hash + "\n")
    print("LOCAL_APP_CONFIG_READY: two developer app fields written to ignored local.properties; values withheld")

if __name__ == "__main__":
    main()
