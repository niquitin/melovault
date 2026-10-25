# E96-P5B3 REWORK-05 — Sonnet self QA — 2026-10-24

**Kết luận của Sonnet:** lỗi test hủy tải đã được sửa (chỉ sửa fixture test, KHÔNG đổi code sản phẩm). Full forced Debug+Release xanh 2 lượt liên tiếp. Chờ ChatGPT nghiệm thu độc lập.

## Git
- Worktree Ni: `D:\Bots\MeloVault-E96-P5B3-Rework-01`, nhánh `dot/E96-P5B3-REWORK-01`.
- Baseline: `6e942c1962dca66429171d51d07dd5359293e875` (sạch, `git diff --check` sạch).
- Commit sửa test: `37c6279c3b1dd761058246a0325b324d11102138` (chỉ 1 file: `app/src/test/.../playback/BulkDownloadTest.kt`, +178/-2). Commit báo cáo ngay sau đó (xem `git log`).
- Không merge, không đụng source production, không đụng docs/workflow, CLAUDE.md.

## Nguyên nhân gốc
`testRetryEligibilityForFailedAndCancelled` gọi `startDownload(202)` rồi `cancelDownload(202)` ngay, không đồng bộ với worker. Stream giả 1 KB hoàn tất rất nhanh nên đôi khi worker đã commit (`OFFLINE_READY`, job bị gỡ khỏi `jobs`) trước khi cancel chạy. Khi đó `cancelDownload` trả `false` và `getDownloadStatus` trả `OFFLINE_READY` — **đúng hợp đồng sản phẩm** (xem `PinnedDownloadManager.cancelDownload`: `jobs[trackId] ?: return false`; commit và cancel cùng `synchronized(job)` nên là nguyên tử: cancel thắng → không bao giờ commit; commit thắng → cancel là no-op). Đây là flake của fixture, không phải bug sản phẩm. Không ép CANCELLED sau khi đã OFFLINE_READY.

## Sửa gì
1. `testRetryEligibilityForFailedAndCancelled`: stream của track 202 bị giữ trong `streamOpener` bằng `CountDownLatch` (opened/release) cho đến khi test đã cancel; chờ listener `onDownloadCancelled` rồi mới assert CANCELLED + không offline-ready. Phần phân loại retry giữ nguyên.
2. Test mới:
   - `testCancelMidTransferNeverCommits` — cancel giữa lúc đang đọc (stream đã mở/đăng ký); sau khi stream tới EOF vẫn CANCELLED, không có record.
   - `testCancelAfterCompletionKeepsOfflineReady` — cancel sau khi hoàn tất → `false`, vẫn OFFLINE_READY.
   - `testCancelQueuedJobBehindActiveDownload` — job QUEUED sau job đang chạy: cancel ngay → CANCELLED, không bao giờ mở stream.
   - `testCancelVersusCompletionRaceIsAlwaysConsistent` — stress 150 vòng, không đồng bộ start/cancel, executor riêng + `awaitTermination` mỗi vòng; bất biến: cancel=true ⇒ CANCELLED, không file/record; cancel=false ⇒ OFFLINE_READY.
- Không weaken assertion, không `@Ignore`, không skip mới.

## Kết quả test (một Gradle owner, `--offline --no-daemon`)
Trước khi chạy: không có tiến trình java/gradle nào khác (kiểm CIM), cây sạch.

| Lượt | Lệnh | Kết quả |
|---|---|---|
| Targeted | `testShareableDebug/ReleaseUnitTest --tests *BulkDownloadTest` | 15 test, 0 fail, 0 error, 0 skipped (cả 2 variant) |
| Full run 1 | `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --rerun-tasks` | BUILD SUCCESSFUL 4m41s; Debug = Release = **1968 tests, 0 failures, 0 errors, 21 skipped** |
| Full run 2 | test Debug+Release + `assembleShareableDebug/Release` `--rerun-tasks` | BUILD SUCCESSFUL 4m46s; **1968/0/0/21 skipped** cả 2 variant |
| Stress lặp | 3× `cleanTest…` + `--tests *BulkDownloadTest` Debug+Release | 6/6 lần 15 test, 0 fail, 0 error, 0 skipped; BUILD SUCCESSFUL |

- 1964 → 1968 (+4 test mới). 21 skipped = nhóm legacy skipped cũ (cùng số như REWORK-04), **không có skip mới**; `BulkDownloadTest` 0 skipped.
- `git diff --check` sạch; `git status` sạch sau commit.
- XML tổng hợp: `rework05-logs\xml-run1-Debug|Release`, `xml-run2-Debug|Release`. Log: `rework05-logs\full-test-forced-run1.log`, `full-test-assemble-forced-run2.log`, `targeted-bulkdownload-run1.log` (UTF-16), `stress-bulk-iter1..3.log`. (Dòng "iter $_ exit" lỗi trong output stress chỉ là lỗi echo của script PowerShell, không ảnh hưởng Gradle; cả 3 log BUILD SUCCESSFUL.)
- APK build lượt 2 (nội bộ, KHÔNG phải QA candidate, không ký Tiny, không cài): debug 33,345,924 B sha256 3fd825d8e67338a8900faaff6f6fb5a95e92fb85f9af1de86e6cbf1db37863be; release 30,620,566 B sha256 ee2e48b66478ac3f3e00f593486502e3225212038a76f2b9181e9dff64c0f3ae.

## Ổn định Gradle / Windows (chẩn đoán)
- Lỗi ChatGPT gặp (AAPT không resolve resource, không xóa được `R.jar`, Kotlin incremental cache close) khớp với **hai tiến trình Gradle cùng ghi vào `build/` của worktree** trên Windows. Trong phiên này chạy tuần tự, một owner, `--no-daemon`: 2 lượt `--rerun-tasks` + 3 lượt stress đều sạch, không phải dọn cache.
- Không xóa `build/` hay cache toàn cục, không kill tiến trình khác; không phải dùng `clean`. Quy ước đề xuất cho độc lập kiểm: kiểm tra không có java/gradle đang chạy (Get-CimInstance Win32_Process) trước, chạy đúng 1 lệnh Gradle tại một thời điểm trên worktree này.

## Thiết bị
- **Không cài lại APK**: chỉ đổi test fixture, APK QA REWORK-04 (sha 89DF19BE…4647, vc109) đã cài trên cả 2 máy vẫn là build đúng source production hiện tại. Không có QA thao tác mới, nên không có ảnh mới.
- Không gửi metadata Telegram, không tạo kênh, không bật (c), không đụng 2 bài đã lưu, không xóa dữ liệu, không ADB write. Mô phỏng QA trước đó vẫn chỉ là *QA display simulation*, **không phải** bằng chứng thay đổi Telegram từ xa thật.
- Kiểm chỉ-đọc cuối phiên: Shanling M3 Ultra (30313860180939) `mScreenState=OFF`, `mWakefulness=Asleep`, app không chạy. Sony (QV725WEQ54, qua Tiny) `Display State=OFF`, `mWakefulness=Dozing`, app không chạy. `SHANLING_SCREEN_END=OFF`, `SONY_SCREEN_END=OFF`. Không bật màn hình trong phiên này.

## Dọn dẹp
- Không còn tiến trình java/gradle (CIM = 0). File tạm ngoài repo (script splice) đã xóa; file scratchpad của phiên nằm ngoài repo. Không có marker QA nào được tạo trong phiên này; backup REWORK-04 giữ nguyên.

## Chưa kiểm chứng / giới hạn
- Delta Telegram từ xa thật hiện không cần restart: vẫn CHƯA XÁC NHẬN (chỉ mô phỏng + JVM V2 model), cần consent riêng nếu muốn.
- Audio thật: CHƯA XÁC NHẬN ÂM THANH THỰC (phiên này không phát nhạc).
- JVM test và BUILD SUCCESSFUL không chứng minh ADB/TDLib/UI thật.
- Chưa có Tiny signed QA APK mới (không cần vì không đổi production).

Đã dừng, chờ ChatGPT nghiệm thu.
