package com.whalepet.ui

import android.graphics.Typeface
import android.os.Build

/** 字重到系统字体的映射，优先使用平台自带字体的可变字重，低版本回退到最接近的家族。 */
object Typefaces {

    private const val FAMILY = "sans-serif"

    fun weighted(weight: Int): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(Typeface.SANS_SERIF, weight, false)
        }
        return Typeface.create(legacyFamily(weight), Typeface.NORMAL)
    }

    private fun legacyFamily(weight: Int): String {
        return when {
            weight >= 800 -> "sans-serif-black"
            weight >= 600 -> "sans-serif-medium"
            weight <= 300 -> "sans-serif-light"
            else -> FAMILY
        }
    }
}

/**
 * 文字层级。
 *
 * 字号越大字距越紧，行高随字号反向变化，与平台字号缩放叠加后仍保持层级关系。
 */
enum class TextRole(
    val size: Float,
    val tracking: Float,
    val lineHeight: Float,
    val weight: Int
) {
    LARGE_TITLE(28f, -0.02f, 1.12f, 700),
    TITLE(20f, -0.015f, 1.2f, 700),
    HEADLINE(17f, -0.01f, 1.25f, 600),
    BODY(16f, 0f, 1.35f, 400),
    CALLOUT(15f, 0.002f, 1.3f, 400),
    SUBHEAD(13.5f, 0.004f, 1.25f, 500),
    CAPTION(12f, 0.008f, 1.2f, 400),
    VALUE(17f, -0.01f, 1.2f, 600)
}
