// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.118-open-beta/MeloVault-0.6.118-open-beta.apk",
  versionName: "0.6.118-open",
  versionCode: 143,
  sha256: "451717f76b511d22184ac6e3f2c161503cb3d96948fd1b0819b5aef45384f0a3",
  sizeBytes: 31472397,
  releaseDate: "2026-11-12",
  notes: [
    "Mục Video có thêm chip \"Khác\" (sau Phim) để xem video không rõ là nhạc hay phim; \"Tất cả\" vẫn chỉ nhạc và phim như trước.",
    "Các nhóm, kênh có video (mục Kênh video ở tab Nguồn) nay cũng được lưu sẵn để mở nhanh và tìm được ở Thư viện.",
    "Hộp \"Nguồn video\" có thêm các kênh này để bạn chọn Nhạc, Phim hoặc Ẩn; kênh đã Ẩn ghi \"đã ẩn ở mục Video\" ở thẻ Kênh video.",
    "Đã thử trên điện thoại (Xanh đêm). Chưa thử trên máy tính bảng ngang và các kiểu màu Liquid Glass, Teal sáng: bạn nào có xin thử và báo giúp.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
