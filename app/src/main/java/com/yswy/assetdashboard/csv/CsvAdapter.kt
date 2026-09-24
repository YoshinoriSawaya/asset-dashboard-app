package com.yswy.assetdashboard.csv

import java.time.LocalDate

/**
 * CSV1行を正規化したもの(取引明細)。
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

/**
 * ある日付における、ある項目の金額。
 *
 * CLAUDE.mdの汎用スキーマでいうMetricの1点。項目ごとに専用の型を作らず、
 * [metricKey] に項目名を持たせて横に増やせるようにする
 * (資産推移CSVに列が増えたら、自動的にMetricが1種類増える)。
 */
data class MetricPoint(
    /** 例: `投資信託` `預金・現金` `年金` `合計`。CSVの列名をそのまま使う。 */
    val metricKey: String,
    val date: LocalDate,
    val valueYen: Long,
)

/** 読めなかった行。落とさずに理由を持って回る。 */
data class SkippedRow(val lineNumber: Int, val reason: String, val raw: String)

/**
 * パース結果の中身。CSVの性格によって2種類ある。
 *
 * 明細(いつ・いくら動いたか)と推移(いつ・いくらだったか)は別物なので、
 * 取り違えないよう型で分ける。
 */
sealed interface ParsedData {

    /** 銀行の入出金明細。 */
    data class Transactions(val rows: List<BankTransaction>) : ParsedData {
        val latestBalance: Long? get() = rows.lastOrNull { it.balance != null }?.balance
        val dateRange: ClosedRange<LocalDate>? get() = rows.map { it.date }.toRange()
    }

    /** 資産推移のような、日付ごとの残高スナップショット。 */
    data class Metrics(val points: List<MetricPoint>) : ParsedData {
        val keys: List<String> get() = points.map { it.metricKey }.distinct()
        val dateRange: ClosedRange<LocalDate>? get() = points.map { it.date }.toRange()

        /** [key] の最新値。 */
        fun latest(key: String): Long? =
            points.filter { it.metricKey == key }.maxByOrNull { it.date }?.valueYen
    }
}

private fun List<LocalDate>.toRange(): ClosedRange<LocalDate>? {
    val min = minOrNull() ?: return null
    val max = maxOrNull() ?: return null
    return min..max
}

data class ParseResult(
    val adapterId: String,
    val data: ParsedData,
    val skipped: List<SkippedRow>,
)

/**
 * CSVのアダプター。
 *
 * 新しい形式が増えたらこれを実装して[CsvAdapters.all]に足すだけで済むようにする
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
        AssetTrendAdapter,
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
