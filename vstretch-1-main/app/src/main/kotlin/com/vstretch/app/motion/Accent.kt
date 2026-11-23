package com.vstretch.app.motion

/**
 * Dynamic Accent: sắc độ của Neon Pink đổi nhẹ theo trạng thái (vẫn trong họ hồng thương hiệu).
 * Chỉ dùng cho bề mặt phát sáng (ambient glow, glow theo chạm). Màu thương hiệu `Ui.PINK` không đổi.
 */
object Accent {
    const val NORMAL = 0xFFFF2E93.toInt()    // mặc định
    const val ACTIVE = 0xFFFF4DA6.toInt()    // đang kéo giãn: sáng hơn một chút
    const val OFFLINE = 0xFFC94B8E.toInt()   // Shizuku chưa sẵn sàng: dịu hơn

    var color: Int = NORMAL
        private set

    fun update(active: Boolean, online: Boolean): Int {
        color = when {
            !online -> OFFLINE
            active -> ACTIVE
            else -> NORMAL
        }
        return color
    }
}
