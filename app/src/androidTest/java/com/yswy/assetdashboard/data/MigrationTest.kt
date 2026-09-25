package com.yswy.assetdashboard.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 取り込み済みの記録([IngestedFile])はDriveから作り直せない唯一のテーブル。
 * バージョンを上げても消えないことを確かめる。
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun v1からv2でingested_fileが残る() {
        helper.createDatabase(DB, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO ingested_file (driveFileId, name, modifiedTime, md5Checksum, ingestedAt) " +
                    "VALUES ('id-1', 'meisai.csv', '2026-09-25T00:00:00Z', NULL, 1)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).use { v2 ->
            v2.query("SELECT name FROM ingested_file").use { c ->
                assertEquals(1, c.count)
                c.moveToFirst()
                assertEquals("meisai.csv", c.getString(0))
            }
            // 新しいテーブルができていて、空である
            for (table in listOf("item", "metric_point", "bank_transaction")) {
                v2.query("SELECT COUNT(*) FROM $table").use { c ->
                    c.moveToFirst()
                    assertEquals(0, c.getInt(0))
                }
            }
        }
    }

    /**
     * アプリと同じ設定([AppDatabase.build])で開いても消えないこと。
     *
     * 上のテストはマイグレーション単体の確認。こちらは本番のビルダー設定
     * (`fallbackToDestructiveMigration`と併用している)で、作り直しではなく
     * マイグレーションが選ばれることを確かめる。
     */
    @Test
    fun アプリの設定で開いてもingested_fileが残る() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(APP_DB)
        helper.createDatabase(APP_DB, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO ingested_file (driveFileId, name, modifiedTime, md5Checksum, ingestedAt) " +
                    "VALUES ('id-1', 'meisai.csv', '2026-09-25T00:00:00Z', NULL, 1)",
            )
        }

        val db = AppDatabase.build(context, APP_DB)
        try {
            val files = runBlocking { db.ingestedFileDao().getAll() }
            assertEquals(listOf("meisai.csv"), files.map { it.name })
            assertEquals(0, runBlocking { db.itemDao().getAll() }.size)
        } finally {
            db.close()
            context.deleteDatabase(APP_DB)
        }
    }

    private companion object {
        /** 本物のDB(asset-dashboard.db)は消さないよう別の名前にする。 */
        const val APP_DB = "app-config-test.db"

        const val DB = "migration-test.db"
    }
}
