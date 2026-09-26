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

    @Test
    fun v5からv6で目標が残り下限が空になる() {
        helper.createDatabase(DB6, 5).use { v5 ->
            v5.execSQL(
                "INSERT INTO item (id, type, name, metricKey, targetYen, dueDate, repeat, sortOrder, hidden, autoAverageMonths, autoCoverMonths, resetsYearly, rampUpMonths) " +
                    "VALUES ('f1', 'GOAL', '生活防衛資金', NULL, NULL, NULL, NULL, 0, 0, 6, 6, 0, NULL)",
            )
        }
        helper.runMigrationsAndValidate(DB6, 6, true).use { v6 ->
            v6.query("SELECT name, autoCoverMonths, autoFloorMonths FROM item").use { c ->
                c.moveToFirst(); assertEquals("生活防衛資金", c.getString(0)); assertEquals(6, c.getInt(1)); assertTrue(c.isNull(2))
            }
        }
    }

    @Test
    fun v6からv7で系列が残り純資産に数えない() {
        helper.createDatabase(DB7, 6).use { v6 ->
            v6.execSQL(
                "INSERT INTO item (id, type, name, metricKey, targetYen, dueDate, repeat, sortOrder, hidden, autoAverageMonths, autoCoverMonths, resetsYearly, rampUpMonths, autoFloorMonths) " +
                    "VALUES ('metric:合計', 'METRIC', '合計', '合計', NULL, NULL, NULL, 0, 1, NULL, NULL, 0, NULL, NULL)",
            )
        }
        helper.runMigrationsAndValidate(DB7, 7, true).use { v7 ->
            v7.query("SELECT name, hidden, inNetWorth FROM item").use { c ->
                c.moveToFirst(); assertEquals("合計", c.getString(0)); assertEquals(1, c.getInt(1)); assertEquals(0, c.getInt(2))
            }
        }
    }

    @Test
    fun v7からv8でリマインダーが残り何年ごとと積立先が空になる() {
        helper.createDatabase(DB8, 7).use { v7 ->
            v7.execSQL(
                "INSERT INTO item (id, type, name, metricKey, targetYen, dueDate, repeat, sortOrder, hidden, autoAverageMonths, autoCoverMonths, resetsYearly, rampUpMonths, autoFloorMonths, inNetWorth) " +
                    "VALUES ('r1', 'REMINDER', '車検', NULL, 1000, '2027-03-01', 'YEARLY', -1, 0, NULL, NULL, 0, NULL, NULL, 0)",
            )
        }
        helper.runMigrationsAndValidate(DB8, 8, true).use { v8 ->
            v8.query("SELECT name, targetYen, repeatYears, fundId, sinkingYears, growthRateBp FROM item").use { c ->
                c.moveToFirst(); assertEquals("車検", c.getString(0)); assertEquals(1000, c.getInt(1))
                assertTrue(c.isNull(2)); assertTrue(c.isNull(3)); assertTrue(c.isNull(4)); assertTrue(c.isNull(5))
            }
        }
    }

    @Test
    fun v8からv9で系列が残りまとめ先が空になる() {
        helper.createDatabase(DB9, 8).use { v8 ->
            v8.execSQL(
                "INSERT INTO item (id, type, name, metricKey, sortOrder, hidden, resetsYearly, inNetWorth) " +
                    "VALUES ('metric:NISA', 'METRIC', 'NISA', 'NISA', 0, 0, 0, 1)",
            )
        }
        helper.runMigrationsAndValidate(DB9, 9, true).use { v9 ->
            v9.query("SELECT name, inNetWorth, groupKey FROM item").use { c ->
                c.moveToFirst(); assertEquals("NISA", c.getString(0)); assertEquals(1, c.getInt(1)); assertTrue(c.isNull(2))
            }
        }
    }

    @Test
    fun v9からv10で目標が残り埋める期間が空になる() {
        helper.createDatabase(DB10, 9).use { v9 ->
            v9.execSQL(
                "INSERT INTO item (id, type, name, targetYen, sortOrder, hidden, resetsYearly, inNetWorth) " +
                    "VALUES ('g1', 'GOAL', '予備費', 1000, -1, 0, 0, 0)",
            )
        }
        helper.runMigrationsAndValidate(DB10, 10, true).use { v10 ->
            v10.query("SELECT name, targetYen, refillMonths FROM item").use { c ->
                c.moveToFirst(); assertEquals("予備費", c.getString(0)); assertEquals(1000, c.getInt(1)); assertTrue(c.isNull(2))
            }
        }
    }

    @Test
    fun v10からv11で明細が残りカテゴリが空になる() {
        helper.createDatabase(DB11, 10).use { v10 ->
            v10.execSQL(
                "INSERT INTO bank_transaction (dedupKey, date, description, withdrawal, deposit, balance, memo, label, sourceFileId, excludedFromSpending) " +
                    "VALUES ('k1', '2026-09-01', '電気代', 1000, NULL, NULL, NULL, NULL, 'f', 0)",
            )
        }
        helper.runMigrationsAndValidate(DB11, 11, true).use { v11 ->
            v11.query("SELECT description, category, categoryKind, cardPayment FROM bank_transaction").use { c ->
                c.moveToFirst(); assertEquals("電気代", c.getString(0)); assertTrue(c.isNull(1)); assertTrue(c.isNull(2)); assertEquals(0, c.getInt(3))
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
        const val DB6 = "migration-test-6.db"
        const val DB7 = "migration-test-7.db"
        const val DB8 = "migration-test-8.db"
        const val DB9 = "migration-test-9.db"
        const val DB10 = "migration-test-10.db"
        const val DB11 = "migration-test-11.db"
    }
}
