// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.132-open-beta/MeloVault-0.6.132-open-beta.apk",
  versionName: "0.6.132-open",
  versionCode: 157,
  sha256: "da39e8ea032f945da4354ed35d91228e835626937aaf150f98245d7f21fcf170",
  sizeBytes: 34214897,
  releaseDate: "2026-11-15",
  notes: [
    "Xem video TikTok dạng dọc: vuốt lên/xuống đổi video, nút Xem video ở Đang phát, thoát thì nghe tiếp đúng chỗ.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
