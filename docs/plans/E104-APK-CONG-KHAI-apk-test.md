# E104-APK-CONG-KHAI — APK QA Candidate

## Bản hiện tại: vc120 `bf174c5` — REWORK vòng 1 (27/10/2026)

**Trạng thái: ĐÃ NGHIỆM THU — PASS** (chủ dự án "đã ĐẠT" 27/10/2026, `E104-APK-CONG-KHAI-nghiem-thu.md` mục 16). **KHÔNG ĐĂNG CÔNG KHAI** (khóa debug). Ký bằng khóa debug Tiny; không đưa lên web/GitHub/Telegram.

- [THƯ MỤC APK + ẢNH QA E104 vc120 bf174c5](https://drive.google.com/drive/folders/1lLeIK5m6l76OUb2yYs108k-3xLECD5Zi)
- [APK OPEN Debug — app.melovault.player.open 0.6.95-open (cài thử bản tự nhập mã)](https://drive.google.com/file/d/1WgBT7usftQ0-hIJfAsymQrFOJalrZEyr/view)
- [APK THƯỜNG Debug — app.melovault.player 0.6.95-beta (bản riêng, có mã nhúng)](https://drive.google.com/file/d/1LyLRI46VmL97EXYQrAstufJIQ8oZVLlB/view)
- [APK OPEN Release — app.melovault.player.open 0.6.95-open (chưa cài thử)](https://drive.google.com/file/d/10qOgBNHMf_RMWpr0YoS9cz8XXPIMP5vz/view)
- [SHA256.txt](https://drive.google.com/file/d/1ahH115fGlONZU4lrAwQ_Y8FOcmCdvmJ4/view)

| APK | Package / vc / vname | Bytes | SHA-256 | Config nhúng | Quét hash (Tiny) | QA |
|---|---|---:|---|---|---|---|
| `E104-bf174c5-normal-debug-vc120.apk` | `app.melovault.player` / 120 / `0.6.95-beta` | 33.462.212 | `ED65DF389FC70D3FC1D49672F29190C0C2E236206BE8874EC0944B5C880B7ADD` | true | found=true (đối chứng) | Shanling Q5 nhanh (vòng trước: phát PLAYING) |
| `E104-bf174c5-open-debug-vc120.apk` | `app.melovault.player.open` / 120 / `0.6.95-open` | 33.462.216 | `29F918E9E2E2EE37EBEE79FD7BA8B29D937FCBF0742A0FBD2543039EFF49D8A2` | false (thiết kế) | **found=false** | Shanling Q1–Q4, hash che |
| `E104-bf174c5-open-release-vc120.apk` | `app.melovault.player.open` / 120 / `0.6.95-open` | 30.703.990 | `DAF93D97B68EBBB1D345AF74EF39A7936A628F1020B5E20EDA222487F480D0FB` | false (thiết kế) | **found=false** | chưa cài |

| Trường | Giá trị |
|---|---|
| Git source | `bf174c5` (nhánh `dot/E104-APK-CONG-KHAI`) |
| Build | Tiny, clone cô lập `C:\Bots\MeloVault-E104-IsolatedQA-rework1-20261027-0940`, bundle SHA-256 `06B87819…85BC90` |
| Signer cert SHA-256 | `eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8` (cả 3, như các Beta) |
| Drive folder | `MeloVault_QA/E104-APK-CONG-KHAI-20261027-vc120-bf174c5` (ID `1lLeIK5m6l76OUb2yYs108k-3xLECD5Zi`), thư mục mới |
| Drive permission | chỉ owner ntdung.md — connector kiểm thư mục và cả 3 APK |
| Kiểm Opus | SHA-256 bản Ni = `SHA256.txt` trên Drive; kích thước Drive khớp từng APK; log Tiny: cert, config, hash check như bảng |
| Nguồn local | Ni `docs/plans/artifacts/E104-APK-CONG-KHAI/rework1-20261027-0940/`; Tiny `C:\Bots\_qa_temp\E104-rework1-20261027-0940\retry` |

Chưa kiểm: đăng nhập bằng mã API thật, lỗi `API_ID_INVALID` trên máy, âm thanh thật, giao diện bản Release, Y700/S25/FYT/Sony.

## Bản cũ: vc120 `4f8b575` — không dùng (ô API hash hiện rõ ký tự), không upload.
