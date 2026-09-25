package com.yswy.assetdashboard.ui

import java.text.NumberFormat
import java.util.Locale

/** 画面に出す金額・割合の書式。どの画面でも同じ見た目にするため1か所に置く。 */
object Formatters {

    private val grouping = NumberFormat.getIntegerInstance(Locale.JAPAN)

    /** `1,234,567円` */
    fun yen(value: Long): String = "${grouping.format(value)}円"

    /** 増減。`+12,345円` / `-500円` / `±0円` */
    fun yenChange(value: Long): String = when {
        value > 0 -> "+${yen(value)}"
        value < 0 -> "-${yen(-value)}"
        else -> "±0円"
    }

    /** `42%`。達成超えもそのまま出す(`120%`)。 */
    fun percent(ratio: Double): String = "${(ratio * 100).toInt()}%"

    /** `あと3日` / `今日` / `5日過ぎ` */
    fun daysLeft(days: Long): String = when {
        days > 0 -> "あと${days}日"
        days == 0L -> "今日"
        else -> "${-days}日過ぎ"
    }
}
