# E94-LOCAL-MUSIC — APK QA Candidate (2026-10-19)

**Trạng thái:** QA CANDIDATE — CHƯA NGHIỆM THU. Bản test, không phải bản phát hành Stable.

- [MỞ APK TEST E94 TRÊN GOOGLE DRIVE](https://drive.google.com/file/d/1b3ww7qIKw04sxY0U6NsHjGBayNYNXeRB/view)
- [THƯ MỤC APK VÀ 9 ẢNH QA](https://drive.google.com/drive/folders/1NC14C3-ySGWZUtA4vvjRpzhrnsk1LEKs)

| Trường | Giá trị |
|---|---|
| TASK_ID | E94-LOCAL-MUSIC |
| Build | Debug, từ Tiny QA có Telegram config hợp lệ (Sonnet kiểm boolean, không công bố secret) |
| Package | app.melovault.player |
| versionCode / versionName | 109 / 0.6.88-beta |
| Git source | 2d5812139b0f98c64cc4c4f2f9d25f612dc88674 |
| SHA-256 | A02D3B8ABA7EF37CA812B2B88684B5FBE23D9D45CA102F16089AB432524300F1 |
| Bytes | 33640480 |
| Signer cert SHA-256 | eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8 |
| Drive file ID | 1b3ww7qIKw04sxY0U6NsHjGBayNYNXeRB |
| Drive permission | shared=false / not_shared, chỉ chủ sở hữu Ni N |
| Nguồn trên Tiny | C:\Bots\_qa_temp\E94-LOCAL-MUSIC_2d58121\E94-LOCAL-MUSIC_2d58121_app-shareable-debug.apk |

**Kiểm tra độc lập ChatGPT:** so khớp SHA-256 và số byte giữa APK trên Tiny, ổ T: của Ni và bản sao trên ổ Drive X:. Đọc aapt: package, vc109, vn0.6.88-beta; apksigner: chứng chỉ trùng report. Liệt kê Drive xác nhận file đúng kích thước và trạng thái not_shared. Không in/đọc Telegram API secret. Tính năng đang ở trạng thái BLOCKED — CHỜ CHỦ DỰ ÁN do bất thường kiểm thử media dispatch; xem biên bản nghiệm thu.

Không gửi APK Ni build thiếu Telegram config. Không cài lên FYT hoặc đổi versionCode.
