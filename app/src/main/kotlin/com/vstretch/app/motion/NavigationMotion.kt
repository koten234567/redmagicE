package com.vstretch.app.motion

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.vstretch.app.Ui

class NavItem(val label: String, val icon: Int)

/**
 * Điều hướng có Sliding Active Indicator.
 * [vertical] = true: sidebar (tablet, danh sách dọc). false: thanh dưới (điện thoại).
 * Thanh được dựng 1 lần; chuyển mục chỉ animate indicator + màu (không tạo lại).
 */
class NavBar(
    c: Context,
    private val items: List<NavItem>,
    private val vertical: Boolean,
    private val onSelect: (Int) -> Unit
) : FrameLayout(c) {

    private class Cell(
        val view: LinearLayout,
        val icon: ImageView,
        val label: TextView,
        val bg: GradientDrawable
    ) {
        var t = 0f
        var anim: ValueAnimator? = null
    }

    private val d = c.resources.displayMetrics.density
    private val cells = ArrayList<Cell>()
    private val list = LinearLayout(c)
    private val indicator = View(c)
    private val indLen = (if (vertical) 24 else 28) * d
    private val indThick = 3 * d
    private var indicatorMoving = false

    var selected = 0
        private set

    init {
        list.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        items.forEachIndexed { i, item ->
            val bg = GradientDrawable().apply {
                cornerRadius = 10 * d
                setColor(NONE)
            }
            val icon = ImageView(c).apply {
                setImageResource(item.icon)
                setColorFilter(Ui.MUTED)
            }
            val label = TextView(c).apply {
                text = item.label
                textSize = if (vertical) 14f else 11f
                setTextColor(Ui.MUTED)
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                maxLines = 1
            }
            val cell = LinearLayout(c).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = bg
                isClickable = true
                isFocusable = true
                contentDescription = item.label
                val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = 10 * d }
                foreground = RippleDrawable(ColorStateList.valueOf(0x33FF2E93), null, mask)
                if (vertical) {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                    addView(icon, LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt()))
                    addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { leftMargin = (12 * d).toInt() })
                } else {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(0, (6 * d).toInt(), 0, (6 * d).toInt())
                    addView(icon, LinearLayout.LayoutParams((22 * d).toInt(), (22 * d).toInt()))
                    addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = (2 * d).toInt() })
                }
                setOnClickListener {
                    if (i != selected) {
                        select(i, true)
                        onSelect(i)
                    }
                }
            }
            val lp = if (vertical)
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * d).toInt()).apply {
                    topMargin = (2 * d).toInt(); bottomMargin = (2 * d).toInt()
                }
            else
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins((6 * d).toInt(), (6 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
                }
            list.addView(cell, lp)
            cells.add(Cell(cell, icon, label, bg))
        }

        indicator.background = GradientDrawable().apply {
            cornerRadius = 2 * d
            setColor(Ui.PINK)
        }
        val ilp = if (vertical) LayoutParams(indThick.toInt(), indLen.toInt()) else LayoutParams(indLen.toInt(), indThick.toInt())
        ilp.gravity = Gravity.TOP or Gravity.START
        addView(indicator, ilp)
    }

    /** Chọn mục [i]. [animate] = false: đặt ngay (khởi tạo). Không gọi onSelect. */
    fun select(i: Int, animate: Boolean = true) {
        selected = i
        cells.forEachIndexed { idx, cell ->
            val goal = if (idx == i) 1f else 0f
            cell.label.typeface = Typeface.create(if (idx == i) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            if (animate && !MotionPreferences.reduce && cell.t != goal) animateCell(cell, goal) else setCell(cell, goal)
        }
        placeIndicator(i, animate)
    }

    private fun animateCell(cell: Cell, goal: Float) {
        cell.anim?.cancel()
        cell.anim = ValueAnimator.ofFloat(cell.t, goal).apply {
            duration = MotionTokens.NAV_INDICATOR
            interpolator = MotionTokens.STANDARD
            addUpdateListener { setCell(cell, it.animatedValue as Float) }
            start()
        }
    }

    private fun setCell(cell: Cell, t: Float) {
        cell.t = t
        cell.icon.setColorFilter(MotionPresets.lerpColor(Ui.MUTED, Ui.PINK, t))
        cell.label.setTextColor(MotionPresets.lerpColor(Ui.MUTED, Ui.TEXT, t))
        cell.bg.setColor(MotionPresets.lerpColor(NONE, Ui.PINK_TINT, t))
    }

    private fun targetPos(i: Int): Float {
        val v = cells[i].view
        return if (vertical) v.top + (v.height - indLen) / 2f else v.left + (v.width - indLen) / 2f
    }

    private fun placeIndicator(i: Int, animate: Boolean) {
        indicator.animate().cancel()
        indicatorMoving = false
        val tgt = targetPos(i)
        if (!animate || MotionPreferences.reduce || !isLaidOut) {
            if (vertical) indicator.translationY = tgt else indicator.translationX = tgt
            return
        }
        indicatorMoving = true
        val a = indicator.animate().setDuration(MotionTokens.NAV_INDICATOR).setInterpolator(MotionTokens.EMPHASIZED)
        (if (vertical) a.translationY(tgt) else a.translationX(tgt)).withEndAction { indicatorMoving = false }.start()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!indicatorMoving && cells.isNotEmpty()) {
            val tgt = targetPos(selected)
            if (vertical) indicator.translationY = tgt else indicator.translationX = tgt
        }
    }

    private companion object {
        const val NONE = 0x00FF2E93
    }
}

/**
 * Vùng nội dung chính: chuyển trang có hướng (tiến = từ phải, lùi = từ trái),
 * trang cũ fade-out nhẹ. Chỉ animate container trang, không động tới sidebar / cửa sổ.
 */
class PageHost(c: Context) : FrameLayout(c) {
    var current: View? = null
        private set
    private var leaving: View? = null

    /** [dir]: +1 tiến, -1 lùi, 0 không animate. */
    fun show(next: View, dir: Int) {
        leaving?.let { it.animate().cancel(); removeView(it) }
        leaving = null
        val prev = current
        prev?.animate()?.cancel()
        next.animate().cancel()
        addView(next, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        current = next
        if (prev == null || dir == 0) {
            prev?.let { removeView(it) }
            return
        }
        val reduce = MotionPreferences.reduce
        val shift = if (reduce) 0f else dir * MotionTokens.PAGE_SHIFT_DP * resources.displayMetrics.density
        next.alpha = 0f
        next.translationX = shift
        next.animate().alpha(1f).translationX(0f)
            .setDuration(if (reduce) MotionTokens.INSTANT else MotionTokens.MEDIUM)
            .setInterpolator(MotionTokens.ENTER).withLayer().start()
        leaving = prev
        prev.animate().alpha(0f)
            .setDuration(if (reduce) MotionTokens.INSTANT else MotionTokens.FAST)
            .setInterpolator(MotionTokens.EXIT).withLayer()
            .withEndAction {
                removeView(prev)
                if (leaving === prev) leaving = null
            }.start()
    }
}
