# APK test — 2026-10-30-dao-thu-tu-phat

**Nhãn: QA CANDIDATE — CHƯA NGHIỆM THU.** Build Debug, không phải bản phát hành.

| Mục | Giá trị |
|---|---|
| TASK_ID / nhánh | `2026-10-30-dao-thu-tu-phat` / `dot/2026-10-30-dao-thu-tu-phat` |
| Git HEAD lúc build | `3296b05e64a1771c8606d57e109f22412808180e` (Tiny, clone cô lập, cây sạch) |
| Commit chứa source | `798b25465b5fe2773ecb5dc8119e73ce73250f7f` (sau đó chỉ có commit tài liệu: `git diff --stat 798b254..3296b05` chỉ `docs/`) |
| Loại build | Shareable Debug (`:app:assembleShareableDebug`, Tiny, BUILD SUCCESSFUL 2m41s) |
| Package / version | `app.melovault.player` / versionCode `121` (không tăng) / `0.6.96-beta`; debuggable; native `arm64-v8a` |
| Cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (khớp cert app đang cài trên Sony) |
| Telegram config | `true` (`reportTelegramConfig`; không in giá trị, cấu hình chỉ ở Tiny) |
| SHA-256 APK | `1D61F67F2B08F525986ED4067EA43E270A28B15E05AE806A9E4E412B5902D485` |
| Bytes | 33.576.900 |
| Đường dẫn nội bộ | Tiny: `C:\Bots\_qa_temp\dao-3296b05-20261030-s6\MeloVault-dao-3296b05-vc121-debug.apk` |
| Bundle chuyển code | Ni `D:\Bots\_handoff\dao-thu-tu-phat-3296b05.bundle`, 13.167.767 byte, SHA-256 `4313A028667CAA7074E4E7F5B887B95EB0B8F73B0492013B7300624EE975F2AE` (Tiny đối chiếu khớp) |

## Cài trên Sony `QV725WEQ54`

Không chạy `adb install -r` trong phiên này: APK đang cài trên Sony (lastUpdateTime 12:55:31 hôm nay, do một lần chạy S6 chưa ghi hồ sơ) đã được kéo về và có **SHA-256 và số byte trùng APK trên** (`1d61f67f…d485`, 33.576.900), cert khớp. Không uninstall, không `pm clear`, không downgrade.

## Link Drive (owner-only, đã kiểm bằng connector)

- Thư mục: https://drive.google.com/drive/folders/1EbxLWY-3ZjrLQOhBuzpEVPIkDTAbB3Bs — quyền chỉ `ntdung.md@gmail.com` (owner), không chia sẻ.
- APK: https://drive.google.com/file/d/1wRuzTnLXTt_7P7fi_bQImn-Sh64U_BvG/view?usp=drivesdk — 33.576.900 byte (khớp), quyền chỉ owner.
- Tải thật trên trình duyệt: **chưa thử** (connector chỉ kiểm tồn tại/kích thước/quyền; Drive có thể cảnh báo khi tải APK).

**Giới hạn về tài khoản:** connector Drive của phiên này thấy tài khoản `ntdung.md@gmail.com` (thư mục `MeloVault_QA`). Các thư mục QA-CANDIDATE cũ của dự án không hiện trong connector này, nên có thể "Drive Ni N" là tài khoản khác. APK/ảnh được đặt ở tài khoản kiểm được; nếu cần đúng Drive Ni N, chủ dự án cho biết để chép sang. Thư mục tạm `_transfer_dao-3296b05` (chứa bundle code, owner-only) cũng nằm ở `MeloVault_QA` của tài khoản này.

## Rollback

Cài lại bản trước bằng `adb install -r` sau gate: R1c Debug vc121 `138688175DC02450467381F49CF4FB61CF1361B9EDA87EC39CE56ABE3D063482` (34.570.449 byte) còn trên Tiny tại `C:\Bots\_qa_temp\dao-d0076c1-20261030-1250\installed-before.apk` (do lần chạy chưa ghi hồ sơ lưu; kiểm lại SHA trước khi dùng). Cấm uninstall/pm clear.
