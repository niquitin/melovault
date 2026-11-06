# Kế hoạch — Hợp nhất các tính năng thành phiên bản mới

**TASK_ID** `2026-11-06-hop-nhat-phien-ban` · 06/11/2026 · Claude Code (Opus 5.5).
**Yêu cầu của chủ dự án (chat 06/11):** đọc kỹ báo cáo Codex về nhạc theo chủ đề Telegram và gộp các tính năng hiện có thành một phiên bản mới.
Nhánh `dot/2026-11-06-hop-nhat-phien-ban`, worktree `D:\Bots\_worktrees\MeloVault-hop-nhat-phien-ban`, baseline `42be21cfe851ff5f7dd797de466b59c8381caeff` (đầu nhánh tích hợp, đã có trên remote nguồn).

## 1. Hiện trạng đã đối chiếu bằng Git

| Tính năng | Trạng thái | Commit merge |
|---|---|---|
| Nhạc theo chủ đề Telegram | ĐẠT 04/11, đã merge | `b583803` (source `e55ca7e`) |
| Bộ chỉnh âm EQ 10 dải | ĐẠT 06/11, đã merge | `5b11b3f` (source `cade4dc`) |
| Thiết kế lại Cài đặt | ĐẠT 02/11, đã merge | `563ce37` |
| Header không nháy khi cuộn | ĐẠT 02/11, đã merge | `9d84ba7` |
| Nhiều Playlist có tên | ĐẠT 01/11, đã merge | `2b222c3` |
| Lời nhạc P2-B | ĐẠT 01/11, đã merge | `b136f4d` |
| Giao diện đa thiết bị | ĐẠT 01/11, đã merge | `4517ce5` |
| Đăng nhập số điện thoại, ảnh bìa P2-A, dọn UI sau xoay, Đảo | ĐẠT 30–31/10, đã merge | `1416f1c`, `d1da309`, `b6f61a0` và các commit trước |
| **Widget Đang phát** | **Chưa ĐẠT, SƠ BỘ: CẦN SỬA, QA có bài BLOCKED** | Chưa merge (`dot/2026-11-03-widget-dang-phat`, `240f66b`) |

**Khoảng trống thật:** mã đã nằm chung trên nhánh tích hợp nhưng:

1. Chưa từng có APK build từ mã hợp nhất. APK EQ không có chủ đề Telegram; APK chủ đề Telegram không có EQ.
2. Chưa có QA thiết bị cho bản hợp nhất (HANDOFF 06/11 đã ghi rõ).
3. Mọi candidate cùng là vc121 / 0.6.96, không phân biệt được bằng số phiên bản.

## 2. Phạm vi

**Làm:**

- Nâng phiên bản Shareable: versionCode 121 → **122**, versionName `0.6.96` → **`0.6.97`** (chỉ khối flavor Shareable trong `app/build.gradle`). Đây là yêu cầu “phiên bản mới” của chủ dự án, không phải cách né vướng mắc cài đặt.
- Chạy lại gate Ni, build trên Tiny có Telegram config, cài Sony và QA tổ hợp.
- Hồ sơ APK/ảnh/báo cáo/bàn giao; upload Drive owner-only.

**Không làm:**

- **Không đưa Widget vào:** chủ dự án chưa ĐẠT, còn 2 đường lỗi đã ghi (`2026-11-03-widget-dang-phat-nghiem-thu-so-bo.md`), và merge thử xung đột ở `ShareablePlaybackService.kt`. CLAUDE.md mục 1 cấm tích hợp khi chưa ĐẠT. Sau khi sửa, widget đi bản kế tiếp.
- Không đổi tính năng, schema DB (giữ v3), songKey, package, signer, Personal (giữ vc61).
- Không phát hành APK công khai, không đụng `main`. Không merge/push trước khi chủ dự án ĐẠT bản này.

## 3. Các bước

| Bước | Việc | Nơi |
|---|---|---|
| S0 | Kiểm Git đầu phiên, baseline khớp | Ni |
| S1 | Nâng phiên bản, commit | Ni |
| S2 | `testShareableDebugUnitTest`, `testShareableReleaseUnitTest`, `assembleShareableDebug`, `assembleShareableRelease`, `-PmeloOpen=true assembleShareableDebug`, `assemblePersonalDebug`; đếm từ XML; `git diff --check` | Ni |
| S3 | Bundle, đối chiếu SHA-256 hai đầu, clone cô lập | Ni → Tiny |
| S4 | Build Debug + AndroidTest với Telegram config (chỉ báo boolean); kiểm package/version/cert/SHA | Tiny |
| S5 | Kiểm MediaSession không phát, cài `adb install -r`, đối chiếu APK đã cài | Tiny → Sony |
| S6 | QA Q1–Q6 | Sony |
| S7 | Ảnh tổng hợp, upload Drive, hồ sơ, dừng chờ sơ bộ/chủ dự án | Ni |

## 4. QA tổ hợp trên Sony (XQ-AS52, `QV725WEQ54`)

| Q | Thao tác | Kỳ vọng |
|---|---|---|
| Q1 | Nâng vc121 → vc122 bằng `install -r` | Không mất dữ liệu: DB v3, số bài không giảm, 2 mục yêu thích, vẫn đăng nhập |
| Q2 | Chạy lại `TelegramForumFeatureDeviceTest` trên bản hợp nhất | OK: danh mục chủ đề, queue vượt trang 200, Next trong A, duyệt B giữ queue, xoay/recreate |
| Q3 | Cài đặt → dòng Phiên bản | Hiện `0.6.97-beta (vc122)` |
| Q4 | **Điểm tổ hợp mới:** phát bài trong chủ đề → Đang phát → EQ → Bật, chọn mẫu | Bảng EQ mở, log `MeloEq` có RMS vào/ra và `clipped`, không crash, queue chủ đề không đổi |
| Q5 | Giữ EQ Bật, Next trong chủ đề, xoay/quay lại | Vẫn trong chủ đề, EQ vẫn Bật, không FATAL |
| Q6 | Smoke: tab Lời nhạc, tab Playlist | Mở được, không crash |

Kết thúc: EQ về Tắt/Phẳng, nhạc dừng, màn OFF trong `finally`, ghi `SONY_SCREEN_END`.

## 5. Tiêu chí xong

- Gate Ni: 0 failure/error ở cả Debug và Release; số test không giảm so với 2796.
- APK đúng commit, package `app.melovault.player`, vc122 / `0.6.97-beta`, cert `eecd3df7…fce8`, Telegram config=true.
- Q1–Q6 có bằng chứng hoặc ghi đúng CHƯA KIỂM/BLOCKED.
- Hồ sơ đủ; nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**.

## 6. Rủi ro, rollback, điều kiện dừng

- **vc122 là bước một chiều trên Sony:** sau khi cài, không cài lại được APK vc121 nếu không hạ cấp (bị cấm). Mọi nhánh mở sau đó, kể cả REWORK widget, phải lấy baseline từ bản này.
- Rollback: sửa tiến bằng bản vc cao hơn; không hạ APK/DB, không xóa dữ liệu. Tắt riêng từng tính năng bằng cờ sẵn có: `MeloEqPolicy.FEATURE_ENABLED`, `NamedPlaylistFlags.NAMED_ENABLED`.
- Dừng và ghi BLOCKED nếu: lệch chữ ký, thiếu config, ADB unauthorized, Sony đang phát nhạc, gate đỏ. Lỗi tổ hợp cần sửa logic thì ghi lại, không tự mở rộng phạm vi.

## 7. Cách chủ dự án thử

1. Cài APK, mở app: dữ liệu và đăng nhập còn nguyên.
2. Cài đặt: dòng Phiên bản ghi `0.6.97-beta (vc122)`.
3. Nguồn → nhóm có chủ đề → chọn chủ đề → Phát hết: chỉ phát bài trong chủ đề.
4. Đang phát → EQ → Bật, chọn mẫu: nghe thay đổi, không rè; bấm Tiếp vẫn trong chủ đề.
5. Xoay máy và quay lại: vẫn đúng chủ đề, EQ giữ nguyên.
6. Thử tab Lời nhạc và Playlist.

Phản hồi: `ĐẠT 2026-11-06-hop-nhat-phien-ban — <commit>/<SHA APK>` hoặc `Yêu cầu sửa 2026-11-06-hop-nhat-phien-ban: <lỗi>`.

## 8. Ghi chú quy trình

Chủ dự án giao trực tiếp cả việc gộp trong một phiên, nên kế hoạch và thực thi nằm cùng phiên Claude Code; phần code chỉ là nâng số phiên bản. Nghiệm thu sơ bộ vẫn phải ở phiên mới, và chủ dự án quyết định cuối.
