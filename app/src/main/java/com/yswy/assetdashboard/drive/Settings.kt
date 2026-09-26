package com.yswy.assetdashboard.drive

import android.util.Log
import com.yswy.assetdashboard.data.ItemEntity
import com.yswy.assetdashboard.data.ItemType
import com.yswy.assetdashboard.data.Repeat
import com.yswy.assetdashboard.data.toItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 項目の定義(Metric/Goal/Reminder)。Driveの `settings/items.json` が正。
 *
 * GoalやReminderは人がアプリで入力するもので、CSVからは作り直せない。
 * Roomにだけ置くとアプリを入れ直した瞬間に消えるので、Driveに置く(E02-01)。
 *
 * Metric項目は自動で生えるので(E02-01)、ここに載るのは人が名前を変えた・
 * 隠した・並べ替えたものだけ。
 *
 * 形式と扱いは[Corrections]に揃えてある: `formatVersion` 付きで丸ごと
 * 書き戻し、読めない項目は落として読める分だけ使う。
 */
object Settings {

    private const val TAG = "Settings"
    private const val FILE_NAME = "items.json"

    const val FORMAT_VERSION = 1

    /**
     * Driveから項目を読む。
     *
     * ファイルが無いのは普通(まだ何も設定していない)なので空を返す。
     * 探せない・読めないときは`null`を返し、空と区別する。空で上書きすると
     * 設定が全部消えたように見えるので、呼ぶ側は今のキャッシュを残すこと。
     */
    suspend fun load(api: DriveApi, folders: AppFolders): List<ItemEntity>? {
        val fileId = try {
            api.findFile(FILE_NAME, folders.settings) ?: return emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "items.jsonを探せなかった", e)
            return null
        }

        return try {
            parse(String(api.download(fileId), Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "items.jsonを読めなかった", e)
            null
        }
    }

    /** 全体を書き戻す。差分にしない理由は[Corrections.save]と同じ。 */
    suspend fun save(api: DriveApi, folders: AppFolders, items: List<ItemEntity>): Boolean = try {
        api.putTextFile(
            name = FILE_NAME,
            parentId = folders.settings,
            content = render(items),
            mimeType = "application/json",
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "items.jsonを書けなかった", e)
        false
    }

    /** 項目を足す。同じidがあれば置き換える。 */
    fun upsert(items: List<ItemEntity>, item: ItemEntity): List<ItemEntity> =
        items.filterNot { it.id == item.id } + item

    fun remove(items: List<ItemEntity>, id: String): List<ItemEntity> = items.filterNot { it.id == id }

    fun render(items: List<ItemEntity>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("type", item.type.name)
                    .put("name", item.name)
                    .put("sortOrder", item.sortOrder)
                    .put("hidden", item.hidden)
                    .also { json -> if (item.resetsYearly) json.put("resetsYearly", true) }
                    .also { json -> if (item.inNetWorth) json.put("inNetWorth", true) }
                    .also { json -> item.repeatYears?.let { json.put("repeatYears", it) } }
                    .also { json -> item.fundId?.let { json.put("fundId", it) } }
                    .also { json ->
                        item.sinkingYears?.let { years ->
                            json.put(
                                "sinkingFund",
                                JSONObject().put("horizonYears", years).put("growthRateBp", item.growthRateBp ?: 0),
                            )
                        }
                    }
                    .also { json -> item.rampUpMonths?.let { json.put("rampUpMonths", it) } }
                    .also { json ->
                        item.metricKey?.let { json.put("metricKey", it) }
                        item.targetYen?.let { json.put("targetYen", it) }
                        item.dueDate?.let { json.put("dueDate", it.toString()) }
                        item.repeat?.let { json.put("repeat", it.name) }
                        if (item.autoAverageMonths != null && item.autoCoverMonths != null) {
                            json.put(
                                "autoTarget",
                                JSONObject()
                                    .put("averageMonths", item.autoAverageMonths)
                                    .put("coverMonths", item.autoCoverMonths)
                                    .also { auto -> item.autoFloorMonths?.let { auto.put("floorMonths", it) } },
                            )
                        }
                    },
            )
        }
        return JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("items", array)
            .toString(2)
    }

    /**
     * 読める項目だけ返す。種類に必要な値が欠けた項目も落とす
     * ([toItem]がnullになるもの)。1件のために全部を捨てない。
     */
    fun parse(json: String): List<ItemEntity> {
        val array = JSONObject(json).optJSONArray("items") ?: return emptyList()
        val items = mutableListOf<ItemEntity>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
            val type = runCatching { ItemType.valueOf(obj.optString("type")) }.getOrNull() ?: continue

            val item = ItemEntity(
                id = id,
                type = type,
                name = obj.optString("name").ifBlank { id },
                metricKey = obj.optString("metricKey").takeIf { it.isNotBlank() },
                targetYen = if (obj.has("targetYen")) obj.optLong("targetYen") else null,
                dueDate = runCatching { LocalDate.parse(obj.optString("dueDate")) }.getOrNull(),
                repeat = runCatching { Repeat.valueOf(obj.optString("repeat")) }.getOrNull(),
                sortOrder = obj.optInt("sortOrder"),
                hidden = obj.optBoolean("hidden"),
                autoAverageMonths = obj.optJSONObject("autoTarget")?.optInt("averageMonths")?.takeIf { it > 0 },
                autoCoverMonths = obj.optJSONObject("autoTarget")?.optInt("coverMonths")?.takeIf { it > 0 },
                autoFloorMonths = obj.optJSONObject("autoTarget")?.optInt("floorMonths")?.takeIf { it > 0 },
                resetsYearly = obj.optBoolean("resetsYearly"),
                inNetWorth = obj.optBoolean("inNetWorth"),
                repeatYears = if (obj.has("repeatYears")) obj.optInt("repeatYears").takeIf { it > 1 } else null,
                fundId = obj.optString("fundId").takeIf { it.isNotBlank() },
                sinkingYears = obj.optJSONObject("sinkingFund")?.optInt("horizonYears")?.takeIf { it > 0 },
                growthRateBp = obj.optJSONObject("sinkingFund")?.optInt("growthRateBp"),
                rampUpMonths = if (obj.has("rampUpMonths")) obj.optInt("rampUpMonths").takeIf { it > 0 } else null,
            )
            if (item.toItem() == null) {
                Log.w(TAG, "項目を読めないので飛ばす: id=$id type=$type")
                continue
            }
            items += item
        }
        return items
    }
}
