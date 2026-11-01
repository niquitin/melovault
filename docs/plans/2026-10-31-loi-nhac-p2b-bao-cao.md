# Báo cáo thực thi — lời nhạc Shareable (P2-B)

Thực thi: Claude Code (Sonnet 5.5), phiên chặng A. TASK_ID `2026-10-31-loi-nhac-p2b`, nhánh `dot/2026-10-31-loi-nhac-p2b`, worktree `D:\Bots\_worktrees\MeloVault-loi-nhac-p2b`, baseline `94ab6b0`.
Kế hoạch: [`2026-10-31-loi-nhac-p2b.md`](2026-10-31-loi-nhac-p2b.md). Bàn giao: [`-ban-giao.md`](2026-10-31-loi-nhac-p2b-ban-giao.md).
Trạng thái: **CHẶNG A XONG (L0–L4). Chưa có UI, chưa có APK, chưa QA thiết bị. Chưa nghiệm thu.**

## 1. Tóm tắt

| Bước | Nội dung | Kết quả |
|---|---|---|
| L0 | Git sạch, HEAD `8bf3baa` khớp bàn giao, không java/gradle chạy | XONG |
| L1 | `MeloLyricsPolicy` + `MeloLyricsPolicyTest` | XONG, 23 test |
| L2 | `ShareableLyricsProvider` + `ShareableLyricsProviderTest` | XONG, 20 test |
| L3 | `ShareableLyricsCache` + `ShareableLyricsCacheTest` | XONG, 18 test |
| L4 | `ShareableLyricsController` + `ShareableLyricsControllerTest` | XONG, 22 test |

Commit mã: `6f274b9`. Mã nằm trong `app/src/shareable/.../data/telegram/`, test trong `app/src/test/.../data/telegram/`. Không đụng service, Media3, DB, TDLib, package, signer, versionCode, `main`.

## 2. Quyết định mục 9 của kế hoạch

Chủ dự án chỉ giao “Lời nhạc P2-B”, không trả lời khác → dùng **mặc định** cả 6 câu: LRCLIB gọi trực tiếp; tắt sẵn + hỏi đồng ý lần đầu; **không có URL/email liên hệ** nên User-Agent chỉ là `MeloVault/<ver>`; ngưỡng mixset 15 phút; có trong bản open (vẫn tắt sẵn); bỏ qua lời nhúng/`.lrc`.

## 3. Việc đã làm theo từng bước

- **L1.** `signature` = SHA-256 của `title|artist|album|giây` đã chuẩn hóa (NFC, chữ thường, gộp khoảng trắng, bỏ `feat./ft./featuring` ở nghệ sĩ). `shouldAutoFetch`: thiếu/“unknown”/“chưa xác định” → MISSING_METADATA; > 900 giây → TOO_LONG. `pickCandidate`: chỉ tự nhận khi **đúng 1** kết quả lệch ≤ 2 giây; chưa biết thời lượng thì không bao giờ tự nhận; tối đa 20, ứng viên gần nhất xếp trước. `currentLineIndex` gọi `LrcParser.findActiveLineIndex`. `userAgent` bỏ ký tự điều khiển.
- **L2.** `LyricsTransport` + `HttpUrlConnectionLyricsTransport` (kết nối 5 s, đọc 8 s, thân tối đa 512 KB, không theo redirect). Request chỉ có `track_name`, `artist_name`, `album_name`, `duration`, và `q` ở tìm thủ công; header chỉ `User-Agent`, `Accept`. 200 → bản ghi; 404 → NoMatch (không bao giờ thành “instrumental”); 429 → chờ `Retry-After` (mặc định 5 s, tối đa 600 s) + 0–1 s ngẫu nhiên và **chặn các request sau đó** đến hết thời gian; 5xx/mã lạ/IOException/JSON hỏng → TemporaryError. Cách nhau ≥ 300 ms. Log chỉ `stage=lyrics_request|response|blocked` + mã HTTP.
- **L3.** `noBackupFilesDir/melo_lyrics_v1/<owner>/<sig>.json` (đường dẫn gốc do chặng B truyền vào), ghi tệp tạm rồi `Files.move`. Hạn: có lời 90 ngày, NoMatch 3 ngày, chọn thủ công không hết hạn. Giới hạn 3000 mục/20 MB **mỗi tài khoản**, xóa theo LRU (mtime). Owner ID lạ (`../`, `/`) được băm; chữ ký lạ bị từ chối. Mọi lỗi I/O nuốt, không làm hỏng việc phát.
- **L4.** Thứ tự: đồng ý → cache → mạng → công bố qua `post`. Mỗi `request` tăng `generation`; kết quả chỉ được công bố nếu owner + generation + chữ ký bài còn đúng (nếu không ghi `stage=lyrics_drop`). Có `cancel`, `searchManual`, `choose`, `clearChoice`, `clearOwner`. Controller **không có tham chiếu tới player/queue**; mọi ngoại lệ thành `TemporaryError`.

## 4. Test và gate

Log đầy đủ ở thư mục cục bộ (bị `.gitignore`): `docs/plans/artifacts/2026-10-31-loi-nhac-p2b/A-20261101-105218/` (`A1-debug-targeted.log`, `A2-full-debug-release.log`, `A2-exit.txt`).

| Lệnh | Kết quả |
|---|---|
| `:app:testShareableDebugUnitTest --tests` 4 lớp mới | BUILD SUCCESSFUL, 83 test, 0 lỗi |
| `:app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest` | BUILD SUCCESSFUL (2 phút 49 giây) |
| Cộng XML Debug | 183 suite, **2393 test, 21 skipped, 0 failures, 0 errors** |
| Cộng XML Release | 183 suite, **2393 test, 21 skipped, 0 failures, 0 errors** |
| `git diff --check` | sạch |

2393 = 2310 (gate S4 ở baseline) + 83 test mới. Skipped 21 là các test đã bỏ qua từ trước, không thêm. `OnlineLyricsTest` (53) của lõi Personal vẫn xanh.

Test chứng minh yêu cầu của kế hoạch (mục 7):
- request không chứa chat/message/songKey/account/đường dẫn: `noTelegramIdentifierPathOrSongKeyIsInTheRequest`, `getUrlCarriesOnlyTheFourAllowedFields`;
- log không có tên bài/nghệ sĩ: `logsHoldStagesAndCodesButNoTitleOrArtist`, `logsCarryNoSongNames`;
- kết quả lộn xộn A → B → A, owner khác, đóng tab: `answersArrivingOutOfOrderOnlyShowTheLatestSong`, `answerForAnotherAccountIsDropped`, `cancelDropsTheAnswerOfAClosedTab`;
- cache bài A không dùng cho cùng tên khác thời lượng: `cacheOfSongAIsNotUsedForSameNameOtherLength`;
- lỗi không thành ngoại lệ: `anyFailureBecomesAStateNeverAnException`.

## 5. Lệch so với kế hoạch (ghi để chặng sau biết)

1. Dựng URL bằng `URLEncoder` thay cho `Uri.Builder`, vì `android.net.Uri` không chạy được trong test JVM; kết quả mã hóa giống nhau (khoảng trắng → `%20`).
2. Kế hoạch ghi `LrcParser.kt` và `LyricsModels.kt` “không import gì”. Đúng, nhưng `LyricsTrackMetadata` trong `LyricsModels.kt` có gọi `LyricsCache.computeMetadataSignature` (cùng package, không cần import). Mã mới chỉ dùng `LrcParser` và `LyricsLine`, không dùng `LyricsTrackMetadata`/`LyricsCache`/`LyricsRepository`/`CarApiClient`.
3. Giới hạn 3000 mục/20 MB áp **mỗi tài khoản**, không phải toàn cục. Tệp `.part` mồ côi sau sự cố chỉ bị xóa khi xóa cả tài khoản.
4. Đồng ý lần đầu thể hiện bằng `LyricsConsent { UNDECIDED, GRANTED, DENIED }` do chặng B cấp qua một lambda; controller chỉ đọc.
5. Bài thiếu tên/nghệ sĩ vẫn đọc cache trước, nên lời đã **chọn thủ công** hiện lại khi quay về bài (khớp Q5).

## 6. Chưa làm / chưa kiểm chứng

- **Không có UI** (L5), công tắc Cài đặt, hộp đồng ý, nối `TelegramPreviewActivity`, xóa cache khi đăng xuất: thuộc chặng B.
- **Chưa gọi mạng thật.** Chưa xác nhận LRCLIB còn trả đúng định dạng trong tài liệu (phiên lập kế hoạch 31/10 đọc được `docs.md`); chỉ có transport giả.
- Chưa build APK, chưa Tiny, chưa Sony, chưa Q1–Q6. Âm thanh thật: **CHƯA XÁC NHẬN**.
- Chưa chạy `assembleShareableDebug/Release`, `-PmeloOpen=true`, `assemblePersonalDebug` (thuộc gate L6).
- Chưa đo thời gian ghi/đọc cache trên máy thật.

## 7. Thiết bị / màn hình

Không dùng ADB, không đụng thiết bị. `SONY_SCREEN_END` = KHÔNG THAO TÁC.

## 8. Rủi ro / rollback

- Rủi ro: LRCLIB đổi API hoặc chặn → `TemporaryError`, app vẫn phát. Bản quyền lời khi đăng công khai vẫn là câu hỏi mở (LD9).
- Rollback: chặng A chỉ thêm 8 file mới và không có điểm nối vào app nên có thể `git revert 6f274b9` hoàn toàn; không có migration.
