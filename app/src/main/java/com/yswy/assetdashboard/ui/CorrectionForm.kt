package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.MetricOrigin
import com.yswy.assetdashboard.drive.Corrections
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 補正画面(E03-04)の入力を、保存できる形にする。画面から切り離した純粋関数。
 */
object CorrectionForm {

    sealed interface Result {
        data class Ok(val metricKey: String, val date: LocalDate, val valueYen: Long, val note: String?) : Result
        data class Invalid(val message: String) : Result
    }

    /**
     * 金額は `1,234,567` `¥1,234,567` `1234567円` `-500` を受ける。
     * CSVの金額と同じ読み方にしたいが、あちらは既知の列向けにわざと緩い
     * (`ABC123` でも123を返す)。手入力では打ち間違いを通したくないので、
     * 記号を落とした残りが数字だけのときに限る。
     */
    fun parse(metricKey: String, date: String, value: String, note: String): Result {
        val key = metricKey.trim()
        if (key.isEmpty()) return Result.Invalid("系列の名前を入れてください")

        val day = try {
            LocalDate.parse(date.trim())
        } catch (e: DateTimeParseException) {
            return Result.Invalid("日付は 2026-09-25 の形で入れてください")
        }

        val yen = parseYen(value) ?: return Result.Invalid("金額は数字で入れてください(例: 1,234,567)")

        return Result.Ok(key, day, yen, note.trim().takeIf { it.isNotEmpty() })
    }

    /**
     * 手入力の金額。カンマ・円・¥・マイナスを落とした残りが数字だけのときに限る。
     * 目標額の入力(E07-01)でも使う。
     */
    fun parseYen(value: String): Long? {
        val text = value.trim().removePrefix("¥").removeSuffix("円").replace(",", "")
        val negative = text.startsWith("-")
        val digits = text.removePrefix("-")
        if (digits.isEmpty() || !digits.all { it.isDigit() } || digits.length > 14) return null
        return digits.toLong().let { if (negative) -it else it }
    }

    /**
     * 補正の種類。CSV由来の点を直すならOVERRIDE、CSVに無い点を足すならMANUAL。
     *
     * @param currentOrigin その日の点の今の由来。点が無ければnull。
     *   すでにOVERRIDEされている点は、下にCSVの点があるのでOVERRIDEのまま。
     */
    fun kindFor(currentOrigin: MetricOrigin?): Corrections.Kind = when (currentOrigin) {
        MetricOrigin.CSV, MetricOrigin.OVERRIDE -> Corrections.Kind.OVERRIDE
        MetricOrigin.MANUAL, null -> Corrections.Kind.MANUAL
    }
}
