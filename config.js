// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.99-open-beta/MeloVault-0.6.99-open-beta.apk",
  versionName: "0.6.99-open",
  versionCode: 124,
  sha256: "77ac3a9e55ba10e428f1348ea62f9459e403a7306d7360657b8ea27aad59199a",
  sizeBytes: 31104908,
  releaseDate: "2026-11-08",
  notes: [
    "Màn Đang phát hiện ảnh bìa ngay khi mở bài, không phải chờ bài tải xong.",
    "Đang có bản 0.6.98: cập nhật ngay trong app (Cài đặt → Giới thiệu → Cập nhật ứng dụng), không cần gỡ.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
