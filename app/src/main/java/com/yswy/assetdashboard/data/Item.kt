package com.yswy.assetdashboard.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.LocalDate

enum class ItemType { METRIC, GOAL, REMINDER }

enum class Repeat { NONE, MONTHLY, YEARLY }

/**
 * 項目(Metric/Goal/Reminder)の行。
 *
 * 3種類を1テーブルに入れ、[type]で分ける。種類ごとに使わない列はnull。
 * どの列をどの種類が使うかはE02-01の表を正とする。
 *
 * コードではこの行を直接触らず、[toItem]で[Item]に変換して使う。
 * 行は汎用、型はKotlin側で守る、という分担。
 *
 * 正はDriveの `settings/items.json` で、これはそのキャッシュ。
 * 現在値は持たない(表示のたびに[MetricPointEntity]から計算する)。
 */
@Entity(tableName = "item")
data class ItemEntity(
    @PrimaryKey val id: String,
    val type: ItemType,
    val name: String,
    val metricKey: String? = null,
    val targetYen: Long? = null,
    val dueDate: LocalDate? = null,
    val repeat: Repeat? = null,
    val sortOrder: Int = 0,
    val hidden: Boolean = false,
    /** 目標額を支出から自動で出すとき(E07-06)。平均を取る月数と、何か月分か。 */
    val autoAverageMonths: Int? = null,
    val autoCoverMonths: Int? = null,
    /** 毎年1月にリセットする枠(ふるさと納税・NISAの年間枠など)。E07-09。 */
    @ColumnInfo(defaultValue = "0")
    val resetsYearly: Boolean = false,
    /** 期日の何か月前から積み増すか(E07-11)。 */
    val rampUpMonths: Int? = null,
    /** 自動の目標の下限。生活費の何か月分か(E07-12)。 */
    val autoFloorMonths: Int? = null,
)

/**
 * 目標額を支出の実績から出す決まり(E07-06)。
 * 「直近[averageMonths]か月の生活費の平均 × [coverMonths]か月」。
 */
data class AutoTarget(
    val averageMonths: Int = 6,
    val coverMonths: Int = 6,
    /**
     * 下限。生活費の何か月分か(E07-12)。目標額から下限までは取り崩してよく、
     * 下限を割ったら強く知らせる。決めていなければnull(目標額を割ったら知らせる)。
     */
    val floorMonths: Int? = null,
)

/** 型の付いた項目。[ItemEntity]との行き来は[toItem] / [toEntity]。 */
sealed interface Item {
    val id: String
    val name: String
    val sortOrder: Int
    val hidden: Boolean

    /** 1つの系列(metricKey)を表示する。 */
    data class Metric(
        override val id: String,
        override val name: String,
        val metricKey: String,
        override val sortOrder: Int = 0,
        override val hidden: Boolean = false,
    ) : Item

    /**
     * 目標額に向けた進捗。
     *
     * [metricKey]は進捗を測る系列。Goalを先に作って系列は後で
     * 紐づける、という順番もありうるのでnullを許す(その間は進捗不明)。
     *
     * 目標額は[targetYen](決まった額)か[autoTarget](支出から出す。E07-06)の
     * どちらか。両方あれば自動のほうを使う。実際の額は[ItemOverview.Goal.targetYen]。
     */
    data class Goal(
        override val id: String,
        override val name: String,
        val targetYen: Long?,
        val metricKey: String? = null,
        val dueDate: LocalDate? = null,
        override val sortOrder: Int = 0,
        override val hidden: Boolean = false,
        val autoTarget: AutoTarget? = null,
        /**
         * 毎年1月にリセットする枠(E07-09)。進捗は「今年使った額 ÷ 枠」で、
         * 系列の値は今年の累計。去年の点は数えない。
         */
        val resetsYearly: Boolean = false,
        /**
         * 期日の何か月前から積み増すか(E07-11)。期日([dueDate])があるときだけ意味を持つ。
         * それまでは何もしなくてよく、この時期に入ったら期日までの月々の額を出して知らせる。
         */
        val rampUpMonths: Int? = null,
    ) : Item

    data class Reminder(
        override val id: String,
        override val name: String,
        /** 次の期日。繰り返すものは、済んだら次の回に進める。 */
        val dueDate: LocalDate,
        val repeat: Repeat = Repeat.NONE,
        override val sortOrder: Int = 0,
        override val hidden: Boolean = false,
        /**
         * かかりそうな額(任意)。大型出費のカレンダー(E09-04)に出す。
         * 行の `targetYen` 列に入れる(列を増やさない。汎用スキーマ)。
         */
        val amountYen: Long? = null,
    ) : Item

    companion object {
        /**
         * Metric項目のid。metricKeyから決まる形にしておくと、自動で生えた
         * 項目を後から編集してsettingsに書いても、同じ項目として扱える。
         */
        fun metricId(metricKey: String): String = "metric:$metricKey"
    }
}

/**
 * 行を型付きの項目にする。種類に必要な列が欠けていればnull。
 *
 * items.jsonを手で壊した、古い形式が残っていた、などで起きうる。
 * 1件のために一覧全体を捨てないよう、例外ではなくnullで返して
 * 呼ぶ側でスキップさせる(CLAUDE.mdの「落ちるよりスキップ」)。
 */
fun ItemEntity.toItem(): Item? = when (type) {
    ItemType.METRIC -> metricKey?.let {
        Item.Metric(id, name, it, sortOrder, hidden)
    }
    ItemType.GOAL -> {
        val auto = if (autoAverageMonths != null && autoCoverMonths != null) {
            AutoTarget(autoAverageMonths, autoCoverMonths, autoFloorMonths)
        } else {
            null
        }
        // 目標額の決め方がどちらも無いGoalは読めない
        if (targetYen == null && auto == null) null
        else Item.Goal(id, name, targetYen, metricKey, dueDate, sortOrder, hidden, auto, resetsYearly, rampUpMonths)
    }
    ItemType.REMINDER -> dueDate?.let {
        Item.Reminder(id, name, it, repeat ?: Repeat.NONE, sortOrder, hidden, amountYen = targetYen)
    }
}

fun Item.toEntity(): ItemEntity = when (this) {
    is Item.Metric -> ItemEntity(
        id = id, type = ItemType.METRIC, name = name,
        metricKey = metricKey,
        sortOrder = sortOrder, hidden = hidden,
    )
    is Item.Goal -> ItemEntity(
        id = id, type = ItemType.GOAL, name = name,
        metricKey = metricKey, targetYen = targetYen, dueDate = dueDate,
        sortOrder = sortOrder, hidden = hidden,
        autoAverageMonths = autoTarget?.averageMonths, autoCoverMonths = autoTarget?.coverMonths,
        autoFloorMonths = autoTarget?.floorMonths,
        resetsYearly = resetsYearly,
        rampUpMonths = rampUpMonths,
    )
    is Item.Reminder -> ItemEntity(
        id = id, type = ItemType.REMINDER, name = name,
        dueDate = dueDate, repeat = repeat,
        sortOrder = sortOrder, hidden = hidden,
        targetYen = amountYen,
    )
}

@Dao
interface ItemDao {

    @Query("SELECT * FROM item ORDER BY sortOrder, name")
    suspend fun getAll(): List<ItemEntity>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun findById(id: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ItemEntity>)

    @Query("DELETE FROM item WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM item")
    suspend fun deleteAll()
}
