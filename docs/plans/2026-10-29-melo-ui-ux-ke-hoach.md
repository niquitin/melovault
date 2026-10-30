# Melo — kế hoạch kỹ thuật UI/UX và tính năng sau khảo sát video

Lập kế hoạch: Codex, model/effort không xác định từ metadata phiên; ngày 29/10/2026.
TASK_ID: `2026-10-29-melo-ui-ux-ke-hoach`.
Trạng thái: **KẾ HOẠCH ĐỂ CHỦ DỰ ÁN GIAO THỰC THI — CHƯA CODE, CHƯA BUILD, CHƯA NGHIỆM THU**.

## 1. Yêu cầu và quy tắc áp dụng

Chủ dự án yêu cầu đọc lại “Melo — Kế hoạch UI/UX và tính năng, bổ sung từ video” và viết kế hoạch chi tiết để model tiết kiệm token hơn thực thi ở phiên mới. Ngày 29/10, chủ dự án yêu cầu áp dụng quy tắc mới: Codex và Claude ngang quyền; lập kế hoạch → thực thi → sơ bộ ở các phiên riêng → chủ dự án nghiệm thu chính thức. Model/effort do chủ dự án chọn; không tự phân loại nhỏ/lớn, không tự bắt đầu code.

- Nguồn yêu cầu: [bản chụp nguyên văn tài liệu định hướng](2026-10-29-melo-ui-ux-ke-hoach-nguon-video.md), SHA-256 `AA648F099D43428BFE11832C63E964271454B6E5AF18D501D040D97D161A95BA`.
- Bản chụp giữ nguyên các dòng mô tả **phiên trước**, kể cả “không commit trong phiên này”; đó là lịch sử nguồn, không phải lệnh cho phiên kế hoạch kỹ thuật mới.
- Nguồn quy trình mới: `c9a1c09:CLAUDE.md`, `c9a1c09:AGENTS.md`, `c9a1c09:docs/workflow/QUY_TRINH_3_VAI_TRO.md`, `c9a1c09:docs/workflow/PROMPT_CODEX.md`.
- Worktree này giữ source baseline và các file luật cũ tại baseline. **Đọc bản quy tắc 29/10 bằng `git show c9a1c09:<path>` trước khi nhận việc**, theo chỉ thị trực tiếp của chủ dự án. Không áp lại giới hạn cũ “Codex chỉ lập kế hoạch nhỏ”.
- Không merge nhánh tích hợp để lấy luật: diff giữa baseline và `c9a1c09` còn có thay đổi production E105. Kế hoạch không cấp quyền loại bỏ/ghi đè các thay đổi đó.
- Giữ toàn bộ luật Git, bí mật, ADB, Tiny, màn hình và nghiệm thu. Không sửa CLAUDE.md/AGENTS.md/workflow trong nhiệm vụ này.

## 2. Git baseline, worktree và bằng chứng khảo sát

| Mục | Giá trị |
|---|---|
| Baseline source | `7f3cca874e525956a10f94f8caf577c6cc80f815` |
| Worktree kế hoạch/thực thi tiếp | `C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable` |
| Nhánh | `dot/2026-10-29-melo-ui-ux-ke-hoach` |
| Repo đầu vào, chỉ đọc | `D:\Bots\MeloVault-E91-Shareable` |
| Nhánh đầu vào | `dot/2026-10-29-hoc-hoi-ui-ux-telegram-music` |
| File đầu vào có sẵn chưa commit | Tài liệu video và `.codex-remote-attachments/`; không nhận sửa/commit chúng tại repo đầu vào |
| Commit kế hoạch | Tra `git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach.md` |
| Video | Mốc quan sát kế thừa tài liệu nguồn; phiên này không xem lại video/không xác nhận âm thanh |
| Khảo sát mới | Đọc source baseline, test hiện có, đối chiếu quy trình và tài liệu chính thức |
| Test/build/thiết bị | Chưa chạy; không đánh thức hoặc cài thiết bị |

Nếu chủ dự án muốn thực thi trên baseline khác, phải đối chiếu các file trong mục 4 và phụ lục từng chặng, cập nhật kế hoạch/bàn giao trước; không mang nguyên diff áp mù. E105 có code nhưng chưa được phiên này xác minh nghiệm thu; giữ nguyên mọi kết luận và gate còn mở của E105.

## 3. Phạm vi và lựa chọn thiết kế

### Trong đợt P1

1. Điện thoại/tablet: phạm vi tìm kiếm rõ; tải dữ liệu, rỗng và lỗi có nhãn khác nhau.
2. Giữ nguồn, bộ lọc, từ khóa, vị trí cuộn khi mở/đóng Đang phát và xoay/chuyển bố cục.
3. Tablet nghe và duyệt cùng lúc: khung phát bên trái, khung thư viện bên phải; chọn nguồn ngay trong vùng duyệt, không che điều khiển phát.
4. Lưu nhanh vào **Playlist hiện hữu**; mọi màn cùng đọc trạng thái đã ghi thành công.
5. Phản hồi mở bài/đệm/chờ/lỗi; thông báo tiếng Việt, không suy phần trăm/thời gian chờ.
6. “Đã tải” phản ánh bằng chứng tệp đầy đủ còn hợp lệ; hàng chờ phân biệt đang phát/tạm dừng/đệm.
7. Tên hai dòng, metadata có thật, thời lượng dài, nút tối thiểu 48 × 48 dp; không đổi bản sắc màu Melo.

### Đợt P2, chỉ chạy khi chủ dự án giao riêng

- **P2-A:** ảnh bìa nhất quán giữa kiểu màu trên điện thoại/tablet; đã có phương án ở phụ lục.
- **P2-B:** lời nhạc Shareable. Có hợp đồng UI, thứ tự công việc và test dự kiến; **chưa đủ quyết định dịch vụ để giao code mạng/cache**. Không hiện tab rỗng “sắp có” trong P1.
- Nhiều Playlist có tên và ghim nguồn: chỉ nghiên cứu; không tạo kênh, chuyển tiếp tin nhắn hay đổi schema.
- Loại bình luận, livestream, nghe chung, AirPlay/truyền phát mới khỏi phạm vi.
- Không đổi package/signer/versionCode/build config, DB schema, songKey, owner/session, thuật toán sync Playlist, Media3/service hoặc chính sách prefetch.
- Không đổi bố cục Gọn/Shanling, Car Mode hoặc màn cũ; vẫn test hồi quy vì có lớp dùng chung.
- Không thêm tua vào thanh phát thu gọn; giữ thanh tua E103, −30/+60 giây, hẹn giờ, chất lượng thật và “Nghe tiếp” không tự phát.

### Quyết định đủ cụ thể để model không phải tự thiết kế lại

- Không thêm cột thứ ba. Ở Hai khung, nút **Nguồn** đổi vùng danh sách bài bên phải sang danh sách nguồn cuộn độc lập; chọn nguồn xong trở lại bài. Khung phát bên trái giữ nguyên.
- Bốn lối vào trong vùng thư viện: **Thư viện / Đã tải / Playlist / Nguồn**. Màu Teal dùng tab hiện có; màu thường chuẩn hóa hàng chọn hiện có. Không nhét thêm mục vào cột trái khi không đủ chiều cao.
- Thư viện/Đã tải/Playlist ở hàng điều hướng là phạm vi toàn thư viện; khi đang mở một kênh, bộ lọc trong kênh vẫn có nhãn nêu rõ nguồn. Tránh một nút mang hai nghĩa.
- Lần đầu mở nguồn mới: từ khóa rỗng, cuộn đầu. Quay lại một nguồn đã duyệt trong cùng phiên: khôi phục trạng thái của nguồn đó. Đổi query/sort bằng người dùng: cuộn đầu.
- Nút ☆/★ trực tiếp chỉ khi đủ chỗ cho thông tin bài và hai vùng bấm 48 dp; thiếu chỗ giữ thao tác trong ⋮. Không thu nhỏ vùng bấm để vừa.
- Chọn source/tab/đóng player không gọi play/pause/seek/setMediaItems. Chỉ hành động phát rõ ràng được phép đổi bài/hàng chờ.
- Lưu bài và “đã đồng bộ thiết bị khác” là hai kết quả riêng. Không biến ★ thành chứng nhận sync.
- UI “Tiếp theo” hiển thị hàng chờ hiện có của host. Không tự chỉnh thứ tự shuffle/service trong một đợt UI.
- Khi cỡ chữ tăng, giảm ảnh bìa/vùng phụ hoặc cho vùng phụ cuộn; giữ hàng điều khiển và thanh tua.

## 4. Bằng chứng source và khoảng trống

Đường dẫn rút gọn: `T = app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/`; `U = app/src/test/java/com/bsdung/musicassistantcar/data/telegram/`. Số dòng là gợi ý ở baseline, **tìm theo tên hàm** sau từng chặng.

| Vị trí đã đọc | Hiện trạng | Việc cần làm |
|---|---|---|
| `T/MeloLibraryPane.kt:onLibraryChanged`, khoảng 228 | Hint chủ yếu là tìm theo tên; khóa nhóm dùng sourceTitle; chỉ một inheritedAnchor | Phạm vi typed, khóa dùng ID, loading thật, restore theo nhóm |
| `T/MeloLibraryPane.kt:RowsAdapter,unclickable`, khoảng 552 | Có hai dòng tên/ảnh/menu; helper vô hiệu click con trừ ⋮ | Không viết lại list; bổ sung save và giữ listener riêng |
| `T/MeloTabletScreen.kt:renderQuickSwitch`, khoảng 501 | Khi mở kênh, quickPane thay playerPane, còn mini controls | Chuyển chọn nguồn vào vùng thư viện; giữ player trái |
| `T/TelegramPreviewActivity.kt:onConfigurationChanged,onSaveInstanceState`, khoảng 841 | Có phục hồi UI cũ, đóng/mở deck; anchor Melo chủ yếu nằm RAM đơn | Khôi phục UI Melo có owner/generation, không khôi phục playback bằng UI |
| `T/MeloScreenHost.kt:Snapshot,Row,NowPlaying` | Row chưa có trạng thái lưu; NowPlaying đã có favorite/saveBusy/availability | Thêm dữ liệu UI mặc định an toàn, tái dùng writer |
| `T/TelegramPreviewActivity.kt:requestPlaylistToggle,onPlaylistWriteDone`, khoảng 4709 | Một writer, SaveToggleGuard, cập nhật sau commit local, hook E105 afterSave | Nút dòng đi qua đúng đường này, không writer/sync mới |
| `T/TelegramPlayFeedbackPolicy.kt:errorText` | Chưa parse số giây FLOOD_WAIT; lỗi mở bài dùng Toast | Trạng thái typed + inline + retry có kiểm soát |
| `T/TelegramPreviewActivity.kt:notifyMeloPlayback,songIsDownloaded`, khoảng 5200 | Xóa feedback khi songKey thành current; Đã tải trả true theo mode | Phân biệt current/playing/buffering và kiểm bằng chứng offline |
| `T/MeloStudioDeck.kt:refresh,queueRow`, khoảng 407/527 | queueStatus không nhận paused; refresh theo key/tick tải | Refresh theo trạng thái, giữ cuộn, sửa nhãn |
| `T/MeloNowPlayingPolicy.kt`, `MeloComponents.kt:MeloSeekLine` | Đã có seek E103, chặn cuối bài, controls ghim | Tái dùng; regression, không viết seek mới |
| `T/MeloThemePolicy.kt:rowArtLayer`, `MeloLibraryPane` | Teal/Gọn có loader; pane dùng nhiều điều kiện c.teal | P2-A tách quyền tải ảnh khỏi màu |
| `app/src/main/.../lyrics/LyricsRepository.kt` | Có generation/cancel và constructor CarApiClient | Không nối backend Personal vào Shareable mặc định |

**Đã có, không làm lại:** tìm không dấu/A–Z; ảnh E98–101; Nghe tiếp E100; Settings E102; thanh tua E103; Playlist E96/E105. BACKLOG có dữ liệu lịch sử, ưu tiên source thực.

## 5. Thứ tự chặng và cách tiết kiệm ngữ cảnh

Chi tiết thao tác, hợp đồng dữ liệu, file, test và điểm hoàn tất: [phụ lục thực thi](2026-10-29-melo-ui-ux-ke-hoach-chi-tiet.md).

| Chặng | Nội dung | Phụ thuộc | Kết quả dừng |
|---|---|---|---|
| S0 | Kiểm Git, luật mới, ghi checkpoint, inventory tác vụ còn chạy | Chủ dự án giao thực thi | Đúng baseline và phạm vi |
| S1 | Phạm vi tìm kiếm, loading/empty, hợp đồng trạng thái duyệt | S0 | Code + test, checkpoint |
| S2 | Giữ ngữ cảnh khi quay lại/xoay màn | S1 | Code + test, checkpoint |
| S3 | Tablet nguồn trong vùng duyệt, player luôn thấy | S1–S2 | Code + test, checkpoint |
| S4 | Lưu nhanh, trạng thái lưu đồng nhất | S1, cơ chế E105 giữ nguyên | Code + test, checkpoint |
| S5a | Phản hồi mở bài/đệm/lỗi/chờ | S1–S2 | Code + test, checkpoint |
| S5b | Trạng thái tải đầy đủ/còn dùng được | S5a | Code + test, checkpoint |
| S6 | Hàng chờ, chữ lớn, kiểm hồi quy tích hợp P1 | S1–S5b | Full unit Debug/Release + build Ni, commit source |
| S7 | Tiny build cấu hình hợp lệ + QA + bàn giao APK | S6 | Candidate, hồ sơ, prompt sơ bộ |
| R1 | Phiên mới kiểm sơ bộ có chọn lọc | S7 | Biên bản sơ bộ; chủ dự án nghiệm thu |
| P2-A | Ảnh bìa mọi kiểu màu Phone/Tablet | P1 và chỉ định riêng | Candidate riêng |
| P2-B | Chốt dịch vụ lời → bổ sung plan → thực thi lời | Chỉ định/đáp án quyết định riêng | Chưa mở code ở P1 |

Gợi ý mỗi phiên code nhận **một chặng**, không đọc lại toàn bộ lịch sử/video. Chặng xong có commit và bảng bàn giao; chủ dự án có thể giao phiên kế tiếp tiếp tục cùng nhánh. Không tự mở task/chat/agent mới. Nếu quota còn ít: ghi phần đang dở và diff trước khi kết thúc, không cố làm sang chặng sau.

Mỗi phiên đọc luật → bàn giao → mục chặng → đúng file/hàm + test liên quan. Không quét toàn repo, không dán log Gradle dài, không “refactor tiện thể”. S1–S5 có thể chạy test mục tiêu trước; bắt buộc full Debug/Release ở S6 cho source tích hợp. Nếu kết thúc sớm và bàn giao candidate riêng thì chạy full gate cho source candidate đó. Chỉ chạy lại khi source đổi hoặc có lỗi/thiếu bằng chứng.

## 6. Test, build, APK và QA

### Lệnh bắt buộc trên Ni ở mốc candidate

```powershell
.\gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest --console=plain
.\gradlew.bat :app:assembleShareableDebug :app:assembleShareableRelease --console=plain
git diff --check
```

- Mỗi lệnh Gradle redirect ra log tên duy nhất trong artifacts; ghi exit code. Chỉ đọc dòng BUILD SUCCESSFUL/FAILED và tổng tests, passed, skipped, failures, errors từ XML của **lần chạy đó**. Skipped không tính passed.
- Không dùng kết quả UP-TO-DATE/cached không rõ source như một lần test mới; ghi đúng nguồn, không tự rerun mọi thứ khi bằng chứng còn hợp lệ.
- Không sửa package/signer/versionCode để vượt gate. Ni thiếu Telegram config thì không cài/test Telegram; build Tiny từ bundle.
- Tiny: bundle từ commit source đã ghi → SHA-256 hai đầu khớp → clone mới; không repo Tiny gốc, không sửa qua T:. Cấu hình giữ tại Tiny theo cơ chế được chủ dự án cấp trước; không đọc/in/hash/copy bí mật về Ni.
- Gradle Tiny chạy nền `Start-Process -WindowStyle Hidden`, log và exit code riêng dưới `C:\Bots\_qa_temp\`. Không coi tool timeout là build thất bại/thành công.
- APK: kiểm package, versionCode không giảm, cert khớp bản đang cài, config chỉ boolean=true, source SHA, SHA-256/bytes thật. MediaSession không đang phát trước cài. Chỉ `adb -s SERIAL install -r "APK_DA_KIEM"`.
- Candidate hợp lệ upload Drive Ni N owner-only vào folder riêng TASK_ID/ngày/commit, không ghi đè; xác minh owner/shared=false và URL. Drive chặn tải: **BLOCKED — CHƯA BÀN GIAO**, không né cảnh báo.
- Ghi `*-apk-test.md`, `*-hinh-nghiem-thu.md`, `*-bao-cao.md`, `*-ban-giao.md`; chưa có artifact thì không tạo URL/metadata giả.

### Ma trận thiết bị

| Môi trường | Mục đích | Giới hạn |
|---|---|---|
| Sony XQ-AS52, Tiny, serial tham chiếu QV725WEQ54 | Phone dọc/ngang, UI mới, đổi bài, Save, deck | Kiểm lại serial/model; không thay tablet hoặc Shanling |
| Shanling M3 Ultra, Ni, 30313860180939 | Hồi quy Gọn: list, ảnh, đáy, phát/lưu/seek đã có | Không áp bố cục mới lên Gọn |
| Tablet Y700 | Hai khung + dọc, nguồn, xoay, chữ lớn | Chủ dự án thử hoặc thiết bị hợp lệ được giao; thiếu → CHƯA KIỂM CHỨNG tablet thật |
| Cấu hình UI test/preview | 384×832, 832×384, 600×960, 960×600, 1280×800 dp; font 1/1.3/1.5/2 | Không đổi wm density/font thiết bị âm thầm; mô phỏng không thay test máy thật |
| Car Mode/FYT | Regression unit/layout hiện có | Không tự cài FYT; không kết luận QA vật lý |

ADB mỗi phiên phải `devices -l` trên đúng máy, `getprop ro.product.model` và manufacturer. Màn chỉ ON trước bước cần nhìn; `keyevent 224`; `finally` luôn `keyevent 223` và kiểm `dumpsys display` có `Display State=OFF`. Ghi SHANLING_SCREEN_END/SONY_SCREEN_END. Phiên không dùng máy ghi “không thao tác, chưa xác minh”, không ghi OFF giả.

### Sáu thao tác để chủ dự án thử / agent thực thi chụp bằng chứng

| Bước | Thiết bị, màn, thao tác | Kỳ vọng | Bằng chứng |
|---|---|---|---|
| QA1 | Tablet Hai khung phát A → Nguồn → chọn kênh khác → tìm B | Player A/controls vẫn thấy; A không bị thay cho tới khi bấm B; scope đúng | Ảnh trước/sau, mediaId/position không lộ PII trong báo cáo |
| QA2 | Sony/Tablet cuộn và nhập query → Đang phát → đóng → xoay dọc/ngang 3 lần | Query/filter/source/anchor giữ; không tự phát lại hoặc seek; không nhảy tới đầu | Video hoặc ảnh có đánh số, mốc thao tác |
| QA3 | Sony mở dòng chưa phát → ☆ → xem Playlist/Đã tải/Đang phát → bỏ lưu | Không phát khi chạm ☆; chỉ đổi ★ sau ghi; cùng bài cùng trạng thái; ⋮ còn dùng được | Ảnh/clip + test lỗi writer bằng fake |
| QA4 | Sony chọn bài chưa có sẵn; xem mở/đệm; test fake FLOOD_WAIT_3/error và tệp invalid ở JVM/UI test | Phân biệt đang mở/đệm/chờ/tải đủ; retry không gửi sớm; kết quả cũ không đè bài mới | UI thật nếu tình huống có sẵn; fake ghi rõ mô phỏng, không tạo flood thật |
| QA5 | Sony/Tablet phát mixset >2 giờ → tạm dừng → Tiếp theo → −30/+60/seek → tên dài/chữ lớn ở preview | Hàng chờ đúng paused/buffering; h:mm:ss; dock luôn thấy; seek giữ hợp đồng E103 | Ảnh 2 trạng thái + test biên duration |
| QA6 | Shanling Gọn mở app và duyệt/phát; kiểm Car policy bằng unit | Không tự phát lúc mở; list/ảnh/đáy/controls cũ không vỡ; không đổi Gọn/Car | Ảnh Shanling + test Car, không tuyên bố đã cài FYT |

Lưu 6–8 ảnh chính thành `docs/plans/artifacts/<TASK_ID>/00_tong_hop.png`, cạnh dài ≤1600 px; ảnh gốc private có tên/mốc duy nhất. Không commit ảnh/PII. Không tắt mạng, xóa file/cache hoặc tạo lỗi bằng thao tác bị cấm. Dùng fake cho các nhánh lỗi không thể tái hiện an toàn.

## 7. Tiêu chí hoàn tất và dừng

P1 đạt kỹ thuật khi từng tiêu chí S1–S6 trong phụ lục có bằng chứng; test Debug/Release không failures/errors; build hợp lệ; APK đúng source; QA được phân rõ thật/mô phỏng/chưa kiểm. Không ép bỏ test để xanh. Không dùng BUILD SUCCESSFUL chứng minh UI, Media3 PLAYING chứng minh âm thanh hoặc đã tải chứng minh offline thật.

- Âm thanh thật: **CHƯA XÁC NHẬN** nếu không có nguồn nghe độc lập.
- Offline mất mạng: **CHƯA KIỂM CHỨNG** nếu chưa có kịch bản hợp lệ; không bật airplane/tắt mạng.
- Tablet thiếu máy: ghi giới hạn ảnh hưởng mục tiêu trọng tâm, không coi Sony ngang là đã QA Y700.
- Lời nhạc/nhiều Playlist: không ghi hoàn thành chỉ vì P1 xong.
- Chính thức: **CHỜ CHỦ DỰ ÁN NGHIỆM THU**; chỉ ghi PASS đúng commit/APK/phạm vi sau lời ĐẠT. Sơ bộ dùng kết luận riêng của quy trình 29/10.

**Dừng phần bị chặn** khi: baseline/file thay đổi lạ, agent khác sửa code, cần sửa service/DB schema/security/permission/signing, source identity không xác minh được, API/lyrics chưa chốt, test phát hiện lỗi ngoài phạm vi, hoặc gate APK/ADB/Drive thất bại. Ghi nguyên nhân, file/hàm, bước đang dở và lựa chọn vào bàn giao; không tự chữa bằng mở rộng kiến trúc.

**Rollback an toàn:** commit riêng từng chặng; trước khi commit có thể sửa lại đúng phần mình vừa viết bằng patch có đối chiếu, không reset/restore đè. Sau commit, nếu chủ dự án giao rollback thì dùng commit đảo ngược có chọn lọc theo thứ tự phụ thuộc. Không tự hạ version/cài APK cũ, không xóa dữ liệu. Giữ code/source/artifact cũ để truy vết.

## 8. Bàn giao và bước kế tiếp

Đọc [checkpoint và prompt thực thi](2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md). Phiên kế tiếp chỉ bắt đầu **S0–S1** nếu chủ dự án giao theo prompt đó; không coi kế hoạch là lệnh triển khai tất cả P1/P2. Các chặng còn lại đã được phân tích để tiếp tục không cần thiết kế lại.

Đã làm: đối chiếu tài liệu video, source và quy tắc mới; viết kế hoạch cùng phụ lục, snapshot nguồn và checkpoint.
Chưa kiểm chứng: runtime UI, audio, offline thực, hiệu năng, APK và thiết bị.
Điểm dừng: tài liệu; không code/build/cài/merge/push.

Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.
