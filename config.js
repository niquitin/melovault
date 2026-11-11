// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.109-open-beta/MeloVault-0.6.109-open-beta.apk",
  versionName: "0.6.109-open",
  versionCode: 134,
  sha256: "250e5ef6f676c819a4e95176c53f73a16bb6ee840bab9b3732f640a08722c373",
  sizeBytes: 31341325,
  releaseDate: "2026-11-11",
  notes: [
    "Mới: nghe tiếng video nhạc như một bài hát: bấm ⋮ ở video › Nghe như nhạc, hoặc nút tai nghe 🎧 trong trình phát video (nghe tiếp đúng chỗ đang xem).",
    "Mới: nút Nghe cả danh sách ở mục Video. Tắt màn hình hay đóng app vẫn nghe, có điều khiển trên màn khóa; tiếng video không ghi vào Playlist hay Nghe tiếp.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
