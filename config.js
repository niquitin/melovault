// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.100-open-beta/MeloVault-0.6.100-open-beta.apk",
  versionName: "0.6.100-open",
  versionCode: 125,
  sha256: "6008836b7acda28b225ef6cfc8b0e5ec8c9e6d6cf40216ebddbe58fffdad8216",
  sizeBytes: 31138237,
  releaseDate: "2026-11-08",
  notes: [
    "Thêm giao diện Liquid Glass (thử nghiệm): Cài đặt › Giao diện & màu sắc.",
    "Nền đổi màu theo ảnh bìa, thanh đáy kính nổi; mặc định vẫn là giao diện cũ.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
