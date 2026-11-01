# Bàn giao — Nhiều Playlist có tên

TASK_ID: **2026-11-01-nhieu-playlist-co-ten**.

## Checkpoint 01/11/2026 19:05 — Chặng 4 DỪNG GIỮA CHỪNG, BLOCKED: Sony mất ADB (Claude Code, Sonnet 5.5)

- **Vai:** thực thi Chặng 4 (Tiny build + QA Sony), theo lệnh “tiến hành Chặng 4”. Không code, không sửa mã. Phiên đã dừng; không còn tiến trình QA (watcher Tiny đã tắt 19:02:27).
- **Worktree/nhánh:** `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`, `dot/2026-11-01-nhieu-playlist-co-ten`. Mã không đổi từ Chặng 3 (`751b623`); đầu vào Chặng 4 = HEAD `0efec5a`. HEAD hiện tại: `git rev-parse HEAD` (commit hồ sơ Chặng 4 nằm sau đó).
- **Báo cáo chi tiết:** [phụ lục Chặng 4](2026-11-01-nhieu-playlist-co-ten-bao-cao-chang4.md) (bảng Q1–Q6, sự cố, việc còn lại).

| Bước | Trạng thái | Bằng chứng | Việc còn lại |
|---|---|---|---|
| S20 bundle + build Tiny | Xong | bundle `d43922cb…fecff` 13.673.183 byte khớp hai đầu; clone `C:\Bots\MeloVault-NPL-IsolatedQA-20261101-0efec5a` HEAD `0efec5a`; config=true; Debug 2m51s, Release 3m42s BUILD SUCCESSFUL | — |
| S21 gate APK + cài | Xong | Debug vc121 `0.6.96-beta`, SHA-256 `5B2FB5CC323B486ADFC2F359BF7C9FCC20E104D0D6FDE4DB3D2D3B3BDE03FEF8`, 33.773.508 byte, cert `eecd3df7…fce8`; một lệnh `install -r` → Success; SHA bản cài khớp. Release `7C3340E1…81810` 30.933.358 byte chưa cài | — |
| S22 QA Sony | **Dở**: Q1 Q2 Q3 PASS; Q4 dở; Q5 Q6 chưa | 29 ảnh trong `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/chang4/shots/` (git bỏ qua) | Làm Q4–Q6, dọn, ghép `00_tong_hop.png` |
| S23 Drive | Chưa làm | — | Upload owner-only sau khi QA xong; chưa có link nào |
| S24 báo cáo + prompt sơ bộ | Chưa làm | — | Sau S22–S23 |

- **BLOCKED:** `adb` mất Sony `QV725WEQ54` lúc ≈ 18:58 (ngay sau khi chạm “Xem cả hàng đợi”; chụp màn hình ra 0 byte), chưa quay lại sau ≈ 4+ phút thăm dò. Chưa rõ do cáp/USB, khởi động lại, hay app treo — **không kết luận lỗi ứng dụng**. Cập nhật 19:07: máy chỉ **thoáng hiện một lần** trong `adb devices -l` (transport id 10556, ≈ 19:04) rồi lại mất; tám lần thử tiếp (≈ 15 s/lần) đều `device not found` — kết nối USB chập chờn, không đủ ổn định để QA. Chưa đọc được logcat/crash sau sự cố.
- **Cần làm ngay khi Sony kết nối lại (trước mọi thao tác khác):** (1) `adb devices -l` + `getprop` xác minh lại; (2) đọc `logcat -b crash` / `pidof app.melovault.player` để biết app có crash không; (3) khôi phục xoay: `settings put system user_rotation 0` rồi `settings put system accelerometer_rotation 1`; (4) màn hình đang `ON` ở lần kiểm cuối — **`SONY_SCREEN_END` chưa xác minh**, phải tắt (`keyevent 223`) và kiểm `Display State=OFF` trong `finally`.
- **Tự báo cáo sự cố (chi tiết phụ lục mục 4):** `monkey` khởi chạy ứng dụng lạ (hộp thoại USB — tôi chạm “TỪ CHỐI” một lần —, Google Tin nhắn, Vita3K); sau đó chỉ dùng `am start`. Một lần chạm ⌃ vô tình phát nhạc ≈ 20–47 s rồi tạm dừng. Một lần `am kill` (ở nền) để kiểm Q3.
- **Dọn còn lại:** Playlist thử `QA Thu 1` (4 bài) còn trên Sony; chưa đổi tên/xóa. Đồng bộ đang “Chưa bật đồng bộ” nên không lên Telegram.
- **Chênh lệch kế hoạch:** tên thử ASCII `QA Thu 1` (adb không gõ được dấu); thứ tự Q4/Q5 đổi (Q5 trước để hàng chờ có nhiều bài). Q5 theo điều chỉnh: hàng chờ chỉ gồm bài Telegram (3 bài của `QA Thu 1`).
- **Chưa làm / vẫn BLOCKED:** đồng bộ hai máy Q7–Q9 (chưa có (a)/(b)); âm thanh thật CHƯA XÁC NHẬN; Release chưa chạy trên máy.
- **File chưa commit:** không có sau commit hồ sơ này. Repo chính vẫn có `?? .codex-remote-attachments/` và kế hoạch UI/UX chưa commit của phiên khác, không động tới.
- **Bước tiếp:** chủ dự án kiểm cáp Sony và giao lại phiên thực thi Chặng 4 (Codex hoặc Claude Code) bằng prompt tiếp tục dưới.

### Prompt tiếp tục Chặng 4 (S22 từ Q4 → S24)

```text
MELOVAULT — TIẾP TỤC THỰC THI CHẶNG 4 (S22 Q4 → S24)

Repo/worktree: D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten (KHÔNG làm ở repo chính D:\Bots\MeloVault-E91-Shareable)
TASK_ID: 2026-11-01-nhieu-playlist-co-ten
Nhánh: dot/2026-11-01-nhieu-playlist-co-ten
Kế hoạch đã chốt: docs/plans/2026-11-01-nhieu-playlist-co-ten.md (mục 5 Chặng 4, mục 7, mục 8)
Bàn giao: docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md (checkpoint đầu file); phụ lục Chặng 4: docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao-chang4.md; báo cáo chính: …-bao-cao.md
HEAD dự kiến: commit hồ sơ Chặng 4 (git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao-chang4.md); mã kết thúc ở 751b623; APK Debug đã build/cài từ HEAD 0efec5a
Model: do chủ dự án chọn. Bước tiếp tục: S22 từ Q4, rồi S23, S24. KHÔNG build lại, KHÔNG cài lại APK (APK Debug SHA-256 5B2FB5CC323B486ADFC2F359BF7C9FCC20E104D0D6FDE4DB3D2D3B3BDE03FEF8 đã cài trên Sony; chỉ cài lại nếu `pm path` + pull base.apk cho SHA khác).
Tiny: DESKTOP-HNP4GVR (Desktop Commander id 9c7f081b-6e1c-4221-ac78-da470bab1e5d); clone build C:\Bots\MeloVault-NPL-IsolatedQA-20261101-0efec5a; APK Debug …\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk; công cụ QA C:\Bots\_qa_temp\NPL-0efec5a\ (q.ps1, watcher.ps1, shots\) — đọc q.ps1 trước khi dùng.

Lưu ý riêng:
- Điều kiện vào: `adb devices -l` trên Tiny phải thấy QV725WEQ54 = device, getprop model XQ-AS52 / Sony. Chưa thấy ⇒ BLOCKED, dừng, báo.
- Việc đầu tiên khi máy quay lại: đọc logcat -b crash và pidof app.melovault.player (app có crash lúc “Xem cả hàng đợi” không? ghi vào phụ lục, không kết luận khi thiếu bằng chứng); khôi phục xoay (settings put system user_rotation 0; settings put system accelerometer_rotation 1) khi kết thúc QA; tắt màn và xác minh Display State=OFF trong finally, ghi SONY_SCREEN_END.
- KHÔNG dùng `adb shell monkey` (nó khởi chạy ứng dụng lạ). Mở app bằng: adb shell am start -n app.melovault.player/com.bsdung.musicassistantcar.data.telegram.TelegramPreviewActivity. Cấm BACK ở màn gốc, cấm uninstall/pm clear/xóa dữ liệu/logout/-d/downgrade/bật máy bay. Chạm vào thanh “Nghe tiếp”/⌃ sẽ PHÁT nhạc: tránh trừ khi đang làm Q5.
- Q4: màn Đang phát → cuộn lên → tab “Tiếp theo” → “Xem cả hàng đợi” → chip “≡ Lưu Playlist” → tên `QA Hang cho` (ASCII) → kiểm “Đã lưu N bài, bỏ qua M bài.” và số bài. Nếu app thật sự crash ở “Xem cả hàng đợi” ⇒ ghi BLOCKED/REWORK (lỗi có vị trí, bước tái hiện, logcat) và dừng; sửa mã là việc của REWORK.
- Q5 (điều chỉnh): ▶ Phát hết trong QA Thu 1 ⇒ hàng chờ CHỈ gồm 3 bài Telegram (bài máy “Bee Moved” không nằm trong hàng chờ; thông báo “Bài trên máy không nằm trong hàng chờ…”); Next 1 lần; Media3 PLAYING chỉ chứng minh luồng phát — ghi CHƯA XÁC NHẬN ÂM THANH THỰC; tạm dừng sau khi xong.
- Q6: Đổi tên QA Thu 1 → QA Thu 2; Xóa QA Hang cho (xác nhận) — bài vẫn còn trong thư viện; xoay ngang rồi dọc ở chi tiết (settings put system accelerometer_rotation 0; user_rotation 1/0; chờ ≈ 10 s trước khi dump), kiểm giữ màn chi tiết/cuộn; khôi phục xoay. Dọn: xóa QA Thu 2 bằng UI khi hết ảnh (ghi lại; đồng bộ đang “Chưa bật đồng bộ”, KHÔNG bật).
- Không tự bật tự đồng bộ, không gửi/xóa outbox, không tắt mạng. Q7–Q9 hai máy: ghi BLOCKED — chưa QA đồng bộ hai máy thật, trừ khi chủ dự án chọn (a)/(b) ở kế hoạch mục 7.
- S22 xong: ghép 6–8 ảnh chính thành docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/00_tong_hop.png (≤ 1600 px cạnh dài, ghi số bước từng ô); không commit ảnh riêng tư. Cập nhật phụ lục Chặng 4 (bảng Q1–Q6) và bàn giao sau mỗi mốc.
- S23: Drive Ni N owner-only theo CLAUDE.md mục 8–9 (thư mục riêng TASK_ID/ngày/vc121/commit, tên APK duy nhất, kiểm quyền bằng connector, không public). Tạo docs/plans/2026-11-01-nhieu-playlist-co-ten-apk-test.md và …-hinh-nghiem-thu.md chỉ với link đã kiểm; nhãn “QA CANDIDATE — CHƯA NGHIỆM THU”. Drive chặn ⇒ BLOCKED đúng phần, lưu vị trí nội bộ. Không bịa link.
- S24: hoàn chỉnh báo cáo chính (≤ ~250 dòng, chi tiết ở phụ lục), bàn giao, và viết PROMPT_NGHIEM_THU_SO_BO.md đầy đủ (mẫu docs/workflow/PROMPT_NGHIEM_THU_SO_BO.md) cho phiên sơ bộ mới. KHÔNG tự chạy vai sơ bộ. Không merge/push/public.

Kết thúc tiếng Việt dễ hiểu ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: “Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh.” kèm đường dẫn bàn giao và prompt sơ bộ (hoặc, nếu chưa hết chặng, “Đã dừng, bàn giao phiên thực thi tiếp theo.”). KHÔNG tự đề xuất rồi làm việc kế tiếp.
```

---

## Checkpoint 01/11/2026 — Chặng 3 (S11–S19) xong (Claude Code, Sonnet 5.5)

- **Vai:** thực thi Chặng 3 (code + test Ni), theo lệnh “Tiếp chặng 3” của chủ dự án trong cùng hội thoại. Không ADB/Sony/Tiny/Drive; `SONY_SCREEN_END=KHÔNG THAO TÁC`. Phiên đã dừng, không còn tác vụ chạy.
- **Worktree/nhánh:** `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`, `dot/2026-11-01-nhieu-playlist-co-ten`. Baseline `57c1be6`, commit kế hoạch `b0cfeac`. Mã Chặng 3 kết thúc ở `751b623`; HEAD hiện tại: `git rev-parse HEAD` (commit hồ sơ Chặng 3 nằm sau đó).
- **Báo cáo:** [chính](2026-11-01-nhieu-playlist-co-ten-bao-cao.md) (mục “Chặng 3”) và [phụ lục Chặng 3](2026-11-01-nhieu-playlist-co-ten-bao-cao-chang3.md).

| Chặng / bước | Trạng thái | Commit | Bằng chứng | Việc còn lại |
|---|---|---|---|---|
| Chặng 1 S1–S5, Chặng 2 S6–S10 | Xong | `fa6e858` … `2ebf127` | xem checkpoint cũ bên dưới | — |
| S11 nhãn Yêu thích | Xong | `3f150c5` | sửa 6 dòng test nhãn (liệt kê ở phụ lục mục 4) | — |
| S12 policy thuần | Xong | `a02ed2a` | 29 test pass | — |
| S13 controller + mở/đóng + cờ | Xong | `79b318e` | 22 test pass | — |
| S14–S18 giao diện | Xong về mã | `0e8d45a`, `751b623` | 10 guard nguồn pass; **chưa nhìn thấy trên máy** | QA Sony ở Chặng 4 |
| S19 gate Ni | Xong | — | Debug và Release mỗi biến thể: 199 suite, 2683 test, 21 skipped, 0 failures, 0 errors (baseline 196/2622/21/0/0; +61); assemble Shareable Debug/Release, `-PmeloOpen=true`, Personal Debug BUILD SUCCESSFUL; `git diff --check` sạch | — |
| Chặng 4 S20–S24 (Tiny, Sony, Drive) | Chưa làm | — | — | Phiên thực thi 4 |

- **File chưa commit:** không có sau commit hồ sơ (log gate ở `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/`, git bỏ qua). Repo chính vẫn có `?? .codex-remote-attachments/` và kế hoạch UI/UX chưa commit của phiên khác, không động tới.
- **File cũ đã chạm ở Chặng 3 (chỉ thêm hoặc đổi chữ):** `TelegramPreviewActivity.kt` (nối controller, menu, snapshot), `MeloLibraryPane.kt`, `MeloCompactScreen.kt`, `MeloQueuePane.kt`, `MeloScreenHost.kt` (+`named()` mặc định null), các policy nhãn, `MeloSyncSettingsActivity.kt`, `MeloUiLabActivity.kt`, `PlaylistSyncStatusPolicy.kt` (chữ). Không đụng `TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator`, `TransmitPlanner`, V1, SQLite, service/Media3, TDLib auth, package, signer, versionCode. Car Mode không có tính năng mới (guard nguồn).
- **Lỗi/giới hạn còn mở:**
  1. Giao diện Playlist có tên chưa được nhìn thấy hay chạm thử trên máy (phụ lục mục 6).
  2. Phát Playlist có bài trên máy: hàng chờ chỉ gồm bài Telegram (phụ lục 5.2) — khác Q5 của kế hoạch; chủ dự án quyết định có chấp nhận hay giao REWORK có đổi service.
  3. Cờ `NAMED_ENABLED` ở file riêng; chưa có chấp thuận chuyển sang `TelegramPlaylistV2Session.kt`.
  4. Guard cũ `SyncBuildGateReworkTest` giữ nguyên (xem Chặng 2); đồng bộ Playlist có tên chưa chạy qua Telegram thật; E105 vẫn BLOCKED nghiệm thu.
  5. Hai quyết định treo từ kế hoạch: 4.5 (một công tắc chung, đang theo mặc định) và mục 7 (QA đồng bộ hai máy ⇒ mặc định BLOCKED).
- **Bước tiếp:** chủ dự án mở phiên thực thi mới cho **Chặng 4 (S20–S24)** bằng prompt dưới.

### Prompt cho phiên thực thi Chặng 4

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten (repo chuẩn D:\Bots\MeloVault-E91-Shareable — KHÔNG làm ở repo chính, nó đang ở nhánh tài liệu khác và có file chưa commit của phiên khác)
TASK_ID: 2026-11-01-nhieu-playlist-co-ten
Nhánh: dot/2026-11-01-nhieu-playlist-co-ten
Kế hoạch Claude đã được chủ dự án CHỐT: docs/plans/2026-11-01-nhieu-playlist-co-ten.md
Bàn giao: docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md; báo cáo: docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao.md (Chặng 1, 2) và phụ lục docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao-chang3.md (Chặng 3)
HEAD dự kiến: commit hồ sơ Chặng 3 (git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md); mã Chặng 3 kết thúc ở 751b623
Model: do chủ dự án chọn; bước tiếp tục: Chặng 4, S20 → S24
Baseline: 57c1be64436c3c364bcd8c1a5f8a8193ea52a553, commit kế hoạch: b0cfeac138e3cb0edbb4857f650795c79bd78957
PHIÊN: 4 = build Tiny + QA Sony + bàn giao APK. Không code tính năng mới. Gặp lỗi cần sửa mã → ghi BLOCKED/REWORK trong bàn giao và dừng (sửa code là việc của phiên thực thi REWORK do chủ dự án giao).
Lưu ý riêng:
- Gate Ni cuối Chặng 3: Debug và Release mỗi biến thể 199 suite, 2683 test, 21 skipped, 0 failures, 0 errors; assembleShareableDebug/Release, -PmeloOpen=true, assemblePersonalDebug đều BUILD SUCCESSFUL. KHÔNG có hình hay thử trên máy nào của giao diện Playlist có tên: Chặng 4 là lần đầu nhìn thấy nó. Phần “Chưa kiểm chứng” của phụ lục Chặng 3 (mục 6) là danh sách việc cần soi: bố cục chip 4 nút ở đầu chi tiết, hộp thoại, vùng chạm, xoay màn hình, chữ lớn, thanh trạng thái.
- Tính năng nằm ở: tab Playlist = chỉ mục (★ Yêu thích · N bài, các Playlist, + Tạo Playlist); mở một Playlist → chip ‹ Playlist, ⤨ Ngẫu nhiên, ▶ Phát hết, ⋮ (Đổi tên, Sắp xếp, Xóa Playlist); “≡ Thêm vào Playlist…” trong menu dòng (nhấn giữ một dòng bài) và menu dòng của Hàng đợi; “≡ Lưu Playlist” (chip) trong màn Hàng đợi; hộp Sắp xếp ↑/↓. Gọn: menu “☆ Playlist” mở chỉ mục dạng hộp. Car Mode: chỉ đổi chữ “Playlist”→“Yêu thích”.
- Điều chỉnh Q5 (kế hoạch mục 7) theo phụ lục Chặng 3 mục 5.2: khi ▶ Phát một Playlist có bài trên máy, hàng chờ CHỈ gồm các bài Telegram theo thứ tự (service giữ hai hàng chờ riêng); bài trên máy phải chạm từng bài. Q5 kỳ vọng: hàng chờ = số bài Telegram của Playlist (Q2: 3 bài Telegram + 1 bài máy ⇒ 3 bài), có dòng thông báo “Bài trên máy không nằm trong hàng chờ…”. Ghi rõ vào báo cáo, không coi là lỗi nhưng nêu để chủ dự án quyết định.
- Đồng bộ: Playlist có tên đồng bộ chung công tắc “Tự đồng bộ Yêu thích & Playlist”; chỉ chạy khi công tắc BẬT và Yêu thích đã mở. KHÔNG tự bật công tắc đồng bộ thật, KHÔNG gửi/xóa outbox cũ, KHÔNG tắt mạng (kế hoạch mục 7 “Không tự động”). Sony không có máy thứ hai ⇒ Q7–Q9 (đồng bộ hai máy) ghi BLOCKED — chưa QA đồng bộ hai máy thật trừ khi chủ dự án cho cách (a)/(b) trong bàn giao.
- Test JVM không thay chứng cứ Sony; ngược lại Sony không chứng minh âm thanh thật: ghi CHƯA XÁC NHẬN ÂM THANH THỰC.
- Cờ NAMED_ENABLED nằm ở NamedPlaylistFlags.kt (object riêng); không sửa TelegramPlaylistV2Session.kt.
- Giữ nguyên mọi luật CLAUDE.md mục 3, 7, 8, 9: bundle Git từ Ni có SHA-256 hai đầu, clone cô lập MỚI trên Tiny, config Telegram theo ngoại lệ E104 S11 (chỉ Copy-Item từ C:\Bots\MeloVault-E99-IsolatedQA\local.properties, không đọc/in/hash; kiểm bằng reportTelegramConfig), gate APK (package app.melovault.player, vc121 / 0.6.96-beta, cert SHA-256 khớp bản đang cài, config=true, SHA-256/bytes Tiny=Ni), kiểm Media3 không đang phát trước adb -s QV725WEQ54 install -r, cấm uninstall/pm clear/xóa dữ liệu/-d/downgrade, tắt màn trong finally và ghi SONY_SCREEN_END. Ảnh tại docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/ (00_tong_hop.png ≤ 1600 px), không commit ảnh riêng tư. Drive Ni N owner-only; nhãn QA CANDIDATE — CHƯA NGHIỆM THU; Drive chặn tải ⇒ BLOCKED đúng phần.
- Dọn sau QA: xóa các Playlist thử (QA Thử 2 …) bằng UI; nếu đồng bộ đang bật, thao tác xóa cũng đồng bộ — ghi lại. Không đọc nội dung file tg_named_playlists_v1.
- Gặp điều kiện dừng ở kế hoạch mục 8 → ghi BLOCKED vào bàn giao, dừng.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, *-ban-giao.md, các báo cáo, kế hoạch TASK_ID (mục 5 Chặng 4, mục 7). Kiểm git status, branch, HEAD, plan commit, git diff --check, agent/build đang chạy. Tree dirty không rõ nguồn hoặc HEAD không khớp bàn giao → STOP; không reset/clean/stash/đè.
2. Làm S20→S23 đúng kế hoạch mục 5 (bảng Chặng 4), có thể chia hai phiên (S20–S21 build+gate APK; S22–S23 QA+Drive) nếu cạn quota — ghi checkpoint vào bàn giao sau mỗi mốc. QA theo mục 7 với 6 thao tác Q1–Q6 (điều chỉnh Q5 như trên), kèm bảng 3–6 bước (thiết bị/serial, hành động, kỳ vọng, thực tế, PASS/FAIL/BLOCKED, ảnh trước/sau) trong báo cáo, một ảnh tổng hợp, link Drive đã kiểm vào *-apk-test.md và *-hinh-nghiem-thu.md.
3. S24: hoàn chỉnh báo cáo (≤ ~250 dòng; chi tiết QA tách phụ lục .md có liên kết), bàn giao, và đưa prompt PROMPT_NGHIEM_THU_SO_BO.md đầy đủ cho phiên sơ bộ mới. Không tự chạy vai sơ bộ.
4. Commit hồ sơ trên dot/2026-11-01-nhieu-playlist-co-ten. Không merge/push/public.

Kết thúc tiếng Việt dễ hiểu ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: “Đã dừng. Mở phiên mới để nghiệm thu sơ bộ nhanh.” kèm đường dẫn bàn giao và prompt nghiệm thu sơ bộ (hoặc, nếu chưa hết chặng, “Đã dừng, bàn giao phiên thực thi tiếp theo.”). KHÔNG tự đề xuất rồi làm việc kế tiếp.
```

---

## Checkpoint 01/11/2026 — Chặng 2 (S6–S10) xong (Claude Code, Sonnet 5.5)

- **Vai:** thực thi Chặng 2 (code + test Ni), theo lệnh “tiếp tục chặng 2” của chủ dự án trong cùng hội thoại với Chặng 1. Không ADB/Sony/Tiny/Drive; `SONY_SCREEN_END=KHÔNG THAO TÁC`. Phiên đã dừng, không còn tác vụ chạy.
- **Worktree/nhánh:** `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`, `dot/2026-11-01-nhieu-playlist-co-ten`. Baseline `57c1be6`, commit kế hoạch `b0cfeac`. HEAD hiện tại: `git rev-parse HEAD` (commit hồ sơ Chặng 2 nằm sau mã `2ebf127`).
- **Báo cáo:** [2026-11-01-nhieu-playlist-co-ten-bao-cao.md](2026-11-01-nhieu-playlist-co-ten-bao-cao.md) (mục “Chặng 2”).

| Chặng / bước | Trạng thái | Commit | Bằng chứng | Việc còn lại |
|---|---|---|---|---|
| Chặng 1 S1–S5 | Xong | `fa6e858` … `3336154` | 2539 test, xem checkpoint Chặng 1 bên dưới | — |
| S6 `NamedPlaylistSyncProtocol` (`#mvlist v1`) | Xong | `ea38b30` | 19 test pass | — |
| S7 `NamedPlaylistSyncCoordinator` | Xong | `7f8248d` | 26 test pass | — |
| S8 nối `AutomaticPlaylistSync` / service / transport / cổng | Xong | `3994772` | 13 + 7 + 5 test pass | — |
| S9 trạng thái + “Thay đổi chờ gửi” | Xong | `2ebf127` | 10 + 3 test pass | — |
| S10 gate Ni | Xong | — | Debug và Release mỗi biến thể: 196 suite, 2622 test, 21 skipped, 0 failures, 0 errors (baseline Chặng 2: 190/2539/21/0/0; +83 đúng phần mới); assembleShareableDebug+Release BUILD SUCCESSFUL; `git diff --check` sạch | — |
| Chặng 3 S11–S19 (UI) | Chưa làm | — | — | Phiên thực thi 3 |
| Chặng 4 S20–S24 (Tiny, Sony, Drive) | Chưa làm | — | — | Phiên thực thi 4 |

- **File chưa commit:** không có sau commit hồ sơ (log gate ở `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/`, git bỏ qua). Repo chính vẫn có `?? .codex-remote-attachments/` và kế hoạch UI/UX chưa commit của phiên khác, không động tới.
- **File Yêu thích/đồng bộ cũ đã chạm (đều chỉ thêm, có test):** `AutomaticPlaylistSync.kt` (gọi Playlist có tên sau Yêu thích; review mang thêm trường), `TdlibPlaylistSyncService.kt` (+`runNamedSync`), `SyncServiceProvider.kt`, `SyncSettingsController.kt` (+`runNamed` mặc định BUILD_OFF), `TdlibPlaylistSyncTransport.kt` (+tiền tố `#mvlist` được phép gửi), `TdlibHistoryReader.kt` (tin `#mvlist` bị sửa = bằng chứng liên tục), `PlaylistSyncStatusPolicy.kt` (+đếm Playlist có tên), `MeloSyncSettingsActivity.kt` (liệt kê/đếm thêm). Không đụng `TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator`, `TransmitPlanner`, V1, SQLite, service/Media3, package/signer/versionCode. Không file test cũ nào bị sửa.
- **Lỗi/giới hạn còn mở:**
  1. Chưa có gì mở `NamedPlaylistSession` trong app: mã đồng bộ Chặng 2 chưa chạy thật cho tới S13 (Chặng 3).
  2. Cờ `NAMED_ENABLED` chưa có (cần sửa `TelegramPlaylistV2Session.kt`). Chủ dự án cần chốt: cho phép Chặng 3 thêm đúng 1 dòng const vào file đó, hoặc dùng object cờ riêng. **Chưa có chấp thuận ghi trong hồ sơ ⇒ Chặng 3 mặc định dùng object cờ riêng.**
  3. Guard cũ `SyncBuildGateReworkTest` (đếm `.runOnce(`) không sửa; lối vào mới đặt tên khác + guard mới `NamedPlaylistSyncCallSitesTest` (báo cáo mục 5). Chủ dự án muốn cập nhật guard cũ ⇒ REWORK.
  4. Chưa chạy Keystore thật / Telegram thật với `#mvlist`; E105 vẫn BLOCKED nghiệm thu.
  5. Hai quyết định treo từ kế hoạch (4.5 công tắc chung, mặc định đang theo; mục 7 QA hai máy).
- **Bước tiếp:** chủ dự án mở phiên thực thi mới cho **Chặng 3 (S11–S19)** bằng prompt dưới.

### Prompt cho phiên thực thi Chặng 3

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten (repo chuẩn D:\Bots\MeloVault-E91-Shareable — KHÔNG làm ở repo chính, nó đang ở nhánh tài liệu khác và có file chưa commit của phiên khác)
TASK_ID: 2026-11-01-nhieu-playlist-co-ten
Nhánh: dot/2026-11-01-nhieu-playlist-co-ten
Kế hoạch Claude đã được chủ dự án CHỐT: docs/plans/2026-11-01-nhieu-playlist-co-ten.md
Bàn giao: docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md; báo cáo: docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao.md (mục Chặng 1 và Chặng 2)
HEAD dự kiến: commit hồ sơ Chặng 2 (git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md); mã Chặng 2 kết thúc ở 2ebf127
Model: do chủ dự án chọn; bước tiếp tục: Chặng 3, S11 → S19
Baseline: 57c1be64436c3c364bcd8c1a5f8a8193ea52a553, commit kế hoạch: b0cfeac138e3cb0edbb4857f650795c79bd78957
PHIÊN: 3 = code + test Ni, CHỈ Chặng 3 (S11–S19, giao diện Views). Không làm Chặng 4 (Tiny/Sony/Drive).
Lưu ý riêng:
- Đã có từ Chặng 1–2 (đọc trước khi viết UI, không viết lại): NamedPlaylistModel.kt (ListId, NamedPlaylistState: lists(), entries(listId), listsContaining(item), itemCount, các thao tác), NamedPlaylistSession.kt (NamedPlaylistSessionApi: open/close/snapshotFor/entries/listsContaining + createList/renameList/deleteList/addTelegram/removeTelegram/moveItem/addLocal/removeLocal/moveLocal/rebalance trả NamedOutcome), NamedPlaylistSyncProtocol/Coordinator, NamedPendingReviewPolicy, TelegramPlaylistRuntime.namedSession(noBackupDir), LocalFingerprint.of(tên, thời lượng ms, kích thước).
- Việc UI phải làm mà Chặng 2 CHƯA làm: MỞ/ĐÓNG NamedPlaylistSession cùng chỗ loadSavedPlaylist/closeSavedPlaylist trong TelegramPreviewActivity (S13). Cho tới lúc đó session luôn ở trạng thái "chưa mở" nên mã đồng bộ Chặng 2 chưa chạy thật (AutomaticPlaylistSync thấy view = null và bỏ qua). Mọi ghi qua TelegramPlaylistRuntime.submit; callback kiểm tài khoản/epoch như MeloSavePolicy.acceptsCallback; sau khi đồng bộ áp thay đổi thì làm mới UI (mẫu PlaylistLiveRefresh).
- Cờ NAMED_ENABLED (kế hoạch mục 8) CHƯA có: phải nằm cạnh V2_ENABLED trong TelegramPlaylistV2Session.kt, file Chặng 1–2 cấm sửa. Chặng 3 được phép sửa ĐÚNG MỘT dòng const đó nếu chủ dự án đã chấp thuận trong bàn giao; nếu bàn giao chưa ghi chấp thuận thì tạo object cờ riêng trong file mới và ghi quyết định vào báo cáo.
- Nhãn S11: đổi chữ theo Phụ lục A (kế hoạch). Các chuỗi mới ở Chặng 2 (NamedPendingReviewPolicy, ghi chú trạng thái "Playlist có tên") đã theo Phụ lục A; dòng "Tự đồng bộ Playlist" ở Cài đặt (MeloSyncSettingsActivity) và nhãn trạng thái còn chữ cũ → đổi ở S11 và liệt kê test nào đổi chuỗi.
- KHÔNG sửa TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator, V1, SQLite, service/Media3, package/signer/versionCode. Có guard mã nguồn (SyncBuildGateReworkTest, NamedPlaylistSyncCallSitesTest, P5ReadinessInertnessTest, TdlibSendDiagnosticsTest, ShareableDialogThemeTest): không sửa assertion cũ; UI mới dùng Theme_MeloVault_Dialog cho hộp thoại.
- Gate kế hoạch mục 6 (Chặng 3): thêm -PmeloOpen=true :app:testShareableDebugUnitTest :app:assembleShareableDebug và :app:assemblePersonalDebug. Log ra docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/, đếm XML. Baseline cho Chặng 3 = kết quả cuối Chặng 2 (xem báo cáo, mục Chặng 2); không @Ignore, không sửa assertion cũ ngoài nhãn S11 (liệt kê).
- Không ADB/Sony/Tiny/Drive ở chặng này. Màn Sony giữ OFF, ghi SONY_SCREEN_END=KHÔNG THAO TÁC.
- Gặp điều kiện dừng ở kế hoạch mục 8 → ghi BLOCKED vào bàn giao, dừng.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, *-ban-giao.md, *-bao-cao.md, kế hoạch TASK_ID. Kiểm git status, branch, HEAD, plan commit, git diff --check, agent/build đang chạy. Tree dirty không rõ nguồn hoặc HEAD không khớp bàn giao → STOP; không reset/clean/stash/đè.
2. Làm S11→S18 đúng kế hoạch mục 5 (bảng Chặng 3), commit theo mốc từng bước. Logic thuần đặt ở policy/model, Activity chỉ nối. Viết test đủ cột "Xong khi". Chạy gate mục 6 (có thêm -PmeloOpen và Personal) một lần sau S18 (S19), log ra file, chỉ ghi tổng kết, test defined/pass/skipped/fail thực và diff --check. Sau mỗi mốc cập nhật báo cáo (mục "Chặng 3", mở đầu "Thực thi: Claude Code <model> <effort nếu biết>, phiên Chặng 3") và *-ban-giao.md: bước xong/dở, file/commit, bằng chứng, lỗi/giới hạn, việc tiếp.
3. Báo cáo ≤ ~250 dòng (nếu vượt, tách phụ lục .md có liên kết), bảng trước. Không đoán model/chứng cứ.
4. Commit code, báo cáo, bàn giao trên dot/2026-11-01-nhieu-playlist-co-ten. Không merge/push/public. Viết prompt đầy đủ cho phiên thực thi Chặng 4 (S20–S24: build Tiny, QA Sony, Drive owner-only, hồ sơ APK) vào *-ban-giao.md theo cùng mẫu này.

Kết thúc tiếng Việt dễ hiểu ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm đường dẫn bàn giao và prompt Chặng 4. KHÔNG tự đề xuất rồi làm việc kế tiếp.
```

---

## Checkpoint 01/11/2026 — Chặng 1 (S1–S5) xong (Claude Code, Sonnet 5.5)

- **Vai:** thực thi Chặng 1 (code + test Ni). Không ADB/Sony/Tiny/Drive; `SONY_SCREEN_END=KHÔNG THAO TÁC`. Phiên đã dừng, không còn tác vụ chạy.
- **Worktree/nhánh:** `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`, `dot/2026-11-01-nhieu-playlist-co-ten`. Baseline `57c1be6`, commit kế hoạch `b0cfeac`. HEAD hiện tại: `git rev-parse HEAD` (commit hồ sơ Chặng 1 nằm sau mã `3336154`).
- **Báo cáo:** [2026-11-01-nhieu-playlist-co-ten-bao-cao.md](2026-11-01-nhieu-playlist-co-ten-bao-cao.md).

| Chặng / bước | Trạng thái | Commit | Bằng chứng | Việc còn lại |
|---|---|---|---|---|
| S1 `FractionalIndex` | Xong | `fa6e858` | 10 test pass | — |
| S2 `NamedPlaylistModel` | Xong | `eeb22c8` | 39 test pass | — |
| S3 `NamedPlaylistCodec` + `NamedPlaylistStore` + `NAMED_V1_ALIAS` | Xong | `e0d70c6` | 27 test pass | — |
| S4 `NamedPlaylistSession` + `TelegramPlaylistRuntime.namedSession` | Xong | `3336154` | 18 test pass | — |
| S5 gate Ni | Xong | — | Debug và Release mỗi biến thể: 190 suite, 2539 test, 21 skipped, 0 failures, 0 errors (baseline 186/2445/21/0/0; +94 đúng phần mới); assembleShareableDebug+Release BUILD SUCCESSFUL; `git diff --check` sạch | — |
| Chặng 2 S6–S10 | Chưa làm | — | — | Phiên thực thi 2 |
| Chặng 3 S11–S19 | Chưa làm | — | — | Phiên thực thi 3 |
| Chặng 4 S20–S24 | Chưa làm | — | — | Phiên thực thi 4 |

- **File chưa commit:** không có sau commit hồ sơ (log gate nằm ở `docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/`, bị git bỏ qua). Repo chính vẫn có `?? .codex-remote-attachments/` và kế hoạch UI/UX chưa commit của phiên khác, không động tới.
- **File đã chạm ngoài file mới:** `TelegramSavedPlaylistStore.kt` (+3 dòng, `NAMED_V1_ALIAS`), `TelegramPlaylistSession.kt` (+8 dòng, `namedSession` trong runtime). Không đụng class/định dạng Yêu thích, `#mvsync`, SQLite, service/Media3, package/signer/versionCode.
- **Lỗi/giới hạn còn mở:**
  1. Cờ `NAMED_ENABLED` (kế hoạch mục 8) chưa có vì phải nằm trong `TelegramPlaylistV2Session.kt`, file bị cấm. Chặng 3 (hoặc chủ dự án) chốt chỗ đặt.
  2. Chưa chạy Keystore thật với alias mới; chỉ cipher giả trong JVM.
  3. Còn hai chỗ chủ dự án cần quyết định từ kế hoạch (4.5 công tắc chung, mục 7 QA hai máy) — chưa ảnh hưởng Chặng 2.
- **Bước tiếp:** chủ dự án mở phiên thực thi mới cho **Chặng 2 (S6–S10)** bằng prompt dưới.

### Prompt cho phiên thực thi Chặng 2

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten (repo chuẩn D:\Bots\MeloVault-E91-Shareable — KHÔNG làm ở repo chính, nó đang ở nhánh tài liệu khác và có file chưa commit của phiên khác)
TASK_ID: 2026-11-01-nhieu-playlist-co-ten
Nhánh: dot/2026-11-01-nhieu-playlist-co-ten
Kế hoạch Claude đã được chủ dự án CHỐT: docs/plans/2026-11-01-nhieu-playlist-co-ten.md
Bàn giao: docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md; báo cáo Chặng 1: docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao.md
HEAD dự kiến: commit hồ sơ Chặng 1 (git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md); mã Chặng 1 kết thúc ở 3336154
Model: do chủ dự án chọn; bước tiếp tục: Chặng 2, S6 → S10
Baseline: 57c1be64436c3c364bcd8c1a5f8a8193ea52a553, commit kế hoạch: b0cfeac138e3cb0edbb4857f650795c79bd78957; Chặng 1 xong tại mã S4 3336154.
PHIÊN: 2 = code + test Ni, CHỈ Chặng 2 (S6–S10). Không làm Chặng 1 lại, không làm Chặng 3/4.
Lưu ý riêng:
- Chặng 1 đã có (đọc trước khi viết): FractionalIndex.kt, NamedPlaylistModel.kt (NamedOp, NamedPlaylistState, ListId, NamedOpType, Reg, NamedPlaylistNames), NamedPlaylistCodec.kt (encodeName/decodeName base64url KHÔNG padding, dùng lại cho tin #mvlist), NamedPlaylistStore.kt, NamedPlaylistSession.kt (NamedPlaylistSyncHost { view(account): NamedSyncView?; update(account, epoch, transform): NamedUpdateResult }), TelegramPlaylistRuntime.namedSession(noBackupDir). Test tương ứng ở app/src/test/.../data/telegram/.
- Hợp đồng model cần nhớ: applyRemote(op) idempotent, KHÔNG đụng outbox (coordinator tự acknowledge op của mình); NamedOp tự kiểm (tên 1–60 điểm mã, pos hợp lệ ≤ 48, dots ≤ 32, LCREATE phải có dot = opId = listId) và ném IllegalArgumentException — decode tin phải bắt thành Invalid; localItems không bao giờ vào op/outbox; checkpoint nằm trong NamedPlaylistState.checkpoint (SyncCheckpoint dùng lại, replayPosition như Yêu thích); thanh ghi tên/pos LWW theo (seq, opId).
- Chủ dự án đã duyệt tin #mvlist v1 (kế hoạch mục 4.4). KHÔNG sửa TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator, V1, SQLite, service/Media3, package/signer/versionCode. Class Yêu thích chỉ được đọc để bắt chước hợp đồng; helper thuần dùng chung chỉ khi giữ nguyên byte hành vi cũ và toàn bộ test cũ pass không đổi assertion (kế hoạch S7).
- Chỉ được sửa AutomaticPlaylistSync.run / SyncActionsPort / SyncServiceProvider như S8 mô tả; mọi test cũ giữ nguyên assertion.
- Gate kế hoạch mục 6: log ra docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/, đếm XML. Baseline cho Chặng 2 = kết quả Chặng 1 (xem báo cáo Chặng 1: số test/skipped/fail mỗi biến thể); không @Ignore, không sửa assertion cũ.
- Cờ NAMED_ENABLED (kế hoạch mục 8) CHƯA có: nó phải nằm cạnh V2_ENABLED trong TelegramPlaylistV2Session.kt, file mà Chặng 1 cấm sửa. Chặng 2 vẫn không sửa file đó; nếu cần cờ cho S8, tạo object cờ riêng trong file mới và ghi quyết định vào báo cáo để chủ dự án/Chặng 3 chốt chỗ đặt.
- Không ADB/Sony/Tiny/Drive ở chặng này. Màn Sony giữ OFF, ghi SONY_SCREEN_END=KHÔNG THAO TÁC.
- Gặp điều kiện dừng ở kế hoạch mục 8 → ghi BLOCKED vào bàn giao, dừng.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, *-ban-giao.md, *-bao-cao.md, kế hoạch TASK_ID. Kiểm git status, branch, HEAD, plan commit, git diff --check, agent/build đang chạy. Tree dirty không rõ nguồn hoặc HEAD không khớp bàn giao → STOP; không reset/clean/stash/đè.
2. Làm S6→S9 đúng kế hoạch mục 5 (bảng Chặng 2), commit theo mốc từng bước. Viết test đủ cột "Xong khi". Chạy gate mục 6 một lần sau S9 (S10), log ra file, chỉ ghi tổng kết, test defined/pass/skipped/fail thực và diff --check. Sau mỗi mốc cập nhật báo cáo (mục "Chặng 2", mở đầu "Thực thi: Claude Code <model> <effort nếu biết>, phiên Chặng 2") và *-ban-giao.md: bước xong/dở, file/commit, bằng chứng, lỗi/giới hạn, việc tiếp.
3. Báo cáo ≤ ~250 dòng, bảng trước (nếu vượt, tách phụ lục .md). Không đoán model/chứng cứ.
4. Commit code, báo cáo, bàn giao trên dot/2026-11-01-nhieu-playlist-co-ten. Không merge/push/public. Viết prompt đầy đủ cho phiên thực thi Chặng 3 (S11–S19) vào *-ban-giao.md theo cùng mẫu này.

Kết thúc tiếng Việt dễ hiểu ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm đường dẫn bàn giao và prompt Chặng 3. KHÔNG tự đề xuất rồi làm việc kế tiếp.
```

---

## (Lịch sử)

## Checkpoint 01/11/2026 — lập kế hoạch xong (Claude Code, Opus 5.5)

- **Vai:** lập kế hoạch. Không code, không build, không ADB/Sony, không Drive.
- **Quyết định chủ dự án (chat 01/11):**
  - phạm vi Nhiều Playlist có tên;
  - đồng bộ ngay qua kênh hiện có;
  - Playlist cũ giữ làm “Yêu thích” mặc định;
  - thêm Sắp xếp lại bài, Lưu hàng chờ thành Playlist, Bài Nhạc trên máy.
- **Đã duyệt khi giao thực thi:** file mới `tg_named_playlists_v1/` + loại tin `#mvlist v1` (kế hoạch mục 1, 4).
- **Repo/worktree:** `D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten`; nhánh `dot/2026-11-01-nhieu-playlist-co-ten`.
- **Baseline:** `57c1be64436c3c364bcd8c1a5f8a8193ea52a553` (`feature/e93-03d-y700-offline-cockpit`).
- **Commit kế hoạch:** `git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten.md`.
- **Kế hoạch:** [2026-11-01-nhieu-playlist-co-ten.md](2026-11-01-nhieu-playlist-co-ten.md). Báo cáo/sơ bộ/APK: chưa có.

| Chặng / bước | Trạng thái | Bằng chứng | Việc còn lại |
|---|---|---|---|
| Kế hoạch | Xong | file kế hoạch, commit trên nhánh | Chủ dự án giao thực thi |
| Chặng 1 S1–S5 (mô hình, lưu, session) | Chưa làm | — | Phiên thực thi 1 |
| Chặng 2 S6–S10 (`#mvlist`, coordinator, nối tự đồng bộ) | Chưa làm | — | Phiên thực thi 2 |
| Chặng 3 S11–S19 (UI) | Chưa làm | — | Phiên thực thi 3 |
| Chặng 4 S20–S24 (Tiny, Sony, Drive) | Chưa làm | — | Phiên thực thi 4 |

- **File chưa commit:** không có, sau commit kế hoạch.
- **Repo chính** `D:\Bots\MeloVault-E91-Shareable` có `?? .codex-remote-attachments/` và `?? docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md` của phiên trước. Không thuộc nhiệm vụ này, không động tới.
- **Test/build:** chưa chạy. Baseline theo HANDOFF: 2445 test / 21 skipped / 0 lỗi mỗi biến thể.
- **APK/thiết bị:** không có APK mới. Sony không thao tác; `SONY_SCREEN_END=KHÔNG THAO TÁC`.
- **Chỗ cần chủ dự án quyết định:**
  1. Kế hoạch 4.5: một công tắc tự đồng bộ chung (mặc định) hay công tắc riêng.
  2. Kế hoạch mục 7: cách QA đồng bộ hai máy — bản open trên Sony do chủ dự án đăng nhập, máy thứ hai, hoặc chấp nhận BLOCKED.
- **Rủi ro chính:** E105 (tự đồng bộ) vẫn BLOCKED nghiệm thu; nhiệm vụ này dựa trên đường đó, không sửa nó.
- **Bước tiếp:** chủ dự án mở phiên thực thi mới cho **Chặng 1** bằng prompt dưới. Phiên lập kế hoạch đã dừng, không còn tác vụ chạy.

### Prompt cho phiên thực thi Chặng 1

```text
MELOVAULT — PHIÊN THỰC THI ĐÚNG KẾ HOẠCH

Repo/worktree: D:\Bots\_worktrees\MeloVault-nhieu-playlist-co-ten (repo chuẩn D:\Bots\MeloVault-E91-Shareable — KHÔNG làm ở repo chính, nó đang ở nhánh tài liệu khác và có file chưa commit của phiên khác)
TASK_ID: 2026-11-01-nhieu-playlist-co-ten
Nhánh: dot/2026-11-01-nhieu-playlist-co-ten
Kế hoạch Claude đã được chủ dự án CHỐT: docs/plans/2026-11-01-nhieu-playlist-co-ten.md
Bàn giao: docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md; HEAD dự kiến: commit kế hoạch (git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten-ban-giao.md)
Model: do chủ dự án chọn; bước tiếp tục: Chặng 1, S1 → S5
Baseline: 57c1be64436c3c364bcd8c1a5f8a8193ea52a553, commit kế hoạch: git log -1 --format=%H -- docs/plans/2026-11-01-nhieu-playlist-co-ten.md
PHIÊN: 1 = code + test Ni, CHỈ Chặng 1 (S1–S5). Không làm Chặng 2/3/4.
Lưu ý riêng:
- Chủ dự án đã duyệt file mới tg_named_playlists_v1/ và tin #mvlist v1 (kế hoạch mục 1, 4). KHÔNG sửa TelegramPlaylistModel/Codec/StoreV2/V2Session/SyncProtocol/SyncCoordinator, V1, SQLite, service/Media3, package/signer/versionCode.
- File mới tại app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/, test tại app/src/test/java/com/bsdung/musicassistantcar/data/telegram/. Thuần JVM, không Android/IO/đồng hồ trong model.
- Chỉ thêm NAMED_V1_ALIAS cạnh alias V2 và getter session mới trong TelegramPlaylistRuntime; không đổi đường cũ.
- Gate kế hoạch mục 6: log ra docs/plans/artifacts/2026-11-01-nhieu-playlist-co-ten/, đếm XML; baseline 2445/21/0/0 mỗi biến thể; không @Ignore, không sửa assertion cũ.
- Không ADB/Sony/Tiny/Drive ở chặng này. Màn Sony giữ OFF, ghi SONY_SCREEN_END=KHÔNG THAO TÁC.
- Gặp điều kiện dừng ở kế hoạch mục 8 → ghi BLOCKED vào bàn giao, dừng.

1. Đọc CLAUDE.md mục 0, mục cập nhật đầu HANDOFF.md, docs/workflow/QUY_TRINH_3_VAI_TRO.md, *-ban-giao.md, kế hoạch TASK_ID. Kiểm git status, branch, HEAD, baseline/plan commit, git diff --check, agent/build đang chạy. Tree dirty không rõ nguồn hoặc baseline không khớp → STOP; không reset/clean/stash/đè.
2. Làm S1→S4 đúng kế hoạch mục 5 (bảng Chặng 1), commit theo mốc từng bước. Viết test đủ cột "Xong khi". Chạy gate mục 6 một lần sau S4 (S5), log ra file, chỉ ghi tổng kết, test defined/pass/skipped/fail thực và diff --check. Sau mỗi mốc cập nhật docs/plans/2026-11-01-nhieu-playlist-co-ten-bao-cao.md (mở đầu "Thực thi: Claude Code <model> <effort nếu biết>, phiên Chặng 1") và *-ban-giao.md: bước xong/dở, file/commit, bằng chứng, lỗi/giới hạn, việc tiếp.
3. Báo cáo ≤ ~250 dòng, bảng trước. Không đoán model/chứng cứ.
4. Commit code, báo cáo, bàn giao trên dot/2026-11-01-nhieu-playlist-co-ten. Không merge/push/public. Viết prompt đầy đủ cho phiên thực thi Chặng 2 (S6–S10) vào *-ban-giao.md theo cùng mẫu này.

Kết thúc tiếng Việt dễ hiểu ba đoạn: Đã làm / Chưa kiểm chứng / Rủi ro. Dòng cuối: "Đã dừng, bàn giao phiên thực thi tiếp theo." kèm đường dẫn bàn giao và prompt Chặng 2. KHÔNG tự đề xuất rồi làm việc kế tiếp.
```
