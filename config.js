// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.123-open-beta/MeloVault-0.6.123-open-beta.apk",
  versionName: "0.6.123-open",
  versionCode: 148,
  sha256: "e3549a17466e5a9e3ca64bbcea7f4e8bcfd9e141f769901c91c80fdcd38df9b4",
  sizeBytes: 34082985,
  releaseDate: "2026-11-14",
  notes: [
    "Mới: Dùng thử không cần đăng nhập — nút ở màn đăng nhập mở trình phát với 4 bài mẫu và nhạc trên máy, không cần tài khoản Telegram",
    "Link \"Chính sách quyền riêng tư\" ở màn đăng nhập trỏ trang chính sách mới",
    "Chạm \"Dùng thử không cần đăng nhập\" rồi phát bài, mở Đang phát, EQ; thoát bằng nút Đăng nhập hoặc Back",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
