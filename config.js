// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.106-open-beta/MeloVault-0.6.106-open-beta.apk",
  versionName: "0.6.106-open",
  versionCode: 131,
  sha256: "e6d3ebadfa50c74e0c56d13ec6a7fbe57602c7ab34001e2546ee8bd470a80ac9",
  sizeBytes: 31291565,
  releaseDate: "2026-11-11",
  notes: [
    "Video có menu ⋮ ở cuối mỗi dòng (hoặc nhấn giữ dòng): Tải về máy, Tạm dừng / Tiếp tục tải, Xóa khỏi máy, Mở trong Telegram, Chi tiết.",
    "Mục mới \"Đã tải\" trong Video: xem lại các video đã tải về máy, có tổng dung lượng và nút Xóa tất cả.",
    "Dòng video không còn ghi tên kênh dạng \"Chat -100…\" khi chưa biết tên.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
