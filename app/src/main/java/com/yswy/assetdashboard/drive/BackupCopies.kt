package com.yswy.assetdashboard.drive

import java.io.File
import java.security.MessageDigest

/**
 * 落としたbackupの写し(E02-08)。アプリの中のフォルダに、DriveのファイルIDを名前にして置く。
 *
 * 使うかどうかは**中身のmd5がDriveの一覧のmd5と同じか**で決める。同じなら落とし直さない。
 * 何を使うかは毎回Driveの一覧で決めるので、Driveが正なのは変わらない。写しが消えても壊れても、落とし直すだけ。
 * 中身は取り込んだ明細なので、Room DBと同じくアプリの中にだけ置く。
 */
class BackupCopies(
    private val dir: File,
    /**
     * このアプリの入れ物の印(E02-09)。版と、入れた・更新した日時。入れ直すたびに変わるので、
     * 読み方を直したアプリを入れたら(版を上げ忘れたデバッグ版でも)次の同期で必ず作り直す。
     */
    val appStamp: String = "",
) {

    /** Driveと同じ中身の写しがあればそれ。無い・違う・読めない・Driveがmd5を返さなければnull(落とし直す)。 */
    fun read(file: DriveApi.DriveFile): ByteArray? {
        val expected = file.md5Checksum ?: return null
        val bytes = runCatching { copyOf(file.id).takeIf { it.isFile }?.readBytes() }.getOrNull() ?: return null
        return bytes.takeIf { md5(it).equals(expected, ignoreCase = true) }
    }

    /** 落としたものを写す。書けなくても同期は止めない(次も落とすだけ)。 */
    fun write(fileId: String, bytes: ByteArray) {
        runCatching {
            dir.mkdirs()
            // 途中で止まっても壊れた写しを残さないよう、書き終えてから名前を変える
            val tmp = File(dir, "$fileId.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(copyOf(fileId))) {
                copyOf(fileId).delete()
                tmp.renameTo(copyOf(fileId))
            }
        }
    }

    /** Driveの一覧に無いものの写しを消す(Driveで消した・古い名前を片付けたbackup)。書きかけも消す。 */
    fun keepOnly(fileIds: Set<String>) {
        dir.listFiles()?.forEach { f ->
            when {
                f.name.endsWith(".tmp") -> f.delete()
                f.name.endsWith(".json") && f.name.removeSuffix(".json") !in fileIds -> f.delete()
            }
        }
    }

    /**
     * 前に作り直したときの入力の組み合わせ(E02-09)と、そのときの指紋。無ければnull。
     * 組み合わせが同じなら、作り直しても同じ中身になるので飛ばせる。
     */
    fun lastRebuild(): Pair<String, String>? = runCatching {
        val lines = File(dir, REBUILD_FILE).readLines()
        lines[0] to lines[1]
    }.getOrNull()

    /** 作り直せたときだけ書く。書けなくても次は作り直すだけ。 */
    fun saveRebuild(key: String, fingerprint: String) {
        runCatching {
            dir.mkdirs()
            File(dir, REBUILD_FILE).writeText("$key\n$fingerprint\n")
        }
    }

    private fun copyOf(fileId: String) = File(dir, "$fileId.json")

    companion object {
        private const val REBUILD_FILE = "rebuild.txt"

        fun md5(bytes: ByteArray): String =
            MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
