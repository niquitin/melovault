// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.125-open-beta/MeloVault-0.6.125-open-beta.apk",
  versionName: "0.6.125-open",
  versionCode: 150,
  sha256: "c5cf6f1de2e1b49cf293922357feb9025026b56563ca725e7d0d6f579c9a6126",
  sizeBytes: 34132169,
  releaseDate: "2026-11-14",
  notes: [
    "Thử nghiệm: nghe Bộ sưu tập TikTok công khai (dán link ở tab Nguồn)",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
