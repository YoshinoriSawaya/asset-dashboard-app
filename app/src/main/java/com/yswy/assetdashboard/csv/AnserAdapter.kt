package com.yswy.assetdashboard.csv

import java.time.LocalDate

/**
 * `レコード区分, 年, 月, 日, ..., 取引名, 取扱日付　年/月/日, 金額, 取引後残高, 摘要, コメント` 形式。
 *
 * 入出金明細の通知(ANSER系)でよくある形。前の形式と違うのは2点:
 * - **日付が3列に分かれている**(年・月・日が別カラム)
 * - **金額が1列しかない**。入金か出金かは[取引名]で決まる
 *
 * 日付は`取扱日付`(取引が実際にあった日)を優先し、空なら
 * レコード自体の`年/月/日`にフォールバックする。
 */
object AnserAdapter : CsvAdapter {

    override val id = "取引名・取扱日付・金額形式"

    private val REQUIRED = listOf("取引名", "金額", "取引後残高")

    // 全角スペースはnormalizeHeaderで落ちるので "取扱日付年" になる
    private const val TX_YEAR = "取扱日付年"
    private const val TX_MONTH = "取扱日付月"
    private const val TX_DAY = "取扱日付日"

    /**
     * 出金を表す語。[取引名]にこれらが含まれれば出金とみなす。
     *
     * 実データに出てきた取引名はログに出しているので、
     * 想定外の値があればここに足す(`CsvIngest`のログを参照)。
     */
    private val WITHDRAWAL_WORDS = listOf("出金", "引落", "引き落", "支払", "振込", "送金", "手数料")
    private val DEPOSIT_WORDS = listOf("入金", "入付", "振替入", "返金")

    override fun matches(header: List<String>): Boolean {
        val normalized = header.map { it.normalizeHeader() }
        return REQUIRED.all { required -> normalized.any { it == required } }
    }

    override fun parse(header: List<String>, rows: List<List<String>>): ParseResult {
        val index = header.mapIndexed { i, name -> name.normalizeHeader() to i }.toMap()

        val txNameAt = index.getValue("取引名")
        val amountAt = index.getValue("金額")
        val balanceAt = index.getValue("取引後残高")
        val descriptionAt = index["摘要"]
        val commentAt = index["コメント"]

        val transactions = mutableListOf<BankTransaction>()
        val skipped = mutableListOf<SkippedRow>()

        rows.forEachIndexed { i, row ->
            val lineNumber = i + 2
            fun cell(at: Int?): String = at?.let { row.getOrNull(it) }.orEmpty()

            val date = readDate(index, ::cell, row, TX_YEAR, TX_MONTH, TX_DAY)
                ?: readDate(index, ::cell, row, "年", "月", "日")
            if (date == null) {
                skipped += SkippedRow(lineNumber, "日付を読めない", row.joinToString(","))
                return@forEachIndexed
            }

            val amount = FieldParsers.parseAmount(cell(amountAt))
            if (amount == null) {
                skipped += SkippedRow(lineNumber, "金額が空", row.joinToString(","))
                return@forEachIndexed
            }

            val txName = cell(txNameAt)

            val isWithdrawal = classify(txName, amount)
            if (isWithdrawal == null) {
                skipped += SkippedRow(
                    lineNumber,
                    "入金か出金か判定できない取引名: 「$txName」",
                    row.joinToString(","),
                )
                return@forEachIndexed
            }

            val magnitude = kotlin.math.abs(amount)
            transactions += BankTransaction(
                date = date,
                description = cell(descriptionAt).ifEmpty { txName },
                withdrawal = if (isWithdrawal) magnitude else null,
                deposit = if (isWithdrawal) null else magnitude,
                balance = FieldParsers.parseAmount(cell(balanceAt)),
                memo = cell(commentAt).takeIf { it.isNotEmpty() },
                label = txName.takeIf { it.isNotEmpty() },
            )
        }

        // 取引名は BankTransaction.label に入れてあるので、
        // 判定の当たり外れの確認(どんな取引名が来たか)は呼び出し側でできる。
        return ParseResult(id, ParsedData.Transactions(transactions), skipped)
    }

    /**
     * 出金ならtrue、入金ならfalse、判定できなければnull。
     *
     * 金額が負ならそれを信じる。そうでなければ取引名の語で判断する。
     */
    private fun classify(txName: String, amount: Long): Boolean? {
        if (amount < 0) return true
        if (WITHDRAWAL_WORDS.any { txName.contains(it) }) return true
        if (DEPOSIT_WORDS.any { txName.contains(it) }) return false
        return null
    }

    private fun readDate(
        index: Map<String, Int>,
        cell: (Int?) -> String,
        row: List<String>,
        yearKey: String,
        monthKey: String,
        dayKey: String,
    ): LocalDate? {
        val year = FieldParsers.parseAmount(cell(index[yearKey]))?.toInt() ?: return null
        val month = FieldParsers.parseAmount(cell(index[monthKey]))?.toInt() ?: return null
        val day = FieldParsers.parseAmount(cell(index[dayKey]))?.toInt() ?: return null
        // 2桁年(26 → 2026)にも一応備えておく
        val fullYear = if (year in 0..99) 2000 + year else year
        return FieldParsers.dateOf(fullYear, month, day)
    }
}
