# Nghiệm thu E102-CAI-DAT-MOI — Opus (phiên nghiệm thu độc lập)

**Ngày:** 26/10/2026 · **Người nghiệm thu:** Claude Opus (phiên mới, không Resume phiên lập kế hoạch/Sonnet) · **Quy tắc:** `PROMPT_OPUS_NGHIEM_THU.md` (đọc từ nhánh `dot/workflow-opus-nghiem-thu`, `ffcc584`)

## 1. Trạng thái

**BLOCKED — CHỜ CHỦ DỰ ÁN.** Phía kỹ thuật đạt: phạm vi đúng, test/build Opus tự chạy lại xanh, APK đạt gate và đã lên Drive owner-only, ảnh QA Shanling bước 1–5 và Sony bước 6 khớp kỳ vọng. Còn thiếu đúng tiêu chí cuối của kế hoạch mục 8: chủ dự án xem ảnh (hoặc dùng vc118 đã cài trên Shanling) và nói **ĐẠT**.

## 2. Git

| Mục | Giá trị |
|---|---|
| Nhánh / HEAD lúc nghiệm thu | `dot/E102-CAI-DAT-MOI` @ `6414e67`, cây sạch |
| Nền | `ac53c4e` (`feature/e93-03d-y700-offline-cockpit`, sau merge E101) |
| Commit | `cedcab9` kế hoạch · `27c2827` S1 · `5a75ccb` S2 · `9d331cb` S3 · `4c8ef8b` S4 · `39f8e16` S5 · `2f4d736` S6 (vc118) · `6414e67` báo cáo phiên 2 |
| `git diff --check ac53c4e..HEAD` | sạch |
| Phiên Sonnet | đã dừng (commit cuối 11:37, phiên không chạy khi bắt đầu 11:39) |

## 3. Phạm vi file (so với kế hoạch mục 4)

`git diff --stat ac53c4e..HEAD`: 11 file mã/test + 2 file kế hoạch/báo cáo. Đúng danh sách: `MeloSettingsPolicy.kt` (mới), `MeloOverflowMenu.kt` (mới), `MeloUiLabActivity.kt`, `TelegramPreviewActivity.kt`, `TelegramSyncCoordinator.kt` (+4 dòng: chỉ getter), `MeloCompactScreen.kt`/`MeloPhoneScreen.kt`/`MeloComponents.kt` (chỉ nhãn), `app/build.gradle` (chỉ vc/versionName), 2 file test. **Không có file ngoài phạm vi.** Không đụng manifest, signing, drawable, DB/schema, TDLib, Media3, service.

## 4. Đọc kỹ file rủi ro

| Điểm | Kết luận |
|---|---|
| `TelegramSyncCoordinator` | Chỉ thêm `@Synchronized fun isPaused()`; logic không đổi. |
| Lệch kế hoạch về "đã tạm dừng" (báo cáo mục 5.1) | Opus kiểm mã: `pauseSync()` (`TelegramSyncCoordinator.kt:193-211`) đặt `isPaused = true` **và** `isSyncing = false`; `startSync`/`cancelSync` trả `isPaused = false` (dòng 132, 220). Kế hoạch mục 2 mô tả sai; cách Sonnet cho `paused` ưu tiên trước `syncing` là đúng và có test. **Chấp nhận**, không phải REWORK. |
| Hành động menu ⋮ (`TelegramPreviewActivity.showLibraryOverflow`) | Thân từng nhánh giữ nguyên như mục 1/2/3/4/5/6/11 cũ, vẫn kiểm `auth.isReady()` và `onManualScanRequested()`. Đăng xuất / Giao diện / Hiển thị nguồn đã rời menu. |
| Đăng xuất | Hộp xác nhận chữ cũ ở `MeloUiLabActivity.kt:390-396` → `setResult(RESULT_LOGOUT)`; host chỉ `logoutUser()` khi `RESULT_LOGOUT && auth.isReady()`. Không còn đường đăng xuất không hỏi. |
| Lối vào Cài đặt | Không còn `startActivity(uiLabIntent())`; cả 2 chỗ dùng `settingsResult.launch` (menu ⋮ và `host.openUiLab()` cho Compact/Phone/Tablet/Car). `registerForActivityResult` khai báo ở mức thuộc tính (trước STARTED), đúng quy tắc AndroidX. |
| Khóa prefs | Mọi lựa chọn cũ của UiLab vẫn gọi đúng hàm save cũ (`MeloLibraryPolicy.saveStyle`, `MeloThemePolicy.save/setRowArtSwitch`, `MeloNowPlayingPolicy.saveStyle`, `MeloCompactPolicy/MeloCarPolicy.saveSetting`, `WindowInsetsPolicy.saveHandleBand`, `MeloScreenPolicy.saveEmulation/savePreviewCarMode`) trên prefs `MeloScreenPolicy.PREFS_NAME`. Preset và Bento chuyển từ hộp thoại cũ sang Cài đặt vẫn ghi `UiThemePolicy.savePreset` / `TelegramVaultBentoPolicy.saveSourceDisplayMode` trên `UiThemePolicy.PREFS_NAME`, đúng như `setThemePreset`/`setSourceDisplayMode` cũ. |
| `onResume` | Đọc lại preset/nguồn, chỉ áp dụng khi khác giá trị hiện tại, không ghi lại prefs. Lần mở đầu không áp dụng thừa vì `onCreate` đã đọc cùng prefs (dòng 878-879). |
| Popup | `PopupWindow(focusable=true)`, rộng `min(312dp, cửa sổ − 2×lề)`, cao giới hạn trong cửa sổ, nội dung trong `ScrollView`, đóng ở `onPause`. |

**Nhận xét nhỏ, không chặn:** thẻ đồng bộ coi `globalScanLoading || isBusy()` là "Đang đồng bộ". Nếu có lúc `globalScanLoading = true` mà coordinator không bận, nút "Tạm dừng" sẽ gọi `scanAllTelegramAudio(...)` và hàm này thoát ngay (`TelegramPreviewActivity.kt:6801`), tức là bấm không có tác dụng. Menu cũ cũng có hành vi này (nhãn "Tiếp tục đồng bộ" rồi thoát ngay), nên đây không phải lỗi mới. Có thể làm rõ ở việc sau nếu chủ dự án gặp.

## 5. Test và build (Opus tự chạy trên Ni, log ở scratchpad ngoài repo)

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease` | BUILD SUCCESSFUL (task test up-to-date nên chạy lại riêng như dòng dưới) |
| `:app:testShareableDebugUnitTest --rerun :app:testShareableReleaseUnitTest --rerun` | **BUILD SUCCESSFUL in 2m 5s**, 2 task test chạy thật (XML 11:53/11:54) |
| Debug (XML) | **2089** test, **21** bỏ qua, **0** lỗi |
| Release (XML) | **2089** test, **21** bỏ qua, **0** lỗi |

Khớp báo cáo. Nền 2060 + 28 (`MeloSettingsPolicyTest`, kế hoạch đòi ≥ 20) + 1 (coordinator). Số bỏ qua giữ 21. JVM không chứng minh giao diện thật.

## 6. APK candidate (CLAUDE.md §9) — đạt gate

| Trường | Giá trị (Opus tự kiểm) |
|---|---|
| Tệp (Ni) | `D:\Bots\_transfer\E102\MeloVault-E102-CAI-DAT-MOI-2f4d736-vc118-TinySigned-Debug.apk` |
| SHA-256 / bytes | `0F1DB45A…E143D414` / 33.445.828 — trùng báo cáo, trùng `base.apk` đang cài trên Shanling (`sha256sum` trên máy), trùng kích thước Drive |
| `aapt2 dump badging` | `app.melovault.player`, vc **118**, `0.6.93-beta`, `arm64-v8a` |
| `apksigner verify --print-certs` | `CN=Android Debug`, SHA-256 `eecd3df7…c3fce8` (như các bản trước) |
| Telegram config | true (chỉ boolean, theo log build Tiny; vc118 trên Shanling hiện thư viện Telegram 29.842 bài bình thường) |
| Drive | thư mục mới `MeloVault_QA/E102-CAI-DAT-MOI-20261026-vc118-2f4d736`, 14 PNG + APK + `SHA256.txt`, SHA-256 từng ảnh khớp khi chép; quyền **chỉ owner** (connector: thư mục, thư mục cha, APK, ảnh tổng hợp) |

Link: `E102-CAI-DAT-MOI-apk-test.md` (APK) và `E102-CAI-DAT-MOI-hinh-nghiem-thu.md` (ảnh).

## 7. QA thiết bị (xem ảnh thật, không chạy lại)

Opus xem `00_tong_hop.png` và mở thêm ảnh gốc `05c`. Chứng cứ đủ và không mâu thuẫn, nên không chạy lại QA.

| # | Kỳ vọng kế hoạch | Thấy trong ảnh | Kết quả |
|---|---|---|---|
| 1 | Popup mới đủ 6 phần, không còn Đăng xuất/Giao diện/Hiển thị nguồn, chữ không cắt | Thẻ "Thư viện đã đồng bộ · Cập nhật lúc 11:30 · 29.842 bài" + Lấy bài mới / Quét toàn bộ; Tin đã lưu, Nhạc trên máy, Nhóm/kênh/bot, Mở tệp nhạc, Cài đặt; nằm trong màn | ĐẠT |
| 2 | Thẻ tài khoản, 5 nhóm đúng thứ tự, chân trang `0.6.93-beta · vc118` | Đúng (02a, 02b, 05a có chân trang) | ĐẠT |
| 3 | Cũ hiện 3 kiểu, Mới ẩn, Back về trang chính | 3a có Portrait/Cockpit/Cổ điển; 3b không có; 03c là trang "Cài đặt" | ĐẠT |
| 4 | Gọn = Tự động, Nâng cao 6 dòng rồi bung đủ | 6 dòng đúng nhãn, "Gọn (máy nhỏ)"; ảnh 04c bung đủ | ĐẠT |
| 5 | Hộp đúng chữ cũ, Hủy thì vẫn đăng nhập | Hộp đúng chữ; 05c thư viện 29.842 bài, vẫn đăng nhập | ĐẠT |
| 6 | Sony: như 1–2, popup không tràn | Popup trong màn, Cài đặt đủ nhóm | ĐẠT |

Log: FATAL 0, ANR 0 trên cả 2 máy (theo báo cáo). Lựa chọn trên Shanling trả về giá trị ban đầu (Kiểu thư viện = Mới; ảnh 03b). Media3 không PLAYING; **CHƯA XÁC NHẬN ÂM THANH THỰC** (không liên quan nhiệm vụ). Màn hình: `SHANLING_SCREEN_END` OFF (Opus kiểm lại 11:52: `mGlobalDisplayState=OFF`, `mWakefulness=Asleep`); `SONY_SCREEN_END` OFF theo báo cáo.

## 8. Đối chiếu PASS/FAIL (kế hoạch mục 8, không nới)

| Tiêu chí | Kết quả |
|---|---|
| Test 0 lỗi, có test mới, build OK, diff sạch, đúng file | ĐẠT (Opus tự chạy) |
| Shanling 1–5, Sony 6 | ĐẠT (ảnh) |
| Lựa chọn cũ còn đủ, ghi đúng prefs | ĐẠT qua mã + test policy + bước 3 |
| Không crash/ANR | ĐẠT (log Sonnet) |
| Không sửa logic đồng bộ/TDLib/DB/Media3/service | ĐẠT (chỉ thêm getter) |
| Chủ dự án nói ĐẠT | **CHỜ** |

Không có điều kiện FAIL nào bị chạm.

## 9. Chưa kiểm chứng / giới hạn

- Đổi Cũ/Mới và Bento **có tác dụng khi quay lại thư viện**: chỉ kiểm bằng mã (`onResume`); trên Shanling (Gọn) lựa chọn này không đổi màn nên chưa thấy tận mắt. Cockpit/Portrait/Classic và Bento chưa đổi thử trên máy thật.
- Nút Tạm dừng/Tiếp tục khi đồng bộ thật; Lấy bài mới / Quét toàn bộ; đăng xuất thật (cấm); xoay màn giữ trang.
- Y700, S25, FYT, Car Mode, Tablet chưa kiểm.
- Âm thanh thật: không liên quan, không phát nhạc.

## 10. Ghi chú quy trình

- Sonnet tự báo đã ghi đè 2 tệp log tạm E101 trên Tiny (`C:\Bots\_qa_temp\e101_build.*`). Không phải APK/chứng cứ trong repo; kết quả E101 vẫn ở báo cáo và nghiệm thu E101. Không ảnh hưởng E102.
- 3 hàm cũ (`showThemePickerDialog`, `toggleThemePreset`, `showSourceDisplayModeDialog`) còn lại kèm comment rollback, đúng kế hoạch. Opus đề nghị giữ đến khi E102 đã dùng ổn một thời gian, rồi dọn trong một việc nhỏ riêng.

## 11. Việc tiếp theo

Chủ dự án xem ảnh (link trong `E102-CAI-DAT-MOI-hinh-nghiem-thu.md`) hoặc thử trực tiếp vc118 đang cài trên Shanling theo 5 bước "Bác sĩ tự kiểm tra" của kế hoạch, rồi nói **ĐẠT** hoặc báo lỗi. Khi ĐẠT: đổi trạng thái thành PASS, xin xác nhận thao tác merge rồi `git merge --no-ff` vào `feature/e93-03d-y700-offline-cockpit`.
