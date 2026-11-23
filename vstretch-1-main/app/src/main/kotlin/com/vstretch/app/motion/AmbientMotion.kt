package com.vstretch.app.motion

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout

/** Quầng sáng hồng tĩnh (vẽ 1 lần, cache bằng hardware layer) — chỉ dịch chuyển bằng transform. */
private class GlowBlob(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tint = Accent.NORMAL
    private var strong = false

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    override fun onMeasure(w: Int, h: Int) {
        val s = (maxOf(MeasureSpec.getSize(w), MeasureSpec.getSize(h)) * 0.9f).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild() }

    /** Đổi sắc độ (Dynamic Accent) / cường độ (Enhanced) — dựng lại shader một lần, không animate từng khung. */
    fun configure(color: Int, enhanced: Boolean) {
        if (color == tint && enhanced == strong) return
        tint = color; strong = enhanced
        rebuild()
        invalidate()
    }

    private fun rebuild() {
        if (width <= 0) return
        val rgb = tint and 0x00FFFFFF
        val a = if (strong) 0x3C else 0x26
        p.shader = RadialGradient(width / 2f, height / 2f, width / 2f, intArrayOf((a shl 24) or rgb, rgb), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(cv: Canvas) {
        cv.drawCircle(width / 2f, height / 2f, width / 2f, p)
    }
}

/**
 * Ambient Pink Glow: gradient hồng rất nhẹ, trôi chậm (chu kỳ ~8s), không nhấp nháy, không blur nặng.
 * Tắt khi Reduce Motion, khi app ở nền, hoặc khi trang hiện tại không phải Dashboard.
 */
class AmbientGlow(c: Context) : FrameLayout(c) {
    private val blob = GlowBlob(c)
    private var anim: ValueAnimator? = null

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        alpha = 0f
        addView(blob, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
    }

    /** Dynamic Accent: sắc độ hồng theo trạng thái. */
    fun setAccent(color: Int) = blob.configure(color, MotionPreferences.enhanced)

    fun setRunning(on: Boolean) {
        blob.configure(Accent.color, MotionPreferences.enhanced)
        val show = on && !MotionPreferences.reduce
        animate().cancel()
        animate().alpha(if (show) 1f else 0f)
            .setDuration(if (MotionPreferences.reduce) 0L else MotionTokens.SMOOTH)
            .setInterpolator(MotionTokens.STANDARD).start()
        if (show) resumeAnim() else anim?.pause()
    }

    private fun resumeAnim() {
        val a = anim
        if (a == null) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = MotionTokens.AMBIENT
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val v = it.animatedValue as Float
                    blob.translationX = -width * 0.35f * v
                    blob.translationY = height * 0.18f * v
                }
                start()
            }
        } else if (a.isPaused) {
            a.resume()
        } else if (!a.isStarted) {
            a.start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }
}
