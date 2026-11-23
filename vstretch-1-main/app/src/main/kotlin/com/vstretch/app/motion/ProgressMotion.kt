package com.vstretch.app.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import com.vstretch.app.Ui
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Liquid Progress Bar — thanh có bề mặt chất lỏng gợn sóng. Hai chế độ, KHÔNG có % giả:
 *  - Giá trị thật: [showValue] (vd. mức kéo hiện tại / mức tối đa của máy) — chạy mượt tới giá trị mới.
 *  - Gắn với thao tác ([bindAction]): đang xử lý = dải chạy không xác định (không giả tiến độ);
 *    khi backend trả về mới đổ đầy xanh (thành công) hoặc đỏ (lỗi), rồi mờ dần.
 */
class LiquidBar(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.SURFACE_HI }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val clip = Path()
    private val rect = RectF()

    private var mode = IDLE
    private var fill = 0f
    private var color = Ui.PINK
    private var amp = 0f          // biên độ sóng 0..1
    private var phase = 0f
    private var band = 0f         // vị trí dải khi đang xử lý 0..1
    private var alphaF = 1f
    private var key: String? = null
    private var loop: ValueAnimator? = null
    private var fillAnim: ValueAnimator? = null
    private var wave: ValueAnimator? = null
    private var tl: ValueAnimator? = null
    private var tlMs = 0f
    private val rec: ActionRec? get() = key?.let { MotionMemory.action(it) }
    private val idleRun = Runnable { idle() }

    /** Gắn với thao tác [k]: hiển thị theo bản ghi trạng thái thật trong [MotionMemory]. */
    fun bindAction(k: String) {
        key = k
        MotionMemory.addBar(k, this)
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (12 * d).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        clip.reset()
        clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, Path.Direction.CW)
    }

    // ---------------------------------------------------------- chế độ giá trị thật
    fun showValue(v: Float, memKey: String, animate: Boolean = true) {
        stopAll()
        mode = VALUE; color = Ui.PINK; alphaF = 1f
        val target = v.coerceIn(0f, 1f)
        val prev = MotionMemory.rolled.put(memKey, floatArrayOf(v))?.getOrNull(0)
        if (!animate || prev == null || prev == v || MotionPreferences.reduce) { fill = target; amp = 0f; invalidate(); return }
        fill = prev.coerceIn(0f, 1f)
        fillAnim = ValueAnimator.ofFloat(fill, target).apply {
            duration = MotionTokens.SMOOTH
            interpolator = MotionTokens.STANDARD
            addUpdateListener { fill = it.animatedValue as Float; invalidate() }
            start()
        }
        ripple()
    }

    /** Gợn sóng ngắn khi mức thay đổi. */
    private fun ripple() {
        wave?.cancel()
        wave = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = MotionTokens.STANDARD
            addUpdateListener {
                val f = it.animatedValue as Float
                amp = 1f - f; phase = f * 2f * PI.toFloat() * 1.5f; invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { amp = 0f; invalidate() }
            })
            start()
        }
    }

    // ---------------------------------------------------------- chế độ thao tác
    /** Đồng bộ theo bản ghi (an toàn khi view bị dựng lại giữa chừng). */
    fun syncAction() {
        val r = rec ?: return
        val now = SystemClock.uptimeMillis()
        when {
            r.pending > 0 -> showLoading()
            r.result != 0 && now - r.resultAt < TOTAL -> playResult(now - r.resultAt, r.result == 1)
            else -> idle()
        }
    }

    private fun showLoading() {
        stopAll()
        mode = LOADING; color = Ui.PINK; alphaF = 1f; amp = 1f; band = 0.3f
        startLoop()
        invalidate()
    }

    private fun startLoop() {
        if (MotionPreferences.reduce || !isAttachedToWindow || loop?.isRunning == true) return
        loop = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val f = it.animatedValue as Float
                band = f; phase = f * 2f * PI.toFloat() * 2f; invalidate()
            }
            start()
        }
    }

    private fun playResult(elapsed: Long, ok: Boolean) {
        stopAll()
        mode = RESULT; color = if (ok) Ui.OK else Ui.ERR
        if (MotionPreferences.reduce) {
            fill = 1f; amp = 0f; alphaF = 1f
            invalidate()
            postDelayed(idleRun, max(0L, TOTAL - elapsed - 150L))
            return
        }
        val anim = ValueAnimator.ofFloat(elapsed.toFloat(), TOTAL.toFloat())
        tl = anim
        anim.apply {
            duration = max(1L, TOTAL - elapsed)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                tlMs = t
                fill = MotionTokens.EMPHASIZED.getInterpolation(min(1f, t / DRAW))
                amp = max(0f, 1f - t / DRAW)
                phase = t / 90f
                alphaF = if (t > FADE_START) 1f - (t - FADE_START) / (TOTAL - FADE_START) else 1f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { if (tl === anim) idle() }
            })
            start()
        }
    }

    private fun idle() {
        stopAll()
        mode = IDLE; fill = 0f; amp = 0f; alphaF = 1f
        invalidate()
    }

    private fun stopAll() {
        val t = tl
        tl = null
        t?.cancel()
        loop?.cancel(); loop = null
        fillAnim?.cancel(); fillAnim = null
        wave?.cancel(); wave = null
        removeCallbacks(idleRun)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (key != null) syncAction()
    }

    override fun onDetachedFromWindow() {
        stopAll()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (mode == LOADING) { if (isVisible) startLoop() else { loop?.cancel(); loop = null } }
    }

    // ---------------------------------------------------------- vẽ
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        cv.drawRoundRect(rect, h / 2f, h / 2f, track)
        if (alphaF <= 0.01f) return
        cv.save()
        cv.clipPath(clip)
        fillP.color = color
        fillP.alpha = (255 * alphaF).toInt().coerceIn(0, 255)
        if (mode == LOADING) {
            val bw = w * 0.38f
            val x0 = -bw + (w + bw) * band
            liquid(cv, max(0f, x0), min(w, x0 + bw))
        } else if (fill > 0.001f) {
            liquid(cv, 0f, w * fill.coerceIn(0f, 1f))
        }
        cv.restore()
    }

    /** Vùng chất lỏng từ x0..x1, mặt trên gợn sóng. */
    private fun liquid(cv: Canvas, x0: Float, x1: Float) {
        if (x1 - x0 < 1f) return
        val h = height.toFloat()
        val base = h * 0.32f
        val a = amp * h * 0.18f
        val k = (2f * PI.toFloat()) / (60f * d)
        path.rewind()
        path.moveTo(x0, h)
        path.lineTo(x0, base + a * sin(k * x0 + phase))
        var x = x0
        while (x < x1) {
            x = min(x + 4f * d, x1)
            path.lineTo(x, base + a * sin(k * x + phase))
        }
        path.lineTo(x1, h)
        path.close()
        cv.drawPath(path, fillP)
    }

    private companion object {
        const val IDLE = 0
        const val VALUE = 1
        const val LOADING = 2
        const val RESULT = 3
        const val DRAW = 450f
        const val FADE_START = 1000f
        const val TOTAL = 1300L
    }
}

/**
 * Scanning Sweep — dải sáng quét ngang. Chỉ tồn tại khi có tác vụ thật (đọc thông tin máy / kiểm tra kết nối):
 * nơi dùng chỉ thêm view này khi tác vụ đang chạy và gỡ ngay khi xong, lỗi hoặc bị hủy.
 * Hiện dần sau ~300ms để các tác vụ chớp nhoáng không nháy.
 */
class ScanSweep(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.LINE }
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val rect = RectF()
    private var grad: LinearGradient? = null
    private var pos = 0f
    private var anim: ValueAnimator? = null

    init { alpha = 0f }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (3 * d).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val c = Accent.color
        val strong = (c and 0x00FFFFFF) or (0xCC shl 24)
        val none = c and 0x00FFFFFF
        grad = LinearGradient(0f, 0f, w * 0.35f, 0f, intArrayOf(none, strong, none), null, Shader.TileMode.CLAMP)
        band.shader = grad
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    private fun start() {
        animate().cancel()
        animate().alpha(1f).setStartDelay(if (MotionPreferences.reduce) 0L else 300L)
            .setDuration(MotionTokens.NORMAL).start()
        if (MotionPreferences.reduce || anim?.isRunning == true) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            interpolator = MotionTokens.STANDARD
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun stop() {
        anim?.cancel(); anim = null
        animate().cancel()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) { if (isAttachedToWindow) start() } else stop()
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        cv.drawRoundRect(rect, h / 2f, h / 2f, track)
        if (MotionPreferences.reduce) return        // Reduce Motion: chỉ còn vạch tĩnh
        val gw = w * 0.35f
        m.setTranslate(-gw + (w + gw) * pos, 0f)
        grad?.setLocalMatrix(m)
        cv.drawRoundRect(rect, h / 2f, h / 2f, band)
    }
}
