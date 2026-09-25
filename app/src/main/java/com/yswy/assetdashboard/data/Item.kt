package com.yswy.assetdashboard.data

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
     */
    data class Goal(
        override val id: String,
        override val name: String,
        val targetYen: Long,
        val metricKey: String? = null,
        val dueDate: LocalDate? = null,
        override val sortOrder: Int = 0,
        override val hidden: Boolean = false,
    ) : Item

    data class Reminder(
        override val id: String,
        override val name: String,
        /** 次の期日。繰り返すものは、済んだら次の回に進める。 */
        val dueDate: LocalDate,
        val repeat: Repeat = Repeat.NONE,
        override val sortOrder: Int = 0,
        override val hidden: Boolean = false,
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
    ItemType.GOAL -> targetYen?.let {
        Item.Goal(id, name, it, metricKey, dueDate, sortOrder, hidden)
    }
    ItemType.REMINDER -> dueDate?.let {
        Item.Reminder(id, name, it, repeat ?: Repeat.NONE, sortOrder, hidden)
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
    )
    is Item.Reminder -> ItemEntity(
        id = id, type = ItemType.REMINDER, name = name,
        dueDate = dueDate, repeat = repeat,
        sortOrder = sortOrder, hidden = hidden,
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
