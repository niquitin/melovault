# BACKLOG — việc còn lại (18/10/2026)

Mỗi phiên làm đúng 1 việc: tạo `docs/plans/<việc>.md` (mục tiêu, phạm vi, file đụng, test, tiêu chí xong), làm, cập nhật `HANDOFF.md`, dừng. Việc đổi schema DB phải được chủ dự án duyệt trước.

## 1. Thanh A–Z (Thư viện)
- Thiếu so với đặc tả bước 7. Cần sắp theo tên trong DB (có thể đụng truy vấn/chỉ mục, hỏi duyệt nếu đổi schema), rồi thanh chữ cái trong `MeloLibraryPane` (Điện thoại/Tablet; Car không có A–Z).
- Chưa kiểm được lưới 3 cột Hai khung trên Y700 thật.

## 2. EQ
- Nút EQ ở Studio Deck hiện ghi "Sắp có". Cần quyết định phạm vi (EQ ExoPlayer/AudioEffect, bit-perfect, DSP FYT) trước khi code. Không đổi ExoPlayer/MediaSession khi chưa có plan.

## 3. Tìm không dấu
- DB dùng `LIKE`, phân biệt dấu: "su thanh hoa" không ra "Sử Thanh Hóa". Cần cột/chỉ mục chuẩn hóa không dấu, có thể đổi schema → phải được duyệt, kèm migration an toàn cho DB đang có.

## 4. Sửa test mạng flaky trên Tiny
- `AudioStreamingNetworkRecoveryTest.testNetworkDrop_5Seconds_SocketDisconnectAndRangeResume` (dòng ~194) fail lần chạy đầu sau mỗi lần fast-forward code trên Tiny (Beta94, Beta95; Debug + Release), pass khi `--rerun`; Ni luôn xanh. Cần tìm nguyên nhân (thời gian/socket/cold start) và làm ổn định, không bỏ qua test bằng skip/Ignore.
