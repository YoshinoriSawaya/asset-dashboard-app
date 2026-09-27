package com.yswy.assetdashboard.data

import java.time.LocalDate

/**
 * 基準価額の推移の期間(E05-11)。本人が選んだ: 3か月・1年・全期間を切り替え、平均取得単価の横線を付ける。
 */
enum class NavPeriod(val label: String, val months: Long?) {
    THREE_MONTHS("3か月", 3),
    ONE_YEAR("1年", 12),
    ALL("全期間", null),
}

object NavHistory {

    /**
     * 期間に入る点。期間の起点は最新の点の日付から数える(今日からではない。基準価額は前の営業日までしか無い)。     */
    fun window(navs: List<Nav>, period: NavPeriod): List<Nav> {
        val sorted = navs.sortedBy { it.date }
        val last = sorted.lastOrNull() ?: return emptyList()
        val months = period.months ?: return sorted
        val from: LocalDate = last.date.minusMonths(months)
        return sorted.filter { !it.date.isBefore(from) }
    }

    /** 期間の最初の点から最新の点までの増減の割合。点が足りない・最初が0ならnull。 */
    fun change(window: List<Nav>): Double? {
        val first = window.firstOrNull()?.yen ?: return null
        val last = window.lastOrNull()?.yen ?: return null
        if (window.size < 2 || first == 0L) return null
        return (last - first).toDouble() / first
    }
}
