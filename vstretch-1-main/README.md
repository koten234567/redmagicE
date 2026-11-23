# StretchX — kéo giãn màn hình THẬT (True Stretch) bằng Shizuku

App Android (Kotlin, không phụ thuộc AppCompat/Compose) đổi **kích thước màn hình ảo** (`wm size` + `wm density`)
rồi **kéo giãn hình ra toàn bộ panel ở tầng SurfaceFlinger** — không còn viền đen. Thanh kéo **1.00x → 1.50x**,
**cửa sổ nổi** để chỉnh ngay khi đang chơi và nút **Reset**. Có profile sẵn cho **Valorant Mobile bản Trung Quốc** (`com.tencent.tmgp.codev`).
Tối ưu bố cục 2 cột cho tablet (RedMagic Astra: 2400×1504, 16:10).l

## Cách dùng
1. Cài & mở **Shizuku**, bấm Start (Gỡ lỗi không dây: Android 11+ không cần PC).
2. Mở StretchX → **Cấp quyền** Shizuku.
3. Chọn hệ số / hướng / % độ phân giải → **Áp dụng**. Hoặc bấm preset 16:9, 2:1, 21:9 (tự tính theo tỉ lệ máy).
4. **Bật cửa sổ nổi** → thoát app, mở game, chạm bong bóng để chỉnh. **Reset** = khôi phục kích thước và mật độ đang dùng trước khi áp dụng StretchX.
5. Nút “Mở game & áp dụng” áp dụng trước rồi mở Valorant CN (khuyên dùng).

## Giới hạn quan trọng (đọc kỹ)
* **Cảm ứng không được kéo giãn theo.** Android chỉ đổi ánh xạ cảm ứng khi chính DisplayManager đổi, còn ta chỉ đổi phần hiển thị.
  Hệ quả: tại **giữa màn hình** cảm ứng khớp 100%, càng ra **mép** (theo chiều bị kéo) càng lệch — tối đa bằng đúng bề rộng
  viền đen cũ (≈5% chiều cao với mức 1.12x trên panel 16:10). Mức kéo càng nhỏ thì lệch càng ít → hãy bắt đầu từ 1.05–1.15x.
  Không có quyền shell nào cho phép hiệu chỉnh cảm ứng (`SET_INPUT_CALIBRATION` chỉ dành cho hệ thống).
* Kéo "Rộng" = màn hình ảo rộng hơn tỉ lệ máy → hình kéo giãn theo chiều **dọc**. "Hẹp" = ngược lại → kéo giãn theo chiều **ngang**
  (kiểu "4:3 kéo giãn" quen thuộc: mục tiêu trông to bề ngang hơn).
* Mức tối đa tự giảm trên máy độ phân giải thấp (sàn 800×480 để không dãn quá mức).
* Đổi kích thước khi đang trong trận có thể làm game tải lại/văng → áp dụng trước khi vào game.
* Phụ thuộc API ẩn của Android (10 → 16). Nếu ROM của hãng chặn/đổi API, mục **Chẩn đoán** sẽ báo rõ bước nào lỗi và app tự
  rơi về kiểu cũ (có viền đen) chứ không làm hỏng màn hình.
* Một số game/anti-cheat có thể phản ứng với việc đổi độ phân giải; tự chịu trách nhiệm khi dùng.

## Nếu bị kẹt màn hình sau khi reboot
`wm size` được hệ thống lưu lại. Nếu Shizuku chưa chạy, dùng PC: `adb shell wm size reset && adb shell wm density reset`
(hoặc bật lại Shizuku rồi bấm Reset trong app).

Phép chiếu kéo giãn của SurfaceFlinger **không** được lưu qua lần khởi động lại: nếu hình còn bị kéo lạ sau khi reset,
chỉ cần **xoay màn hình một lần** hoặc **khởi động lại máy** là về bình thường.

## v2.5.0 — Sửa cảm ứng khi kéo giãn thật ("Modo Pro" kiểu Cat Resolution)

**Vấn đề:** kéo giãn thật (Sf.kt) kéo HÌNH ra toàn panel nhưng hệ thống vẫn ánh xạ CHẠM theo vùng vừa khít cũ
→ dải viền cũ không nhận chạm (2 cạnh không ấn/vuốt được), vùng gần nó bị lệch.

**An toàn:** mặc định TẮT. Bật bằng nút "Sửa cảm ứng" rồi bấm "Áp dụng".
* Chèn thất bại 5 lần liên tiếp → tự nhả cảm ứng như cũ.
* **Bấm Volume+ 3 lần trong 2.5 giây → khôi phục khẩn cấp** (nhả cảm ứng, khôi phục kích thước và mật độ trước khi áp dụng StretchX).
* Đóng app / dừng Shizuku → kernel tự nhả thiết bị chạm.
* Nút "Chẩn đoán" hiện thêm mục "Sửa cảm ứng" (thiết bị chạm, số sự kiện đã chèn, lỗi gần nhất).

**Lưu ý:** đây là cơ chế thử nghiệm, chưa kiểm chứng trên mọi ROM (cần quyền EVIOCGRAB cho shell, và đường `injectInputEvent`).
Chạm được phát lại bằng chèn sự kiện nên game/anti-cheat có thể coi là thao tác lạ.


## v2.6.0 — Theme "RedMagic Cyber-Red" (chỉ giao diện, không đổi chức năng lõi)

* **Màu:** nền đen #0A0A0A + hoa văn carbon mờ, ánh LED đỏ ở đáy; nhấn đỏ #FF2D55, cam neon #FF5722; chữ 3 cấp sáng (TEXT › MUTED › DIM).
* **Hình khối:** phẳng, góc cắt vát (hard-edge) cho thẻ/nút/bong bóng; nút chính đỏ sáng viền, nút phụ đen xám viền mảnh (`CutDrawable` trong `Ui.kt`).
* **Công tắc "liquid glass"** (`GlassSwitch`) thay cho nút BẬT/TẮT ở *Kéo giãn thật* và *Sửa cảm ứng* — cùng hành vi cũ.
* **Cửa sổ nổi gọn:** bong bóng vát đỏ; bảng chỉ còn số + thanh trượt + [−] [Reset] [+]; hướng Rộng/Hẹp và thông tin nằm trong mục "Chi tiết".
* **Hộp thoại** theo theme (`Cyber.Dialog`), theme chính `Cyber.App` (hết nháy trắng khi mở app).
* **Logo mới:** khung cắt góc + mũi tên kéo giãn + chữ X, phát sáng đỏ; có lớp monochrome cho icon theo chủ đề Android 13+; icon thông báo riêng.
* Không đụng tới: `Engine`, `Sf`, `Touch`, `Shizu`, `Stretch`, AIDL — toàn bộ logic giữ nguyên.


## v2.7.0 — Phím tắt nhanh + Hồ sơ kéo giãn + Phiên game

### 1. Nút bật/tắt nhanh ở bảng phím tắt (Quick Settings)
* Có **3 ô**: “StretchX 1 / 2 / 3”. Mỗi ô gắn với **1 hồ sơ** đã lưu (mặc định 1.10x / 1.05x / 1.15x).
* **Chạm ô = bật hồ sơ** (ô sáng), **chạm lần nữa = khôi phục** màn hình. Không cần mở app, không cần kéo tay.
* Thêm ô: trong app, mục **Phím tắt nhanh → “+ Thêm ô”** (Android 13+ hiện hộp xác nhận).
  Nếu máy không hiện: vuốt xuống bảng phím tắt → biểu tượng **bút (Chỉnh sửa)** → mục **“Ô ứng dụng thứ 3”** → giữ và kéo
  “StretchX …” lên khu vực phím tắt (như màn hình “Chỉnh sửa” của RedMagic).
* Tên và phụ đề ô tự đổi theo hồ sơ (“1.10x · StretchX · Đang bật”). Giữ lâu ô = mở app.
* Cần Shizuku đang chạy; nếu chưa, chạm ô sẽ báo và mở app.

### 2. Hồ sơ kéo giãn (lưu sẵn độ kéo)
* Mỗi hồ sơ lưu: **hệ số, hướng Rộng/Hẹp, % độ phân giải, Kéo giãn thật, Sửa cảm ứng**.
* Mục **Hồ sơ nhanh** (đầu trang): chạm chip = bật hồ sơ; chạm lần nữa = khôi phục; **giữ lâu** = bật / sửa / gán phím tắt ô 1-2-3 / xóa.
* **+ Lưu hiện tại** (hoặc “+ Lưu thành hồ sơ nhanh” ở mục chỉnh tay) = lưu đúng giá trị thanh trượt đang chọn thành hồ sơ mới.
* Lần đầu có sẵn 4 hồ sơ mẫu **1.05x / 1.10x / 1.11x / 1.15x** (sửa/xóa tuỳ ý).
* Cửa sổ nổi cũng có **hàng hồ sơ nhanh** (4 hồ sơ đầu) ngay dưới thanh trượt.

### 3. Nâng cấp theo ứng dụng mẫu (ảnh 3)
* **Tính năng chính**: 3 ô bật/tắt — *Cửa sổ nổi*, *Chiếu thật* (kéo giãn thật), *Áp dụng/Khôi phục* (quay về bằng 1 chạm).
* **Độ phân giải hiện tại** hiển thị lớn ở mục Trạng thái (đỏ khi đang bị chỉnh).
* **Game · phiên bảo vệ**: danh sách nhiều game (có biểu tượng), mỗi game chọn 1 hồ sơ riêng. **▶ Chơi** = áp dụng hồ sơ rồi mở game;
  khi bạn **thoát game màn hình tự về bình thường** (sau ~6 giây), quay lại game thì tự kéo giãn lại; game tắt hẳn thì phiên tự kết thúc.
  Thông báo có nút **Kết thúc & khôi phục**. (Theo dõi bằng lệnh shell qua Shizuku mỗi 2 giây; nếu ROM không cho đọc activity đang hiện thì
  chỉ dựa vào việc game còn chạy hay không.)
* **Ẩn/hiện bong bóng bằng Volume −**: bật trong mục Cửa sổ nổi; **nhấn đúp** Volume − để ẩn/hiện (không chặn phím, âm lượng vẫn đổi bình thường).
  Thông báo cũng có nút **Ẩn/Hiện**.
* Giao diện giữ nguyên vị trí cuộn khi bấm nút (trước đây nhảy về đầu trang).

### Thay đổi kỹ thuật
* Mới: `Profiles.kt` (hồ sơ, game, lịch sử, `Actions`), `Tile.kt`, `SessionService.kt`, `Keys.kt`, `IKeyCb.aidl`, `ic_tile.xml`.
* `IShell.aidl` thêm `setKeyListener` (số 6) — `Engine`, `Sf`, `Touch` **không đổi**.
* Mọi nơi bật/tắt (app, ô phím tắt, cửa sổ nổi, phiên game) đi qua `Actions` nên hồ sơ đang bật luôn đồng bộ.


## v2.7.1 — DPI khi khôi phục, mượt hơn khi bật/tắt cửa sổ nổi, bỏ Lịch sử

* **Mục “DPI khi khôi phục”**: đặt DPI (hoặc chọn nhanh theo dp: 1200 / 1000 / 900 / 800 / 720 dp) mà máy sẽ về khi bấm Reset / Khôi phục / tắt hồ sơ.
  dp = chiều dài màn hình ÷ (DPI ÷ 160). Nút **“Dùng DPI hiện tại”** lưu nhanh mức đang dùng; **“Tự động”** = như cũ (DPI trước khi app can thiệp).
  Sau khi reset app **kiểm tra lại DPI** và ép lại một lần nếu ROM tự đổi; khôi phục khẩn cấp (Volume+ ×3) cũng dùng đúng DPI này.
* **Giảm giật khi bật/tắt cửa sổ nổi**: cửa sổ nổi dùng GPU (trước đây vẽ bằng CPU), bỏ animation thêm/bỏ cửa sổ của hệ thống,
  áp lại phép chiếu kéo giãn ngay sau khi thêm/bỏ cửa sổ, canh gác kiểm tra dày hơn (0,6 giây/lần), giao diện app không còn dựng lại cả cửa sổ
  (có bộ nhớ đệm biểu tượng game và hồ sơ, gộp các lần cập nhật).
* Phiên game theo dõi thưa hơn (3 giây/lần) để lệnh `dumpsys` không gây khựng khi đang chơi.
* **Đã bỏ mục Lịch sử.**


## v2.8.0 — Theme "Neon Pink" + Motion Design System 1.0 (chỉ giao diện)

**Hướng thiết kế:** Hybrid Gaming × Minimal · nền charcoal `#121214` · bề mặt `#1B1B1F`/`#25252B` · nhấn **Neon Pink `#FF2E93`** ·
Muted Pink `#D98AB5` · chữ Roboto · thẻ bo 14dp, nút bo 12dp · icon nét kiểu Lucide. Glow hồng chỉ xuất hiện ở mục đang chọn.

**Bố cục:** tablet (≥600dp) có **sidebar cố định** + nội dung tối đa 1040dp (2 cột khi đủ rộng); điện thoại dùng **thanh điều hướng dưới**, 1 cột.
5 trang: Tổng quan · Kéo dãn · Game · Công cụ · Thiết bị. Mọi card/callback cũ giữ nguyên, chỉ được nhóm lại theo trang.

**Module `motion/`** (tất cả animation dùng chung `MotionTokens`):

| File | Nội dung |
|---|---|
| `MotionTokens` | duration + easing (cubic-bezier theo spec) |
| `MotionPreferences` | Reduce Motion (đọc `animator_duration_scale`) |
| `MotionPresets` | enter/stagger, Card Lift (scale 0.98), `MotionMemory` (trạng thái hiển thị qua các lần dựng lại) |
| `NavigationMotion` | `NavBar` (Sliding Active Indicator), `PageHost` (chuyển trang có hướng 240ms) |
| `InteractionMotion` | `ElasticSwitch`, `MagneticSeekBar`, `SelectionMotion` (viền hồng + đường sáng chạy 1 vòng), `ExpandablePanel` |
| `FeedbackMotion` | `ActionButton` (Processing Ring → Success Morph 450ms), `ToastHost` (Smart Toast), `PulseDot`/`ConnectionChip`, `NumberRoll`, `Skeleton` |
| `AmbientMotion` | `AmbientGlow` (chỉ transform, chu kỳ 8s, tắt khi ở nền / Reduce Motion / không ở Tổng quan) |

**Nguyên tắc giữ logic:** animation chỉ *phản chiếu* state thực. Công tắc, số, preset, nút Áp dụng/Khôi phục chỉ đổi sau khi callback cũ chạy xong;
không có % tiến độ giả. `Engine`, `Sf`, `Touch`, `Shizu`, `Stretch`, `Profiles`, `Prefs`, AIDL, lệnh ADB/`wm` **không đổi**.

**Reduce Motion** (Cài đặt Android → Trợ năng → Xóa hiệu ứng): tắt glow, stagger, number rolling, dịch chuyển trang (còn fade ngắn);
vẫn giữ feedback thành công/thất bại (tick/X hiện tĩnh), ripple và toast.

**Lưu ý:** mục *Nâng cao* (Kéo giãn thật, Sửa cảm ứng, Chẩn đoán, % độ phân giải) mặc định thu gọn; trạng thái mở được nhớ trong phiên.
Ripple dùng `RippleDrawable` của hệ thống nên thời lượng do Android quyết định (xấp xỉ 350ms).


## v2.9.0 — Futuristic Gaming Motion & UI Upgrade

**Chưa build/chạy thử.** Môi trường phát triển không có Kotlin/Gradle/Android SDK nên mã chỉ được rà soát tĩnh
(cân bằng ngoặc, tham chiếu resource, so sánh với 2.8). Hãy build bằng GitHub Actions (workflow đã thêm bước **Debug**
bên cạnh Release → tải artifact `StretchX-debug-apk` / `StretchX-arm64-v8a-apk`) hoặc `gradle :app:assembleDebug`.
Nếu có lỗi biên dịch, gửi log để sửa.

### Business logic: KHÔNG thay đổi
`Engine`, `Sf`, `Touch`, `Shizu`, `Stretch`, `Profiles`, `Prefs`, `Keys`, `DeviceInfo`, `SessionService`, `ShellService`, `Tile`,
AIDL, `AndroidManifest.xml`, applicationId/ABI/ký APK **giữ nguyên byte-for-byte so với 2.8** (đã so sánh bằng `cmp`).
Các nút Áp dụng/Khôi phục/hồ sơ/game gọi đúng `Actions.*` như cũ; trạng thái Loading/Success/Error chỉ phản chiếu callback thật.
Tuỳ chỉnh giao diện lưu trong SharedPreferences riêng `stretchx_ui` (lớp `UiPrefs`), không đụng `Prefs`.

### Trạng thái từng tính năng
| # | Tính năng | Trạng thái | Ghi chú |
|---|---|---|---|
| 1 | Interactive Glow Tracking | ✅ | `GlowCard`: ánh sáng bám ngón tay trên thẻ nội dung, nằm dưới nội dung, tắt khi Reduced |
| 2 | Liquid Progress Bar | ✅ | `LiquidBar`: (a) đồng hồ "mức kéo / tối đa của máy" lấy từ `Plan`; (b) dưới Áp dụng/Khôi phục: đang xử lý = dải không xác định (không % giả), chỉ đổ đầy xanh/đỏ khi backend trả về |
| 3 | Shared Element Transition | ✅ (2 điểm) | Thẻ "Thông số trực tiếp" → bảng chi tiết; nút Game ở dock → bảng game. Có xử lý Back, kéo tay nắm, xoay màn hình (dựng lại sheet không animate) |
| 4 | Scanning Sweep | ✅ | Chỉ hiện khi đọc thông tin máy lần đầu hoặc bấm "Làm mới"; gỡ ngay khi xong/lỗi |
| 5 | Contextual Micro-interactions | ✅ | Mỗi loại một phản hồi: chip/preset (viền + đường sáng), công tắc (thumb co), thẻ (glow), nút chính (ripple + vòng xử lý). Ripple chỉ còn ở nút chính |
| 6 | Haptic Feedback | ✅ | `Haptics` qua `performHapticFeedback` (theo cài đặt rung hệ thống); chọn preset/bật tắt/hoàn tất; 1 nhịp khi thanh trượt chạm vạch preset |
| 7 | Live Device Dashboard | ✅ | Độ phân giải, DPI, tần số quét (từ `Dev`), trạng thái Shizuku. Cập nhật mỗi lần `reload()` (không dùng timer giả) |
| 8 | Customizable Dashboard | ✅ | Ẩn/hiện + đổi thứ tự bằng nút ▲▼ (chưa có kéo-thả) |
| 9 | Smart Bottom Sheet | ✅ | `BottomSheet`: Back, chạm nền, kéo xuống, vùng an toàn |
| 10 | Quick Action Dock | ✅ | Áp dụng · Khôi phục · Game; chống bấm trùng (bỏ qua khi thao tác cùng loại đang chạy) |
| 11 | Command Palette | ✅ | Nút tìm kiếm; tìm không dấu; trang, hành động, hồ sơ, game, cài đặt |
| 12 | Adaptive Layout | ✅ | 1/2/3 cột theo bề rộng thực; sidebar ≥600dp; theo dõi xoay/đổi kích thước cửa sổ |
| 13 | Dynamic Accent | ◐ một phần | Sắc độ hồng đổi nhẹ (bình thường / đang kéo giãn / Shizuku chưa sẵn sàng) chỉ áp cho ambient glow, glow theo chạm, scan sweep, preview. Màu thương hiệu `Ui.PINK` (const) không đổi |
| 14 | Preset Preview | ✅ | `RatioPreview`: vẽ viền đen/kéo giãn của cấu hình đang chọn trước khi Áp dụng; chỉ đọc từ `Plan`, không gửi lệnh |
| 15 | Motion Intensity | ✅ | Reduced / Normal / Enhanced (Công cụ → Giao diện); Reduce Motion hệ thống luôn thắng |
| 16 | Edge-to-edge | ✅ | Trong suốt status/nav bar, xử lý cutout & insets cho sidebar, top bar, nav, dock, sheet, toast, trang |
| 17 | Empty State Illustration | ✅ | Vector `ill_empty_*`: hồ sơ nhanh, game, tìm kiếm, Tổng quan trống |

### Hành vi UI mới cần biết
- Back: palette → bottom sheet → về Tổng quan → thoát.
- Tổng quan có card mới "Thông số trực tiếp" (mặc định ở đầu; ẩn được).
- Trang Công cụ có card "Giao diện & chuyển động".

### File thay đổi
Sửa: `MainActivity.kt`, `Ui.kt`, `motion/{MotionPreferences,MotionPresets,FeedbackMotion,InteractionMotion,NavigationMotion,AmbientMotion}.kt`,
`app/build.gradle.kts` (version 2.9.0 / 2900), `.github/workflows/build.yml` (thêm build Debug), `README.md`.
Thêm: `motion/{UiPrefs,Haptics,Accent,ProgressMotion,SheetMotion,PreviewMotion}.kt`,
drawable `ic_{search,sliders,play,rotate,check}.xml`, `ill_empty_{list,game,search}.xml`.

### Cần kiểm tra khi có máy
Apply/Restore (nút chính, dock, palette), Shizuku connect/disconnect, hồ sơ + game, cửa sổ nổi, xoay màn hình khi sheet mở,
Reduce Motion + 3 mức cường độ, cutout/gesture bar, bàn phím trong palette, haptic trên máy thật.
