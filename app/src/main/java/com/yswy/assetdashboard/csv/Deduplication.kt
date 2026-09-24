package com.yswy.assetdashboard.csv

/**
 * 同じ取引・同じ測定値を二重に数えないための判定。
 *
 * ## 起きうる重複は2種類
 * 1. **同じCSVをもう一度取り込んだ** … 全行が丸ごと重複する
 * 2. **期間が重なるCSVを取り込んだ** … 重なった期間の行だけ重複する
 *
 * どちらも「同じ内容の行が2つある」ことに変わりはないので、
 * 行の中身からキーを作って判定する。
 *
 * ## なぜファイル単位ではなく行単位で見るか
 * ファイル単位(md5が同じなら丸ごと捨てる)だと2番のケースを拾えない。
 * 先月ぶんと今月ぶんのCSVで期間が数日重なる、というのは普通に起きる。
 *
 * ## 同じ日に同じ金額の取引が本当に2件あったら
 * 区別できない。「同じ日に同じ相手へ同じ額」を2回払うことは実際ある
 * (交通費など)。**重複とみなして1件に潰してしまう**のが現状の割り切り。
 *
 * 取りこぼす(1件消える)ほうを選んだのは、二重計上のほうが資産額を
 * 狂わせるため。1件消えても残高列で気づける。
 */
object Deduplication {

    /**
     * 取引を一意に表すキー。
     *
     * 日付・出金・入金・摘要の4つ。残高は含めない——同じ取引でも
     * 取り込むCSVによって残高の並びが違うことがあるため。
     */
    fun keyOf(transaction: BankTransaction): String = listOf(
        transaction.date.toString(),
        transaction.withdrawal?.toString().orEmpty(),
        transaction.deposit?.toString().orEmpty(),
        transaction.description.normalizeForKey(),
    ).joinToString("|")

    /**
     * Metricを一意に表すキー。
     *
     * 同じ日・同じ項目の値は1つしかありえない(時点の残高なので)。
     * 値が違っても後から取り込んだほうが正しいとみなす。
     */
    fun keyOf(point: MetricPoint): String = "${point.metricKey}|${point.date}"

    /**
     * 重複を取り除く。[existingKeys] に既にあるものも落とす。
     *
     * @param existingKeys 既に取り込み済みの行のキー
     * @return 残った行と、落とした件数
     */
    fun dedupeTransactions(
        transactions: List<BankTransaction>,
        existingKeys: Set<String> = emptySet(),
    ): Result<BankTransaction> {
        val seen = existingKeys.toMutableSet()
        val kept = mutableListOf<BankTransaction>()
        var dropped = 0

        for (transaction in transactions) {
            if (seen.add(keyOf(transaction))) kept += transaction else dropped++
        }
        return Result(kept, dropped)
    }

    /**
     * Metricの重複を取り除く。
     *
     * 取引と違い、**後から来たほうを採る**。同じ日・同じ項目の値は
     * 時点の残高なので、新しく取り込んだファイルのほうが正しい。
     */
    fun dedupeMetrics(
        points: List<MetricPoint>,
        existingKeys: Set<String> = emptySet(),
    ): Result<MetricPoint> {
        val byKey = LinkedHashMap<String, MetricPoint>()
        var dropped = 0

        for (point in points) {
            val key = keyOf(point)
            if (byKey.put(key, point) != null || key in existingKeys) dropped++
        }
        return Result(byKey.values.toList(), dropped)
    }

    data class Result<T>(val kept: List<T>, val dropped: Int) {
        val hasDuplicates: Boolean get() = dropped > 0
    }
}

/**
 * 摘要の表記ゆれを吸収する。
 *
 * 同じ取引でも、CSVによって全角/半角スペースの入り方が違うことがある。
 * スペースを落として比較すれば、それで別物扱いにならずに済む。
 */
private fun String.normalizeForKey(): String =
    trim().replace("　", "").replace(" ", "").replace("\t", "")
