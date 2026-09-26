package com.yswy.assetdashboard.data

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.CardStatementAdapter
import com.yswy.assetdashboard.csv.ParsedData
import com.yswy.assetdashboard.drive.BackupReader
import com.yswy.assetdashboard.drive.CacheSync
import com.yswy.assetdashboard.drive.CategoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 明細のカテゴリ(E07-21)と積立投資の目安(E10-04)。値はすべて作り物。 */
class CategoriesTest {

    private val settings = CategorySettings(
        CategorySettings.DEFAULT_CATEGORIES,
        listOf(
            CategoryRule("振替", "振替"),
            CategoryRule("ＳＢＩ", "積立投資"),
            CategoryRule("家具店", "家具・家電"),
            CategoryRule("レジャー", "遊び代"),
            CategoryRule("スーパー", "食費"),
        ),
    )

    @Test
    fun `摘要の言葉でカテゴリを決め、長い言葉を優先し、全角半角は区別しない`() {
        assertEquals("振替", settings.categoryOf("ﾌﾘｶｴ 振替 自分")?.name)
        assertEquals("積立投資", settings.categoryOf("SBI証券")?.name)
        assertNull(settings.categoryOf("電気代"))
        val detailed = settings.assign("ＳＢＩ証券手数料", "遊び代")
        assertEquals("遊び代", detailed.categoryOf("ＳＢＩ証券手数料")?.name)
        assertEquals("積立投資", detailed.categoryOf("ＳＢＩ証券投信積立")?.name)
    }

    @Test
    fun `摘要の付け替え・外す、カテゴリを消すとその決まりも消える`() {
        val moved = settings.assign("スーパー", "遊び代")
        assertEquals("遊び代", moved.categoryOf("スーパーA")?.name)
        assertNull(settings.assign("スーパー", null).categoryOf("スーパーA"))
        val removed = settings.withoutCategory("食費")
        assertNull(removed.categoryOf("スーパーA"))
        assertEquals(4, removed.categories.size)
    }

    @Test
    fun `前の除く言葉は生活費以外のカテゴリとして引き継ぐ`() {
        val legacy = CategorySettings.fromLegacy(listOf("投信積立"))
        assertEquals(CategoryKind.DISCRETIONARY, legacy.categoryOf("ＳＢＩ証券投信積立サービス")?.kind)
        assertEquals(CategorySettings.EMPTY, CategorySettings.fromLegacy(emptyList()))
    }

    @Test
    fun `categoriesjsonに書いて読み戻せ、知らない種類と名前の無いカテゴリの決まりは落とす`() {
        assertEquals(settings, CategoryStore.parse(CategoryStore.render(settings)))
        val broken = """{"formatVersion":1,"categories":[{"name":"食費","kind":"LIVING"},{"name":"謎","kind":"UNKNOWN"}],
            "rules":[{"keyword":"スーパー","category":"食費"},{"keyword":"x","category":"謎"},{"keyword":"","category":"食費"}]}"""
        assertEquals(
            CategorySettings(listOf(Category("食費", CategoryKind.LIVING)), listOf(CategoryRule("スーパー", "食費"))),
            CategoryStore.parse(broken),
        )
    }

    private fun bank(date: String, desc: String, out: Long? = null, inn: Long? = null) =
        BankTransaction(LocalDate.parse(date), desc, out, inn, null)

    private fun card(date: String, desc: String, out: Long) =
        BankTransaction(LocalDate.parse(date), desc, out, null, null, label = CardStatementAdapter.LABEL)

    /** 2026年8月: 給与30万、振替10万(出と入)、スーパー4万、レジャー2万、家具8万、SBI積立3万(カード)、電気代1万。 */
    private val august = listOf(
        bank("2026-08-25", "給与", inn = 300_000),
        bank("2026-08-26", "振替 口座A", out = 100_000),
        bank("2026-08-26", "振替 口座B", inn = 100_000),
        bank("2026-08-10", "電気代", out = 10_000),
        card("2026-08-05", "スーパー", 40_000),
        card("2026-08-12", "レジャー", 20_000),
        card("2026-08-15", "家具店", 80_000),
        card("2026-08-20", "ＳＢＩ証券投信積立", 30_000),
    )

    @Test
    fun `種類ごとに、収入・支出・生活費・消費・積立投資に入るかが決まる`() {
        val snapshot = CacheSync.build(
            listOf(BackupReader.Backup("f", "f.csv", ParsedData.Transactions(august))),
            corrections = emptyList(), settings = emptyList(), categories = settings,
        )
        val aug = Summary.cashflow(snapshot.transactions, Summary.month(YearMonth.of(2026, 8)))
        assertEquals(300_000L, aug.incomeYen) // 振替の入金は入れない
        assertEquals(10_000L, aug.spendingYen) // 銀行の出金。振替は入れない(カードは口座を出入りしない)
        assertEquals(50_000L, aug.livingSpendingYen) // 電気代 + スーパー
        assertEquals(70_000L, aug.consumptionYen) // + レジャー
        assertEquals(30_000L, aug.investmentYen)
    }

    @Test
    fun `積立投資の目安は、収入から消費と守りのお金を引いた残りの8割`() {
        val snapshot = CacheSync.build(
            listOf(BackupReader.Backup("f", "f.csv", ParsedData.Transactions(august))),
            corrections = emptyList(), settings = emptyList(), categories = settings,
        )
        val monthly = Summary.monthlyCashflow(snapshot.transactions)
        val fund = ItemOverview.Goal(Item.Goal("f", "生活防衛資金", 600_000, refillMonths = 6), 540_000) // あと6万を6か月 → 1万
        val sinking = ItemOverview.Goal(
            Item.Goal("s", "家電の積立", null, sinking = SinkingFund()), 0,
            sinking = SinkingPlan.Result(nextYearYen = 0, monthlyYen = 20_000, horizonYears = 5, occurrences = emptyList()),
        )
        // 積み増し中: 期日(2027-03-31)まで6か月で あと6万 → 月1万
        val car = ItemOverview.Goal(
            Item.Goal("c", "車の頭金", 100_000, dueDate = LocalDate.of(2027, 3, 31), rampUpMonths = 12), 40_000,
        )
        val plan = InvestPlan.of(monthly, listOf(fund, sinking, car), LocalDate.of(2026, 9, 26))!!
        val rampUp = (RampUp.of(car.item, 100_000, 40_000, LocalDate.of(2026, 9, 26)) as RampUp.Active).monthlyYen
        // (300,000 − 70,000 − 10,000 − 20,000 − 積み増し) × 0.8(1000円未満切り捨て)
        val surplus = 200_000L - rampUp
        assertEquals(InvestPlan(1, 300_000, 70_000, 10_000, 20_000, rampUp, 30_000, (surplus * 0.8).toLong() / 1000 * 1000), plan)
        assertEquals(surplus, plan.surplusYen)
        // 今月しか無ければ出さない
        assertNull(InvestPlan.of(monthly, emptyList(), LocalDate.of(2026, 8, 30)))
    }
}
