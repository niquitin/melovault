# Báo cáo thực thi — 2026-10-30-don-ui-sau-xoay

Claude Code (Opus 5.5), một phiên, 30/10/2026 17:05–18:10 +07. Thiết bị: Ni (repo, Gradle, bundle), Tiny DESKTOP-HNP4GVR (build cô lập + Sony). Trạng thái: **QA CANDIDATE — CHƯA NGHIỆM THU**.

**Quyết định chủ dự án 30/10/2026 17:01:** bỏ QA1 tablet và QA6 Shanling M3; mọi QA thiết bị chỉ trên Sony XQ-AS52 `QV725WEQ54` qua Tiny. Tablet và Shanling: **ngoài phạm vi theo quyết định chủ dự án** (không phải BLOCKED).

## 1. Kết quả chính

| Mục | Kết quả | Bằng chứng |
|---|---|---|
| D1 log `MeloAnchor` không ra Release | **Đạt** | Release dex 0 chuỗi `MeloAnchor` (Debug 1); Sony chạy Release: logcat đầy đủ 17:59:36→hết lượt (22.465 dòng, có lần khởi động tiến trình) **0 dòng** `MeloAnchor` |
| D2 lưu vị trí ngay sau mở app | **Đạt** | Test JVM đỏ trên luật cũ (16 chạy, 1 hỏng) → xanh (16/16). Sony mở nguội + 11 lần vuốt từ giây 2,5: **0 `save_rejected activeOk=false`** (R1c: 7); xoay ×3 cùng bài |
| D3 hết khoảng trống header sau xoay | **Đạt** (chỉ bằng chứng thiết bị) | Hàng neo y=685 trước và sau mọi lần xoay (R1c: 685 → 1124); ô tìm y=79 (R1c: 266); Teal y=559 → 559 |
| Test Ni (S4) | Debug **2268 passed / 21 skipped / 0 failed**; Release giống hệt | 179 suite mỗi biến thể, 2289 = 2287 (mốc Đảo) + 2 test mới; assemble Debug+Release BUILD SUCCESSFUL; `git diff --check` sạch |
| QA Sony Q1–Q6 | **Đạt cả 6** (Q4 có ghi chú cách so) | Mục 4 |
| Drive owner-only | Xong | [apk-test](2026-10-30-don-ui-sau-xoay-apk-test.md) · [hình](2026-10-30-don-ui-sau-xoay-hinh-nghiem-thu.md) |

## 2. Commit

| Bước | Commit | Nội dung |
|---|---|---|
| S0 | `c5339af` | Kế hoạch + nguyên nhân D1–D3 + file bàn giao |
| S1 D1 | `42f6ee3` | `anchorLog` inline chỉ ghi khi `BuildConfig.DEBUG` (10 dòng pane) + chặn dòng `save_rejected` ở host |
| S2 D2 | `36c042b` | `MeloBrowseState.savePosition`: vẫn từ chối hàng của truy vấn cũ; nếu key đang hiện khác key đang nhớ (đổi phạm vi không qua làm mới) thì kích hoạt key đang hiện (cùng query/sort như `rememberBrowseContext`) rồi lưu. +2 test |
| S3 D3 | `8bf9d03` | Đổi gập header/thẻ nguồn khi `list.isInLayout` thì `post` sau lượt layout; ngoài layout giữ như cũ |

Production `805a800..8bf9d03`: 3 file, +48/−19 (`MeloBrowseState.kt` +13, `MeloLibraryPane.kt`, `TelegramPreviewActivity.kt`). Test: `MeloBrowseStateTest.kt` +35. Không đổi schema, songKey, TDLib, playback/service, thứ tự Đảo, Playlist/E105, package/signer/versionCode, build config; không đụng `scanAllTelegramAudio`.

## 3. Nguyên nhân (đã kiểm bằng mã + log/XML)

- **D1:** 11 lệnh `Log.w("MeloAnchor")` không điều kiện; Release `minifyEnabled false` nên không bị lược.
- **D2:** `allChatsMode` mặc định `false`. Sau đăng nhập, `loadDatabaseIndex` → `refreshSongRows()` kích hoạt key phạm vi lúc đó, rồi `scanAllTelegramAudio()` đặt `allChatsMode = true` **không làm mới** → `browseKey()` đổi sang Thư viện, generation giữ nguyên → mọi lần lưu bị từ chối tới lần làm mới kế.
- **D3:** XML ô 3 R1c: `headerBox` (187 px) và `sourceCardsHolder` (252 px) đã GONE nhưng vẫn giữ chỗ. Sau xoay, `setSelectionFromTop` khiến ListView gọi `onScroll` ngay trong lượt layout; việc đặt GONE ở đó không được bố cục lại. Không phải inset hay offset neo.

## 4. QA Sony `QV725WEQ54` (XQ-AS52, Sony, Android 12) — APK Debug `E6EC8773…E92C`

| # | Thao tác | Kỳ vọng | Thực tế | KQ | Bằng chứng (Tiny `C:\Bots\_qa_temp\don-ui-8bf9d03-20261030\`) |
|---|---|---|---|---|---|
| Q1 | Cài lại (mở nguội) → mở app → vuốt 8 lần từ 2,5 s sau mở (không tìm/đổi nguồn) → xoay ngang/dọc ×3 | Cùng bài đầu; không `save_rejected` sau khi danh sách đầu hiện | Cuộn tới hàng 42; 3/3 vòng cùng bài (ngang+dọc), ΔY 0, ô tìm y=79; log: 0 `save_rejected`, 6 `settled`, 0 `gave_up`; player không đổi | **ĐẠT** | `q1b-current-*`, `q1b-current-meloanchor.log` |
| Q2 | Thư viện, gõ "an", cuộn, xoay ×3 | Hàng neo lệch ≤ 1 hàng (189 px) | 3/3 cùng bài, **ΔY 0**, ô tìm y=79, query "an" giữ; 0 `save_rejected` | **ĐẠT** | `q2-current-*` |
| Q3a | Mở Deck → Back → xoay ngang → dọc | Deck không mở lại; danh sách giữ | Sau Back và cả hai lần xoay: không có nút "Đóng màn Đang phát", có ô tìm; hàng đầu trước/sau giống | **ĐẠT** | `q3-01…04` |
| Q3b | Deck: Phát → nút **Dừng** → Bài kế | Có "Tạm dừng" khi dừng; sau Bài kế không còn | Dừng: state 2, nhãn có; Bài kế: bài khác, state 3, **không nhãn** ở +3 s và +15 s | **ĐẠT** | `q3b-01…03`, `q3b-result.json` |
| Q4 | Deck: bật Đảo → đọc "Thứ tự phát · Đảo bật" → ⏭ ×2 | Phát đúng hàng 2 rồi 3 | Hàng 1 = bài đang phát; sau ⏭ #1 Deck hiện đúng tên hàng 2, sau ⏭ #2 đúng hàng 3. Đảo trả về "tắt" | **ĐẠT** (ghi chú) | `q4-01…03` |
| Q5 | Giao diện Teal sáng: lặp Q1 (mở nguội) + Q2 | Như Q1/Q2 | Cả hai: 3/3 cùng bài, ΔY 0 (y=559), ô tìm 79, "an" giữ; 0 `save_rejected activeOk=false` (1 dòng `genOk=false`: hàng của generation cũ lưu muộn, từ chối đúng luật) | **ĐẠT** | `q5a-teal-*`, `q5b-teal-*` |
| Q6 | Cài **Release** (cùng signer, vc121) → mở nguội, cuộn, xoay ×3 | Không có `MeloAnchor` | 0 dòng; đối chứng bộ đệm đầy đủ có tiến trình Release khởi động 17:59:39; xoay ×3 vẫn cùng bài ΔY 0. Sau đó cài lại Debug candidate | **ĐẠT** | `q6-release-*`, `q6-control-all.log` |

- Ảnh tổng hợp `00_tong_hop.png` (12 ô, 1560×1232) trên Drive.
- **SONY_SCREEN_END=OFF** 18:02:57 (`Display State=OFF`). Cuối phiên: giao diện trả về "Hiện tại" như trước QA, Đảo "tắt", nhạc tạm dừng; Sony chạy APK Debug candidate (SHA đối chiếu sau cài khớp).
- Âm thanh: Media3 PLAYING ≠ âm thanh thật → **CHƯA XÁC NHẬN ÂM THANH THỰC**.

## 5. Build / bàn giao

| Mục | Giá trị |
|---|---|
| Bundle | 13.187.813 byte, SHA-256 `7C8681E679284CED53B1094F60F242FB1148D56830F85ACE0A9E68FC1B446837`, `git bundle verify` ok; chuyển qua Drive `_transfer_don-ui-8bf9d03` (owner-only), Tiny đối chiếu khớp |
| Clone Tiny | `C:\Bots\MeloVault-DONUI-IsolatedQA-20261030-8bf9d03`, HEAD `8bf9d03`, cây sạch; `local.properties` chép từ clone Đảo trên Tiny (không in, không về Ni) |
| Build Tiny | `reportTelegramConfig` = true; Debug+Release BUILD SUCCESSFUL 4m43s |
| APK Debug | Chi tiết [apk-test](2026-10-30-don-ui-sau-xoay-apk-test.md) |

## 6. Chưa kiểm chứng / giới hạn

- D3 không có test JVM (cần lượt layout View thật; dự án không có Robolectric, không được đổi build config) → chỉ bằng chứng thiết bị.
- D2 trên máy chứng minh gián tiếp: lần lưu thành công không ghi log; bằng chứng là 0 `save_rejected` khi cuộn ngay sau mở (R1c: 7) + test JVM tái hiện đúng chuỗi sự kiện.
- Q4: phép so tự động qua `description` của MediaSession không khớp tiền tố tên; kết luận dựa trên tiêu đề "Đang phát" hiển thị trên Deck (ảnh `09_…`).
- Âm thanh thật; tải APK bằng trình duyệt (cảnh báo 403 của Drive) chưa thử — đã thử tải lại qua Drive for desktop trên Ni, SHA khớp.
- Tablet, Shanling: ngoài phạm vi theo quyết định chủ dự án.

## 7. Sự cố trong phiên (minh bạch)

1. Lần đầu chép bundle lên Drive, PowerShell tạo nhầm một **file** tên thư mục (thiếu `-Path`); sửa ngay trong phút đó (đổi tên/chuyển vào đúng thư mục, SHA khớp), không còn file thừa.
2. Q1 lượt đầu (`q1-current-*`): xoay ×3 đạt nhưng log mở app bị trôi khỏi bộ đệm logcat (mốc `-T` bị adb tách) → làm lại Q1b với logcat dạng luồng sau một lần `install -r` cùng APK.
3. Q3 lượt đầu BLOCKED do script chạm theo cây UI chụp lúc bố cục đang đổi (chạm trúng tiêu đề, không phát) — ảnh chẩn đoán cho thấy màn bình thường; sửa script chờ vị trí ổn định rồi chạy lại.
4. Hai lần `install -r` thành công nhưng bước kéo APK về đối chiếu bị ADB rớt giữa chừng (đã gặp ở R1c); chạy lại riêng bước đối chiếu, SHA khớp candidate.
5. Thư mục Drive `MeloVault_QA\_transfer_don-ui-8bf9d03` (bundle) để lại, owner-only; chủ dự án dọn nếu muốn.
6. Không uninstall/clear data/logout/xóa cache/TDLib; không đổi timeout/brightness; không merge/push.
