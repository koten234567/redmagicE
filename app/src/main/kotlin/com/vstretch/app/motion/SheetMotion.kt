package com.vstretch.app.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.vstretch.app.R
import com.vstretch.app.SurfaceDrawable
import com.vstretch.app.Ui
import java.text.Normalizer

/** Giới hạn bề rộng tối đa của nội dung và căn giữa (cha phải đặt width = WRAP_CONTENT). */
class CenterMaxWidth(c: Context, private val maxW: Int) : FrameLayout(c) {
    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        super.onMeasure(MeasureSpec.makeMeasureSpec(minOf(s, maxW), MeasureSpec.EXACTLY), h)
    }
}

/** LinearLayout giới hạn rộng/cao tối đa (cha cho AT_MOST). */
private class CappedLinear(c: Context) : LinearLayout(c) {
    var maxW = Int.MAX_VALUE
    var maxH = Int.MAX_VALUE
    override fun onMeasure(w: Int, h: Int) {
        val ws = MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(w), maxW), MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(h), maxH), MeasureSpec.AT_MOST)
        super.onMeasure(ws, hs)
    }
}

/**
 * Smart Bottom Sheet + Shared Element Transition.
 * - Có [origin]: thẻ nguồn "mở rộng" liên tục thành bảng (vỏ bề mặt phóng/dịch bằng transform, không nhấp nháy),
 *   đóng lại thì thu về đúng thẻ nếu thẻ còn tồn tại ([originProvider]); nếu không thì trượt xuống.
 * - Không có origin: trượt từ dưới lên.
 * - Back / chạm nền / kéo tay nắm xuống để đóng; chừa vùng an toàn (system insets) qua [setInsets].
 * - Xoay màn hình: gọi [cancelAnimations] rồi dựng lại bằng show(animate = false).
 */
@SuppressLint("ClickableViewAccessibility")
class BottomSheet(c: Context) : FrameLayout(c) {
    private val d = c.resources.displayMetrics.density
    private val bleed = (24 * d).toInt()        // phần đáy kéo ra ngoài màn hình để góc bo dưới không lộ
    private val scrim = View(c)
    private val panel = CappedLinear(c)
    private val handleRow = FrameLayout(c)
    private val titleTv = TextView(c)
    private val scroll = ScrollView(c)
    private val bodyHost = FrameLayout(c)
    private var ghost: View? = null
    private var anim: ValueAnimator? = null
    private var dragStart = 0f

    var showing: Boolean = false
        private set
    var originProvider: (() -> Rect?)? = null
    var onClosed: (() -> Unit)? = null

    init {
        visibility = GONE
        scrim.setBackgroundColor(0xB3000000.toInt())
        scrim.alpha = 0f
        scrim.setOnClickListener { dismiss(true) }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        panel.orientation = LinearLayout.VERTICAL
        panel.maxW = (640 * d).toInt()
        panel.background = SurfaceDrawable(Ui.SURFACE, Ui.LINE, d, 20f * d)
        panel.isClickable = true

        handleRow.contentDescription = "Kéo xuống để đóng"
        handleRow.addView(View(c).apply { background = Ui.round(Ui.TRACK_OFF, 2f * d) },
            LayoutParams((36 * d).toInt(), (4 * d).toInt(), Gravity.CENTER))
        handleRow.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dragStart = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - dragStart).coerceAtLeast(0f)
                    panel.translationY = dy
                    scrim.alpha = (1f - dy / (panel.height + 1f)).coerceIn(0.2f, 1f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (panel.translationY > 96f * d) {
                        dismiss(true, false)
                    } else {
                        panel.animate().translationY(0f).setDuration(MotionTokens.FAST).start()
                        scrim.animate().alpha(1f).setDuration(MotionTokens.FAST).start()
                    }
                    true
                }
                else -> false
            }
        }
        panel.addView(handleRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (28 * d).toInt()))

        titleTv.textSize = 17f
        titleTv.setTextColor(Ui.TEXT)
        titleTv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        titleTv.setPadding((20 * d).toInt(), 0, (20 * d).toInt(), (8 * d).toInt())
        panel.addView(titleTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        bodyHost.setPadding((16 * d).toInt(), (4 * d).toInt(), (16 * d).toInt(), (8 * d).toInt())
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = OVER_SCROLL_NEVER
        scroll.addView(bodyHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        addView(panel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        setInsets(0, 0, 0, 0)
    }

    override fun onMeasure(w: Int, h: Int) {
        panel.maxH = (MeasureSpec.getSize(h) * 0.88f).toInt()
        super.onMeasure(w, h)
    }

    /** Vùng an toàn (cutout / thanh hệ thống) để bảng không bị che. */
    fun setInsets(l: Int, t: Int, r: Int, b: Int) {
        val lp = panel.layoutParams as LayoutParams
        lp.leftMargin = l; lp.rightMargin = r
        lp.topMargin = t + (24 * d).toInt()
        lp.bottomMargin = -bleed
        panel.layoutParams = lp
        panel.setPadding(0, 0, 0, b + bleed)
    }

    fun replaceContent(v: View) {
        bodyHost.removeAllViews()
        bodyHost.addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun show(title: String, content: View, origin: Rect?, animate: Boolean) {
        stopAnims()
        titleTv.text = title
        titleTv.visibility = if (title.isEmpty()) GONE else VISIBLE
        replaceContent(content)
        visibility = VISIBLE
        showing = true
        panel.translationY = 0f
        if (!animate) { scrim.alpha = 1f; panel.alpha = 1f; return }
        if (MotionPreferences.reduce) {
            scrim.alpha = 0f; panel.alpha = 0f
            scrim.animate().alpha(1f).setDuration(MotionTokens.INSTANT).start()
            panel.animate().alpha(1f).setDuration(MotionTokens.INSTANT).start()
            return
        }
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(MotionTokens.MEDIUM).setInterpolator(MotionTokens.STANDARD).start()
        if (origin != null) {
            panel.alpha = 0f
        } else {
            panel.alpha = 1f
            panel.translationY = 800f * d       // ngoài màn hình cho tới khi biết kích thước thật
        }
        val vto = viewTreeObserver
        vto.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val o = if (vto.isAlive) vto else viewTreeObserver
                o.removeOnPreDrawListener(this)
                enter(origin)
                return true
            }
        })
    }

    private fun enter(origin: Rect?) {
        if (!showing) return
        if (origin == null) {
            panel.translationY = (panel.height + bleed).toFloat()
            panel.animate().translationY(0f).setDuration(MotionTokens.MEDIUM).setInterpolator(MotionTokens.ENTER).start()
        } else {
            morph(origin, Rect(panel.left, panel.top, panel.right, panel.bottom), true)
        }
    }

    /** Vỏ bề mặt chạy liên tục từ rect [from] sang [to] (transform GPU, không relayout nội dung). */
    private fun morph(from: Rect, to: Rect, opening: Boolean) {
        removeGhost()
        val bw = maxOf(1, from.width()); val bh = maxOf(1, from.height())
        val g = View(context)
        g.background = SurfaceDrawable(Ui.SURFACE, Ui.LINE, d, 14f * d)
        g.pivotX = 0f; g.pivotY = 0f
        addView(g, LayoutParams(bw, bh, Gravity.TOP or Gravity.START))
        ghost = g
        fun place(f: Float) {
            val l = from.left + (to.left - from.left) * f
            val t = from.top + (to.top - from.top) * f
            val r = from.right + (to.right - from.right) * f
            val b = from.bottom + (to.bottom - from.bottom) * f
            g.translationX = l; g.translationY = t
            g.scaleX = (r - l) / bw; g.scaleY = (b - t) / bh
        }
        place(0f)
        val va = ValueAnimator.ofFloat(0f, 1f)
        va.duration = MotionTokens.SMOOTH
        va.interpolator = MotionTokens.EMPHASIZED
        va.addUpdateListener {
            val f = it.animatedValue as Float
            place(f)
            if (opening) {
                panel.alpha = ((f - 0.55f) / 0.45f).coerceIn(0f, 1f)
                g.alpha = 1f - ((f - 0.7f) / 0.3f).coerceIn(0f, 1f)
            } else {
                panel.alpha = (1f - f * 2.2f).coerceIn(0f, 1f)
                g.alpha = if (f < 0.3f) f / 0.3f else 1f
            }
        }
        va.addListener(object : AnimatorListenerAdapter() {
            private var canceled = false
            override fun onAnimationCancel(a: Animator) { canceled = true }
            override fun onAnimationEnd(a: Animator) {
                if (canceled) return
                removeGhost()
                if (opening) panel.alpha = 1f else finishClose()
            }
        })
        anim = va
        va.start()
    }

    fun dismiss(animate: Boolean = true, useOrigin: Boolean = true) {
        if (!showing) return
        showing = false
        stopAnims()
        if (!animate || MotionPreferences.reduce) { finishClose(); return }
        scrim.animate().alpha(0f).setDuration(MotionTokens.MEDIUM).setInterpolator(MotionTokens.EXIT).start()
        val origin = if (useOrigin) originProvider?.invoke() else null
        if (origin != null && panel.width > 0) {
            morph(Rect(panel.left, panel.top, panel.right, panel.bottom), origin, false)
        } else {
            panel.alpha = 1f
            panel.animate().translationY((panel.height + bleed).toFloat())
                .setDuration(MotionTokens.MEDIUM).setInterpolator(MotionTokens.EXIT)
                .withEndAction { finishClose() }.start()
        }
    }

    private fun finishClose() {
        removeGhost()
        visibility = GONE
        scrim.alpha = 0f
        panel.alpha = 1f
        panel.translationY = 0f
        bodyHost.removeAllViews()
        onClosed?.invoke()
    }

    private fun removeGhost() {
        ghost?.let { removeView(it) }
        ghost = null
    }

    private fun stopAnims() {
        anim?.cancel()
        anim = null
        panel.animate().cancel()
        scrim.animate().cancel()
        removeGhost()
    }

    /** Dừng mọi animation (xoay màn hình / huỷ view) — không để listener mồ côi. */
    fun cancelAnimations() = stopAnims()

    fun onBack(): Boolean {
        if (!showing) return false
        dismiss(true)
        return true
    }

    override fun onDetachedFromWindow() {
        stopAnims()
        super.onDetachedFromWindow()
    }
}

/** Một lệnh trong Command Palette — [action] gọi lại đúng chức năng hiện có. */
class Cmd(val title: String, val sub: String, val keys: String, val icon: Int, val action: () -> Unit) {
    val hay: String by lazy { CommandPalette.norm("$title $sub $keys") }
}

/**
 * Command Palette: tìm chức năng / công cụ / cài đặt (không dấu cũng tìm được). Mở bằng nút tìm kiếm.
 * Chọn kết quả (hoặc Enter) -> đóng palette rồi gọi đúng hành động hiện có.
 */
class CommandPalette(c: Context) : FrameLayout(c) {
    private val d = c.resources.displayMetrics.density
    private val scrim = View(c)
    private val panel = CappedLinear(c)
    private val input = EditText(c)
    private val list = LinearLayout(c)
    private val scroll = ScrollView(c)
    private var current: List<Cmd> = emptyList()

    var provider: () -> List<Cmd> = { emptyList() }
    var showing: Boolean = false
        private set

    init {
        visibility = GONE
        scrim.setBackgroundColor(0xB3000000.toInt())
        scrim.alpha = 0f
        scrim.setOnClickListener { dismiss() }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        panel.orientation = LinearLayout.VERTICAL
        panel.maxW = (560 * d).toInt()
        panel.background = SurfaceDrawable(Ui.SURFACE, Ui.LINE, d, 16f * d)
        panel.setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (8 * d).toInt())
        panel.isClickable = true

        input.hint = "Tìm chức năng, công cụ, cài đặt…"
        input.setHintTextColor(Ui.DIM)
        input.setTextColor(Ui.TEXT)
        input.textSize = 15f
        input.setSingleLine()
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.background = SurfaceDrawable(Ui.SURFACE_HI, Ui.LINE, d, 12f * d)
        input.setPadding((14 * d).toInt(), (12 * d).toInt(), (14 * d).toInt(), (12 * d).toInt())
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { refresh(s?.toString() ?: "") }
        })
        input.setOnEditorActionListener { _, _, _ ->
            current.firstOrNull()?.let { dismiss(); it.action() }
            true
        }
        panel.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        list.orientation = LinearLayout.VERTICAL
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        panel.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = (8 * d).toInt() })

        addView(panel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        setInsets(0, 0, 0)
    }

    override fun onMeasure(w: Int, h: Int) {
        panel.maxH = (MeasureSpec.getSize(h) * 0.55f).toInt()   // chừa chỗ cho bàn phím
        super.onMeasure(w, h)
    }

    fun setInsets(l: Int, t: Int, r: Int) {
        val lp = panel.layoutParams as LayoutParams
        lp.leftMargin = l + (12 * d).toInt(); lp.rightMargin = r + (12 * d).toInt()
        lp.topMargin = t + (12 * d).toInt()
        panel.layoutParams = lp
    }

    private fun refresh(q: String) {
        val toks = norm(q).trim().split(' ').filter { it.isNotEmpty() }
        current = provider().filter { cmd -> toks.all { cmd.hay.contains(it) } }
        list.removeAllViews()
        if (current.isEmpty()) {
            list.addView(Ui.emptyIllustration(context, R.drawable.ill_empty_search, "Không tìm thấy kết quả",
                "Thử từ khóa khác, ví dụ “áp dụng”, “dpi”, “game”."))
            return
        }
        current.forEach { list.addView(rowFor(it)) }
    }

    private fun rowFor(cmd: Cmd): View {
        val c = context
        return LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * d).toInt(), (11 * d).toInt(), (10 * d).toInt(), (11 * d).toInt())
            addView(ImageView(c).apply { setImageResource(cmd.icon); setColorFilter(Ui.PINK_SOFT) },
                LinearLayout.LayoutParams((20 * d).toInt(), (20 * d).toInt()))
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((12 * d).toInt(), 0, 0, 0)
                addView(Ui.tv(c, cmd.title, 14f, Ui.TEXT, true))
                if (cmd.sub.isNotEmpty()) addView(Ui.tv(c, cmd.sub, 12f, Ui.DIM))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            isClickable = true
            MotionPresets.attachPress(this)
            setOnClickListener { dismiss(); cmd.action() }
        }
    }

    fun show() {
        if (showing) return
        showing = true
        panel.animate().cancel(); scrim.animate().cancel()
        visibility = VISIBLE
        input.setText("")
        refresh("")
        if (MotionPreferences.reduce) {
            scrim.alpha = 1f; panel.alpha = 1f; panel.translationY = 0f
        } else {
            scrim.alpha = 0f; panel.alpha = 0f; panel.translationY = -16f * d
            scrim.animate().alpha(1f).setDuration(MotionTokens.NORMAL).setInterpolator(MotionTokens.ENTER).start()
            panel.animate().alpha(1f).translationY(0f).setDuration(MotionTokens.NORMAL).setInterpolator(MotionTokens.ENTER).start()
        }
        input.requestFocus()
        input.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun dismiss() {
        if (!showing) return
        showing = false
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
        panel.animate().cancel(); scrim.animate().cancel()
        if (MotionPreferences.reduce) { visibility = GONE; return }
        scrim.animate().alpha(0f).setDuration(MotionTokens.FAST).setInterpolator(MotionTokens.EXIT).start()
        panel.animate().alpha(0f).translationY(-12f * d).setDuration(MotionTokens.FAST)
            .setInterpolator(MotionTokens.EXIT).withEndAction { visibility = GONE }.start()
    }

    fun onBack(): Boolean {
        if (!showing) return false
        dismiss()
        return true
    }

    /** Dừng animation (xoay màn hình / huỷ). */
    fun cancelAnimations() {
        panel.animate().cancel(); scrim.animate().cancel()
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        super.onDetachedFromWindow()
    }

    companion object {
        private val marks = Regex("\\p{Mn}+")

        /** Chuẩn hoá để tìm không phân biệt hoa/thường và dấu tiếng Việt. */
        fun norm(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "").replace('đ', 'd')
    }
}
