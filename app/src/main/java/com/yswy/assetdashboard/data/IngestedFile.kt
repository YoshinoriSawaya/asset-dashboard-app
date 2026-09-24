package com.yswy.assetdashboard.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * 取り込みに成功したinboxファイルの記録。
 *
 * これは「Driveから再構築できないローカル状態」の数少ない例外。
 * フォルダIDと違ってDriveを見ても分からないので永続化する必要がある。
 * ただし失われても致命傷ではない(最悪もう一度取り込むだけで、
 * 重複はE01-11の重複検出で弾く)。
 *
 * **失敗したファイルはここに記録しない。** inboxに残してlogsに理由を書き、
 * 次回もう一度試す(CLAUDE.mdの「落ちるよりスキップしてログ」に沿う)。
 */
@Entity(tableName = "ingested_file")
data class IngestedFile(
    @PrimaryKey val driveFileId: String,
    val name: String,
    /** DriveのmodifiedTime(RFC3339)。同じidでも中身が差し替わったら変わる。 */
    val modifiedTime: String,
    val md5Checksum: String?,
    /** 取り込んだ時刻(epoch millis)。 */
    val ingestedAt: Long,
)

@Dao
interface IngestedFileDao {

    @Query("SELECT * FROM ingested_file")
    suspend fun getAll(): List<IngestedFile>

    @Query("SELECT * FROM ingested_file WHERE driveFileId = :driveFileId")
    suspend fun findById(driveFileId: String): IngestedFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: IngestedFile)

    @Query("SELECT COUNT(*) FROM ingested_file")
    suspend fun count(): Int
}
