// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.130-open-beta/MeloVault-0.6.130-open-beta.apk",
  versionName: "0.6.130-open",
  versionCode: 155,
  sha256: "3a13baca16f053e3262308b62f942898ee7efbe58cbe525243b18f256481b698",
  sizeBytes: 34214209,
  releaseDate: "2026-11-15",
  notes: [
    "Nhóm thử trên Google Play nay cũng có Bộ sưu tập TikTok (bản tải từ GitHub không đổi chức năng).",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
