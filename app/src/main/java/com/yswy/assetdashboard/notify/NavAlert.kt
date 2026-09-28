package com.yswy.assetdashboard.notify

import com.yswy.assetdashboard.data.FundHoldings
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.Nav
import com.yswy.assetdashboard.data.NavBase

/**
 * 基準価額が平均取得単価より10%以上上がったら知らせる(E05-09)。
 * 最高値と比べるファンドは、選んだときに固定した最高値より10%以上上がったら(E05-15)。ファンドごとに知らせないこともできる。
 *
 * - 平均取得単価は、保有商品一覧(E01-15)のいちばん新しい取り込みの値([FundHoldings.Snapshot.unitCost])
 * - **超えた朝に1回**。10%を下回ったら記録を消し、また超えたら知らせる(本人が選んだ)
 * - 通知の文面はファンド名だけ(金額は出さない。E05の決まり)
 * - 単価の桁が合わない(1万口あたりと1口あたりの取り違えなど)ときは知らせない: 比が1/10〜10倍の外
 */
object NavAlert {

    const val THRESHOLD = 0.10

    data class Result(val notices: List<NotificationRules.Notice>, val forget: List<String>)

    /**
     * 通知の鍵。末尾に `:up` を付ける: 通知の番号は鍵の最後の `:` より前から作る(DailyCheck.post)ので、
     * 付けないと全ファンドが同じ番号になり、あとの通知が前の通知を上書きする(エミュレータで踏んだ)。
     */
    fun key(fundKey: String) = "navup:$fundKey:up"

    /** 平均取得単価に対する増減。比べられなければnull。 */
    fun gain(unitCost: Long?, nav: Nav?): Double? {
        if (unitCost == null || unitCost <= 0 || nav == null) return null
        val ratio = nav.yen.toDouble() / unitCost
        if (ratio > 10 || ratio < 0.1) return null
        return ratio - 1
    }

    /** 最高値と比べるときの通知の鍵(E05-15)。平均取得単価のときと分け、基準を変えたら前の記録に引きずられないようにする。 */
    fun peakKey(fundKey: String) = "navpeak:$fundKey:up"

    /** 固定した最高値(E05-15)に対する増減。同じ投資信託協会のCSVの値どうしなので、桁の確かめは要らない。 */
    fun gainFromPeak(peakBase: Nav?, nav: Nav?): Double? {
        if (peakBase == null || peakBase.yen <= 0 || nav == null) return null
        return nav.yen.toDouble() / peakBase.yen - 1
    }

    /**
     * ファンドごとに選んだ基準(E05-14・E05-15)で、10%以上上がったら知らせる。下がったときは知らせない(本人の判断)。
     * 取り先の無いファンドは平均取得単価で比べる。知らせないにしたファンドは、記録も消す(また知らせるにしたとき、超えていれば出る)。
     */
    fun evaluate(holdings: FundHoldings, navs: Map<String, Nav>, lastNotified: Map<String, *>, sources: List<FundSource> = emptyList()): Result {
        val notices = mutableListOf<NotificationRules.Notice>()
        val forget = mutableListOf<String>()
        val byKey = sources.associateBy { it.fundKey }
        // 売り切ったファンドは知らせない(平均取得単価は売る前のもので、もう持っていない。E01-19)
        for (fund in holdings.held) {
            val fundKey = FundSource.keyOf(fund.section, fund.name)
            val source = byKey[fundKey]
            val peak = source?.base == NavBase.PEAK
            val key = if (peak) peakKey(fundKey) else key(fundKey)
            // 使っていないほうの鍵の記録は消す(基準を変えて戻したとき、前の記録で止まらないように)
            val other = if (peak) key(fundKey) else peakKey(fundKey)
            if (other in lastNotified) forget += other
            if (source?.notify == false) {
                if (key in lastNotified) forget += key
                continue
            }
            val nav = navs[fundKey]
            val g = (if (peak) gainFromPeak(source?.peakBase, nav) else gain(fund.latest.unitCost, nav)) ?: continue
            if (g >= THRESHOLD) {
                if (key !in lastNotified) {
                    notices += NotificationRules.Notice(
                        key,
                        if (peak) "${fund.name}が決めた最高値より10%以上上がりました" else "${fund.name}が平均取得単価より10%以上上がりました",
                        "基準価額 ${nav!!.date} 時点。押すと投資信託の損益を開きます。",
                        NotificationRules.SCREEN_FUNDS,
                    )
                }
            } else if (key in lastNotified) {
                // 下回ったので、また超えたら知らせられるように戻す
                forget += key
            }
        }
        return Result(notices, forget)
    }
}
