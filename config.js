// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.115-open-beta/MeloVault-0.6.115-open-beta.apk",
  versionName: "0.6.115-open",
  versionCode: 140,
  sha256: "bd055a8b6446e0d43c15809c1afd600c64788b93f0ee897f71f881578ce097ce",
  sizeBytes: 31439629,
  releaseDate: "2026-11-12",
  notes: [
    "Nhiều tài khoản Telegram: Cài đặt › Tài khoản hiện tài khoản đang dùng và nút \"+ Thêm tài khoản Telegram\" (số điện thoại khác).",
    "Ở màn đăng nhập thêm tài khoản có nút \"← Quay lại\" (hoặc phím Back) để về tài khoản cũ, thư viện giữ nguyên; thêm hoặc quay lại mất chưa tới 1 giây.",
    "Đang phát nhạc trên máy thì vẫn phát khi bấm Thêm tài khoản; bài Telegram dừng và \"Nghe tiếp\" nhớ đúng vị trí.",
    "Nút Đăng xuất ghi rõ tên tài khoản. Chuyển qua lại giữa hai tài khoản đã đăng nhập chưa thử trên máy thật: bạn nào có hai số xin thử và báo giúp.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
