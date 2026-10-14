#!/usr/bin/env python3
"""Show TDLib's short-lived owner preview login QR locally on Lenovo only.

Read via ADB run-as from a *debuggable* Sony Preview app. Never print, log,
persist, upload or send the QR/login link to ChatGPT, Drive or a server.
A successful QR scan still requires approval by the user's existing Telegram.
"""
from __future__ import annotations

import re
import subprocess
import tkinter as tk
from pathlib import Path
import qrcode
from PIL import ImageTk

ADB = Path("C:/Android/Sdk/platform-tools/adb.exe")
DEVICE = "QV725WEQ54"
PACKAGE = "app.melovault.player"
PRIVATE_TOKEN = "no_backup/e91_pending_qr_login.txt"

class QRWindow:
    def __init__(self):
        self.win = tk.Tk()
        self.win.title("MeloVault E9.1 - Xac nhan Telegram bang QR tren Lenovo")
        self.win.geometry("660x770")
        self.win.configure(bg="#192338")
        self.win.attributes("-topmost", True)
        self.current = None
        self.image = None
        self.closed = False
        tk.Label(self.win, text="MELOVAULT - TELEGRAM QR",
                 font=("Segoe UI", 22, "bold"), fg="#fff", bg="#192338").pack(pady=18)
        tk.Label(self.win, text="Trên điện thoại ĐANG ĐĂNG NHẬP TELEGRAM:\n"
                 "Telegram → Cài đặt → Thiết bị → Liên kết thiết bị\n"
                 "Quét mã QR hiển thị bên dưới và chấp thuận trong Telegram.",
                 font=("Segoe UI", 14), fg="#dbe7f8",
                 bg="#192338", justify="center").pack(pady=9)
        self.qr = tk.Label(self.win, bg="white", width=520, height=510)
        self.qr.pack(pady=10)
        self.status = tk.StringVar(value="Đang chờ mã QR từ Sony qua cáp ADB...")
        tk.Label(self.win, textvariable=self.status, font=("Segoe UI", 12),
                 fg="#a5f0cc", bg="#192338", wraplength=620).pack(pady=12)
        tk.Label(self.win, text="Mã chỉ xuất hiện cục bộ tại Lenovo.\n"
                 "KHÔNG gửi mã QR, OTP hoặc mật khẩu qua ChatGPT.",
                 font=("Segoe UI", 10), fg="#f2d1b0", bg="#192338").pack()
        tk.Button(self.win, text="ĐÓNG", command=self.close,
                  padx=20, pady=9).pack(pady=10)
        self.win.protocol("WM_DELETE_WINDOW", self.close)
        self.win.after(700, self.poll)

    def fetch(self):
        if not ADB.is_file():
            return None
        p = subprocess.run(
            [str(ADB), "-s", DEVICE, "exec-out",
             "run-as", PACKAGE, "cat", PRIVATE_TOKEN],
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            timeout=7, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        if p.returncode != 0:
            return None
        value = p.stdout.decode("utf-8", errors="strict").strip()
        if not (32 <= len(value) <= 1024 and
                re.match(r"^tg://login\?token=[A-Za-z0-9_%+\-/=]+$", value)):
            return None
        return value

    def poll(self):
        if self.closed:
            return
        try:
            value = self.fetch()
            if value and value != self.current:
                qr_obj = qrcode.QRCode(
                    error_correction=qrcode.constants.ERROR_CORRECT_M,
                    box_size=10, border=3)
                qr_obj.add_data(value)
                qr_obj.make(fit=True)
                img = qr_obj.make_image(fill_color="black", back_color="white")
                # Keep QR only in memory; never write image/token to a file.
                img = img.convert("RGB").resize((490, 490))
                self.image = ImageTk.PhotoImage(img)
                self.qr.configure(image=self.image, width=500, height=500)
                self.current = value
                self.status.set(
                    "QR đã sẵn sàng. Dùng Telegram trên điện thoại khác để quét.")
            elif not value and self.current is not None:
                self.current = None
                self.image = None
                self.qr.configure(image="", width=520, height=510)
                self.status.set(
                    "QR đã hết hạn hoặc Sony đã chuyển bước xác thực. "
                    "Kiểm tra lại MeloVault Preview.")
        except Exception:
            self.status.set("Tạm thời không đọc được QR. Kiểm tra ADB Sony.")
        self.win.after(2000, self.poll)

    def close(self):
        self.closed = True
        self.current = None
        self.image = None
        self.win.destroy()

    def run(self):
        self.win.mainloop()

if __name__ == "__main__":
    QRWindow().run()
