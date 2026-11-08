// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.98-open-beta/MeloVault-0.6.98-open-beta.apk",
  versionName: "0.6.98-open",
  versionCode: 123,
  sha256: "cc532b21960d644a727fcb56cc258725b196fa9e6de232dc0134ab4b8f65bf22",
  sizeBytes: 31104908,
  releaseDate: "2026-11-08",
  notes: [
    "Tự báo khi có bản mới (Cài đặt → Giới thiệu → Cập nhật ứng dụng), chỉ tải khi bạn đồng ý.",
    "Ký bằng khóa phát hành chính thức: các bản sau cài đè hoặc cập nhật trong app.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
