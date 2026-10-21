package com.virtualrun.app.model

import java.util.Locale

const val FASTEST_RUN_PACE_SECONDS_PER_KM = 423.0
const val SLOWEST_RUN_PACE_SECONDS_PER_KM = 446.0
// Reference value for estimated duration; the live profile varies within the bounds above.
const val FIXED_RUN_PACE_MIN_PER_KM = (423f + 446f) / 120f

data class RunPace(
    val fastestSecondsPerKm: Int = 423,
    val slowestSecondsPerKm: Int = 446
) {
    init {
        require(fastestSecondsPerKm > 0 && slowestSecondsPerKm > 0) {
            "配速必须大于0"
        }
        require(fastestSecondsPerKm <= slowestSecondsPerKm) { "最快配速不能大于最慢配速" }
    }
    val isFixed get() = fastestSecondsPerKm == slowestSecondsPerKm
    val referenceMinutesPerKm get() = ((fastestSecondsPerKm.toDouble() + slowestSecondsPerKm) / 120.0).toFloat()
    val displayText get() = if (isFixed) "固定配速 ${format(fastestSecondsPerKm)} /公里"
        else "配速范围 ${format(fastestSecondsPerKm)}–${format(slowestSecondsPerKm)} /公里"

    companion object {
        fun format(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
        fun parse(text: String): Int? {
            val parts = text.trim().replace('：', ':').split(':')
            if (parts.size !in 1..2 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
            val minutes = parts[0].toLongOrNull()?.takeIf { it in 0..(Int.MAX_VALUE.toLong() / 60) } ?: return null
            val seconds = if (parts.size == 1) 0 else parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            return (minutes * 60 + seconds).takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
        }
    }
}
