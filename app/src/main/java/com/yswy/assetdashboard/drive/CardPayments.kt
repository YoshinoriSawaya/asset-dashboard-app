package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.CardStatementAdapter
import com.yswy.assetdashboard.data.BankTransactionEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 銀行のカード引き落としを見つけて、支出を二重に数えないようにする(E01-14)。
 *
 * カードの利用明細(1回ずつ)と、銀行のカード引き落とし(まとめて1行)を両方取り込むと、
 * 同じ支出を2回数える。内訳のあるカードの明細を正とし、銀行の引き落としの行を
 * 生活費から除く。
 *
 * ## 摘要の言葉ではなく、請求の合計額で見つける
 * 引き落としの摘要はカード会社・銀行ごとに違い、決め打ちすると他のカードで外れる。
 * 確定した明細には請求の合計があり、引き落とされる額はこれと**1円まで一致する**。
 * 最後に使った日の後、[WINDOW_DAYS]日以内に同じ額の出金があれば、それが引き落とし。
 *
 * カードの明細を取り込んでいない月の引き落としは、突き合わせる相手が無いので
 * そのまま支出に数える(数えなくなるより、まとめて1行で数えるほうがよい)。
 */
object CardPayments {

    /** 締め日から引き落とし日まで(月末締め翌月26日払いなどで最長2か月弱)を覆う日数。 */
    const val WINDOW_DAYS = 75L

    data class Statement(val totalYen: Long, val lastUseDate: LocalDate)

    /**
     * @param transactions 銀行とカードの取引全部
     * @return 引き落としと見なした銀行の行の dedupKey
     */
    fun matchedKeys(transactions: Collection<BankTransactionEntity>, statements: List<Statement>): Set<String> {
        val candidates = transactions
            .filter { it.label != CardStatementAdapter.LABEL && it.withdrawal != null }
            .sortedBy { it.date }
        val matched = mutableSetOf<String>()
        for (statement in statements.sortedBy { it.lastUseDate }) {
            candidates.firstOrNull { row ->
                row.dedupKey !in matched &&
                    row.withdrawal == statement.totalYen &&
                    ChronoUnit.DAYS.between(statement.lastUseDate, row.date) in 1..WINDOW_DAYS
            }?.let { matched += it.dedupKey }
        }
        return matched
    }
}
