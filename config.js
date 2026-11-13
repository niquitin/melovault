// Chỉ điền thông tin APK được chủ dự án cho phép phân phối.
// Không đưa liên kết riêng tư, credentials hoặc token bí mật vào đây.
// Sửa cùng lúc với update.json: url = apkUrl, versionName, versionCode, sha256, sizeBytes, releaseDate, notes phải khớp.
window.MELOVAULT_RELEASE = {
  url: "https://github.com/niquitin/melovault/releases/download/v0.6.121-open-beta/MeloVault-0.6.121-open-beta.apk",
  versionName: "0.6.121-open",
  versionCode: 146,
  sha256: "10199c9713f12408d69e11810375fe82a9165431345c7fcb20d0bd41446f079e",
  sizeBytes: 31494873,
  releaseDate: "2026-11-13",
  notes: [
    "Mở lại app là \"Nghe tiếp\" đúng chỗ đang nghe, kể cả khi app bị tắt lúc đang phát (vị trí được nhớ mỗi 10 giây ngay cả khi màn app đã đóng)",
    "Nhớ cả bài Nhạc trên máy để nghe tiếp; thẻ đáy ghi bài nghe sau cùng của Telegram hoặc Nhạc trên máy",
    "Thẻ Nghe tiếp hiện thời điểm sẽ phát tiếp, ví dụ \"Nghe tiếp · 12:34 · tên kênh\"",
    "Bài Telegram chưa tải xong thì Nghe tiếp vẫn phát từ đầu (bản sau sẽ sửa); bản Gọn, máy tính bảng ngang và Car Mode chưa thử trên máy thật, bạn dùng các kiểu này nhớ báo giúp",
    "Đang có bản 0.6.97: phải gỡ bản đó rồi cài bản này một lần (mất dữ liệu trong app).",
    "Ứng dụng không chính thức, dùng Telegram API; không liên kết với Telegram."
  ]
};
