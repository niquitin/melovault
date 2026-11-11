// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.107-open-beta/MeloVault-0.6.107-open-beta.apk",
  versionName: "0.6.107-open",
  versionCode: 132,
  sha256: "3718eb03bbf3bbe6674864916bdb17f5b99b5a26e9dea60dd38662c65fe78acc",
  sizeBytes: 31307949,
  releaseDate: "2026-11-11",
  notes: [
    "Mục Video giờ chỉ có video nhạc và phim: video tin tức được ẩn tự động.",
    "Chip Nhạc / Phim để lọc; \"Nguồn video\" để chọn kênh nào là nhạc, phim hoặc ẩn hẳn.",
    "Bấm ⋮ ở một video để ẩn nhanh cả kênh đó.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
