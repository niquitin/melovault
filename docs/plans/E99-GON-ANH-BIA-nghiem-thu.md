# E99-GON-ANH-BIA — Biên bản nghiệm thu (Opus)

**Vai trò:** Claude Opus nghiệm thu độc lập, 25/10/2026 ~16:09–16:30, theo `docs/workflow/PROMPT_OPUS_NGHIEM_THU.md` và `CLAUDE.md` của nhánh `dot/workflow-opus-nghiem-thu` (`ffcc584`, chưa merge — "ChatGPT" trong tài liệu cũ đọc là Opus nghiệm thu).

| Vòng | Thời điểm | Trạng thái |
|---|---|---|
| 1 | 25/10 ~16:30 | BLOCKED — CHỜ CHỦ DỰ ÁN (kỹ thuật đạt; Janky lạnh cần chủ dự án cảm nhận; chờ duyệt ảnh/cài vc114) |
| 1 — phản hồi chủ dự án | 25/10 | "ĐẠT" + "Gọn luôn hiện ảnh bìa, luôn có ảnh" (trả lời câu hỏi §3.2 kế hoạch) |
| REWORK-01 — chỉ thị | 25/10 ~21:56 | **REWORK** — chỉ đổi điều kiện bật ảnh ở Gọn (mục 12); phần còn lại chủ dự án đã chấp nhận |
| **REWORK-01 — nghiệm thu (hiện tại)** | 25/10 ~22:30–22:45 | **BLOCKED — CHỜ CHỦ DỰ ÁN** (kỹ thuật đạt hết, mục 13; chờ chủ dự án duyệt ảnh/vc115) |

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

## 12. REWORK-01 — Gọn luôn có ảnh bìa, không phụ thuộc Kiểu màu

**Phản hồi chủ dự án (25/10):** "ĐẠT" cho vc114 (độ mượt khi vuốt chấp nhận) và chốt §3.2 kế hoạch: **"Gọn luôn hiện ảnh bìa, luôn có ảnh"** — chọn phương án thay thế, bỏ điều kiện Kiểu màu = Teal sáng cho Gọn. vc114 đang làm theo đề xuất mặc định (chỉ khi Teal) nên kết luận vòng này là **REWORK-01** (nhỏ, không mở kế hoạch mới). Merge chỉ sau khi REWORK-01 đạt.

**Chỉ thị (đúng phạm vi, không thêm việc):**
1. `MeloThemePolicy.rowArtLayer`: Gọn (`Profile.COMPACT`) trả `true` với **mọi** `ColorTheme`; Phone/Tablet giữ `appliesTo` như E98; Car luôn `false`. Sửa KDoc hàm và KDoc đầu file ("Gọn giữ màu, luôn có ảnh bìa dòng").
2. Công tắc **"Tải ảnh bìa từ Telegram"** (mặc định bật) **giữ nguyên** là cách tắt ảnh ở Gọn (`rowArtOn()` = layer && công tắc, không sửa).
3. `MeloUiLabActivity`: chữ ghi chú Kiểu màu → "… Bản Gọn giữ màu cũ và luôn có ảnh bìa nhỏ (tắt bằng công tắc bên dưới); Car Mode không đổi." Không thêm lựa chọn mới.
4. `MeloThemePolicyTest`: đổi `rowArtLayerAddsOnlyTheCompactLayerWithTheTealTheme` thành: CURRENT + COMPACT = true, TEAL + COMPACT = true, CAR = false với mọi theme/layer, PHONE layer null = false; giữ nguyên test khóa Phone/Tablet = `appliesTo`. `layerKey` không đổi.
5. `app/build.gradle`: vc114 → **vc115**, `0.6.90` → **`0.6.90.1`**.
6. Chỉ 4 file trên + báo cáo. Không sửa loader/DB/TDLib/Media3/palette/kích thước.

**QA bắt buộc — Shanling `30313860180939`** (vc115 build Tiny qua Git bundle, đủ gate §3, chỉ `install -r`):

| # | Hành động | Kỳ vọng |
|---|---|---|
| R1 | Mở app (Kiểu màu đang Teal) | Như vc114: 8 dòng, có ảnh, màu Gọn cũ |
| R2 | ☰ → Cài đặt giao diện → Kiểu màu = **Hiện tại** → quay lại, chờ 5 s | Gọn **vẫn có ảnh** (≥ 80 % dòng), màu không đổi, vẫn 8 dòng |
| R3 | Tắt công tắc "Tải ảnh bìa từ Telegram" → quay lại; rồi bật lại | Tắt: ô chữ cái, không dòng `MeloRowArt` mới; bật: ảnh về |
| R4 | **Trả Kiểu màu = Teal sáng, công tắc bật** (như trước QA) | Như R1 |

Sony (nếu Tiny ADB sẵn sàng và không ai đang dùng): Điện thoại dọc, Kiểu màu Hiện tại → **không** có ảnh (hợp đồng E98); Teal → có ảnh; trả cài đặt như cũ. Màn hình OFF trong `finally` (§7). Ghép `00_tong_hop_rework01.png`, không ghi đè ảnh vòng 1. Thêm mục "REWORK-01" vào `E99-GON-ANH-BIA-bao-cao.md` kèm "APK QA cần Opus nghiệm thu bàn giao". **Không** dùng `am force-stop` trên Shanling (giữ hàng đợi của chủ dự án).

**PASS REWORK-01:** test 0 lỗi, ≥ 2042 mỗi biến thể; build OK; đúng 4 file; R1–R4 đạt; Phone Hiện tại không có ảnh (mã + test, Sony nếu có); chủ dự án duyệt.

## 13. Nghiệm thu REWORK-01 (vc115) — Opus, 25/10 ~22:30–22:45

**Git:** HEAD `d14d991` (báo cáo), code `6fb4e65`, cha `2687956`; cây sạch, không Gradle/adb nào khác đang chạy (commit cuối 22:23, Sonnet đã báo dừng). `git diff --stat 2687956..HEAD`: đúng 4 file chỉ thị (+15/−12) + báo cáo; `git diff --check` sạch. Không đụng loader/DB/TDLib/Media3/palette/kích thước.

**Mã (đọc toàn bộ diff):** `rowArtLayer = appliesTo(...) || profile == COMPACT` — Gọn luôn bật lớp ảnh, Phone/Tablet vẫn `= appliesTo` (Hiện tại → không ảnh), Car `false`. Công tắc vẫn chặn qua `rowArtOn()` (`TelegramPreviewActivity.kt:128`). Vì `rowArt` của Gọn nay luôn `true`, khóa `layerKey` không đổi khi đổi Kiểu màu → không dựng lại lớp Gọn thừa. Test viết lại phủ mọi `ColorTheme` × COMPACT/CAR. Ghi chú nhỏ (không chặn): `MeloThemePolicy.rowArtActive` (dòng 41–42, KDoc "chỉ khi Teal") không còn dùng ở mã sản phẩm, chỉ còn trong test — có thể dọn ở nhiệm vụ sau.

| Tiêu chí PASS REWORK-01 | Kết quả | Bằng chứng |
|---|---|---|
| Test 0 lỗi, ≥ 2042 mỗi biến thể | **ĐẠT** | Opus tự chạy `--rerun` 22:30–22:32: `BUILD SUCCESSFUL in 1m 40s`; XML mới (166 file/biến thể): Debug 2042/21 bỏ qua/0 lỗi, Release 2042/21/0. Số test giữ nguyên (1 test viết lại) |
| Build Debug + Release OK | **ĐẠT** | cùng lần chạy trên |
| Đúng 4 file | **ĐẠT** | diff --stat ở trên |
| R1 Teal: 8 dòng có ảnh | **ĐẠT** | ô 1 ảnh tổng hợp, `R1_…221120` |
| R2 Hiện tại: Gọn vẫn có ảnh ≥ 80 %, màu không đổi | **ĐẠT** | ô 2–3, `R2_07`, `R2_08`; log 22:14:11–16 (lúc đang Hiện tại) `sent` 123→161, `decoded` 115→175, `compact visible=9 withArt=9` — bộ nạp thật sự tải ở Hiện tại |
| R3 tắt → ô chữ cái; bật → ảnh về | **ĐẠT** | ô 4–5; log không dòng nào 22:14:16 → 22:15:47 (bật lại, `cache` reset 240 KB). Log có điều tiết nên "không request khi tắt" dựa trên mã (`artKey = null`) + ảnh, như vòng 1 |
| R4 trả Teal + công tắc bật | **ĐẠT** | ô 6, `R4_02`; trạng thái cuối Shanling = trước QA |
| Phone Hiện tại không ảnh (mã + test; Sony) | **ĐẠT** | test `rowArtLayerKeepsTheE98BehaviourForPhoneAndTablet`; Sony ô 7 (Hiện tại: chữ cái), ô 8 (Teal: có ảnh); Sony trả về Hiện tại |
| inflight ≤ 3, không crash/ANR | **ĐẠT** | log inflight 0/1 (10/3 lần); báo cáo: crash buffer 0 FATAL |
| Không `force-stop`/uninstall/`pm clear`/`-d` | **ĐẠT** | báo cáo R.1, R.3; Media3 Shanling `state=0`, Sony `state=2` không đụng |
| Màn hình OFF cuối (§7) | **ĐẠT** | Sonnet: Shanling 22:17:06, Sony 22:21:58; Opus kiểm lại Shanling `mScreenState=OFF`, không đánh thức máy nào |
| Chủ dự án duyệt | **CHỜ** | — |

**APK (§9) — đạt gate:** `MeloVault-E99-REWORK01-6fb4e65-vc115-TinySigned-debug.apk`, SHA-256 `341200C7…C636E6`, 33.413.060 byte — trùng ở Tiny, Ni, Drive và `base.apk` đang cài trên Shanling. `aapt2`: `app.melovault.player` vc115 / `0.6.90.1-beta`; `apksigner`: cert `eecd3df7…c3fce8`; clone Tiny @ `6fb4e65` sạch, log build `telegram.config.valid=true`. Drive owner-only, thư mục mới `E99-GON-ANH-BIA-20261025-vc115-6fb4e65`: `E99-GON-ANH-BIA-apk-test.md`, ảnh: `E99-GON-ANH-BIA-hinh-nghiem-thu.md`. Nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**.

**Giới hạn:** Janky không đo lại (REWORK-01 không đổi bộ nạp/kích thước; cùng điều kiện ở Kiểu màu Hiện tại nay Gọn cũng tải ảnh như Teal — chủ dự án đã chấp nhận độ mượt vc114). Tablet/Hai khung, Y700/S25/FYT/Car Mode chưa kiểm; Gọn lúc đang quét offline chưa kiểm. **CHƯA XÁC NHẬN ÂM THANH THỰC.**

**Kết luận REWORK-01: BLOCKED — CHỜ CHỦ DỰ ÁN.** Kỹ thuật đạt toàn bộ tiêu chí mục 12. Chủ dự án xem ảnh / dùng vc115 (Kiểu màu Hiện tại → Gọn vẫn có ảnh bìa) rồi nói **ĐẠT** hoặc báo lỗi. Khi ĐẠT: đổi thành PASS, xin xác nhận rồi `git merge --no-ff dot/E99-GON-ANH-BIA` vào `feature/e93-03d-y700-offline-cockpit`, cập nhật đầu HANDOFF.md.
