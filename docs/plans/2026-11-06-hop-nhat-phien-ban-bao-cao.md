Thực thi: Claude Code, Opus 5.5, phiên 06/11/2026 (cùng phiên kế hoạch theo yêu cầu trực tiếp của chủ dự án).

# Báo cáo — Hợp nhất các tính năng thành phiên bản mới

**TASK_ID** `2026-11-06-hop-nhat-phien-ban` · **QA CANDIDATE — CHƯA NGHIỆM THU.** Chưa merge, chưa push, chưa phát hành công khai.

| Mục | Kết quả |
|---|---|
| Nhánh / worktree | `dot/2026-11-06-hop-nhat-phien-ban` / `D:\Bots\_worktrees\MeloVault-hop-nhat-phien-ban` |
| Baseline | `42be21cfe851ff5f7dd797de466b59c8381caeff` |
| Source APK | `7cd4ebb0af7a3f75c6a5c038831f7720b24319df` (kế hoạch `c00e15e`, nâng phiên bản `7cd4ebb`) |
| Thay đổi mã | Chỉ `app/build.gradle`: Shareable vc121 → **vc122**, `0.6.96` → **`0.6.97`**. Không đổi tính năng, schema, package, signer |
| APK | [hồ sơ APK](2026-11-06-hop-nhat-phien-ban-apk-test.md): 33.894.510 B, SHA-256 `BF7B23A74588BD9826CA9DFBFEBE0AEC5DEC61F8F20D5CD5914CCDDC38C56441` |
| Ảnh | [hồ sơ ảnh](2026-11-06-hop-nhat-phien-ban-hinh-nghiem-thu.md) |

## 1. Kết quả đọc báo cáo Codex

Đã đọc báo cáo, nghiệm thu, bàn giao, APK của `2026-11-03-chu-de-nhac-telegram` và đối chiếu Git:

- Chủ đề Telegram **đã ĐẠT và đã merge** từ 04/11 (`b583803`), không còn gì phải gộp bằng Git. EQ cũng đã merge (`5b11b3f`). Nhánh tích hợp `42be21c` trùng remote nguồn.
- Khoảng trống thật là: chưa có APK và QA thiết bị của bản hợp nhất, mọi candidate cùng vc121.
- **Widget Đang phát không được đưa vào:** chưa ĐẠT, `SƠ BỘ: CẦN SỬA` với 2 đường lỗi (chặn Tiếp bị mở sớm khi metadata đổi; widget báo trống khi bài bắt đầu từ yêu cầu chờ kết nối), QA có bài BLOCKED, merge thử xung đột ở `ShareablePlaybackService.kt` và `HANDOFF.md`.

## 2. Gate Ni

Mỗi biến thể `testShareableDebugUnitTest` / `testShareableReleaseUnitTest`: **207 suite, 2.796 test, 2.775 đạt, 21 skipped, 0 failure, 0 error** (đếm từ XML), bằng số của gate sau merge EQ.

| Lệnh | Kết quả |
|---|---|
| Test Debug + Release | BUILD SUCCESSFUL 2m21s |
| `assembleShareableDebug` + `assembleShareableRelease` | BUILD SUCCESSFUL 1m03s |
| `-PmeloOpen=true assembleShareableDebug` | BUILD SUCCESSFUL 17s |
| `assemblePersonalDebug` | BUILD SUCCESSFUL 19s |
| `git diff --check 42be21c HEAD` | Sạch |

Log private: `docs/plans/artifacts/2026-11-06-hop-nhat-phien-ban/ni-*.log`. APK Ni không có Telegram config, không cài.

## 3. Build Tiny

Bundle `hop-nhat-7cd4ebb.bundle`, 14.005.637 B, SHA-256 `6EB3A23F7B8F784C47D5636E4EA6E1EFA9113E7933A4A04395AC0BCE701C2FE2`, khớp Ni/Tiny, `git bundle verify` đạt. Clone cô lập mới `C:\Bots\MeloVault-HOPNHAT-IsolatedQA-20261106-7cd4ebb`, detach đúng source. JDK 17, config qua `meloLocalPropertiesFile` đã có trên Tiny, không đọc/chép. `telegram.config.valid=true`, BUILD SUCCESSFUL 3m32s, exit 0. Log/script: `C:\Bots\_qa_temp\hop-nhat-20261106-7cd4ebb\`.

## 4. QA Sony

Thiết bị: Sony XQ-AS52, `QV725WEQ54`, Android 12, qua Tiny. Trước cài: vc121 (`e55ca7e`), không phiên phát.

| Q | Hành động | Kết quả thực tế | Trạng thái |
|---|---|---|---|
| Q1 | `adb install -r` vc121 → vc122 | Success; APK pull từ máy khớp SHA. Trước và sau: DB v3, **25.569 bài, 2 yêu thích**. Sau đó đồng bộ nền tăng lên 25.612 bài. Vẫn đăng nhập. Chỉ so số lượng, không so từng khóa | PASS |
| Q2 | Chạy `TelegramForumFeatureDeviceTest` | OK (1 test), 32,2 s. Queue 226/226 vượt trang 200, Next trong A, duyệt B giữ queue, Back/touch-mode không crash, xoay/recreate đúng, 200 khóa mẫu resolve | PASS |
| Q3 | Cài đặt → Giới thiệu | Dòng Giới thiệu `0.6.97-beta`; trang Giới thiệu `0.6.97-beta (vc122)`. Lần chạm đầu không mở trang, lần sau mở đúng; chưa rõ nguyên nhân, không tái hiện lại | PASS |
| Q4 | **Tổ hợp:** chủ đề LUSH (16 bài) → Phát hết → Đang phát → EQ → Bật → Tăng bass | Bảng EQ đúng; log `MeloEq`: vào −11,8 dB, ra −15,1 dB, `clipped=0`, 1.447 µs/buffer; chip `EQ · Tăng bass` | PASS (log, không phải nghe) |
| Q5 | Giữ EQ Bật: Next, xoay ngang, về dọc, Home, quay lại | Next sang bài kế **cùng chủ đề**, tiến trình chạy (00:12 → 00:54 khi xoay). EQ giữ nguyên. Log: `clipped=0` ở cả hai cửa sổ sau đó. PID 8660 suốt lượt, 0 `FATAL EXCEPTION` | PASS |
| Q6 | Smoke tab Lời nhạc, Playlist | Lời nhạc mở, báo thiếu tên/nghệ sĩ và có nút Tìm lời; Playlist hiện Yêu thích 2 bài, Tạo Playlist | PASS smoke |

Ảnh tổng hợp đã xem trực tiếp. Chạm lỗi của công cụ QA, không phải app: một lần chuỗi tiếng Việt lỗi mã hóa khiến helper tự dừng; một lần helper chạm vào (0,0) trên thanh trạng thái trước khi được sửa, không có tác động.

**Quan sát nhỏ, chưa sửa (ngoài phạm vi):**

- Xoay ngang màn Đang phát: chip `Hẹn giờ` lệch xuống và bị cắt đáy so với chip EQ (`M12_landscape`). Hồ sơ EQ ghi xoay ngang chưa kiểm, nên đây là lần đầu thấy; chưa xác định có từ bản nào.
- Dọc: hàng `Tiếp theo | Lời nhạc` bị che một phần phía dưới khi chưa cuộn (`M11_next_lyrics`).

**Trạng thái cuối Sony:** EQ Tắt, Phẳng; nhạc tạm dừng (`state=2`); `user_rotation=0`, `accelerometer_rotation=1` đã khôi phục về đúng gốc; không đổi tài khoản/dữ liệu. **`SONY_SCREEN_END`: Display State=OFF.**

## 5. Chưa kiểm chứng

- **CHƯA XÁC NHẬN ÂM THANH THỰC** của bản hợp nhất. Log EQ không thay cho nghe.
- Phát offline trong chủ đề, mất mạng; FLAC/WAV document trên máy; tai nghe/Bluetooth.
- Shuffle/Previous thực riêng trong chủ đề; Gọn/Car/FYT, Y700/S25; chữ lớn; Release/`.open`/Personal chạy trên máy.
- Tải APK từ Drive bằng trình duyệt: chưa thử. APK chủ đề Telegram và Widget từng bị Google chặn 403 (`cannotDownloadAbusiveFile`) trên một tài khoản Drive khác, nên có thể gặp lại.
- Các giới hạn đã chấp nhận của từng nhiệm vụ trước vẫn giữ nguyên (HANDOFF).

## 6. Rủi ro

Sony đã ở vc122: không cài lại được APK vc121 nếu không hạ cấp (bị cấm). Nhánh sửa Widget phải lấy baseline từ bản này. Rollback bằng bản sửa tiến.

[Kế hoạch](2026-11-06-hop-nhat-phien-ban.md) · [Bàn giao](2026-11-06-hop-nhat-phien-ban-ban-giao.md) · [Nghiệm thu](2026-11-06-hop-nhat-phien-ban-nghiem-thu.md).
