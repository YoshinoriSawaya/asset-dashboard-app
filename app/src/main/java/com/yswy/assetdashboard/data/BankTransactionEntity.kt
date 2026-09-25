package com.yswy.assetdashboard.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.Deduplication
import java.time.LocalDate

/**
 * いつ・いくら**動いた**か。[BankTransaction]のキャッシュ。
 *
 * 主キーを重複検出のキー([Deduplication.keyOf])にしてある。
 * 同じ取引を2回入れようとしてもDBの制約で1件になるので、
 * 取り込みの側で既存キーを集めて突き合わせる必要が無い。
 */
@Entity(tableName = "bank_transaction", indices = [Index("date")])
data class BankTransactionEntity(
    @PrimaryKey val dedupKey: String,
    val date: LocalDate,
    val description: String,
    val withdrawal: Long?,
    val deposit: Long?,
    val balance: Long?,
    val memo: String?,
    val label: String?,
    /** どのCSVから来たか(DriveのファイルID)。backupのJSONと突き合わせられる。 */
    val sourceFileId: String,
) {
    companion object {
        fun from(transaction: BankTransaction, sourceFileId: String) = BankTransactionEntity(
            dedupKey = Deduplication.keyOf(transaction),
            date = transaction.date,
            description = transaction.description,
            withdrawal = transaction.withdrawal,
            deposit = transaction.deposit,
            balance = transaction.balance,
            memo = transaction.memo,
            label = transaction.label,
            sourceFileId = sourceFileId,
        )
    }
}

@Dao
interface BankTransactionDao {

    /**
     * 書き込む。同じキーが既にあれば**何もしない(先勝ち)**。
     * 取引は「起きた事実」なので、最初に見たものを残す
     * ([Deduplication.dedupeTransactions]と同じ判断)。
     *
     * @return 行ごとのrowid。無視された行は -1
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<BankTransactionEntity>): List<Long>

    @Query("SELECT * FROM bank_transaction ORDER BY date")
    suspend fun all(): List<BankTransactionEntity>

    /**
     * 期間の明細。両端を含む。
     *
     * 日付はISO文字列で持っているので、`LocalDate.MIN/MAX` を渡すと
     * `+999999999-12-31` のような文字列になり、並びが崩れて何も返らない。
     * 全件が欲しいときは[all]を使う。
     */
    @Query("SELECT * FROM bank_transaction WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: LocalDate, to: LocalDate): List<BankTransactionEntity>

    /** いちばん新しい取引の日付。同期の要否に使う([SyncPolicy])。 */
    @Query("SELECT MAX(date) FROM bank_transaction")
    suspend fun latestDate(): LocalDate?

    @Query("SELECT COUNT(*) FROM bank_transaction")
    suspend fun count(): Int

    @Query("DELETE FROM bank_transaction WHERE dedupKey = :dedupKey")
    suspend fun delete(dedupKey: String)

    @Query("DELETE FROM bank_transaction")
    suspend fun deleteAll()
}
