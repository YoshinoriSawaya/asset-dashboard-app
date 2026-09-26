package com.yswy.assetdashboard.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun v2からv3で既存の行が残り新しい列が既定値になる() {
        helper.createDatabase(DB3, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO ingested_file (driveFileId, name, modifiedTime, md5Checksum, ingestedAt) " +
                    "VALUES ('id-1', 'meisai.csv', '2026-09-25T00:00:00Z', NULL, 1)",
            )
            v2.execSQL(
                "INSERT INTO item (id, type, name, metricKey, targetYen, dueDate, repeat, sortOrder, hidden) " +
                    "VALUES ('g1', 'GOAL', '車', NULL, 1000, NULL, NULL, 0, 0)",
            )
            v2.execSQL(
                "INSERT INTO bank_transaction (dedupKey, date, description, withdrawal, deposit, balance, memo, label, sourceFileId) " +
                    "VALUES ('k', '2026-09-01', 'x', 100, NULL, NULL, NULL, NULL, 'f')",
            )
        }
        helper.runMigrationsAndValidate(DB3, 3, true).use { v3 ->
            v3.query("SELECT COUNT(*) FROM ingested_file").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            v3.query("SELECT autoAverageMonths, autoCoverMonths FROM item").use { c ->
                c.moveToFirst(); assertTrue(c.isNull(0) && c.isNull(1))
            }
            v3.query("SELECT excludedFromSpending FROM bank_transaction").use { c ->
                c.moveToFirst(); assertEquals(0, c.getInt(0))
            }
        }
    }

    @Test
    fun v3からv4で目標が残り毎年の枠の印が既定値になる() {
        helper.createDatabase(DB4, 3).use { v3 ->
            v3.execSQL(
                "INSERT INTO item (id, type, name, metricKey, targetYen, dueDate, repeat, sortOrder, hidden, autoAverageMonths, autoCoverMonths) " +
                    "VALUES ('g1', 'GOAL', '車', NULL, 1000, NULL, NULL, 0, 0, NULL, NULL)",
            )
        }
        helper.runMigrationsAndValidate(DB4, 4, true).use { v4 ->
            v4.query("SELECT name, resetsYearly FROM item").use { c ->
                c.moveToFirst(); assertEquals("車", c.getString(0)); assertEquals(0, c.getInt(1))
            }
        }
    }

    @Test
    fun v4からv5で目標が残り積み増しの時期が空になる() {
        helper.createDatabase(DB5, 4).use { v4 ->
            v4.execSQL(
                "INSERT INTO item (id, type, name, metricKey, targetYen, dueDate, repeat, sortOrder, hidden, autoAverageMonths, autoCoverMonths, resetsYearly) " +
                    "VALUES ('g1', 'GOAL', '車', NULL, 1000, '2030-04-01', NULL, 0, 0, NULL, NULL, 0)",
            )
        }
        helper.runMigrationsAndValidate(DB5, 5, true).use { v5 ->
            v5.query("SELECT name, rampUpMonths FROM item").use { c ->
                c.moveToFirst(); assertEquals("車", c.getString(0)); assertTrue(c.isNull(1))
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
        const val DB3 = "migration-test-3.db"
        const val DB4 = "migration-test-4.db"
        const val DB5 = "migration-test-5.db"
    }
}
