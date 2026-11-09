// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.103-open-beta/MeloVault-0.6.103-open-beta.apk",
  versionName: "0.6.103-open",
  versionCode: 128,
  sha256: "447438b1e618e72bd124536ec1d1c56f03f74c26ee88287aa4dd171b3a525857",
  sizeBytes: 31191873,
  releaseDate: "2026-11-09",
  notes: [
    "Giao diện Liquid Glass thành mặc định (ai đã tự chọn kiểu màu khác vẫn giữ).",
    "Đang phát: nút gọn bằng biểu tượng — Lưu, Đảo, Lặp, Hàng đợi, EQ, Hẹn giờ. Nhấn giữ một nút để xem tên.",
    "Thông tin chất lượng gộp một dòng; nút tua 30/60 giây dạng vòng có số; giờ đã chạy và tổng ở hai đầu.",
    "Muốn giao diện cũ: Cài đặt › Giao diện & màu sắc › Xanh đêm.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
