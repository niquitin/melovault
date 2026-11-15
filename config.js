// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.131-open-beta/MeloVault-0.6.131-open-beta.apk",
  versionName: "0.6.131-open",
  versionCode: 156,
  sha256: "0aa6d1c9002472831a1ed9c6759efa213bb422b1bc84c0b604f866dda1600ed5",
  sizeBytes: 34214869,
  releaseDate: "2026-11-15",
  notes: [
    "Tab Telegram đổi tên thành Nguồn — nơi gom Telegram, TikTok.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
