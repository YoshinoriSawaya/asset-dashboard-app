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
    /** 純資産に数える系列か(E10-01)。Metricだけが使う。 */
    @ColumnInfo(defaultValue = "0")
    val inNetWorth: Boolean = false,
    /** 毎年のリマインダーを何年ごとにするか(E07-15)。nullは1年ごと。 */
    val repeatYears: Int? = null,
    /** リマインダーの見込み額を積み立てる目標のid(E07-15)。 */
    val fundId: String? = null,
    /** 大型出費の積立の目標(E07-15)。何年分の予定をならして月々の額を出すか。 */
    val sinkingYears: Int? = null,
    /** 大型出費の積立で使う物価上昇率(E07-15)。年率の1万分率(2% = 200)。 */
    val growthRateBp: Int? = null,
    /** 系列のまとめ先(E07-18)。親の系列のmetricKey。Metricだけが使う。 */
    val groupKey: String? = null,
    /** 目標が足りないとき、何か月で埋めるか(E07-19)。Goalだけが使う。 */
    val refillMonths: Int? = null,
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

/**
 * 大型出費の積立(E07-15)。家電・車のように、何年かごとに来る出費に向けて貯める。
 * 目標額は、この目標を積立先にしたリマインダーの、向こう1年の見込み額の合計。
 */
data class SinkingFund(
    /** 何年分の予定をならして、月々の積立額を出すか。 */
    val horizonYears: Int = 5,
    /** 物価上昇率。年率の1万分率(2% = 200)。先の回ほど見込み額を増やす。 */
    val growthRateBp: Int = 0,
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
        /**
         * 純資産に数えるか(E10-01)。資産推移の「合計」とその内訳のように重なる系列があるので、
         * 全部を足さず、人が選んだ系列だけを足す。一覧から隠していても数える。
         */
        val inNetWorth: Boolean = false,
        /**
         * まとめ先の系列(E07-18)。親の系列のmetricKey。トップでは親の1行にまとめ、
         * 親の詳細に内訳として並べる。親が一覧に無ければ(隠している・消えた)、自分の行を出す。
         */
        val groupKey: String? = null,
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
        /** 目標額を大型出費の予定から出す(E07-15)。決まった額・生活費から出す目標ならnull。 */
        val sinking: SinkingFund? = null,
        /**
         * 足りないとき何か月で埋めるか(E07-19)。決めておくと、月々の額を1つに決めて出す
         * (生活防衛資金を取り崩したら、N か月で戻す、など)。
         */
        val refillMonths: Int? = null,
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
        /** [Repeat.YEARLY]のとき何年ごとか(E07-15)。車検は2、洗濯機は10など。 */
        val repeatYears: Int = 1,
        /** 見込み額を積み立てる目標([Item.Goal.sinking]のある目標)のid(E07-15)。 */
        val fundId: String? = null,
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
        Item.Metric(id, name, it, sortOrder, hidden, inNetWorth, groupKey)
    }
    ItemType.GOAL -> {
        val auto = if (autoAverageMonths != null && autoCoverMonths != null) {
            AutoTarget(autoAverageMonths, autoCoverMonths, autoFloorMonths)
        } else {
            null
        }
        val sinking = sinkingYears?.let { SinkingFund(it, growthRateBp ?: 0) }
        // 目標額の決め方がどれも無いGoalは読めない
        if (targetYen == null && auto == null && sinking == null) null
        else Item.Goal(id, name, targetYen, metricKey, dueDate, sortOrder, hidden, auto, resetsYearly, rampUpMonths, sinking, refillMonths)
    }
    ItemType.REMINDER -> dueDate?.let {
        Item.Reminder(
            id, name, it, repeat ?: Repeat.NONE, sortOrder, hidden,
            amountYen = targetYen, repeatYears = repeatYears ?: 1, fundId = fundId,
        )
    }
}

fun Item.toEntity(): ItemEntity = when (this) {
    is Item.Metric -> ItemEntity(
        id = id, type = ItemType.METRIC, name = name,
        metricKey = metricKey,
        sortOrder = sortOrder, hidden = hidden,
        inNetWorth = inNetWorth,
        groupKey = groupKey,
    )
    is Item.Goal -> ItemEntity(
        id = id, type = ItemType.GOAL, name = name,
        metricKey = metricKey, targetYen = targetYen, dueDate = dueDate,
        sortOrder = sortOrder, hidden = hidden,
        autoAverageMonths = autoTarget?.averageMonths, autoCoverMonths = autoTarget?.coverMonths,
        autoFloorMonths = autoTarget?.floorMonths,
        resetsYearly = resetsYearly,
        rampUpMonths = rampUpMonths,
        sinkingYears = sinking?.horizonYears,
        growthRateBp = sinking?.growthRateBp,
        refillMonths = refillMonths,
    )
    is Item.Reminder -> ItemEntity(
        id = id, type = ItemType.REMINDER, name = name,
        dueDate = dueDate, repeat = repeat,
        sortOrder = sortOrder, hidden = hidden,
        targetYen = amountYen,
        repeatYears = repeatYears.takeIf { it > 1 },
        fundId = fundId,
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
