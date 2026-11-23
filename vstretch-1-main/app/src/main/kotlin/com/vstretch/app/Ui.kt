package com.vstretch.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.vstretch.app.motion.ActionButton
import com.vstretch.app.motion.ElasticSwitch
import com.vstretch.app.motion.Haptics
import com.vstretch.app.motion.MotionPresets
import com.vstretch.app.motion.SelectionMotion
import com.vstretch.app.motion.Skeleton
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * THEME "StretchX Neon Pink" — Hybrid Gaming × Minimal.
 * Nền charcoal, bề mặt nâng, nhấn Neon Pink; glow chỉ xuất hiện ở thành phần đang chọn.
 * Chỉ chứa phần HIỂN THỊ — không có logic chức năng.
 */
object Ui {
    // ---- bảng màu ----
    const val BG = 0xFF121214.toInt()          // charcoal
    const val SURFACE = 0xFF1B1B1F.toInt()     // thẻ / sidebar
    const val SURFACE_HI = 0xFF25252B.toInt()  // bề mặt nâng (nút, ô)
    const val LINE = 0xFF30303A.toInt()        // viền mảnh, tương phản thấp
    const val TRACK_OFF = 0xFF3B3B45.toInt()

    const val PINK = 0xFFFF2E93.toInt()        // Neon Pink (nhấn chính)
    const val PINK_SOFT = 0xFFD98AB5.toInt()   // Muted Pink (nhấn phụ)
    const val PINK_DEEP = 0xFF3A1B2C.toInt()   // nền mục đang chọn
    const val PINK_TINT = 0x29FF2E93           // lớp phủ chọn (ARGB)
    const val ON_PINK = 0xFF16060E.toInt()     // chữ trên nền Neon Pink (đủ tương phản)

    const val TEXT = 0xFFF5F5F7.toInt()        // cấp 1
    const val MUTED = 0xFFA3A3AE.toInt()       // cấp 2
    const val DIM = 0xFF82828D.toInt()         // cấp 3

    const val OK = 0xFF34D399.toInt()
    const val WARN = 0xFFFBBF24.toInt()
    const val ERR = 0xFFF87171.toInt()

    /** Nút phụ (giữ tên cũ để các nơi gọi vẫn dùng được). */
    const val GRAY = SURFACE_HI

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).roundToInt()

    fun round(color: Int, r: Float, stroke: Int = 0, sw: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = r
        if (sw > 0) setStroke(sw, stroke)
    }

    // ---- chữ (Roboto hệ thống; "medium" cho nhấn) ----
    fun tv(c: Context, s: CharSequence, sp: Float = 14f, color: Int = TEXT, bold: Boolean = false) =
        TextView(c).apply {
            text = s; textSize = sp; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    fun glowText(t: TextView, color: Int, radiusDp: Float = 8f) {
        t.setShadowLayer(radiusDp * t.resources.displayMetrics.density, 0f, 0f, color)
    }

    /** Tiêu đề thẻ: vạch hồng nhỏ + chữ medium. */
    fun title(c: Context, s: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(c, 12))
        addView(View(c).apply { background = round(PINK, dp(c, 2).toFloat()) },
            LinearLayout.LayoutParams(dp(c, 3), dp(c, 14)).apply { rightMargin = dp(c, 8) })
        addView(tv(c, s, 15f, TEXT, true))
    }

    /** Logo + wordmark STRETCHX. */
    fun brand(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(c).apply { setImageResource(R.drawable.ic_logo) }, LinearLayout.LayoutParams(dp(c, 44), dp(c, 32)))
        val word = SpannableString("STRETCHX").apply {
            setSpan(ForegroundColorSpan(PINK), 7, 8, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        addView(tv(c, word, 18f, TEXT, true).apply { letterSpacing = 0.08f; setPadding(dp(c, 8), 0, 0, 0) })
    }

    // ---- nền ----
    fun panel(c: Context): Drawable = SurfaceDrawable(SURFACE, LINE, dp(c, 1).toFloat(), dp(c, 14).toFloat())

    /** Bảng cửa sổ nổi: bán trong suốt, viền hồng, glow nhẹ (đang là thành phần cần chú ý). */
    fun overlayPanel(c: Context): Drawable =
        SurfaceDrawable(0xF21B1B1F.toInt(), 0x99FF2E93.toInt(), dp(c, 1).toFloat(), dp(c, 14).toFloat(),
            glow = 0x66FF2E93, glowW = dp(c, 4).toFloat(), selectT0 = 1f)

    /** Bong bóng nổi: khối hồng, sáng viền. */
    fun bubbleBg(c: Context): Drawable =
        SurfaceDrawable(0xF0FF2E93.toInt(), 0, 0f, dp(c, 16).toFloat(),
            glow = 0xFFFFB3D5.toInt(), glowW = dp(c, 3).toFloat(), selectT0 = 1f, selFill = 0xF0FF2E93.toInt())

    /** Ripple Neon Pink bo theo [radius]; [content] có thể null (chỉ hiệu ứng chạm). */
    fun ripple(content: Drawable?, color: Int, radius: Float): Drawable {
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = radius }
        return RippleDrawable(ColorStateList.valueOf(color), content, mask)
    }

    // ---- nút ----
    fun styleButton(b: Button, s: String, primary: Boolean) {
        val c = b.context
        val r = dp(c, 12).toFloat()
        val surf = if (primary) SurfaceDrawable(PINK, 0, 0f, r)
                   else SurfaceDrawable(SURFACE_HI, LINE, dp(c, 1).toFloat(), r)
        b.background = ripple(surf, if (primary) 0x55FFFFFF else 0x55FF2E93, r)
        b.stateListAnimator = null
        b.text = s; b.isAllCaps = false; b.textSize = 13f
        b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        b.setTextColor(if (primary) ON_PINK else TEXT)
        b.minHeight = 0; b.minimumHeight = dp(c, 44); b.minWidth = 0; b.minimumWidth = dp(c, 44)
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0)
    }

    /** fill == PINK -> nút chính (hồng). Mọi màu khác -> nút phụ (bề mặt nâng, viền mảnh). */
    fun btn(c: Context, s: String, fill: Int = PINK, onClick: () -> Unit) = Button(c).apply {
        styleButton(this, s, fill == PINK)
        setOnClickListener { onClick() }
    }

    /**
     * Nút có trạng thái Processing Ring -> Success Morph. [run] nhận hàm `finish(ok)`;
     * gọi finish đúng khi backend đã trả kết quả (không giả lập tiến độ).
     */
    fun actionBtn(c: Context, s: String, fill: Int, key: String, guard: Boolean = false, run: (finish: (Boolean) -> Unit) -> Unit): Button {
        val primary = fill == PINK
        return ActionButton(c, key, primary, guard).apply {
            styleButton(this, s, primary)
            setOnClickListener { begin(run) }
        }
    }

    /** Nút chọn (preset, hướng, hồ sơ nhanh): được chọn -> viền hồng + đường sáng chạy quanh 1 lần. */
    fun choice(c: Context, s: String, selected: Boolean, key: String, onClick: () -> Unit): Button {
        val r = dp(c, 12).toFloat()
        val surf = SurfaceDrawable(SURFACE_HI, LINE, dp(c, 1).toFloat(), r, selFill = PINK_DEEP)
        return Button(c).apply {
            background = surf       // phản hồi riêng: viền hồng + đường sáng + co nhẹ (không dùng ripple)
            stateListAnimator = null
            text = s; isAllCaps = false; textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (selected) TEXT else MUTED)
            minHeight = 0; minimumHeight = dp(c, 44); minWidth = 0; minimumWidth = dp(c, 44)
            setPadding(dp(c, 12), 0, dp(c, 12), 0)
            SelectionMotion.bind(this, surf, key, selected)
            MotionPresets.attachPress(this)
            setOnClickListener { Haptics.tick(this); onClick() }
        }
    }

    /** Công tắc đàn hồi + nhãn + mô tả. onClick đổi trạng thái ở code gọi; công tắc chỉ phản chiếu state thực. */
    fun switchRow(c: Context, label: String, sub: String, on: Boolean, onClick: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10))
        val sw = ElasticSwitch(c, "sw:$label", on)
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv(c, label, 14f, if (on) TEXT else MUTED, true))
            addView(tv(c, sub, 12f, DIM))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(sw)
        isClickable = true
        MotionPresets.attachPress(this, scale = false) { down -> sw.press(down) }   // phản hồi riêng: thumb co lại
        setOnClickListener { Haptics.tick(this); onClick() }
    }

    /** Ô tính năng lớn: tiêu đề + mô tả + trạng thái BẬT/TẮT (có ký hiệu, không chỉ màu), chạm để đổi. */
    fun feature(c: Context, title: String, sub: String, on: Boolean, onClick: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12))
        val r = dp(c, 12).toFloat()
        val surf = SurfaceDrawable(SURFACE_HI, LINE, dp(c, 1).toFloat(), r,
            glow = 0x66FF2E93, glowW = dp(c, 3).toFloat(), selFill = PINK_DEEP)
        background = surf
        SelectionMotion.bind(this, surf, "feat:$title", on)
        addView(tv(c, title, 12f, if (on) TEXT else MUTED, true).apply { letterSpacing = 0.03f })
        addView(tv(c, sub, 11f, DIM).apply { setPadding(0, dp(c, 2), 0, dp(c, 6)) })
        addView(tv(c, if (on) "● BẬT" else "○ TẮT", 11f, if (on) PINK else DIM, true))
        isClickable = true
        MotionPresets.attachPress(this)
        setOnClickListener { Haptics.tick(this); onClick() }
    }

    /** Thanh trượt: rãnh tối, phần đã kéo hồng, núm sáng viền hồng. */
    fun seek(s: SeekBar) {
        val c = s.context
        s.progressTintList = ColorStateList.valueOf(PINK)
        s.progressBackgroundTintList = ColorStateList.valueOf(LINE)
        s.thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(TEXT); setStroke(dp(c, 3), PINK); setSize(dp(c, 22), dp(c, 22))
        }
    }

    // ---- trạng thái trống / lỗi / skeleton ----
    fun emptyState(c: Context, msg: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(c, 12), 0, dp(c, 4))
        addView(ImageView(c).apply { setImageResource(R.drawable.ic_toast_warning); setColorFilter(WARN) },
            LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)))
        addView(tv(c, msg, 13f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 8), 0, dp(c, 4)) })
        if (actionLabel != null && onAction != null) addView(btn(c, actionLabel, GRAY) { onAction() })
    }

    /** Trạng thái trống có minh hoạ vector nhẹ (hồ sơ game, tìm kiếm, danh sách). */
    fun emptyIllustration(c: Context, drawable: Int, title: String, sub: String,
                          actionLabel: String? = null, onAction: (() -> Unit)? = null) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(c, 8), dp(c, 14), dp(c, 8), dp(c, 10))
        addView(ImageView(c).apply { setImageResource(drawable); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            LinearLayout.LayoutParams(dp(c, 96), dp(c, 96)))
        addView(tv(c, title, 14f, TEXT, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 6), 0, 0) })
        addView(tv(c, sub, 12f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(c, 4), 0, dp(c, 6)) })
        if (actionLabel != null && onAction != null) addView(btn(c, actionLabel, GRAY) { onAction() })
    }

    /** Skeleton giữ đúng kích thước dự kiến. [wDp] = 0 -> rộng hết chiều ngang. */
    fun skeleton(c: Context, wDp: Int, hDp: Int): View = Skeleton(c, wDp, hDp).apply {
        layoutParams = LinearLayout.LayoutParams(
            if (wDp > 0) dp(c, wDp) else LinearLayout.LayoutParams.MATCH_PARENT, dp(c, hDp)
        ).apply { topMargin = dp(c, 8) }
    }
}

/**
 * Bề mặt bo góc: nền + viền mảnh + glow nội bộ tuỳ chọn.
 * [selectT] 0..1: chuyển viền sang hồng (và hiện glow) khi được chọn.
 * [runT] 0..1: đường sáng chạy quanh viền đúng một lần; < 0 = không vẽ.
 */
class SurfaceDrawable(
    private val fill: Int,
    private val line: Int = 0,
    private val lineW: Float = 0f,
    private val radius: Float,
    private val glow: Int = 0,
    private val glowW: Float = 0f,
    private val selFill: Int = fill,
    private val selLine: Int = Ui.PINK,
    selectT0: Float = 0f
) : Drawable() {
    var selectT: Float = selectT0
        set(v) { field = v; invalidateSelf() }
    var runT: Float = -1f
        set(v) { field = v; invalidateSelf() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val seg = Path()
    private val rect = RectF()
    private val meas = PathMeasure()
    private var len = 0f

    override fun onBoundsChange(b: Rect) {
        val i = lineW / 2f
        rect.set(b.left + i, b.top + i, b.right - i, b.bottom - i)
        val r = minOf(radius, rect.width() / 2f, rect.height() / 2f).coerceAtLeast(0f)
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        meas.setPath(path, false)
        len = meas.length
    }

    override fun draw(cv: Canvas) {
        p.style = Paint.Style.FILL
        p.color = MotionPresets.lerpColor(fill, selFill, selectT)
        cv.drawPath(path, p)

        if (glow != 0 && glowW > 0f && selectT > 0.01f) {
            cv.save(); cv.clipPath(path)
            p.style = Paint.Style.STROKE
            val a = Color.alpha(glow)
            for ((k, al) in listOf(2.0f to 0.14f, 1.4f to 0.20f, 0.9f to 0.30f, 0.5f to 0.45f)) {
                p.strokeWidth = glowW * 2f * k
                p.color = glow
                p.alpha = (a * al * selectT).toInt().coerceIn(0, 255)
                cv.drawPath(path, p)
            }
            cv.restore()
        }

        if (lineW > 0f) {
            p.style = Paint.Style.STROKE; p.strokeWidth = lineW
            p.color = MotionPresets.lerpColor(if (line == 0) fill else line, selLine, selectT)
            cv.drawPath(path, p)
        }

        if (runT in 0f..1f && len > 0f) {
            val segLen = len * 0.28f
            val start = runT * len
            val end = start + segLen
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = (lineW * 2f).coerceAtLeast(2f)
            p.color = Ui.PINK_SOFT
            p.alpha = (255 * sin(PI * runT)).toInt().coerceIn(0, 255)
            seg.reset(); meas.getSegment(start, minOf(end, len), seg, true); cv.drawPath(seg, p)
            if (end > len) { seg.reset(); meas.getSegment(0f, end - len, seg, true); cv.drawPath(seg, p) }
            p.strokeCap = Paint.Cap.BUTT
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
