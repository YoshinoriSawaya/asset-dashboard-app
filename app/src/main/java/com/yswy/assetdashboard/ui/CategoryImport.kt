package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.csv.CsvText
import com.yswy.assetdashboard.data.Category
import com.yswy.assetdashboard.data.CategoryKind
import com.yswy.assetdashboard.data.CategoryRule
import com.yswy.assetdashboard.data.CategorySettings

/**
 * 明細のカテゴリの決まりをまとめて取り込む(E07-22)。Driveの `settings/category_rules.csv` を読み、
 * 明細のカテゴリ(E07-21)に足す。一度取り込んだら、以後はアプリで直す(予定の取り込み(E07-16)と同じ)。
 *
 * ```
 * 言葉,カテゴリ,種類
 * ＮＩＮＴＥＮＤＯ,遊び代,遊び代
 * ＥＴＣ,交通,生活費
 * ニトリ,家具・家電,大型出費
 * ﾐﾂｲｽﾐﾄﾓｶ-ﾄﾞ,,
 * ```
 * - 種類は 生活費・遊び代・大型出費・振替・積立投資 のどれか。無いカテゴリは、この種類で作る
 * - カテゴリが空の行は、その言葉に当たる決まりを外す(カテゴリなし=生活費に戻す)
 *
 * ## 言葉を含む細かい決まりは外して、この言葉に寄せる
 * カテゴリは長い言葉が勝つ(E07-21)。前の除く言葉を引き継いだ決まりは摘要まるごとの長い言葉なので、
 * 「ＮＩＮＴＥＮＤＯ」のような短い言葉で付けても、長いほうが勝って変わらない。取り込む言葉を**含む**決まりは
 * 外して、取り込む言葉に寄せる。外すものは取り込む前に画面に出す。
 */
object CategoryImport {

    const val FILE_NAME = "category_rules.csv"

    data class Row(val keyword: String, val category: String?, val kind: CategoryKind?)

    data class Problem(val lineNumber: Int, val reason: String)

    data class Parsed(val rows: List<Row>, val problems: List<Problem>)

    data class Plan(
        val result: CategorySettings,
        /** 新しく作るカテゴリ。 */
        val newCategories: List<Category>,
        /** 足す・置き換える決まり。 */
        val rules: List<CategoryRule>,
        /** 外す決まり(取り込む言葉を含む細かい決まりと、カテゴリが空の行で外すもの)。 */
        val removed: List<CategoryRule>,
        /** 同じ名前のカテゴリが別の種類で既にある(今の種類のまま使う)。 */
        val kindMismatches: List<String>,
    )

    private val KINDS = CategoryKind.entries.associateBy { it.label }

    fun parse(bytes: ByteArray): Parsed = parse(CsvText.decode(bytes).text)

    fun parse(text: String): Parsed {
        val rows = mutableListOf<Row>()
        val problems = mutableListOf<Problem>()
        CsvText.splitRows(text).map { r -> r.map { it.trim() } }.forEachIndexed { i, cells ->
            val line = i + 1
            if (cells.all { it.isEmpty() }) return@forEachIndexed
            if (i == 0 && cells.firstOrNull() == "言葉") return@forEachIndexed
            val keyword = cells.getOrNull(0).orEmpty()
            if (keyword.isEmpty()) {
                problems += Problem(line, "言葉が空")
                return@forEachIndexed
            }
            val category = cells.getOrNull(1).orEmpty().ifEmpty { null }
            val kindText = cells.getOrNull(2).orEmpty()
            val kind = if (kindText.isEmpty()) null else KINDS[kindText]
            if (category != null && kindText.isNotEmpty() && kind == null) {
                problems += Problem(line, "種類は ${KINDS.keys.joinToString("・")} のどれか")
                return@forEachIndexed
            }
            rows += Row(keyword, category, kind)
        }
        return Parsed(rows, problems)
    }

    fun plan(rows: List<Row>, current: CategorySettings): Plan {
        var settings = current
        val newCategories = mutableListOf<Category>()
        val mismatches = mutableListOf<String>()
        val removed = mutableListOf<CategoryRule>()
        val added = mutableListOf<CategoryRule>()

        // このCSVで足した決まり。後ろの行の短い言葉で外さない(同じCSVの中は、長い言葉が勝つ普通の決まりに任せる)
        val fromThisImport = mutableSetOf<CategoryRule>()
        for (row in rows) {
            val key = CategorySettings.normalize(row.keyword)
            // この言葉を含む、取り込む前からあった決まりを外す(同じ言葉のものも)
            val (absorbed, kept) = settings.rules.partition {
                it !in fromThisImport && CategorySettings.normalize(it.keyword).contains(key)
            }
            removed += absorbed.filterNot { r -> row.category != null && CategorySettings.normalize(r.keyword) == key && r.category == row.category }
            settings = settings.copy(rules = kept)

            val name = row.category ?: continue
            val existing = settings.categories.firstOrNull { it.name == name }
            if (existing == null) {
                val created = Category(name, row.kind ?: CategoryKind.LIVING)
                settings = settings.withCategory(created)
                newCategories += created
            } else if (row.kind != null && row.kind != existing.kind) {
                mismatches += name
            }
            val rule = CategoryRule(row.keyword, name)
            settings = settings.copy(rules = settings.rules + rule)
            added += rule
            fromThisImport += rule
        }
        // 取り込みの中で足してから外したもの(同じ行どうし)は、外したものに数えない
        val finalRules = settings.rules.toSet()
        return Plan(
            result = settings,
            newCategories = newCategories,
            rules = added,
            removed = removed.distinct().filterNot { it in finalRules },
            kindMismatches = mismatches.distinct(),
        )
    }
}
