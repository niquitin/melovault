// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.117-open-beta/MeloVault-0.6.117-open-beta.apk",
  versionName: "0.6.117-open",
  versionCode: 142,
  sha256: "e8378093bf9b1579aedad6102852c8658f64d3698503e1822ade84c89825c031",
  sizeBytes: 31456013,
  releaseDate: "2026-11-12",
  notes: [
    "Tab Nguồn có thêm mục \"Kênh video\": app tự tìm mọi nhóm, kênh có video (kể cả nhóm không có nhạc và chat đã lưu trữ) và ghi số video thật. Chạm vào để xem video của kênh.",
    "Thẻ kênh nhạc nay ghi số video thật thay cho số đã lưu (ví dụ \"70 video\").",
    "Lần đầu app mất khoảng một phút để đọc danh sách và đếm; lần sau hiện ngay và chỉ cập nhật khi cần. Bấm \"Quét lại\" để làm mới.",
    "Đã thử trên điện thoại (Xanh đêm). Chưa thử trên máy tính bảng ngang và các kiểu màu Liquid Glass, Teal sáng: bạn nào có xin thử và báo giúp.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
