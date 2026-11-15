// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.129-open-beta/MeloVault-0.6.129-open-beta.apk",
  versionName: "0.6.129-open",
  versionCode: 154,
  sha256: "00fa789a4e01926126e177b9dae6fc821f6d8cbde9487fb4a30faad5838ecd3a",
  sizeBytes: 34214209,
  releaseDate: "2026-11-15",
  notes: [
    "Đầu Thư viện gọn hơn: bỏ chip thể loại, thêm thẻ TikTok, ba thẻ nguồn bằng nhau",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
