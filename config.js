// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.124-open-beta/MeloVault-0.6.124-open-beta.apk",
  versionName: "0.6.124-open",
  versionCode: 149,
  sha256: "98db6c93cfb10725ec32f4ee5e7cafc7f0d22ce3b32a1fe3f4d0cf0ecd764bf4",
  sizeBytes: 34082985,
  releaseDate: "2026-11-14",
  notes: [
    "Mới: Thêm \"Điều khoản sử dụng\" ở Cài đặt → Giới thiệu và ở màn đăng nhập (trang melovault.pages.dev/terms, có cách gửi khiếu nại bản quyền).",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
