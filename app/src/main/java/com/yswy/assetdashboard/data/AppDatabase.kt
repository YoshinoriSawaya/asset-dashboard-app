package com.yswy.assetdashboard.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 表示用のローカルキャッシュ。
 *
 * Driveが正のデータソースなので、このDBは消えてもDriveから作り直せる
 * ことを常に保つ(唯一の例外が[IngestedFile]だが、これも失われたら
 * もう一度取り込むだけで済む)。
 *
 * テーブルの分け方(値と項目を分ける、項目は1テーブル)はE02-01。
 *
 * ## マイグレーション
 * 基本は[fallbackToDestructiveMigration]で作り直してDriveから入れ直す。
 * ただしそれだと[IngestedFile]まで消える。テーブルを足すだけの変更は
 * [AutoMigration]で済むので、書けるときは書いて記録を残す。
 */
@Database(
    entities = [
        IngestedFile::class,
        ItemEntity::class,
        MetricPointEntity::class,
        BankTransactionEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // E02-02: item / metric_point / bank_transaction を追加
        AutoMigration(from = 1, to = 2),
        // E07-06: 目標額の自動計算の列と、生活費から除く印を追加
        AutoMigration(from = 2, to = 3),
        // E07-09: 毎年リセットする枠の印を追加
        AutoMigration(from = 3, to = 4),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingestedFileDao(): IngestedFileDao
    abstract fun itemDao(): ItemDao
    abstract fun metricPointDao(): MetricPointDao
    abstract fun bankTransactionDao(): BankTransactionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private const val NAME = "asset-dashboard.db"

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context, NAME).also { instance = it }
            }

        /**
         * アプリと同じ設定でDBを開く。テストが本物のDB(`asset-dashboard.db`)を
         * 消さずに、同じ設定を別の名前で試せるように分けてある。
         */
        internal fun build(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
                // キャッシュなので、マイグレーションを書くのが面倒な段階では
                // 作り直してDriveから入れ直すほうが安全で速い。
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
