# E105 — Nghiệm thu độc lập sau REWORK 1

Nghiệm thu: Codex, phiên độc lập 28/10/2026 (+07), tiếp tục bằng gpt-6.1-sol; effort không được công cụ xác nhận.
Chủ dự án xác nhận ngoại lệ Codex nghiệm thu/cài kiểm tra, giữ nguyên `.codex-remote-attachments/`.

## 1. Kết luận hiện tại

**BLOCKED — chưa đủ điều kiện nghiệm thu để dùng đồng bộ thường xuyên.**
Hai lỗi R1/R2 của biên bản 27/10 đã được sửa và kiểm lại. ADD/REMOVE thật đã truyền giữa hai máy, nhưng khôi phục bài sau REMOVE chưa hoàn tất: Shanling phát sinh 2 thay đổi chờ gửi chưa giải thích được. Không quy kết lỗi ứng dụng khi chưa phân biệt với thao tác QA.
Hiện hai máy còn đúng hai bài nền; bài thử chưa được khôi phục. Đã dừng mọi ghi cloud, không gửi/xóa hàng đợi để làm xanh kết quả.
Bản riêng đã cài: Shanling Release, Sony Debug, cùng `121 / 0.6.96-beta`, source `449ff72`. Open Debug/Release đã cài kiểm UI trên Shanling, chưa cấu hình/đăng nhập.

## 2. Git và phạm vi

- Nhánh `dot/E105-DONG-BO-PLAYLIST`; HEAD kiểm `449ff72004c80aa223c66220f90d6af20bc494a4`; baseline `9adeeba`; kế hoạch `72c1e1e`.
- Tracked sạch, diff check sạch; thư mục ảnh untracked đã được chủ dự án cho giữ nguyên. Không thấy build khác trên Ni/Tiny lúc bắt đầu; kiểm lại HEAD khi tiếp tục không đổi.
- Đã đọc CLAUDE mục 0, HANDOFF mục đầu, BACKLOG, quy trình, kế hoạch, báo cáo và biên bản trước. Chỉ đọc kỹ phần rủi ro và test mới; không sửa production/test/quy trình.
- Ngoại lệ giữ vc121 và cấp config clone E105 mới trên Tiny đã có trong hồ sơ. Không đổi package/signing/schema/songKey/owner/session.
- Lịch sử nghiệm thu 27/10: Git `4c7b2a6:docs/plans/E105-DONG-BO-PLAYLIST-nghiem-thu.md`; bản sao riêng tại A/prior-audit-4c7b2a6.md. Kết luận REWORK cũ là lịch sử.

**A** = `docs/plans/artifacts/E105-DONG-BO-PLAYLIST/audit-20261028-0652/` (ignored, private). Không ghi đè bộ ảnh/APK phiên cũ; ảnh tổng hợp mới là A/00_tong_hop.png, 1600×1440.

## 3. Test/build độc lập

Chạy một lần mỗi ma trận trên Ni, lưu log/XML/APK trước khi đổi biến thể. Bản thường dùng `--rerun-tasks`; hai task unit test open thực thi thật.
`./gradlew.bat :app:testShareableDebugUnitTest :app:testShareableReleaseUnitTest :app:assembleShareableDebug :app:assembleShareableRelease :app:reportTelegramConfig --console=plain`
Bản open thêm `-PmeloOpen=true`. Không tính UP-TO-DATE là test mới.

| Biến thể | Tổng | Thành công | Bỏ qua | Failure/error | Build |
|---|---:|---:|---:|---:|---|
| Thường Debug | 2144 | 2123 | 21 | 0/0 | Thành công |
| Thường Release | 2144 | 2123 | 21 | 0/0 | Thành công |
| Open Debug | 2144 | 2123 | 21 | 0/0 | Thành công |
| Open Release | 2144 | 2123 | 21 | 0/0 | Thành công |

- Log A/private-gradle.log: BUILD SUCCESSFUL 5m32s, 110 task thực thi. A/open-gradle.log: BUILD SUCCESSFUL 3m25s. Cả hai exit=0.
- XML và thống kê A/private-test-summary.json, A/open-test-summary.json khớp báo cáo REWORK. Không giảm số test, không thêm skip, không sửa assertion trong phiên nghiệm thu.
- 7 test xác minh kênh mới cùng các hồi quy account/epoch/budget/late result đều xanh; status login mới xanh. JVM không thay QA Telegram thật.
- Outputs có sẵn được giữ ở A/preexisting-outputs; APK Ni config=false, không dùng cài Telegram.

## 4. Tiny và APK

- Bundle Ni→Tiny SHA-256 hai đầu: `2A473583F7FA1FB4D9598361D75B6D39168BCBE75EB66660039F99AF2DFEFD42`; bundle verify thành công, đúng HEAD `449ff72`.
- Clone mới `C:\Bots\MeloVault-E105-Audit-20261028-0652`; không sửa repo Tiny gốc/qua T:. T: chỉ đọc/sao chép artifact về Ni.
- Cấp config theo cách đã được duyệt cho E105: Copy-Item nguồn E99 vào clone mới trên Tiny; không mở/in/hash nội dung, không đưa về Ni. `reportTelegramConfig=true` bản thường; open=false đúng thiết kế.
- Gradle nền `Start-Process -WindowStyle Hidden`, log/exit ở `C:\Bots\_qa_temp\E105-449ff72-audit-20261028`. Bốn APK build thành công; overall.exit=0.
- Quét hash E104: open Debug/Release found=false, exit=0; đối chứng thường found=true, exit=1 đúng kỳ vọng. Không dùng mã QA/fixture.
- Độc lập aapt/apksigner/hash/bytes cả 4 APK; bản Ni sau chuyển khớp file Tiny. Cert tất cả `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`, ABI arm64-v8a.
- APK đang cài cũ được pull riêng để đối chiếu cert; trước cài kiểm package/vc121, model/serial và MediaSession không PLAYING. Chỉ dùng `install -r`; cả 5 lần cài báo Success.
- Chi tiết APK và link thật tại [chỉ mục APK](E105-DONG-BO-PLAYLIST-apk-test.md); metadata local A/candidate-verification.json. Chưa hash được bản tải lại từ Drive vì Drive chặn tải Release.

## 5. QA thật và điểm dừng

Ni: `30313860180939`, Shanling M3 Ultra/Shanling. Tiny: `QV725WEQ54`, XQ-AS52/Sony. Xác minh ADB/model mỗi đợt; Sony mất ADB thoáng qua hai đợt, dừng thao tác rồi kiểm lại khi kết nối phục hồi. Không dùng Sony thay Shanling.
Bài thử được chủ dự án duyệt: “Mixtape Chillout Viet Trung - Duc Kenzo”. Chủ dự án duyệt gửi mục cũ, sau đó duyệt tạm bỏ rồi thêm lại đúng bài này. Không tạo kênh, không dùng bài giả.

| Bước | Thực tế | Chứng cứ A |
|---|---|---|
| R1 open chưa đăng nhập | Debug/Release hiện “Cần đăng nhập Telegram”, cổng đồng bộ khóa; không nhập API/OTP | 34/35, 47/48 |
| R2 xác minh mới | Mở màn: chưa kiểm kênh, grant/sync/replay khóa; Tìm kênh hợp lệ mới mở; đóng/mở lại khóa và grant=0 | 12–14, 27/28, 39/40 |
| Budget 0 Shanling Debug | Giữ 1 pending, báo SEND_BUDGET_ZERO; không tự gửi | 17 |
| ADD Shanling Debug→Sony Debug | Cấp 1 lượt theo duyệt; Shanling báo Đã đồng bộ; Sony đọc budget0 nhận đúng bài, 2→3, không restart, nền nguyên | 20–24, 17-sony-debug-playlist-after.png |
| Release Shanling đọc | Cài Release giữ đăng nhập; tìm kênh và đọc budget0 báo Đã đồng bộ | 37–44 |
| REMOVE Sony Debug→Shanling Release | Bỏ đúng bài; cấp 1 lượt, Sony báo Đã đồng bộ; Shanling đọc, còn đúng 2 bài nền, không restart | 20-sony, 25–28-sony, 55-shanling |
| Khôi phục bài | Tìm 6 kết quả gần trùng tên; đối chiếu tên/nguồn/ảnh đúng bài. Sau thao tác thêm lại, UI báo 2 pending, cuối vẫn 2 bài nền | 57–62, 64; 29-sony-final-playlist.png |
| Tắt/bật sync, open auth và đủ ma trận cloud | Chưa khép chuỗi theo kế hoạch do điểm dừng; không suy Release Sony hoặc open từ bản thường | BLOCKED |

**Điểm cần điều tra trước mọi gửi tiếp:** A/62 cho 2 pending, A/64 Shanling còn 2 bài, Sony cuối 2 bài. Chưa xác định loại/mục tiêu của hai thao tác; không khẳng định đã ADD thành công, không khẳng định tự phát sinh. Cần phân biệt thao tác QA/menu sau sleep với lỗi phần mềm bằng chứng riêng. Không đọc/giải mã dữ liệu TDLib/noBackup để truy nguyên; không gửi/xóa pending hay thêm/bỏ tiếp sau khi phát hiện.
Ảnh A/63 hiển thị 0 là kết quả bộ lọc tên còn giữ; thao tác tìm kiếm sau đó cho A/64 đúng 2 bài nền. Không lấy ảnh lọc làm bằng chứng mất cả Playlist.

## 6. Bàn giao, màn hình và giới hạn

- [Ảnh tổng hợp và ảnh chính](E105-DONG-BO-PLAYLIST-hinh-nghiem-thu.md): 5 ảnh owner-only Ni N; connector xác minh shared=false và chỉ owner, bytes khớp nguồn. Không commit/upload công khai ảnh/log chứa metadata Telegram.
- Bốn APK upload owner-only Ni N, bytes/permission kiểm lại. **BLOCKED — CHƯA BÀN GIAO APK TẢI DÙNG ĐƯỢC**: tải lại Release nhận HTTP403 `cannotDownloadAbusiveFile` (Drive đánh dấu malware/spam); không coi đây là kết luận mã độc, không vượt bộ lọc, chưa kiểm SHA-256 bản tải lại.
- `SHANLING_SCREEN_END=OFF`, xác minh 12:05:40 +07: mGlobalDisplayState=OFF/mScreenState=OFF. `SONY_SCREEN_END=OFF`, xác minh 12:05:46 +07: Display State=OFF. Mọi nhóm UI dùng finally 223; không đổi timeout/brightness/AOD/mạng.
- Chưa xác nhận âm thanh thật; chưa QA open đã đăng nhập, đầy đủ chuỗi cả bốn biến thể/cả hai máy, lỗi mạng/unknown delivery/Keystore/lịch sử lớn, Y700/S25/FYT. Không coi build xanh hoặc ảnh một bước là PASS toàn nhiệm vụ.
- R1/R2 đã khép kiểm mã/test; nghiệm thu toàn E105 còn BLOCKED do pending/khôi phục chưa xong, open/ma trận thiếu, Drive và chưa có chủ dự án duyệt chứng cứ cuối.
- Không merge/push, không sửa code/quy trình, không mở nhiệm vụ kế. Chỉ commit biên bản và chỉ mục bàn giao trên dot/E105-DONG-BO-PLAYLIST rồi dừng.
