package com.vstretch.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.vstretch.app.motion.ActionButton
import com.vstretch.app.motion.AmbientGlow
import com.vstretch.app.motion.BottomSheet
import com.vstretch.app.motion.CenterMaxWidth
import com.vstretch.app.motion.Cmd
import com.vstretch.app.motion.CommandPalette
import com.vstretch.app.motion.GlowCard
import com.vstretch.app.motion.Haptics
import com.vstretch.app.motion.LiquidBar
import com.vstretch.app.motion.RatioPreview
import com.vstretch.app.motion.ScanSweep
import com.vstretch.app.motion.UiPrefs
import com.vstretch.app.motion.Accent
import com.vstretch.app.motion.ConnectionChip
import com.vstretch.app.motion.ExpandablePanel
import com.vstretch.app.motion.LinkState
import com.vstretch.app.motion.MagneticSeekBar
import com.vstretch.app.motion.MotionMemory
import com.vstretch.app.motion.MotionPreferences
import com.vstretch.app.motion.MotionPresets
import com.vstretch.app.motion.NavBar
import com.vstretch.app.motion.NavItem
import com.vstretch.app.motion.NumberRoll
import com.vstretch.app.motion.PageHost
import com.vstretch.app.motion.ToastHost
import com.vstretch.app.motion.ToastType
import rikka.shizuku.Shizuku
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

private fun wh(w: Int, h: Int) = "${max(w, h)}×${min(w, h)}"

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var uiPrefs: UiPrefs
    private var dev: Dev? = null
    private var devLoadFailed = false
    private var reloading = false
    private var bigTv: TextView? = null
    private var infoTv: TextView? = null
    private var seek: SeekBar? = null
    private var previewView: RatioPreview? = null
    private var usageBar: LiquidBar? = null
    private var liveCardRef: View? = null
    private var gamesBtnRef: View? = null

    // ---- khung giao diện (Presentation) ----
    private var curScroll: ScrollView? = null
    private var curHost: LinearLayout? = null
    private var pageHost: PageHost? = null
    private var nav: NavBar? = null
    private var glow: AmbientGlow? = null
    private var toasts: ToastHost? = null
    private var chip: ConnectionChip? = null
    private var sheet: BottomSheet? = null
    private var palette: CommandPalette? = null
    private var dockView: View? = null
    private var sideView: View? = null
    private var topView: View? = null
    private var mainView: View? = null
    private var bodyView: View? = null
    private var tablet = false
    private val edge = IntArray(4)          // system insets: trái, trên, phải, dưới
    private var lastCols = 0
    private var page = 0
    private var sheetSpec: SheetSpec? = null

    private val ui = Handler(Looper.getMainLooper())
    private val buildRun = Runnable { doBuild() }
    private val loadTimeout = Runnable { if (dev == null) { devLoadFailed = true; buildUi() } }
    private val iconCache = HashMap<String, Drawable?>()   // nạp biểu tượng game rất chậm -> chỉ nạp 1 lần

    private val binderL = Shizuku.OnBinderReceivedListener { runOnUiThread { Shizu.ensureBound(); reload() } }
    private val deadL = Shizuku.OnBinderDeadListener { runOnUiThread { reload() } }
    private val permL = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { Shizu.ensureBound(); reload() } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        uiPrefs = UiPrefs(this)
        MotionPreferences.refresh(this)
        setupEdgeToEdge()
        Shizuku.addBinderReceivedListenerSticky(binderL)
        Shizuku.addBinderDeadListener(deadL)
        Shizuku.addRequestPermissionResultListener(permL)
        buildShell()
        ui.postDelayed(loadTimeout, 6000)
    }

    override fun onResume() {
        super.onResume()
        MotionPreferences.refresh(this)
        glow?.setRunning(page == 0)
        // Cửa sổ nổi bật/tắt -> cập nhật giao diện đúng 1 lần (thay cho hẹn giờ)
        FloatingService.onState = { runOnUiThread { buildUi() } }
        Shizu.ensureBound(); reload()
    }

    override fun onPause() {
        FloatingService.onState = null
        glow?.setRunning(false)          // dừng ambient khi app ở nền
        super.onPause()
    }

    override fun onDestroy() {
        ui.removeCallbacks(loadTimeout)
        ui.removeCallbacks(buildRun)
        sheet?.cancelAnimations()
        palette?.cancelAnimations()
        Shizuku.removeBinderReceivedListener(binderL)
        Shizuku.removeBinderDeadListener(deadL)
        Shizuku.removeRequestPermissionResultListener(permL)
        super.onDestroy()
    }

    override fun onConfigurationChanged(c: android.content.res.Configuration) {
        super.onConfigurationChanged(c)
        MotionPreferences.refresh(this)
        buildShell()                      // xoay màn hình / đổi kích thước cửa sổ: dựng lại khung, giữ trang và bottom sheet
    }

    /** Back: palette -> bottom sheet -> về Tổng quan (chuyển trang lùi) -> thoát. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (palette?.onBack() == true) return
        if (sheet?.onBack() == true) return
        if (page != 0) { goPage(0); return }
        super.onBackPressed()
    }

    private fun reload() = Engine.refresh(this) { dev = it; devLoadFailed = false; reloading = false; buildUi() }

    /** Làm mới có chủ ý: hiện Scanning Sweep đúng trong lúc tác vụ đọc đang chạy. */
    private fun refreshNow() {
        reloading = true
        buildUi()
        reload()
    }

    /** Thông báo toàn cục (Smart Toast). Loại thông báo suy ra từ nội dung — chỉ ở tầng hiển thị. */
    private fun toast(s: String) {
        val type = when {
            s.startsWith("ERR") -> ToastType.ERROR
            Actions.failed(s) || s.contains("⚠") || s.startsWith("Hãy") || s.startsWith("Chưa") -> ToastType.WARNING
            s.startsWith("Đã") || s.contains("✔") -> ToastType.SUCCESS
            else -> ToastType.INFO
        }
        toasts?.bottomExtra = toastExtra()
        toasts?.show(s, type)
    }

    /** Chừa chỗ cho thanh điều hướng / system inset / dock để toast không che nút thao tác. */
    private fun toastExtra(): Int {
        var e = if (tablet) edge[3] else (nav?.height ?: 0)
        val dk = dockView
        if (dk != null && dk.visibility == View.VISIBLE) e += dk.height + dp(12)
        return e
    }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun dlg() = AlertDialog.Builder(this, R.style.StretchX_Dialog)

    // ---------------------------------------------------------------- Edge-to-edge

    @Suppress("DEPRECATION")
    private fun setupEdgeToEdge() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    @Suppress("DEPRECATION")
    private fun readInsets(ins: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            edge[0] = i.left; edge[1] = i.top; edge[2] = i.right; edge[3] = i.bottom
        } else {
            val cut = if (Build.VERSION.SDK_INT >= 28) ins.displayCutout else null
            edge[0] = max(ins.systemWindowInsetLeft, cut?.safeInsetLeft ?: 0)
            edge[1] = max(ins.systemWindowInsetTop, cut?.safeInsetTop ?: 0)
            edge[2] = max(ins.systemWindowInsetRight, cut?.safeInsetRight ?: 0)
            edge[3] = max(ins.systemWindowInsetBottom, cut?.safeInsetBottom ?: 0)
        }
    }

    /** Áp system insets lên từng phần của khung: nền vẫn tràn sau thanh hệ thống, nội dung/nút được chừa chỗ. */
    private fun applyInsets() {
        val l = edge[0]; val t = edge[1]; val r = edge[2]; val b = edge[3]
        if (tablet) {
            sideView?.let {
                it.setPadding(l + dp(14), t + dp(18), dp(14), b + dp(14))
                (it.layoutParams as? LinearLayout.LayoutParams)?.let { lp -> lp.width = dp(216) + l; it.layoutParams = lp }
            }
            mainView?.setPadding(0, t, r, 0)
        } else {
            bodyView?.setPadding(l, 0, r, 0)
            topView?.setPadding(dp(14), t + dp(10), dp(14), dp(6))
            nav?.setPadding(0, 0, 0, b)
        }
        sheet?.setInsets(l, t, r, b)
        palette?.setInsets(l, t, r)
        curHost?.setPadding(dp(10), dp(10), dp(10), pageBottomPad())
        dockView?.let {
            (it.layoutParams as? FrameLayout.LayoutParams)?.let { lp -> lp.bottomMargin = dp(12) + (if (tablet) b else 0); it.layoutParams = lp }
        }
        if (columns() != lastCols) buildUi()
    }

    private fun pageBottomPad() = dp(16) + (if (uiPrefs.dock) dp(76) else 0) + (if (tablet) edge[3] else 0)

    private fun contentWdpNow(): Int {
        val dm = resources.displayMetrics
        val side = if (tablet) dp(216) + edge[0] else edge[0]
        val avail = dm.widthPixels - side - edge[2]
        return (min(avail, dp(1040)) / dm.density).toInt()
    }

    /** Adaptive Layout: số cột theo bề rộng nội dung thực (đổi khi xoay / đổi kích thước cửa sổ). */
    private fun columns(): Int {
        val w = contentWdpNow()
        return if (w >= 960) 3 else if (w >= 640) 2 else 1
    }

    // ---------------------------------------------------------------- UI

    private class PageDef(val label: String, val icon: Int)
    private class SheetSpec(val title: String, val live: Boolean, val build: () -> View)

    private val pages = listOf(
        PageDef("Tổng quan", R.drawable.ic_nav_home),
        PageDef("Kéo dãn", R.drawable.ic_nav_stretch),
        PageDef("Game", R.drawable.ic_nav_game),
        PageDef("Công cụ", R.drawable.ic_nav_tools),
        PageDef("Thiết bị", R.drawable.ic_nav_device)
    )

    /** Dựng khung (và khi đổi cấu hình): sidebar (tablet) hoặc thanh dưới (điện thoại), vùng trang, dock, sheet, palette, toast. */
    private fun buildShell() {
        sheet?.cancelAnimations()
        palette?.cancelAnimations()
        tablet = resources.configuration.screenWidthDp >= 600
        val items = pages.map { NavItem(it.label, it.icon) }

        val g = AmbientGlow(this); glow = g
        val tw = ToastHost(this); toasts = tw
        val ph = PageHost(this); pageHost = ph
        val ch = ConnectionChip(this); chip = ch
        val nb = NavBar(this, items, tablet) { navigate(it) }; nav = nb
        val sh = BottomSheet(this).apply { onClosed = { sheetSpec = null } }; sheet = sh
        val pal = CommandPalette(this).apply { provider = { commands() } }; palette = pal

        val main = FrameLayout(this); mainView = main
        val wrap = CenterMaxWidth(this, dp(1040))
        wrap.addView(ph, FrameLayout.LayoutParams(MATCH, MATCH))
        main.addView(wrap, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER_HORIZONTAL))
        val dk = buildDock(); dockView = dk
        main.addView(dk, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(12) })

        val root = FrameLayout(this).apply { setBackgroundColor(Ui.BG) }
        root.addView(g, FrameLayout.LayoutParams(MATCH, MATCH))

        val body = LinearLayout(this); bodyView = body
        if (tablet) {
            body.orientation = LinearLayout.HORIZONTAL
            val side = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Ui.SURFACE)
                setPadding(dp(14), dp(18), dp(14), dp(14))
                addView(Ui.brand(this@MainActivity), LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = dp(16) })
                addView(searchButton(false), LinearLayout.LayoutParams(MATCH, dp(44)).apply { bottomMargin = dp(12) })
                addView(nb, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(View(this@MainActivity), LinearLayout.LayoutParams(MATCH, 0, 1f))
                addView(ch, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(Ui.tv(this@MainActivity, "v${BuildConfig.VERSION_NAME}", 11f, Ui.DIM).apply { setPadding(dp(4), dp(8), 0, 0) })
            }
            sideView = side; topView = null
            body.addView(side, LinearLayout.LayoutParams(dp(216), MATCH))
            body.addView(View(this).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(1, MATCH))
            body.addView(main, LinearLayout.LayoutParams(0, MATCH, 1f))
        } else {
            body.orientation = LinearLayout.VERTICAL
            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(6))
                addView(Ui.brand(this@MainActivity), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(searchButton(true), LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(8) })
                addView(ch)
            }
            topView = top; sideView = null
            body.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
            body.addView(main, LinearLayout.LayoutParams(MATCH, 0, 1f))
            body.addView(View(this).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(MATCH, 1))
            body.addView(nb.apply { setBackgroundColor(Ui.SURFACE) }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        root.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(sh, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(pal, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(tw, FrameLayout.LayoutParams(MATCH, MATCH))
        root.setOnApplyWindowInsetsListener { _, ins -> readInsets(ins); applyInsets(); ins }
        setContentView(root)

        nb.select(page, false)
        showPage(page, 0)
        updateChip()
        applyInsets()
        sheetSpec?.let { sp -> sh.show(sp.title, sp.build(), null, false) }   // khôi phục bottom sheet sau khi xoay
        root.requestApplyInsets()
    }

    private fun searchButton(compact: Boolean): View {
        val ctx = this
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            contentDescription = "Tìm kiếm chức năng"
            isClickable = true
            background = SurfaceDrawable(Ui.SURFACE_HI, Ui.LINE, dp(1).toFloat(), dp(12).toFloat())
            setPadding(dp(12), 0, dp(12), 0)
            if (compact) gravity = Gravity.CENTER
            addView(ImageView(ctx).apply { setImageResource(R.drawable.ic_search); setColorFilter(Ui.MUTED) },
                LinearLayout.LayoutParams(dp(20), dp(20)))
            if (!compact) addView(Ui.tv(ctx, "Tìm kiếm…", 13f, Ui.DIM).apply { setPadding(dp(10), 0, 0, 0) })
            MotionPresets.attachPress(this)
            setOnClickListener { Haptics.tick(this); palette?.show() }
        }
    }

    // ---------------------------------------------------------------- Điều hướng trang

    private fun goPage(i: Int) {
        nav?.select(i, true)
        navigate(i)
    }

    private fun navigate(i: Int) {
        if (i == page) return
        val dir = if (i > page) 1 else -1
        page = i
        showPage(i, dir)
    }

    /** Tạo trang mới, đưa vào vùng nội dung với chuyển trang có hướng; stagger chỉ ở lần mở đầu tiên. */
    private fun showPage(i: Int, dir: Int) {
        val sv = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER; isVerticalScrollBarEnabled = false }
        val h = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), pageBottomPad())
        }
        sv.addView(h)
        curScroll = sv; curHost = h
        val cards = fillPage(i, h)
        pageHost?.show(sv, dir)
        glow?.setRunning(i == 0)
        if (MotionMemory.visitedPages.add(i)) MotionPresets.stagger(cards)
    }

    /** Gộp nhiều yêu cầu dựng lại trong cùng một nhịp thành 1 lần. */
    private fun buildUi() { ui.removeCallbacks(buildRun); ui.post(buildRun) }

    /** Làm mới nội dung trang hiện tại tại chỗ (không chuyển trang, không stagger, giữ vị trí cuộn). */
    private fun doBuild() {
        val h = curHost ?: return
        val s = curScroll ?: return
        val keep = s.scrollY
        updateChip()
        fillPage(page, h)
        if (keep > 0) s.post { s.scrollTo(0, keep) }
        sheetSpec?.let { sp -> if (sp.live && sheet?.showing == true) sheet?.replaceContent(sp.build()) }
    }

    private fun fillPage(i: Int, h: LinearLayout): List<View> {
        bigTv = null; infoTv = null; seek = null; previewView = null; usageBar = null; liveCardRef = null
        val d = dev
        val cards: List<View> = when (i) {
            0 -> dashboardCards(d)
            1 -> listOf(stretchCard(d), resetCard(d))
            2 -> listOf(gameCard())
            3 -> listOf(tileCard(), floatCard(), appearanceCard())
            else -> listOf(deviceCard(d))
        }
        h.removeAllViews()
        val ncol = columns()
        lastCols = ncol
        if (ncol >= 2 && cards.size > 1) {                    // rộng: nhiều cột
            val n = min(ncol, cards.size)
            val cols = List(n) { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }
            cards.forEachIndexed { idx, v -> cols[idx % n].addView(v) }
            val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            cols.forEach { grid.addView(it, LinearLayout.LayoutParams(0, WRAP, 1f)) }
            h.addView(grid)
        } else if (ncol >= 2) {                               // 1 card trên màn rộng: không kéo giãn hết cỡ
            val w = min(dp(720), dp(contentWdpNow() - 20))
            h.addView(cards[0], LinearLayout.LayoutParams(w, WRAP).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
        } else {
            cards.forEach { h.addView(it) }
        }
        if (i == 0) {
            h.addView(Ui.btn(this, "Tùy chỉnh trang ⚙", Ui.GRAY) { customizeSheet() },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
        }
        return cards
    }

    // ---------------------------------------------------------------- Kết nối Shizuku (dùng cho chip + Live Dashboard)

    private fun link(): Triple<LinkState, String, String> {
        val run = Shizu.running(); val ok = Shizu.granted(); val d = dev
        return when {
            !run -> Triple(LinkState.DISCONNECTED, "Shizuku: chưa chạy", "Chưa chạy")
            !ok -> Triple(LinkState.CONNECTING, "Shizuku: chờ cấp quyền", "Chờ cấp quyền")
            d == null -> Triple(LinkState.CONNECTING, "Shizuku: đang kết nối…", "Đang kết nối")
            !d.viaShizuku -> Triple(LinkState.ERROR, "Shizuku: lỗi đọc dữ liệu", "Lỗi đọc dữ liệu")
            else -> Triple(LinkState.CONNECTED, "Shizuku: đã kết nối", "Đã kết nối")
        }
    }

    private fun updateChip() {
        val (s, long, short) = link()
        chip?.update(s, if (tablet) long else short)
        Accent.update(prefs.active, Shizu.running() && Shizu.granted())   // Dynamic Accent theo trạng thái
        glow?.setAccent(Accent.color)
    }

    /** Skeleton khi đang tải; trạng thái lỗi + thử lại nếu quá lâu không có dữ liệu. */
    private fun loadingBlock(rows: Int): View =
        if (devLoadFailed) Ui.emptyState(this, "Không đọc được thông tin máy.", "Thử lại") {
            devLoadFailed = false
            ui.postDelayed(loadTimeout, 6000)
            buildUi(); reload()
        } else LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            repeat(rows) { addView(Ui.skeleton(this@MainActivity, 0, 18)) }
        }

    // ---------------------------------------------------------------- Tổng quan tùy chỉnh + Live Dashboard

    private val dashNames = mapOf(
        "live" to "Thông số trực tiếp", "status" to "Trạng thái Shizuku",
        "quick" to "Hồ sơ nhanh", "feat" to "Tính năng chính"
    )

    private fun dashboardCards(d: Dev?): List<View> {
        val hidden = uiPrefs.dashHidden()
        val list = ArrayList<View>()
        uiPrefs.dashOrder().forEach { id ->
            if (id !in hidden) when (id) {
                "live" -> list.add(liveCard(d))
                "status" -> list.add(statusCard(d))
                "quick" -> list.add(quickCard())
                "feat" -> list.add(featureCard())
            }
        }
        if (list.isEmpty()) {
            list.add(card("Tổng quan",
                Ui.emptyIllustration(this, R.drawable.ill_empty_list, "Tổng quan đang trống",
                    "Bạn đã ẩn mọi card. Bật lại những card cần dùng.", "Tùy chỉnh") { customizeSheet() }))
        }
        return list
    }

    private fun metric(label: String, value: CharSequence, color: Int = Ui.TEXT): Pair<View, TextView> {
        val v = Ui.tv(this, value, 18f, color, true)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = SurfaceDrawable(Ui.SURFACE_HI, Ui.LINE, dp(1).toFloat(), dp(12).toFloat())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(v)
            addView(Ui.tv(this@MainActivity, label, 11f, Ui.DIM))
        }
        return box to v
    }

    /** Live Dashboard: số liệu thật từ Dev (độ phân giải, DPI, tần số quét) + trạng thái Shizuku. Chạm để mở chi tiết. */
    private fun liveCard(d: Dev?): View {
        val c = card("Thông số trực tiếp")
        liveCardRef = c
        // Scanning Sweep: chỉ khi thực sự có tác vụ đọc (lần đọc đầu hoặc làm mới có chủ ý)
        if ((d == null && !devLoadFailed) || reloading) {
            c.addView(ScanSweep(this), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) })
        }
        if (d == null) { c.addView(loadingBlock(3)); return c }

        val (resBox, resTv) = metric("Độ phân giải", "")
        NumberRoll.bind(resTv, "live-res", floatArrayOf(max(d.curW, d.curH).toFloat(), min(d.curW, d.curH).toFloat())) { v ->
            "${v[0].roundToInt()} × ${v[1].roundToInt()}"
        }
        val (dpiBox, dpiTv) = metric("Mật độ (DPI)", "")
        NumberRoll.bind(dpiTv, "live-dpi", floatArrayOf(d.curDpi.toFloat())) { v -> "${v[0].roundToInt()}" }
        val (hzBox, hzTv) = metric("Tần số quét", "")
        NumberRoll.bind(hzTv, "live-hz", floatArrayOf(d.hz)) { v -> "${v[0].roundToInt()} Hz" }
        val (state, longText, _) = link()
        val (linkBox, _) = metric("Shizuku", longText.removePrefix("Shizuku: ").replaceFirstChar { it.uppercase() },
            when (state) { LinkState.CONNECTED -> Ui.OK; LinkState.ERROR -> Ui.ERR; else -> Ui.TEXT })
        c.addView(row(resBox, dpiBox))
        c.addView(row(hzBox, linkBox))
        c.addView(row(
            Ui.btn(this, "Làm mới", Ui.GRAY) { refreshNow() },
            Ui.btn(this, "Chi tiết ›", Ui.GRAY) { openSheet("Chi tiết thiết bị", { liveCardRef }, true) { detailContent() } }
        ))
        c.isClickable = true
        c.setOnClickListener { Haptics.tick(c); openSheet("Chi tiết thiết bị", { liveCardRef }, true) { detailContent() } }
        return c
    }

    private fun customizeSheet() = openSheet("Tùy chỉnh trang Tổng quan", { null }) { customizeContent() }

    private fun commitDash(order: List<String>, hidden: Set<String>) {
        uiPrefs.saveDash(order, hidden)
        sheet?.replaceContent(customizeContent())
        buildUi()
    }

    private fun customizeContent(): View {
        val order = uiPrefs.dashOrder().toMutableList()
        val hidden = uiPrefs.dashHidden().toMutableSet()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(note("Bật/tắt để ẩn hoặc hiện card; dùng ▲ ▼ để đổi thứ tự. Tùy chỉnh được lưu trên máy."))
        order.forEachIndexed { i, id ->
            box.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(Ui.switchRow(this@MainActivity, dashNames[id] ?: id, "Hiển thị trên Tổng quan", id !in hidden) {
                    if (id in hidden) hidden.remove(id) else hidden.add(id)
                    commitDash(order, hidden)
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.btn(this@MainActivity, "▲", Ui.GRAY) {
                    if (i > 0) { java.util.Collections.swap(order, i, i - 1); commitDash(order, hidden) }
                })
                addView(Ui.btn(this@MainActivity, "▼", Ui.GRAY) {
                    if (i < order.size - 1) { java.util.Collections.swap(order, i, i + 1); commitDash(order, hidden) }
                }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6) })
            })
        }
        box.addView(row(Ui.btn(this, "Đặt lại mặc định", Ui.GRAY) { commitDash(UiPrefs.DASH_IDS, emptySet()) }))
        return box
    }

    // ---------------------------------------------------------------- Bottom sheet (+ Shared Element Transition)

    /** [origin]: thẻ nguồn để "mở rộng" thành bảng (null = trượt từ dưới lên). [live]: tự làm mới khi dữ liệu đổi. */
    private fun openSheet(title: String, origin: () -> View?, live: Boolean = false, build: () -> View) {
        val s = sheet ?: return
        sheetSpec = SheetSpec(title, live, build)
        s.originProvider = { originRectOf(origin()) }
        s.show(title, build(), originRectOf(origin()), true)
    }

    private fun closeSheet() { sheet?.dismiss() }

    private fun originRectOf(v: View?): Rect? {
        val sh = sheet ?: return null
        if (v == null || !v.isAttachedToWindow || v.width == 0 || v.visibility != View.VISIBLE) return null
        val a = IntArray(2); val b = IntArray(2)
        v.getLocationInWindow(a); sh.getLocationInWindow(b)
        return Rect(a[0] - b[0], a[1] - b[1], a[0] - b[0] + v.width, a[1] - b[1] + v.height)
    }

    private fun detailContent(): View {
        val d = dev
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (d == null) {
            box.addView(loadingBlock(7))
        } else {
            addDeviceRows(box, d)
            box.addView(row(Ui.btn(this, "Chẩn đoán", Ui.GRAY) { showDiag() }))
        }
        return box
    }

    private fun gamesContent(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val games = Games.all(this)
        if (games.isEmpty()) {
            box.addView(Ui.emptyIllustration(this, R.drawable.ill_empty_game, "Chưa có game nào",
                "Thêm game ở trang Game để chơi nhanh từ đây.", "Mở trang Game") { closeSheet(); goPage(2) })
        } else {
            games.forEach { box.addView(gameRow(it)) }
        }
        return box
    }

    // ---------------------------------------------------------------- Giao diện & chuyển động

    private fun appearanceSheet() = openSheet("Giao diện & chuyển động", { null }) { appearanceContent() }

    private fun refreshAppearance() {
        sheet?.replaceContent(appearanceContent())
        buildUi()
        glow?.setRunning(page == 0)
    }

    private fun appearanceContent(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val lv = MotionPreferences.level
        box.addView(Ui.tv(this, "Cường độ chuyển động", 14f, Ui.TEXT, true))
        box.addView(row(
            Ui.choice(this, "Reduced", lv == MotionPreferences.REDUCED, "mi:0") { MotionPreferences.setLevel(this, MotionPreferences.REDUCED); refreshAppearance() },
            Ui.choice(this, "Normal", lv == MotionPreferences.NORMAL, "mi:1") { MotionPreferences.setLevel(this, MotionPreferences.NORMAL); refreshAppearance() },
            Ui.choice(this, "Enhanced", lv == MotionPreferences.ENHANCED, "mi:2") { MotionPreferences.setLevel(this, MotionPreferences.ENHANCED); refreshAppearance() }
        ))
        box.addView(note(
            if (MotionPreferences.systemReduce) "Android đang bật “Xóa hiệu ứng”: ứng dụng luôn giảm chuyển động, bất kể lựa chọn này."
            else "Reduced: tắt glow, stagger, number rolling, dịch chuyển trang (vẫn giữ phản hồi thành công/lỗi). Enhanced: glow và ánh sáng theo chạm rõ hơn."))
        box.addView(Ui.switchRow(this, "Rung phản hồi", "Rung nhẹ khi chọn, bật/tắt, hoàn tất (theo cài đặt rung của hệ thống)", uiPrefs.haptics) {
            uiPrefs.haptics = !uiPrefs.haptics
            Haptics.enabled = uiPrefs.haptics
            refreshAppearance()
        })
        box.addView(Ui.switchRow(this, "Thanh thao tác nhanh", "Dock Áp dụng · Khôi phục · Game ở cuối màn hình", uiPrefs.dock) {
            uiPrefs.dock = !uiPrefs.dock
            dockView?.visibility = if (uiPrefs.dock) View.VISIBLE else View.GONE
            applyInsets()
            refreshAppearance()
        })
        return box
    }

    private fun appearanceCard(): View {
        val c = card("Giao diện & chuyển động")
        c.addView(Ui.tv(this, "Cường độ chuyển động, rung phản hồi, thanh thao tác nhanh.", 12f, Ui.MUTED))
        c.addView(row(Ui.btn(this, "Mở cài đặt giao diện", Ui.GRAY) { appearanceSheet() }))
        return c
    }

    // ---------------------------------------------------------------- Quick Action Dock

    /** Dùng đúng callback Apply/Restore hiện có (cùng một nguồn cho nút chính, dock và palette). */
    private fun applyRun(done: (Boolean) -> Unit) =
        Actions.applyManual(this, prefs.k, prefs.wide, prefs.res, "app") { m -> done(!Actions.failed(m)); toast(m); reload() }

    private fun restoreRun(done: (Boolean) -> Unit) =
        Actions.restore(this, "app") { m -> done(!Actions.failed(m)); toast(m); reload() }

    /** Khởi chạy thao tác dùng chung (palette): bỏ qua nếu thao tác cùng loại đang chạy -> không chạy 2 lần. */
    private fun runShared(key: String, run: ((Boolean) -> Unit) -> Unit) {
        if (MotionMemory.action(key).pending > 0) return
        ActionButton.start(key)
        run { ok -> ActionButton.complete(key, ok) }
    }

    private fun buildDock(): View {
        val ctx = this
        val games = Ui.btn(ctx, "Game ▸", Ui.GRAY) {}
        gamesBtnRef = games
        games.setOnClickListener { openSheet("Hồ sơ game", { gamesBtnRef }, true) { gamesContent() } }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = SurfaceDrawable(0xF2202026.toInt(), Ui.LINE, dp(1).toFloat(), dp(22).toFloat())
            setPadding(dp(8), dp(8), dp(8), dp(8))
            visibility = if (uiPrefs.dock) View.VISIBLE else View.GONE
            fun lp() = LinearLayout.LayoutParams(dp(92), WRAP).apply { setMargins(dp(3), 0, dp(3), 0) }
            addView(Ui.actionBtn(ctx, "Áp dụng", Ui.PINK, "apply", guard = true) { done -> applyRun(done) }, lp())
            addView(Ui.actionBtn(ctx, "Khôi phục", Ui.GRAY, "restore", guard = true) { done -> restoreRun(done) }, lp())
            addView(games, lp())
        }
    }

    // ---------------------------------------------------------------- Command Palette

    private fun commands(): List<Cmd> {
        val out = ArrayList<Cmd>()
        pages.forEachIndexed { i, pg -> out.add(Cmd("Đi tới ${pg.label}", "Trang", "trang chuyen mo", pg.icon) { goPage(i) }) }
        out.add(Cmd("Áp dụng kéo dãn", "Hệ số hiện tại ${fmtK(prefs.k)}", "apply ap dung stretch keo dan ty le", R.drawable.ic_nav_stretch) {
            runShared("apply") { done -> applyRun(done) }
        })
        out.add(Cmd("Khôi phục mặc định", "Trả màn hình về bình thường", "restore reset khoi phuc mac dinh", R.drawable.ic_rotate) {
            runShared("restore") { done -> restoreRun(done) }
        })
        out.add(Cmd("Chẩn đoán", "Xem thông tin kỹ thuật", "chan doan diagnostic debug", R.drawable.ic_toast_info) { showDiag() })
        out.add(Cmd("Bật/tắt Sửa cảm ứng", "Nâng cao · thử nghiệm", "cam ung touch modo", R.drawable.ic_nav_tools) { toggleTouch() })
        out.add(Cmd("Bật/tắt Kéo giãn thật", "Nâng cao · lấp đầy viền đen", "keo gian that chieu that fill vien den", R.drawable.ic_nav_stretch) {
            prefs.fill = !prefs.fill; buildUi()
        })
        out.add(Cmd("Lưu cài đặt hiện tại thành hồ sơ", "Hồ sơ nhanh", "luu ho so profile save", R.drawable.ic_check) { saveCurrent() })
        out.add(Cmd(if (FloatingService.running) "Tắt cửa sổ nổi" else "Bật cửa sổ nổi", "Công cụ", "cua so noi floating overlay", R.drawable.ic_nav_tools) { toggleFloat() })
        out.add(Cmd("Mở Shizuku", "Kết nối", "shizuku ket noi", R.drawable.ic_nav_device) { openShizuku() })
        out.add(Cmd("Tùy chỉnh trang Tổng quan", "Ẩn/hiện, đổi thứ tự card", "tuy chinh dashboard tong quan an hien", R.drawable.ic_sliders) { customizeSheet() })
        out.add(Cmd("Chi tiết thiết bị", "Thông số máy", "chi tiet thiet bi thong so device", R.drawable.ic_nav_device) {
            openSheet("Chi tiết thiết bị", { null }, true) { detailContent() }
        })
        out.add(Cmd("Giao diện & chuyển động", "Reduced / Normal / Enhanced, rung, dock", "giao dien chuyen dong motion rung haptic dock", R.drawable.ic_sliders) { appearanceSheet() })
        Profiles.all(this).forEach { p ->
            out.add(Cmd("Hồ sơ: ${p.name}", p.summary(), "ho so profile bat", R.drawable.ic_play) { toggleProfile(p) })
        }
        val pm = packageManager
        Games.all(this).forEach { g ->
            val label = try { pm.getApplicationInfo(g.pkg, 0).loadLabel(pm).toString() } catch (_: Exception) { g.pkg }
            out.add(Cmd("Chơi: $label", "Game · phiên bảo vệ", "game choi play", R.drawable.ic_play) { startSession(g) })
        }
        return out
    }

    private fun card(title: String, vararg views: View) = GlowCard(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.panel(context)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) }
        addView(Ui.title(context, title))
        views.forEach { addView(it) }
    }

    private fun kv(k: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(3), 0, dp(3))
        addView(Ui.tv(context, k, 13f, Ui.MUTED), LinearLayout.LayoutParams(0, WRAP, 0.42f))
        addView(Ui.tv(context, v, 13f), LinearLayout.LayoutParams(0, WRAP, 0.58f))
    }

    private fun row(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
        v.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
    }

    private fun note(s: String) = Ui.tv(this, s, 12f, Ui.MUTED).apply { setPadding(0, dp(8), 0, 0) }

    // ---- Trạng thái + Shizuku + độ phân giải hiện tại
    private fun statusCard(d: Dev?): View {
        val run = Shizu.running(); val ok = Shizu.granted()
        val msg = when {
            !run -> "Shizuku chưa chạy. Mở Shizuku rồi bấm Start (qua Gỡ lỗi không dây hoặc root)."
            !ok -> "Shizuku đang chạy nhưng chưa cấp quyền cho StretchX."
            else -> "Đã kết nối Shizuku ✔ (quyền shell)"
        }
        val c = card("Trạng thái", Ui.tv(this, msg, 13f, if (ok) Ui.OK else Ui.TEXT))
        if (run && !ok) c.addView(row(Ui.btn(this, "Cấp quyền") { Shizu.requestPermission(100) }))
        if (!run) c.addView(row(Ui.btn(this, "Mở Shizuku") { openShizuku() }))
        c.addView(Ui.tv(this, "ĐỘ PHÂN GIẢI HIỆN TẠI", 11f, Ui.DIM, true).apply { letterSpacing = 0.12f; setPadding(0, dp(12), 0, 0) })
        if (d == null) c.addView(loadingBlock(2))
        if (d != null) {
            val resTv = Ui.tv(this, "", 26f, if (d.overridden) Ui.PINK else Ui.TEXT, true)
            NumberRoll.bind(resTv, "res", floatArrayOf(max(d.curW, d.curH).toFloat(), min(d.curW, d.curH).toFloat())) { v ->
                "${v[0].roundToInt()} × ${v[1].roundToInt()}"
            }
            c.addView(resTv)
            val ap = Profiles.all(this).firstOrNull { Profiles.isActive(this, it) }
            val st = when {
                !prefs.active -> "đang ở mặc định"
                ap != null -> "hồ sơ “${ap.name}” đang bật"
                else -> "chỉnh tay ${fmtK(prefs.k)}"
            }
            c.addView(Ui.tv(this, "${d.curDpi} dpi · $st", 12f, Ui.MUTED))
        }
        return c
    }

    private fun openShizuku() {
        val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
        startActivity(i)
    }

    // ---- Hồ sơ nhanh (Quick presets)
    private fun quickCard(): View {
        val list = Profiles.all(this)
        val c = card("Hồ sơ nhanh")
        c.addView(Ui.tv(this, "Chạm = bật hồ sơ đã lưu (không cần kéo tay); chạm lần nữa = khôi phục. Giữ lâu để sửa / gán phím tắt.", 12f, Ui.MUTED))
        if (list.isEmpty()) c.addView(Ui.emptyIllustration(this, R.drawable.ill_empty_list, "Chưa có hồ sơ nào",
            "Lưu cài đặt hiện tại để bật lại chỉ bằng một chạm.", "+ Lưu hiện tại") { saveCurrent() })
        list.chunked(4).forEach { chunk ->
            val views = chunk.map { chip(it) }.toMutableList<View>()
            while (views.size < 4) views.add(View(this))
            c.addView(row(*views.toTypedArray()))
        }
        c.addView(row(
            Ui.btn(this, "+ Lưu hiện tại", Ui.GRAY) { saveCurrent() },
            Ui.btn(this, "Tùy chỉnh ✎", Ui.GRAY) { manageDialog() },
            Ui.actionBtn(this, "Khôi phục", Ui.GRAY, "restore-quick") { done ->
                Actions.restore(this, "app") { m -> done(!Actions.failed(m)); toast(m); reload() }
            }
        ))
        return c
    }

    private fun chip(p: Profile): Button {
        val on = Profiles.isActive(this, p)
        val b = Ui.choice(this, p.name, on, "chip:${p.id}") { toggleProfile(p) }
        b.maxLines = 1
        b.ellipsize = TextUtils.TruncateAt.END
        b.setOnLongClickListener { profileMenu(p); true }
        return b
    }

    private fun toggleProfile(p: Profile) = Actions.toggleProfile(this, p, "app") { m -> toast(m); reload() }

    private fun saveCurrent() =
        editProfile(Profile(Profiles.newId(), fmtK(prefs.k), prefs.k, prefs.wide, prefs.res, prefs.fill, prefs.touch))

    private fun manageDialog() {
        val list = Profiles.all(this)
        if (list.isEmpty()) { toast("Chưa có hồ sơ nào"); return }
        dlg().setTitle("Hồ sơ đã lưu")
            .setItems(list.map { "${it.name} — ${it.summary()}" }.toTypedArray()) { _, i -> profileMenu(list[i]) }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun profileMenu(p: Profile) {
        fun mark(s: Int) = if (Profiles.forSlot(this, s)?.id == p.id) " ✔" else ""
        val items = arrayOf(
            "Bật ngay", "Sửa hồ sơ",
            "Gán cho phím tắt ô 1${mark(0)}", "Gán cho phím tắt ô 2${mark(1)}", "Gán cho phím tắt ô 3${mark(2)}",
            "Xóa hồ sơ"
        )
        dlg().setTitle("${p.name} — ${p.summary()}")
            .setItems(items) { _, i ->
                when (i) {
                    0 -> toggleProfile(p)
                    1 -> editProfile(p)
                    2, 3, 4 -> { prefs.setTile(i - 2, p.id); toast("Ô phím tắt ${i - 1} → ${p.name}"); buildUi() }
                    5 -> confirmDelete(p)
                }
            }
            .show()
    }

    private fun confirmDelete(p: Profile) {
        dlg().setTitle("Xóa hồ sơ “${p.name}”?")
            .setPositiveButton("Xóa") { _, _ -> Profiles.delete(this, p.id); buildUi() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    /** Hộp thoại tạo / sửa hồ sơ (id chưa có trong danh sách = tạo mới). */
    private fun editProfile(init: Profile) {
        var k = init.k; var wide = init.wide; var res = init.res; var fill = init.fill; var touch = init.touch
        val isNew = Profiles.get(this, init.id) == null

        val name = EditText(this).apply {
            setText(init.name); setTextColor(Ui.TEXT); setHintTextColor(Ui.DIM); hint = "Tên hồ sơ"
            setSingleLine(); textSize = 16f; setSelectAllOnFocus(true)
        }
        val kTv = Ui.tv(this, fmtK(k), 30f, Ui.PINK, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        val infoT = Ui.tv(this, "", 12f, Ui.MUTED)
        fun upd() {
            kTv.text = fmtK(k)
            val p = Engine.planFor(this@MainActivity, k, wide, res)
            infoT.text = if (p == null) "" else "Màn hình ảo ${p.vl}×${p.vs} (${DeviceInfo.aspectLabel(p.vl, p.vs)}) · tối đa ${fmtK(p.maxK)}"
        }
        val sk = SeekBar(this).apply {
            Ui.seek(this)
            max = 50; progress = ((k - 1f) * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { k = 1f + p / 100f; upd() }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        fun dirT() = if (wide) "Hướng: Rộng" else "Hướng: Hẹp"
        fun resT() = "Độ phân giải: $res%"
        fun fillT() = "Kéo giãn thật: " + if (fill) "BẬT" else "TẮT"
        fun touchT() = "Sửa cảm ứng: " + if (touch) "BẬT" else "TẮT"
        val dirB = Ui.btn(this, dirT(), Ui.GRAY) {}
        val resB = Ui.btn(this, resT(), Ui.GRAY) {}
        val fillB = Ui.btn(this, fillT(), Ui.GRAY) {}
        val touchB = Ui.btn(this, touchT(), Ui.GRAY) {}
        dirB.setOnClickListener { wide = !wide; dirB.text = dirT(); upd() }
        resB.setOnClickListener {
            val opts = listOf(100, 90, 80, 70, 60)
            res = opts[(opts.indexOf(res) + 1).mod(opts.size)]
            resB.text = resT(); upd()
        }
        fillB.setOnClickListener { fill = !fill; fillB.text = fillT() }
        touchB.setOnClickListener { touch = !touch; touchB.text = touchT() }
        upd()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(name); addView(kTv); addView(sk); addView(infoT)
            addView(row(dirB, resB)); addView(row(fillB, touchB))
        }
        dlg().setTitle(if (isNew) "Lưu hồ sơ mới" else "Sửa hồ sơ")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Lưu") { _, _ ->
                val nm = name.text.toString().trim().ifEmpty { fmtK(k) }
                Profiles.save(this, Profile(init.id, nm, k, wide, res, fill, touch))
                toast("Đã lưu hồ sơ “$nm”")
                buildUi()
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    // ---- Tính năng chính
    private fun featureCard(): View {
        val c = card("Tính năng chính")
        c.addView(row(
            Ui.feature(this, "CỬA SỔ NỔI", "Chỉnh khi đang chơi", FloatingService.running) { toggleFloat() },
            Ui.feature(this, "CHIẾU THẬT", "Lấp đầy, không viền", prefs.fill) { prefs.fill = !prefs.fill; buildUi() },
            Ui.feature(this, "ÁP DỤNG / KHÔI PHỤC", "Quay về bằng 1 chạm", prefs.active) { quickToggle() }
        ))
        return c
    }

    /** Đang kéo giãn -> khôi phục; chưa -> bật lại hồ sơ dùng gần nhất (hoặc cài đặt chỉnh tay). */
    private fun quickToggle() {
        if (prefs.active) { Actions.restore(this, "app") { m -> toast(m); reload() }; return }
        val p = Profiles.get(this, prefs.lastProfile)
        when {
            p != null -> Actions.applyProfile(this, p, "app") { m -> toast(m); reload() }
            prefs.k > 1f -> Actions.applyManual(this, prefs.k, prefs.wide, prefs.res, "app") { m -> toast(m); reload() }
            else -> toast("Hãy chọn một hồ sơ nhanh hoặc kéo hệ số trước")
        }
    }

    private fun toggleFloat() {
        if (FloatingService.running) stopService(Intent(this, FloatingService::class.java)) else startFloat()
        // giao diện tự cập nhật khi dịch vụ báo đã bật/tắt (FloatingService.onState)
    }

    // ---- Thông tin máy
    private fun deviceCard(d: Dev?): View {
        val c = card("Thông tin thiết bị")
        if (d == null) { c.addView(loadingBlock(7)); return c }
        addDeviceRows(c, d)
        return c
    }

    private fun addDeviceRows(c: LinearLayout, d: Dev) {
        c.addView(kv("Máy", d.model))
        c.addView(kv("Hệ điều hành", d.android))
        c.addView(kv("Chip", d.soc))
        c.addView(kv("ABI", d.abi))
        c.addView(kv("RAM", String.format(Locale.US, "%.1f GB", d.ramGb)))
        c.addView(kv("Độ phân giải gốc", "${wh(d.physW, d.physH)} px"))
        c.addView(kv("Đang dùng", "${wh(d.curW, d.curH)} px" + if (d.overridden) "  (đã chỉnh)" else ""))
        c.addView(kv("Tỉ lệ khung", d.aspect))
        c.addView(kv("Kích thước", String.format(Locale.US, "≈ %.2f inch", d.inches)))
        c.addView(kv("Mật độ (DPI)", if (d.curDpi == d.physDpi) "${d.physDpi}" else "${d.curDpi} (gốc ${d.physDpi})"))
        c.addView(kv("Tần số quét", String.format(Locale.US, "%.0f Hz — hỗ trợ: %s", d.hz, d.modes)))
        if (d.redmagic) c.addView(note("Đã nhận diện thiết bị RedMagic/Nubia ✔"))
        if (!d.viaShizuku) c.addView(note("Chưa có Shizuku: số liệu lấy từ API Android, có thể thiếu thông tin phần đã chỉnh."))
    }

    // ---- Kéo dãn (chỉnh tay)
    private fun stretchCard(d: Dev?): View {
        val big = Ui.tv(this, fmtK(prefs.k), 44f, Ui.PINK, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.02f; Ui.glowText(this, Ui.PINK, 10f)
        }
        NumberRoll.bind(big, "k", floatArrayOf(prefs.k)) { v -> fmtK(v[0]) }
        bigTv = big
        infoTv = Ui.tv(this, "", 13f, Ui.TEXT).apply { setPadding(0, dp(10), 0, dp(2)) }
        val sk = MagneticSeekBar(this).apply {
            Ui.seek(this)
            max = 50; progress = ((prefs.k - 1f) * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { prefs.k = 1f + p / 100f; updatePreview() }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        seek = sk

        val c = card("Kéo dãn màn hình (chỉnh tay)", big, sk)

        // Preset thông minh theo tỉ lệ máy (đồng thời là các vạch "hít" trên thanh trượt)
        val presets = ArrayList<View>()
        val marks = ArrayList<MagneticSeekBar.Mark>()
        presets.add(Ui.choice(this, "Gốc 1.00x", prefs.k <= 1.004f, "preset:orig") { prefs.k = 1f; buildUi() })
        if (d != null) {
            val a = max(d.physW, d.physH).toFloat() / min(d.physW, d.physH)
            listOf("16:9" to 16f / 9, "2:1" to 2f, "21:9" to 21f / 9).forEach { (name, t) ->
                val k = (t / a * 100).roundToInt() / 100f
                if (k in 1.01f..Stretch.MAX) {
                    marks.add(MagneticSeekBar.Mark(name, k))
                    val sel = prefs.wide && kotlin.math.abs(prefs.k - k) < 0.005f
                    presets.add(Ui.choice(this, "$name · ${fmtK(k)}", sel, "preset:$name") { prefs.wide = true; prefs.k = k; buildUi() })
                }
            }
        }
        sk.marks = marks
        c.addView(row(*presets.toTypedArray()))

        c.addView(row(
            Ui.choice(this, "Rộng (viền trên/dưới)", prefs.wide, "dir:wide") { prefs.wide = true; buildUi() },
            Ui.choice(this, "Hẹp (viền trái/phải)", !prefs.wide, "dir:narrow") { prefs.wide = false; buildUi() }
        ))
        // Preset Preview: chỉ VẼ minh hoạ từ Plan (đọc-chỉ), không gửi lệnh nào tới hệ thống
        val pv = RatioPreview(this)
        previewView = pv
        c.addView(pv, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        // Liquid Progress Bar: mức kéo hiện tại so với mức tối đa an toàn của máy (giá trị thật)
        val ub = LiquidBar(this)
        usageBar = ub
        c.addView(Ui.tv(this, "Mức kéo so với tối đa của máy", 11f, Ui.DIM).apply { setPadding(0, dp(6), 0, 0) })
        c.addView(ub, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) })
        c.addView(infoTv!!)
        c.addView(row(
            withBar(Ui.actionBtn(this, "Áp dụng", Ui.PINK, "apply") { done -> applyRun(done) }, "apply"),
            withBar(Ui.actionBtn(this, "Reset mặc định", Ui.GRAY, "restore") { done -> restoreRun(done) }, "restore")
        ))
        c.addView(row(Ui.btn(this, "+ Lưu thành hồ sơ nhanh", Ui.GRAY) { saveCurrent() }))

        // Nâng cao: mở/đóng theo chiều cao; nội dung luôn nằm trong cây view
        val adv = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, 0)
            addView(Ui.switchRow(this@MainActivity, "Kéo giãn thật", "Lấp đầy viền đen bằng SurfaceFlinger", prefs.fill) { prefs.fill = !prefs.fill; buildUi() })
            addView(Ui.switchRow(this@MainActivity, "Sửa cảm ứng", "Modo Pro · thử nghiệm", prefs.touch) { toggleTouch() })
            addView(row(Ui.btn(this@MainActivity, "Chẩn đoán", Ui.GRAY) { showDiag() }))
            addView(row(*listOf(100, 90, 80, 70, 60).map { r ->
                Ui.choice(this@MainActivity, "$r%", prefs.res == r, "res:$r") { prefs.res = r; buildUi() }
            }.toTypedArray()))
            addView(note("Hàng 100%–60% là tỉ lệ độ phân giải (giảm để nhẹ máy hơn). DPI tự bù để giao diện không đổi kích thước."))
            addView(note("“Kéo giãn thật” ép SurfaceFlinger kéo hình ra TOÀN BỘ panel (hết viền đen). Game vẫn thấy màn hình 16:9 nên không bị khóa tỉ lệ. " +
                "Cảm ứng: hệ thống vẫn chấm theo vùng gốc nên 2 cạnh bị chết/lệch. Bật “Sửa cảm ứng” (thử nghiệm, giống Modo Pro của Cat) để app tự phát lại chạm đúng chỗ. " +
                "Kẹt cảm ứng: bấm Volume+ 3 lần liên tiếp để khôi phục khẩn cấp."))
        }
        c.addView(ExpandablePanel(this, "Nâng cao", "adv-stretch", adv),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        updatePreview(false)
        return c
    }

    /** Nút + thanh chất lỏng bám trạng thái thật của thao tác (đang xử lý / thành công / lỗi). */
    private fun withBar(btn: Button, key: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(btn, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(LiquidBar(this@MainActivity).apply { bindAction(key) },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
    }

    /** [live] = true khi người dùng đang kéo (cập nhật số ngay); false khi dựng view (số đã được Number Rolling lo). */
    private fun updatePreview(live: Boolean = true) {
        val d = dev
        val p = Engine.planFor(this, prefs.k, prefs.wide, prefs.res)
        var clamped = false
        if (p != null && abs1(p.k - prefs.k)) {            // bị kẹp theo giới hạn độ phân giải của máy
            prefs.k = p.k; seek?.progress = ((p.k - 1f) * 100).roundToInt(); clamped = true
        }
        if (live || clamped) { bigTv?.text = fmtK(prefs.k); NumberRoll.set("k", floatArrayOf(prefs.k)) }
        if (p != null && d != null) {
            val panelA = max(d.physW, d.physH).toFloat() / min(d.physW, d.physH)
            val real = prefs.fill && !p.isDefault
            previewView?.update(panelA, p.aspect, real,
                "Xem trước · ${DeviceInfo.aspectLabel(p.vl, p.vs)}" +
                    (if (real) " · lấp đầy, không viền" else String.format(Locale.US, " · viền đen ≈ %.1f%%", p.barPct)),
                !live)
            val usage = if (p.maxK > 1f) (p.k - 1f) / (p.maxK - 1f) else 0f
            usageBar?.showValue(usage, "bar:usage", !live)
        }
        infoTv?.text = if (p == null || d == null) "Chưa đọc được thông tin máy" else
            "Màn hình ảo: ${p.vl}×${p.vs} (${DeviceInfo.aspectLabel(p.vl, p.vs)})\n" +
            "DPI: ${if (prefs.active && prefs.baseDpi > 0) prefs.baseDpi else d.curDpi} → ${p.density}\n" +
            (if (prefs.fill && !p.isDefault)
                "Viền đen: 0% — lấp đầy bằng kéo giãn ${if (prefs.wide) "dọc" else "ngang"} ${fmtK(p.k)}\n"
            else
                String.format(Locale.US, "Viền đen: ≈ %.1f%% %s\n", p.barPct, if (prefs.wide) "(trên/dưới)" else "(trái/phải)")) +
            "Mức kéo tối đa an toàn cho máy này: ${fmtK(p.maxK)}"
    }

    private fun abs1(x: Float) = kotlin.math.abs(x) > 0.004f

    private fun toggleTouch() {
        if (prefs.touch) {
            prefs.touch = false; buildUi()
            Shizu.call({ it.setTouch(false, 0, 0, 0, 0, 0, 0, 0, false, false) }) { toast("Đã tắt sửa cảm ứng") }
            return
        }
        dlg()
            .setTitle("Bật sửa cảm ứng (thử nghiệm)")
            .setMessage("App sẽ giữ thiết bị cảm ứng và tự phát lại mỗi lần chạm đúng vị trí trên hình đã kéo giãn, nhờ đó 2 cạnh vẫn ấn/vuốt được.\n\n" +
                "• Cảm ứng bị kẹt: bấm Volume+ 3 lần liên tiếp để khôi phục khẩn cấp (hoặc giữ nguồn để khởi động lại).\n" +
                "• Chạm được phát lại bằng cơ chế chèn sự kiện; game/anti-cheat có thể coi đó là thao tác lạ — tự cân nhắc rủi ro.\n\n" +
                "Sau khi bật, bấm “Áp dụng” để kích hoạt.")
            .setPositiveButton("Bật") { _, _ -> prefs.touch = true; buildUi() }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun showDiag() = Engine.diag { text ->
        dlg().setTitle("Chẩn đoán kéo giãn").setMessage(text).setPositiveButton("Đóng", null).show()
    }

    // ---- DPI khi khôi phục (Reset)
    /** dp theo chiều dài màn hình: px / (dpi / 160). */
    private fun dpOf(d: Dev, dpi: Int) = (max(d.physW, d.physH) * 160f / dpi).roundToInt()
    private fun dpiOf(d: Dev, dp: Int) = (max(d.physW, d.physH) * 160f / dp).roundToInt()

    private fun resetCard(d: Dev?): View {
        val c = card("DPI khi khôi phục")
        if (d == null) { c.addView(loadingBlock(3)); return c }
        val dpi = Engine.restoreDensity(prefs, d).first
        val custom = prefs.resetDpi > 0
        c.addView(Ui.tv(this, "Khi bấm Reset / Khôi phục (hoặc tắt hồ sơ), máy sẽ về mức:", 12f, Ui.MUTED))
        val dpiTv = Ui.tv(this, "", 24f, if (custom) Ui.PINK else Ui.TEXT, true).apply { setPadding(0, dp(6), 0, 0) }
        NumberRoll.bind(dpiTv, "resetDpi", floatArrayOf(dpi.toFloat(), dpOf(d, dpi).toFloat())) { v ->
            "${v[0].roundToInt()} dpi · ${v[1].roundToInt()} dp"
        }
        c.addView(dpiTv)
        c.addView(Ui.tv(this, if (custom) "Do bạn đặt" else "Tự động: lấy DPI trước khi app can thiệp", 11f, Ui.DIM))
        c.addView(row(
            Ui.btn(this, "Đặt DPI…") { editResetDpi(d) },
            Ui.btn(this, "Dùng DPI hiện tại", Ui.GRAY) {
                val cur = if (prefs.active && prefs.baseDpi > 0) prefs.baseDpi else d.curDpi
                prefs.resetDpi = cur
                toast("Khi reset sẽ về $cur dpi (${dpOf(d, cur)} dp)")
                buildUi()
            },
            Ui.btn(this, "Tự động", Ui.GRAY) { prefs.resetDpi = 0; buildUi() }
        ))
        c.addView(note("dp = chiều dài màn hình ÷ (DPI ÷ 160). DPI càng thấp thì dp càng lớn (giao diện nhỏ, nhiều chỗ hơn). " +
            "Nếu sau khi tắt kéo giãn máy không về đúng dp mong muốn, hãy đặt DPI ở đây — app sẽ ép lại nếu ROM tự đổi."))
        return c
    }

    private fun editResetDpi(d: Dev) {
        val start = Engine.restoreDensity(prefs, d).first
        val et = EditText(this).apply {
            setText(start.toString()); setTextColor(Ui.TEXT); setHintTextColor(Ui.DIM); hint = "DPI (ví dụ 320)"
            inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(); textSize = 22f; setSelectAllOnFocus(true)
        }
        val dpTv = Ui.tv(this, "", 14f, Ui.PINK, true).apply { setPadding(0, dp(6), 0, 0) }
        fun upd() {
            val v = et.text.toString().toIntOrNull()
            dpTv.text = if (v == null || v <= 0) "—" else "= ${dpOf(d, v)} dp (chiều dài ${max(d.physW, d.physH)} px)"
        }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(e: Editable?) { upd() }
            override fun beforeTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(t: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        upd()
        val presets = listOf(1200, 1000, 900, 800, 720).map { w ->
            Ui.btn(this, "${w}dp", Ui.GRAY) { et.setText(dpiOf(d, w).toString()); et.setSelection(et.text.length) }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(Ui.tv(context, "Nhập DPI, hoặc chọn nhanh theo độ rộng dp:", 12f, Ui.MUTED))
            addView(et); addView(dpTv)
            addView(row(*presets.take(3).toTypedArray()))
            addView(row(*presets.drop(3).toTypedArray()))
        }
        fun parse(): Int? {
            val v = et.text.toString().toIntOrNull()
            if (v == null || v < 120 || v > 800) { toast("DPI hợp lệ: 120–800"); return null }
            return v
        }
        val b = dlg().setTitle("DPI khi khôi phục")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Lưu") { _, _ ->
                val v = parse()
                if (v != null) { prefs.resetDpi = v; toast("Khi reset sẽ về $v dpi (${dpOf(d, v)} dp)"); buildUi() }
            }
            .setNegativeButton("Hủy", null)
        if (!prefs.active && Shizu.granted()) {
            // Đang ở trạng thái bình thường: cho đặt luôn DPI này ngay bây giờ
            b.setNeutralButton("Lưu & đặt ngay") { _, _ ->
                val v = parse()
                if (v != null) {
                    prefs.resetDpi = v
                    Shizu.exec(if (v == d.physDpi) "wm density reset" else "wm density $v") { toast("Đã đặt $v dpi"); reload() }
                }
            }
        }
        b.show()
    }

    // ---- Game + phiên bảo vệ
    private fun gameCard(): View {
        val games = Games.all(this)
        val c = card("Game · phiên bảo vệ")
        c.addView(Ui.tv(this, "Bấm ▶ Chơi: áp dụng hồ sơ rồi mở game. Khi bạn thoát game, màn hình TỰ về bình thường; quay lại game thì tự kéo giãn lại.", 12f, Ui.MUTED))
        val ses = SessionService.running
        if (ses != null) {
            c.addView(Ui.tv(this, "● Đang có phiên: $ses", 13f, Ui.PINK, true).apply { setPadding(0, dp(8), 0, 0) })
            c.addView(row(Ui.btn(this, "Kết thúc & khôi phục") {
                SessionService.stop(this)
                ui.postDelayed({ reload() }, 1500)
            }))
        }
        if (games.isEmpty()) c.addView(Ui.emptyIllustration(this, R.drawable.ill_empty_game, "Chưa có game nào",
            "Thêm game để chơi với phiên bảo vệ tự khôi phục.", "+ Thêm game") { pickGame() })
        games.forEach { c.addView(gameRow(it)) }
        c.addView(row(Ui.btn(this, "+ Thêm game", Ui.GRAY) { pickGame() }))
        c.addView(note("Nên áp dụng TRƯỚC khi vào game. Đổi độ phân giải giữa trận có thể làm game tải lại hoặc thoát."))
        return c
    }

    private fun gameRow(g: GameEntry): View {
        val pm = packageManager
        val label = try { pm.getApplicationInfo(g.pkg, 0).loadLabel(pm).toString() } catch (_: Exception) { null }
        val icon = iconCache.getOrPut(g.pkg) { try { pm.getApplicationIcon(g.pkg) } catch (_: Exception) { null } }
        val prof = Profiles.get(this, g.profile)
        val on = SessionService.running == g.pkg
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
            addView(ImageView(context).apply {
                if (icon != null) setImageDrawable(icon.constantState?.newDrawable() ?: icon) else setImageResource(R.drawable.ic_logo)
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(6), 0)
                addView(Ui.tv(context, label ?: g.pkg, 14f, if (label != null) Ui.TEXT else Ui.MUTED, true))
                addView(Ui.tv(context, (if (label == null) "chưa cài · " else "") + "Hồ sơ: " + (prof?.name ?: "cài đặt hiện tại"), 11f, Ui.DIM))
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(Ui.btn(context, if (on) "Đang chơi" else "▶ Chơi", if (on) Ui.GRAY else Ui.PINK) { startSession(g) })
            addView(Ui.btn(context, "⋯", Ui.GRAY) { gameMenu(g) }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6) })
        }
    }

    private fun startSession(g: GameEntry) {
        if (packageManager.getLaunchIntentForPackage(g.pkg) == null) { toast("Game chưa cài: ${g.pkg}"); return }
        if (!Shizu.granted()) { toast("Cần bật Shizuku và cấp quyền cho StretchX"); return }
        val prof = Profiles.get(this, g.profile)
            ?: Profile("", "Hiện tại", prefs.k, prefs.wide, prefs.res, prefs.fill, prefs.touch)
        askNotifPermission()
        Actions.applyProfile(this, prof, "game") { m ->
            if (Actions.failed(m)) { toast(m); reload() }
            else {
                toast(m)
                SessionService.start(this, g.pkg, prof)
                ui.postDelayed({ launchPkg(g.pkg); reload() }, 700)
            }
        }
    }

    private fun launchPkg(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) toast("Không mở được $pkg") else startActivity(i)
    }

    private fun gameMenu(g: GameEntry) {
        dlg().setTitle(g.pkg)
            .setItems(arrayOf("Chọn hồ sơ cho game này", "Xóa khỏi danh sách")) { _, i ->
                when (i) {
                    0 -> chooseGameProfile(g)
                    1 -> { Games.remove(this, g.pkg); buildUi() }
                }
            }
            .show()
    }

    private fun chooseGameProfile(g: GameEntry) {
        val list = Profiles.all(this)
        val names = (listOf("Cài đặt hiện tại (chỉnh tay)") + list.map { "${it.name} — ${it.summary()}" }).toTypedArray()
        dlg().setTitle("Hồ sơ cho game")
            .setItems(names) { _, i -> Games.setProfile(this, g.pkg, if (i == 0) "" else list[i - 1].id); buildUi() }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun pickGame() {
        val pm = packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
        dlg().setTitle("Thêm game")
            .setItems(list.map { "${it.second}\n${it.first}" }.toTypedArray()) { _, i -> Games.add(this, list[i].first); buildUi() }
            .show()
    }

    private fun askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    // ---- Phím tắt nhanh (Quick Settings)
    private fun tileCard(): View {
        val c = card("Phím tắt nhanh")
        c.addView(Ui.tv(this, "Bật/tắt kéo giãn ngay từ bảng phím tắt của máy (vuốt từ cạnh trên xuống) — không cần mở app, không cần kéo tay. Mỗi ô gắn với 1 hồ sơ đã lưu.", 12f, Ui.MUTED))
        for (slot in 0..2) {
            val p = Profiles.forSlot(this, slot)
            c.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
                addView(Ui.tv(context, "Ô ${slot + 1}: ${p?.name ?: "—"}", 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(Ui.btn(context, "Đổi hồ sơ", Ui.GRAY) { pickTileProfile(slot) })
                addView(Ui.btn(context, "+ Thêm ô", Ui.GRAY) { addTile(slot) }, LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = dp(6) })
            })
        }
        c.addView(note("Nếu nút “+ Thêm ô” không hiện hộp xác nhận: vuốt xuống bảng phím tắt → biểu tượng bút (Chỉnh sửa) → " +
            "mục “Ô ứng dụng thứ 3” → giữ và kéo “StretchX 1/2/3” lên khu vực phím tắt. Chạm ô = bật, chạm lại = khôi phục. " +
            "Giữ lâu ô để mở app."))
        return c
    }

    private fun pickTileProfile(slot: Int) {
        val list = Profiles.all(this)
        if (list.isEmpty()) { toast("Hãy tạo hồ sơ trước"); return }
        dlg().setTitle("Hồ sơ cho ô ${slot + 1}")
            .setItems(list.map { "${it.name} — ${it.summary()}" }.toTypedArray()) { _, i -> prefs.setTile(slot, list[i].id); buildUi() }
            .show()
    }

    private fun addTile(slot: Int) {
        val cls = listOf(StretchTile1::class.java, StretchTile2::class.java, StretchTile3::class.java)[slot]
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val sbm = getSystemService(android.app.StatusBarManager::class.java)
                val p = Profiles.forSlot(this, slot)
                sbm.requestAddTileService(
                    ComponentName(this, cls), p?.name ?: "StretchX ${slot + 1}",
                    Icon.createWithResource(this, R.drawable.ic_tile), mainExecutor
                ) { r ->
                    toast(when (r) {
                        2 -> "Đã thêm ô vào phím tắt ✔"
                        1 -> "Ô này đã có trong phím tắt"
                        else -> "Chưa thêm — hãy kéo thủ công (xem hướng dẫn bên dưới)"
                    })
                }
                return
            } catch (_: Throwable) {}
        }
        toast("Vuốt xuống bảng phím tắt → biểu tượng bút → kéo “StretchX ${slot + 1}” từ mục “Ô ứng dụng thứ 3” lên trên")
    }

    // ---- Cửa sổ nổi
    private fun floatCard(): View {
        val can = Settings.canDrawOverlays(this)
        val c = card("Cửa sổ nổi (pop-up)", Ui.tv(this, if (can) "Quyền hiển thị trên ứng dụng khác: đã cấp ✔" else "Cần cấp quyền hiển thị trên ứng dụng khác.", 13f, if (can) Ui.OK else Ui.TEXT))
        if (!can) c.addView(row(Ui.btn(this, "Cấp quyền") { grantOverlay() }))
        c.addView(row(
            Ui.btn(this, "Bật cửa sổ nổi") { startFloat() },
            Ui.btn(this, "Tắt", Ui.GRAY) { stopService(Intent(this, FloatingService::class.java)) }
        ))
        c.addView(Ui.switchRow(this, "Ẩn/hiện bằng Volume −", "Nhấn đúp Volume − khi đang chơi (cần Shizuku)", prefs.volHide) {
            prefs.volHide = !prefs.volHide
            if (FloatingService.running)
                startService(Intent(this, FloatingService::class.java).setAction(FloatingService.ACTION_KEYS))
            buildUi()
        })
        c.addView(note("Bong bóng nổi kéo thả được: chạm để mở thanh chỉnh 1.00x–1.50x, hàng hồ sơ nhanh và nút Reset. Thông báo có nút “Reset màn hình”, “Ẩn/Hiện” và “Tắt”."))
        return c
    }

    private fun grantOverlay() {
        if (Shizu.granted()) Shizu.exec("appops set $packageName SYSTEM_ALERT_WINDOW allow") { reload() }
        else startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun startFloat() {
        if (!Settings.canDrawOverlays(this)) { grantOverlay(); toast("Hãy cấp quyền rồi bấm lại"); return }
        askNotifPermission()
        startForegroundService(Intent(this, FloatingService::class.java))
        toast("Đã bật bong bóng nổi")
    }
}
