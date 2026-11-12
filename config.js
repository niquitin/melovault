// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.114-open-beta/MeloVault-0.6.114-open-beta.apk",
  versionName: "0.6.114-open",
  versionCode: 139,
  sha256: "26fb2a197659298bb8e3ea4ec563f8ff9ea743a0e5db3617db3dfaa59414cbd7",
  sizeBytes: 31423245,
  releaseDate: "2026-11-12",
  notes: [
    "Chuẩn bị nền cho nhiều tài khoản Telegram: app lưu danh sách tài khoản (chưa có nút mới, dùng như cũ).",
    "Cài đè bản mới vẫn vào thẳng thư viện, không phải đăng nhập lại.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
