# Nghiệm thu — E100-THANH-DAY-CHUA-PHAT (Opus, phiên độc lập)

Ngày: 26/10/2026. Người nghiệm thu: Claude Opus (thay vai ChatGPT theo quyết định chủ dự án 25/10). Không sửa mã sản phẩm.

## 1. Git

| Mục | Giá trị |
|---|---|
| Nhánh | `dot/E100-THANH-DAY-CHUA-PHAT`, cây sạch lúc bắt đầu, không Gradle/agent khác đang chạy |
| Nền | `c606424` (= đầu `feature/e93-03d-y700-offline-cockpit`, trên merge E99 `6510d05`) |
| Commit | `90bf087` kế hoạch · `c7f6003` S1 · `59dd2ae` S2 · `c17fc6d` S3 · `8295598` S4 (vc116) · `a2df3e0` báo cáo |
| `git diff --check c606424..a2df3e0` | sạch |
| File đổi | đúng danh sách kế hoạch §4: `MeloResumePolicy.kt` (mới), `MeloBottomBarPolicy.kt`, `MeloBottomBar.kt`, `MeloScreenHost.kt`, `MeloCompactScreen.kt` (+1), `MeloPhoneScreen.kt` (+1), `TelegramPreviewActivity.kt`, `app/build.gradle`, 2 file test, 2 file docs. **Không** đụng `ShareablePlaybackService`, DB/schema, TDLib handler, `MeloRowArtLoader/Policy`, `MeloCarScreen`, `MeloTabletScreen`, CLAUDE.md, docs/workflow |
| Cỡ diff | production +304/−39 (kể cả chú thích; kế hoạch ước 170–220 dòng, không phải ngưỡng cứng), test +121 |

## 2. Soát mã (các điểm rủi ro kế hoạch nêu)

| Điểm | Kết luận | Bằng chứng |
|---|---|---|
| Prefs chỉ chứa số | ĐẠT | `MeloResumePolicy.kt`: 3 khóa `resume_song/pos/at_<acc>`; `TelegramPreviewActivity.rememberResume` chỉ `putLong`. Tên/kênh lấy lại từ DB (`getSongByKey`) trong `startResumeFlow` |
| Chỉ ghi khi đăng nhập + bài Telegram | ĐẠT | `shouldWrite(auth.isReady(), account>0, mediaId "telegram:")`; `local:`/`picked:audio` không ghi (unit test) |
| Xóa khi đăng xuất / đổi tài khoản | ĐẠT theo mã | `clearResume()` ở nhánh TDLib LoggingOut/Closing/Closed (Activity ~6420) và `logoutUser()` (~7336), đều **sau** `clearPlaybackSession()`; xóa cả RAM và toàn bộ prefs. Chưa kiểm thiết bị (cấm logout) |
| Không tự phát khi mở app | ĐẠT | `startResumeFlow` chỉ nạp `resumeSong` + `notifyMeloPlayback()`; không `prepare/play` |
| Vị trí chỉ khi file tải đủ | ĐẠT theo mã | `downloadAndPlay(song, resumeFromMs)`: chỉ nhánh file đầy đủ gọi `startPositionMs(..., fileComplete=true)`; `enqueueProgressivePlayback` không nhận vị trí. `startPlayback` dùng `C.TIME_UNSET` khi vị trí = 0 ⇒ mọi lời gọi cũ không đổi |
| Ghi đúng vị trí khi vừa nghe tiếp | ĐẠT | `onMediaItemTransition` ghi `lastStartPositionMs` khi lý do `PLAYLIST_CHANGED`, các lý do khác 0 |
| Bài đã bị xóa khỏi chỉ mục | ĐẠT | `row == null || isDeleted` ⇒ xóa khóa của account đó, thẻ về gợi ý |
| Không tải ảnh mới cho thẻ Nghe tiếp | ĐẠT | `resumeCard()` chỉ `thumbnailCache.get` / `peekRowArt` |
| Thanh đáy khi đang có bài | Không đổi | `update(now)` nhánh có bài giữ nguyên; vuốt lên chỉ khi `hasSong` |
| Host mặc định cho Car/Hai khung | ĐẠT | `MeloScreenHost`: `resumeCard()=null`, `canPlayAll()=false`, `playResume()=Unit` |

Không phát hiện lỗi chặn. Ghi chú nhỏ (không chặn): ô ảnh của thẻ gợi ý là chữ "C" (chữ đầu của "Chưa phát bài nào", do `artView` sẵn có) thay vì "♫" — kế hoạch để Sonnet chọn.

## 3. Test và build — Opus tự chạy lại trên Ni

`gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease --rerun-tasks --console=plain` (26/10 ~08:28–08:31): **BUILD SUCCESSFUL 3m20s**.

| Biến thể | tests | skipped | failures | errors |
|---|---|---|---|---|
| Debug (XML, 167 file) | **2055** | 21 | 0 | 0 |
| Release (XML, 167 file) | **2055** | 21 | 0 | 0 |

Khớp báo cáo Sonnet; tăng 13 so với mốc 2042 (9 `MeloResumePolicyTest` + 4 `MeloBottomBarPolicyTest`), bỏ qua giữ 21. APK Ni (Telegram ID 0) chỉ chứng minh biên dịch, không cài.

## 4. APK candidate (CLAUDE.md §9) — đạt gate

`MeloVault-E100-THANH-DAY-CHUA-PHAT-8295598-vc116-TinySigned-Debug.apk`: SHA-256 `F5E358CE…FEA19515`, 33.413.060 byte — **trùng** ở Tiny (`C:\Bots\_qa_handoff\E100-THANH-DAY-CHUA-PHAT\`), Ni (`D:\Bots\_transfer\E100\`), Drive và `base.apk` đang cài trên Shanling (`sha256sum` trên máy). `aapt2`: `app.melovault.player` vc116 / `0.6.91-beta`; `apksigner`: cert `eecd3df7…c3fce8`. Log build Tiny: bundle SHA-256 khớp, clone `C:\Bots\MeloVault-E100-IsolatedQA` @ `8295598` sạch, `telegram.config.valid=true`, bản sao `local.properties` đã xóa khỏi clone (gốc không đổi). vc116 > vc115, không hạ phiên bản.
Drive owner-only, thư mục mới `MeloVault_QA/E100-THANH-DAY-CHUA-PHAT-20261026-vc116-8295598`: **`E100-THANH-DAY-CHUA-PHAT-apk-test.md`**; ảnh: **`E100-THANH-DAY-CHUA-PHAT-hinh-nghiem-thu.md`**. Quyền (connector): APK, thư mục và thư mục cha chỉ có owner. Nhãn **QA CANDIDATE — CHƯA NGHIỆM THU**.
[CẦN XÁC NHẬN] kế hoạch §11: versionName `0.6.91` cho vc116 — Opus chấp nhận (nhiệm vụ mới tăng số thứ ba, như E98→E99).

## 5. QA thiết bị — đối chiếu ảnh

Opus xem `00_tong_hop.png` (8 ô) và ảnh Sony `12`; từng ô khớp bảng báo cáo §6–7.

| # | Thiết bị | Kỳ vọng kế hoạch §7 | Thấy trong ảnh / log báo cáo | Kết quả |
|---|---|---|---|---|
| 1 | Shanling | Thẻ gợi ý, không trống, 8 dòng, đáy 102 dp, không tự phát | "Chưa phát bài nào / Bấm để phát danh sách đang xem", ▶ sáng, 8 dòng; uiautomator đáy 204 px = 102 dp; Media3 state 0 | ĐẠT |
| 2 | Shanling | Màn nguồn: "Chọn một bài…", bấm không làm gì; Thư viện lại gợi ý | Đúng chữ, ▶ mờ; state 0 sau bấm; về Thư viện đúng | ĐẠT |
| 3 | Shanling | Bấm gợi ý = phát bài đầu | "Nonstop – Lào Cai Phố Vol 3" (dòng 1) đang phát, thẻ kiểu Đang phát | ĐẠT |
| 4 | Shanling | Bài đã tải, dừng ≈ 30 s | PAUSED, 34,5 s | ĐẠT |
| 5 | Shanling | Mở lạnh: "Nghe tiếp · kênh", không tự phát 10 s | "LOW 2026 – Thiện Matthew / Nghe tiếp · NHÓM TRACK DJ (EMI)"; state 0 | ĐẠT |
| 6 | Shanling | ▶ phát tiếp ≥ 25 s | PLAYING, 34,6 s lần đọc đầu, đúng bài; không FATAL/ANR | ĐẠT |
| 8 | Shanling (tùy chọn) | Bài chưa tải → từ đầu | Bài tải xong trong ~20 s nên tiếp từ vị trí (đúng thiết kế cho file đủ) | **CHƯA KIỂM nhánh progressive** |
| S1–S4 | Sony (hồi quy) | Gợi ý; Nghe tiếp sau mở lạnh | Ảnh 09: gợi ý; ảnh 12: "# Mưa Bay Bay.mp3 / Nghe tiếp · SƯU TẦM NHẠC BAY", ▶, không PLAYING | ĐẠT phần hiển thị (bấm ▶ Nghe tiếp trên Sony chưa kiểm) |

Màn hình: `SHANLING_SCREEN_END=OFF`, `SONY_SCREEN_END=OFF` theo báo cáo; Opus kiểm Shanling qua ADB chỉ bằng `dumpsys`/`sha256sum`, không đánh thức màn. Opus không chạy lại QA thiết bị (chứng cứ đủ, không mâu thuẫn).

## 6. Bảng tiêu chí PASS (kế hoạch §8)

| Tiêu chí | Trạng thái |
|---|---|
| Test 0 lỗi, ≥ 2042 + mới, bỏ qua 21; build OK; diff-check sạch; đúng file | ĐẠT (Opus tự chạy) |
| Shanling bước 1–6 | ĐẠT |
| Khi đang có bài: thẻ mở Đang phát, ▶ Phát/Dừng như vc115 | ĐẠT theo mã + ảnh 04/07 (thẻ kiểu Đang phát, nút ⏸) |
| Prefs chỉ số; xóa ở 2 đường đăng xuất; unit test theo account | ĐẠT theo mã + test |
| Chủ dự án xem ảnh / dùng vc116 và nói **ĐẠT** | **ĐẠT** (26/10, xem mục 9) |

## 7. Chưa kiểm chứng (không chặn theo kế hoạch, cần chủ dự án biết)

- **CHƯA XÁC NHẬN ÂM THANH THỰC** (Media3 PLAYING + position tăng chỉ chứng minh luồng phát).
- Xóa bản ghi khi đăng xuất / đổi tài khoản: chỉ mã + unit test, **chưa kiểm thiết bị**.
- Bài **chưa tải** nghe tiếp phải phát từ đầu: chỉ mã + unit test (`startPositionMs(fileComplete=false)=0`).
- Bài kế khi nghe tiếp đi theo danh sách đang xem: chưa kiểm riêng trên máy.
- Activity bị hủy khi đang phát nền: bài kế từ màn khóa không được ghi (giới hạn đã nêu trong kế hoạch).
- Ảnh bìa thẻ Nghe tiếp khi mở lạnh là ô chữ cái (chỉ đọc cache) — đúng thiết kế.
- Tablet dọc, Hai khung, Y700/S25/FYT/Car Mode chưa kiểm trên máy; Janky chưa đo.

## 8. Kết luận vòng kỹ thuật (trước phản hồi chủ dự án — đã thay bằng mục 9: **PASS**)

**BLOCKED — CHỜ CHỦ DỰ ÁN.** Mã đúng phạm vi, test/build Opus tự chạy xanh, APK đạt gate và đã lên Drive owner-only, QA Shanling thật đạt bước 1–6 và Sony hồi quy đạt phần hiển thị. Còn chờ chủ dự án xem ảnh (hoặc dùng vc116 trên Shanling, đã cài sẵn) theo 5 bước "Bác sĩ tự kiểm tra" của kế hoạch, rồi nói **ĐẠT** hoặc báo lỗi. Khi ĐẠT: đổi thành PASS, xin xác nhận rồi `git merge --no-ff` vào `feature/e93-03d-y700-offline-cockpit`.

## 9. Phản hồi chủ dự án và kết luận cuối

**Phản hồi chủ dự án (26/10/2026):** "ĐẠT. Đổi nghiệm thu E100 thành PASS và chuẩn bị merge."

**Kết luận cuối: PASS** theo phạm vi đã kiểm (mục 5–6). APK đã duyệt: vc116 / `0.6.91-beta` @ `8295598`, SHA-256 `F5E358CE…FEA19515`. Các giới hạn ở mục 7 vẫn giữ nguyên (âm thanh thật, xóa khi đăng xuất trên máy, nhánh bài chưa tải, Tablet/Hai khung/Y700/S25/FYT/Car Mode) — không đảo kết luận, có thể tách kiểm riêng khi cần.
Bước kế: chủ dự án xác nhận thao tác rồi mới `git merge --no-ff dot/E100-THANH-DAY-CHUA-PHAT` vào `feature/e93-03d-y700-offline-cockpit` (không force, không push).
