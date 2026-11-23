package com.vstretch.app.motion

import android.content.Context

/**
 * Tuỳ chỉnh GIAO DIỆN (không phải dữ liệu nghiệp vụ): cường độ chuyển động, rung, dock, bố cục Tổng quan.
 * Lưu bằng SharedPreferences riêng ("stretchx_ui") — không chạm vào lớp [com.vstretch.app.Prefs].
 */
class UiPrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("stretchx_ui", Context.MODE_PRIVATE)

    var motionLevel: Int
        get() = sp.getInt("motion", MotionPreferences.NORMAL)
        set(v) { sp.edit().putInt("motion", v).apply() }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) { sp.edit().putBoolean("haptics", v).apply() }

    var dock: Boolean
        get() = sp.getBoolean("dock", true)
        set(v) { sp.edit().putBoolean("dock", v).apply() }

    /** Thứ tự card Tổng quan (luôn đủ mọi id đã biết, id lạ bị bỏ). */
    fun dashOrder(): List<String> {
        val saved = (sp.getString("dash_order", "") ?: "").split(",").filter { it in DASH_IDS }.distinct()
        return saved + DASH_IDS.filter { it !in saved }
    }

    fun dashHidden(): Set<String> =
        (sp.getString("dash_hidden", "") ?: "").split(",").filter { it in DASH_IDS }.toSet()

    fun saveDash(order: List<String>, hidden: Set<String>) {
        sp.edit().putString("dash_order", order.joinToString(",")).putString("dash_hidden", hidden.joinToString(",")).apply()
    }

    companion object {
        val DASH_IDS = listOf("live", "status", "quick", "feat")
    }
}
