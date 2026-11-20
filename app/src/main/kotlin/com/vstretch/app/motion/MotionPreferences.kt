package com.vstretch.app.motion

import android.content.Context
import android.provider.Settings

/**
 * Reduce Motion: theo thiết lập hệ thống Android "Xóa hiệu ứng động" (animator_duration_scale = 0).
 * Gọi [refresh] ở onCreate / onResume / onConfigurationChanged.
 */
object MotionPreferences {
    @Volatile
    var reduce: Boolean = false
        private set

    fun refresh(c: Context) {
        reduce = try {
            Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Throwable) {
            false
        }
    }
}
