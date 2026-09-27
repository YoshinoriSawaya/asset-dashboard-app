package com.yswy.assetdashboard.data

import kotlin.math.roundToLong

/**
 * 今の基準価額で見直した評価額・含み益と、史上最高値との比べ(E05-12)。
 *
 * ## 取り込み時の口数のまま見積もる
 * 口数は持っていない(E01-19の [FundHoldings.Snapshot.units] と同じ)ので、評価額 × 今の基準価額 ÷ 取り込み時の現在値 で出す。
 * 取り込みのあとに積み立てた・売った分は入らない。本当の値は保有商品一覧を取り込み直せば揃う。
 *
 * ## 最高値は基準価額の設定来の最高値
 * 投資信託協会のCSV(E05-11と同じもの)から出す。分配金は足していない(分配を出すファンドでは最高値が高めに残る)。
 */
object FundNow {

    /** 見直した値。[sinceImport] は取り込み時の現在値からの増減。 */
    data class Estimate(val valueYen: Long, val costYen: Long?, val sinceImport: Double) {
        val gainYen: Long? get() = costYen?.let { valueYen - it }
    }

    /**
     * 今の基準価額で見直す。見直せなければnull:
     * 現在値が無い、基準価額が取り込みより古い(取り込みの値のほうが新しい)、単価の桁が合わない(比が1/10〜10倍の外。E05-09と同じ)。
     * 取り込みの日より前の基準価額でも、取り込み時の現在値と同じなら見直す(±0%)。取り込みの現在値は前の営業日の基準価額なので、
     * 取り込んだ翌日などはこうなる(エミュレータで踏んだ: 取れたのに何も出なかった)。
     */
    fun estimate(s: FundHoldings.Snapshot, nav: Nav?): Estimate? {
        val price = s.price?.takeIf { it > 0 } ?: return null
        if (nav == null || (nav.date.isBefore(s.date) && nav.yen != price)) return null
        val ratio = nav.yen.toDouble() / price
        if (ratio > 10 || ratio < 0.1) return null
        return Estimate((s.valueYen * ratio).roundToLong(), s.costYen, ratio - 1)
    }

    /** 設定来の最高値。同じ値が何度もあれば新しい日。 */
    fun peak(history: List<Nav>): Nav? = history.maxWithOrNull(compareBy<Nav> { it.yen }.thenBy { it.date })

    /** ファンドごとの最高値(E05-13)。点の無いファンドは入れない。 */
    fun peaks(histories: Map<String, List<Nav>>): Map<String, Nav> =
        histories.mapNotNull { (k, v) -> peak(v)?.let { k to it } }.toMap()

    /** 最高値に対する今の基準価額の増減(0以下)。比べられなければnull。 */
    fun fromPeak(nav: Nav?, peak: Nav?): Double? {
        if (nav == null || peak == null || peak.yen <= 0) return null
        return nav.yen.toDouble() / peak.yen - 1
    }

    /** 最高値のときの評価額の見積もり(今の見積もりの口数 × 最高値)。 */
    fun valueAtPeak(estimate: Estimate, nav: Nav, peak: Nav): Long? =
        if (nav.yen <= 0) null else (estimate.valueYen * peak.yen.toDouble() / nav.yen).roundToLong()

    /**
     * 持っているファンドの合計。見直せないファンドは取り込み時の値のまま数える。
     * 含み益は取得額の分かるファンドだけで数える。[refreshed] は今の基準価額で見直せた本数。
     */
    data class Total(val valueYen: Long, val costYen: Long, val gainYen: Long, val refreshed: Int, val count: Int)

    fun total(funds: List<FundHoldings.Fund>, navs: Map<String, Nav>): Total {
        var value = 0L
        var cost = 0L
        var gain = 0L
        var refreshed = 0
        funds.forEach { f ->
            val s = f.latest
            val e = estimate(s, navs[FundSource.keyOf(f.section, f.name)])
            if (e != null) refreshed++
            val v = e?.valueYen ?: s.valueYen
            value += v
            s.costYen?.let { cost += it; gain += v - it }
        }
        return Total(value, cost, gain, refreshed, funds.size)
    }

    /** 最高値と比べられたファンドの合計。今の評価額と、最高値のときの評価額。 */
    data class PeakTotal(val nowYen: Long, val atPeakYen: Long, val count: Int) {
        val diffYen: Long get() = nowYen - atPeakYen
        val ratio: Double? get() = if (atPeakYen > 0) nowYen.toDouble() / atPeakYen - 1 else null
    }

    fun peakTotal(funds: List<FundHoldings.Fund>, navs: Map<String, Nav>, peaks: Map<String, Nav>): PeakTotal {
        var now = 0L
        var atPeak = 0L
        var count = 0
        funds.forEach { f ->
            val key = FundSource.keyOf(f.section, f.name)
            val nav = navs[key] ?: return@forEach
            val peak = peaks[key] ?: return@forEach
            val e = estimate(f.latest, nav) ?: return@forEach
            val p = valueAtPeak(e, nav, peak) ?: return@forEach
            now += e.valueYen
            atPeak += p
            count++
        }
        return PeakTotal(now, atPeak, count)
    }
}
