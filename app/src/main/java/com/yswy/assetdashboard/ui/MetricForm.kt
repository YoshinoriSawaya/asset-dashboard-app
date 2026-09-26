package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item

/**
 * Metric項目の表示名・非表示(E07-14)・純資産に数えるか(E10-01)の入力を、settingsへの変更にする。
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

    fun parse(current: Item.Metric, name: String, hidden: Boolean, inNetWorth: Boolean = current.inNetWorth): Result {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Invalid("名前を入れてください")

        val edited = current.copy(name = trimmed, hidden = hidden, inNetWorth = inNetWorth)
        return if (isDefault(edited)) Result.Reset(current.id) else Result.Save(edited)
    }

    /** CSVの列から自動で生えたときと同じか。 */
    fun isDefault(metric: Item.Metric): Boolean =
        metric.name == metric.metricKey && !metric.hidden && metric.sortOrder == 0 && !metric.inNetWorth
}
