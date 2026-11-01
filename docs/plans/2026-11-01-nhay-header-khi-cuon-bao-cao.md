# 2026-11-01-nhay-header-khi-cuon — Báo cáo thực thi

Thực thi: Claude Code (model Sonnet 5.5, effort không xác định), phiên GỘP S0–S10, 01/11/2026. Kế hoạch: [kế hoạch](2026-11-01-nhay-header-khi-cuon.md). Bàn giao: [bàn giao](2026-11-01-nhay-header-khi-cuon-ban-giao.md). APK: [apk-test](2026-11-01-nhay-header-khi-cuon-apk-test.md). Hình: [hình nghiệm thu](2026-11-01-nhay-header-khi-cuon-hinh-nghiem-thu.md).

Nhánh `dot/2026-11-01-nhay-header-khi-cuon`, baseline `8a8c978`, commit kế hoạch `8a48584`, **commit mã `4fe5a37`**. Kết luận kỹ thuật của agent thực thi: đạt tiêu chí kỹ thuật (mỗi cử chỉ ≤ 1 lần đổi header); **chưa nghiệm thu** — QA CANDIDATE, chờ sơ bộ và chủ dự án.

## 1. Bảng bước

| Bước | Trạng thái | Kết quả |
|---|---|---|
| S0 Kiểm đầu phiên | Xong | HEAD `8a48584`, cây sạch, `diff --check` sạch, không có Gradle/java khác đang chạy |
| S1 Baseline Sony | Xong, **tái hiện được** | Mục 3: header đổi 8 và 14 lần trong hai cử chỉ giữ tay kéo |
| S2 Hàm thuần + `HeaderFold` | Xong | `scrollDeltaPx`, `collapseThresholdPx`, file mới `MeloHeaderFoldPolicy.kt` |
| S3 Nối `MeloLibraryPane` | Xong | Mục 2 |
| S4 Log `MeloHeaderFold` | Xong | Chỉ `BuildConfig.DEBUG`; `collapsed phase first layer`, không tên bài/kênh |
| S5 Unit test | Xong | 15 test mới `MeloHeaderFoldPolicyTest` (a)–(f) + ca phụ |
| S6 Gate Ni | Xong | Mục 4 |
| S7 Build Tiny | Xong | Mục 5 |
| S8 Cài + QA Sony | Xong (Q6 CHƯA KIỂM) | Mục 6 |
| S9 Drive owner-only + hồ sơ | Xong | apk-test, hình nghiệm thu; quyền owner-only đã kiểm bằng connector |
| S10 Hồ sơ cuối | Xong | Báo cáo này, bàn giao, prompt sơ bộ |

## 2. Thay đổi mã (commit `4fe5a37`)

Chỉ các file ở mục 8 của kế hoạch. Không đụng `MeloPhoneScreen`/`MeloTabletScreen`, `TelegramPreviewActivity`, `MainActivity`, Media3, DB, TDLib, package, signer; không tăng versionCode.

| File | Nội dung |
|---|---|
| `MeloHeaderFoldPolicy.kt` (mới) | `HeaderFold(thresholdPx)`: `applied`, `wanted`, `accumulated`, `phase (IDLE/TOUCH/FLING)`. `onDelta(delta, atTop)` cộng dồn theo hướng (kẹp ±4×ngưỡng; đổi hướng thì lấy lại từ delta mới), `pendingChange()` chỉ trả giá trị khi `phase != TOUCH`, `markApplied()` đặt cờ lấy lại mốc, `reset()` |
| `MeloLibraryPolicy.kt` | `scrollDeltaPx(...)` đo trên cùng một dòng; `collapseThresholdPx(density) = round(16×density)`. Giữ `COLLAPSE_THRESHOLD_PX` và `headerCollapsed` (test cũ còn dùng) |
| `MeloLibraryPane.kt` | `HeaderFold` thay `collapsed`/`lastScrollKey`; `collapsed` là getter của `fold.applied`. `onScrollStateChanged` map phase rồi `applyFoldIfAllowed()`; `onScroll` tính delta rồi `applyFoldIfAllowed()`; nhánh `isInLayout → post` giữ; `resetScroll()` gọi `fold.reset()` + `prevFirst = -1` |

Khác kế hoạch (minh bạch):
- `HeaderFold.onDelta(delta, atTop)` không nhận `firstVisible` (thừa vì `atTop` đã gồm `first <= 0 && top >= 0`).
- `scrollDeltaPx` có thêm tham số mặc định `newFirstTopBefore`: khi cuộn **xuống** qua ranh giới dòng, dòng đầu cũ đã trôi khỏi màn nên `prevFirstTopNow` luôn null. Pane giữ top của 4 dòng đầu ở lần gọi trước, lấy vị trí cũ của dòng đầu mới từ đó, nên không phải ước lượng bằng bước dòng.
- Rời chế độ nguồn (`showingSources`) thì đặt `prevFirst = -1`.

## 3. S1 — Baseline trên Sony (APK vc121 0.6.96-beta chưa sửa, SHA `5B2FB5CC…FEF8`)

Sony XQ-AS52 `QV725WEQ54` (authorized, Android 12, 1080×2520, 420 dpi), qua Tiny. **Giới hạn:** tab Playlist của Sony chỉ có “Yêu thích · 2 bài”; không tạo dữ liệu mới. Dùng tab **Thư viện/Tất cả (25.5k bài)**, cùng `MeloLibraryPane` nên cùng lỗi.

Đếm số lần header đổi bằng độ sáng vùng biểu tượng ⚙ của hàng “MeloVault” (khung 10 fps; script `count_header.ps1`):

| Video (private) | Cử chỉ | Số lần header đổi |
|---|---|---|
| `base_02_all` | `input swipe` nhanh ×3 + chậm 1500 ms, toàn xuống | 1 — chưa thấy nháy |
| `base_03_mixed` | `input swipe` xuống–lên xen kẽ | 2 — chưa thấy nháy |
| `base_04_held` | **Giữ ngón tay** kéo 14×40 px (`input motionevent`), kéo lên ×2 | **8** và **14** (nháy 0,1 s/lần) |

`input swipe` (ít sự kiện MOVE) không tái hiện; **giữ tay kéo bằng `motionevent` tái hiện đúng lỗi** (nguyên nhân 1 trong kế hoạch). Nguyên nhân 2 chưa tách riêng được trên máy.

## 4. S6 — Gate Ni (trên `4fe5a37`)

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL 4m38s |
| Debug và Release (XML) | mỗi biến thể 200 suite, **2698 test**, 21 skipped, 0 failures, 0 errors (= 2683 + 15 mới) |
| `:app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL 1m53s |
| `git diff --check` | Sạch |

APK Ni không có Telegram config nên không cài lên Sony.

## 5. S7 — Build Tiny

| Mục | Giá trị |
|---|---|
| Bundle | `MeloVault_nhay-header_full_4fe5a37_20261101.bundle` 13.721.556 B, SHA-256 `D7167D57…E495` khớp Ni và Tiny (`bundle verify` chạy ngoài repo báo lỗi vô hại; clone thành công) |
| Clone | `C:\Bots\MeloVault-NHAY-IsolatedQA-20261101-4fe5a37`, checkout detach `4fe5a37`, cây sạch trước và sau build |
| Config | `telegram.config.valid=true` (copy `local.properties` ngay trên Tiny theo mẫu cũ; không đọc nội dung) |
| Build | `assembleShareableDebug` BUILD SUCCESSFUL 2m54s |
| APK | 33.789.892 B, SHA-256 `E5D808D2…6B74`, package `app.melovault.player`, vc121/0.6.96-beta, debuggable, cert `eecd3df7…ce8` = cert bản đang cài |
| Gate cài | Sony authorized, model `XQ-AS52`/Sony, vc121 = vc121 (không downgrade), media state=2 (tạm dừng, không phát), màn OFF; cài **một** lệnh `adb -s QV725WEQ54 install -r` → Success; SHA bản cài = SHA APK |

## 6. S8 — QA Sony (APK `E5D808D2…6B74`)

Số lần đổi = đếm trên video + dòng log `MeloHeaderFold`. Bảng so sánh với baseline cùng chuỗi cử chỉ:

| # | Thao tác (tab Thư viện/Tất cả) | Kỳ vọng | Thực tế | Kết quả |
|---|---|---|---|---|
| Q1 | Lướt nhanh ×3 từ đầu danh sách | 1 lần đổi, danh sách không nhảy | Video 1 lần; log 1 dòng `collapsed=true phase=FLING` | PASS |
| Q2 | Giữ tay kéo xuống/lên (chuỗi `HU,P,H,P,HU` — giống `base_04_held`), mỗi lần giữ ≈ 1,5 s | Lúc giữ tay không đổi; nhả tay ≤ 1 lần | Video 3 lần (baseline 8 và 14 ở hai cử chỉ kéo lên); 3 dòng log đều `phase=IDLE`, trùng thời điểm nhả tay | PASS |
| Q3 | Lướt nhanh xuống/lên xen kẽ ×5 (`new_q3c`) | Mỗi cử chỉ 1 lần | Video 5 lần = log 5 dòng (`phase=FLING`); lần chạy trước `new_q3b`: video 2, log 1 (xem lưu ý) | PASS (có lưu ý) |
| Q4 | Đang gấp, giữa danh sách → tab Đã tải | Header hiện, danh sách ở đầu | Header hiện, danh sách ở đầu (ảnh 04b→04c) | PASS |
| Q5 | Lướt nhanh ×8 liền nhau | Không nháy, danh sách trượt qua nhiều trang | 0 lần đổi (header đã gấp sẵn), danh sách trượt bình thường; không kiểm riêng số lần tải thêm | PASS (một phần) |
| Q6 | Xoay ngang (Hai khung) | Không nhảy | **CHƯA KIỂM** (cần đổi cài đặt xoay màn hình của máy) | CHƯA KIỂM |

Lưu ý:
- `new_q3b` (chạy trước `new_q3c`): video thấy header gấp rồi hiện đúng 1 lần mỗi chiều nhưng log chỉ có dòng “hiện”; thiếu dòng “gấp” chưa giải thích được (nghi logd bỏ dòng). Lặp lại `new_q3c` với chuỗi dài hơn thì 5/5 khớp. Số lần đổi của Q3b theo video vẫn là 2 (không nháy).
- Q2/Q3 dùng `input motionevent`/`input swipe` giả lập, không phải ngón tay thật; ghi “nhả tay” = lệnh `UP`.
- Điều kiện ĐẠT kỹ thuật “mỗi cử chỉ ≤ 1 lần đổi header”: đạt ở Q1–Q5 theo bảng trên. Hành vi mới: header chỉ gấp/hiện **sau khi nhả tay** hoặc khi danh sách trôi — cần chủ dự án xác nhận (kế hoạch mục 5).
- Không phát nhạc: **CHƯA XÁC NHẬN ÂM THANH THỰC**.

`SONY_SCREEN_END`: màn tắt bằng `keyevent 223`, xác minh `Display State=OFF`, `mWakefulness=Dozing`, media không phát. Ảnh tổng hợp: `docs/plans/artifacts/2026-11-01-nhay-header-khi-cuon/00_tong_hop.png` (và bản Drive trong hình nghiệm thu). Ảnh/video nằm trong `.gitignore`, không commit.

## 7. APK QA bàn giao chủ dự án nghiệm thu

Xem [apk-test](2026-11-01-nhay-header-khi-cuon-apk-test.md): Drive owner-only (thư mục/APK/`hinh` đều chỉ quyền owner), APK 33.789.892 B, SHA-256 `E5D808D2C29F70333350EF3682A50FBF686D6140C93513F1A6AA7534A27C6B74`, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**. Tải bằng trình duyệt chưa thử.

## 8. Rủi ro và việc chưa kiểm

- Hành vi “gấp khi nhả tay” có thể không đúng ý chủ dự án; muốn gấp ngay lúc kéo cần phương án B (nhiệm vụ riêng).
- Chưa kiểm: Playlist ≥ 20 bài; Hai khung/xoay ngang (Q6); ngón tay thật; âm thanh thật; Release/`.open`/Personal trên máy; chữ lớn/TalkBack.
- Áp dụng thay đổi khi đang FLING làm danh sách giãn ra một bước (chấp nhận, thấy ở Q1/Q3).
- Không cập nhật `HANDOFF.md` đầu trang (ngoài danh sách file kế hoạch; chờ nghiệm thu).
