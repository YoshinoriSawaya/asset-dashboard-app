package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import java.time.LocalDate

/**
 * 銀行明細の残高から、口座ごとの残高の系列を作る(E02-07)。
 *
 * 資産推移CSVの「預金・現金」は全部の銀行の合計で、生活費の口座と自由に使える口座を分けて見られない。
 * 銀行明細には取引ごとの残高の列があるので、そこから口座ごとの推移を作る。
 *
 * ## 口座は明細の形式で分ける
 * 明細の中に口座や銀行の名前は無い。形式(アダプター)ごとに1つの系列にする。今は銀行ごとに形式が違うので、
 * これで口座が分かれる。系列名は `残高(<形式名>)` で、銀行名はコードに書かない(CLAUDE.mdの「対応付けはしない」)。
 * 人が見る名前は、系列の表示名(E07-14)で付ける。
 * 同じ形式の銀行が2つになると、1つの系列に混ざる(そのときに分け方を考える)。
 *
 * ## 日ごとの最後の残高
 * 1日に何件も取引があれば、その日の最後の取引の後の残高をその日の値にする。明細は古い順とは限らない
 * (新しい順のファイルもある)ので、ファイルごとに日付の向きを見て、古い順にそろえてから読む。
 * 同じ日が複数のファイルにあれば、後から取り込んだファイルの値を使う(Metricの後勝ちと同じ)。
 *
 * backupには書かない。backupの明細から毎回作るので、作り方を変えても取り込み直さずに済む。
 */
object AccountBalances {

    /** 系列名。形式ごとに1つ。 */
    fun keyFor(adapterId: String): String = "残高($adapterId)"

    /** @param backups 古い順(後から取り込んだものが後ろ) */
    fun points(backups: List<BackupReader.Backup>): List<MetricPoint> {
        val byKey = LinkedHashMap<Pair<String, LocalDate>, Long>()
        for (backup in backups) {
            val data = backup.data as? ParsedData.Transactions ?: continue
            if (backup.adapterId.isBlank()) continue
            val rows = data.rows.filter { it.balance != null }
            if (rows.isEmpty()) continue
            // 新しい順のファイルは古い順にする(同じ日の中の順番もそろう)
            val ordered = if (rows.first().date > rows.last().date) rows.asReversed() else rows
            val key = keyFor(backup.adapterId)
            ordered.forEach { row -> byKey[key to row.date] = row.balance!! }
        }
        return byKey.map { (k, yen) -> MetricPoint(k.first, k.second, yen) }
    }
}
