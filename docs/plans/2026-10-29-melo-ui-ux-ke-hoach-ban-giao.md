# Bàn giao — Melo UI/UX sau video

TASK_ID: **2026-10-29-melo-ui-ux-ke-hoach**.

## Checkpoint hiện tại — S0–S1, 29/10/2026, sau test cuối 11:07:31 +07

**S0–S1 đã xong code/test mục tiêu; chưa QA UI hoặc tạo candidate. Phiên thực thi dừng sau commit checkpoint này.** Không dùng prompt S0–S1 lịch sử bên dưới để làm lại; không coi checkpoint này là lệnh triển khai S2.

| Mục | Giá trị đã kiểm |
|---|---|
| Vai/công cụ | THỰC THI, Codex; model/effort không xác định từ metadata, không tự đổi |
| Worktree | `C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable` |
| Nhánh | `dot/2026-10-29-melo-ui-ux-ke-hoach` |
| Baseline source | `7f3cca874e525956a10f94f8caf577c6cc80f815` |
| Commit kế hoạch / HEAD trước sửa | `f912ef5eca8574deb304872af73a2f7ad5856f95` |
| Source S1 / HEAD tiếp nhận | Commit chứa code và checkpoint này; lấy hai SHA bằng lệnh bên dưới, sau đó kiểm HEAD thực |
| Báo cáo | [Báo cáo S0–S1](2026-10-29-melo-ui-ux-ke-hoach-bao-cao.md) |
| Quy tắc áp dụng | Bốn file luật 29/10 tại `c9a1c09`, đọc bằng Git; không merge nhánh chứa source E105 khác baseline |
| Trạng thái chính thức | **CHỜ CHỦ DỰ ÁN NGHIỆM THU**; chưa có kết luận sơ bộ/PASS |

~~~powershell
git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md
git log -1 --format=%H -- app/src/shareable/java/com/bsdung/musicassistantcar/data/telegram/TelegramPreviewActivity.kt
git status --short
git branch --show-current
git rev-parse HEAD
git log -5 --oneline
git diff --check
~~~

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| S0 | Xong | Cây sạch trước sửa, đúng HEAD/baseline; không có build/agent tham chiếu worktree; phiên kế hoạch đã dừng |
| S1 policy/adapter | Xong code | Bảy scope, ID nguồn, initial/update/ready/error, count null, guard owner/generation/lifecycle, retry đúng scope |
| S1 metadata | Xong code | Nghệ sĩ thật tách Kênh; thư mục LOCAL có nhãn; opt-in list Phone/Tablet, mặc định Gọn/Car giữ cũ |
| S1 test | Xong mục tiêu | Debug 220 passed/0 skipped/0 failed/0 errors; Release cùng kết quả, 14 suite/biến thể |
| S2–S6 | Chưa làm | Chỉ thực thi khi chủ dự án giao chặng; S6 có full unit/assemble tích hợp |
| S7 / sơ bộ / chính thức | Chưa làm | Candidate/build/QA/Drive rồi phiên sơ bộ riêng; chủ dự án nghiệm thu |

- Hai bổ sung phạm vi được chủ dự án xác nhận trực tiếp trong hội thoại 29/10: (1) tối thiểu `MeloLocalLibrary.kt` + test; (2) cờ kết quả đọc `LocalMediaRepository.kt` + test. Đã gỡ hai BLOCKED về lỗi LOCAL bị che, không đổi truy vấn/cache/hợp đồng trả về cũ.
- Phần giao nộp: 7 file production (policy mới, host, Activity, pane, components, local library, repository), 3 file test (policy mới, local library, repository), báo cáo và file bàn giao này. Không có code còn dở; các file kế hoạch/luật giữ nguyên. Tất cả phần việc được commit cùng checkpoint; khi tiếp nhận phải kiểm status thực, không nhận file lạ.
- Lượt cuối: 11:07:11–11:07:31 +07, exit 0, BUILD SUCCESSFUL in 19s. Log `artifacts/2026-10-29-melo-ui-ux-ke-hoach/s1-targeted-20261029_110711.log`; XML riêng `s1-results-20261029_110711/`; manifest `s1-source-20261029_110711.txt`, SHA-256 `022977A92D683B1E473838AA437F7D082052F9E5822F14173F99850998FF1951`. Đã kiểm từng hash source/test sau test, tất cả khớp; sau đó chỉ sửa hồ sơ.
- Lượt đầu 219 passed mỗi biến thể, exit 0; log/XML/manifest `20261029_110215` vẫn giữ. Sau lượt đầu có chỉnh giữ rows qua scan/paging và test callback; chỉ lượt cuối đại diện source bàn giao.
- Không có wrapper/test worker/build/ADB nền đang chạy tiếp. Gradle daemon PID 27832 và Kotlin daemon PID 27228 còn thường trú sau lệnh test đã exit; không phải candidate đang build. Một sub-agent chỉ đọc kiểm kê test đã kết thúc; agent chính là người duy nhất sửa file.
- Commit đầu bị từ chối vì thiếu danh tính Git; ba commit gần nhất xác minh cùng `Codex <codex@openai.com>`, dùng lại bằng tùy chọn `git -c` chỉ cho lệnh commit, không thay cấu hình global/local. Source test không đổi.
- Không tạo/cài APK, không ADB/Tiny/Drive, không có ảnh/video/00_tong_hop.png; không xác nhận app đang cài. SHANLING_SCREEN_END và SONY_SCREEN_END: **không thao tác, chưa xác minh trạng thái màn**.
- Chưa chứng minh runtime thường/Teal, tablet thật, hiệu năng, audio/offline; test JVM không thay QA UI. Chưa full suite/assemble; S1 chưa là candidate. E105 không được nghiệm thu bằng nhiệm vụ này.
- Bước chưa làm đầu tiên trong kế hoạch là S2 giữ ngữ cảnh duyệt; chỉ đọc phụ lục S2 và source cần thiết khi chủ dự án giao phiên thực thi mới. Không tự chạy S2–S7/P2, sơ bộ, merge/push hoặc sửa quy trình.

Đã dừng, bàn giao phiên thực thi tiếp theo.

Phần bên dưới là **lịch sử phiên lập kế hoạch**, không thay checkpoint hiện tại ở trên.
Ngày: 29/10/2026. Người lập: Codex; model/effort không xác định từ metadata.
Vai đã thực hiện: **lập kế hoạch**, không code/build/QA.

## 1. Đọc nhanh và quyết định còn hiệu lực

- Chủ dự án giao viết kế hoạch chi tiết để model tiết kiệm token hơn lập trình ở phiên mới; sau đó yêu cầu áp dụng quy tắc mới 29/10 và tiếp tục phần tài liệu bị gián đoạn.
- Codex/Claude ngang quyền, không cố định model theo loại nhiệm vụ; chủ dự án chọn model/effort và chặng.
- Không tự chuyển từ lập kế hoạch sang code. Phiên thực thi kết thúc bằng bàn giao phiên sơ bộ mới; chủ dự án nghiệm thu chính thức.
- Ưu tiên Phone/Tablet; giữ Melo, Android Views; Gọn/Car chỉ hồi quy.
- P1 gồm nguồn/tìm kiếm/ngữ cảnh/lưu nhanh/trạng thái phát–tải/hàng chờ.
- P2-A ảnh bìa theo màu có phương án nhưng chưa được giao code. P2-B lời nhạc phải chốt dịch vụ và chính sách dữ liệu/cache trước. Nhiều Playlist có tên, ghim nguồn chỉ nghiên cứu.

## 2. Worktree, baseline, commit

| Mục | Giá trị |
|---|---|
| Worktree tiếp tục | C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable |
| Nhánh | dot/2026-10-29-melo-ui-ux-ke-hoach |
| Source baseline | 7f3cca874e525956a10f94f8caf577c6cc80f815 |
| HEAD lúc bắt đầu/lúc khảo sát | Cùng source baseline trên |
| Commit kế hoạch/checkpoint | Commit chứa file này, tra bằng lệnh dưới; tránh SHA tự tham chiếu |
| Source dùng build | Chưa có build của nhiệm vụ này |
| Repo đầu vào | D:\Bots\MeloVault-E91-Shareable, chỉ đọc; không lập trình tại nhánh đầu vào |

~~~powershell
git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md
git log -1 --format=%H -- docs/plans/2026-10-29-melo-ui-ux-ke-hoach.md
git status --short
git branch --show-current
git rev-parse HEAD
git log -5 --oneline
git diff --check
~~~

Khi nhận S0–S1 lần đầu, HEAD phải là commit tài liệu đã bàn giao; diff từ 7f3cca8 chỉ có bốn tài liệu liệt kê bên dưới. Nếu có code mới/HEAD lạ, đối chiếu checkpoint và chủ dự án trước, không reset/clean/stash/restore.

**Quy tắc mới không nằm trong working file tại baseline này.** Chủ dự án đã trực tiếp yêu cầu áp dụng bản 29/10. Đọc bằng Git, không merge nhánh tích hợp:

~~~powershell
git show c9a1c09:CLAUDE.md
git show c9a1c09:AGENTS.md
git show c9a1c09:docs/workflow/QUY_TRINH_3_VAI_TRO.md
git show c9a1c09:docs/workflow/PROMPT_CODEX.md
~~~

Nhánh c9a1c09 có production khác E105 so với 7f3cca8; không lấy cả nhánh để “đồng bộ luật”. Nếu ref luật không còn resolve được, báo thiếu nguồn, không tự quay lại phân vai cũ.

## 3. Bộ hồ sơ và checkpoint

| File | Vai trò / trạng thái |
|---|---|
| [Kế hoạch chính](2026-10-29-melo-ui-ux-ke-hoach.md) | Phạm vi, baseline, lựa chọn UI, thứ tự chặng, build/QA/rollback |
| [Phụ lục thực thi](2026-10-29-melo-ui-ux-ke-hoach-chi-tiet.md) | File/hàm, hợp đồng dữ liệu, bước nhỏ, test, điều kiện hoàn tất theo S0–S7 và P2 |
| [Nguồn video](2026-10-29-melo-ui-ux-ke-hoach-nguon-video.md) | Snapshot nguyên văn, chỉ đọc khi cần truy nguồn yêu cầu |
| File bàn giao này | Checkpoint, phạm vi kế tiếp và prompt copy dùng ngay |

| Bước | Trạng thái | Bằng chứng / việc còn lại |
|---|---|---|
| Đọc luật, kiểm Git | Xong | Baseline 7f3cca8; quy tắc c9a1c09; worktree riêng |
| Đọc tài liệu nguồn | Xong | SHA-256 snapshot khớp bản D: |
| Khảo sát source | Xong trong phạm vi lập kế hoạch | Mục 4 kế hoạch; các hàm cụ thể trong phụ lục |
| Viết plan/phụ lục | Xong | Hai file có thật, không chỉ nội dung chat |
| Tạo snapshot/bàn giao | Xong | Bộ bốn tài liệu này |
| S0–S6 code/test | Chưa làm | Chờ phiên thực thi do chủ dự án mở |
| S7 build/QA/APK | Chưa làm | Không có candidate mới, không có link Drive |
| Sơ bộ/chính thức | Chưa làm | Không có PASS hoặc kết luận sơ bộ |
| P2-B | Chưa đủ quyết định để code | Năm điểm chốt trong phụ lục P2-B |

## 4. File chưa commit, tác vụ và thiết bị

- Phần viết trong phiên này chỉ bốn file docs/plans/2026-10-29-melo-ui-ux-ke-hoach*.md liệt kê ở mục 3; được commit cùng đợt bàn giao kế hoạch. Sau commit, chuẩn nhận việc là cây sạch; kiểm thực tế bằng Git, không suy từ câu này.
- Repo D: vẫn có sẵn .codex-remote-attachments/ và docs/plans/2026-10-29-hoc-hoi-ui-ux-telegram-music.md chưa tracked. Không sửa/xóa/commit chúng; snapshot mới được tạo trong worktree.
- Snapshot nguồn SHA-256: AA648F099D43428BFE11832C63E964271454B6E5AF18D501D040D97D161A95BA.
- Git blob của snapshot: 1a6804a83df672c333b6f90d928e4f457cadaa7a. SHA-256 trên là byte lúc sao chụp; checkout Windows có thể chuẩn hóa LF/CRLF, khi đó đối chiếu blob để phân biệt đổi xuống dòng với đổi nội dung.
- Không có tác vụ build/test/ADB/Tiny do phiên này khởi chạy. Kiểm process Java/javaw/Gradle tại thời điểm hoàn thiện không thấy tiến trình tương ứng; phiên nhận vẫn phải kiểm lại.
- Không dùng sub-agent; không mở phiên code hoặc nhắn agent khác.
- Agent lập kế hoạch kết thúc sau commit; chủ dự án giao phiên mới sau khi nhận lời kết. Không có công việc nền của phiên này.
- SHANLING_SCREEN_END: không thao tác thiết bị, chưa xác minh trạng thái màn.
- SONY_SCREEN_END: không thao tác thiết bị, chưa xác minh trạng thái màn.
- Không tạo APK/ảnh QA; không xác nhận bản đang cài. Báo cáo thực thi/APK/sơ bộ tạo khi đến đúng giai đoạn, không tạo hồ sơ giả.

## 5. Rủi ro, giới hạn và bước tiếp

1. E105 trong baseline chưa được phiên này xác minh ĐẠT; không thay kết luận lịch sử hoặc lấy plan UI làm bằng chứng nghiệm thu sync.
2. Restore UI phải dùng ID + owner/generation; deep pagination sau process recreation có giới hạn đã ghi ở S2.
3. Đã tải cần phân biệt cached path và completion thật. S5b chỉ adapter UI/facts, không sửa cache/service; thiếu facts thì báo điểm chặn.
4. Queue hiện map từ queueSongOrder; nếu cần sửa service/order thì tách quyết định, không lén mở rộng S6.
5. LRCLIB docs trả HTTP403 khi mở trong phiên khảo sát; không coi hợp đồng dịch vụ đã được kiểm chứng.
6. Chưa có QA thiết bị/video/audio/offline/hiệu năng mới. Chủ dự án được đọc kế hoạch nhưng đó không phải nghiệm thu APK.
7. Bước tiếp chính xác: **phiên thực thi mới nhận S0–S1**, làm đúng scope loading/search/metadata, ghi checkpoint và dừng. Không làm S2/P2 khi prompt chỉ giao S1.

## 6. Prompt thực thi S0–S1 — copy sang phiên mới

~~~text
MELOVAULT — THỰC THI ĐÚNG KẾ HOẠCH, CHẶNG S0–S1

TASK_ID: 2026-10-29-melo-ui-ux-ke-hoach
Worktree: C:\Users\ntdun\.codex\worktrees\melo-ui-ux-plan\MeloVault-E91-Shareable
Nhánh: dot/2026-10-29-melo-ui-ux-ke-hoach
Baseline source: 7f3cca874e525956a10f94f8caf577c6cc80f815
Kế hoạch: docs/plans/2026-10-29-melo-ui-ux-ke-hoach.md
Chi tiết: docs/plans/2026-10-29-melo-ui-ux-ke-hoach-chi-tiet.md, chỉ S0–S1
Bàn giao: docs/plans/2026-10-29-melo-ui-ux-ke-hoach-ban-giao.md
Commit kế hoạch/HEAD dự kiến: commit chứa bộ bốn file tài liệu;
xác định bằng git log -1 --format=%H -- <file bàn giao>, kiểm diff với baseline.
Model/effort: dùng lựa chọn của tôi trong phiên này; không tự đổi.

Tôi áp dụng quy tắc 29/10: Codex và Claude ngang quyền, không khóa model
theo nhỏ/lớn; các vai ở phiên riêng. Working files luật ở baseline còn cũ:
đọc git show c9a1c09:CLAUDE.md, c9a1c09:AGENTS.md và quy trình/PROMPT_CODEX
cùng ref, không merge nhánh tích hợp để lấy quy tắc.

Phiên lập kế hoạch đã dừng, không có build/ADB nền do phiên đó chạy.
Kiểm Git/agent và đối chiếu bàn giao trước khi sửa.
Tôi giao thực thi S0–S1: scope tìm kiếm, loading/empty/error, metadata dòng.
Không tự làm S2–S7 hoặc P2, không refactor ngoài phạm vi.

Đọc kế hoạch chính một lần, sau đó chỉ file/hàm và test cần cho S1.
Tạo báo cáo thực thi, cập nhật bàn giao sau từng mốc và khi sắp hết quota.
Chạy test mục tiêu Debug/Release cho phần đổi; ghi passed/skipped/failed/errors,
log và source tương ứng. S1 chưa phải candidate cuối nên không cài thiết bị.
Đừng nhận BUILD SUCCESSFUL là QA UI; full gate candidate theo mục 6 kế hoạch.

Giữ package/signer/versionCode, DB/schema, songKey, owner/session,
service/playback và thuật toán sync/cache. Gặp thiếu phạm vi/HEAD lạ thì
ghi BLOCKED đúng phần, không đoán, không reset/clean/stash.
Commit đúng phần S1 + hồ sơ rồi dừng và bàn giao chặng chưa làm.
~~~

## 7. Prompt sơ bộ — chỉ dùng sau S7

Phiên thực thi cuối phải điền **source SHA, APK SHA-256, link và phạm vi thật** vào prompt sau; các ô chưa có dữ liệu không được dùng để bắt đầu kiểm APK:

~~~text
MELOVAULT — NGHIỆM THU SƠ BỘ NHANH, PHIÊN MỚI
TASK_ID: 2026-10-29-melo-ui-ux-ke-hoach
Worktree/nhánh: như bàn giao đã cập nhật.
Source SHA: <điền sau thực thi>; APK SHA-256 và link: <điền sau S7>.
Đọc luật 29/10, bàn giao, kế hoạch/báo cáo và hồ sơ APK/ảnh.
Kiểm diff và các file rủi ro đã liệt kê; chỉ chạy lại test/QA khi có lý do.
Không sửa code/build mới; ghi kết luận sơ bộ và giới hạn vào
docs/plans/2026-10-29-melo-ui-ux-ke-hoach-nghiem-thu-so-bo.md.
Không ghi PASS chính thức; bàn giao chủ dự án nghiệm thu.
~~~

Đã dừng. Mở phiên mới, chọn model thực thi tiết kiệm hơn theo lựa chọn của chủ dự án.
