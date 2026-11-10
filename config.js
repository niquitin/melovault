// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.105-open-beta/MeloVault-0.6.105-open-beta.apk",
  versionName: "0.6.105-open",
  versionCode: 130,
  sha256: "79a5af039ab378370c1fea4fb687fde7c0cea7a8973079427c0f25200bddc277",
  sizeBytes: 31258797,
  releaseDate: "2026-11-10",
  notes: [
    "Xem video mượt hơn: tua tới đoạn chưa tải thì tải ngay từ đoạn đó, không phải chờ tải tuần tự.",
    "Video gửi dạng tệp (.mkv, .mp4) cũng hiện trong danh sách (dòng \"Tệp MKV\"); video có bản nhẹ thì chọn chất lượng ở nút trên trình phát (Tự động tiết kiệm dữ liệu khi dùng mạng di động).",
    "Hình trong hình: bấm Home khi đang xem để tiếp tục xem trong cửa sổ nhỏ.",
    "Màn dọc có tiêu đề, chú thích và danh sách \"Tiếp theo\"; hết video tự chuyển sang video kế sau 5 giây (tắt được); chạm đúp nửa trái/phải để lùi/tới 10 giây.",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
