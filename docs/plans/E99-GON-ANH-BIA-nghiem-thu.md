# E99-GON-ANH-BIA — Biên bản nghiệm thu (Opus)

**Vai trò:** Claude Opus nghiệm thu độc lập, 25/10/2026 ~16:09–16:30, theo `docs/workflow/PROMPT_OPUS_NGHIEM_THU.md` và `CLAUDE.md` của nhánh `dot/workflow-opus-nghiem-thu` (`ffcc584`, chưa merge — "ChatGPT" trong tài liệu cũ đọc là Opus nghiệm thu).

| Vòng | Thời điểm | Trạng thái |
|---|---|---|
| **1 — hiện tại** | 25/10 ~16:30 | **BLOCKED — CHỜ CHỦ DỰ ÁN** (kỹ thuật đạt; Janky lạnh cần chủ dự án cảm nhận; chờ duyệt ảnh/cài vc114) |

## 1. Git

| Mục | Kết quả |
|---|---|
| Nhánh / HEAD lúc nghiệm thu | `dot/E99-GON-ANH-BIA` @ `08cf746` (báo cáo Sonnet), cây sạch; commit cuối 15:42, bắt đầu nghiệm thu 16:09 (27 phút) — không agent nào đang sửa nhánh |
| Nền | `feature/e93-03d-y700-offline-cockpit` @ `455c8af` → kế hoạch `d0c8531` → S1 `3f1efa0`, S2 `713dd80`, S3 `a606bd7` (nguồn APK), báo cáo `08cf746` |
| `git diff --stat 455c8af..HEAD` | 12 file, +466/−21: 8 file production (+81/−21, gồm `app/build.gradle`), 2 file test (+41), kế hoạch, báo cáo |
| `git diff --check 455c8af..HEAD` | sạch |
| File ngoài phạm vi | **không có** — đúng bảng §4 kế hoạch. Không đụng `MeloRowArtLoader`/`MeloRowArtPolicy`, DB/schema, TDLib handler, Media3, palette/kích thước Gọn, Car Mode, Studio Deck |
| versionName (§11 kế hoạch cần Opus xác nhận) | vc114 / `0.6.90-beta` — **đồng ý**: nhiệm vụ mới tăng số thứ ba (E98 vc112 = 0.6.89, rework vc113 = 0.6.89.1) |

## 2. Đọc mã rủi ro

- `TelegramPreviewActivity.applyMeloLayer` (nhánh Gọn, ≈4764–4787): `rowArt = MeloThemePolicy.rowArtLayer(load(prefs), null, COMPACT)` tính **trước** `key`; khóa `MeloCompactPolicy.layerKey(..., rowArt)` thêm `:art`; `rowArtThemeActive = rowArt` gán **sau** `detachMeloLayer()` (hàm này đặt lại `false`) — đúng thứ tự bắt buộc §3.3.
- `showNewLayer` (≈4837): `rowArtLayer(theme, layer, profile)` với `theme` đã là `effective(...)`, nên với Phone/Tablet kết quả = `theme == TEAL_BRIGHT` như cũ (test khóa `rowArtLayer == appliesTo`).
- `MeloComponents.songRow`: `(teal || isCompact) && artKey != null` → `rowArtView`; Car không bao giờ có `artKey`. `findRowArtFrame` = thân `findArtHost` cũ, chỉ di chuyển (`MeloLibraryPane` dùng lại, logic không đổi).
- `MeloCompactScreen`: listener gắn ở `attach`, gỡ ở `detach`; không yêu cầu khi FLING (`MeloRowArtPolicy.shouldLoad`), bỏ qua lưới nguồn; log chỉ số đếm.
- Tắt công tắc: `snapshot()` chỉ gắn `artKey` khi `rowArtOn()` (theme layer **và** công tắc) ⇒ `artKey = null` → ô chữ cái; `requestRowArt` gọi `reset()` loader khi tắt. Ảnh còn thấy ở vài bài đã tải khi tắt = `art = thumbnailCache.get(...)` (`TelegramPreviewActivity.kt:5103`), cơ chế có sẵn từ trước E99 — không phải lỗi.
- TDLib/tài khoản: chỉ qua loader E98 (khóa có accountId, ≤ 3 inflight, `reset()` khi đăng xuất). Không ghi/xóa cache/DB.
- Ghi chú nhỏ (không chặn): đổi Kiểu màu Teal → Hiện tại khi đang ở Gọn không gọi `reset()` loader (màn Gọn mới không gọi `requestRowArt` nữa) nên bộ nhớ đệm ảnh nhỏ (~200 KB) giữ tới lần reset kế — cùng hành vi khung Teal của E98.

## 3. Test và build Opus tự chạy lại (Ni, 16:10–16:12)

`gradlew :app:testShareableDebugUnitTest --rerun :app:testShareableReleaseUnitTest --rerun :app:assembleShareableDebug :app:assembleShareableRelease --console=plain` → `BUILD SUCCESSFUL in 1m 48s`. XML `app/build/test-results` (166 file mỗi biến thể, giờ ghi 16:11/16:12): **Debug 2042 / 21 bỏ qua / 0 lỗi; Release 2042 / 21 / 0**. Nền 2038 → +4 (đúng 4 test mới). Khớp báo cáo Sonnet.

## 4. Bảng kiểm theo tiêu chí PASS/FAIL (kế hoạch §8)

| Tiêu chí | Kết quả | Bằng chứng |
|---|---|---|
| Test 0 lỗi, ≥ 2038 + mới, bỏ qua = 21; build OK; diff --check sạch; đúng file §4 | **ĐẠT** | mục 1, 3 |
| Shanling 1: 8 dòng, dòng 52 dp, ảnh 40 dp, thanh đáy 102 dp, cửa sổ 574 dp | **ĐẠT** | `logs/ui_02.xml` (báo cáo §5): dòng 104 px, khung ảnh 80×80 px, đáy 204 px, cửa sổ 768×1208 px @2,0; ảnh 09 |
| Shanling 2: ≥ 80 % dòng tab Tất cả có ảnh sau 5 s, ảnh đúng dòng | **ĐẠT** | ảnh 09: 8/8; logcat `compact visible=9 withArt=9 settled=9` |
| Shanling 3: không ANR/crash, inflight ≤ 3 | **ĐẠT** | crash buffer 0; logcat inflight 0/1/2/3 = 127/51/24/10 lần, không > 3 |
| Shanling 3: Janky không tăng > 5 điểm so với vc113 | **ĐẠT khi cùng điều kiện; số đầu tiên vượt — cần chủ dự án cảm nhận** | mục 5 |
| Shanling 4: phát bài đã tải → 3 rồi 2; dòng đang phát có ảnh; thẻ mở Đang phát | **ĐẠT** | ảnh 13, 15; `media_session` 3 → 2 |
| Shanling 5: tắt công tắc → ô chữ cái, không request; bật lại → ảnh về | **ĐẠT** | ảnh 22, 28; `logs/logcat_rowart_off.txt`: dòng `MeloRowArt` đầu tiên 15:36:40 (lúc bật lại), không dòng nào trong lúc tắt |
| Shanling 6 (tùy chọn): Nhạc trên máy | **ĐẠT** | ảnh 30: ảnh nhúng + ô chữ cái, không crash |
| Màu Gọn không đổi | **ĐẠT** | ảnh 01 (vc113) vs 09/22: nền, chữ, thanh đáy, ô chữ cái giống; không sửa palette |
| Car Mode, Phone/Tablet không đổi hành vi | **ĐẠT theo mã + test; Sony hồi quy MỘT PHẦN** | Sony vc114 Hiện tại: giao diện cũ, không log `MeloRowArt` (ảnh 32). Teal Phone trên Sony **chưa kiểm** (kế hoạch: không chặn) |
| Log chỉ số đếm; không ghi/xóa cache/DB; không sửa loader | **ĐẠT** | 3 tệp logcat chỉ có `sent/hits/decoded/.../inflight` và `compact visible/withArt/settled` |
| Chủ dự án xem ảnh / cài vc114 và nói ĐẠT | **CHỜ** | — |

## 5. Janky (Shanling, cùng kịch bản 10 swipe) — Opus đọc lại `logs/gfx_*.txt`

| Phép đo | Khung | Janky | p90 |
|---|---|---|---|
| vc113, app chạy lâu (ấm) — nền | 623 | 0,80 % | 9 ms |
| vc114 ảnh BẬT, lần đầu ngay sau cài | 597 | **8,21 %** | 15 ms |
| vc114 ảnh BẬT, lần 2 / lần 3 (ấm) | 615 / 615 | 2,44 % / 1,30 % | 12 / 10 ms |
| vc114 ảnh TẮT (ấm) | 620 | 0,00 % | 8 ms |
| vc114 ảnh TẮT / BẬT, sau `force-stop` (lạnh) | 611 / 599 | 3,27 % / 6,01 % | 13 / 14 ms |

Đánh giá: so **cùng điều kiện**, ảnh bìa thêm +0,5…+1,6 điểm (ấm, so nền vc113) và +2,7 điểm (lạnh, cùng vc114 tắt/bật) — dưới ngưỡng 5. Chỉ phép so khác điều kiện (lần chạy đầu ngay sau cài — mã chưa được biên dịch tối ưu — với nền vc113 đã chạy lâu) là +7,4. Không thể đo vc113 lạnh vì đã cài đè (không hạ phiên bản). p90 ≤ 15 ms < 16,7 ms. Opus **không nới ngưỡng**: ghi rõ số đầu vượt, và đưa mục "Bác sĩ tự kiểm tra" bước 3 (vuốt nhanh, có đơ không) làm điều kiện chủ dự án xác nhận. Nếu chủ dự án thấy giật → REWORK (hướng: bớt tải khi cuộn, ô ảnh nhẹ hơn).

## 6. APK candidate (CLAUDE.md §9) — đạt gate

`MeloVault-E99-a606bd7-vc114-TinySigned-debug.apk`: SHA-256 `272D53AAC70FCD4DDF86C69B4A11C92E6894B5A6AACC7869BBA36DAE7C07B653`, 33.413.060 byte — **trùng** ở Tiny (`C:\Bots\_qa_handoff\E99-GON-ANH-BIA\`), Ni (`D:\Bots\_e99apk\`), `base.apk` đang cài trên Shanling (`sha256sum` trên máy) và bản trên Drive. `aapt2`: `app.melovault.player` vc114 / `0.6.90-beta`; `apksigner`: cert `eecd3df7…c3fce8` (Android Debug, như các bản trước). Clone Tiny `C:\Bots\MeloVault-E99-IsolatedQA` @ `a606bd7`, sạch; Opus chạy `reportTelegramConfig` ở đó: `telegram.config.valid=true` (chỉ boolean). Chuyển code bằng Git bundle có SHA-256 (đúng quy trình). Drive owner-only: `docs/plans/E99-GON-ANH-BIA-apk-test.md`. Nhãn: **QA CANDIDATE — CHƯA NGHIỆM THU**.

## 7. Ảnh

Opus xem `00_tong_hop.png` (8 ô) — từng ô khớp bảng báo cáo §5. 10 ảnh chính + APK trên Drive owner-only: `docs/plans/E99-GON-ANH-BIA-hinh-nghiem-thu.md`. Ảnh gốc ở `docs/plans/artifacts/E99-GON-ANH-BIA/` (gitignored, có tên bài/kênh).

## 8. Quan sát quy trình (không chặn)

- Sonnet dùng `am force-stop` trên Shanling để đo lạnh (không có trong kế hoạch, không bị cấm, không xóa dữ liệu): hàng đợi tạm dừng cũ của chủ dự án ("Nonstop - (Nhật Ký)…") không còn nạp lại. Media3 hiện `state=0`. Lần sau ghi trước vào kế hoạch nếu cần đo lạnh.
- Sony: ô tìm kiếm tự có chữ "xe mô" giữa ảnh 33 và 34, Sonnet không gõ — nguồn chưa rõ (có thể người/phiên khác). Sonnet đã dừng thao tác Sony đúng luật. Opus không chạm Sony.
- Bundle chép sang Tiny qua ổ `T:` vào thư mục mới (chỉ chép file bundle, không sửa repo Tiny) — chấp nhận.

## 9. Màn hình (CLAUDE.md §7)

Shanling: `SHANLING_SCREEN_END` OFF 15:37:59 (Sonnet); Opus kiểm lại 16:13 `mScreenState=OFF`. Sony: OFF 15:42:20 (Sonnet); Opus kiểm lại `mWakefulness=Dozing`. Opus không đánh thức thiết bị nào.

## 10. Giới hạn / chưa kiểm chứng

- Teal Phone/Tablet/Hai khung trên Sony sau khi gom `findRowArtFrame` (mã chỉ di chuyển, test E98 xanh); Gọn giả lập trên Sony.
- Đổi Kiểu màu Teal ↔ Hiện tại rồi quay lại Gọn trên máy (chỉ test JVM khóa `:art`).
- Gọn lúc đang quét offline (Đã tải còn 7 dòng — giới hạn mở từ E98, không thuộc E99).
- Điều kiện bật (§3.2 kế hoạch, [CẦN XÁC NHẬN]): Gọn chỉ có ảnh khi Kiểu màu = **Teal sáng** + công tắc bật — làm theo đề xuất mặc định, chờ chủ dự án xác nhận.
- Y700 / S25 / FYT / Car Mode trên máy. **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING chỉ chứng minh luồng phát).

## 11. Kết luận vòng 1

**BLOCKED — CHỜ CHỦ DỰ ÁN.** Mã đúng phạm vi, test/build Opus tự chạy xanh, APK đạt gate và đã lên Drive owner-only, QA Shanling thật đạt các bước 1–6. Còn chờ chủ dự án: (1) xem ảnh / dùng vc114 trên Shanling — đặc biệt vuốt nhanh có giật không (mục 5); (2) giữ điều kiện "chỉ khi Teal sáng" hay không; rồi nói **ĐẠT** hoặc báo lỗi. Khi ĐẠT: đổi thành PASS, xin xác nhận rồi `git merge --no-ff` vào `feature/e93-03d-y700-offline-cockpit`.
