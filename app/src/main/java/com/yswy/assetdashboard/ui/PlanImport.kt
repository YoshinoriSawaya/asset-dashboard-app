package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.csv.CsvText
import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.Repeat
import com.yswy.assetdashboard.data.SinkingFund
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * 大型出費の予定をまとめて取り込む(E07-16)。Driveの `settings/plan.csv` を読み、
 * 見込み額つきのリマインダー(E07-15)として登録する。一度取り込んだら、以後はアプリで編集する。
 *
 * ```
 * 名前,次の時期,何年ごと,見込み額,積立先
 * 車検,2027-03,2,150000,車・家電
 * 浄化槽の点検,2027-06-15,1,20000,家まわり
 * 壁・屋根の塗装,2035-05,0,250000,家まわり
 * ```
 * - 次の時期: `20270315`・`2027-03-15`・`2027/3/15`、年と月だけなら `202703`・`2027-03`(その月の1日)
 * - 何年ごと: 空か1なら毎年、0なら一度だけ
 * - 積立先: 大型出費の積立の目標の名前。空なら積立に入れない。無ければ作る
 *
 * ## 名前で突き合わせる
 * 同じ名前のリマインダーがあれば置き換える(idと並び順は残す)。取り込み直しても増えない。
 * 名前が違えば別の予定として足す。アプリで消した予定は、取り込み直すとまた入る。
 *
 * ## 読めない行は飛ばして理由を出す
 * 1行のために全体を捨てない(CLAUDE.md)。行の中身は画面にだけ出し、ログには書かない。
 */
object PlanImport {

    const val FILE_NAME = "plan.csv"

    data class Row(val name: String, val due: LocalDate, val everyYears: Int?, val amountYen: Long, val fundName: String?)

    data class Problem(val lineNumber: Int, val reason: String)

    data class Parsed(val rows: List<Row>, val problems: List<Problem>)

    /** 登録の中身。[items]をsettingsに足す(同じidは置き換える)。 */
    data class Plan(
        val items: List<Item>,
        val added: List<Item.Reminder>,
        val replaced: List<Item.Reminder>,
        /** 積立先の名前の目標が無かったので、新しく作る積立の目標。 */
        val newFunds: List<Item.Goal>,
        /** 積立の目標のidと名前(既存と新しく作るもの)。画面に積立先の名前を出すのに使う。 */
        val fundNames: Map<String, String> = emptyMap(),
    )

    private val HEADER = listOf("名前", "次の時期", "何年ごと", "見込み額", "積立先")

    fun parse(bytes: ByteArray): Parsed = parse(CsvText.decode(bytes).text)

    fun parse(text: String): Parsed {
        val lines = CsvText.splitRows(text).map { row -> row.map { it.trim() } }
        val rows = mutableListOf<Row>()
        val problems = mutableListOf<Problem>()
        val seen = mutableSetOf<String>()
        lines.forEachIndexed { i, cells ->
            val lineNumber = i + 1
            if (cells.all { it.isEmpty() }) return@forEachIndexed
            // 見出しの行は読み飛ばす(無くてもよい)
            if (i == 0 && cells.firstOrNull() == HEADER[0]) return@forEachIndexed
            val name = cells.getOrNull(0).orEmpty()
            if (name.isEmpty()) {
                problems += Problem(lineNumber, "名前が空")
                return@forEachIndexed
            }
            // 名前で突き合わせるので、同じ名前は1つだけ(2つ目以降は、どちらが正しいか分からない)
            if (!seen.add(name)) {
                problems += Problem(lineNumber, "同じ名前が前の行にもある")
                return@forEachIndexed
            }
            val due = parseDate(cells.getOrNull(1).orEmpty())
            if (due == null) {
                problems += Problem(lineNumber, "次の時期を読めない(2027-03 や 2027-03-15 の形)")
                return@forEachIndexed
            }
            val everyText = cells.getOrNull(2).orEmpty()
            val every = if (everyText.isEmpty()) 1 else everyText.toIntOrNull()?.takeIf { it in 0..50 }
            if (every == null) {
                problems += Problem(lineNumber, "何年ごとを読めない(0〜50。空なら毎年)")
                return@forEachIndexed
            }
            val amount = CorrectionForm.parseYen(cells.getOrNull(3).orEmpty())?.takeIf { it > 0 }
            if (amount == null) {
                problems += Problem(lineNumber, "見込み額を読めない")
                return@forEachIndexed
            }
            rows += Row(name, due, every.takeIf { it > 0 }, amount, cells.getOrNull(4)?.takeIf { it.isNotEmpty() })
        }
        return Parsed(rows, problems)
    }

    /** 年と月だけ(`2027-03` `2027/3` `202703`)。その月の1日にする。 */
    private val MONTH_ONLY = Regex("""(\d{4})(?:[/\-](\d{1,2})|(\d{2}))""")

    /** 日付は画面の入力と同じ形(E07-17)に、年と月だけの形を足したもの。 */
    private fun parseDate(text: String): LocalDate? {
        DateInput.parse(text)?.let { return it }
        val m = MONTH_ONLY.matchEntire(text.trim()) ?: return null
        val (y, separated, compact) = m.destructured
        return runCatching { YearMonth.of(y.toInt(), separated.ifEmpty { compact }.toInt()).atDay(1) }.getOrNull()
    }

    /**
     * 読めた行を、今の項目と突き合わせて登録の中身にする。
     * @param existing settingsにある項目(と、画面に出ている項目)
     */
    fun plan(rows: List<Row>, existing: List<Item>, newId: () -> String = { UUID.randomUUID().toString() }): Plan {
        val reminders = existing.filterIsInstance<Item.Reminder>().associateBy { it.name }
        val funds = existing.filterIsInstance<Item.Goal>().filter { it.sinking != null }.associateBy { it.name }.toMutableMap()
        val newFunds = mutableListOf<Item.Goal>()
        val added = mutableListOf<Item.Reminder>()
        val replaced = mutableListOf<Item.Reminder>()

        for (row in rows) {
            val fundId = row.fundName?.let { name ->
                funds.getOrPut(name) {
                    // 積立の目標が無ければ作る。ならす年数と物価上昇率、測る系列はあとで目標の編集で決める
                    Item.Goal(newId(), name, targetYen = null, sortOrder = -1, sinking = SinkingFund()).also { newFunds += it }
                }.id
            }
            val before = reminders[row.name]
            val reminder = Item.Reminder(
                id = before?.id ?: newId(),
                name = row.name,
                dueDate = row.due,
                repeat = if (row.everyYears == null) Repeat.NONE else Repeat.YEARLY,
                sortOrder = before?.sortOrder ?: -1,
                hidden = before?.hidden ?: false,
                amountYen = row.amountYen,
                repeatYears = row.everyYears ?: 1,
                fundId = fundId,
            )
            if (before == null) added += reminder else replaced += reminder
        }
        return Plan(newFunds + added + replaced, added, replaced, newFunds, funds.values.associate { it.id to it.name })
    }
}
