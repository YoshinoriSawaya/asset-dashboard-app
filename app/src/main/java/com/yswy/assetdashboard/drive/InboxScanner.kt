package com.yswy.assetdashboard.drive

import com.yswy.assetdashboard.data.IngestedFileDao

/**
 * inboxの中身を見て、まだ取り込んでいないファイルを選り分ける。
 *
 * 判定は「取り込み済み記録に無い」か「記録はあるがmodifiedTimeが違う」。
 * 後者は、同じファイルをDrive上で上書き更新した場合(idは変わらず
 * modifiedTimeだけ進む)を拾うため。ファイル名は判定に使わない
 * ——「ファイル名も気にせず放り込む」のが前提なので、名前は当てにならない。
 */
object InboxScanner {

    suspend fun scan(api: DriveApi, folders: AppFolders, dao: IngestedFileDao): Result {
        val files = api.listFiles(folders.inbox)

        val pending = mutableListOf<DriveApi.DriveFile>()
        val alreadyIngested = mutableListOf<DriveApi.DriveFile>()

        for (file in files) {
            val record = dao.findById(file.id)
            when {
                record == null -> pending += file
                record.modifiedTime != file.modifiedTime -> pending += file
                else -> alreadyIngested += file
            }
        }

        return Result(pending = pending, alreadyIngested = alreadyIngested)
    }

    /**
     * @param pending これから取り込むファイル
     * @param alreadyIngested 取り込み済みなのにinboxに残っているファイル。
     *   本来はE01-07でprocessedへ移動しているはずなので、ここに出るのは
     *   移動に失敗した形跡。取り込み自体はやり直さない。
     */
    data class Result(
        val pending: List<DriveApi.DriveFile>,
        val alreadyIngested: List<DriveApi.DriveFile>,
    ) {
        val hasWork: Boolean get() = pending.isNotEmpty()
    }
}
