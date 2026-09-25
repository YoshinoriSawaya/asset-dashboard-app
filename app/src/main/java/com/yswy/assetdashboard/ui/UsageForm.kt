package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.MetricPointEntity
import java.time.LocalDate

/**
 * 毎年リセットする枠に「使った分を足す」(E07-09)。
 *
 * 系列の値は今年の累計なので、今回の額を今年の最新の累計に足した値を、
 * 今日の点として保存する。人は今回の額だけを入れればよい。
 */
object UsageForm {

    sealed interface Result {
        data class Ok(val date: LocalDate, val newTotalYen: Long) : Result
        data class Invalid(val message: String) : Result
    }

    /** 今年の最新の累計。今年まだ無ければ0(去年の点は数えない)。 */
    fun usedThisYear(points: List<MetricPointEntity>, today: LocalDate): Long =
        points.filter { it.date.year == today.year }.maxByOrNull { it.date }?.valueYen ?: 0L

    fun parse(points: List<MetricPointEntity>, amount: String, today: LocalDate): Result {
        val yen = CorrectionForm.parseYen(amount)
            ?: return Result.Invalid("金額は数字で入れてください(例: 10,000)")
        if (yen <= 0) return Result.Invalid("0より大きい額を入れてください")
        return Result.Ok(today, usedThisYear(points, today) + yen)
    }
}
