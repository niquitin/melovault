# Báo cáo thực thi 1 — Đảo thứ tự phát (S0–S5)

TASK_ID `2026-10-30-dao-thu-tu-phat` · nhánh `dot/2026-10-30-dao-thu-tu-phat` · baseline source `c6c7970` · HEAD kế hoạch `72e456d` · Ni · Claude Code (Sonnet 5.5) · 30/10/2026. Kế hoạch: [2026-10-30-dao-thu-tu-phat.md](2026-10-30-dao-thu-tu-phat.md). Quyết định chủ dự án: mặc định Q1–Q9 (file bàn giao không ghi khác).

## 1. Bảng chặng

| Chặng | Trạng thái | Commit | Kiểm chứng |
|---|---|---|---|
| S0 tái hiện Sony | Xong, tái hiện được lệch của nút ⏭ | không có (chỉ ảnh/log) | Sony XQ-AS52 `QV725WEQ54`, mục 3 |
| S1 policy + test | Xong | `f04dd86` | 18 test mới pass Debug và Release |
| S2 service | Xong | `768b3ae` | Biên dịch Debug và Release |
| S3 Activity Telegram | Xong | `31a6098` | Biên dịch Debug và Release |
| S4 Nhạc trên máy | Xong | `7da4620` | Biên dịch Debug và Release |
| S5 UI chữ + full test/build | Xong | `798b254` (HEAD source đã test) | Test + assemble Debug/Release xanh, mục 5 |
| S6 Tiny build, QA, Drive | Xong ở phiên thực thi 2 (mục 8) | HEAD `3296b05`, APK `1D61F67F…D485` | Q-1…Q-8 PASS trên Sony, có giới hạn |

(Cập nhật phiên 2) Hành vi mới đã được QA trên Sony ở mục 8; Shanling và âm thanh thật vẫn chưa kiểm chứng. Test JVM chỉ chứng minh policy thuần và chữ nhãn.

## 2. Phạm vi file

Chỉ sửa đúng các file kế hoạch nêu, không đổi schema/songKey/TDLib/package/signer/versionCode (vc121, `0.6.96-beta`):

| File | Thay đổi |
|---|---|
| `MeloShuffleOrderPolicy.kt` (mới) + `MeloShuffleOrderPolicyTest.kt` (mới) | `shuffled`, `rebase`, `toggled`, `insertNext`, `restore`; 18 test |
| `ShareablePlaybackService.kt` | Thứ tự đồng nhất cho ExoPlayer; trạng thái `telegramBase`/`queueTracks` (= thứ tự phát)/seed/`localBaseIds`; API `configureTelegramQueue(…, anchor)`, `queueTelegramNext`, `orderLocalQueue`, `currentTelegramBase`; listener `onShuffleModeEnabledChanged` (Telegram + Local), lưới an toàn `order_reset`, log `MeloShuffle` (chỉ Debug) |
| `TelegramPreviewActivity.kt` | `configureTelegramQueueFor` / `syncTelegramQueueFromService` / `queueTracksOf` / `serviceBaseSongs`; `addToUpcomingQueue`, `restorePlayingQueueIfAny`, `playbackListener.onShuffleModeEnabledChanged`, `playAll(shuffle)`, `playLocal` |
| `MeloQueuePane.kt`, `MeloQueueUiPolicyTest.kt`, `MeloScreenHost.kt` | `MeloQueueUiPolicy.caption`, 1 test, KDoc `queue()` |

## 3. S0 — tái hiện trên Sony (APK đang cài, không cài mới)

- Gate: `adb devices -l` thấy `QV725WEQ54`; `ro.product.model=XQ-AS52`, `manufacturer=Sony`, Android 12; `app.melovault.player` vc121 / `0.6.96-beta`, debuggable; không phiên phát khi bắt đầu; màn OFF trước. Bật màn bằng keyevent 224; cleanup tắt bằng 223.
- Kịch bản: Thư viện → **Đã tải · 67 bài** (sắp Tên A–Z) → phát bài hàng 2 → Đang phát → bấm **⇄ Đảo** (chip sáng, ảnh `s0_03`) → bấm ⏭ ba lần, đọc tiêu đề bài từ MediaSession sau mỗi lần.
- Kết quả: bài phát lần lượt là bài hàng 3, hàng 4, rồi bài kế theo A–Z (hàng 5 không nằm trong ảnh danh sách). **Đúng thứ tự gốc, Đảo không có tác dụng với nút ⏭ trên app** — khớp dự đoán mục 3 kế hoạch (`playAdjacent` dùng `queueSongOrder` gốc). Không có dòng log `MeloShuffle` (bản baseline chưa có, đúng kỳ vọng).
- **Chưa tái hiện được:** (a) khung hàng chờ — không tìm thấy lối vào khung này ở màn Đang phát compact trong ảnh `s0_02`/`s0_03` (`openQueue` mở khung ở Deck/Tablet hoặc `showMeloQueue`; S6 thử Deck/xoay ngang), nên chưa có ảnh nhãn "Thứ tự gốc · Đảo đang bật"; (b) chuyển bài tự nhiên ngẫu nhiên trong cửa sổ Media3 khi hết bài (bài thử dài 3 giờ 21 phút, không đợi hết); (c) ⏭ khi tắt màn/tai nghe.
- Dọn dẹp: bấm tạm dừng, `dumpsys display` → `Display State=OFF`, `mScreenState=OFF`. **SONY_SCREEN_END** ghi nhận.
- Ảnh: 9 file PNG (`s0_00`…`s0_06`) nằm trên Tiny tại `%TEMP%\dtp_s0` (thư mục `C:\Users\Lenovo\AppData\Local\Temp\dtp_s0`); **chưa chép về Ni** vì hai máy không có đường chép an toàn trong phiên này. Có Telegram PII (tên kênh/bài) nên không commit; `docs/plans/artifacts/` vốn đã `.gitignore` toàn bộ. Phiên 2 chạy trên Tiny nên dùng trực tiếp được.

## 4. Quyết định thiết kế ngoài kế hoạch (cần chủ dự án biết)

1. **Thêm API `currentTelegramBase(accountId)`** (service). Lý do: `TelegramQueueRetentionPolicy.forSelected` lấy hàng chờ cũ làm `previous`; hàng chờ cũ giờ là thứ tự phát (đã xáo). Nếu chạm hàng của khung hàng chờ khi người dùng đang duyệt danh sách khác, base mới sẽ là thứ tự xáo và "Tắt Đảo" không về được thứ tự gốc. Activity lấy `previous` từ thứ tự gốc của service.
2. `rebase` xử lý "cùng base" bằng chính nhánh "giữ phần đã định + nối bài mới xáo" (bài mới rỗng thì giữ nguyên thứ tự phát), nên không có nhánh riêng; hành vi trùng kế hoạch (test `rebaseSameBase…`).
3. `insertNext` khi `id == current` giữ nguyên thứ tự (kế hoạch chưa nói); tránh đẩy bài đang phát xuống cuối.
4. `reorderLocal` đo thời gian và ghi `ms=` trong log Debug để S6 đánh giá rủi ro giật 500 bài.
5. Số dòng trong kế hoạch lệch (file Activity ~8000 dòng); đã định vị bằng tìm kiếm. Đã đọc và **không sửa** `playAdjacent`, `localQueueItems`, `playLocalQueueAt`, `prefetchNextCachedNeighbor`, `insertCachedNeighborIfRelevant`: chúng đọc `queueSongOrder`/danh sách Media3 nay đã là thứ tự phát.
6. Ni chưa cấu hình `user.name/email` git; commit dùng `-c user.name="Claude Code" -c user.email="noreply@anthropic.com"` từng lệnh (đúng danh tính các commit trước), không đổi cấu hình.

## 5. Test và build (S5, chạy một lần trên Ni, source = `798b254`)

Log đầy đủ (không commit, `docs/plans/artifacts/2026-10-30-dao-thu-tu-phat/`): `s5_test.log`, `s5_assemble.log`; thêm `s1_test.log`, `s2/s3/s4_compile.log`. Số liệu lấy từ XML `app/build/test-results`, mới hơn lần sửa cuối.

| Biến thể | Suite | Test | Passed | Skipped | Failed | Error |
|---|---|---|---|---|---|---|
| `testShareableDebugUnitTest` | 179 | 2287 | 2266 | 21 | 0 | 0 |
| `testShareableReleaseUnitTest` | 179 | 2287 | 2266 | 21 | 0 | 0 |

- Test mới/đổi: `MeloShuffleOrderPolicyTest` 18/18 và `MeloQueueUiPolicyTest` 10/10 ở cả hai biến thể. 21 skipped là mức bình thường theo hồ sơ R1.
- `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest`: BUILD SUCCESSFUL (1m 52s). `:app:assembleShareableDebug :app:assembleShareableRelease`: BUILD SUCCESSFUL (1m 42s). `git diff --check`: sạch (exit 0).
- APK build trên Ni (không có cấu hình Telegram nên **không dùng để QA/cài**, không bàn giao): Debug 33.576.900 byte, Release 30.769.518 byte tại `app\build\outputs\apk\shareable\{debug,release}\`. APK QA phải build ở Tiny (S6).
- JVM test và BUILD SUCCESSFUL **không** chứng minh ADB/TDLib/UI/âm thanh thật.

## 6. Chưa kiểm chứng / rủi ro

| # | Mục | Mức |
|---|---|---|
| R-1 | Service giữ tối đa **2000 bài** (giới hạn cũ, `setQueue` xóa hàng chờ nếu lớn hơn). `shownSongs` tải theo trang (`offlineVisibleLimit`, nối thêm khi cuộn) nên chỉ chạm giới hạn khi danh sách đang hiện > 2000 bài; khi đó service không có hàng chờ, Đảo không đổi được thứ tự và ⏭ trên app vẫn theo thứ tự gốc (lỗi gốc còn nguyên cho trường hợp này). Chưa đổi vì kế hoạch ghi rõ giữ giới hạn; chủ dự án quyết có xử lý riêng không. | Trung bình, chỉ khi > 2000 bài hiện |
| R-2 | Chưa chứng minh bằng chạy thực rằng `UnshuffledShuffleOrder` được giữ qua `MediaController.setMediaItems` (Media3 1.11.1). Có lưới an toàn `order_reset`. Nếu Q-2 (⏭ ×3) vẫn lệch → BLOCKED theo mục 10 kế hoạch. | Trung bình |
| R-3 | Cắt cửa sổ Media3 khi bật/tắt Đảo có thể làm mất bài đã nạp (chấp nhận theo kế hoạch). Local 500 bài: remove/add chưa đo thật. | Thấp–trung bình |
| R-4 | `playAll(shuffle=true)` đặt cờ Đảo bất đồng bộ rồi gọi `configureTelegramQueue`; hai thứ tự đến trước/sau đều ra thứ tự đã xáo nhưng bài ngẫu nhiên có thể không đứng đầu khi hàng chờ cùng nguồn đã có. Chưa thử trên máy. | Thấp |
| R-5 | Âm thanh thật chưa xác nhận (S0 chỉ đọc MediaSession): **CHƯA XÁC NHẬN ÂM THANH THỰC**. | Ghi nhận |
| R-6 | Không build Tiny, không cài APK mới, không Drive trong phiên này (S6). Sony vẫn là R1c Debug vc121 chưa có thay đổi này. | Theo kế hoạch |

## 7. Điểm dừng

Không gặp điều kiện dừng mục 10 kế hoạch (không đổi schema/DB/TDLib/songKey/signer/package; HEAD, nhánh, cây sạch đúng; test cũ không hỏng). Đã dừng sau S5, bàn giao S6.


## 8. Phiên thực thi 2 — S6 (Claude Code Sonnet 5.5, 30/10/2026 ~16:00–16:35 +07)

Thiết bị: Ni (repo/Gradle/bundle), DESKTOP-HNP4GVR = Tiny (build + Sony). Không dùng Bs-Dung.local. Không sửa source. Đầu phiên: HEAD `3296b05`, nhánh đúng, cây sạch, `git diff --check` sạch, `git diff --stat 798b254..HEAD` chỉ `docs/`, không agent khác đang chạy.

| Bước | Kết quả | Bằng chứng |
|---|---|---|
| Bundle + SHA | Xong | 13.167.767 byte, SHA-256 `4313A028…F2AE`, `git bundle verify` ok; chuyển qua Drive owner-only, Tiny đối chiếu khớp |
| Clone cô lập Tiny | Xong | `C:\Bots\MeloVault-DAO-S6-IsolatedQA-20261030-3296b05`, HEAD `3296b05`, cây sạch, `798b254` có mặt |
| Build Debug Tiny | BUILD SUCCESSFUL 2m41s | `reportTelegramConfig` = `true` (không in giá trị) |
| Gate APK | Đạt | `app.melovault.player`, vc121 `0.6.96-beta`, cert `eecd3df7…ce8`, 33.576.900 byte, SHA `1D61F67F…D485`, debuggable, arm64 |
| Gate Sony | Đạt | `QV725WEQ54` XQ-AS52 Sony Android 12; vc121 đang cài, cert khớp; APK đang cài **trùng SHA/bytes** với APK vừa build → **không cần `install -r`** (không chạy lệnh cài nào) |
| QA Q-1…Q-8 | PASS (có giới hạn Q-6, Q-7) | [nghiem-thu](2026-10-30-dao-thu-tu-phat-nghiem-thu.md); ảnh [hinh-nghiem-thu](2026-10-30-dao-thu-tu-phat-hinh-nghiem-thu.md) |
| Shanling | KHÔNG THAO TÁC | `adb devices -l` trên Ni rỗng |
| Cleanup Sony | Xong | tạm dừng, `Display State=OFF`, `mScreenState=OFF`; **SONY_SCREEN_END** |
| Drive | Xong, nhãn QA CANDIDATE | thư mục + 11 file, quyền chỉ owner (xem apk-test) |

### Kết quả quan trọng

- Q-1…Q-5, Q-8: khớp kỳ vọng kế hoạch. Q-6: phím media NEXT ×2 khi `Display State=OFF` ra hàng 2, 3 của thứ tự phát; kéo thanh tua tới gần cuối bài, tắt màn, bài tự chuyển sang đúng hàng 2 (cửa sổ Media3 lúc đó chỉ 1 mục) → R-2 và lưới an toàn không bị kích hoạt. Q-7: xem giới hạn trong nghiem-thu.
- Log Debug `MeloShuffle`: toggle 25, configure 7, next_inserted 1, local_reorder 5 (`ms=1…3`, 3 bài), **order_reset 0**; không FATAL/ANR trong bộ đệm lỗi.
- Media3 `size` dao động 1→17 theo thao tác: bật/tắt Đảo cắt cửa sổ còn 1 mục như thiết kế; bài kế được nạp lại khi ⏭ hoặc prefetch.
- **Ghi nhận (không chặn):** Đảo bật sẵn + chọn bài từ danh sách thư viện cùng nguồn → giữ thứ tự xáo cũ (bài vừa chọn ở hạng 50 thay vì đầu). Đúng quy tắc `rebase` "cùng base giữ thứ tự" nhưng có thể gây bất ngờ; cần chủ dự án quyết.
- R-1 chỉ ghi nhận: chưa mở được danh sách > 2000 bài trên máy (thử 67 và 200 bài). R-3: local chỉ đo trên 3 bài.

### Sự cố / việc ngoài kế hoạch (minh bạch)

1. Trên Tiny đã có **một lần chạy S6 chưa ghi hồ sơ** (clone `MeloVault-DAO-IsolatedQA-20261030-1305`, log `C:\Bots\_qa_temp\dao-d0076c1-20261030-1250`, ~12:50–13:09, từ `d0076c1`). Không dùng làm chứng cứ, không sửa/xóa. Nó để Sony sáng màn, MeloVault đang phát suốt ~3 giờ (không ai chạm); tôi tạm dừng phát (phím media PAUSE) rồi dọn dẹp cuối phiên. APK nó đã cài trên Sony chính là bản trùng SHA nói trên.
2. Một lệnh PowerShell cổng APK lặp vô hạn, ghi ~38 MB vào `apk-gate.log`; đã dừng tiến trình và thay log bằng bản tóm tắt sạch (cùng giá trị). Không ảnh hưởng thiết bị/repo.
3. `local.properties` (Telegram) chép từ clone cô lập `…DAO-IsolatedQA-20261030-1305` sang clone mới, chỉ trên Tiny, không in giá trị; không mang về Ni.
4. Chuyển bundle qua thư mục Drive `MeloVault_QA\_transfer_dao-3296b05` (owner-only); tôi không thể xóa — để chủ dự án dọn nếu muốn.
5. Khi tìm đường chuyển bundle, tôi liệt kê gốc các Drive mount và vô tình in tên file cá nhân của chủ dự án vào kết quả lệnh; không lưu, không dùng, không đưa vào hồ sơ.
6. Tile 3 của `00_tong_hop.png` lỗi dấu tiếng Việt ở nhãn (không ảnh hưởng ảnh).

### Chưa kiểm chứng

CHƯA XÁC NHẬN ÂM THANH THỰC; Shanling; nút ⏭ thật trên thông báo/khóa màn; R-1; local 500 bài; tải APK từ Drive bằng trình duyệt; tài khoản "Drive Ni N" (connector chỉ thấy `ntdung.md@gmail.com`).
