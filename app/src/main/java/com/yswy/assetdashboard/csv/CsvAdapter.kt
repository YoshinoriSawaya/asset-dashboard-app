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

        /**
         * [key] の最新値。
         *
         * 同じ日付の点が複数あるときは**後の行を採る**。CSVは古い順に
         * 並んでいるのが普通なので、同日なら後ろのほうが新しい。
         * (maxByOrNullは同着だと最初の要素を返すので、それだと古い値が残る)
         */
        fun latest(key: String): Long? {
            val ofKey = points.filter { it.metricKey == key }
            val latestDate = ofKey.maxOfOrNull { it.date } ?: return null
            return ofKey.last { it.date == latestDate }.valueYen
        }
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

    /**
     * ありえない金額のしきい値(10兆円)。
     *
     * 桁が飛んだ値は、たいてい行がずれて別の列を読んでいるか、
     * 区切り文字を誤判定して数字が繋がっている。個人の資産で
     * これを超えることは無いので、通すより弾いたほうが安全。
     */
    private const val MAX_ABS_AMOUNT = 10_000_000_000_000L

    /** ありえない年。これを外れる日付は誤読とみなす。 */
    private val PLAUSIBLE_YEARS = 1900..2100

    /**
     * 年月日から日付を作る。ありえない年なら誤読とみなしてnull。
     * 日付を組み立てる場所が複数あるので、判定をここに1つだけ置く。
     */
    fun dateOf(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }
            .getOrNull()
            ?.takeIf { it.year in PLAUSIBLE_YEARS }

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
            return dateOf(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].toInt(),
            )
        }

        if (text.length == 8 && text.all { it.isDigit() }) {
            return dateOf(
                text.substring(0, 4).toInt(),
                text.substring(4, 6).toInt(),
                text.substring(6, 8).toInt(),
            )
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

        // 桁が多すぎるとLongも溢れる。toLongOrNullがnullを返すのでそこでも止まる。
        val magnitude = digits.toLongOrNull() ?: return null
        if (magnitude > MAX_ABS_AMOUNT) return null

        return if (negative) -magnitude else magnitude
    }
}
