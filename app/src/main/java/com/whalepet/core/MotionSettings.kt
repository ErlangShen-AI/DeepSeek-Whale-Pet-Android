package com.whalepet.core

import android.content.Context
import android.provider.Settings

/**
 * 系统动效偏好。
 *
 * 用户在开发者选项或无障碍设置中关闭动画后，位移、形变与数字滚动直接落位，气泡文字淡入不再等待延迟。
 */
object MotionSettings {

    fun prefersReducedMotion(context: Context): Boolean {
        val resolver = context.contentResolver
        val animatorScale =
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transitionScale =
            Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        return animatorScale == 0f || transitionScale == 0f
    }
}
