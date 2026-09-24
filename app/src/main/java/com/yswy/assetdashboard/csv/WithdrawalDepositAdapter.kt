package com.yswy.assetdashboard.csv

/**
 * `年月日, お引出し, お預入れ, お取り扱い内容, 残高, メモ, ラベル` 形式。
 *
 * メイン口座の明細(meisai.csv)がこの形。銀行名で決め打ちせず
 * **列の顔ぶれ**で判定しているので、同じ列構成の他行でもそのまま通る。
 *
 * 列の順番には依存しない。ヘッダー名から位置を引くので、
 * 並びが変わっても、後ろに列が増えても壊れない。
 */
object WithdrawalDepositAdapter : CsvAdapter {

    override val id = "年月日・お引出し・お預入れ形式"

    /** これが揃っていれば扱える。メモ・ラベルは無くてもよい。 */
    private val REQUIRED = listOf("年月日", "お引出し", "お預入れ", "残高")

    private const val DESCRIPTION = "お取り扱い内容"
    private const val MEMO = "メモ"
    private const val LABEL = "ラベル"

    override fun matches(header: List<String>): Boolean {
        val normalized = header.map { it.normalizeHeader() }
        return REQUIRED.all { required -> normalized.any { it == required } }
    }

    override fun parse(header: List<String>, rows: List<List<String>>): ParseResult {
        val index = header.mapIndexed { i, name -> name.normalizeHeader() to i }.toMap()

        val dateAt = index.getValue("年月日")
        val withdrawalAt = index.getValue("お引出し")
        val depositAt = index.getValue("お預入れ")
        val balanceAt = index.getValue("残高")
        val descriptionAt = index[DESCRIPTION]
        val memoAt = index[MEMO]
        val labelAt = index[LABEL]

        val transactions = mutableListOf<BankTransaction>()
        val skipped = mutableListOf<SkippedRow>()

        rows.forEachIndexed { i, row ->
            // ヘッダーが1行目なので、データ行の実際の行番号は +2
            val lineNumber = i + 2
            fun cell(at: Int?): String = at?.let { row.getOrNull(it) }.orEmpty()

            val date = FieldParsers.parseDate(cell(dateAt))
            if (date == null) {
                // 合計行やフッターが混ざることがある。落とさずに飛ばす。
                skipped += SkippedRow(lineNumber, "日付を読めない", row.joinToString(","))
                return@forEachIndexed
            }

            val withdrawal = FieldParsers.parseAmount(cell(withdrawalAt))
            val deposit = FieldParsers.parseAmount(cell(depositAt))
            if (withdrawal == null && deposit == null) {
                skipped += SkippedRow(lineNumber, "入出金の金額が両方とも空", row.joinToString(","))
                return@forEachIndexed
            }

            transactions += BankTransaction(
                date = date,
                description = cell(descriptionAt),
                withdrawal = withdrawal,
                deposit = deposit,
                balance = FieldParsers.parseAmount(cell(balanceAt)),
                memo = cell(memoAt).takeIf { it.isNotEmpty() },
                label = cell(labelAt).takeIf { it.isNotEmpty() },
            )
        }

        return ParseResult(id, ParsedData.Transactions(transactions), skipped)
    }
}

/**
 * ヘッダー名の表記ゆれを吸収する。
 * 全角スペース・半角スペース・BOMの残り・引用符を落とす。
 */
internal fun String.normalizeHeader(): String =
    trim().removeSurrounding("\"").replace("　", "").replace(" ", "").replace("﻿", "")
