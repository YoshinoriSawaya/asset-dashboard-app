package com.yswy.assetdashboard.drive

import android.util.Log
import com.yswy.assetdashboard.csv.BankTransaction
import com.yswy.assetdashboard.csv.MetricPoint
import com.yswy.assetdashboard.csv.ParsedData
import org.json.JSONArray
import org.json.JSONObject

/**
 * 整形済みのデータをbackupフォルダにJSONで書く。
 *
 * ## なぜ必要か
 * processedにあるのは**元のCSV**で、銀行ごとにバラバラの形をしている。
 * ローカルDBを失ったときにそこから作り直すには、また全部パースし直す
 * ことになる。パーサーを直したら過去の結果が変わる、ということも起きる。
 *
 * backupには**パース済みの正規化された形**を置く。これがあれば
 * 「Driveから再構築できる」が本当に成立する(CLAUDE.mdの大原則)。
 *
 * ## ファイルの分け方
 * 取り込んだ元ファイルごとに1つのJSONを作る
 * (`<元のファイル名>.<元ファイルのDriveのID>.json`)。全部を1ファイルにまとめると、
 * 1回の書き込み失敗で全履歴を失う。元ファイル単位なら、
 * 壊れても壊れたぶんだけで済む。
 *
 * 同じ元ファイル(同じID)なら中身を差し替える。processedからinboxへ戻して
 * 取り込み直しても、DriveのIDは変わらないので、ファイルは増えない。
 *
 * ## 名前にIDを入れる(E01-16)
 * 以前は `<元のファイル名>.json` だった。銀行やカードから落としたCSVは
 * `明細 (1).csv` のように**別のファイルでも同じ名前**になりやすく、後から取り込んだ
 * ほうが前のbackupを上書きして、前のファイルの中身がbackupから消えていた。
 * 古い名前のbackupは、同じ元ファイルのものなら新しい名前で書いたあとゴミ箱に移す。
 * 別の元ファイルのものなら残す(それしか残っていない中身かもしれない)。
 */
object BackupWriter {

    private const val TAG = "BackupWriter"

    /** JSONの形を変えたときに、読む側が気づけるようにする。 */
    const val FORMAT_VERSION = 1

    /**
     * 1ファイルぶんのパース結果をbackupに書く。
     *
     * 書けなくても取り込み自体は成立しているので例外は投げない。
     * @return 書けたらtrue
     */
    suspend fun write(
        api: DriveApi,
        folders: AppFolders,
        sourceFileName: String,
        sourceFileId: String,
        adapterId: String,
        data: ParsedData,
    ): Boolean {
        val json = render(sourceFileName, sourceFileId, adapterId, data)

        val written = try {
            api.putTextFile(
                name = nameFor(sourceFileName, sourceFileId),
                parentId = folders.backup,
                content = json,
                mimeType = "application/json",
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "backupに書けなかった: $sourceFileName", e)
            false
        }
        if (written) trashLegacy(api, folders, sourceFileName, sourceFileId)
        return written
    }

    /** backupのファイル名。同じ名前の別ファイルとぶつからないよう、元ファイルのIDを入れる。 */
    fun nameFor(sourceFileName: String, sourceFileId: String): String = "$sourceFileName.$sourceFileId.json"

    /**
     * 古い名前(`<元のファイル名>.json`)のbackupが同じ元ファイルのものなら、ゴミ箱に移す。
     * 残っていても読む側([CacheSync])が元ファイルごとに新しいほうだけを使うので、
     * 失敗しても取り込みは止めない。
     */
    private suspend fun trashLegacy(api: DriveApi, folders: AppFolders, sourceFileName: String, sourceFileId: String) {
        try {
            val legacyId = api.findFile("$sourceFileName.json", folders.backup) ?: return
            val legacy = BackupReader.parse(String(api.download(legacyId), Charsets.UTF_8))
            if (legacy?.sourceFileId == sourceFileId) api.trashFile(legacyId)
        } catch (e: Exception) {
            Log.w(TAG, "古い名前のbackupを片付けられなかった: $sourceFileName", e)
        }
    }

    /** JSONの組み立て。テストから直接確かめられるよう分けてある。 */
    fun render(
        sourceFileName: String,
        sourceFileId: String,
        adapterId: String,
        data: ParsedData,
    ): String {
        val root = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("sourceFileName", sourceFileName)
            .put("sourceFileId", sourceFileId)
            .put("adapterId", adapterId)

        when (data) {
            is ParsedData.Transactions -> {
                root.put("kind", "transactions")
                data.statementTotal?.let { root.put("statementTotal", it) }
                root.put("transactions", JSONArray().apply {
                    data.rows.forEach { put(it.toJson()) }
                })
            }

            is ParsedData.Metrics -> {
                root.put("kind", "metrics")
                root.put("metrics", JSONArray().apply {
                    data.points.forEach { put(it.toJson()) }
                })
            }
        }

        return root.toString(2)
    }

    private fun BankTransaction.toJson(): JSONObject = JSONObject()
        .put("date", date.toString())
        .put("description", description)
        .putOrNull("withdrawal", withdrawal)
        .putOrNull("deposit", deposit)
        .putOrNull("balance", balance)
        .putOrNull("memo", memo)
        .putOrNull("label", label)

    private fun MetricPoint.toJson(): JSONObject = JSONObject()
        .put("metricKey", metricKey)
        .put("date", date.toString())
        .put("valueYen", valueYen)

    /**
     * nullの項目はキーごと出さない。
     * `put(key, null)` はJSONObjectではキー削除と同義なので明示的に書く。
     */
    private fun JSONObject.putOrNull(key: String, value: Any?): JSONObject =
        if (value == null) this else put(key, value)
}
