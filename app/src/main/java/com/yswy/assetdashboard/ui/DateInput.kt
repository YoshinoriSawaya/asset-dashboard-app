package com.yswy.assetdashboard.ui

import java.time.LocalDate

/**
 * 画面で入れる日付(E07-17)。スマホで打ちやすい `20270301` を基本にし、
 * `2027-03-01` と `2027/3/1` も受け付ける。前から入っている値(`2027-03-01` の形)を
 * そのまま保存し直しても通るようにするため。
 */
object DateInput {

    /** 入力欄の例。 */
    const val EXAMPLE = "20270301"

    private val COMPACT = Regex("""(\d{4})(\d{2})(\d{2})""")
    private val SEPARATED = Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})""")

    /** 読めなければnull(存在しない日付も)。 */
    fun parse(text: String): LocalDate? {
        val t = text.trim()
        val m = COMPACT.matchEntire(t) ?: SEPARATED.matchEntire(t) ?: return null
        val (y, mo, d) = m.destructured
        return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
    }

    /** 入力欄に入れておく形。 */
    fun format(date: LocalDate): String = "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)
}
