// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.112-open-beta/MeloVault-0.6.112-open-beta.apk",
  versionName: "0.6.112-open",
  versionCode: 137,
  sha256: "b025a069b2fa11c58d40ad9a39ae10e2ecc2bd605a8dc9c25677bb42ea41637e",
  sizeBytes: 31390477,
  releaseDate: "2026-11-12",
  notes: [
    "Mục Video rộng hơn: kéo danh sách xuống thì phần đầu trang (tiêu đề, ô tìm, các tab, nút Video) tự gập lại, thấy nhiều video hơn; kéo lên lại hiện ra.",
    "Bỏ dòng \"Phạm vi: Video\" thừa ở đầu danh sách Video.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
