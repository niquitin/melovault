// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.104-open-beta/MeloVault-0.6.104-open-beta.apk",
  versionName: "0.6.104-open",
  versionCode: 129,
  sha256: "17316293bd2862ecf52572ee5ab3d1212b1151da8b8b23aa49944962db39c553",
  sizeBytes: 31242389,
  releaseDate: "2026-11-09",
  notes: [
    "Mới: xem video nhạc từ kênh Telegram — Thư viện › chip \"Video\" (gộp các kênh nhạc) hoặc mở một kênh › \"Video trong kênh\".",
    "Chạm video để xem ngay khi đang tải; xoay ngang để xem toàn màn, tua, lùi/tới 10 giây, chuyển video trước/kế.",
    "Mở video thì nhạc tự tạm dừng.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
