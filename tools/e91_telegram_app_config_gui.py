#!/usr/bin/env python3
"""Local-only E9.1 Telegram application credentials vault.

Stores developer API ID/hash encrypted using Windows DPAPI CurrentUser; the
values are NEVER printed, sent to a service, or copied into a handoff/Drive.
This is NOT a TDLib client and does not request any user's Telegram account.
"""
from __future__ import annotations

import argparse
import ctypes
import ctypes.wintypes
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone
from tkinter import Tk, StringVar, Label, Button, Entry, Frame, messagebox

BASE = Path("C:/Bots/_secrets/melovault-e91-telegram-api")
ENCRYPTED = BASE / "telegram_api.dpapi"
RECEIPT = BASE / "telegram_api_verified.json"
PURPOSE = b"MELOVAULT_E91_TELEGRAM_DEVELOPER_APP_CREDENTIALS_V1"
FLAG_NO_UI = 1

class Blob(ctypes.Structure):
    _fields_ = [("cbData", ctypes.wintypes.DWORD),
                ("pbData", ctypes.POINTER(ctypes.c_ubyte))]

def blob_from_bytes(data: bytes):
    buf = ctypes.create_string_buffer(data)
    b = Blob(len(data), ctypes.cast(buf, ctypes.POINTER(ctypes.c_ubyte)))
    return b, buf

def transform_dpapi(data: bytes, encrypt: bool) -> bytes:
    if sys.platform != "win32":
        raise RuntimeError("Windows DPAPI only")
    input_blob, input_buffer = blob_from_bytes(data)
    entropy, entropy_buffer = blob_from_bytes(PURPOSE)
    output_blob = Blob()
    c = ctypes.windll.crypt32
    function = c.CryptProtectData if encrypt else c.CryptUnprotectData
    function.argtypes = [ctypes.POINTER(Blob), ctypes.c_void_p,
                         ctypes.POINTER(Blob), ctypes.c_void_p, ctypes.c_void_p,
                         ctypes.wintypes.DWORD, ctypes.POINTER(Blob)]
    function.restype = ctypes.wintypes.BOOL
    ok = function(ctypes.byref(input_blob), None, ctypes.byref(entropy),
                  None, None, FLAG_NO_UI, ctypes.byref(output_blob))
    if not ok:
        raise ctypes.WinError()
    try:
        return ctypes.string_at(output_blob.pbData, output_blob.cbData)
    finally:
        free = ctypes.windll.kernel32.LocalFree
        free.argtypes = [ctypes.c_void_p]
        free.restype = ctypes.c_void_p
        free(ctypes.cast(output_blob.pbData, ctypes.c_void_p))

def valid_credentials(api_id: str, api_hash: str) -> bool:
    return (api_id.isascii() and api_id.isdigit()
            and 1 <= int(api_id) < 2**31
            and re.fullmatch(r"[a-fA-F0-9]{32}", api_hash) is not None)

def icacls(path: Path, *args: str) -> None:
    r = subprocess.run(["icacls", str(path), *args],
                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
                       timeout=15, text=True,
                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    if r.returncode != 0:
        raise RuntimeError("Unable to restrict local credentials directory")

def restrict_folder() -> None:
    if not Path("C:/Bots/_secrets").is_dir():
        raise RuntimeError("Existing private secrets parent folder missing")
    BASE.mkdir(exist_ok=True)
    owner = subprocess.check_output(["whoami"], text=True).strip()
    # No Authenticated Users / Everyone or Users in this child folder.
    icacls(BASE, "/grant:r", f"{owner}:(OI)(CI)F",
           "SYSTEM:(OI)(CI)F", "Administrators:(OI)(CI)F")
    icacls(BASE, "/inheritance:r")
    r = subprocess.check_output(["icacls", str(BASE)], text=True).lower()
    if any(x in r for x in ["authenticated users", "everyone", "builtin\\users"]):
        raise RuntimeError("Unsafe credentials folder ACL")

def save_local(api_id: str, api_hash: str) -> None:
    if not valid_credentials(api_id, api_hash):
        raise ValueError("API ID must be positive digits; API Hash must be 32 hexadecimal characters")
    restrict_folder()
    if ENCRYPTED.exists():
        raise FileExistsError("Credentials already saved. Refusing overwrite without a separate rotation workflow")
    payload = json.dumps({"schema": 1, "api_id": int(api_id),
                          "api_hash": api_hash.lower()}, separators=(",", ":")).encode()
    encrypted = transform_dpapi(payload, True)
    with ENCRYPTED.open("xb") as fp:
        fp.write(encrypted)
        fp.flush()
        os.fsync(fp.fileno())
    # Decrypt in RAM to confirm DPAPI works; never print decrypted credentials.
    try:
        verify_local()
    except Exception:
        ENCRYPTED.unlink(missing_ok=True)
        raise
    RECEIPT.write_text(json.dumps({"status": "LOCAL_DPAPI_VERIFIED",
        "verified_utc": datetime.now(timezone.utc).isoformat(),
        "credential_values_logged": False,
        "app_account_connected": False,
        "native_tdlib_linked": False,
        "exported_to_drive": False}, indent=2) + "\n", encoding="utf-8")

def verify_local() -> bool:
    if not ENCRYPTED.is_file():
        raise FileNotFoundError("No encrypted Telegram API app credentials")
    data = transform_dpapi(ENCRYPTED.read_bytes(), False)
    obj = json.loads(data.decode("utf-8"))
    if obj.get("schema") != 1 or not valid_credentials(
        str(obj.get("api_id", "")), obj.get("api_hash", "")
    ):
        raise ValueError("DPAPI credential verification failed")
    return True

def selftest() -> None:
    assert valid_credentials("12345678", "a" * 32)
    assert not valid_credentials("0", "a" * 32)
    assert not valid_credentials("xyz", "a" * 32)
    assert not valid_credentials("123", "not-a-hash")
    for value in (b"synthetic-e91-no-credential", b"abc" * 133):
        assert transform_dpapi(transform_dpapi(value, True), False) == value
    print("SELFTEST_PASS: DPAPI memory roundtrip and validation; no real secrets accessed")

def gui() -> None:
    w = Tk()
    w.title("MeloVault E9.1 — Telegram API (nhập tại Lenovo)")
    w.geometry("750x370")
    w.resizable(False, False)
    w.attributes("-topmost", True)
    w.configure(bg="#172235")
    status = StringVar(value="Chưa có Telegram API ID/Hash cục bộ.")
    Label(w, text="MELOVAULT E9.1 — CẤU HÌNH ỨNG DỤNG TELEGRAM",
          bg="#172235", fg="white", font=("Segoe UI", 16, "bold")).pack(pady=(20, 8))
    Label(w, text="Chỉ nhập API ID / API Hash từ my.telegram.org/apps.\n"
          "KHÔNG nhập số điện thoại, OTP, mật khẩu Telegram hoặc bot token.\n"
          "Mọi giá trị được mã hóa trên Lenovo; không chuyển về ChatGPT/Google Drive.",
          bg="#172235", fg="#dce7f8", font=("Segoe UI", 10)).pack(pady=(0, 12))
    box = Frame(w, bg="#172235")
    box.pack()
    Label(box, text="Telegram API ID", bg="#172235", fg="white",
          width=19, anchor="w").grid(row=0, column=0, pady=5)
    api_id = Entry(box, width=46, font=("Consolas", 11))
    api_id.grid(row=0, column=1, padx=10)
    Label(box, text="Telegram API Hash", bg="#172235", fg="white",
          width=19, anchor="w").grid(row=1, column=0, pady=5)
    api_hash = Entry(box, width=46, show="*", font=("Consolas", 11))
    api_hash.grid(row=1, column=1, padx=10)
    Label(w, textvariable=status, bg="#172235", fg="#aef1c9",
          wraplength=710).pack(pady=12)

    def save():
        try:
            save_local(api_id.get().strip(), api_hash.get().strip())
            api_id.delete(0, "end")
            api_hash.delete(0, "end")
            status.set("PASS — API ID/Hash mã hóa cục bộ, kiểm tra giải mã thành công.")
            messagebox.showinfo("MeloVault E9.1", "Đã lưu & xác minh mã hóa DPAPI.\n"
                "Không cần gửi thông tin API cho ChatGPT.", parent=w)
        except Exception as exc:
            status.set("Chưa lưu thành công.")
            messagebox.showerror("MeloVault E9.1",
                                 type(exc).__name__ + ": " + str(exc)[:170], parent=w)

    def verify():
        try:
            verify_local()
            status.set("PASS — Bản mã DPAPI tồn tại và xác minh được.")
        except Exception:
            status.set("Chưa có cấu hình hợp lệ được xác minh.")

    controls = Frame(w, bg="#172235")
    controls.pack()
    Button(controls, text="LƯU BẢO MẬT", command=save,
           bg="#b3f4db", fg="#192132", padx=15, pady=9,
           font=("Segoe UI", 11, "bold")).grid(row=0, column=0, padx=7)
    Button(controls, text="KIỂM TRA", command=verify,
           padx=15, pady=9).grid(row=0, column=1, padx=7)
    Button(controls, text="ĐÓNG", command=w.destroy,
           padx=15, pady=9).grid(row=0, column=2, padx=7)
    w.mainloop()

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--gui", action="store_true")
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--check", action="store_true")
    opts = p.parse_args()
    if opts.self_test:
        selftest()
    elif opts.check:
        try:
            verify_local()
            print("E91_TELEGRAM_API_CONFIG=ENCRYPTED_AND_VALIDATED")
        except Exception:
            print("E91_TELEGRAM_API_CONFIG=NOT_READY")
            sys.exit(1)
    elif opts.gui:
        gui()
    else:
        p.error("Use --gui, --check, or --self-test")
