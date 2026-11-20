package com.vstretch.app.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.vstretch.app.R
import com.vstretch.app.SurfaceDrawable
import com.vstretch.app.Ui
import java.lang.ref.WeakReference
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// =====================================================================================
// 5.1 Processing Ring + 5.2 Success Morph
// =====================================================================================

/**
 * Nút Apply / Restore. Trạng thái LOADING hiển thị khi người dùng bấm; chỉ chuyển sang
 * Success (dấu tick vẽ dần) hoặc Fail (dấu X) khi backend THỰC SỰ trả kết quả ([complete]).
 * Không giả lập % tiến độ. Không chặn thêm thao tác ngoài quy tắc của logic hiện tại.
 * Trạng thái nằm trong [MotionMemory] nên vẫn tiếp tục đúng khi UI được dựng lại.
 */
class ActionButton(c: Context, private val key: String, private val primary: Boolean) : Button(c) {
    private val d = c.resources.displayMetrics.density
    private val rec = MotionMemory.action(key)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val oval = RectF()

    private var mode = IDLE
    private var spin = 0f
    private var spinAnim: ValueAnimator? = null
    private var tl: ValueAnimator? = null
    private var tlMs = 0f
    private var savedColors: ColorStateList? = null
    private var ok = true
    private val idleRun = Runnable { idle() }

    init {
        MotionMemory.buttons[key] = WeakReference(this)
        p.strokeWidth = 2f * d
    }

    private val ink: Int get() = if (primary) Ui.ON_PINK else Ui.TEXT

    /** Gọi khi người dùng bấm: hiện vòng xử lý rồi chạy [run]; [run] phải gọi finish(ok) khi backend trả về. */
    fun begin(run: ((Boolean) -> Unit) -> Unit) {
        rec.pending++
        rec.result = 0
        MotionMemory.buttons[key] = WeakReference(this)
        sync()
        run { success -> complete(key, success) }
    }

    /** Đồng bộ hiển thị theo bản ghi (an toàn khi view bị dựng lại giữa chừng). */
    fun sync() {
        val now = SystemClock.uptimeMillis()
        when {
            rec.pending > 0 -> showLoading()
            rec.result != 0 && now - rec.resultAt < TOTAL -> playResult(now - rec.resultAt, rec.result == 1)
            else -> idle()
        }
    }

    private fun hideText() {
        if (savedColors == null) { savedColors = textColors; setTextColor(Color.TRANSPARENT) }
    }

    private fun showText() {
        savedColors?.let { setTextColor(it); savedColors = null }
    }

    private fun stopTimeline() {
        val old = tl
        tl = null
        old?.cancel()
        removeCallbacks(idleRun)
    }

    private fun showLoading() {
        stopTimeline()
        mode = LOADING
        hideText()
        startSpin()
        invalidate()
    }

    private fun startSpin() {
        if (MotionPreferences.reduce || !isAttachedToWindow) return
        if (spinAnim?.isRunning == true) return
        spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = MotionTokens.SPINNER
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { spin = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopSpin() {
        spinAnim?.cancel()
        spinAnim = null
    }

    private fun playResult(elapsed: Long, success: Boolean) {
        stopTimeline()
        stopSpin()
        mode = RESULT
        ok = success
        hideText()
        if (MotionPreferences.reduce) {
            tlMs = DRAW.toFloat()   // giữ feedback nhưng không animate: hiện glyph hoàn chỉnh
            postDelayed(idleRun, max(0L, TOTAL - elapsed - 150L))
            invalidate()
            return
        }
        tlMs = elapsed.toFloat()
        val anim = ValueAnimator.ofFloat(elapsed.toFloat(), TOTAL.toFloat())
        tl = anim
        anim.apply {
            duration = max(1L, TOTAL - elapsed)
            interpolator = LinearInterpolator()
            addUpdateListener { tlMs = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { if (tl === anim) idle() }
            })
            start()
        }
    }

    private fun idle() {
        stopTimeline()
        stopSpin()
        mode = IDLE
        showText()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sync()
    }

    override fun onDetachedFromWindow() {
        stopSpin()
        stopTimeline()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (mode == LOADING) { if (isVisible) startSpin() else stopSpin() }
    }

    private fun ease(x: Float): Float = MotionTokens.EMPHASIZED.getInterpolation(x.coerceIn(0f, 1f))

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mode == IDLE) return
        val cx = width / 2f
        val cy = height / 2f
        val r = 9f * d
        oval.set(cx - r, cy - r, cx + r, cy + r)
        p.color = ink

        if (mode == LOADING) {
            p.alpha = 60
            canvas.drawCircle(cx, cy, r, p)
            p.alpha = 255
            canvas.drawArc(oval, spin - 90f, 100f, false, p)
            return
        }

        // RESULT: vòng xử lý khép lại + glyph vẽ dần, giữ, rồi mờ dần về nút bình thường
        val t = tlMs
        val e = ease(t / DRAW)
        val fade = if (t > HOLD_END) 1f - (t - HOLD_END) / (TOTAL - HOLD_END) else 1f
        val a = (255 * fade).toInt().coerceIn(0, 255)

        p.alpha = ((60 + 195 * e) * fade).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, r, p)

        val arcA = (1f - min(1f, t / (DRAW * 0.4f))) * fade
        if (arcA > 0.01f) {
            p.alpha = (255 * arcA).toInt().coerceIn(0, 255)
            canvas.drawArc(oval, spin - 90f, 100f, false, p)
        }

        p.alpha = a
        val g = ease((t - DRAW * 0.2f) / (DRAW * 0.8f))
        if (ok) {
            drawPoly(canvas, cx, cy, floatArrayOf(-4.5f, 0.5f, -1.5f, 3.5f, 4.5f, -3.5f), g)
        } else {
            drawPoly(canvas, cx, cy, floatArrayOf(-3.5f, -3.5f, 3.5f, 3.5f), min(1f, g * 2f))
            if (g > 0.5f) drawPoly(canvas, cx, cy, floatArrayOf(3.5f, -3.5f, -3.5f, 3.5f), (g - 0.5f) * 2f)
        }
    }

    /** Vẽ đường gấp khúc (toạ độ dp quanh tâm) đến [prog] 0..1 chiều dài. */
    private fun drawPoly(cv: Canvas, cx: Float, cy: Float, pts: FloatArray, prog: Float) {
        if (prog <= 0f) return
        val n = pts.size / 2
        var total = 0f
        for (i in 1 until n) total += hypot(pts[i * 2] - pts[(i - 1) * 2], pts[i * 2 + 1] - pts[(i - 1) * 2 + 1])
        var left = total * prog.coerceIn(0f, 1f)
        for (i in 1 until n) {
            val x0 = pts[(i - 1) * 2]; val y0 = pts[(i - 1) * 2 + 1]
            val x1 = pts[i * 2]; val y1 = pts[i * 2 + 1]
            val seg = hypot(x1 - x0, y1 - y0)
            val f = if (seg <= 0f) 1f else min(1f, left / seg)
            cv.drawLine(cx + x0 * d, cy + y0 * d, cx + (x0 + (x1 - x0) * f) * d, cy + (y0 + (y1 - y0) * f) * d, p)
            left -= seg
            if (left <= 0f) break
        }
    }

    companion object {
        private const val IDLE = 0
        private const val LOADING = 1
        private const val RESULT = 2
        private const val DRAW = 450L            // MotionTokens.EMPHASIS
        private const val HOLD_END = 1150L
        private const val TOTAL = 1300L

        /** Backend đã trả kết quả. Cập nhật bản ghi rồi đồng bộ nút còn sống (nếu có). */
        fun complete(key: String, success: Boolean) {
            val r = MotionMemory.action(key)
            if (r.pending > 0) r.pending--
            if (r.pending == 0) {
                r.result = if (success) 1 else 2
                r.resultAt = SystemClock.uptimeMillis()
            }
            MotionMemory.buttons[key]?.get()?.let { if (it.isAttachedToWindow) it.sync() }
        }
    }
}

// =====================================================================================
// 5.3 Smart Toast
// =====================================================================================

enum class ToastType { SUCCESS, ERROR, WARNING, INFO }

/**
 * Thông báo toàn cục: trượt lên + fade, có icon (hình dạng khác nhau, không chỉ màu).
 * Không chặn chạm vào nội dung bên dưới (trừ toast lỗi: chạm để đóng). Lỗi tồn tại lâu hơn.
 */
class ToastHost(c: Context) : FrameLayout(c) {
    private val d = c.resources.displayMetrics.density
    private val h = Handler(Looper.getMainLooper())
    private var current: View? = null
    private val hide = Runnable { dismiss() }

    fun show(msg: CharSequence, type: ToastType) {
        h.removeCallbacks(hide)
        current?.let { it.animate().cancel(); removeView(it) }
        val v = build(msg, type)
        addView(v, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (24 * d).toInt()
            leftMargin = (16 * d).toInt(); rightMargin = (16 * d).toInt()
        })
        current = v
        if (MotionPreferences.reduce) {
            v.alpha = 1f
        } else {
            v.alpha = 0f
            v.translationY = 20f * d
            v.animate().alpha(1f).translationY(0f)
                .setDuration(MotionTokens.NORMAL).setInterpolator(MotionTokens.ENTER).start()
        }
        h.postDelayed(hide, when (type) {
            ToastType.ERROR -> 6000L
            ToastType.WARNING -> 4200L
            else -> 2600L
        })
    }

    private fun dismiss() {
        val v = current ?: return
        val done = Runnable { removeView(v); if (current === v) current = null }
        if (MotionPreferences.reduce) { done.run(); return }
        v.animate().alpha(0f).translationY(12f * d)
            .setDuration(MotionTokens.FAST).setInterpolator(MotionTokens.EXIT)
            .withEndAction(done).start()
    }

    private fun build(msg: CharSequence, type: ToastType): View {
        val c = context
        val (icon, color) = when (type) {
            ToastType.SUCCESS -> R.drawable.ic_toast_success to Ui.OK
            ToastType.ERROR -> R.drawable.ic_toast_error to Ui.ERR
            ToastType.WARNING -> R.drawable.ic_toast_warning to Ui.WARN
            ToastType.INFO -> R.drawable.ic_toast_info to Ui.PINK
        }
        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (11 * d).toInt(), (16 * d).toInt(), (11 * d).toInt())
            background = SurfaceDrawable(Ui.SURFACE_HI, color, 1f * d, 14f * d)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            addView(ImageView(c).apply { setImageResource(icon); setColorFilter(color) },
                LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()))
            addView(TextView(c).apply {
                text = msg; textSize = 13f; setTextColor(Ui.TEXT); maxLines = 3
                maxWidth = (460 * d).toInt()
                setPadding((10 * d).toInt(), 0, 0, 0)
            })
            if (type == ToastType.ERROR) {
                isClickable = true
                setOnClickListener { h.removeCallbacks(hide); dismiss() }
            }
        }
    }
}

// =====================================================================================
// 5.4 Connection Pulse
// =====================================================================================

enum class LinkState { CONNECTED, CONNECTING, DISCONNECTED, ERROR }

/** Chấm trạng thái Shizuku. Connected ổn định = tĩnh (chỉ 1 nhịp khi vừa kết nối). */
class PulseDot(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var state = LinkState.DISCONNECTED
    private var pulse = 0f
    private var anim: ValueAnimator? = null

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((18 * d).toInt(), (18 * d).toInt())

    fun setState(s: LinkState) {
        val old = state
        state = s
        stop()
        when (s) {
            LinkState.CONNECTING -> loop()
            LinkState.CONNECTED -> if (old != LinkState.CONNECTED) once()
            else -> pulse = 0f
        }
        invalidate()
    }

    private fun loop() {
        if (MotionPreferences.reduce) { pulse = 0.6f; return }
        if (!isAttachedToWindow) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionTokens.PULSE
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun once() {
        if (MotionPreferences.reduce) { pulse = 0f; return }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionTokens.PULSE
            interpolator = MotionTokens.STANDARD
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { pulse = 0f; invalidate() }
            })
            start()
        }
    }

    private fun stop() {
        anim?.cancel()
        anim = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (state == LinkState.CONNECTING && anim == null) loop()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (state == LinkState.CONNECTING) { if (isVisible) { if (anim == null) loop() } else stop() }
    }

    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = 4.5f * d
        val color = when (state) {
            LinkState.CONNECTED -> Ui.OK
            LinkState.CONNECTING -> Ui.PINK
            LinkState.DISCONNECTED -> Ui.DIM
            LinkState.ERROR -> Ui.ERR
        }
        if (pulse > 0f && (state == LinkState.CONNECTING || state == LinkState.CONNECTED)) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d
            p.color = color; p.alpha = ((1f - pulse) * 150).toInt().coerceIn(0, 255)
            cv.drawCircle(cx, cy, r + pulse * 4f * d, p)
        }
        when (state) {
            LinkState.DISCONNECTED -> {                       // vòng rỗng
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f * d; p.color = color
                cv.drawCircle(cx, cy, r, p)
            }
            LinkState.ERROR -> {                              // chấm đặc + dấu "!"
                p.style = Paint.Style.FILL; p.color = color
                cv.drawCircle(cx, cy, r + 1.5f * d, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f * d; p.strokeCap = Paint.Cap.ROUND; p.color = Ui.BG
                cv.drawLine(cx, cy - 2.6f * d, cx, cy + 0.6f * d, p)
                cv.drawPoint(cx, cy + 2.8f * d, p)
                p.strokeCap = Paint.Cap.BUTT
            }
            else -> {                                         // chấm đặc
                p.style = Paint.Style.FILL; p.color = color
                cv.drawCircle(cx, cy, r, p)
            }
        }
    }
}

/** Chip trạng thái kết nối: chấm + nhãn chữ (không phụ thuộc riêng vào màu). */
class ConnectionChip(c: Context) : LinearLayout(c) {
    private val dot = PulseDot(c)
    private val label = TextView(c)

    init {
        val d = c.resources.displayMetrics.density
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((10 * d).toInt(), (6 * d).toInt(), (12 * d).toInt(), (6 * d).toInt())
        background = SurfaceDrawable(Ui.SURFACE_HI, Ui.LINE, d, 20f * d)
        addView(dot)
        label.textSize = 12f
        label.setTextColor(Ui.TEXT)
        label.maxLines = 1
        label.setPadding((6 * d).toInt(), 0, 0, 0)
        addView(label)
    }

    fun update(s: LinkState, text: String) {
        label.text = text
        contentDescription = text
        dot.setState(s)
    }
}

// =====================================================================================
// 6.2 Number Rolling
// =====================================================================================

/**
 * Số chạy mượt, CHỈ theo giá trị thực từ state: gọi khi dựng view; nếu giá trị khác lần trước
 * thì chạy từ giá trị cũ -> mới. Không tạo hiệu ứng "đã đổi thông số" trước khi Apply hoàn tất.
 */
object NumberRoll {
    fun bind(tv: TextView, key: String, values: FloatArray, fmt: (FloatArray) -> CharSequence) {
        val prev = MotionMemory.rolled.put(key, values.copyOf())
        tv.text = fmt(values)
        if (prev == null || prev.size != values.size || MotionPreferences.reduce || prev.contentEquals(values)) return
        val cur = FloatArray(values.size)
        tv.text = fmt(prev)
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionTokens.SMOOTH
            interpolator = MotionTokens.STANDARD
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                for (i in cur.indices) cur[i] = prev[i] + (values[i] - prev[i]) * f
                tv.text = fmt(cur)
            }
            start()
        }
    }

    /** Ghi nhớ giá trị hiện tại mà không animate (vd. khi đang kéo thanh trượt). */
    fun set(key: String, values: FloatArray) {
        MotionMemory.rolled[key] = values.copyOf()
    }
}

// =====================================================================================
// 6.5 Skeleton Loading
// =====================================================================================

/** Khối giữ chỗ có shimmer rất nhẹ. Chỉ dùng cho dữ liệu bất đồng bộ chưa tải xong. */
class Skeleton(c: Context, private val wDp: Int, private val hDp: Int) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val base = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.SURFACE_HI }
    private val sh = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val rect = RectF()
    private var grad: LinearGradient? = null

    override fun onMeasure(w: Int, h: Int) {
        val ww = if (wDp > 0) (wDp * d).toInt() else MeasureSpec.getSize(w)
        setMeasuredDimension(ww, (hDp * d).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        grad = LinearGradient(0f, 0f, w * 0.6f, 0f, intArrayOf(0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        sh.shader = grad
    }

    override fun onDraw(cv: Canvas) {
        val r = 8f * d
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        cv.drawRoundRect(rect, r, r, base)
        if (!MotionPreferences.reduce) {
            val phase = (SystemClock.uptimeMillis() % MotionTokens.SHIMMER).toFloat() / MotionTokens.SHIMMER
            val gw = width * 0.6f
            m.setTranslate(-gw + (width + gw) * phase, 0f)
            grad?.setLocalMatrix(m)
            cv.drawRoundRect(rect, r, r, sh)
            postInvalidateOnAnimation()   // dừng tự nhiên khi view không còn được vẽ (ẩn / nền)
        }
    }
}
