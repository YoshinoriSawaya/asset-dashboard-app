package com.yswy.assetdashboard.csv

import java.time.LocalDate

/**
 * CSV1行を正規化したもの。
 *
 * 金額は円単位の整数(Long)で持つ。小数は出てこないし、
 * Doubleにすると合計がずれるので使わない。
 */
data class BankTransaction(
    val date: LocalDate,
    val description: String,
    /** 出金額。無い行はnull。 */
    val withdrawal: Long?,
    /** 入金額。無い行はnull。 */
    val deposit: Long?,
    /** その行時点の残高。列が無いフォーマットではnull。 */
    val balance: Long?,
    val memo: String? = null,
    val label: String? = null,
)

/** 読めなかった行。落とさずに理由を持って回る。 */
data class SkippedRow(val lineNumber: Int, val reason: String, val raw: String)

data class ParseResult(
    val adapterId: String,
    val transactions: List<BankTransaction>,
    val skipped: List<SkippedRow>,
) {
    val latestBalance: Long? get() = transactions.lastOrNull { it.balance != null }?.balance
    val dateRange: Pair<LocalDate, LocalDate>? get() {
        val dates = transactions.map { it.date }
        val min = dates.minOrNull() ?: return null
        val max = dates.maxOrNull() ?: return null
        return min to max
    }
}

/**
 * 銀行ごとのCSVアダプター。
 *
 * 新しい銀行が増えたらこれを実装して[CsvAdapters.all]に足すだけで済むようにする
 * (CLAUDE.mdの「銀行が増える方向の拡張はしやすく」)。
 */
interface CsvAdapter {
    /** ログや画面に出す識別子。 */
    val id: String

    /** ヘッダー行を見て、自分が扱える形式か答える。 */
    fun matches(header: List<String>): Boolean

    /** ヘッダー行を除いたデータ行をパースする。 */
    fun parse(header: List<String>, rows: List<List<String>>): ParseResult
}

object CsvAdapters {

    val all: List<CsvAdapter> = listOf(
        WithdrawalDepositAdapter,
        AnserAdapter,
    )

    /** ヘッダーに一致するアダプターを返す。無ければnull(E01-05のフォールバックに回す)。 */
    fun findFor(header: List<String>): CsvAdapter? = all.firstOrNull { it.matches(header) }
}

/** 金額・日付のパースはフォーマットを問わず共通なのでここにまとめる。 */
internal object FieldParsers {

    private val DATE_PATTERNS = listOf(
        Regex("""^(\d{4})[/\-年](\d{1,2})[/\-月](\d{1,2})日?$"""),
    )

    /**
     * `2026/9/24` `2026-09-24` `2026年9月24日` `20260924` を受ける。
     * 読めなければnull。
     */
    fun parseDate(value: String): LocalDate? {
        val text = value.trim()
        if (text.isEmpty()) return null

        for (pattern in DATE_PATTERNS) {
            val m = pattern.find(text) ?: continue
            return runCatching {
                LocalDate.of(
                    m.groupValues[1].toInt(),
                    m.groupValues[2].toInt(),
                    m.groupValues[3].toInt(),
                )
            }.getOrNull()
        }

        if (text.length == 8 && text.all { it.isDigit() }) {
            return runCatching {
                LocalDate.of(
                    text.substring(0, 4).toInt(),
                    text.substring(4, 6).toInt(),
                    text.substring(6, 8).toInt(),
                )
            }.getOrNull()
        }
        return null
    }

    /**
     * `1,234` `¥1,234` `-500` `1234円` を受ける。
     * 空欄・ハイフンのみ・読めない場合はnull(0ではない)。
     *
     * 「出金なし」と「出金0円」を区別したいのでnullと0を分ける。
     */
    fun parseAmount(value: String): Long? {
        val text = value.trim()
        if (text.isEmpty() || text == "-" || text == "―" || text == "－") return null

        val negative = text.startsWith("-") || text.startsWith("△") || text.startsWith("▲")
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty()) return null

        val magnitude = digits.toLongOrNull() ?: return null
        return if (negative) -magnitude else magnitude
    }
}
