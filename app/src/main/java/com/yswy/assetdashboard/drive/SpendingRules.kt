package com.yswy.assetdashboard.drive

import android.util.Log
import com.yswy.assetdashboard.csv.CardStatementAdapter
import com.yswy.assetdashboard.data.BankTransactionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate

/**
 * 生活費から除く出金の決まり(E07-06)。Driveの `settings/spending_rules.json` が正。
 *
 * 振替やカードの引き落としは出金だが、生活費ではない。摘要にこの言葉を
 * 含む出金を、生活費の計算から外す。人が画面で登録する。
 *
 * 形式と扱いは[Settings]・[Corrections]に揃えてある。
 */
object SpendingRules {

    private const val TAG = "SpendingRules"
    private const val FILE_NAME = "spending_rules.json"

    const val FORMAT_VERSION = 1

    /**
     * 除くかどうかを決める手がかり(E07-20)。出金の摘要ごとに、件数・合計・最後に使った日。
     * 画面にだけ出し、どこにも書き出さない。
     */
    data class Candidate(
        val description: String,
        val count: Int,
        val totalYen: Long,
        val lastDate: LocalDate,
        /** カードの利用明細の摘要か(銀行の明細でなく)。 */
        val fromCard: Boolean,
        /** 今のキャッシュで全部が生活費から外れている(保存済みの言葉、カードの引き落としの突き合わせ)。 */
        val excludedNow: Boolean,
        /** 入金の合計(E07-21)。振替・給与など、入金の摘要にもカテゴリを付けるため */
        val depositYen: Long = 0,
        /** 全部がカードの引き落とし(E01-14)で、内訳はカードの明細の側にある */
        val cardPaymentOnly: Boolean = false,
    )

    enum class Order(val label: String) { COUNT("件数順"), AMOUNT("金額順"), RECENT("最近順") }

    /** 明細を摘要ごとにまとめる。出金も入金も(振替は両方に出る。E07-21)。 */
    fun candidates(transactions: List<BankTransactionEntity>): List<Candidate> =
        transactions.filter { (it.withdrawal ?: 0) > 0 || (it.deposit ?: 0) > 0 }
            .groupBy { it.description }
            .map { (description, rows) ->
                Candidate(
                    description = description,
                    count = rows.size,
                    totalYen = rows.sumOf { it.withdrawal ?: 0 },
                    lastDate = rows.maxOf { it.date },
                    fromCard = rows.any { it.label == CardStatementAdapter.LABEL },
                    excludedNow = rows.all { it.excludedFromSpending },
                    depositYen = rows.sumOf { it.deposit ?: 0 },
                    cardPaymentOnly = rows.all { it.cardPayment },
                )
            }

    fun sorted(candidates: List<Candidate>, order: Order): List<Candidate> = when (order) {
        Order.COUNT -> candidates.sortedWith(compareByDescending<Candidate> { it.count }.thenByDescending { it.totalYen })
        Order.AMOUNT -> candidates.sortedByDescending { maxOf(it.totalYen, it.depositYen) }
        Order.RECENT -> candidates.sortedWith(compareByDescending<Candidate> { it.lastDate }.thenByDescending { it.count })
    }

    /**
     * カテゴリの無い出金の摘要だけ(E07-23)。カテゴリの無い出金は生活費として消費に入るので、
     * 振替・大型出費・積立投資が混ざっていないかを見直すのに使う。
     * 入金だけの摘要(給与など)と、カードの引き落とし(内訳はカードの明細)は出さない。
     */
    fun uncategorizedSpending(candidates: List<Candidate>, isCategorized: (String) -> Boolean): List<Candidate> =
        candidates.filter { it.totalYen > 0 && !it.cardPaymentOnly && !isCategorized(it.description) }

    /**
     * 摘要1つを除く・除かないに切り替えたあとの言葉(E07-20)。
     * 当たっていなければ摘要そのものを足す。当たっていれば、当たっている言葉を外す
     * (部分の言葉で当たっていれば、その言葉で除いていたほかの摘要も戻る)。
     */
    fun toggle(keywords: List<String>, description: String): List<String> =
        if (matches(description, keywords)) keywords.filterNot { matches(description, listOf(it)) }
        else keywords + description

    /**
     * 摘要が[keywords]のどれかを含むか。
     *
     * 全角・半角、大文字・小文字、空白の違いは無視する。銀行のCSVは
     * `ﾌﾘｶｴ` のような半角カナで出てくることが多く、人は `フリカエ` と
     * 入れるので、そのまま比べると当たらない。
     */
    fun matches(description: String, keywords: List<String>): Boolean {
        val text = normalize(description)
        return keywords.map(::normalize).any { it.isNotEmpty() && text.contains(it) }
    }

    /** NFKCで半角カナ→全角、全角英数→半角にそろえ、空白を落として小文字にする。 */
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("\\s"), "").lowercase()

    /** Driveから読む。無ければ空、読めなければnull(空と区別する。[Settings.load]と同じ)。 */
    suspend fun load(api: DriveApi, folders: AppFolders): List<String>? {
        val fileId = try {
            api.findFile(FILE_NAME, folders.settings) ?: return emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "spending_rulesを探せなかった", e)
            return null
        }
        return try {
            parse(String(api.download(fileId), Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "spending_rulesを読めなかった", e)
            null
        }
    }

    suspend fun save(api: DriveApi, folders: AppFolders, keywords: List<String>): Boolean = try {
        api.putTextFile(FILE_NAME, folders.settings, render(keywords), "application/json")
        true
    } catch (e: Exception) {
        Log.w(TAG, "spending_rulesを書けなかった", e)
        false
    }

    fun render(keywords: List<String>): String = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("excludeKeywords", JSONArray().apply { keywords.forEach { put(it) } })
        .toString(2)

    /** 空の言葉は落とす(全部の出金に当たってしまう)。 */
    fun parse(json: String): List<String> {
        val array = JSONObject(json).optJSONArray("excludeKeywords") ?: return emptyList()
        return (0 until array.length())
            .map { array.optString(it).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
