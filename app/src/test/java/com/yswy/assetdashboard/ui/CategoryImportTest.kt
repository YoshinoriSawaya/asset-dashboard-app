package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Category
import com.yswy.assetdashboard.data.CategoryKind
import com.yswy.assetdashboard.data.CategoryRule
import com.yswy.assetdashboard.data.CategorySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** カテゴリの決まりのまとめ取り込み(E07-22)。値はすべて作り物。 */
class CategoryImportTest {

    private val legacy = CategorySettings.fromLegacy(
        listOf("ＮＩＮＴＥＮＤＯ　ＣＣ１２３", "ＮＩＮＴＥＮＤＯ　ＣＤ４５６", "ﾐﾂｲｽﾐﾄﾓｶ-ﾄﾞ (ｶ", "ニトリ"),
    )

    @Test
    fun `見出しを飛ばし、種類の書き間違いと空の言葉は読めない行にする`() {
        val parsed = CategoryImport.parse("言葉,カテゴリ,種類\nＥＴＣ,交通,生活費\n,x,生活費\nA,B,生活費じゃない\nﾐﾂｲｽﾐﾄﾓｶ-ﾄﾞ,,")
        assertEquals(
            listOf(CategoryImport.Row("ＥＴＣ", "交通", CategoryKind.LIVING), CategoryImport.Row("ﾐﾂｲｽﾐﾄﾓｶ-ﾄﾞ", null, null)),
            parsed.rows,
        )
        assertEquals(listOf(3, 4), parsed.problems.map { it.lineNumber })
    }

    @Test
    fun `短い言葉で付けると、それを含む細かい決まりは外して寄せる`() {
        val rows = CategoryImport.parse("ＮＩＮＴＥＮＤＯ,遊び代,遊び代\nＥＴＣ,交通,生活費").rows
        val plan = CategoryImport.plan(rows, legacy)

        assertEquals(listOf(Category("交通", CategoryKind.LIVING)), plan.newCategories) // 遊び代は初めからある
        assertEquals(setOf("ＮＩＮＴＥＮＤＯ　ＣＣ１２３", "ＮＩＮＴＥＮＤＯ　ＣＤ４５６"), plan.removed.map { it.keyword }.toSet())
        assertEquals("遊び代", plan.result.categoryOf("ＮＩＮＴＥＮＤＯ　ＣＣ１２３")?.name)
        assertEquals("交通", plan.result.categoryOf("ＥＴＣ　関東支社")?.name)
        // 触っていない決まりは残る
        assertEquals(CategorySettings.LEGACY_CATEGORY, plan.result.categoryOf("ニトリ")?.name)
    }

    @Test
    fun `同じCSVで足した長い決まりは、後ろの行の短い言葉で外さない`() {
        // 「店舗 モール店」を日用品にしたあと、「モール」を遊び代にしても、長いほうが勝つ
        val rows = CategoryImport.parse("ドラッグＡ　モールＸ店,日用品,生活費\nモールＸ,遊び代,遊び代").rows
        val plan = CategoryImport.plan(rows, legacy)
        assertEquals("日用品", plan.result.categoryOf("ドラッグＡ　モールＸ店")?.name)
        assertEquals("遊び代", plan.result.categoryOf("モールＸ　専門店")?.name)
        assertTrue(plan.removed.none { it.category == "日用品" })
    }

    @Test
    fun `カテゴリが空の行は、その言葉に当たる決まりを外してカテゴリなしに戻す`() {
        val plan = CategoryImport.plan(CategoryImport.parse("ﾐﾂｲｽﾐﾄﾓｶ-ﾄﾞ,,").rows, legacy)
        assertNull(plan.result.categoryOf("ﾐﾂｲｽﾐﾄﾓｶ-ﾄﾞ (ｶ"))
        assertEquals(listOf("ﾐﾂｲｽﾐﾄﾓｶ-ﾄﾞ (ｶ"), plan.removed.map { it.keyword })
        assertTrue(plan.rules.isEmpty())
    }

    @Test
    fun `同じ名前のカテゴリが別の種類であれば今の種類のまま使い、知らせる`() {
        val plan = CategoryImport.plan(CategoryImport.parse("ニトリ,振替,大型出費").rows, legacy)
        assertEquals(listOf("振替"), plan.kindMismatches)
        assertEquals(CategoryKind.TRANSFER, plan.result.categoryOf("ニトリ")?.kind)
    }

    @Test
    fun `取り込み直しても決まりは増えず、外すものも出ない`() {
        val rows = CategoryImport.parse("ＮＩＮＴＥＮＤＯ,遊び代,遊び代").rows
        val once = CategoryImport.plan(rows, legacy).result
        val twice = CategoryImport.plan(rows, once)
        assertEquals(once.rules.toSet(), twice.result.rules.toSet())
        assertTrue(twice.removed.isEmpty())
        assertEquals(listOf(CategoryRule("ＮＩＮＴＥＮＤＯ", "遊び代")), twice.rules)
    }
}
