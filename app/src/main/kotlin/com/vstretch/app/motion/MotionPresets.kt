package com.vstretch.app.motion

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import java.lang.ref.WeakReference

/** Trạng thái hiển thị tồn tại qua các lần dựng lại UI (chỉ phục vụ animation, không phải dữ liệu nghiệp vụ). */
class ActionRec {
    var pending = 0
    var result = 0          // 0 chưa có, 1 thành công, 2 thất bại
    var resultAt = 0L
}

object MotionMemory {
    val switches = HashMap<String, Boolean>()
    val selected = HashMap<String, Boolean>()
    val expanded = HashMap<String, Boolean>()
    val rolled = HashMap<String, FloatArray>()
    val visitedPages = HashSet<Int>()
    val actions = HashMap<String, ActionRec>()
    val buttons = HashMap<String, WeakReference<ActionButton>>()

    fun action(key: String): ActionRec = actions.getOrPut(key) { ActionRec() }
}

/** Các animation dựng sẵn dùng lại ở nhiều nơi. */
object MotionPresets {
    private val argb = ArgbEvaluator()

    fun lerpColor(a: Int, b: Int, t: Float): Int = argb.evaluate(t.coerceIn(0f, 1f), a, b) as Int

    fun dp(v: View, x: Int): Float = x * v.resources.displayMetrics.density

    /** Fade + dịch vào vị trí. Tôn trọng Reduce Motion. */
    fun enter(v: View, dx: Float, dy: Float, delay: Long, duration: Long) {
        if (MotionPreferences.reduce) return
        v.animate().cancel()
        v.alpha = 0f
        v.translationX = dx
        v.translationY = dy
        v.animate().alpha(1f).translationX(0f).translationY(0f)
            .setStartDelay(delay).setDuration(duration)
            .setInterpolator(MotionTokens.ENTER).withLayer().start()
    }

    /** Staggered Entrance: card lần lượt, giới hạn số bước. */
    fun stagger(views: List<View>) {
        if (MotionPreferences.reduce) return
        views.forEachIndexed { i, v ->
            val delay = minOf(i, MotionTokens.STAGGER_MAX_STEPS) * MotionTokens.STAGGER_STEP
            enter(v, 0f, dp(v, MotionTokens.STAGGER_SHIFT_DP), delay, MotionTokens.NORMAL)
        }
    }

    /** Card Lift: nhấn -> scale 0.98, nhả -> 1.0. Không chặn click. */
    @SuppressLint("ClickableViewAccessibility")
    fun attachPress(v: View, scale: Boolean = true, onPress: ((Boolean) -> Unit)? = null) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> press(view, true, scale, onPress)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> press(view, false, scale, onPress)
            }
            false
        }
    }

    private fun press(view: View, down: Boolean, scale: Boolean, cb: ((Boolean) -> Unit)?) {
        cb?.invoke(down)
        if (!scale || MotionPreferences.reduce) return
        val s = if (down) MotionTokens.PRESS_SCALE else 1f
        view.animate().scaleX(s).scaleY(s)
            .setDuration(MotionTokens.INSTANT).setInterpolator(MotionTokens.STANDARD).start()
    }
}
