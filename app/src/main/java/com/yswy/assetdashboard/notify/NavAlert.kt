package com.yswy.assetdashboard.notify

import com.yswy.assetdashboard.data.FundHoldings
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.Nav

/**
 * 基準価額が平均取得単価より10%以上上がったら知らせる(E05-09)。
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

    fun evaluate(holdings: FundHoldings, navs: Map<String, Nav>, lastNotified: Map<String, *>): Result {
        val notices = mutableListOf<NotificationRules.Notice>()
        val forget = mutableListOf<String>()
        // 売り切ったファンドは知らせない(平均取得単価は売る前のもので、もう持っていない。E01-19)
        for (fund in holdings.held) {
            val fundKey = FundSource.keyOf(fund.section, fund.name)
            val g = gain(fund.latest.unitCost, navs[fundKey]) ?: continue
            val key = key(fundKey)
            if (g >= THRESHOLD) {
                if (key !in lastNotified) {
                    notices += NotificationRules.Notice(
                        key,
                        "${fund.name}が平均取得単価より10%以上上がりました",
                        "基準価額 ${navs.getValue(fundKey).date} 時点。押すと投資信託の損益を開きます。",
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
