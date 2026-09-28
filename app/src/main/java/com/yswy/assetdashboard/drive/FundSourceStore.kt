package com.yswy.assetdashboard.drive

import android.util.Log
import com.yswy.assetdashboard.data.FundLocalStore
import com.yswy.assetdashboard.data.FundSource
import com.yswy.assetdashboard.data.Nav
import com.yswy.assetdashboard.data.NavBase
import org.json.JSONObject

/**
 * ファンドの基準価額の取り先(E05-09)を、Driveの `settings/funds.json` に読み書きする。
 * 人が決めたもので作り直せないので settings に置く。形は[CategoryStore]にそろえた(`formatVersion` 付きで丸ごと書き戻す)。
 */
object FundSourceStore {

    private const val TAG = "FundSourceStore"
    private const val FILE_NAME = "funds.json"
    const val FORMAT_VERSION = 1

    /** まだ無ければ空。読めなければnull(空と区別する。空で上書きすると全部消えたように見える)。 */
    suspend fun load(api: DriveApi, folders: AppFolders): List<FundSource>? {
        val fileId = try {
            api.findFile(FILE_NAME, folders.settings)
        } catch (e: Exception) {
            Log.w(TAG, "fundsを探せなかった", e)
            return null
        } ?: return emptyList()
        return try {
            parse(String(api.download(fileId), Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "fundsを読めなかった", e)
            null
        }
    }

    suspend fun save(api: DriveApi, folders: AppFolders, sources: List<FundSource>): Boolean = try {
        api.putTextFile(FILE_NAME, folders.settings, render(sources), "application/json")
        true
    } catch (e: Exception) {
        Log.w(TAG, "fundsを書けなかった", e)
        false
    }

    fun render(sources: List<FundSource>): String = JSONObject()
        .put("formatVersion", FORMAT_VERSION)
        .put("funds", org.json.JSONArray(FundLocalStore.encodeSources(sources)))
        .toString(2)

    /** 読めるものだけ返す。形の合わないコードの行は落とす。 */
    fun parse(json: String): List<FundSource> =
        FundLocalStore.decodeSources(JSONObject(json).optJSONArray("funds")?.toString())

    /**
     * 同じファンドの取り先を置き換える。コードが空ならその取り先をやめる。
     * 決めてあった比べる基準(E05-14)・固定した最高値と通知する/しない(E05-15)は残す。
     */
    fun upsert(sources: List<FundSource>, source: FundSource, remove: Boolean = false): List<FundSource> {
        val kept = sources.firstOrNull { it.fundKey == source.fundKey }
            ?.let { source.copy(base = it.base, peakBase = it.peakBase, notify = it.notify) } ?: source
        return sources.filterNot { it.fundKey == source.fundKey } + if (remove) emptyList() else listOf(kept)
    }

    /**
     * そのファンドの比べる基準を変える(E05-14)。取り先の無いファンドならnull(基準価額が取れないので決められない)。
     * 最高値なら、その時点の最高値を基準の値として固定する(E05-15)。平均取得単価に戻すと固定した値は消す。
     */
    fun setBase(sources: List<FundSource>, fundKey: String, base: NavBase, peakBase: Nav? = null): List<FundSource>? {
        if (sources.none { it.fundKey == fundKey }) return null
        val fixed = if (base == NavBase.PEAK) peakBase else null
        return sources.map { if (it.fundKey == fundKey) it.copy(base = base, peakBase = fixed) else it }
    }

    /** 通知する/しないを変える(E05-15)。取り先の無いファンドならnull。 */
    fun setNotify(sources: List<FundSource>, fundKey: String, notify: Boolean): List<FundSource>? {
        if (sources.none { it.fundKey == fundKey }) return null
        return sources.map { if (it.fundKey == fundKey) it.copy(notify = notify) else it }
    }

    /** 名前で探して見つけた取り先をまとめて足す(E05-10)。すでに取り先のあるファンドは変えない(人が入れたものを上書きしない)。 */
    fun addMissing(sources: List<FundSource>, found: List<FundSource>): List<FundSource> {
        val known = sources.map { it.fundKey }.toSet()
        return sources + found.filterNot { it.fundKey in known }.distinctBy { it.fundKey }
    }
}
