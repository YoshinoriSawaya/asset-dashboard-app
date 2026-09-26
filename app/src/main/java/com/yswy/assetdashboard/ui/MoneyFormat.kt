package com.yswy.assetdashboard.ui

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs
import kotlin.math.roundToLong

/** 金額の見せ方(E06-04)。人前でも開けるように、実額を隠せる。 */
enum class PrivacyMode(val label: String) {
    REAL("実額"),

    /** 絶対額は隠し、割合(進捗率・増減率)だけを出す。 */
    PERCENT("%"),

    /** 数字を全部隠す。グラフの形だけが見える。 */
    MASK("マスク"),
}

/**
 * 画面に出す金額の書式を、見せ方([PrivacyMode])に合わせて変える(E06-04)。
 *
 * 画面は `Formatters.yen` を直接呼ばず、これを通す。見せ方を足すときも
 * 画面側を変えずに済む。AI用の書き出し(E03-07)と入力欄は、本人が実額を
 * 必要とする場面なので、これを通さない。
 */
class MoneyFormat(val mode: PrivacyMode) {

    /** 金額。 */
    fun amount(yen: Long): String = when (mode) {
        PrivacyMode.REAL -> Formatters.yen(yen)
        PrivacyMode.PERCENT -> HIDDEN_SHORT
        PrivacyMode.MASK -> HIDDEN
    }

    /**
     * 増減。%のときは[base](増減の前の値)に対する増減率にする。
     * 前の値が分からない・0のときは率を出せないので隠す。
     */
    fun change(yen: Long, base: Long?): String = when (mode) {
        PrivacyMode.REAL -> Formatters.yenChange(yen)
        PrivacyMode.PERCENT -> base?.takeIf { it != 0L }?.let { signedPercent(yen.toDouble() / abs(it)) } ?: HIDDEN_SHORT
        PrivacyMode.MASK -> HIDDEN
    }

    /** 進捗などの割合。%のときもそのまま出す。 */
    fun percent(ratio: Double): String = when (mode) {
        PrivacyMode.MASK -> HIDDEN
        else -> Formatters.percent(ratio)
    }

    /** 内訳の割合(E10-03)。%のときもそのまま出す。 */
    fun share(ratio: Double): String = when (mode) {
        PrivacyMode.MASK -> HIDDEN
        else -> Formatters.sharePercent(ratio)
    }

    /**
     * 推移の折れ線の目盛り。%のときは最初の点に対する増減率にする
     * (「NISA評価額のような絶対額の推移グラフは増減率に変換」)。マスクなら出さない。
     */
    fun lineAxis(value: Long, first: Long): String? = when (mode) {
        PrivacyMode.REAL -> Formatters.yenCompact(value)
        PrivacyMode.PERCENT -> first.takeIf { it != 0L }?.let { signedPercent((value - it).toDouble() / abs(it)) }
        PrivacyMode.MASK -> null
    }

    /**
     * 積み上げグラフ(E07-13)の目盛り。0から積むので、%のときは最初の点からの増減率ではなく
     * 最新の合計に対する割合にする(増減率だと0円が「-100%」になる)。マスクなら出さない。
     */
    fun shareAxis(value: Long, total: Long): String? = when (mode) {
        PrivacyMode.REAL -> Formatters.yenCompact(value)
        PrivacyMode.PERCENT -> total.takeIf { it > 0 }?.let { Formatters.percent(value.toDouble() / it) }
        PrivacyMode.MASK -> null
    }

    /** 増減の棒の目盛り。棒ごとに基準が違い率にできないので、%とマスクでは出さない。 */
    fun barAxis(value: Long): String? = if (mode == PrivacyMode.REAL) Formatters.yenCompact(value) else null

    val showsAmounts: Boolean get() = mode == PrivacyMode.REAL

    companion object {
        const val HIDDEN = "※※※"
        const val HIDDEN_SHORT = "※"

        /** `+1.2%` / `-0.5%` / `±0%` */
        fun signedPercent(ratio: Double): String {
            val tenths = (ratio * 1000).roundToLong() / 10.0
            return when {
                tenths > 0 -> "+$tenths%"
                tenths < 0 -> "$tenths%"
                else -> "±0%"
            }
        }
    }
}

/** 画面全体で使う書式。トップで切り替えると全画面に効く。 */
val LocalMoney = staticCompositionLocalOf { MoneyFormat(PrivacyMode.REAL) }

/** 見せ方を端末に覚えておく(アプリを閉じても保つ)。Driveには置かない。 */
class PrivacyPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("privacy", Context.MODE_PRIVATE)

    var mode: PrivacyMode
        get() = runCatching { PrivacyMode.valueOf(prefs.getString(KEY, null) ?: "") }.getOrDefault(PrivacyMode.REAL)
        set(value) {
            prefs.edit().putString(KEY, value.name).apply()
        }

    private companion object {
        const val KEY = "mode"
    }
}
