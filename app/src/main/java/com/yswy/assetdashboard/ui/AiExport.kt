package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.FundOutlook
import com.yswy.assetdashboard.data.ItemOverview
import com.yswy.assetdashboard.data.PeriodSummary
import com.yswy.assetdashboard.data.RampUp
import com.yswy.assetdashboard.data.RecoveryPlan
import java.time.LocalDate

/**
 * AIに貼り付けて相談するためのテキスト(E03-07)。
 *
 * ## 入れないもの
 * - **明細の摘要**: 振込相手の氏名などが入る(`振込 ﾀﾅｶ ﾀﾛｳ` のような形)。
 *   入出金は月ごとの合計だけにする
 * - 口座番号・銀行名: そもそもキャッシュに持っていない
 *
 * 金額・系列名・推移だけを整形する。どこかへ自動で送ることはせず、
 * コピーした人が貼る先を選ぶ。
 */
object AiExport {

    /** 各系列・入出金の月次を何か月ぶん入れるか。 */
    const val MONTHS = 6

    fun build(
        today: LocalDate,
        latestDataDate: LocalDate?,
        months: List<PeriodSummary>,
        goals: List<ItemOverview.Goal>,
    ): String = buildString {
        appendLine("# 資産の状況(${today}時点)")
        appendLine()
        appendLine("個人の資産管理アプリから書き出したデータです。金額は円。")
        latestDataDate?.let { appendLine("手元のデータは ${it} までのものです。") }
        appendLine()

        val recent = months.take(MONTHS)
        val metricItems = recent.firstOrNull()?.metrics?.map { it.first }.orEmpty()
        if (metricItems.isNotEmpty()) {
            appendLine("## 系列ごとの推移(直近${recent.size}か月、各月の最後の値と前月からの増減)")
            appendLine()
            for (item in metricItems) {
                appendLine("### ${item.name}")
                appendLine("| 月 | 値 | 増減 |")
                appendLine("|---|---:|---:|")
                for (month in recent) {
                    val change = month.metrics.firstOrNull { it.first.id == item.id }?.second ?: continue
                    appendLine("| ${month.label} | ${change.closingYen?.let(::yen) ?: "データなし"} | ${change.changeYen?.let(::signed) ?: "-"} |")
                }
                appendLine()
            }
        }

        val withCash = recent.filter { it.cashflow != null }
        if (withCash.isNotEmpty()) {
            appendLine("## 入出金(銀行明細の月ごとの合計)")
            appendLine()
            appendLine("| 月 | 収入 | 支出 | 収支 | 件数 | 明細の期間 |")
            appendLine("|---|---:|---:|---:|---:|---|")
            for (month in withCash) {
                val c = month.cashflow ?: continue
                val coverage = month.cashflowCoverage?.let { "${it.start}〜${it.endInclusive}" } ?: ""
                appendLine("| ${month.label} | ${yen(c.incomeYen)} | ${yen(c.spendingYen)} | ${signed(c.netYen)} | ${c.count} | $coverage |")
            }
            appendLine()
        }

        if (goals.isNotEmpty()) {
            appendLine("## 目標")
            appendLine()
            for (goal in goals) {
                val item = goal.item
                val target = goal.targetYen
                append("- ${item.name}: ${if (item.resetsYearly) "今年の枠" else "目標"} ${target?.let(::yen) ?: "不明"}")
                item.autoTarget?.let { append("(生活費の${it.averageMonths}か月平均 × ${it.coverMonths}か月から自動計算)") }
                goal.currentYen?.let { current ->
                    append(if (item.resetsYearly) " / 今年使った額 ${yen(current)}" else " / 現在 ${yen(current)}")
                    goal.progress?.let { append("(${(it * 100).toInt()}%)") }
                    target?.let { append(" / 残り ${yen((it - current).coerceAtLeast(0))}") }
                }
                // 同じ系列を分け合っていれば、「現在」はこの目標への割当額(E07-10)
                goal.share?.let {
                    append(" / ${it.metricKey}(${yen(it.seriesYen)})を${it.count}つの目標で上から順に分けた${it.position}番目。自由に使えるお金 ${yen(it.freeYen)}")
                }
                item.dueDate?.let { append(" / 期日 $it") }
                // 生活防衛資金の下限(E07-12)。ここまでは取り崩してよい
                item.autoTarget?.floorMonths?.let { months ->
                    append(" / 下限 ${goal.floorYen?.let(::yen) ?: "不明"}(生活費の${months}か月分。ここまでは取り崩してよい)")
                }
                // 期日に向けた積み増し(E07-11)
                item.rampUpMonths?.let { append(" / 期日の${it}か月前から積み増す") }
                (RampUp.of(item, target, goal.currentYen, today) as? RampUp.Active)?.let {
                    append("(期間中。期日まで月々${yen(it.monthlyYen)})")
                }
                FundOutlook.of(goal)?.let { outlook ->
                    outlook.monthsCovered?.let { append(" / 生活費の約${"%.1f".format(it)}か月分") }
                    outlook.monthsUntilBelowTarget?.let { append(" / このペースだと約${it}か月後に目標を割る") }
                }
                RecoveryPlan.of(target, goal.currentYen)?.takeIf { it.isShort && !item.resetsYearly && item.rampUpMonths == null }?.let { plan ->
                    append(" / 不足分を埋めるには ")
                    append(plan.options.joinToString("、") { "${it.months}か月なら月々${yen(it.monthlyYen)}" })
                }
                appendLine()
            }
            appendLine()
        }

        appendLine("## 読むときの注意")
        appendLine("- 系列は資産推移CSVの列そのまま。「合計」のような列は他の列の合計なので、系列同士を足すと二重に数えることになる")
        appendLine("- 支出には自分の口座間の振替やカードの引き落としも含まれる")
        appendLine("- 入出金は銀行から取得した期間の分しか無い(明細の期間を参照)")
    }

    private fun yen(v: Long) = Formatters.yen(v)
    private fun signed(v: Long) = Formatters.yenChange(v)
}
