# 2026-11-01-nhay-header-khi-cuon — Báo cáo thực thi

Thực thi: Claude Code (model Sonnet 5.5, effort không xác định), phiên GỘP S0–S10, 01/11/2026. Kế hoạch: [kế hoạch](2026-11-01-nhay-header-khi-cuon.md). Bàn giao: [bàn giao](2026-11-01-nhay-header-khi-cuon-ban-giao.md).

Nhánh `dot/2026-11-01-nhay-header-khi-cuon`, baseline `8a8c978`, commit kế hoạch `8a48584`, commit mã `4fe5a37`.

## 1. Bảng bước

| Bước | Trạng thái | Kết quả |
|---|---|---|
| S0 Kiểm đầu phiên | Xong | HEAD `8a48584`, cây sạch, `git diff --check` sạch, không có agent khác (không có Gradle/java đang chạy) |
| S1 Baseline Sony | Xong, **tái hiện được** | Mục 3. Header đổi 8 và 14 lần trong hai cử chỉ giữ tay kéo xuống |
| S2 Hàm thuần + `HeaderFold` | Xong | `MeloLibraryPolicy.scrollDeltaPx`, `collapseThresholdPx`; file mới `MeloHeaderFoldPolicy.kt` |
| S3 Nối `MeloLibraryPane` | Xong | Mục 2 |
| S4 Log `MeloHeaderFold` | Xong | Chỉ `BuildConfig.DEBUG`; ghi `collapsed phase first layer`, không có tên bài/kênh |
| S5 Unit test | Xong | 15 test mới `MeloHeaderFoldPolicyTest`, phủ (a)–(f) và các ca phụ |
| S6 Gate Ni | Xong | Mục 4 |
| S7 Build Tiny | Chưa làm | — |
| S8 Cài + QA Sony | Chưa làm | — |
| S9 Drive + hồ sơ APK/ảnh | Chưa làm | — |
| S10 Hồ sơ cuối | Chưa làm | — |

## 2. Thay đổi mã (commit `4fe5a37`)

Chỉ các file ở mục 8 của kế hoạch; không đụng `MeloPhoneScreen`/`MeloTabletScreen`, `TelegramPreviewActivity`, `MainActivity`, Media3, DB, TDLib, package, signer; không tăng versionCode.

| File | Nội dung |
|---|---|
| `MeloHeaderFoldPolicy.kt` (mới) | Lớp `HeaderFold(thresholdPx)`: `applied`, `wanted`, `accumulated`, `phase` (`IDLE/TOUCH/FLING`). `onDelta(delta, atTop)` cộng dồn theo hướng (kẹp ±4×ngưỡng, đổi hướng thì lấy lại từ delta mới), `pendingChange()` chỉ trả giá trị khi `phase != TOUCH`, `markApplied()` đặt cờ lấy lại mốc, `reset()` |
| `MeloLibraryPolicy.kt` | `scrollDeltaPx(...)` đo trên cùng một dòng; `collapseThresholdPx(density) = round(16×density)`. Giữ nguyên `COLLAPSE_THRESHOLD_PX` và `headerCollapsed` (test cũ còn dùng) |
| `MeloLibraryPane.kt` | Thay `collapsed`/`lastScrollKey` bằng `HeaderFold` + `prevFirst`/`prevTops`; `collapsed` còn là getter của `fold.applied`. `onScrollStateChanged` map phase rồi `applyFoldIfAllowed()`; `onScroll` tính delta rồi `applyFoldIfAllowed()`; nhánh `isInLayout → post` giữ nguyên; `resetScroll()` gọi `fold.reset()` + `prevFirst = -1` |

Khác kế hoạch (ghi lại để minh bạch):
- `HeaderFold.onDelta(delta, atTop)` không nhận `firstVisible` (thừa vì `atTop` đã gồm `first <= 0 && top >= 0`).
- `scrollDeltaPx` có thêm tham số mặc định `newFirstTopBefore`: khi cuộn **xuống** qua ranh giới dòng, dòng đầu cũ đã trôi khỏi màn nên `prevFirstTopNow` luôn null. Pane giữ top của 4 dòng đầu ở lần gọi trước, lấy vị trí cũ của dòng đầu mới từ đó, nên không phải dùng bước ước lượng.
- Rời chế độ nguồn (`showingSources`) thì đặt `prevFirst = -1` để không đo từ mốc cũ.

## 3. S1 — Baseline trên Sony (APK vc121 0.6.96-beta, chưa sửa)

Thiết bị: Sony XQ-AS52 `QV725WEQ54` (authorized, 1080×2520, 420 dpi), qua Tiny. `app.melovault.player` versionCode 121, versionName 0.6.96-beta.

**Giới hạn:** tab Playlist của Sony chỉ có “Yêu thích · 2 bài” (không có playlist ≥ 20 bài; không tạo dữ liệu mới để tránh sửa dữ liệu app). Dùng tab **Thư viện / Tất cả (25.496 bài)**, cùng `MeloLibraryPane` nên cùng lỗi.

Đo số lần header ẩn/hiện bằng độ sáng vùng biểu tượng ⚙ của hàng “MeloVault” (khung 10 fps), script `count_header.ps1`.

| Video (private, không commit) | Cử chỉ | Số lần header đổi |
|---|---|---|
| `base_02_all.mp4` | `input swipe` nhanh ×3 + chậm 1500 ms, toàn xuống | 1 (gấp ở cử chỉ đầu rồi giữ) — chưa thấy nháy |
| `base_03_mixed.mp4` | `input swipe` chậm/nhanh xen xuống–lên | 2 — chưa thấy nháy |
| `base_04_held.mp4` | Giữ ngón tay, kéo xuống từng bước 14×40 px (`input motionevent`), 2 lần | **8** và **14** (nháy liên tục tại 1,6–2,8 s và 9,6–10,9 s) |

Kết luận: `input swipe` (ít sự kiện MOVE) không đủ tái hiện; **giữ tay kéo bằng `motionevent` tái hiện đúng lỗi** (vòng phản hồi, nguyên nhân 1 của kế hoạch). Nguyên nhân 2 chưa tách riêng được bằng log trên máy (bản baseline chưa có log).

## 4. S6 — Gate Ni (commit `4fe5a37`)

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL 4m38s |
| Debug (XML) | 200 suite, **2698 test**, 21 skipped, 0 failures, 0 errors |
| Release (XML) | 200 suite, **2698 test**, 21 skipped, 0 failures, 0 errors |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL 1m53s |
| `git diff --check` | Sạch |

2698 = 2683 (baseline sau “nhiều playlist có tên”) + 15 test mới. Log Gradle đầy đủ ở scratchpad phiên (không commit).
APK build trên Ni **không có Telegram config** nên không cài lên Sony; APK QA phải build ở Tiny (S7).

## 5. Việc còn lại

S7–S10: build Debug có config trên Tiny, cài `install -r`, QA Q1–Q6, ghép `00_tong_hop.png`, Drive owner-only, `-apk-test.md`, `-hinh-nghiem-thu.md`, prompt sơ bộ.
