package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupWriterTest {

    @Test
    fun `取引はkindがtransactionsになる`() {
        val data = ParsedData.Transactions(
            listOf(
                BankTransaction(
                    date = LocalDate.of(2026, 9, 1),
                    description = "給与",
                    withdrawal = null,
                    deposit = 300000,
                    balance = 500000,
                ),
            ),
        )

        val json = JSONObject(BackupWriter.render("meisai.csv", "id1", "テスト形式", data))

        assertEquals("transactions", json.getString("kind"))
        assertEquals("meisai.csv", json.getString("sourceFileName"))
        assertEquals(BackupWriter.FORMAT_VERSION, json.getInt("formatVersion"))

        val row = json.getJSONArray("transactions").getJSONObject(0)
        assertEquals("2026-09-01", row.getString("date"))
        assertEquals(300000L, row.getLong("deposit"))
        assertEquals(500000L, row.getLong("balance"))
    }

    @Test
    fun `null の項目はキーごと出さない`() {
        val data = ParsedData.Transactions(
            listOf(
                BankTransaction(
                    date = LocalDate.of(2026, 9, 1),
                    description = "給与",
                    withdrawal = null,
                    deposit = 300000,
                    balance = null,
                    memo = null,
                ),
            ),
        )

        val row = JSONObject(BackupWriter.render("a.csv", "id", "t", data))
            .getJSONArray("transactions").getJSONObject(0)

        // 「出金なし」と「出金0円」を取り違えないよう、キーごと落とす
        assertFalse(row.has("withdrawal"))
        assertFalse(row.has("balance"))
        assertFalse(row.has("memo"))
        assertTrue(row.has("deposit"))
    }

    @Test
    fun `Metricはkindがmetricsになる`() {
        val data = ParsedData.Metrics(
            listOf(
                MetricPoint("投資信託", LocalDate.of(2026, 9, 1), 1500000),
                MetricPoint("預金・現金", LocalDate.of(2026, 9, 1), 800000),
            ),
        )

        val json = JSONObject(BackupWriter.render("推移.csv", "id2", "資産推移形式", data))

        assertEquals("metrics", json.getString("kind"))
        val points = json.getJSONArray("metrics")
        assertEquals(2, points.length())
        assertEquals("投資信託", points.getJSONObject(0).getString("metricKey"))
        assertEquals(1500000L, points.getJSONObject(0).getLong("valueYen"))
    }

    @Test
    fun `空でも壊れたJSONにはならない`() {
        val json = JSONObject(
            BackupWriter.render("empty.csv", "id", "t", ParsedData.Metrics(emptyList())),
        )
        assertEquals(0, json.getJSONArray("metrics").length())
    }

    @Test
    fun `日本語のファイル名や項目名がそのまま入る`() {
        val data = ParsedData.Metrics(
            listOf(MetricPoint("預金・現金", LocalDate.of(2026, 9, 1), 100)),
        )
        val json = JSONObject(BackupWriter.render("資産推移月次 (10).csv", "id", "資産推移形式", data))

        assertEquals("資産推移月次 (10).csv", json.getString("sourceFileName"))
        assertEquals("預金・現金", json.getJSONArray("metrics").getJSONObject(0).getString("metricKey"))
    }
}
