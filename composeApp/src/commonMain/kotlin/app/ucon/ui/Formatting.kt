package app.ucon.ui

import kotlin.math.roundToLong

fun Double.toFixed(digits: Int): String {
    val factor = (1..digits).fold(1.0) { acc, _ -> acc * 10.0 }
    val rounded = (this * factor).roundToLong() / factor
    if (digits == 0) return rounded.toLong().toString()
    val whole = rounded.toLong()
    val frac = ((kotlin.math.abs(rounded) - kotlin.math.abs(whole)) * factor).roundToLong()
    val fracStr = frac.toString().padStart(digits, '0')
    val sign = if (this < 0 && whole == 0L) "-" else ""
    return "$sign$whole.$fracStr"
}
