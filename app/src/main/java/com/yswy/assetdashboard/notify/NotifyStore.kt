package com.yswy.assetdashboard.notify

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * 通知ごとに最後に出した日。同じ通知を繰り返しすぎないために使う(E05)。
 *
 * 端末にだけ置く。失っても「同じ通知がもう1度出る」だけなので、Driveには置かない。
 */
class NotifyStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("notify", Context.MODE_PRIVATE)

    fun load(): Map<String, LocalDate> = parse(prefs.getString(KEY, null))

    fun markNotified(keys: List<String>, today: LocalDate) {
        if (keys.isEmpty()) return
        val updated = prune(load() + keys.associateWith { today }, today)
        prefs.edit().putString(KEY, render(updated)).apply()
    }

    companion object {
        private const val KEY = "last_notified"

        /** リマインダーの鍵は期日ごとに増えるので、古いものは捨てる。 */
        const val KEEP_DAYS = 400L

        fun prune(map: Map<String, LocalDate>, today: LocalDate): Map<String, LocalDate> =
            map.filterValues { !it.isBefore(today.minusDays(KEEP_DAYS)) }

        fun render(map: Map<String, LocalDate>): String =
            JSONObject().apply { map.forEach { (k, v) -> put(k, v.toString()) } }.toString()

        fun parse(json: String?): Map<String, LocalDate> {
            if (json.isNullOrBlank()) return emptyMap()
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
            return obj.keys().asSequence().mapNotNull { k ->
                runCatching { LocalDate.parse(obj.getString(k)) }.getOrNull()?.let { k to it }
            }.toMap()
        }
    }
}
