// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.119-open-beta/MeloVault-0.6.119-open-beta.apk",
  versionName: "0.6.119-open",
  versionCode: 144,
  sha256: "b0b9dfb7ccdb13b1a7ce62bf3b0298cdc809394ca5546207551011f46cc12902",
  sizeBytes: 31477861,
  releaseDate: "2026-11-13",
  notes: [
    "Cài đặt có biểu tượng mới đồng bộ: mũi tên, nút chọn, công tắc bật/tắt, đầu trang Đồng bộ",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
