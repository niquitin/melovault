# MeloVault Shareable Telegram Preview — thử nghiệm trên Sony

**Trạng thái:** OWNER-ONLY INTERNAL PREVIEW — CHƯA PHÁT HÀNH CHO BẠN BÈ.

## Đã có
- APK riêng `app.melovault.player` / nhãn **MeloVault Preview**, không ghi đè `com.bsdung.musicassistantcar` (MeloVault Private Stable).
- TDLib 1.8.68 native JSONJava ARM64 build từ nguồn chính thức, SHA-256:
  `2e09562262268a1363939e720be5519187533113e021a558cc6fa89fb4e23d28`.
- Telegram developer API ID/hash lấy từ Lenovo DPAPI và nhập vào BuildConfig chỉ cho Shareable; **không đưa thông tin thực vào Git, log, handoff**. Đây là định danh ứng dụng và có thể trích xuất từ APK; không phải OTP hay session người dùng.
- TDLib API thực sự trả về `authorizationStateWaitPhoneNumber` trên Sony Xperia 5 II, Android 12. Khắc phục lỗi gửi trùng `setTdlibParameters`.
- Form đăng nhập qua số điện thoại → mã Telegram → mật khẩu hai bước/email (khi được Telegram yêu cầu), không ghi lại các dữ liệu này.
- Sau khi đăng nhập và TDLib `getMe` xác minh ID người dùng: thử duyệt 60 tin nhắn gần nhất trong Saved Messages, lọc audio/MP3/FLAC/M4A/WAV, nhấn để TDLib tải và phát bằng Media3.
- TDLib database trong `noBackupFilesDir` theo slot ngẫu nhiên; khóa 32 byte mã hóa AES-GCM bằng Android Keystore. Không có đường truyền hoặc tài khoản của backend cá nhân trong Shareable.

## Hướng dẫn thử nghiệm trên Sony
1. Mở biểu tượng **MeloVault Preview** (KHÔNG phải MeloVault Private).
2. Nhập số Telegram của bạn, ví dụ `+84...`, trong chính ứng dụng Sony.
3. Nhập mã đăng nhập Telegram mà Telegram gửi và mật khẩu 2FA nếu có — **KHÔNG gửi OTP/mật khẩu/API Hash vào ChatGPT**.
4. Khi màn hình xác nhận đăng nhập thành công, chọn **Tải danh sách nhạc Saved Messages**.
5. Chọn một bài MP3 hoặc FLAC nằm trong 60 tin nhắn mới nhất đã lưu và nghe thử. Nếu trống, gửi file nhạc vào Saved Messages rồi thử làm mới.
6. Chỉ báo lại các trạng thái/lỗi không nhạy cảm (ví dụ: `login success`, `list empty`, `song not playing`, `error 400`). Không chụp hoặc gửi mã OTP/2FA.

## Chưa hỗ trợ
- Chưa xác minh **end-to-end** đăng nhập thật + tải/phát nhạc (cần người dùng thực hiện thử nghiệm trên Sony). Hiện chỉ nghiệm thu đến màn nhập số Telegram.
- Mới duyệt Saved Messages (60 tin gần nhất), chưa duyệt nhóm/kênh khác.
- Chưa có màn hình Google Drive OAuth, tìm kiếm toàn thư viện, playlist, phát nền qua MediaLibraryService, điều khiển phím vô lăng, stream theo byte-range hoặc hai tài khoản độc lập.
- Bản Preview chưa được duyệt bảo mật đủ để phát hành công khai. Không gửi APK cho bạn bè.
- Thoát Activity có thể ngừng phát, và không lưu playlist.

## Giới hạn và bảo mật
- APK preview là bản debug thử nghiệm; Google/Telegram có thể yêu cầu thêm xác minh khi phát hành công khai.
- Mã nguồn TDLib và OpenSSL đi kèm các điều khoản giấy phép riêng; phân phối công khai phải kèm thông báo giấy phép đầy đủ.
- **Không xóa dữ liệu/gỡ MeloVault Private**, không đụng M3/FYT/backend production.
- Lỗi TDLib hiện trạng được hiển thị mã và nhóm lỗi mà không in mã xác thực. Việc xác thực đầy đủ và tải nhạc vẫn cần thử nghiệm thực tế.
