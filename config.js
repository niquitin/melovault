// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.122-open-beta/MeloVault-0.6.122-open-beta.apk",
  versionName: "0.6.122-open",
  versionCode: 147,
  sha256: "78471e32221651b926c8434077af566e223bea70f546158779119f649a316807",
  sizeBytes: 31494873,
  releaseDate: "2026-11-13",
  notes: [
    "Giới thiệu có thêm dòng Chính sách quyền riêng tư (trang đầy đủ tiếng Việt và tiếng Anh).",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
