// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.111-open-beta/MeloVault-0.6.111-open-beta.apk",
  versionName: "0.6.111-open",
  versionCode: 136,
  sha256: "44648518e0430e70f28300b7b6baa592a994ec429125681479c28d6d2f66e620",
  sizeBytes: 31390477,
  releaseDate: "2026-11-11",
  notes: [
    "Ô tìm trong Thư viện giờ cho thấy cả video khớp: gõ từ khóa, dòng \"N video khớp ›\" hiện đầu danh sách, chạm để mở danh sách Video với từ khóa đó.",
    "Ô tìm trong mục Video tìm trong danh sách đã lưu trên máy trước (nhanh), có nút \"Tìm thêm trên Telegram\" nếu muốn tìm rộng hơn.",
    "Hộp \"Nguồn video\" và thẻ kênh ở Nguồn Telegram hiện số video app đã lưu của từng kênh.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
