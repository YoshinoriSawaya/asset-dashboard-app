package com.yswy.assetdashboard.csv

/**
 * クレジットカードの利用明細(E01-14)。
 *
 * カード会社のCSVには列名の行が無く、他のアダプターのように「列の顔ぶれ」で
 * 判定できない。代わりに**1行目の形**で見分ける。今あるのは次の2つ
 * (どちらも実物のカードのCSVで確かめた。値は作り物)。
 *
 * ## 確定した明細(氏名の行 + 7列)
 * ```
 * 見本　花子　様,1234-****-****-****,見本カード
 * 2026/09/01,店名,1200,１,１,1200,
 * ...
 * ,,,,,45000,            ← 今回の請求の合計
 * ```
 * 列は 利用日, 利用店名, 利用金額, 支払区分, 今回回数, 支払金額, 備考。
 * 最後の行(日付が無く、支払金額だけ)が請求の合計で、銀行から引き落とされる額と一致する。
 *
 * ## まだ確定していない明細(見出し無しの13列)
 * ```
 * 2026/10/01,店名,ご本人,１回払い,,26年12月,1200,1200,,,,,
 * ```
 * 1行目からもう明細。列は 利用日, 利用店名, 利用者, 支払区分, (空), 支払月, 利用金額, 支払金額, …
 * と見ている。列名が無いので、利用者・支払月の列の意味は形からの推測。
 * 請求の合計が無いので、銀行の引き落としとは突き合わせない。
 *
 * ## 取り込む形
 * 1回の利用を1行の取引にする。額は**利用金額**(分割でも使った日に使った額として数える)。
 * 返品などマイナスの額は入金として持つ。ラベルを [LABEL] にして、銀行の明細と区別する。
 */
object CardStatementAdapter : CsvAdapter {

    override val id = "カード明細(氏名の行 + 7列)"

    /** カードの明細の行に付けるラベル。銀行の取引名と重ならない言葉にする。 */
    const val LABEL = "カード利用明細"

    /**
     * 伏せ字のカード番号か。`1234-****-****-****` `************5678` など、伏せ方は
     * カード会社や明細の種類で違う(桁の位置を決め打ちしたら実物で外れた)ので、
     * 「伏せ字が4つ以上続く」だけで見る。1つ目のセルが「様」で終わることと合わせて判定するので、
     * これで十分に絞れる。
     */
    private fun isMaskedCardNumber(cell: String): Boolean =
        "****" in java.text.Normalizer.normalize(cell, java.text.Normalizer.Form.NFKC)

    override fun matches(header: List<String>): Boolean =
        header.size in 2..5 &&
            header.first().trim().endsWith("様") &&
            header.any(::isMaskedCardNumber)

    override fun parse(header: List<String>, rows: List<List<String>>): ParseResult {
        val transactions = mutableListOf<BankTransaction>()
        val skipped = mutableListOf<SkippedRow>()
        var total: Long? = null

        rows.forEachIndexed { i, row ->
            val lineNumber = i + 2
            fun cell(at: Int) = row.getOrNull(at).orEmpty().trim()

            val date = FieldParsers.parseDate(cell(0))
            if (date == null) {
                when {
                    // 請求の合計。日付と店名が無く、支払金額の列だけに額がある
                    cell(1).isEmpty() && cell(2).isEmpty() && FieldParsers.parseAmount(cell(5)) != null ->
                        total = FieldParsers.parseAmount(cell(5))
                    // 家族カードなどで途中にまた氏名の行が入る。明細ではない
                    matches(row) || row.all { it.isBlank() } -> Unit
                    else -> skipped += SkippedRow(lineNumber, "日付を読めない", row.joinToString(","))
                }
                return@forEachIndexed
            }

            val amount = FieldParsers.parseAmount(cell(2))
            if (amount == null) {
                skipped += SkippedRow(lineNumber, "利用金額を読めない", row.joinToString(","))
                return@forEachIndexed
            }
            transactions += cardTransaction(date, cell(1), amount, memo = cell(6))
        }

        return ParseResult(id, ParsedData.Transactions(transactions, statementTotal = total), skipped)
    }

    internal fun cardTransaction(date: java.time.LocalDate, store: String, amount: Long, memo: String = "") = BankTransaction(
        date = date,
        description = store,
        withdrawal = amount.takeIf { it >= 0 },
        deposit = (-amount).takeIf { it > 0 },
        balance = null,
        memo = memo.takeIf { it.isNotEmpty() },
        label = LABEL,
    )
}

/** まだ確定していないカードの明細(見出し無しの13列)。形は [CardStatementAdapter] に書いた。 */
object CardPendingAdapter : CsvAdapter {

    override val id = "カード明細(見出し無しの13列)"

    private const val COLUMNS = 13
    private const val STORE = 1
    private const val AMOUNT = 6

    /** 1行目がもう明細: 日付で始まり、利用金額の列が数字。 */
    override fun matches(header: List<String>): Boolean =
        header.size == COLUMNS &&
            FieldParsers.parseDate(header[0].trim()) != null &&
            header[STORE].isNotBlank() &&
            FieldParsers.parseAmount(header[AMOUNT].trim()) != null

    /** 見出しが無いので、1行目([header])も明細として読む。 */
    override fun parse(header: List<String>, rows: List<List<String>>): ParseResult {
        val transactions = mutableListOf<BankTransaction>()
        val skipped = mutableListOf<SkippedRow>()

        (listOf(header) + rows).forEachIndexed { i, row ->
            val lineNumber = i + 1
            fun cell(at: Int) = row.getOrNull(at).orEmpty().trim()
            if (row.all { it.isBlank() }) return@forEachIndexed

            val date = FieldParsers.parseDate(cell(0))
            val amount = FieldParsers.parseAmount(cell(AMOUNT))
            if (date == null || amount == null) {
                skipped += SkippedRow(lineNumber, if (date == null) "日付を読めない" else "利用金額を読めない", row.joinToString(","))
                return@forEachIndexed
            }
            transactions += CardStatementAdapter.cardTransaction(date, cell(STORE), amount)
        }
        return ParseResult(id, ParsedData.Transactions(transactions), skipped)
    }
}
