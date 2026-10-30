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
| S6 Tiny build, QA, Drive | Chưa làm (phiên 2) | — | — |

Chưa chạy trên thiết bị sau khi sửa code: mọi hành vi mới (thứ tự phát thật, ⏭ khớp hàng chờ, tắt Đảo) **chưa được kiểm chứng trên Sony/Shanling**. Test JVM chỉ chứng minh policy thuần và chữ nhãn.

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
