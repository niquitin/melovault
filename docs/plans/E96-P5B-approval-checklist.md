# E96-P5B — PHIẾU DUYỆT CỦA CHỦ DỰ ÁN (chưa có mục nào được thực hiện)

Nguồn: `E96-P5A-READINESS-REPORT.md` · mã P5-A `8f751064effb9265b7409ca0444b2c4db1c70f33` (nhánh `dot/E96-P5-PHYSICAL-GATE`, SYNC đang **TẮT**).
Mỗi mục là một quyết định **riêng**. Không mục nào ngầm đồng ý mục khác. Thiếu chữ ký ⇒ Claude không làm và ghi `WAIT_OWNER_APPROVAL`.

## Tình trạng thiết bị lúc P5-A (chỉ đọc)
| Máy | Serial | Ở đâu | Android | Beta đang cài | Cert đang cài |
|---|---|---|---|---|---|
| Sony Xperia 5 II (XQ-AS52) | `QV725WEQ54` | Tiny | 12 | vc109 `0.6.88-beta` | `eecd3df7…fce8` |
| Shanling M3 Ultra | `30313860180939` | Ni | 10 | vc109 `0.6.88-beta` | `eecd3df7…fce8` (giống Sony) |
| FYT 7870 | — | không kết nối | — | — | — |

**P5 PASS thật cần ≥ 2 thiết bị cùng một tài khoản Telegram.** Sony + Shanling đủ cho đồng bộ hai chiều; FYT chưa có ⇒ phần Car Mode trên xe là *PARTIAL/DEFERRED*.

---
## QUYẾT ĐỊNH 1 — Build bản thử đúng chữ ký (trên Tiny)
**Đề xuất:** (a) chuyển mã bằng git bundle `E96-P5A-8f75106.bundle` (SHA-256 `ddf38265605bb2496d59395ba14257b3942faf506a86f430131f8c035ec3b789`) vào **clone mới** `C:\Bots\MeloVault-E96-P5-Physical-QA` trên Tiny (không đụng cây E93, Private Stable, backend); (b) gắn cấu hình Telegram cục bộ cho cây mới bằng quy trình DPAPI có sẵn — **không in/ copy giá trị ra ngoài Tiny**; (c) chạy `gradlew reportTelegramConfig` ⇒ phải ra `telegram.config.valid=true`; (d) build Debug; (e) kiểm cert = `eecd3df7…fce8`, package `app.melovault.player`, **versionCode 109 (giữ)**, ABI arm64; (f) chạy `tools/qa/p5-device-preflight.ps1` với đúng thông số bản build.
**Lý do giữ vc109:** cài `-r` cùng versionCode là cập nhật hợp lệ và cho phép khôi phục về Beta cũ **không cần hạ version** (cấm `-d`, cấm gỡ cài).
**Chọn:** ☐ Đồng ý đúng như trên ☐ Đồng ý nhưng nâng versionCode lên ____ (rollback sẽ cần phương án khác) ☐ Không

**Cần thêm (code, trong P5-B, KHÔNG nằm ở P5-A):** bản thử phải có `SYNC_ENABLED=true`. Đề xuất: nhánh P5-B riêng, bật cờ ở đó (hoặc qua thuộc tính Gradle mặc định `false`), commit riêng, **không** merge vào nhánh có cờ tắt.
☐ Đồng ý nhánh P5-B bật cờ chỉ cho bản thử ☐ Không

## QUYẾT ĐỊNH 2 — Cài bản thử lên thiết bị
Phương pháp duy nhất: `adb -s <serial> install -r <apk>` (cập nhật tại chỗ, dữ liệu giữ nguyên). **Cấm** `uninstall`, `pm clear`, `-d`, đổi package id, xóa cache/TDLib.
Trước khi cài: kéo bản sao `base.apk` đang chạy của đúng máy, ghi SHA-256 (bản khôi phục); kiểm tự động (chữ ký trùng, không hạ version, ABI, Telegram config hợp lệ, serial + model đúng); **bất kỳ cổng nào HALT ⇒ dừng, báo, không làm cách khác**.
| Thiết bị | Serial | Chọn |
|---|---|---|
| Sony Xperia 5 II | `QV725WEQ54` | ☐ Cho phép cài ☐ Không |
| Shanling M3 Ultra | `30313860180939` | ☐ Cho phép cài ☐ Không |
| FYT 7870 | — | ☐ Không cài (không tự cài FYT) |

## QUYẾT ĐỊNH 3 — Điều khiển màn hình / cảm ứng (theo từng máy)
Chỉ bật màn khi đang test, sau đó trả về trạng thái ban đầu (Sony đang **sáng** từ trước ⇒ không tự tắt nếu không phải tôi bật). Chỉ chạm khi app MeloVault đang ở tiền cảnh; không bấm BACK ở màn gốc.
☐ Sony ☐ Shanling ☐ Không

## QUYẾT ĐỊNH 4 — Tài khoản Telegram dùng để thử
Tài khoản: ______________________ (chủ chọn; Claude không đọc OTP/mật khẩu, chủ tự đăng nhập trên máy).
☐ Dùng tài khoản này cho **cả hai** máy ☐ Không

## QUYẾT ĐỊNH 5 — TẠO MỘT kênh Telegram thật
Nội dung đồng ý hiển thị trong app (rút gọn):
> "Playlist sẽ lưu trong một kênh Telegram RIÊNG của bạn, KHÔNG mã hóa đầu-cuối. Chỉ có mã bài/ tin nhắn đã lưu và nhật ký thêm/bỏ — không âm thanh, không mật khẩu/khóa, không đường dẫn tệp. Dữ liệu ở lại đám mây Telegram tới khi bạn tự xóa kênh."

Điều kiện bắt buộc: tối đa **1** kênh riêng tên "MeloVault Sync"; chỉ sau lần dò **chứng minh chưa có kênh** (NotFound); Incomplete/Ambiguous/Unsafe/Failed ⇒ **không tạo**; mỗi lần tạo cần một lần bấm xác nhận mới; không tự tạo khi mở màn hình/ khởi động lại.
☐ Cho phép tạo tối đa 1 kênh ☐ Không
Hoàn tác: ứng dụng **không** xóa kênh; chủ tự xóa kênh trong Telegram nếu muốn. Dữ liệu playlist trên máy không bị ảnh hưởng.

## QUYẾT ĐỊNH 6 — Gửi dữ liệu thật (metadata) lên kênh
Chỉ **bộ thử có giới hạn** do chủ chỉ định, ví dụ tối đa **3 bài** (ghi mã nhóm/tin nhắn), gồm: Lưu A trên Sony, Bỏ lưu A trên Shanling, 1 thao tác offline rồi hội tụ.
Không gửi: âm thanh, đường dẫn, token; không tải toàn bộ playlist; không thao tác hàng loạt.
Bộ thử (chủ điền): bài 1 ________ bài 2 ________ bài 3 ________
☐ Cho phép gửi đúng bộ thử trên ☐ Không
Trong app: phải tick riêng ô "Cho phép gửi lần đầu" (quyền c) sau khi đã bật (a).

## QUYẾT ĐỊNH 7 — Bằng chứng và dừng khẩn
Claude chụp ảnh/quay video thật (nhãn "thật"), lưu `docs/plans/artifacts/E96-P5B/`, không commit ảnh có nội dung riêng tư; ChatGPT đưa 5–10 ảnh vào Drive Ni N (owner-only). **Dừng ngay, không tìm cách khác** nếu: cổng nào HALT, đăng nhập lỗi, Telegram báo FLOOD_WAIT dài, kênh trùng/không rõ, app crash, thiết bị lạ, xe đang chạy.
☐ Đồng ý

## Lộ trình thử sau khi đủ duyệt (mỗi bước có ảnh trước/sau; âm thanh thật vẫn "CHƯA XÁC NHẬN" nếu không có nguồn độc lập)
1. Build đúng chữ ký ở Tiny + báo cáo cổng (QUYẾT ĐỊNH 1) → ChatGPT kiểm toán → mới cài (QUYẾT ĐỊNH 2).
2. Sony: mở Cài đặt > Đồng bộ Playlist → thấy cảnh báo; bật (a) → dò kênh → (nếu NotFound) tạo kênh → gắn.
3. Sony: Lưu bài A → "Chờ đồng bộ" → "Đồng bộ ngay" → "Đã đồng bộ" (chỉ sau OK thật).
4. Shanling: bật (a), dò thấy kênh → gắn → đồng bộ → thấy A.
5. Shanling: Bỏ lưu A → đồng bộ; Sony đồng bộ → A biến mất.
6. Một thao tác offline mỗi bên rồi hội tụ; khởi động lại app: trạng thái còn nguyên; không va chạm âm thanh/ back/ seek/ thanh hệ thống.

## Còn thiếu / chưa kiểm chứng (ghi rõ để không hiểu nhầm là PASS)
FYT 7870 chưa kết nối (BLOCKED_DEVICE) · Telegram config trên Tiny chưa xác minh · TDLib thật (getChatHistory, ACK, FLOOD_WAIT) chưa chứng minh · âm thanh thật · Keystore thật · ảnh/ video thật.

Chữ ký chủ dự án: ______________________   Ngày: ____________
