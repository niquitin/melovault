// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.102-open-beta/MeloVault-0.6.102-open-beta.apk",
  versionName: "0.6.102-open",
  versionCode: 127,
  sha256: "f7a20ad51b55fd7e0b153765e8067269574d33098837156a399437664ac738b4",
  sizeBytes: 31173169,
  releaseDate: "2026-11-08",
  notes: [
    "Liquid Glass hoàn chỉnh: màn Hai khung (máy ngang / máy tính bảng), mọi trang Cài đặt, Đồng bộ, hộp thoại và chế độ Sáng / Theo máy.",
    "Bỏ nhãn \"thử nghiệm\" của giao diện Liquid Glass.",
    "Chỉ khi chọn Liquid Glass trong Cài đặt › Giao diện & màu sắc; mặc định vẫn là giao diện cũ.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
