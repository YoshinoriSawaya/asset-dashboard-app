package com.yswy.assetdashboard.notify

import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.drive.FundSourceStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 基準価額の取り先をファンド名で探す(E05-10)。値はすべて作り物。 */
class FundSearchTest {

    /** 形は投信総合検索ライブラリーの検索の応答に合わせた(使う項目だけ。値は作り物)。 */
    private val response = """
        {"statusCode":"200","searchResultInfo":{"recordsTotal":3,"resultInfoMapList":[
          {"isinCd":"JP90C0000AA1","associFundCd":"0000001A","fundNm":"ｻﾝﾌﾟﾙ　全世界株式（オール）","fundStNm":"ｻﾝﾌﾟﾙ全世界株式《愛称》","entrustCmpNm":"例示アセット"},
          {"isinCd":"JP90C0000BB2","associFundCd":"0000002B","fundNm":"サンプル全世界株式（除く日本）","fundStNm":"","entrustCmpNm":"例示アセット"},
          {"isinCd":"bad","associFundCd":"0000003C","fundNm":"コードの形が合わない","entrustCmpNm":"例示アセット"}
        ]}}
    """.trimIndent()

    @Test
    fun `応答から候補を読み、コードの形が合わない行は落とす`() {
        val list = FundSearch.parse(response)!!
        assertEquals(listOf("JP90C0000AA1", "JP90C0000BB2"), list.map { it.isin })
        assertEquals("0000001A", list[0].code)
        assertEquals("例示アセット", list[0].company)
    }

    @Test
    fun `読めない応答はnullで、0件と区別する`() {
        assertNull(FundSearch.parse("<html>"))
        assertEquals(emptyList<FundSearch.Candidate>(), FundSearch.parse("""{"searchResultInfo":{"resultInfoMapList":[]}}"""))
    }

    @Test
    fun `書き方まで同じ候補は、全角半角と空白の違いを気にせず1本に決める`() {
        val list = FundSearch.parse(response)!!
        assertEquals("JP90C0000AA1", FundSearch.exactMatch("サンプル 全世界株式(オール)", list)?.isin)
        assertEquals("JP90C0000BB2", FundSearch.exactMatch("サンプル全世界株式（除く日本）", list)?.isin)
    }

    @Test
    fun `名前の一部しか合わないときは決めない`() {
        val list = FundSearch.parse(response)!!
        assertNull(FundSearch.exactMatch("サンプル全世界株式", list))
    }

    @Test
    fun `同じ名前の候補が別のコードで2本あれば決めない`() {
        val one = FundSearch.Candidate("JP90C0000AA1", "0000001A", "サンプル債券", "例示アセット")
        val two = FundSearch.Candidate("JP90C0000CC3", "0000004D", "サンプル債券", "別の例示アセット")
        assertNull(FundSearch.exactMatch("サンプル債券", listOf(one, two)))
        // 同じファンドが2回出ただけなら決める
        assertEquals("JP90C0000AA1", FundSearch.exactMatch("サンプル債券", listOf(one, one))?.isin)
    }

    @Test
    fun `検索はファンド名で、前後の空白を落として送る`() {
        val body = JSONObject(FundSearch.requestBody("  サンプル  "))
        assertEquals("サンプル", body.getString("s_keyword"))
        assertEquals("1", body.getString("s_kensakuKbn"))
    }

    @Test
    fun `まとめて探した取り先は、すでに入っているファンドを上書きしない`() {
        val mine = FundSource("NISA", "サンプルA", "JP90C0000AA1", "0000001A")
        val foundA = FundSource("NISA", "サンプルA", "JP90C0000ZZ9", "0000009Z")
        val foundB = FundSource("NISA", "サンプルB", "JP90C0000BB2", "0000002B")
        val result = FundSourceStore.addMissing(listOf(mine), listOf(foundA, foundB))
        assertEquals(listOf(mine, foundB), result)
    }
}
