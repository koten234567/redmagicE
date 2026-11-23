package com.vstretch.app.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Rung nhẹ qua View.performHapticFeedback: tự tôn trọng thiết lập "phản hồi khi chạm" của hệ thống
 * và không làm gì nếu máy không có phần cứng rung. Chỉ gọi tại thời điểm rời rạc (không rung liên tục).
 */
object Haptics {
    @Volatile
    var enabled: Boolean = true

    fun tick(v: View) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(v: View) {
        if (!enabled) return
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        )
    }

    fun reject(v: View) {
        if (!enabled) return
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        )
    }
}
