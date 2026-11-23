package com.vstretch.app.motion

import android.view.animation.PathInterpolator

/**
 * Motion Design System 1.0 — token dùng chung cho MỌI animation.
 * Không hardcode thời lượng / easing ở nơi khác.
 */
object MotionTokens {
    // ---- Duration (ms) ----
    const val INSTANT = 100L      // phản hồi nút
    const val FAST = 140L         // tương tác nhỏ
    const val NORMAL = 180L       // fade, card entrance
    const val MEDIUM = 240L       // chuyển trang
    const val SMOOTH = 280L       // sidebar, panel
    const val EMPHASIS = 450L     // success
    const val AMBIENT = 8000L     // nền phát sáng (6000–10000)

    // ---- Duration riêng theo thành phần (theo spec) ----
    const val NAV_INDICATOR = 250L
    const val TOGGLE = 200L
    const val PRESET_SELECT = 300L   // 250–350
    const val EXPAND = 250L
    const val SHIMMER = 1400L
    const val SPINNER = 900L
    const val PULSE = 1200L

    // ---- Stagger ----
    const val STAGGER_STEP = 40L     // 30–45ms
    const val STAGGER_MAX_STEPS = 6  // giới hạn để không bắt người dùng chờ

    // ---- Khoảng dịch / tỉ lệ ----
    const val PAGE_SHIFT_DP = 16     // 12–20dp
    const val STAGGER_SHIFT_DP = 12
    const val PRESS_SCALE = 0.98f
    const val SNAP_RANGE = 2         // ±0.02x quanh preset thì hiện gợi ý hít

    // ---- Easing ----
    val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)
    val ENTER = PathInterpolator(0f, 0f, 0.2f, 1f)
    val EXIT = PathInterpolator(0.4f, 0f, 1f, 1f)
    val EMPHASIZED = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
    /** Đàn hồi RẤT nhẹ (vượt ~8%), dùng cho công tắc. */
    val ELASTIC = PathInterpolator(0.25f, 0.9f, 0.3f, 1.08f)
}
