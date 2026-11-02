# 2026-11-01-nhay-header-khi-cuon — Kế hoạch: header nháy/giật khi cuộn danh sách bài

Lập kế hoạch: Claude Code (model do chủ dự án chọn, không xác định effort), 01/11/2026. **Không code trong phiên này.**

## 1. Yêu cầu của chủ dự án

- Chat 01/11/2026 ~22:21, kèm video `Screen_Recording_20261101_222143_MeloVault_Preview.mp4` (5,3 giây, 1080×2340): “Chỗ playlist kéo danh sách bị nháy chồng hình. Tình trạng này cũng bị ở các chỗ khác nên kéo lướt danh sách bài hát với tốc độ nhanh.”
- Chủ dự án chọn “Viết kế hoạch” (theo quy trình), không sửa ngay trong phiên chẩn đoán.
- Video **không lưu vào repo**: tên bài trong video có số điện thoại/tên kênh Telegram (PII). Agent thực thi tự quay video tái hiện trên Sony (mục 6).

## 2. Baseline

| Mục | Giá trị |
|---|---|
| Repo chuẩn | `D:\Bots\MeloVault-E91-Shareable` |
| Worktree nhiệm vụ | `D:\Bots\_worktrees\MeloVault-nhay-header-khi-cuon` |
| Nhánh | `dot/2026-11-01-nhay-header-khi-cuon` |
| Baseline | `8a8c978` = tip `feature/e93-03d-y700-offline-cockpit` (đã gồm merge `2b222c3` “nhiều playlist có tên”) |
| Bản trong video | APK “nhiều playlist có tên” `fe518f5` (vc121 0.6.96-beta), giao diện Mới, lớp Điện thoại dọc, theme teal |

## 3. Chẩn đoán (đọc code + phân tích khung hình video)

**Hiện tượng.** Trích 4 khung/giây: trong ~1 giây, cụm đầu trang (`headerBox` = hàng “MeloVault ⋮” + hàng “☁ Đã đồng bộ ⚙”) **ẩn rồi hiện lại liên tục**. Mỗi lần như vậy toàn bộ danh sách nhảy ~70dp lên/xuống, trông như hình nháy và chồng lên nhau. Nội dung danh sách gần như đứng yên; thứ đổi chỗ là header.

**Code liên quan.**
- [`MeloLibraryPane.kt:240-259`](../../app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloLibraryPane.kt) — trong `onScroll` của `ListView`:
  `key = first * (s.rowHeight + s.gap / 2) - child0.top`, rồi `MeloLibraryPolicy.headerCollapsed(collapsed, key - lastScrollKey, first)`; đổi trạng thái thì chạy ngay `applyCollapse` (gồm `onHeaderCollapse(collapsed)` + `applySourceCardsVisibility()`).
- [`MeloPhoneScreen.kt:103-105`](../../app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloPhoneScreen.kt) — `headerBox.visibility = GONE/VISIBLE`.
- [`MeloLibraryPolicy.kt:233-238`](../../app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloLibraryPolicy.kt) — ngưỡng `COLLAPSE_THRESHOLD_PX = 12` **px cho mỗi lần onScroll**, không cộng dồn, không theo dp.

**Nguyên nhân 1 — vòng phản hồi dưới ngón tay (chắc chắn theo cơ chế Android).** Header đổi ngay khi ngón tay **còn đang kéo**. `headerBox` → `GONE` làm `ListView` dịch lên ~70dp ngay dưới ngón tay. `AbsListView` theo dõi ngón tay bằng `MotionEvent.getY()` (toạ độ tương đối của chính ListView), nên lần MOVE kế tiếp coi như ngón tay vừa kéo xuống ~70dp. ListView cuộn ngược, delta âm lớn → `headerCollapsed` trả `false` → header hiện, ListView dịch xuống → delta dương → ẩn… Vòng lặp chỉ dừng khi nhấc tay. Kéo nhanh thì delta mỗi khung lớn nên càng dễ bị.

**Nguyên nhân 2 — ước lượng vị trí cuộn sai khi qua ranh giới dòng (suy từ code, chưa đo trên máy).** `key` giả định mỗi dòng cao đúng `s.rowHeight + s.gap/2`, nhưng:
- theme teal đặt `dividerHeight = s.gap` (`MeloLibraryPane.kt` ~dòng 218), không phải `s.gap/2`. Ở máy 420dpi, sai ~10px mỗi dòng;
- dòng bài chỉ có `minimumHeight = s.rowHeight` (`MeloComponents.kt:386`); tên dài 2 dòng như trong video làm dòng cao hơn 72dp.

Hậu quả: mỗi lần `first` tăng 1, `key` tụt một đoạn bằng phần chênh. Đoạn này thường > 12px, nên ngay cả khi đang cuộn xuống (hoặc đang trôi, không có ngón tay) header vẫn bị “hiện” sai ở mỗi ranh giới dòng rồi lại “ẩn” ở khung sau.

**Phạm vi ảnh hưởng (“các chỗ khác”).** `MeloLibraryPane` dùng chung cho các tab Tất cả / Đã tải / Playlist (cả cấp chỉ mục lẫn cấp 2 của một playlist) và danh sách theo Nguồn, nên cùng một lỗi. `MeloTabletScreen` (Hai khung) không truyền `onHeaderCollapse`, nhưng `applySourceCardsVisibility()` vẫn ẩn/hiện thẻ nguồn theo `collapsed`, nên cũng có thể giật theo cùng cơ chế.

**Đã loại trừ.**
- Ảnh bìa cũ còn dính khi dòng được tái sử dụng: `songRow` gán `setImageBitmap` hoặc `setImageDrawable(null)` mỗi lần bind (`MeloComponents.kt:425-437`).
- Giao diện cũ `TelegramPreviewActivity`: thu gọn thanh điều hướng chỉ ở `ACTION_UP` + chờ idle (`commitNavigationGestureIfIdle`), nên không có vòng phản hồi.

## 4. Mục tiêu / ngoài phạm vi

**Mục tiêu.**
- Cuộn hoặc lướt nhanh trong mọi danh sách bài của giao diện Mới: header **đổi tối đa một lần mỗi cử chỉ**, danh sách không nhảy qua lại.
- Giữ hành vi hiện có: cuộn xuống thì gấp header, cuộn lên hoặc về đầu thì hiện lại, đổi tab/nhóm thì về đầu và hiện header (`resetScroll`).

**Ngoài phạm vi.**
- Thêm hoạt ảnh trượt cho header.
- Đổi sang CoordinatorLayout/AppBarLayout.
- Giao diện cũ (`TelegramPreviewActivity`), Personal (`MainActivity`).
- Hàng đợi/lời nhạc.
- Thay đổi Media3, DB, TDLib, package, signer, versionCode.

Nếu QA thấy nháy ở màn ngoài phạm vi, ghi lại thành phát hiện, **không tự sửa**.

## 5. Phương án và lý do

| Phương án | Đánh giá |
|---|---|
| **A (chọn).** (1) Không đổi header khi ngón tay đang kéo (`SCROLL_STATE_TOUCH_SCROLL`); giữ trạng thái chờ, áp dụng khi chuyển sang FLING hoặc IDLE. Lúc đang trôi thì được đổi ngay. (2) Tính delta cuộn theo **cùng một dòng** giữa hai lần onScroll thay cho công thức `first × pitch`. (3) Cộng dồn delta theo hướng với ngưỡng theo dp. (4) Sau mỗi lần đổi header, lấy lại mốc đo. | Sửa đúng cả hai nguyên nhân. Chỉ đụng 2–3 file UI + test. Cùng tinh thần với `TelegramPreviewActivity` đã ổn định. |
| B. Header dùng `translationY` + bù padding danh sách (không đổi layout) | Mượt hơn nhưng đổi cấu trúc layout, trim UI-01 và Hai khung. Rủi ro lớn hơn yêu cầu sửa lỗi. Để làm sau nếu chủ dự án muốn có hoạt ảnh. |
| C. Chỉ tăng ngưỡng 12px | Không chặn vòng phản hồi (bước nhảy ~70dp luôn lớn hơn ngưỡng). Loại. |

**Hệ quả UX của A (chủ dự án sẽ thấy khi nghiệm thu).** Kéo chậm và giữ tay thì header chưa gấp ngay; gấp lúc nhả tay hoặc lúc danh sách bắt đầu trôi. Nếu chủ dự án muốn header gấp ngay trong lúc kéo thì cần phương án B, là một nhiệm vụ riêng.

## 6. Các bước thực thi

Thực thi có thể **GỘP một phiên** (việc nhỏ). Nếu quota thiếu thì tách tại checkpoint sau S6: Chặng A = S0–S6 trên Ni, Chặng B = S7–S10 trên Tiny/Sony.

**S0 — Kiểm đầu phiên.**
- Chạy đủ lệnh git của CLAUDE.md mục 1 trong worktree trên. HEAD phải là commit kế hoạch (con của `8a8c978`), cây sạch.
- Đọc 3 file ở mục 3 và `MeloLibraryPolicyTest.kt` (test `headerCollapsed` hiện có ở dòng ~160-170).
- Lệch baseline hoặc cây bẩn → DỪNG.

**S1 — Tái hiện baseline trên Sony (trước khi sửa).**
- Trên Tiny: `adb devices -l`, getprop model `XQ-AS52`, serial `QV725WEQ54`.
- Ghi versionCode/versionName của `app.melovault.player` đang cài (`dumpsys package … | findstr version`).
- Bật màn theo quy trình. Mở tab Playlist → playlist có ≥ 20 bài. Quay `adb shell screenrecord --time-limit 10`, trong lúc quay chạy:
  - 3 lần lướt nhanh: `input swipe 540 1900 540 700 120`;
  - 1 lần kéo chậm: `input swipe 540 1900 540 900 1500`.
- Pull video vào `docs/plans/artifacts/2026-11-01-nhay-header-khi-cuon/01_sony_baseline_playlist.mp4`. Trích dải khung 4–8 fps bằng ffmpeg để thấy header ẩn/hiện.
- Không tái hiện được thì ghi rõ và vẫn tiếp tục (bằng chứng: video chủ dự án + phân tích code). Tắt màn trong `finally`.
- Toạ độ swipe ở trên là gợi ý: căn theo ảnh màn hình thật của Sony (1080×2520).

**S2 — Hàm thuần trong `MeloLibraryPolicy.kt` (JVM test được).**
1. `scrollDeltaPx(prevFirst: Int, prevTop: Int, first: Int, firstTop: Int, prevFirstTopNow: Int?, fallbackPitchPx: Int): Int`. Quy ước dương = cuộn xuống (nội dung đi lên), khớp `key - lastScrollKey` cũ.
   - `prevFirst < 0` (chưa có mốc) → `0`.
   - `first == prevFirst` → `prevTop - firstTop`.
   - khác, và `prevFirstTopNow != null` (dòng cũ vẫn đang hiển thị: `list.getChildAt(prevFirst - first)?.top`) → `prevTop - prevFirstTopNow`.
   - còn lại (nhảy xa, ví dụ khi đang trôi rất nhanh) → `(first - prevFirst) * fallbackPitchPx + (prevTop - firstTop)`.
2. Lớp trạng thái thuần `HeaderFold(thresholdPx: Int)`, cùng file hoặc file mới `MeloHeaderFoldPolicy.kt` trong `data/telegram/`:
   - **Trạng thái:** `applied: Boolean`, `wanted: Boolean`, `accumulated: Int`, `scroll: ScrollPhase {IDLE, TOUCH, FLING}`.
   - **`onDelta(delta, firstVisible, atTop)`:**
     - đổi hướng thì `accumulated = delta`, cùng hướng thì cộng dồn (kẹp ±4×threshold);
     - `wanted` theo quy tắc của `headerCollapsed` hiện có nhưng dùng `accumulated` và `thresholdPx`;
     - về đầu (`firstVisible <= 0 && firstTop >= 0`) → `wanted = false`.
   - **`onPhase(phase)`** cập nhật `scroll`.
   - **`pendingChange(): Boolean?`** trả `wanted` khi `wanted != applied && scroll != TOUCH`, ngược lại `null`.
   - **`markApplied(value)`:** `applied = value`, `accumulated = 0`, đặt cờ `rebaseline`.
   - **`reset()`** về chưa gấp, không có mốc.
   - Ngưỡng: `MeloLibraryPolicy.collapseThresholdPx(density) = round(16 × density)`. Giữ `COLLAPSE_THRESHOLD_PX` và hàm `headerCollapsed` cũ nếu test/code khác còn dùng; không xoá vô cớ.

**S3 — Nối vào `MeloLibraryPane.kt`.**
- Thay `collapsed`/`lastScrollKey` bằng `HeaderFold` + `prevFirst`/`prevTop`. Giữ tên `collapsed` dưới dạng getter của `fold.applied`, vì `applyTrim()`/`trimParts()`/`resetScroll()` đang đọc.
- **`onScrollStateChanged`:** map `SCROLL_STATE_TOUCH_SCROLL→TOUCH`, `FLING→FLING`, `IDLE→IDLE`, rồi gọi `applyFoldIfAllowed()`.
- **`onScroll`:**
  - nếu cờ rebaseline đang bật → chỉ lưu `prevFirst/prevTop` rồi tắt cờ;
  - ngược lại → tính `scrollDeltaPx`, với `fallbackPitchPx = s.rowHeight + list.dividerHeight` (dùng divider thật), gọi `fold.onDelta(...)`, lưu mốc, rồi gọi `applyFoldIfAllowed()`.
- **`applyFoldIfAllowed()`:** `fold.pendingChange()?.let { fold.markApplied(it); …chạy applyCollapse… }`. Giữ nguyên nhánh `list.isInLayout → post` (ghi chú D3 hiện có).
- **`resetScroll()`:** `fold.reset()` + đặt lại mốc (`prevFirst = -1`), giữ gọi `onHeaderCollapse(false)` + `applySourceCardsVisibility()` như cũ. `detach()` giữ `removeCallbacks(applyCollapse)`.
- Không đổi logic khôi phục vị trí (songRestore/anchor), tải thêm, ảnh bìa, thanh chữ cái.

**S4 — Log Debug để đo.** Giống `anchorLog` (chỉ khi `BuildConfig.DEBUG`), tag `MeloHeaderFold`, mỗi lần áp dụng ghi: `collapsed=<bool> phase=<IDLE|TOUCH|FLING> first=<n> layer=<layer>`. Không log tên bài/PII.

**S5 — Unit test** (`MeloLibraryPolicyTest.kt` hoặc `MeloHeaderFoldPolicyTest.kt` mới). Tối thiểu:
- (a) `scrollDeltaPx` cùng dòng; qua ranh giới dòng với dòng thật cao 230px trong khi pitch ước lượng 210px → delta dương nhỏ (không âm); nhánh dự phòng; chưa có mốc → 0.
- (b) Hồi quy nguyên nhân 2: mô phỏng cuộn xuống đều qua 10 dòng cao khác nhau → `wanted` không bao giờ quay về `false`.
- (c) Hồi quy nguyên nhân 1: khi `TOUCH`, chuỗi delta +200 / −200 / +200… → `pendingChange()` luôn `null`; chuyển `IDLE` → đúng 1 lần đổi.
- (d) `FLING` cuộn xuống vượt ngưỡng → đổi ngay 1 lần; sau `markApplied`, delta đối ngược nhỏ hơn ngưỡng không đổi lại.
- (e) Về đầu → hiện header; `reset()` → chưa gấp.
- (f) Ngưỡng theo density (1.0 → 16, 2.625 → 42).
- Giữ test `headerCollapsed` cũ xanh, hoặc cập nhật kèm lý do nếu đổi chữ ký.

**S6 — Gate Ni** (CLAUDE.md mục 4, log ra file, chỉ đọc tổng kết):
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```
- Ghi số test/skipped/failed theo XML (baseline tích hợp sau lời nhạc: 2445/21/0/0 mỗi biến thể; sau “nhiều playlist có tên” có thể nhiều hơn, lấy số thực).
- Commit mã + test. Cập nhật `-bao-cao.md` và `-ban-giao.md`.

**S7 — Build Tiny.**
- Git bundle từ nhánh, kiểm SHA256, clone cô lập mới trên Tiny (không đụng các thư mục cấm ở CLAUDE.md mục 1). Build Debug có Telegram config ở Tiny.
- Verify package `app.melovault.player`, versionCode không thấp hơn bản đang cài (giữ vc121 như các nhiệm vụ trước, **không tự tăng**), cert khớp, config chỉ ghi true/false, SHA-256/bytes.

**S8 — Cài và QA Sony.**
- Kiểm MediaSession không phát rồi mới chạy `adb -s QV725WEQ54 install -r <APK_VALIDATED>`.
- Chạy Q1–Q6 (mục 7) với screenrecord và `logcat -s MeloHeaderFold`. Trích dải khung để so trước/sau.
- Ghép `00_tong_hop.png`. Tắt màn trong `finally`, ghi `SONY_SCREEN_END`.

**S9 — Bàn giao APK/ảnh** theo CLAUDE.md mục 8–9: Drive Ni N owner-only, `-apk-test.md`, `-hinh-nghiem-thu.md`, nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**. Video/ảnh có tên bài/kênh riêng tư thì không commit, chỉ để ở Drive owner-only.

**S10 — Hồ sơ cuối.** Cập nhật `-bao-cao.md` (≤ ~250 dòng), `-ban-giao.md`, commit. Đưa prompt sơ bộ (`PROMPT_NGHIEM_THU_SO_BO.md`) đã điền commit/APK/hash. Dừng.

## 7. Thao tác QA (Sony XQ-AS52, dọc, giao diện Mới; màn ON chỉ trong lúc chạy)

| # | Màn hình / thao tác | Kỳ vọng | Bằng chứng |
|---|---|---|---|
| Q1 | Tab **Playlist** → mở playlist ≥ 20 bài → lướt nhanh xuống 3 lần liên tiếp | Header gấp **một lần** ở cử chỉ đầu rồi giữ nguyên; danh sách không nhảy qua lại | screenrecord + dải khung; logcat `MeloHeaderFold` ≤ 1 dòng cho cả chuỗi |
| Q2 | Cùng màn, kéo chậm giữ tay 1,5 giây (`swipe … 1500`) | Lúc còn giữ tay header không đổi; nhả tay mới gấp (nếu chưa gấp); không giật | video + log `phase=IDLE` hoặc `FLING` |
| Q3 | Giữa danh sách, lướt nhanh lên 1 lần | Header hiện lại **một lần** | video + log |
| Q4 | Về đầu danh sách, rồi đổi sang tab **Tất cả** | Header hiện; danh sách ở đầu (`resetScroll`) | ảnh trước/sau |
| Q5 | Tab **Tất cả** (hoặc **Đã tải**): lướt nhanh qua nhiều trang để kích hoạt tải thêm | Không nháy header; tải thêm vẫn chạy; ảnh bìa hiện khi dừng | video + log |
| Q6 (tuỳ chọn) | Xoay ngang (Hai khung), lướt nhanh danh sách | Thẻ nguồn/khung không nhảy liên tục | video; không làm được thì ghi CHƯA KIỂM |

So sánh với `01_sony_baseline_playlist.mp4` (S1).

**Tiêu chí ĐẠT kỹ thuật:**
- mỗi cử chỉ ≤ 1 lần đổi header (log + dải khung);
- test Debug/Release xanh, 0 failures;
- diff chỉ gồm file ở mục 8.

Âm thanh không liên quan: ghi **CHƯA XÁC NHẬN ÂM THANH THỰC** nếu có phát nhạc.

**Thao tác để chủ dự án tự thử** (máy của chủ dự án hoặc Sony):
1. Playlist → mở một playlist dài → vuốt nhanh lên/xuống nhiều lần: không còn nháy.
2. Kéo chậm giữ tay: header chỉ gấp khi nhả tay (hành vi mới, xem mục 5).
3. Tất cả / Đã tải: vuốt nhanh qua nhiều trang: không nháy.
4. Về đầu danh sách: header hiện lại.

## 8. File dự kiến thay đổi

- `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloLibraryPolicy.kt` (hàm thuần, ngưỡng dp)
- `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloLibraryPane.kt` (nối scroll listener, resetScroll)
- (tuỳ chọn) `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloHeaderFoldPolicy.kt` (mới)
- `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloLibraryPolicyTest.kt` và/hoặc `MeloHeaderFoldPolicyTest.kt` (mới)
- Hồ sơ `docs/plans/2026-11-01-nhay-header-khi-cuon-*.md`

**Không** sửa `MeloPhoneScreen.kt`/`MeloTabletScreen.kt` trừ khi bắt buộc để biên dịch; nếu phải sửa thì ghi lý do trong báo cáo.

## 9. Rủi ro, rollback, điểm dừng

- **Rủi ro:**
  - Hành vi “gấp khi nhả tay” có thể không đúng ý chủ dự án (mục 5) → nghiệm thu quyết định, phương án B là nhiệm vụ riêng.
  - Áp dụng thay đổi khi đang FLING làm danh sách giãn ra một lần (một bước nhảy duy nhất, chấp nhận được).
  - Đụng `applyTrim` UI-01 (thẻ nguồn) → QA Q4/Q6.
  - Logic khôi phục vị trí (`songRestore`, `pendingTopReset`) gọi `onScroll` trong layout → giữ nhánh `isInLayout → post`, chạy QA đổi tab và quay lại.
- **Rollback:** revert commit mã trên nhánh `dot/…`. Không đụng dữ liệu/DB/TDLib nên không có migration.
- **DỪNG và báo nếu:**
  - baseline/HEAD lệch, cây bẩn lạ, agent khác đang sửa;
  - cần đổi kiến trúc layout (phương án B) để đạt tiêu chí;
  - Sony không nhận, unauthorized, signature mismatch, downgrade, APK thiếu config;
  - test cũ đỏ không rõ lý do.

## 10. Điều chưa rõ

- [CẦN XÁC NHẬN khi nghiệm thu] Chủ dự án chấp nhận “header chỉ gấp khi nhả tay/khi đang trôi” thay vì gấp ngay trong lúc kéo.
- Nguyên nhân 2 mới chứng minh bằng code; S1/S8 có log để xác nhận trên máy. Video gốc quay trên máy của chủ dự án (1080×2340, không phải Sony 1080×2520); kết quả Sony có thể khác chút về mật độ điểm ảnh.
