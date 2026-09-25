package com.yswy.assetdashboard.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yswy.assetdashboard.csv.MetricPoint
import java.time.LocalDate

/** その点がどこから来たか。補正を重ねた後でも「手で直した」と示せるように残す。 */
enum class MetricOrigin { CSV, OVERRIDE, MANUAL }

/**
 * いつ・いくら**だった**か。[MetricPoint]のキャッシュ。
 *
 * 主キーは「項目 + 日付」。重複検出(E01-11)と補正(E01-10)のキーに
 * 揃えてある。同じ日・同じ項目の値は1つしかない。
 */
@Entity(tableName = "metric_point", primaryKeys = ["metricKey", "date"])
data class MetricPointEntity(
    val metricKey: String,
    val date: LocalDate,
    val valueYen: Long,
    val origin: MetricOrigin,
) {
    companion object {
        fun from(point: MetricPoint, origin: MetricOrigin = MetricOrigin.CSV) =
            MetricPointEntity(point.metricKey, point.date, point.valueYen, origin)
    }
}

@Dao
interface MetricPointDao {

    /**
     * 書き込む。同じ項目・同じ日付があれば**置き換える(後勝ち)**。
     * Metricは時点の残高なので、後から取り込んだほうが正しい
     * ([com.yswy.assetdashboard.csv.Deduplication.dedupeMetrics]と同じ判断)。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(points: List<MetricPointEntity>)

    @Query("SELECT * FROM metric_point WHERE metricKey = :metricKey ORDER BY date")
    suspend fun series(metricKey: String): List<MetricPointEntity>

    @Query("SELECT * FROM metric_point WHERE metricKey = :metricKey ORDER BY date DESC LIMIT 1")
    suspend fun latest(metricKey: String): MetricPointEntity?

    @Query("SELECT * FROM metric_point WHERE metricKey = :metricKey AND date = :date")
    suspend fun find(metricKey: String, date: LocalDate): MetricPointEntity?

    /** いちばん新しい点の日付。同期の要否に使う([SyncPolicy])。 */
    @Query("SELECT MAX(date) FROM metric_point")
    suspend fun latestDate(): LocalDate?

    /** 現れたことのある項目。Metric項目を自動で生やすのに使う(E02-01)。 */
    @Query("SELECT DISTINCT metricKey FROM metric_point ORDER BY metricKey")
    suspend fun keys(): List<String>

    @Query("DELETE FROM metric_point WHERE metricKey = :metricKey AND date = :date")
    suspend fun delete(metricKey: String, date: LocalDate)

    @Query("DELETE FROM metric_point")
    suspend fun deleteAll()
}
