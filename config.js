// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.108-open-beta/MeloVault-0.6.108-open-beta.apk",
  versionName: "0.6.108-open",
  versionCode: 133,
  sha256: "6ada0cb11a8712b0e66613818fce0b63e100e7fdc1504197c8766110f9f148eb",
  sizeBytes: 31340717,
  releaseDate: "2026-11-11",
  notes: [
    "Chip Nhạc và Phim trong mục Video giờ đọc thẳng từng kênh nhạc/phim, nên không còn thưa dù bạn theo nhiều kênh tin tức.",
    "Hộp \"Nguồn video\" hiện số video của mỗi kênh.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
