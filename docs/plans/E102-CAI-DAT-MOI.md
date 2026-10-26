# Kế hoạch — E102-CAI-DAT-MOI (thiết kế lại menu ⋮ và màn Cài đặt)

## 0. Bối cảnh và mục tiêu

Chủ dự án gửi ảnh menu ⋮ trên điện thoại ngày 26/10 và yêu cầu "thiết kế lại giao diện phần cài đặt và các nội dung trong nó". Opus vẽ bản thiết kế 5 màn (canvas riêng tư: https://claude.ai/artifact/Tc9eNuojXcYrvsVGRxsn9Y). Chủ dự án **duyệt** ngày 26/10 ("ok tôi duyệt"). Toàn bộ nội dung cần làm được ghi lại ở mục 3. Sonnet làm theo kế hoạch này, không cần mở canvas.

Hiện trạng:
- `TelegramPreviewActivity.showLibraryOverflow` (3373-3438) là một `PopupMenu` 11 mục. Các mục đó lẫn lộn đồng bộ, nguồn, đăng xuất và 3 lối vào giao diện. "Giao diện: Cockpit…" ghi ✓ cả khi đang dùng thư viện Mới, nơi lựa chọn đó không có tác dụng.
- `MeloUiLabActivity` ("Cài đặt giao diện (thử nghiệm)") là một trang cuộn dài gồm 9 nhóm. Ghi chú kỹ thuật dài nằm lẫn với lựa chọn, và bảng 25+ số đo nằm ngay đầu trang.

**Mục tiêu**
1. Menu ⋮ còn **6 phần**: thẻ đồng bộ, Tin đã lưu, Nhạc trên máy, Nhóm/kênh/bot Telegram, Mở tệp nhạc, **Cài đặt**.
2. Một màn **Cài đặt** có trang chính chia nhóm và 3 trang con (Giao diện & màu sắc · Thiết bị & màn hình · Nâng cao). **Đăng xuất** chuyển vào trang chính.
3. Mọi lựa chọn đang có vẫn còn và vẫn ghi vào **đúng khóa prefs cũ**. Không có lựa chọn mới, không di trú dữ liệu.

**Không đổi:** logic đồng bộ/TDLib/DB/Media3/service, nội dung `MeloSyncSettingsActivity`, `MeloUiPreviewActivity`, Car Mode (chỉ đổi tên nhãn "Cài đặt"), khóa prefs và giá trị mặc định, manifest, signing.

## 1. Git baseline (đọc 26/10/2026)

| Mục | Giá trị |
|---|---|
| Nền | `feature/e93-03d-y700-offline-cockpit` @ **`ac53c4e`** (handoff sau merge E101 `17f2d58`), cây sạch, `git diff --check` sạch |
| App | vc117 / `0.6.92-beta` (`app/build.gradle:93-94`); Shanling và Sony đang cài vc117 (theo HANDOFF) |
| Test nền | 2060 test mỗi biến thể, 21 bỏ qua, 0 lỗi (Opus `--rerun-tasks` 26/10, cây E101) |
| Nhánh | `dot/E102-CAI-DAT-MOI` cắt từ `ac53c4e`; commit kế hoạch: `<PLAN_SHA>` (xem `git log -1` của nhánh) |

## 2. Lối vào hiện tại (đã đối chiếu mã, thư mục `app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`)

| Nơi | Hiện tại | Đổi thành |
|---|---|---|
| `TelegramPreviewActivity.showLibraryOverflow` (3373) – gọi từ ⋮ cũ (2530), `MeloPhoneScreen` ⋮ (88), `MeloCompactScreen` "Thêm…" (398) | `PopupMenu` 11 mục | Popup mới (mục 3.1) |
| `host.openUiLab()` → `uiLabIntent()` (5121) – gọi từ Compact menu (377), Phone ⚙ (85) + SyncRow (35), Tablet (42, 176), Car Nav.SETTINGS (195), mục 10 của overflow | mở `MeloUiLabActivity` | mở Cài đặt trang chính (cùng Activity) qua launcher có kết quả |
| Theme preset Cockpit/Portrait/Classic | mục 8 → `showThemePickerDialog` (1410), prefs `UiThemePolicy.PREFS_NAME` | Giao diện → chỉ hiện khi Kiểu thư viện = **Cũ** |
| Hiển thị nguồn List/Bento | mục 9 → `showSourceDisplayModeDialog` (1474), cùng prefs trên | Giao diện → nút chọn ngang |
| Đăng xuất | mục 7 → hộp xác nhận → `logoutUser()` (7303) | Cài đặt → hàng đỏ → **cùng** hộp xác nhận → trả kết quả về Activity → `logoutUser()` |
| Nhãn | Compact "⚙ Cài đặt giao diện" (370), "Thêm… (đồng bộ, đăng xuất)" (391); Phone/SyncRow mô tả "Cài đặt giao diện" | "⚙ Cài đặt", "Thêm… (đồng bộ, nguồn)", "Cài đặt" |

Trạng thái đồng bộ: `TelegramSyncCoordinator.isBusy()` = `isSyncing`. Sau `pauseSync()` thì `isPaused = true` **nhưng `isSyncing` vẫn true** (189-206). Vì vậy nhãn hiện tại "Tạm dừng đồng bộ" vẫn hiện sau khi đã dừng, và bấm lại không có tác dụng (`pauseSync` trả null). Cần thêm getter chỉ đọc `isPaused()`.

## 3. Thiết kế đã duyệt (nội dung chính xác)

Màu và cỡ chữ lấy từ `MeloComponents.palette` / `sizes` của profile thật (giống UiLab hiện nay: `realMeasurements` → `MeloDesignTokens.sizes`), **không** chép mã màu từ canvas. Chạm tối thiểu `sizes.minTouch`. Biểu tượng dùng drawable có sẵn (`ic_*`), thiếu thì dùng chữ/ký hiệu như các màn khác, **không thêm drawable mới**.

### 3.1 Menu ⋮ (PopupWindow tự dựng, neo ở `anchor`, rộng ≈ 312 dp hoặc `min(312dp, màn − 2×margin)`)
1. **Thẻ đồng bộ** (nền `surfaceRaised`, bo góc). Dòng trạng thái có chấm màu và 2 nút. Nội dung theo `MeloSettingsPolicy.syncCard(state)`:

| Trạng thái | Dòng 1 | Dòng 2 | Nút chính | Nút phụ |
|---|---|---|---|---|
| chưa đăng nhập | "Chưa đăng nhập Telegram" | — | (ẩn cả thẻ) | — |
| đang chạy (`isSyncing && !isPaused`) | "Đang đồng bộ…" | "[N] bài" nếu biết | **Tạm dừng** → `pauseSync()` + `handleSyncAction` (như mục 4 cũ) | (ẩn) |
| đã tạm dừng (`isSyncing && isPaused`) | "Đã tạm dừng đồng bộ" | — | **Tiếp tục** → `scanAllTelegramAudio(browseAfter=false, incremental=false, forceRestart=false)` | **Quét toàn bộ** |
| rảnh, có `lastSyncCompletedMs > 0` | "Thư viện đã đồng bộ" | "Cập nhật lúc HH:mm · [N] bài" (khác ngày: "dd/MM HH:mm") | **Lấy bài mới** → như mục 2 cũ (`incremental=true`) | **Quét toàn bộ** → như mục 3 cũ (`forceRestart=true`) |
| rảnh, chưa đồng bộ lần nào | "Chưa đồng bộ" | — | Lấy bài mới | Quét toàn bộ |

   [N] = `meloLibraryDiag?.liveSongs` khi ≥ 0. Không có thì bỏ phần "· [N] bài". Mọi nút giữ điều kiện `auth.isReady()` và `autoRecoveryPolicy.onManualScanRequested()` như cũ. Bấm nút thì đóng popup.
2. **Tin đã lưu** (như mục 1 cũ)
3. **Nhạc trên máy** (như mục 11 cũ, chỉ khi `meloLayer != null && carScreen == null`)
4. **Nhóm / kênh / bot Telegram** (như mục 5 cũ)
5. **Mở tệp nhạc (Drive / thiết bị)** (như mục 6 cũ)
6. Đường kẻ, rồi **Cài đặt** kèm chữ phụ "Giao diện, tài khoản…" → `openSettings()`

Danh sách mục do `MeloSettingsPolicy.menuItems(authReady, showLocal)` trả ra (thuần, có test). Khi chưa đăng nhập, các mục cần đăng nhập vẫn hiện nhưng mờ/không bấm được, giống hành vi `if (auth.isReady())` cũ.

### 3.2 Cài đặt — trang chính (`Page.HOME`, tiêu đề "Cài đặt", nút ← đóng màn)
- **Thẻ tài khoản** (chỉ khi `EXTRA_SIGNED_IN = true`): ô tròn chữ "T", dòng "Tài khoản Telegram", dòng phụ "Đã đăng nhập · [N] bài · [K] nguồn" (từ extras có sẵn `EXTRA_LIB_LIVE_SONGS` / `EXTRA_LIB_LIVE_CHANNELS`; thiếu số nào thì bỏ phần đó), dòng "Đồng bộ xong lúc …" từ `EXTRA_LIB_LAST_SYNC_MS`. **Không** gọi TDLib để lấy tên người dùng.
- Nhóm **GIAO DIỆN**: "Giao diện & màu sắc" (tóm tắt `homeSummary`: "Thư viện Mới · Màu Hiện tại · Nguồn dạng Bento"), "Màn Đang phát" (tóm tắt tên kiểu). Cả hai mở `Page.APPEARANCE`.
- Nhóm **THƯ VIỆN & ĐỒNG BỘ**: "Đồng bộ Playlist giữa các máy" + nhãn "Thử nghiệm", chữ phụ "Playlist luôn lưu trên máy. Đồng bộ đám mây chưa bật.", mở `MeloSyncSettingsActivity` như nút cũ. (Hai hàng "Đồng bộ thư viện" và "Nhóm/kênh nguồn" trong canvas **bỏ** ở bản này vì chỉ là thông tin, đã có trong menu ⋮.)
- Nhóm **THIẾT BỊ & MÀN HÌNH**: "Bố cục Gọn (máy nhỏ)", "Car Mode (FYT 7870)", "Chừa chỗ nút ••• hệ thống". Mỗi hàng ghi tên lựa chọn hiện tại, cùng mở `Page.DEVICES`.
- Nhóm **NÂNG CAO**: "Thông tin màn hình & giả lập" → `Page.ADVANCED`.
- Nhóm **TÀI KHOẢN** (chỉ khi đã đăng nhập): "Đăng xuất Telegram" màu `warning`/đỏ có sẵn trong palette, chữ phụ "Luôn hỏi xác nhận trước khi đăng xuất". Hộp xác nhận **giữ nguyên chữ** cũ ("Đăng xuất tài khoản Telegram?" / "Bài đã tải và phiên đăng nhập trên thiết bị sẽ thay đổi." / Hủy / Đăng xuất). Bấm Đăng xuất thì `setResult(RESULT_LOGOUT)` và `finish()`.
- Chân trang: "MeloVault `BuildConfig.VERSION_NAME` · vc`VERSION_CODE`".

### 3.3 Giao diện & màu sắc (`Page.APPEARANCE`)
- **Kiểu thư viện**: 2 thẻ chọn "Mới" ("Đầu trang gọn, chip lọc, “Phát hết”") / "Cũ" ("Cockpit / Portrait / Cổ điển"), ghi `MeloLibraryPolicy.saveStyle`. Chữ gợi ý: "Bản Gọn (Shanling) và Car Mode có màn riêng, không đổi theo mục này."
  - Chỉ khi = Cũ: danh sách 3 nút tròn Portrait Edge-to-Edge Pro / Cockpit 2-Pane Split / Cổ điển (Classic), ghi `UiThemePolicy.savePreset` vào prefs `UiThemePolicy.PREFS_NAME`.
- **Kiểu màu**: 2 thẻ "Hiện tại" ("Xanh đêm, ô chữ cái") / "Teal sáng" ("Sáng hơn, ảnh bìa nhỏ, menu có biểu tượng"), ghi `MeloThemePolicy.save`. Dưới đó là công tắc **"Tải ảnh bìa từ Telegram"** ("Tắt để dùng ô chữ cái, tiết kiệm mạng"), `MeloThemePolicy.setRowArtSwitch`.
- **Hiển thị nguồn Telegram**: nút chọn ngang Danh sách | Bento Grid, ghi `TelegramVaultBentoPolicy.saveSourceDisplayMode` vào prefs `UiThemePolicy.PREFS_NAME`.
- **Màn Đang phát**: 2 thẻ "Studio Deck" ("Ảnh bìa lớn, −30s / +60s, hẹn giờ tắt, hàng đợi") / "Màn cũ" ("Hộp thoại Đang phát trước đây"), ghi `MeloNowPlayingPolicy.saveStyle`.
- Khung "Xem trước" trên canvas **không làm** ở bản này (giảm rủi ro).

### 3.4 Thiết bị & màn hình (`Page.DEVICES`)
- Thẻ thông tin: "Máy này đang dùng: **<effectiveLayoutName>**. Thay đổi có hiệu lực khi quay lại thư viện."
- **Bố cục Gọn**: nút chọn ngang Tự động | Luôn bật | Tắt (`CompactSetting.AUTO/ON/OFF`). Chữ gợi ý đổi theo lựa chọn (lấy từ `MeloSettingsPolicy.compactHint`). Nút viền "Thử trong khung Shanling (768 × 1280)" = `EMULATE_SHANLING`. Đang ở EMULATE thì nút này hiện trạng thái chọn và không ô nào trong nút chọn ngang được chọn.
- **Car Mode**: nút chọn ngang Tắt | Bật. Chữ mô tả cũ rút gọn. Hai nút viền "Thử khung · mật độ 160" / "· 240" = `EMULATE_FYT_160/240`. Thêm dòng "Khung thử vẽ đúng 1920 × 1200 điểm ảnh, nên cầm máy ngang."
- **Chừa chỗ nút ••• hệ thống**: Tự động | Luôn chừa | Không chừa. Chữ gợi ý lấy từ `bandHint`.

### 3.5 Nâng cao (`Page.ADVANCED`)
- Dòng mở đầu: "Dành cho việc chỉnh bố cục. Không động tới đăng nhập, thư viện, playlist hay cache."
- **Thông tin màn hình**: mặc định 6 dòng (Bố cục thực tế đang dùng, Độ rộng × cao (dp), Cạnh ngắn nhất, Mật độ Android, Kích thước ước tính, Máy + Android). Nút "Xem tất cả số đo" / "Thu gọn" hiện đủ danh sách `infoLines` cũ. Nút "Sao chép số đo" **luôn sao chép đủ** danh sách như cũ. Việc chọn 6 nhãn nằm ở `MeloSettingsPolicy.SHORT_INFO_LABELS`, so theo nhãn.
- **Giả lập thiết bị**: danh sách nút tròn 4 `Emulation` (dòng chính = tên máy, dòng phụ = kích thước). Công tắc "Xem như Car Mode (chỉ trong khung)". Nút viền "Mở khung xem trước thành phần" và ghi chú cũ.
- Các ghi chú "Mở màn này trên S25 Ultra và Y700…" chuyển thành 1 dòng chữ nhỏ dưới nút Sao chép.

### 3.6 Điều hướng trong Cài đặt
- Một Activity (`MeloUiLabActivity`, **giữ tên lớp và manifest**), có biến `page` và `render()` vẽ theo trang. Mở trang con bằng `page = X; render()` (cuộn về đầu).
- Nút ← và nút Quay lại hệ thống: trang con → HOME, HOME → `finish()`. Dùng `onBackPressedDispatcher` của AndroidX, không override hàm deprecated nếu có thể.
- `page` lưu trong `onSaveInstanceState` để xoay màn không bị về HOME.
- `EXTRA_PAGE` (tùy chọn) cho phép mở thẳng một trang. Bản này chỉ dùng HOME.

## 4. File sửa (production)

| File | Sửa |
|---|---|
| **mới** `MeloSettingsPolicy.kt` | thuần Kotlin: `enum Page`, `SyncState`/`syncCard()`, `menuItems()`, `homeSummary()`, `presetVisible(LibraryStyle)`, `compactHint()`, `bandHint()`, `SHORT_INFO_LABELS` + `shortInfo(lines)`, `formatSyncTime(ms, now, zone)` |
| **mới** `MeloOverflowMenu.kt` | dựng `PopupWindow` từ `MeloComponents` + danh sách của policy; callback cho từng mục; không tự gọi TDLib |
| `MeloUiLabActivity.kt` | tách `render()` thành `renderHome/Appearance/Devices/Advanced`; thêm builder nhỏ (hàng cài đặt, nút chọn ngang, thẻ chọn, công tắc); đăng xuất bằng `setResult`; xử lý Back; `EXTRA_SIGNED_IN`, `RESULT_LOGOUT`, `EXTRA_PAGE`; tiêu đề "Cài đặt" |
| `TelegramPreviewActivity.kt` | `showLibraryOverflow` dùng `MeloOverflowMenu` (cùng nhánh hành động cũ); `uiLabIntent()` thêm `EXTRA_SIGNED_IN`; `settingsResult = registerForActivityResult(StartActivityForResult)`, RESULT_LOGOUT kèm `auth.isReady()` thì gọi `logoutUser()`; thay **mọi** `startActivity(uiLabIntent())` bằng `settingsResult.launch(uiLabIntent())`; `onResume` đọc lại `UiThemePolicy.getSavedPreset` / `getSavedSourceDisplayMode`, khác giá trị hiện tại thì áp dụng (preset: gán + `applyThemeColors()`; nguồn: gán + `updateSourceViewModeToggleUi()` + `refreshSongRows()`), **không ghi lại prefs** |
| `TelegramSyncCoordinator.kt` | thêm đúng 1 getter `fun isPaused(): Boolean = isPaused` (không đổi logic) |
| `MeloCompactScreen.kt` | đổi 2 nhãn (mục 2) |
| `MeloPhoneScreen.kt`, `MeloComponents.kt` (SyncRow) | `contentDescription` "Cài đặt" |
| `app/build.gradle` | vc117 → **vc118**, versionName `0.6.92` → **`0.6.93`** (giữ hậu tố `-beta`) |

`showThemePickerDialog`/`showSourceDisplayModeDialog`/`toggleThemePreset` không còn được gọi sau khi đổi menu. **Không xóa** ở bản này, chỉ thêm comment `// E102: không còn lối vào, giữ để rollback`. Opus nghiệm thu sẽ quyết định dọn sau.

Test mới: `app/src/test/java/com/bsdung/musicassistantcar/data/telegram/MeloSettingsPolicyTest.kt`.

## 5. Bước cho Sonnet

**Phiên 1: code + test Ni (S0–S6)**
- **S0** Kiểm Git: nhánh `dot/E102-CAI-DAT-MOI`, HEAD = commit kế hoạch, cây sạch, không agent khác.
- **S1** Viết `MeloSettingsPolicy` + test (TDD) cho:
  - `syncCard`: đủ 5 trạng thái, nhãn chính xác như bảng 3.1, [N] thiếu/0/âm.
  - `menuItems`: có/không Nhạc trên máy, chưa đăng nhập thì mục bị khóa, không còn "Đăng xuất"/"Giao diện"/"Hiển thị nguồn".
  - `homeSummary`: mọi tổ hợp.
  - `presetVisible`: chỉ LEGACY.
  - `compactHint`/`bandHint`: đủ enum.
  - `shortInfo`: giữ đúng thứ tự, thiếu nhãn thì bỏ qua.
  - `formatSyncTime`: cùng ngày / khác ngày / 0.

  *Commit 1* `E102 S1: settings policy + tests`.
- **S2** `TelegramSyncCoordinator.isPaused()` + 1 test (pause → `isBusy && isPaused`; startSync tiếp → false). *Commit 2*.
- **S3** `MeloOverflowMenu` + nối vào `showLibraryOverflow` (giữ nguyên thân từng nhánh hành động, chỉ đổi cách hiển thị). Popup đóng khi bấm ngoài, khi bấm mục và khi Activity `onPause`. *Commit 3*.
- **S4** Viết lại `MeloUiLabActivity` theo 3.2–3.6. Cài đặt nào cũng phải ghi đúng hàm save cũ. *Commit 4*.
- **S5** Kết quả đăng xuất, `onResume` đọc lại preset/nguồn, đổi nhãn Compact/Phone/SyncRow. *Commit 5*.
- **S6** Bump vc118 / 0.6.93. Chạy đủ lệnh ở mục 6 (log ra file, chỉ đọc dòng tổng kết). Ghi "Phiên 1" vào `docs/plans/E102-CAI-DAT-MOI-bao-cao.md`. *Commit 6*.

**Phiên 2: build Tiny + QA + báo cáo (S7–S8)**
- **S7** Gửi Git bundle (kèm SHA256) sang Tiny, build trong clone cô lập có Telegram config. Kiểm package/vc118/cert/config. Cài Shanling (Ni) và Sony (Tiny) theo cổng an toàn.
- **S8** QA mục 7, ảnh `00_tong_hop.png`, hoàn tất báo cáo, thông tin bàn giao APK, commit.

## 6. Test và build

- JVM: `MeloSettingsPolicyTest` (≥ 20 ca) + test coordinator. Tổng ≥ 2060 + số test mới, 21 bỏ qua, 0 lỗi, cả Debug lẫn Release.
- Lệnh Ni (CLAUDE.md §4):
```
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```
- JVM không chứng minh được giao diện thật. Phần đó dựa vào QA thiết bị ở mục 7.

## 7. QA thiết bị: Sonnet tự làm qua ADB (CLAUDE.md §3, §6, §7; `docs/workflow/SCREEN_LIFECYCLE_QA.md`)

**Cổng trước khi cài:**
- `adb devices -l` thấy serial ở trạng thái device; getprop đúng model.
- Package `app.melovault.player`, máy đang ở vc117, APK mới vc118 (không downgrade); cert khớp app đang cài; Telegram config = true (APK Tiny).
- `dumpsys media_session` không PLAYING. Nếu đang PLAYING: STOP và hỏi.
- Chỉ dùng `adb -s <serial> install -r "<APK_VALIDATED>"`.

**Trước bước 1:** chụp trang Cài đặt cũ (vc117) để làm ảnh "trước". Ghi lại giá trị hiện tại của mọi lựa chọn. **Mọi lựa chọn đổi trong QA phải trả về đúng giá trị ban đầu** trong `finally`.

| # | Thiết bị | Hành động | Kỳ vọng (PASS) | Ảnh |
|---|---|---|---|---|
| 1 | Shanling vc118 (Gọn) | Thư viện → nút lọc → "Thêm… (đồng bộ, nguồn)" | Popup mới: thẻ đồng bộ (dòng trạng thái + Lấy bài mới/Quét toàn bộ, hoặc Tạm dừng nếu đang chạy) + Tin đã lưu, Nhạc trên máy, Nhóm/kênh/bot, Mở tệp nhạc, Cài đặt; **không** còn Đăng xuất/Giao diện/Hiển thị nguồn; chữ không bị cắt | 01 |
| 2 | Shanling | Bấm **Cài đặt** (từ popup); chụp, vuốt xuống, chụp | Trang chính: thẻ tài khoản, 5 nhóm đúng thứ tự 3.2, chân trang `0.6.93-beta · vc118` | 02a, 02b |
| 3 | Shanling | **Giao diện & màu sắc** → chọn **Cũ** → chụp → chọn **Mới** → chụp → Back | Khi Cũ: hiện 3 kiểu Portrait/Cockpit/Cổ điển; khi Mới: ẩn; Back về trang chính (không đóng màn) | 03a, 03b |
| 4 | Shanling | **Thiết bị & màn hình** → chụp → Back → **Nâng cao** → "Xem tất cả số đo" → chụp | Bố cục Gọn = "Tự động" được chọn, dòng "Máy này đang dùng: Gọn (máy nhỏ)"; Nâng cao có 6 dòng rồi bung ra đủ | 04a, 04b |
| 5 | Shanling | Trang chính → **Đăng xuất Telegram** → chụp hộp xác nhận → bấm **Hủy** → Back về thư viện → chụp | Hộp đúng chữ cũ; sau Hủy vẫn đăng nhập, thư viện còn bài. **TUYỆT ĐỐI không bấm "Đăng xuất"** | 05a, 05b |
| 6 | Sony (Tiny `QV725WEQ54`) vc118, Điện thoại dọc | ⋮ ở đầu thư viện → chụp popup → Cài đặt → chụp | Như bước 1–2 trên màn điện thoại; popup nằm trong màn, không tràn | 06a, 06b |

- Bước 6 không chặn PASS nếu ADB của Tiny chưa sẵn sàng (ghi CHƯA KIỂM). Không phát nhạc. Không bấm Lấy bài mới/Quét toàn bộ/Tạm dừng. Bước 1 chỉ để nhìn.
- **Cleanup (`finally`):** trả mọi lựa chọn về giá trị ban đầu, `input keyevent 223`, kiểm `dumpsys display` có `Display State=OFF`, ghi `SHANLING_SCREEN_END` / `SONY_SCREEN_END`.
- Ảnh lưu ở `docs/plans/artifacts/E102-CAI-DAT-MOI/NN_<máy>_<mô tả>_<HHMMSS>.png` và ghép thành `00_tong_hop.png`. Ảnh có tên bài/kênh **không commit**.
- **Không kiểm được:** Y700/S25/FYT/Car Mode trên máy thật; nút Tạm dừng/Tiếp tục khi đang đồng bộ thật (chỉ có unit test); đăng xuất thật (cấm).

### Bác sĩ tự kiểm tra (Shanling M3 Ultra, sau khi có APK candidate vc118, hoặc duyệt ảnh từ xa)
1. Mở MeloVault → nút lọc → "Thêm…": menu mới gọn, phía trên có ô "Thư viện đã đồng bộ" với 2 nút.
2. Bấm **Cài đặt**: thấy các nhóm Giao diện / Thư viện & đồng bộ / Thiết bị & màn hình / Nâng cao / Tài khoản.
3. Vào **Giao diện & màu sắc**, bấm "Cũ": hiện 3 kiểu Cockpit/Portrait/Cổ điển. Bấm lại "Mới": 3 kiểu biến mất.
4. Bấm nút Quay lại: về trang Cài đặt chính, không thoát hẳn.
5. Bấm "Đăng xuất Telegram": hỏi xác nhận. Bấm **Hủy** thì vẫn còn đăng nhập.

## 8. PASS / FAIL

**PASS khi đạt tất cả:**
- Test Debug + Release 0 lỗi, có đủ test mới; build OK; `git diff --check` sạch; chỉ đổi file trong mục 4 (cùng test và báo cáo).
- Shanling bước 1–5 PASS; Sony bước 6 PASS hoặc CHƯA KIỂM có lý do.
- Mọi lựa chọn cũ vẫn đổi được và ghi đúng khóa prefs: kiểm bằng test policy và qua bước 3 (không đổi giá trị mặc định).
- Không crash/ANR trong logcat suốt QA.
- Chủ dự án xem ảnh hoặc cài vc118 và nói **ĐẠT**.

**FAIL khi gặp bất kỳ điều nào:**
- Mất một lựa chọn cũ, hoặc ghi sai prefs.
- Đăng xuất chạy mà không hỏi, hoặc Hủy vẫn đăng xuất.
- Back từ trang con thoát hẳn Cài đặt.
- Popup tràn khỏi màn hoặc chữ bị cắt.
- Đổi Cũ/Mới hay Bento không có tác dụng khi quay lại thư viện.
- Crash/ANR.
- Sửa logic đồng bộ/TDLib/DB/Media3/service.

## 9. STOP: Sonnet dừng và báo, không tự xoay xở

- Cây bẩn không rõ nguồn, HEAD/nhánh lệch, hoặc agent khác đang sửa/build.
- Để làm được mà phải đổi logic `TelegramSyncCoordinator` (ngoài getter), service, DB, TDLib, manifest hoặc thêm Activity mới.
- `registerForActivityResult` gây xung đột với luồng sẵn có (ví dụ Car Mode mở Cài đặt khi Activity bị tạo lại).
- Popup tự dựng lỗi trên Android 10 (Shanling): ghi lỗi và STOP. Phương án dự phòng là `PopupMenu` 6 mục, có dòng trạng thái đồng bộ là mục bị khóa; chỉ dùng khi Opus nghiệm thu đồng ý.
- ADB không thấy máy / unauthorized / signature mismatch / downgrade / sai package / APK thiếu Telegram config: BLOCKED phần QA. Cấm `uninstall`, `pm clear`, `-d`, xóa dữ liệu/cache/TDLib, **đăng xuất**, đổi tài khoản.
- Không có đường Git bundle tới Tiny: STOP và hỏi.

## 10. Rủi ro và rollback

| Rủi ro | Giảm thiểu |
|---|---|
| Đăng xuất qua kết quả Activity bị mất khi Activity chính bị hệ thống hủy | Launcher AndroidX vẫn giao kết quả sau khi tạo lại; kiểm `auth.isReady()` trước `logoutUser()`; lỗi thì người dùng chỉ phải bấm lại |
| Người dùng quen đổi Cockpit nhanh từ menu | Lối đi mới: Cài đặt → Giao diện (2 chạm). Chủ dự án đã duyệt |
| `onResume` áp preset làm giật màn mỗi lần quay về | Chỉ áp dụng khi giá trị **khác** |
| PopupWindow khác PopupMenu về vị trí/đổ bóng | Dùng `showAsDropDown(anchor)` + `elevation`; QA trên 2 máy |
| Màn Cài đặt dài trên Shanling (768×1280) | Dùng `sizes` của Gọn như UiLab hiện tại; QA bước 2 |

**Rollback:** revert theo thứ tự Commit 5 → 4 → 3 (từng phần độc lập). Commit 1–2 chỉ thêm code thuần và getter. Giữ vc118 để không downgrade. Không có dữ liệu hay di trú nào.

## 11. Chưa kiểm chứng / cần xác nhận

- [CẦN XÁC NHẬN] versionName `0.6.93` cho vc118.
- [CẦN XÁC NHẬN] Trạng thái "đã tạm dừng" chỉ suy từ mã (`isSyncing` vẫn true sau `pauseSync`). Đã có unit test, chưa thấy trên máy.
- Canvas có khung "Xem trước" và 2 hàng thông tin đồng bộ. Hai thứ này **cố ý bỏ** ở bản này, có thể làm sau nếu chủ dự án muốn.
- Y700, S25, FYT, Car Mode: chưa kiểm (chủ dự án tự xem). Âm thanh: không liên quan.
