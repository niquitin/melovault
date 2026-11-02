# Hình nghiệm thu — 2026-11-02-bo-chinh-am-eq

**QA CANDIDATE — CHƯA NGHIỆM THU.** Ảnh chụp từ Sony XQ-AS52 (`QV725WEQ54`, Android 12, giao diện Mới, dọc). Drive Ni N owner-only (chỉ `ntdung.md@gmail.com`; `get_file_permissions` đã kiểm cho thư mục, `hinh` và APK). Ảnh có ảnh bìa người thật và ảnh thư viện có tên bài **không** được upload; bản gốc nằm ở `docs/plans/artifacts/2026-11-02-bo-chinh-am-eq/qa/` (`.gitignore`).

- Thư mục: [MeloVault-EQ-QA-CANDIDATE-20261102-vc121-e2faf85](https://drive.google.com/drive/folders/1DpJDqqFrHrYGGosqOLkzuw1rfagTQDVe) · [hinh](https://drive.google.com/drive/folders/1N4mt0Y2DImYMhQnBtjwfE4G6N4FLKUth)
- **Ảnh tổng hợp:** [00_tong_hop.png](https://drive.google.com/file/d/10eoiuL4pDsXUtGOiyf9ArkrupahxqdHC/view?usp=drivesdk)

| Bước | Ảnh / tệp | Nội dung |
|---|---|---|
| Q1 | [03_deck.png](https://drive.google.com/file/d/1s5d78TyBRFZMUPYq5Mnxzr_1VEuebv_f/view?usp=drivesdk) · [04_eq_panel.png](https://drive.google.com/file/d/19cq8cqX5uc8MVY5gIXo5D-mEXpYejBKM/view?usp=drivesdk) | Chip “EQ” chưa tô; bảng EQ Tắt, Phẳng, 10 thanh 0 dB |
| Q2 | [05_bass_up.png](https://drive.google.com/file/d/1S3R1rzw41KTlA51RC8XBXCDUsGrtaeR3/view?usp=drivesdk) | Bật EQ + Tăng bass: +6/+5/+4/+2 dB |
| Q3 | [06_custom.png](https://drive.google.com/file/d/1W2QvSk9QySDpQ6iZycvFNJ_CvonplH5p/view?usp=drivesdk) | Kéo 1 kHz +4 dB (bản `7353587`: chip “Tùy chỉnh” ngoài màn hình — đã sửa) |
| Q4 | [12_panel_custom_scrolled.png](https://drive.google.com/file/d/1me92Z2Ay9DKVMQURJyCQVf22ryDaR4PW/view?usp=drivesdk) | Sau cài lại: vẫn Bật + Tùy chỉnh đúng giá trị; hàng mẫu cuộn tới “Tùy chỉnh” |
| Q5 | [14_eq_off_panel.png](https://drive.google.com/file/d/1j7g1nMFWO_PmoqjH29mVmWov5j1vBbKK/view?usp=drivesdk) | Tắt EQ: công tắc Tắt, bảng mờ, thanh vẫn hiện (chip về “EQ” ở ô 7 của ảnh tổng hợp) |
| Q7 | [18_reset_off_panel.png](https://drive.google.com/file/d/1db-yetpxZJUtthOeX8wxzUquytLUq91G/view?usp=drivesdk) | Đặt lại: Phẳng, Tắt, 10 thanh 0 dB |
| Log | [logcat_meloeq.txt](https://drive.google.com/file/d/13CeyOcp16Gz8CcjaCt2iROOA1fFI8cgI/view?usp=drivesdk) · [m_eq_on_rock.txt](https://drive.google.com/file/d/1DkUX6wSt-ycNneKX9eZKGU7-ZMpG9Jn5/view?usp=drivesdk) · [m_eq_off.txt](https://drive.google.com/file/d/1rBV-ryj2l0857Rh6G4_pPbQdFZ5f0qJ-/view?usp=drivesdk) | Log `MeloEq` (cấu hình, đổi cài đặt, RMS vào/ra, chặn biên, µs/buffer) và mẫu CPU `top` khi EQ Bật (Rock) / Tắt |

Giới hạn: không có ảnh Q6 (chuyển bài/tua, ảnh bìa người) và ảnh xoay ngang/chữ lớn (CHƯA KIỂM). Tải bằng trình duyệt chưa thử.
