# Bàn giao thực thi nhạc theo chủ đề Telegram

**TASK_ID** `2026-11-03-chu-de-nhac-telegram` · **03/11/2026 (Asia/Saigon)** · **Codex, thực thi**
**Checkpoint hiện tại:** S1–S4 đã có mã; sửa metadata nguồn đã qua gate Ni, đang build Tiny source `04aa5ba`. Chưa kết thúc S5 và chưa bàn giao APK tải được. [Kế hoạch](2026-11-03-chu-de-nhac-telegram.md) · [Báo cáo](2026-11-03-chu-de-nhac-telegram-bao-cao.md) · [Phụ lục giữ lịch sử](2026-11-03-chu-de-nhac-telegram-phu-luc-thuc-thi.md).

- Worktree `C:/Users/ntdun/.codex/worktrees/melovault-chu-de-telegram/MeloVault-E91-Shareable`; nhánh `dot/2026-11-03-chu-de-nhac-telegram`.
- Baseline source `b90834f44457b60a9732e59e59c1bc9c971368c3`; kế hoạch `36c0647192234560d97bc1ebc304e89538094cf6`; HEAD/source mới **`04aa5ba074b1299ebaa35053accfebb8ecbdf09c`**. Cây sạch sau commit source; chỉ checkpoint này đang được Codex cập nhật. Không có agent phụ hoặc agent khác sửa code.
- Chủ dự án đã chốt: **“Chốt migration v3 theo kế hoạch, thực thi tiếp S1–S5”**. Không hỏi lại quyền. Giữ songKey/PK/playlist/tệp/account/owner/session, package/signer/versionCode.

| Mốc | Trạng thái và bằng chứng |
|---|---|
| S1 | Xong; TDLib ghim `c15d3f5a5de6e3ba5839822c451152e5e18bb700`, native khớp repo. Sony probe tại35ba26b OK1test:14topic/3trang/General/end, hai topic singleton→rỗng, nguồn thường,109message đúng,24requests. Không chạy lại vô cớ. |
| S2 | v3 cộng thêm tại28ff8e1. Bổ sung04aa5ba giữ metadata nguồn trong recompute/title maintenance bằng INSERT OR IGNORE + UPDATE trong transaction, không REPLACE. Không đổi schema thêm lần nữa. |
| S3 | Parser/reader core tại95d6fda; metadata global, budget3pages, cursor/head/older/search/cancel/backoff. |
| S4 | UI/scope/state/queue tại982eeee; sửa grid badge/catalogue subtitle/strip tại33b16f9. Phone/tablet/two-pane/Gọn/Car cùng host. Queue toàn bộ index đúng topic; chỉ duyệt không đổi queue. |
| Ni mới nhất | `scratch/chu-de-telegram/ni-source-maintenance-r6.*`: **BUILD SUCCESSFUL2m19, exit0**, Debug/Release mỗi loại205suites/2762defined/**2741passed/21skipped/0failed/error**, assembleDebug/Release/AndroidTest xanh, diffcheck sạch. Session57112 đã xong. |
| Sony trước04aa5ba | Migrationv2→v3 giữ25558bài/2yêu thích; sau global sync thêm10bài, DB25568/2yêu thích. Core A226/queue226, NextA, browseB giữqueue, DownloadedB0, xoay/recreate đúng; các probe982eeee/33b16f9 OK. Nhưng manual thấy mất badge sau mở lại vì recompute REPLACE rơi cờ forum. Chưa coi source cũ là bản bàn giao cuối. |
| S5 mới | Đang build Tiny04aa5ba; AndroidTest Feature thêm bước recompute thật, assert DBisForum + root Snapshot.hasTopics, chụp **Q9_sources_badge**. Phải kiểm Q9 trước khi kết thúc. |

**Job đang chạy:** Tiny build nền Hidden **PID27044**, clone mới `C:/Bots/MeloVault-FORUM-MetadataQA-20261103-04aa5ba`, đúng HEAD/cây sạch trước build. Log/exit `C:/Bots/_qa_temp/forum-metadata-20261103-04aa5ba/build.*`. Bundle `forum-metadata-20261103-04aa5ba.bundle`, **13.910.920B**, SHA **0D7BEFEF6DBB8D0F33A4B7A77F3F3E6D8969E52603B29C98CAB76686D75D2DE3** khớp Ni/Tiny và verifyGit. Đọc exit trước, không chạy Gradle trùng. Ni không còn build/test đang chạy.

**Build/config:** JDK17/SDK Tiny. Gradle dùng `-PmeloLocalPropertiesFile=C:/Bots/MeloVault-EQ-IsolatedQA-20261103-cade4dc/local.properties` (config có sẵn trên Tiny). Không đọc/copy/hash/log file này hoặc keystore/session. Report boolean trước khi cài. APK Ni config=false chỉ compile.

**Thiết bị hiện tại:** Tiny DESKTOP-HNP4GVR, Remote Desktop Commander device`9c7f081b-6e1c-4221-ac78-da470bab1e5d`; Sony`QV725WEQ54`/XQ-AS52/Android12. App đang cài source33b16f9, package`app.melovault.player`, vc121/0.6.96-beta, SHA`3D9F4EFAC3B4ACDEA9C71E11B05AEC887AEA423CB833072AB825FB6C1397E104`,33.878.126B, cert`eecd3df7e946bdc8cf0404ad4bb9695e2815cea09ff8eb2ea51dd4edf1c3fce8`,configtrue. Test APK cũ BFF793…EA339/441763B, source04aa5ba test APK sẽ đổi vì thêmQ9. Không instrument còn chạy. Màn cuối **SONY_SCREEN_END=OFF**.

**Tiếp tục:**

1. Chờ build04aa5ba exit0/configtrue; lưu main/test APK tên riêng không ghi đè. Aaptpackage/version, apksignercert khớp, SHA/bytes; source đúng04aa5ba. Đưa APK hợp lệ về Ni artifacts bằng bản sao/hash, không mang config riêng.
2. Kiểm lại ADB/model/version/MediaSession không phát; chỉ install-r app/test, không downgrade/uninstall/clear. Pull APK đã cài để đối chiếu SHA.
3. Chạy BaselineDeviceTest opt-in sau cài (DBv3/count/favourites), rồi FeatureDeviceTest opt-in với baselineSongs25558. Dùng watcher đã sửa tại `C:/Bots/_qa_temp/forum-final-20261103-33b16f9/capture/feature-probe.ps1`, thay taskQa sang folder mới. Không dùng log rỗng như null; chụp từng Q1–Q9, không gán marker cũ vào ảnh mới. Cleanup223, đợi800ms, xác minhOFF. Q9 phải chứng minh recompute không làm rơi forum metadata.
4. Manual trên đúng app sau test: mở app bằng component, kiểm foreground/XML/ảnh trước tap; Telegram→nguồn có badge→catalogue→LUSH→tìmNam→Back. Tọa độ phải lấy từ UI thật. Các ảnh manualM0/M1 cũ ở app khác/selector trùng không là bằng chứng tính năng.
5. Kiểm PNG/ảnh thật, SHA/bytes Tiny→Ni, tạo collage6–8 ảnh cạnh dài≤1600 cho **đúngsource mới**. Root `00_tong_hop.png` hiện là982eeee: giữ nguyên lịch sử, có thể chuyển file đó vào qa-982eeee sau kiểm đường dẫn/hash rồi tạo collage mới ở root; không ghi đè/xóa ảnh cũ.
6. Upload QA APK mới hợp lệ và tối đa10 ảnh (collage+9Q) vào folder riêng04aa5ba DriveNiN owner-only; readback size/permissions/sharedfalse, kiểm tảiAPK một lần. Nếu Drive vẫn403 thì ghi **BLOCKED — CHƯA BÀN GIAO APK qua link tải**, cung cấp đường dẫn nội bộ và bản đã càiSony. Không acknowledgeAbuse/public/đổiđuôi/đóng gói để né.
7. Hoàn tất `*-apk-test.md`, `*-hinh-nghiem-thu.md`; sửa báo cáo chính dùng source/gate/QA mới, giữ lịch sử trong phụ lục; checkpoint và prompt **phiên sơ bộ mới**. Commit docs trên task branch, cây sạch, dừng. Không tự sơ bộ/ĐẠT/merge/push.

**Drive lịch sử:** NiN/nigamehome@gmail.com, linkconnector`link_68aaf36f9ca8819198e563269f52e8d5` đã xác minh profile. APK982eeee file`10S-JFCH6nyiVp4txuUgdRHNRZ3fd1wde`/folder`1qzfjO_WbZoHaX2GHWetqwgjZcx0Y7GG2`; APK33b16f9 file`1NKxoWkElkDldMv8RAvsTir59Ou3VSqKv`/folder`10EQ1mD4KP2IZQ67cscpRqJkAaByDEjqm`. Cả hai upload thành công,sizekhớp,sharedfalse,ownerduynhấtNiN nhưng fetchraw trả403/cannotDownloadAbusiveFile (“malware or spam”). Không coi link là tải được. Hai photosfolderprivate tương ứng `1NQH7_pYz6Qfw4AAbbyQlH6lYySwF6TU0` và `1I7yEwRtbfe04gNblouG8RB0x39o9uN0q` vẫn trống; chưa có Drive ảnh cuối.

**Giới hạn giữ rõ:** chưa nghe âm thanh thực; B không có tệp offline đã xác minh; không tắt mạng/đổi quyền/account để ép lỗi; WAV/FLAC document có fixture nhưng Sony lượt này ngheMP3; Gọn/Car đã compile, chưa kiểm thiết bị tương ứng/FYT. DSD ngoài phạm vi. Bản sửa tiến trên DBv3, không cài APK cũ hạ DB. Artifact private đều .gitignore.

**ƯU TIÊN checkpoint crash, 23:46 +07:** source04aa5ba sửa metadata nguồn đã nativeOK/Q9ảnhbadge đúng, nhưng manual mới bắt crash khi B→catalogue→chạmLUSH. AndroidRuntimeIllegalStateException/ListViewRowsAdaptercount đổi khôngnotify; exit-info reason4 lúc23:42:13/PID8802. Không phải trạngtháimànngoàiuser, khôngdùngM6Home làmQA. Source04 chưa làbảncuối.
- Codex đang sửa2file: MeloLibraryPane.kt (notifyRowsAdapter ngay sauSnapshot, trướcmọiearlyreturn; cuối nhánhbài refreshRows(notify=false) vẫnrestoreanchor) vàTelegramForumFeatureDeviceTest.kt (B→catalogue rồiListViews thật onTouchModeChangedtrue→mởlạiB/queuegiữ). KhôngthayDB/reader/queue, khôngcófilelạ.
- GateNi session25122 `scratch/chu-de-telegram/ni-hidden-list-r7.*` đangchạy. Chờexit/XML rồicommit/bundleSHA/buildTiny/cài/QA + **chạm thực B→catalogue→B**, kiểmAndroidRuntime khôngcócrashmới. Khôngbàn giao04.
- Source04 artifact C:/Bots/_qa_temp/forum-metadata-20261103-04aa5ba/; Niqa-04aa5ba có9Q/collage + drive-photos.json10file URLs privateđãverified. Latestroot00collage04 SHA8A1B6D035E1B076BE0D723B6F1CA248D9AFB9E7C35350936E7FCDDDA2EA9A3ED; collage982 cũ đãmovedkhôngđổihash vàoqa-982eeee/00. Lần tạoảnhbảnmớicầnarchiveroot00vàoqa04 saucheckpath/hash, khôngghiđè.
- Drive04 parent17at0Be-16lzjGeXqHmH5_LizPxTBJygc, photos1Gh2U3j7Wy6q5wfBhXP2vNYbNpF1I0z7i, APKfile1pd6IiydUGsa3xqz9R6P55uVtnzwL0ghP. PrivateNiN/sharedfalse/sizekhớp; APKfetch403cannotDownloadAbusiveFile, collagefetchOK. Khôngnéwarning/public. Chỉuploadcandidatekếkhicófixthậtđủgate. SonycuốiOFF, khônginstrument/Tinybuild đangchạy.

**Gate crash ListView đã qua:** `ni-hidden-list-r7.exit=0`, BUILD SUCCESSFUL2m30, hai variant2741passed/21skipped/0failed/error (205suites/2762defined); assembleDebug/Release/AndroidTest xanh. TestAndroid gọiđược onTouchModeChanged API thật; chờ chạySony. Sourcecommit tiếp theo chứa fixnotify sớm vàtesttouch-mode. Không có jobNi/Tiny đang chạy; SonyOFF. Bước kế bundle→cloneTiny mới/build/gate/cài/QA native vàchạm thật.
