package com.vstretch.app.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import com.vstretch.app.R
import com.vstretch.app.SurfaceDrawable
import com.vstretch.app.Ui
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Animated Preset Selection: viền chuyển hồng + một đường sáng chạy quanh thẻ đúng một lần.
 * Chỉ chạy khi trạng thái chọn THỰC SỰ đổi giữa hai lần dựng (so với [MotionMemory]).
 */
object SelectionMotion {
    fun bind(v: View, d: SurfaceDrawable, key: String, selected: Boolean) {
        val prev = MotionMemory.selected.put(key, selected)
        d.selectT = if (selected) 1f else 0f
        if (prev == null || prev == selected || MotionPreferences.reduce) return

        d.selectT = if (prev) 1f else 0f
        ValueAnimator.ofFloat(d.selectT, if (selected) 1f else 0f).apply {
            duration = MotionTokens.PRESET_SELECT
            interpolator = MotionTokens.STANDARD
            addUpdateListener { d.selectT = it.animatedValue as Float }
            start()
        }
        if (selected) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = MotionTokens.PRESET_SELECT
                interpolator = MotionTokens.STANDARD
                addUpdateListener { d.runT = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(a: Animator) { d.runT = -1f }
                })
                start()
            }
        }
    }
}

/**
 * Elastic Toggle. Công tắc CHỈ phản chiếu state thực: khi code gọi dựng lại với state mới,
 * thumb mới trượt từ trạng thái cũ sang trạng thái mới. Animation không tự đổi dữ liệu lưu trữ.
 */
class ElasticSwitch(c: Context, private val key: String, on: Boolean) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val target = on
    private var t = if (on) 1f else 0f
    private var pressT = 0f
    private var pendingAnim = false

    init {
        val prev = MotionMemory.switches.put(key, on)
        if (prev != null && prev != on && !MotionPreferences.reduce) {
            t = if (prev) 1f else 0f
            pendingAnim = true
        }
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((52 * d).toInt(), (30 * d).toInt())

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pendingAnim) {
            pendingAnim = false
            ValueAnimator.ofFloat(t, if (target) 1f else 0f).apply {
                duration = MotionTokens.TOGGLE
                interpolator = MotionTokens.ELASTIC
                addUpdateListener { t = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    /** Phản hồi tức thời khi chạm (hàng chứa công tắc gọi). */
    fun press(down: Boolean) {
        if (MotionPreferences.reduce) { pressT = if (down) 1f else 0f; invalidate(); return }
        ValueAnimator.ofFloat(pressT, if (down) 1f else 0f).apply {
            duration = MotionTokens.INSTANT
            interpolator = MotionTokens.STANDARD
            addUpdateListener { pressT = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val r = h / 2f
        val tc = t.coerceIn(0f, 1f)
        track.set(d, d, w - d, h - d)
        p.style = Paint.Style.FILL
        p.color = MotionPresets.lerpColor(Ui.TRACK_OFF, Ui.PINK, tc)
        cv.drawRoundRect(track, r, r, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * d
        p.color = MotionPresets.lerpColor(Ui.DIM, Ui.PINK, tc)
        cv.drawRoundRect(track, r, r, p)

        val kr = (r - 5f * d) * (1f + 0.12f * pressT)
        val cx = (r + (w - 2f * r) * t).coerceIn(r * 0.75f, w - r * 0.75f)
        p.style = Paint.Style.FILL
        if (tc > 0.05f) {
            p.color = Ui.PINK; p.alpha = (0x40 * tc).toInt()
            cv.drawCircle(cx, h / 2f, kr + 3f * d, p)
        }
        p.color = MotionPresets.lerpColor(Ui.MUTED, Ui.TEXT, tc)
        cv.drawCircle(cx, h / 2f, kr, p)
    }
}

/**
 * Magnetic Slider: vẫn là SeekBar (logic/giá trị giữ nguyên). Chỉ VẼ thêm các vạch preset bên dưới rãnh;
 * khi thumb ở gần preset (±0.02x) vạch sáng lên + vòng gợi ý quanh rãnh. Không tự đổi giá trị.
 */
class MagneticSeekBar(c: Context) : SeekBar(c) {
    class Mark(val label: String, val k: Float)

    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var hint = FloatArray(0)
    private var lastNear = -1
    private var hintAnim: ValueAnimator? = null

    var marks: List<Mark> = emptyList()
        set(v) {
            field = v
            hint = FloatArray(v.size)
            lastNear = -1
            invalidate()
        }

    init {
        setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (26 * d).toInt())
    }

    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, MeasureSpec.makeMeasureSpec((64 * d).toInt(), MeasureSpec.EXACTLY))
    }

    private fun idx(m: Mark): Int = ((m.k - 1f) * 100f).roundToInt().coerceIn(0, max)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (marks.isEmpty() || max <= 0) return
        val l = paddingLeft.toFloat()
        val avail = (width - paddingLeft - paddingRight).toFloat()
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val now = progress

        var near = -1
        marks.forEachIndexed { i, m -> if (near < 0 && abs(now - idx(m)) <= MotionTokens.SNAP_RANGE) near = i }
        if (near != lastNear) { lastNear = near; retarget(near) }

        marks.forEachIndexed { i, m ->
            val x = l + avail * idx(m) / max.toFloat()
            val h = if (i < hint.size) hint[i] else 0f
            // vạch
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = (1.5f + h) * d
            p.color = MotionPresets.lerpColor(Ui.DIM, Ui.PINK, h)
            val y0 = cy + 12f * d
            canvas.drawLine(x, y0, x, y0 + (5f + 4f * h) * d, p)
            // vòng gợi ý quanh rãnh khi thumb lại gần
            if (h > 0.02f) {
                p.strokeWidth = 1.5f * d
                p.color = Ui.PINK; p.alpha = (140 * h).toInt()
                canvas.drawCircle(x, cy, (9f + 2f * h) * d, p)
            }
            // nhãn
            p.style = Paint.Style.FILL; p.textSize = 10f * d
            p.color = MotionPresets.lerpColor(Ui.DIM, Ui.PINK_SOFT, h)
            canvas.drawText(m.label, x, height - 6f * d, p)
        }
    }

    private fun retarget(near: Int) {
        if (MotionPreferences.reduce) {
            for (i in hint.indices) hint[i] = if (i == near) 1f else 0f
            return
        }
        hintAnim?.cancel()
        val from = hint.copyOf()
        hintAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MotionTokens.FAST
            interpolator = MotionTokens.STANDARD
            addUpdateListener { a ->
                val f = a.animatedValue as Float
                for (i in hint.indices) {
                    if (i >= from.size) break
                    val to = if (i == near) 1f else 0f
                    hint[i] = from[i] + (to - from[i]) * f
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        hintAnim?.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * Expandable Advanced Panel: mở/đóng theo chiều cao + fade, chevron xoay.
 * Nội dung luôn được giữ trong cây view (không mount/unmount) nên dữ liệu nhập không mất.
 * Trạng thái mở được nhớ qua các lần dựng lại (key).
 */
class ExpandablePanel(c: Context, title: String, private val key: String, private val content: View) : LinearLayout(c) {
    private val d = c.resources.displayMetrics.density
    private val chevron = ImageView(c)
    private val body = FrameLayout(c)
    private var open = MotionMemory.expanded[key] ?: false
    private var anim: ValueAnimator? = null

    init {
        orientation = VERTICAL
        val r = 12 * d
        val head = LinearLayout(c).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
            background = Ui.ripple(SurfaceDrawable(Ui.SURFACE_HI, Ui.LINE, d, r), 0x33FF2E93, r)
            isClickable = true
            contentDescription = title
            addView(Ui.tv(c, title, 14f, Ui.TEXT, true), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            chevron.setImageResource(R.drawable.ic_chevron_down)
            chevron.setColorFilter(Ui.MUTED)
            chevron.rotation = if (open) 180f else 0f
            addView(chevron, LayoutParams((20 * d).toInt(), (20 * d).toInt()))
            setOnClickListener { toggle() }
        }
        addView(head, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, if (open) LayoutParams.WRAP_CONTENT else 0))
        body.alpha = if (open) 1f else 0f
        applyA11y()
    }

    private fun applyA11y() {
        body.importantForAccessibility = if (open) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        body.descendantFocusability = if (open) FOCUS_AFTER_DESCENDANTS else FOCUS_BLOCK_DESCENDANTS
    }

    private fun finish() {
        val lp = body.layoutParams
        lp.height = if (open) LayoutParams.WRAP_CONTENT else 0
        body.layoutParams = lp
        body.alpha = if (open) 1f else 0f
    }

    private fun toggle() {
        open = !open
        MotionMemory.expanded[key] = open
        applyA11y()
        anim?.cancel()
        if (MotionPreferences.reduce) {
            chevron.rotation = if (open) 180f else 0f
            finish()
            return
        }
        content.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val from = body.height
        val to = if (open) content.measuredHeight else 0
        val lp = body.layoutParams
        chevron.animate().rotation(if (open) 180f else 0f)
            .setDuration(MotionTokens.EXPAND).setInterpolator(MotionTokens.STANDARD).start()
        anim = ValueAnimator.ofInt(from, to).apply {
            duration = MotionTokens.EXPAND
            interpolator = MotionTokens.STANDARD
            addUpdateListener { a ->
                lp.height = a.animatedValue as Int
                body.layoutParams = lp
                body.alpha = if (open) a.animatedFraction else 1f - a.animatedFraction
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { finish() }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
