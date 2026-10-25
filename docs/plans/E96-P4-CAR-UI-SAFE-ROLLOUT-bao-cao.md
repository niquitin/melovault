# E96-P4 — Car UI & Safe Rollout — BÁO CÁO (Sonnet 5.5)

**Trạng thái: PASS CANDIDATE** (chờ ChatGPT nghiệm thu độc lập). Chưa merge, chưa cài thiết bị, chưa sang P5.

## 1. Git
| Mục | Giá trị |
|---|---|
| Nhánh | `dot/E96-P4-CAR-UI-SAFE-ROLLOUT` |
| Worktree | `D:\Bots\MeloVault-E96-P4-Car-UI` (còn giữ để nghiệm thu) |
| Baseline P3 | `10a0c6102deac2fdf07ddf34fa6d73794eadd109` (đúng HEAD P3 đã PASS, worktree P3 sạch, không bị đụng) |
| **Commit code P4** | **`6dfbe13b72444352c449c5998d01418d47a8be00`** (22 file, +2732/−22) |
| Commit báo cáo | commit docs riêng ngay sau commit code (chỉ thêm `docs/plans/E96-P4-CAR-UI-SAFE-ROLLOUT-bao-cao.md`) |
| `git diff --check` | sạch |

Commit chạy với `-c user.name="Claude Code"` (máy Ni chưa có git identity; không sửa git config).

## 2. Đã làm (theo P4-01…05)
**P4-01 Nút Lưu / Bỏ lưu một chạm trên Car Mode** — `MeloCarScreen.kt`, `MeloSavePolicy.kt`
- Hàng nút lớn dưới hàng tua −30s/+60s ở panel Đang phát (không menu, không tab Playlist, không giữ lâu). Cao = `max(minTouch, pageButton)` = **140 px tham chiếu ≈ 20,6 mm** trên mọi cửa sổ.
- Nhãn theo trạng thái **đã ghi thật**: `☆ Lưu` / `★ Bỏ lưu` (không đảo lạc quan khi đang ghi); `contentDescription` + `stateDescription` cho TalkBack.
- Dùng **đúng** `MeloScreenHost.toggleFavorite()` → `requestPlaylistToggle` → `PlaylistSessionApi.toggle` (một đường ghi V2). Không thêm DAO/file/mirror nào.
- `MeloSavePolicy.availability`: READY / LEGACY_ONLY (chưa rõ chatId/rawMessageId → chỉ lưu trên máy, cảnh báo, không bịa ID, không nhận là đủ điều kiện cloud) / LOCAL_ONLY (“Chỉ lưu được bài Telegram”) / NOT_IDENTIFIED / PLAYLIST_UNREADABLE / PLAYLIST_NOT_LOADED / NOTHING_PLAYING. Nút không hành động vẫn bấm được nhưng chỉ hiện Toast giải thích.
- `SaveToggleGuard`: khi một lần ghi đang chạy, các lần bấm thêm chỉ được đếm; sau khi xong, số lần thêm **lẻ** → đúng 1 lần toggle nữa, số **chẵn** triệt tiêu; bài khác bấm trong lúc ghi vẫn được ghi sau (không mất); ghi lỗi chỉ bỏ các lần bấm thêm của đúng bài đó. Target (songKey, chatId, rawMessageId) chốt **lúc bấm**, nên đổi bài giữa chừng không làm lệch. Guard theo từng tài khoản, thay mới khi đóng/đổi tài khoản; callback cũ không chạy tiếp.
- Cả menu bài hát cũng đi qua cùng guard (chung `requestPlaylistToggle`).
- Các chip Lưu ở Gọn/Tablet/Studio Deck dùng cùng nhãn/mô tả (`MeloSavePolicy.chipLabel/buttonState`).

**P4-02 Mô hình trạng thái trung thực** — `PlaylistSyncStatusPolicy.kt`, `TelegramPlaylistV2Session.statusFacts`
- `PlaylistStatusFacts` đọc từ **V2 đã commit** (đếm bài, outbox chưa ACK, retry, legacy chưa xác định, kênh đã gắn, `opCounter`, cảnh báo import V1, lý do không đọc được) — tính trên luồng playlist, không đọc ở UI thread.
- “Đã đồng bộ” chỉ khi **đồng thời**: build bật + người dùng bật + đã gắn kênh + báo cáo gần nhất `SyncStatus.OK` của **đúng tài khoản** + outbox 0 + không retry + `pendingLeft==0` + `opCounter` không mới hơn báo cáo. Không có Boolean “synced” lưu sẵn; phản hồi tạm của `sendMessage` không bao giờ đi vào lớp này.
- Hiển thị: chưa lưu · “Đã lưu trên máy · Chưa bật đồng bộ” · “…Chờ đồng bộ” (chỉ khi đã opt-in) · đang đồng bộ / đang lấy lịch sử chưa đầy đủ / đang gửi còn N · Đã đồng bộ · “Thử lại sau” (không hiện giờ nếu không biết) · chưa có kênh · chưa cho phép gửi · cần đăng nhập · bận · 5 loại RECOVERY · không đọc được Playlist · nhập V1 bị hoãn · ghi chú legacy/ cắt 300 bản sao V1/ bản sao cũ chưa kịp/ cần gửi lại. Bài nhạc máy: “không đồng bộ”. Không hiện số/đếm của tài khoản khác.
- Vùng trạng thái một dòng: Car (dưới tiêu đề danh sách, chỉ tab Playlist; chạm = Toast chi tiết, không hộp thoại), Điện thoại, Tablet, Gọn. Lời văn chỉ nói “thông tin danh sách”, không hứa phát/tải sang máy khác.

**P4-03 Cài đặt > Đồng bộ Playlist** — `MeloSyncSettingsActivity.kt`, `SyncSettingsController.kt`
- Lối vào: Cài đặt giao diện (nút “Mở Đồng bộ Playlist”, nằm trên cùng; Car → Cài đặt dẫn tới đây).
- Thông báo riêng tư tiếng Việt (kênh Telegram riêng, **không** mã hóa đầu-cuối, chỉ metadata, không âm thanh/token/đường dẫn, dữ liệu ở lại đám mây tới khi tự xóa).
- Ba quyền **độc lập**: (a) bật sau khi đồng ý cảnh báo (hộp thoại), (b) tạo kênh, (c) gửi lần đầu; không tích sẵn, không ép cả ba; thu hồi từng quyền và “Tắt đồng bộ” (xóa cả ba, không xóa Playlist/outbox/kênh).
- Kênh: Found/NotFound/Incomplete/Ambiguous/Unsafe/Failed/Created/NeedsPerson/Bound. Tạo kênh chỉ khi lần dò gần nhất **chứng minh vắng mặt (NotFound)** + quyền (b) + một lần xác nhận mới; xác nhận bị “tiêu” sau mỗi lần thử. Mở màn hình/xoay/resume **không gọi gì**. “Đồng bộ ngay” cần kênh đã gắn, không vượt backoff của service; “Đọc lại toàn bộ lịch sử” cần xác nhận riêng, không xóa gì.
- **Bản P4 QA: công tắc build vẫn FALSE.** Màn hình ghi “Đồng bộ đám mây chưa được kích hoạt trong bản thử nghiệm”, mọi nút khóa, `DisabledSyncActions` trả `BUILD_OFF`, **không ghi consent nào**, không tạo TdlibPlaylistSyncService nào trong code production.

**P4-04 Vòng đời** — trạng thái đọc lại từ V2 sau khởi động/crash (test f02); ghi V2 chạy trên luồng playlist, không treo Activity; không có WorkManager/receiver/vòng lặp nền mới; không đổi/xóa file V1, V2, tombstone, outbox.

## 3. Giữ nguyên
Phát nhạc/Media3, DSP/EQ, seek/giọng nói/điều hướng E95, **không** đưa waveform trở lại, bố cục hai khung 0.42/0.58 và chặn inset hiện có, mọi sửa lỗi P0–P3. `TelegramPlaylistFlags.SYNC_ENABLED = false`; `V2_ENABLED = true`.

## 4. Kiểm thử (JUnit XML mới, `--rerun-tasks`)
Lệnh: `.\gradlew.bat --offline --no-daemon :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks --console=plain` → **BUILD SUCCESSFUL**.

| Biến thể | tests | skipped | failures | errors |
|---|---|---|---|---|
| Debug | 1776 | 21 | 0 | 0 |
| Release | 1776 | 21 | 0 | 0 |

- 21 skipped có sẵn từ trước (BackgroundOfflinePlaylistSyncTest, SmartOfflinePlaylistSyncTest, PlaybackStabilitySoakTest); **0 test mới bị skip**. BulkDownloadTest không bị lỗi lần này.
- **87 test mới** (đều chạy cả hai biến thể): MeloSavePolicyTest 19 · MeloCarLayoutPolicyTest 8 · PlaylistSyncStatusPolicyTest 22 · SyncSettingsControllerTest 23 · PlaylistStatusFactsTest 14 · MeloCarLayoutSimulationTest 1 (sinh SVG).
- P0–P3 (58/81/94/126) giữ nguyên PASS, trong đó `TdlibChannelConsentTest.c24_nothingInTheAppReachesTheSyncServiceYet` vẫn PASS **không sửa** (xem §9 mục 1).
- XML: [Debug](E96-P4-evidence/test-results-debug) · [Release](E96-P4-evidence/test-results-release).
- Ghi chú lần chạy đầu: c24 FAIL vì code P4 của tôi lúc đầu tham chiếu service P3; tôi sửa **code P4** (không sửa test) rồi chạy lại toàn bộ.

Bao phủ chính: nút Lưu (ma trận availability, bất biến nhãn, a11y, nhạc máy/legacy/key sai), guard bấm nhanh (lẻ/chẵn, bài khác, lỗi, reset), ma trận layout, toàn bộ trạng thái, consent riêng rẽ + file hỏng/cụt = tắt hết, kênh ambiguous/incomplete không tạo được, kênh tạo không phản hồi không lặp, account A→B→A, 350 bài giữ nguyên (V1 chỉ 300), xóa bài cuối → file rỗng hợp lệ, outbox đầy, V2 không đọc được (file nguyên vẹn), cảnh báo nhập V1, **phản hồi tạm/mất ACK ≠ Synced**, thay đổi mới sau OK ⇒ quay lại “Chờ”, và **không có yêu cầu TDLib nào** khi flag tắt (mở màn hình, Save, đọc trạng thái, Retry — đo bằng `gateway.requests` của TDLib giả).

## 5. APK QA
| Mục | Giá trị |
|---|---|
| Đường dẫn bản nội bộ | `D:\Bots\_handoff\MeloVault\E96-CROSS-DEVICE-SYNC\E96-P4-apk\MeloVault-E96-P4-6dfbe13-shareable-debug-vc109-QA-CANDIDATE.apk` (gốc: `D:\Bots\MeloVault-E96-P4-Car-UI\app\build\outputs\apk\shareable\debug\app-shareable-debug.apk`) |
| Loại | Shareable **Debug** QA — **QA CANDIDATE — CHƯA NGHIỆM THU** |
| SHA-256 | `a5fdf2a0b25218b3fd79268bc89298cc2436158f4d5115c0a9fc10f8f08c98df` |
| Bytes | 33 329 228 |
| Package / version | `app.melovault.player` · versionCode **109** · `0.6.88-beta` (không đổi, không tăng) |
| ABI | `arm64-v8a` duy nhất |
| Chữ ký | `CN=Android Debug`, cert SHA-256 `a9b8cb6b5b0bc0400955b24dbc85801f5e8855356287caca2dcf3bb4e13db280` |
| Telegram config | **false** (API ID = 0, hash rỗng; Ni không có secret) — đăng nhập/phát Telegram không dùng được |
| Build từ | commit `6dfbe13` (HEAD sạch lúc build) |
| Release (tham khảo, không bàn giao) | `app-shareable-release.apk` sha256 `86a7f1ab1bfd51f4c6c8b376565e9103c4836a6263142d8ad1f4b1b8c4af4260`, 30 587 478 B, cùng package/versionCode/ABI, **cùng khóa Debug** |

**Không cài lên thiết bị nào.** APK này ký Debug nên **không** cài đè được Beta hiện có (khác chữ ký) và thiếu Telegram config — theo CLAUDE.md không được cài. Muốn thử trên thiết bị cần build lại tại Tiny với cấu hình/khóa đúng. Tôi **không** upload Drive/ không phát hành link (ChatGPT phụ trách).

## 6. Bằng chứng giao diện — **MÔ PHỎNG, không phải ảnh thiết bị**
- Không có ảnh chụp thật: Car Mode chỉ hiện sau khi đăng nhập Telegram (không dùng Telegram thật), APK chỉ có `arm64-v8a` còn AVD x86_64, và đề bài cấm đụng thiết bị thật → **SCREENSHOT / INSTRUMENTATION: BLOCKED**. Không dựng ảnh giả, không chạy/ dừng emulator nào (không có emulator do tôi tạo).
- Bản vẽ layout tính từ `MeloCarLayoutPolicy`/`MeloDesignTokens` (SVG, nhãn rõ “MÔ PHỎNG”): [FYT 1920×1200](E96-P4-evidence/sim-01-fyt-1920x1200.svg) · [Sony ngang 2520×1080](E96-P4-evidence/sim-02-sony-landscape-2520x1080.svg).

Ma trận layout (JUnit `MeloCarLayoutPolicyTest`, **toàn bộ là tính toán mô phỏng**, chưa đo trên máy thật):

| Cửa sổ | scale | Player pane / List pane (px) | khả dụng (px, đã trừ ngân sách thanh hệ thống) | Vừa? | Cao nút Lưu |
|---|---|---|---|---|---|
| FYT 1920×1200 (172,8 ppi) | 1,00 | 979 / 1067 | 1088 | ✔ | 140 px ≈ 20,6 mm |
| FYT firmware báo 240 ppi | 1,00 | như trên | 1088 | ✔ | như trên |
| Sony Xperia 5 II ngang 2520×1080 | 0,90 | 879 / 959 | 980 | ✔ | 126 px ≈ 20,6 mm |
| Sony dọc, M3 Ultra dọc, Y700 ngang/dọc | tính trong test | — | — | ✔ | ≥ minTouch, ≥ nút trang, ≥ 15 mm |

Tương phản (WCAG ≥ 4,5:1) kiểm cho: chữ/nút chưa lưu, accent trên accentSoft (đã lưu), chữ phụ và cảnh báo trên nền Car. Chip Lưu ở Gọn/Phone/Tablet vẫn cao đúng 48 dp. **Lề FYT chỉ ~21 px so với ngân sách giả định 96 px thanh hệ thống** — giả định, cần ảnh thật để xác nhận.

## 7. Bản đồ trạng thái & cổng
```
Build SYNC_ENABLED=false ──► LOCAL_SYNC_OFF, mọi điều khiển khóa, KHÔNG ghi consent, KHÔNG gọi TDLib (P4 QA)
Build ON (chỉ test) ─► (a) bật+cảnh báo ─► (b) tạo kênh ─► (c) gửi lần đầu   [độc lập; (b),(c) cần (a)]
   Dò kênh ─ NotFound(đã chứng minh) ─► [xác nhận mới + (b)] ─► Tạo ─► Dò lại ─► Found ─► [xác nhận] ─► Bind
   Incomplete/Ambiguous/Unsafe/Failed ─► KHÔNG tạo
   Đồng bộ ngay: cần (a) + kênh đã gắn; gửi cần (c); backoff/gate của service giữ nguyên
Trạng thái dòng: LOCAL_SYNC_OFF → (opt-in) LOCAL_PENDING/NEVER_SYNCED/SYNCING/CATCHING_UP/SENDING/OFFLINE_BACKOFF/NO_CHANNEL/NEEDS_*/RECOVERY_*/SYNCED
```

## 8. Rollback & dữ liệu
- Rollback = bỏ nhánh `dot/E96-P4-CAR-UI-SAFE-ROLLOUT` (chưa merge; không thao tác git nào ngoài thêm worktree/nhánh và 1–2 commit).
- Không đổi schema/định dạng file, không di trú dữ liệu mới, không ghi consent. Người dùng đang dùng V2 không bị ảnh hưởng; không xóa V1/V2/outbox/tombstone.

## 9. Điểm cần ChatGPT lưu ý / khuyết điểm đã biết
1. **c24 (P3) giữ nguyên**: để test này còn đúng ý nghĩa, adapter `TdlibSyncActions` (nối vào service thật) nằm ở **test source** (`SyncSettingsTestKit.kt`), và controller ghi consent trực tiếp qua `SyncConsentStore` thay vì `SyncConsentActions`. Test k23 chứng minh ghi giống hệt `SyncConsentActions`. Khi P5 được duyệt cần quyết định chuyển adapter vào production.
2. Code View/Activity (`MeloCarScreen`, `MeloSyncSettingsActivity`, nối dây trong `TelegramPreviewActivity`) **chỉ biên dịch + được phủ gián tiếp qua chính sách thuần**; không có test UI thật (không Robolectric/emulator). Cần QA bằng ảnh/thiết bị.
3. Đổi hành vi nhỏ: toast Lưu giờ là “Đã lưu trên máy · N bài trong Playlist” / “Đã bỏ lưu…”; menu bài hát đi qua guard (xếp hàng khi đang ghi); Save cho bài chỉ có trong `shownSongs` giờ được phép (key vẫn được V2 kiểm chứng).
4. Nút Lưu bị khóa khi Playlist chưa nạp xong (`PLAYLIST_NOT_LOADED`) thay vì cho bấm rồi báo “chưa sẵn sàng”.
5. “Đã đồng bộ” **không thể xuất hiện trong bản QA** (sync tắt) — chỉ kiểm bằng test với TDLib giả. Âm thanh thật, mạng thật, thiết bị thật: **CHƯA KIỂM CHỨNG**.
6. Màn Cài đặt đọc file consent ở luồng nền riêng khi mở (đọc cục bộ, không mạng); trạng thái Playlist không đọc file consent khi flag tắt.
7. Env Ni không có git identity; commit dùng `-c`.

## 10. Xác nhận an toàn
- **Không có hành động Telegram thật**: không tạo kênh, không gửi, không đọc, không đăng nhập; `SYNC_ENABLED=false`; không tạo service sync trong production.
- Không ADB/thiết bị thật, không cài APK, không merge, không P5, không đụng Private Stable/backend/TelegramSecretary/Preview Beta85 gốc/TDLib session; không đọc/in secret (`local.properties` không tồn tại ở worktree này).
- Không chạy `git reset/clean/stash/force-push`.

## 11. Dọn dẹp
Gradle chạy `--no-daemon` nên không còn tiến trình Gradle/Java của tôi; không khởi động emulator; đã đóng tab xem SVG; giữ lại APK QA, XML test, SVG mô phỏng, worktree P4. `adb.exe` có sẵn từ trước không do tôi tạo nên không đụng tới.

## 12. Hạng mục cho bác sĩ tự kiểm tra (sau khi có APK đủ cấu hình, 3–6 bước, do Sonnet/ChatGPT thực hiện qua ADB an toàn)
1. FYT/Sony ngang → Car Mode → panel Đang phát: thấy nút lớn “☆ Lưu” dưới hàng tua, không bị thanh hệ thống che.
2. Bấm 1 lần: nút đổi thành “★ Bỏ lưu” sau khi ghi xong; tab Playlist hiện đúng 1 dòng “Đã lưu trên máy · Chưa bật đồng bộ”.
3. Đổi bài rồi bấm Lưu: nút theo bài đang phát, không theo dòng vừa chọn.
4. Phát một bài “Nhạc trên máy”: nút mờ, bấm hiện “Chỉ lưu được bài Telegram”.
5. Cài đặt → Mở Đồng bộ Playlist: thấy dòng “chưa được kích hoạt trong bản thử nghiệm”, mọi nút khóa, ba ô quyền không tích được.

**Status: PASS CANDIDATE.** Đã dừng, chờ ChatGPT nghiệm thu.
