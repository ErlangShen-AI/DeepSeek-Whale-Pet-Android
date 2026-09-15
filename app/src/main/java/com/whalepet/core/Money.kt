package com.whalepet.core

import java.util.Locale

/** 金额与币种的展示格式。 */
object Money {

    const val PLACEHOLDER = "--"

    fun format(amount: Double?, currency: String): String {
        if (amount == null || !amount.isFinite()) return PLACEHOLDER
        return if (currency.equals("CNY", ignoreCase = true)) {
            "¥ " + plain(amount)
        } else {
            plain(amount) + " " + currency
        }
    }

    fun plain(amount: Double): String = String.format(Locale.US, "%.2f", amount)

}
