// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.110-open-beta/MeloVault-0.6.110-open-beta.apk",
  versionName: "0.6.110-open",
  versionCode: 135,
  sha256: "6f102ff4b993fa2607d378603db2b62666e7d47d86eed343429a5261fca26905",
  sizeBytes: 31390477,
  releaseDate: "2026-11-11",
  notes: [
    "Mục Video mở tức thì: app nhớ danh sách video nhạc/phim của các kênh, mở lại là thấy ngay.",
    "App tự cập nhật danh sách video mới ở nền khi mở app, không làm chậm nghe nhạc.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
