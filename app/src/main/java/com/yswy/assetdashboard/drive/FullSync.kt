package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.data.AppDatabase
import com.yswy.assetdashboard.drive.CacheSync.summary

/**
 * 同期ボタン・開いたときの自動同期(E02-04)の中身。
 *
 * フォルダを用意 → inboxを一周(E01)→ Driveの中身でキャッシュを作り直す(E02-03)。
 * 認可・オフラインの扱いは呼ぶ側で[DriveSession]に包む。
 */
object FullSync {

    data class Result(
        val createdFolders: List<String>,
        val inbox: InboxSync.Report,
        val cache: CacheSync.Outcome,
    ) {
        /** 同期結果の詳細。E03-05で要約表示にするまでは、これをそのまま画面に出す。 */
        fun describe(): String = buildString {
            if (createdFolders.isNotEmpty()) append("フォルダ作成: ${createdFolders.joinToString(", ")}\n")
            append(inbox.summary())
            inbox.entries.forEach { entry ->
                append("\n・${entry.fileName}")
                append("\n　 ${entry.status.label}: ${entry.detail}")
            }
            append("\n").append(cache.summary())
        }
    }

    suspend fun run(api: DriveApi, db: AppDatabase): Result {
        val setup = DriveFolderSetup.ensure(api)
        val inbox = InboxSync.run(api, setup.folders, db.ingestedFileDao())

        // 取り込みで増えたbackupも含めて作り直す。取り込みが全部失敗していても、
        // 既存のbackupからキャッシュは作れるので必ず走らせる。
        val cache = CacheSync.rebuild(api, setup.folders, db)

        return Result(setup.created, inbox, cache)
    }
}
