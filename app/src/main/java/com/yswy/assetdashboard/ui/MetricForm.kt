package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item

/**
 * Metric項目の表示名・非表示(E07-14)・純資産に数えるか(E10-01)・まとめ先(E07-18)・
 * 想定利回り(E09-03)の入力を、settingsへの変更にする。
 *
 * ## 既定に戻ったら、settingsから消す
 * Metric項目はCSVの列から自動で生え、settingsには「人が変えたもの」だけを
 * 書く(E02-01)。名前を列名に戻して隠すのもやめたなら、書いておく理由が無い。
 * 消せば次の作り直しで自動の項目に戻り、items.jsonも膨らまない。
 */
object MetricForm {

    sealed interface Result {
        /** settingsに書く(置き換える)。 */
        data class Save(val metric: Item.Metric) : Result

        /** 既定と同じなので、settingsから消す。 */
        data class Reset(val id: String) : Result

        data class Invalid(val message: String) : Result
    }

    fun parse(
        current: Item.Metric,
        name: String,
        hidden: Boolean,
        inNetWorth: Boolean = current.inNetWorth,
        /** まとめ先の系列(E07-18)。無ければnull */
        groupKey: String? = current.groupKey,
        /** 想定利回り(年%)。空なら決めない(将来の評価額を出さない)。 */
        returnRate: String = rateText(current.expectedReturnBp),
        /** 積立額にする積立投資のカテゴリ(E09-05)。nullなら積立投資の全部 */
        investCategory: String? = current.investCategory,
    ): Result {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Invalid("名前を入れてください")
        if (groupKey == current.metricKey) return Result.Invalid("自分自身にはまとめられません")
        val bp = if (returnRate.isBlank()) {
            null
        } else {
            returnRate.trim().toBigDecimalOrNull()
                ?.takeIf { it.signum() >= 0 && it <= MAX_RETURN_PERCENT.toBigDecimal() }
                ?.let { (it * 100.toBigDecimal()).toInt() }
                ?: return Result.Invalid("想定利回りは0〜${MAX_RETURN_PERCENT}(%)で入れてください(例: 3)。出さないなら空に")
        }

        val edited = current.copy(name = trimmed, hidden = hidden, inNetWorth = inNetWorth, groupKey = groupKey, expectedReturnBp = bp,
            // 利回りをやめたら、積立額のカテゴリも使わないので持たない
            investCategory = investCategory?.takeIf { bp != null },
        )
        return if (isDefault(edited)) Result.Reset(current.id) else Result.Save(edited)
    }

    /** CSVの列から自動で生えたときと同じか。 */
    fun isDefault(metric: Item.Metric): Boolean =
        metric.name == metric.metricKey && !metric.hidden && metric.sortOrder == 0 && !metric.inNetWorth && metric.groupKey == null &&
            metric.expectedReturnBp == null && metric.investCategory == null

    /** 入力欄に出す想定利回り(年%)。3% → "3"、3.5% → "3.5"。 */
    fun rateText(bp: Int?): String = bp?.let { (it / 100.0).toString().removeSuffix(".0") }.orEmpty()

    /** 想定利回りの上限(年%)。桁を打ち間違えて、途方もない額を出さない。 */
    private const val MAX_RETURN_PERCENT = 20
}
