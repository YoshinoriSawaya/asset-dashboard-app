package com.yswy.assetdashboard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 表示用のローカルキャッシュ。
 *
 * Driveが正のデータソースなので、このDBは消えてもDriveから作り直せる
 * ことを常に保つ(唯一の例外が[IngestedFile]だが、これも失われたら
 * もう一度取り込むだけで済む)。
 *
 * E02でMetric/Reminder/Goalのテーブルを足していく。
 */
@Database(
    entities = [IngestedFile::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun ingestedFileDao(): IngestedFileDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "asset-dashboard.db",
                )
                    // キャッシュなので、マイグレーションを書くのが面倒な段階では
                    // 作り直してDriveから入れ直すほうが安全で速い。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
