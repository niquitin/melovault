// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.101-open-beta/MeloVault-0.6.101-open-beta.apk",
  versionName: "0.6.101-open",
  versionCode: 126,
  sha256: "56c7ac9a1f5cb03e649bf8db2cb24f4ecf059526fae47b4c77f77a46fbac6e01",
  sizeBytes: 31155541,
  releaseDate: "2026-11-08",
  notes: [
    "Liquid Glass (thử nghiệm): màn Đang phát, menu bài hát và hộp thoại có kiểu kính.",
    "Màn Đang phát đổi màu nền theo ảnh bìa; EQ, Hẹn giờ và menu ⋮ của bài hiện từ dưới lên như bảng kính.",
    "Chỉ khi chọn Liquid Glass trong Cài đặt › Giao diện & màu sắc; mặc định vẫn là giao diện cũ.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
