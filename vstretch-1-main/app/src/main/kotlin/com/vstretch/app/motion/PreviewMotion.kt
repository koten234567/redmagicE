package com.vstretch.app.motion

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.vstretch.app.Ui
import kotlin.math.max
import kotlin.math.min

/**
 * Preset Preview: minh hoạ tỉ lệ sẽ được áp dụng (viền đen, kéo giãn) TRƯỚC khi bấm Áp dụng.
 * Chỉ VẼ từ số liệu đọc-chỉ (Plan do Engine.planFor tính); không gửi bất kỳ lệnh nào tới hệ thống.
 */
class RatioPreview(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var panelAspect = 0f      // cạnh dài / cạnh ngắn của panel
    private var targetAspect = 0f     // tỉ lệ màn hình ảo
    private var shown = 0f            // tỉ lệ đang vẽ (animate)
    private var fillMode = false
    private var label = ""
    private var anim: ValueAnimator? = null

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (104 * d).toInt())
    }

    /** [animate] = true: chuyển mượt từ tỉ lệ đã vẽ trước đó (nhớ qua các lần dựng lại). */
    fun update(panel: Float, content: Float, fill: Boolean, text: String, animate: Boolean) {
        panelAspect = panel; targetAspect = content; fillMode = fill; label = text
        anim?.cancel()
        val prev = MotionMemory.rolled.put("preview", floatArrayOf(content))?.getOrNull(0)
        if (!animate || prev == null || prev == content || MotionPreferences.reduce || prev <= 0f) {
            shown = content; invalidate(); return
        }
        shown = prev
        anim = ValueAnimator.ofFloat(prev, content).apply {
            duration = MotionTokens.SMOOTH
            interpolator = MotionTokens.STANDARD
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        if (panelAspect <= 0f || shown <= 0f) return
        val ph = min(70f * d, (w - 32f * d) / panelAspect)
        val pw = ph * panelAspect
        val left = (w - pw) / 2f
        val top = 4f * d
        // panel (nền đen = vùng viền đen)
        r.set(left, top, left + pw, top + ph)
        p.style = Paint.Style.FILL; p.color = 0xFF08080A.toInt()
        cv.drawRoundRect(r, 8f * d, 8f * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = Ui.TRACK_OFF
        cv.drawRoundRect(r, 8f * d, 8f * d, p)

        // vùng nội dung
        val cw: Float; val ch: Float
        if (fillMode) { cw = pw; ch = ph }
        else if (shown >= panelAspect) { cw = pw; ch = pw / shown }
        else { ch = ph; cw = ph * shown }
        val cl = left + (pw - cw) / 2f
        val ct = top + (ph - ch) / 2f
        r.set(cl, ct, cl + cw, ct + ch)
        p.style = Paint.Style.FILL; p.color = Ui.SURFACE_HI
        cv.drawRoundRect(r, 6f * d, 6f * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Accent.color
        cv.drawRoundRect(r, 6f * d, 6f * d, p)

        // "cảnh game": hình tròn bị kéo thành elip khi kéo giãn thật
        val base = min(cw, ch) * 0.22f
        val sx = if (fillMode && shown > 0f) max(1f, panelAspect / shown) else 1f
        p.style = Paint.Style.FILL; p.color = Ui.PINK_SOFT
        cv.save()
        cv.translate(cl + cw / 2f, ct + ch / 2f)
        cv.scale(sx, 1f)
        cv.drawCircle(0f, 0f, base, p)
        cv.restore()

        // nhãn
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER
        p.textSize = 11f * d; p.color = Ui.MUTED
        cv.drawText(label, w / 2f, top + ph + 22f * d, p)
        p.textAlign = Paint.Align.LEFT
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }
}
