// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.126-open-beta/MeloVault-0.6.126-open-beta.apk",
  versionName: "0.6.126-open",
  versionCode: 151,
  sha256: "1900ce1c42b25651d514d83dbab00539cda6074ddcf76615526e9e71533c4cb5",
  sizeBytes: 34148673,
  releaseDate: "2026-11-14",
  notes: [
    "Thử nghiệm: bấm Chia sẻ trên TikTok để gửi Bộ sưu tập sang MeloVault, tải cả bộ về nghe offline (⋮), Nghe tiếp bài TikTok",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
