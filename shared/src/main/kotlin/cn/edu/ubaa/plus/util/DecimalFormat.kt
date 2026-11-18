package cn.edu.ubaa.plus.util

import java.math.RoundingMode

/** Format upstream decimal text without carrying binary floating point artifacts into the UI. */
fun formatMoney(raw: String?): String? =
    raw?.trim()
        ?.toBigDecimalOrNull()
        ?.takeIf { it.precision() <= 100 && kotlin.math.abs(it.scale().toLong()) <= 100 }
        ?.setScale(2, RoundingMode.HALF_UP)
        ?.toPlainString()

fun formatMoney(value: Double): String = formatMoney(value.toString()) ?: "—"
