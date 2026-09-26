package com.yswy.assetdashboard.ui

import com.yswy.assetdashboard.data.Item
import com.yswy.assetdashboard.data.Repeat
import com.yswy.assetdashboard.data.SinkingFund
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 大型出費の予定の取り込み(E07-16)。値はすべて作り物。 */
class PlanImportTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `見出しと書き方の揺れを読み、読めない行は理由つきで飛ばす`() {
        val csv = """
            名前,次の時期,何年ごと,見込み額,積立先
            車検,2027-03,2,"150,000",車・家電
            浄化槽の点検,2027/6/15,,20000,家まわり
            壁の塗装,2035-05,0,250000,
            ,2027-01,1,1000,x
            日付なし,来年,1,1000,x
            周期おかしい,2027-01,-1,1000,x
            額なし,2027-01,1,,x
            車検,2029-03,2,150000,車・家電
        """.trimIndent()
        val parsed = PlanImport.parse(csv)

        assertEquals(
            listOf(
                PlanImport.Row("車検", d("2027-03-01"), 2, 150_000, "車・家電"),
                PlanImport.Row("浄化槽の点検", d("2027-06-15"), 1, 20_000, "家まわり"),
                PlanImport.Row("壁の塗装", d("2035-05-01"), null, 250_000, null),
            ),
            parsed.rows,
        )
        assertEquals(listOf(5, 6, 7, 8, 9), parsed.problems.map { it.lineNumber })
        assertEquals("同じ名前が前の行にもある", parsed.problems.last().reason)
    }

    @Test
    fun `次の時期はyyyymmddと年月だけの形も読む`() {
        val parsed = PlanImport.parse("a,20270315,1,1000,\nb,202703,1,1000,\nc,2027/3,1,1000,\nd,202713,1,1000,")
        assertEquals(listOf(d("2027-03-15"), d("2027-03-01"), d("2027-03-01")), parsed.rows.map { it.due })
        assertEquals(listOf(4), parsed.problems.map { it.lineNumber })
    }

    @Test
    fun `Shift_JISのファイルも読める`() {
        val bytes = "名前,次の時期,何年ごと,見込み額,積立先\r\n洗濯機,2034-06,10,200000,車・家電\r\n".toByteArray(charset("windows-31j"))
        assertEquals(listOf("洗濯機"), PlanImport.parse(bytes).rows.map { it.name })
    }

    @Test
    fun `名前で突き合わせて置き換え、無い積立先は作る`() {
        val existingFund = Item.Goal("fund-home", "家まわり", null, "預金・現金", sinking = SinkingFund(3, 200))
        val existingReminder = Item.Reminder("r-old", "車検", d("2026-03-01"), Repeat.YEARLY, sortOrder = 5, amountYen = 100_000)
        val rows = listOf(
            PlanImport.Row("車検", d("2027-03-01"), 2, 150_000, "車・家電"),
            PlanImport.Row("浄化槽の点検", d("2027-06-15"), 1, 20_000, "家まわり"),
            PlanImport.Row("洗濯機", d("2034-06-01"), 10, 200_000, "車・家電"),
            PlanImport.Row("壁の塗装", d("2035-05-01"), null, 250_000, null),
        )
        var n = 0
        val plan = PlanImport.plan(rows, listOf(existingFund, existingReminder)) { "id${++n}" }

        // 車・家電 は無いので1つだけ作る(2行で使っても1つ)
        assertEquals(listOf("車・家電"), plan.newFunds.map { it.name })
        val carFund = plan.newFunds.single()
        assertEquals(SinkingFund(), carFund.sinking)
        assertNull(carFund.metricKey)

        // 車検は置き換え(idと並び順は残る)
        val shaken = plan.replaced.single()
        assertEquals("r-old", shaken.id)
        assertEquals(5, shaken.sortOrder)
        assertEquals(2, shaken.repeatYears)
        assertEquals(carFund.id, shaken.fundId)

        assertEquals(listOf("浄化槽の点検", "洗濯機", "壁の塗装"), plan.added.map { it.name })
        assertEquals("fund-home", plan.added[0].fundId)
        assertEquals(Repeat.NONE, plan.added[2].repeat)
        assertNull(plan.added[2].fundId)
        assertEquals(mapOf("fund-home" to "家まわり", carFund.id to "車・家電"), plan.fundNames)
        assertEquals(1 + 3 + 1, plan.items.size)
    }

    @Test
    fun `取り込み直しても増えない`() {
        val rows = listOf(PlanImport.Row("車検", d("2027-03-01"), 2, 150_000, "車・家電"))
        val first = PlanImport.plan(rows, emptyList())
        val second = PlanImport.plan(rows, first.items)
        assertEquals(0, second.added.size)
        assertEquals(0, second.newFunds.size)
        assertEquals(first.added.single().id, second.replaced.single().id)
    }
}
