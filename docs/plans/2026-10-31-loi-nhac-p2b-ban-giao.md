# Bàn giao — lời nhạc Shareable (P2-B)

TASK_ID: **2026-10-31-loi-nhac-p2b**. Worktree `D:\Bots\_worktrees\MeloVault-loi-nhac-p2b`; nhánh `dot/2026-10-31-loi-nhac-p2b`; baseline `94ab6b0`.

## Checkpoint lập kế hoạch — 31/10/2026, Claude Code

- Kế hoạch: [`2026-10-31-loi-nhac-p2b.md`](2026-10-31-loi-nhac-p2b.md). Commit đầu `118c5e9`; HEAD dự kiến khi bắt đầu thực thi là commit chứa file bàn giao này.
- Đã làm:
  - đọc phụ lục P2-B;
  - đọc mã lyrics ở `main` (LrcParser/LyricsModels là mã thuần; LyricsRepository gắn với CarApiClient);
  - đọc tài liệu API LRCLIB `lrclib.net/docs.md`.
- Không code, không build, không dùng ADB/Drive. Không có tiến trình nào còn chạy.
- Chặng: **A** = L0–L4 (lõi + test, Ni); **B** = L5–L6 (UI + gate Ni); **C** = L7–L8 (Tiny + QA Sony + hồ sơ).
- **Chờ chủ dự án:** 6 câu ở kế hoạch mục 9. Chủ dự án không trả lời khác thì dùng mặc định. Riêng câu 3 (URL/email liên hệ) để trống thì User-Agent chỉ ghi `MeloVault/<ver>`.
- Không merge/push. Trạng thái: **KẾ HOẠCH — chờ chủ dự án giao thực thi.**

## Prompt phiên thực thi — chặng A (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-loi-nhac-p2b (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-loi-nhac-p2b
Nhánh: dot/2026-10-31-loi-nhac-p2b
Kế hoạch đã chốt: docs/plans/2026-10-31-loi-nhac-p2b.md
Bàn giao: docs/plans/2026-10-31-loi-nhac-p2b-ban-giao.md; HEAD dự kiến: commit cuối của nhánh (chứa file bàn giao)
Baseline: 94ab6b0 (nhánh tích hợp feature/e93-03d-y700-offline-cockpit)
PHIÊN: 1 = chặng A, lõi + test Ni, bước L0–L4
Lưu ý riêng:
- Theo đúng LD1–LD10 và mục 4; câu nào ở mục 9 chủ dự án đã trả lời khác (ghi trong bàn giao) thì theo câu trả lời đó.
- Chỉ dùng lại LrcParser/LyricsLine ở app/src/main/.../lyrics; KHÔNG import LyricsRepository/LyricsCache/CarApiClient.
- Không thêm thư viện HTTP; HttpURLConnection sau interface LyricsTransport để test bằng transport giả. Chặng A KHÔNG gọi mạng thật.
- Request chỉ gồm track_name/artist_name/album_name/duration + User-Agent; test phải chứng minh không có ID Telegram/songKey/đường dẫn.
- Cache ở noBackupFilesDir/melo_lyrics_v1/<ownerId>/, không đụng TDLib DB/cache.
- Hết L4: dừng, cập nhật bàn giao, đưa prompt chặng B (L5–L6). Không làm UI trong chặng A.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, file bàn giao và kế hoạch trên. Kiểm git status, branch, HEAD, baseline, diff --check, agent đang chạy. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP, không reset/clean/stash/đè.
2. Làm L1 → L2 → L3 → L4 đúng file/hàm trong kế hoạch, commit theo mốc. Test mới: MeloLyricsPolicyTest (≥ 20 ca), ShareableLyricsProviderTest, ShareableLyricsCacheTest, ShareableLyricsControllerTest. Chạy test mục tiêu Debug + Release, rồi full :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest; log ra file dưới docs/plans/artifacts/2026-10-31-loi-nhac-p2b/A-<ts>/, chỉ ghi tổng kết + cộng XML (passed/skipped/failed/errors), git diff --check. Sau mỗi mốc cập nhật docs/plans/2026-10-31-loi-nhac-p2b-bao-cao.md (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng A") và file bàn giao.
3. Không ADB/thiết bị trong chặng A; ghi SONY_SCREEN_END = KHÔNG THAO TÁC.
4. Commit trên dot/2026-10-31-loi-nhac-p2b. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng B đã điền HEAD thật.
```

## Checkpoint chặng A — 01/11/2026, Claude Code (Sonnet 5.5)

- Trạng thái: **CHẶNG A XONG (L0–L4). Chờ phiên thực thi chặng B (L5–L6).** Chưa nghiệm thu; chưa có APK.
- Commit mã `6f274b9` (8 file mới: 4 mã + 4 test). Báo cáo: [`-bao-cao.md`](2026-10-31-loi-nhac-p2b-bao-cao.md). HEAD khi bắt đầu chặng B = commit cuối của nhánh (chứa file này).
- Test Debug + Release: 2393 test mỗi biến thể, 21 skipped, 0 lỗi (83 test mới); `git diff --check` sạch.
- Mục 9 của kế hoạch: chủ dự án chưa trả lời khác → dùng mặc định cả 6 câu (User-Agent không có liên hệ).
- Hợp đồng chặng A để chặng B dùng (tất cả `internal`, package `com.bsdung.musicassistantcar.data.telegram`):
  - `ShareableLyricsController(cache, provider, executor, post, consent, nowMs, log)`; `request(ownerId, Meta, publish)`, `cancel()`, `searchManual(...)`, `choose(...)`, `clearChoice(...)`, `clearOwner(ownerId)`.
  - `LyricsState`: Off, NeedConsent, MissingMetadata, Loading, Synced(lines, fromCache, manual), Plain, Instrumental, NoMatch(canSearch), Ambiguous(candidates), TemporaryError(retryAfterMs).
  - `LyricsConsent` { UNDECIDED, GRANTED, DENIED } do chặng B đọc từ SharedPreferences (mặc định UNDECIDED; “Không” → DENIED).
  - `ShareableLyricsCache(root = File(noBackupFilesDir, "melo_lyrics_v1"))`; `ShareableLyricsProvider(HttpUrlConnectionLyricsTransport(), MeloLyricsPolicy.userAgent(versionName, null), log = { Log.w("MeloVaultLyrics", it) })`.
  - Sony ẩn log mức I của app: dùng mức **W** cho log `stage=lyrics_*` (bài học b100).
- Chưa làm: UI, công tắc Cài đặt, hộp đồng ý, nối host, xóa cache khi đăng xuất, mọi build/QA. Không có tiến trình nào còn chạy; không dùng thiết bị.

## Prompt phiên thực thi — chặng B (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÃ KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-loi-nhac-p2b (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-loi-nhac-p2b
Nhánh: dot/2026-10-31-loi-nhac-p2b
Kế hoạch đã chốt: docs/plans/2026-10-31-loi-nhac-p2b.md
Báo cáo chặng A: docs/plans/2026-10-31-loi-nhac-p2b-bao-cao.md
Bàn giao: docs/plans/2026-10-31-loi-nhac-p2b-ban-giao.md; HEAD dự kiến: commit cuối của nhánh (chứa checkpoint chặng A); commit mã chặng A: 6f274b9
Baseline: 94ab6b0
PHIÊN: 2 = chặng B, UI + gate Ni, bước L5–L6
Lưu ý riêng:
- Dùng đúng lõi chặng A (mục "Hợp đồng chặng A" trong bàn giao); KHÔNG sửa logic lõi trừ khi test chặng A chứng minh sai, và khi đó ghi rõ trong báo cáo.
- Android Views, không Compose. Tab "Tiếp theo | Lời nhạc" trong MeloStudioDeck và khung trái MeloTabletScreen; tab mặc định vẫn Tiếp theo, nhớ tab qua xoay theo cơ chế S2. Không thêm tab ở Gọn/Car.
- Hộp đồng ý LD2 (chữ đúng theo kế hoạch) + công tắc "Lời nhạc từ LRCLIB" trong Cài đặt (MeloSettingsPolicy), SharedPreferences, mặc định chưa quyết định.
- Ô tìm thủ công LD10 có xử lý IME; nút ≥ 48 dp; dòng nguồn "Lời: LRCLIB"; kiểm chữ lớn 1.3 bằng test policy.
- TelegramPreviewActivity: truyền title/performer/album/duration bài đang phát từ dữ liệu đã có; đăng xuất → controller.clearOwner(owner). KHÔNG đọc tệp nhạc, KHÔNG gọi TDLib thêm, KHÔNG đổi service/Media3/DB/package/signer/versionCode.
- Lỗi lời nhạc không được đổi player/queue.
- Gate L6 (log ra file, chỉ đọc tổng kết + cộng XML): test Debug+Release, assembleShareableDebug+Release, -PmeloOpen=true assembleShareableDebug, assemblePersonalDebug, git diff --check. Không đọc/in local.properties.
- Hết L6: dừng, cập nhật báo cáo + bàn giao, đưa prompt chặng C (Tiny build + QA Sony). Không ADB/thiết bị trong chặng B; ghi SONY_SCREEN_END = KHÔNG THAO TÁC.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, file bàn giao, báo cáo và kế hoạch trên. Kiểm git status, branch, HEAD, baseline, diff --check, agent đang chạy. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP, không reset/clean/stash/đè.
2. Làm L5 → L6, commit theo mốc trên dot/2026-10-31-loi-nhac-p2b. Sau mỗi mốc cập nhật báo cáo (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng B") và file bàn giao.
3. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm prompt chặng C đã điền HEAD thật.
```

## Checkpoint chặng B — 01/11/2026, Claude Code (Sonnet 5.5)

- Trạng thái: **CHẶNG B XONG (L5–L6). Chờ phiên thực thi chặng C (L7–L8: Tiny build + QA Sony + hồ sơ).** Chưa nghiệm thu; chưa có APK đủ điều kiện cài.
- Commit mã chặng A `6f274b9`; commit mã chặng B `3447310`. Báo cáo: [`-bao-cao.md`](2026-10-31-loi-nhac-p2b-bao-cao.md) mục 9. HEAD khi bắt đầu chặng C = commit cuối của nhánh (chứa file này).
- Gate Ni: test Debug + Release mỗi biến thể 2412 test, 21 skipped, 0 lỗi; assemble Shareable Debug/Release, open Debug, Personal Debug đều xanh; `git diff --check` sạch.
- Làm theo bản đã chốt: LRCLIB trực tiếp, tắt sẵn + hỏi đồng ý, không liên hệ trong User-Agent, mixset 15 phút, có trong bản open (tắt sẵn), bỏ qua lời nhúng/`.lrc`.
- **Điểm lệch cần chặng C biết:** ở Hai khung, tab Lời nhạc nằm trong panel “Hàng đợi” ở cột phải (bấm “☰ Hàng đợi” rồi chọn “♪ Lời nhạc”), không ở khung trái. Studio Deck (dọc và ngang) có thanh tab đúng như kế hoạch. Xem báo cáo mục 9.3.
- **Phần nhìn thấy được CHƯA TỪNG CHẠY trên máy.** Chặng C là lần đầu thấy giao diện: nếu bố cục lỗi (cuộn lồng nhau ở Deck dọc, bàn phím trong Dialog, Hai khung thấp ~360 dp) thì ghi FAIL kèm ảnh, KHÔNG tự sửa bố cục ngoài phạm vi nhỏ; báo để chủ dự án giao REWORK.
- APK build ở Ni không có Telegram config: **không cài**. Chặng C phải build ở Tiny.
- Không merge/push/public. Không dùng thiết bị trong chặng B.

## Prompt phiên thực thi — chặng C (dán nguyên văn)

```text
MELOVAULT — PHIÊN THỰC THI ĐÃ KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-loi-nhac-p2b (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG làm trong thư mục chính)
TASK_ID: 2026-10-31-loi-nhac-p2b
Nhánh: dot/2026-10-31-loi-nhac-p2b
Kế hoạch đã chốt: docs/plans/2026-10-31-loi-nhac-p2b.md (mục 5 L7–L8, mục 6 QA, mục 7 tiêu chí)
Báo cáo: docs/plans/2026-10-31-loi-nhac-p2b-bao-cao.md (mục 9 là chặng B)
Bàn giao: docs/plans/2026-10-31-loi-nhac-p2b-ban-giao.md; HEAD dự kiến: commit cuối của nhánh (chứa checkpoint chặng B); commit mã: 6f274b9 (chặng A), 3447310 (chặng B)
Baseline: 94ab6b0
PHIÊN: 3 = chặng C, Tiny build + QA Sony + hồ sơ, bước L7–L8
Lưu ý riêng:
- KHÔNG sửa mã trong chặng này. Nếu QA lộ lỗi, ghi FAIL kèm ảnh/log/bước tái hiện và dừng; chủ dự án giao REWORK ở phiên thực thi mới.
- Thiết bị QA thật duy nhất: Sony XQ-AS52 serial QV725WEQ54 qua Tiny (Shanling đã bán 31/10). Kiểm adb devices -l + getprop mỗi phiên. Chỉ adb install -r Debug thường (bản đang có trên máy, cùng chữ ký); tuyệt đối không uninstall/pm clear/xóa dữ liệu, không logout, không máy bay/tắt mạng.
- L7: bundle Git + SHA-256 hai đầu → clone cô lập trên Tiny → copy local.properties theo ngoại lệ E104 S11 (không đọc/in/hash) → reportTelegramConfig (KHÔNG có -PmeloOpen) → build Debug thường → gate package app.melovault.player / versionCode 121 / chứng chỉ eecd3df7…fce8 / SHA-256 / bytes.
- QA theo mục 6 kế hoạch (Q1–Q6) với MỘT thay đổi: ở Q6 và mọi chỗ nói "Hai khung", tab Lời nhạc nằm trong panel "☰ Hàng đợi" ở cột phải (xem bàn giao). Cần thêm: Q7 công tắc "Lời nhạc từ LRCLIB" trong Cài đặt bật/tắt và tab phản ứng đúng; Q8 xoay dọc↔ngang giữ tab. Agent được gõ từ khóa tìm lời. Log lời nhạc: tag MeloVaultLyrics mức W, chỉ có stage + mã HTTP; kiểm không có tên bài trong logcat. Bắt đầu từ trạng thái chưa đồng ý (UNDECIDED); nếu máy đã có lựa chọn cũ trong prefs thì ghi rõ, không xóa prefs.
- Lỗi mạng/429/hết hạn cache chỉ có ở test JVM. Âm thanh thật: CHƯA XÁC NHẬN.
- Màn hình Sony theo CLAUDE.md mục 7: chỉ bật khi cần, tắt trong finally, xác minh Display State=OFF; ghi SONY_SCREEN_END.
- L8: -bao-cao.md (mục chặng C), -apk-test.md, -hinh-nghiem-thu.md, 00_tong_hop.png (≤ 1600 px, 6–8 ảnh), upload APK + ảnh vào Drive Ni N owner-only nhãn "QA CANDIDATE — CHƯA NGHIỆM THU", gửi link đã kiểm; Drive chặn tải thì ghi BLOCKED đúng lỗi. Hồ sơ sơ bộ: đưa prompt cho phiên nghiệm thu sơ bộ MỚI (PROMPT_NGHIEM_THU_SO_BO.md).

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, file bàn giao, báo cáo và kế hoạch trên. Kiểm git status, branch, HEAD, baseline, diff --check, agent đang chạy. Cây bẩn không rõ nguồn hoặc HEAD lạ → STOP, không reset/clean/stash/đè.
2. Làm L7 → L8. Sau mỗi mốc cập nhật báo cáo (mở đầu "Thực thi: <Claude Code/Codex> <model>, phiên chặng C") và file bàn giao.
3. Không merge/push/public, không ghi PASS, không chọn việc khác.

Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh." kèm prompt sơ bộ đã điền HEAD thật.
```

## Checkpoint chặng C — 01/11/2026, Claude Code (Sonnet 5.5)

- Trạng thái: **CHẶNG C XONG (L7–L8). Chờ phiên nghiệm thu sơ bộ.** Trạng thái chính thức: **CHỜ CHỦ DỰ ÁN NGHIỆM THU (QA CANDIDATE — CHƯA NGHIỆM THU), có 1 lỗi đã biết B1**.
- Mã: A `6f274b9`, B `3447310`; APK build từ HEAD `8d4284e` (source không đổi sau `3447310`, chỉ hồ sơ). Báo cáo [`-bao-cao.md`](2026-10-31-loi-nhac-p2b-bao-cao.md) mục 10; APK [`-apk-test.md`](2026-10-31-loi-nhac-p2b-apk-test.md); ảnh [`-hinh-nghiem-thu.md`](2026-10-31-loi-nhac-p2b-hinh-nghiem-thu.md).
- Kết quả QA Sony: Q1 PASS, Q2 PASS có giới hạn, Q3 PASS, Q4 PASS, Q5 PASS có giới hạn, Q6 PASS (Hai khung, panel Hàng đợi), Q7 PASS, **Q8 FAIL**.
- **Lỗi B1:** xoay màn hình khi đang ở tab Lời nhạc của Deck → pane kẹt “Đang tìm lời…” (log `stage=lyrics_drop`). Nguyên nhân khả dĩ: `setShown(false)` của pane Deck cũ gọi `controller.cancel()` sau khi pane Deck mới đã `request`. Chưa sửa (chặng C cấm sửa mã). Nên giao REWORK trước khi nghiệm thu chính thức nếu chủ dự án muốn xoay màn hình hoạt động.
- Sony: màn hình OFF, xoay 1/0, 1080×2520/420, font 1.0. Bài hiện tại “Total Immersion” (nhạc trên máy) tạm dừng 0:02; lời nhạc đang ở trạng thái **tắt**; cache có 1 lựa chọn thủ công (mixset #3) và 1 “không có lời”.
- Không merge/push/public.

## Prompt phiên nghiệm thu sơ bộ (dán nguyên văn)

```text
MELOVAULT — PHIÊN NGHIỆM THU SƠ BỘ NHANH

Repo/worktree: D:\Bots\_worktrees\MeloVault-loi-nhac-p2b (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG sửa mã, KHÔNG build APK mới)
TASK_ID: 2026-10-31-loi-nhac-p2b
Nhánh: dot/2026-10-31-loi-nhac-p2b; baseline 94ab6b0; HEAD dự kiến: commit cuối của nhánh (chứa checkpoint chặng C); mã A 6f274b9, mã B 3447310; APK build từ 8d4284e
Hồ sơ: docs/plans/2026-10-31-loi-nhac-p2b.md (kế hoạch), -bao-cao.md (mục 9 chặng B, mục 10 chặng C), -ban-giao.md, -apk-test.md, -hinh-nghiem-thu.md; quy trình: docs/workflow/PROMPT_NGHIEM_THU_SO_BO.md
Phạm vi kiểm nhanh (không kiểm lại toàn bộ):
1. Git: git status, HEAD, diff --check; git log --oneline 94ab6b0..HEAD; git diff --stat 94ab6b0..HEAD — chỉ 8 file mã mới ở chặng A, UI chặng B (MeloLyricsPane, MeloLyricsUiPolicy, MeloScreenHost, MeloStudioDeck, MeloTabletScreen, MeloUiLabActivity, TelegramPreviewActivity), không đụng service/Media3/DB/TDLib/package/signer/versionCode.
2. Đọc kỹ chỉ phần rủi ro: ShareableLyricsController (generation/cancel/deliver), MeloLyricsPane.setShown/update, MeloLyricsTabs.setActive, hook trong TelegramPreviewActivity (lyricsMetaNow, clearLyricsOf trong logoutUser). Xác nhận hoặc bác bỏ giả thuyết nguyên nhân B1 trong báo cáo 10.3 bằng đọc mã (KHÔNG sửa).
3. APK: SHA-256 31609D80389A9E6EAD8F6417719B640FFBAFFEC3D92916083E3611E687821E88, 33.642.436 byte, vc121, cert eecd3df7…fce8; bản trên Drive owner-only đúng SHA/byte/quyền (connector); bản cài trên Sony khớp (pm path + pull + hash).
4. Sony QV725WEQ54 qua Tiny (an toàn, không cài mới, không uninstall/pm clear/logout/máy bay): kiểm lại nhanh Q1/Q7 (không cần phát nhạc) và, nếu muốn, tái hiện B1 bằng xoay ngang khi tab Lời nhạc đang mở; màn hình tắt trong finally (SONY_SCREEN_END), khôi phục xoay 1/0.
5. Không chạy lại test/Gradle trừ khi có lý do cụ thể. Gate Ni đã ghi trong báo cáo: 2412 test/21 skipped/0 lỗi mỗi biến thể.
Ghi kết quả vào docs/plans/2026-10-31-loi-nhac-p2b-nghiem-thu-so-bo.md: "SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN" / "SƠ BỘ: CẦN SỬA" (liệt kê lỗi có vị trí và bước tái hiện, kể cả B1) / "SƠ BỘ: BLOCKED". Không ghi PASS chính thức, không merge/push/public, không chọn việc khác.
Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, chờ chủ dự án nghiệm thu." kèm `ĐẠT 2026-10-31-loi-nhac-p2b — <commit/APK>` hoặc `Yêu cầu sửa 2026-10-31-loi-nhac-p2b: <lỗi>`.
```

## Checkpoint nghiệm thu sơ bộ vòng 1 — 01/11/2026, Claude Code (Opus 5.5)

- Kết luận: **SƠ BỘ: CẦN SỬA** (lỗi B1). Biên bản: [`-nghiem-thu-so-bo.md`](2026-10-31-loi-nhac-p2b-nghiem-thu-so-bo.md). Trạng thái chính thức: **CHỜ CHỦ DỰ ÁN NGHIỆM THU**.
- Đã xác minh: diff đúng phạm vi; log không lộ tên bài; APK Drive owner-only đúng SHA/byte; bản cài trên Sony đúng SHA `31609D80…1E88`.
- B1 xác nhận bằng mã: `Dialog.dismiss()` gọi `OnDismissListener` bất đồng bộ, nên `cancel()` của pane Deck cũ chạy **sau** `request()` của pane Deck mới và xóa `current` của controller dùng chung → kết quả bị bỏ, pane kẹt Loading (không có nút thoát).
- Sony: chỉ lệnh đọc, màn hình không bật, `Display State=OFF`. Không sửa mã, không build, không merge/push.
- Bước tiếp: chủ dự án chọn REWORK B1 (phiên thực thi mới trên nhánh này) hoặc nghiệm thu kèm giới hạn.

## Checkpoint REWORK-01 (B1) — 01/11/2026, Claude Code (Sonnet 5.5)

- Trạng thái: **REWORK-01 XONG. Chờ phiên nghiệm thu sơ bộ.** Chính thức: **CHỜ CHỦ DỰ ÁN NGHIỆM THU (QA CANDIDATE — CHƯA NGHIỆM THU)**. Bản hiện hành: APK `fd1f943` (SHA `62ED3500…3DB9`); bản `8d4284e` đã bị thay thế.
- Mã: A `6f274b9`, B `3447310`, sửa B1 `fd1f943`. Báo cáo [`-bao-cao.md`](2026-10-31-loi-nhac-p2b-bao-cao.md) mục 11; APK [`-apk-test.md`](2026-10-31-loi-nhac-p2b-apk-test.md); ảnh [`-hinh-nghiem-thu.md`](2026-10-31-loi-nhac-p2b-hinh-nghiem-thu.md).
- Gate Ni: 2419 test/21 skipped/0 lỗi mỗi biến thể; 4 lần assemble xanh. Sony: Q8 kiểm lại PASS (4 lần xoay, 0 `lyrics_drop`).
- Việc còn mở (không thuộc B1): F2 bài Telegram thiếu nghệ sĩ không tự tìm; F3 LRCLIB `search` chập chờn; F4 thanh tab nằm dưới ảnh bìa lớn ở Deck dọc; chưa có lời tự động cho bài Telegram có tên + nghệ sĩ; âm thanh thật chưa xác nhận.
- Không merge/push/public.

### Prompt nghiệm thu sơ bộ — dùng bản này thay cho bản ở “Checkpoint chặng C” (đổi HEAD/APK/SHA)

```text
MELOVAULT — PHIÊN NGHIỆM THU SƠ BỘ NHANH (sau REWORK-01)

Repo/worktree: D:\Bots\_worktrees\MeloVault-loi-nhac-p2b (repo chuẩn D:\Bots\MeloVault-E91-Shareable; KHÔNG sửa mã, KHÔNG build APK mới)
TASK_ID: 2026-10-31-loi-nhac-p2b
Nhánh: dot/2026-10-31-loi-nhac-p2b; baseline 94ab6b0; HEAD dự kiến: commit cuối của nhánh (chứa checkpoint REWORK-01); mã A 6f274b9, mã B 3447310, sửa B1 fd1f943; APK build từ fd1f943
Hồ sơ: docs/plans/2026-10-31-loi-nhac-p2b.md (kế hoạch), -bao-cao.md (mục 9 chặng B, mục 10 chặng C, mục 11 REWORK-01), -ban-giao.md, -apk-test.md, -hinh-nghiem-thu.md; quy trình: docs/workflow/PROMPT_NGHIEM_THU_SO_BO.md
Phạm vi kiểm nhanh (không kiểm lại toàn bộ):
1. Git: git status, HEAD, diff --check; git log --oneline 94ab6b0..HEAD; git diff --stat 94ab6b0..HEAD — chỉ file lời nhạc mới + UI chặng B + host; không đụng service/Media3/DB/TDLib/package/signer/versionCode.
2. Đọc kỹ chỉ phần rủi ro: ShareableLyricsController (request/cancel(client)/deliver), MeloLyricsPane (setShown, update, start, khóa Loading 40 giây), MeloLyricsTabs.setActive, hook TelegramPreviewActivity (lyricsMetaNow, clearLyricsOf). Xác nhận cách sửa B1 đúng với nguyên nhân ở báo cáo 11.1 và không mở ra lỗi mới (ví dụ hai pane cùng sống: Hai khung có panel Hàng đợi mở và Deck mở cùng lúc).
3. APK: SHA-256 62ED3500C171AAF9CCFCEE3EDDB8D7A11F3D08B65B93DA425A3790126B173DB9, 33.642.436 byte, vc121, cert eecd3df7…fce8; bản trên Drive owner-only đúng SHA/byte/quyền (connector); bản cài trên Sony khớp (pm path + pull + hash).
4. Sony QV725WEQ54 qua Tiny (an toàn, không cài mới, không uninstall/pm clear/logout/máy bay): tái hiện lại Q8 (Deck dọc, tab Lời nhạc, xoay ngang rồi dọc) và Q1/Q7 nhanh. Lưu ý: chạm thẻ "Nghe tiếp" sẽ tự phát nhạc; tạm dừng ngay (keyevent 127). Màn hình tắt trong finally (SONY_SCREEN_END), khôi phục xoay 1/0.
5. Không chạy lại test/Gradle trừ khi có lý do cụ thể. Gate Ni đã ghi: 2419 test/21 skipped/0 lỗi mỗi biến thể.
Ghi kết quả vào docs/plans/2026-10-31-loi-nhac-p2b-nghiem-thu-so-bo.md: "SƠ BỘ: KHÔNG PHÁT HIỆN LỖI CHẶN" / "SƠ BỘ: CẦN SỬA" (liệt kê lỗi có vị trí và bước tái hiện) / "SƠ BỘ: BLOCKED". Không ghi PASS chính thức, không merge/push/public, không chọn việc khác.
Kết thúc tiếng Việt: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, chờ chủ dự án nghiệm thu." kèm `ĐẠT 2026-10-31-loi-nhac-p2b — <commit/APK>` hoặc `Yêu cầu sửa 2026-10-31-loi-nhac-p2b: <lỗi>`.
```
