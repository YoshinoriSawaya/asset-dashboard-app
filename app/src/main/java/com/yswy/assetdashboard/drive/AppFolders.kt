package com.yswy.assetdashboard.drive

/**
 * アプリが使うDrive上のフォルダ群のID。
 *
 * **IDはキャッシュしない。** ローカルに持つとDrive側でフォルダを
 * 移動・削除されたときに古いIDを掴み続ける。毎回引き直すほうが
 * 「Driveが正のデータソース」という原則に素直で、自己修復もする。
 * 起動ごとに数回のAPI呼び出しが増えるだけなので、遅くなったら
 * そのときキャッシュを足せばよい。
 */
data class AppFolders(
    val root: String,
    val inbox: String,
    val processed: String,
    val backup: String,
    val corrections: String,
    /** 項目の定義(E02-01)。correctionsと同じく人が入力したもので、作り直せない。 */
    val settings: String,
    val logs: String,
)

/**
 * `/資産アプリ/` 配下のフォルダ構成を作る・確認する。
 *
 * 人間が触るのは inbox だけ。残りはアプリが管理する。
 */
object DriveFolderSetup {

    const val ROOT_NAME = "資産アプリ"

    const val INBOX = "inbox"
    const val PROCESSED = "processed"
    const val BACKUP = "backup"
    const val CORRECTIONS = "corrections"
    const val SETTINGS = "settings"
    const val LOGS = "logs"

    /** Driveのマイドライブ直下を指す予約語。 */
    private const val DRIVE_ROOT = "root"

    /**
     * 6つのフォルダが揃った状態にして、そのIDを返す。
     * 既にあるものはそのまま使い、足りないものだけ作る。
     *
     * @return フォルダIDと、今回新規作成したフォルダ名の一覧
     */
    suspend fun ensure(api: DriveApi): Outcome {
        val created = mutableListOf<String>()

        val root = api.ensureFolder(ROOT_NAME, DRIVE_ROOT)
        if (root.created) created += ROOT_NAME

        suspend fun child(name: String): String {
            val folder = api.ensureFolder(name, root.id)
            if (folder.created) created += name
            return folder.id
        }

        val folders = AppFolders(
            root = root.id,
            inbox = child(INBOX),
            processed = child(PROCESSED),
            backup = child(BACKUP),
            corrections = child(CORRECTIONS),
            settings = child(SETTINGS),
            logs = child(LOGS),
        )
        return Outcome(folders, created)
    }

    data class Outcome(val folders: AppFolders, val created: List<String>)
}
