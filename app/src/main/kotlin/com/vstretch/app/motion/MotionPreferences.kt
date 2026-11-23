package com.vstretch.app.motion

import android.content.Context
import android.provider.Settings

/**
 * Cường độ chuyển động = Reduced / Normal / Enhanced (người dùng chọn) + Reduce Motion của hệ thống.
 * [reduce] = true khi hệ thống yêu cầu giảm chuyển động HOẶC người dùng chọn Reduced:
 * tắt glow, stagger, number rolling, dịch chuyển trang, glow theo chạm...; vẫn giữ phản hồi thành công/lỗi.
 * [enhanced] chỉ true khi chọn Enhanced và hệ thống KHÔNG yêu cầu giảm.
 */
object MotionPreferences {
    const val REDUCED = 0
    const val NORMAL = 1
    const val ENHANCED = 2

    @Volatile
    var systemReduce: Boolean = false
        private set

    @Volatile
    var level: Int = NORMAL
        private set

    val reduce: Boolean get() = systemReduce || level == REDUCED
    val enhanced: Boolean get() = !systemReduce && level == ENHANCED

    fun refresh(c: Context) {
        systemReduce = try {
            Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Throwable) {
            false
        }
        val p = UiPrefs(c)
        level = p.motionLevel.coerceIn(REDUCED, ENHANCED)
        Haptics.enabled = p.haptics
    }

    fun setLevel(c: Context, l: Int) {
        UiPrefs(c).motionLevel = l
        refresh(c)
    }
}
