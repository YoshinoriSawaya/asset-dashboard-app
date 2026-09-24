package com.yswy.assetdashboard.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Drive APIがエラーを返した、または応答を解釈できなかった。
 *
 * [httpCode] は分かる場合のみ。401はトークン失効なので、
 * 呼び出し側で再認可に回せるよう区別できるようにしてある。
 */
class DriveException(
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isUnauthorized: Boolean get() = httpCode == 401
}

/**
 * Drive REST APIの薄いラッパー。
 *
 * google-api-services-driveは依存が重くAndroidでの取り回しも悪いので、
 * 必要なエンドポイントだけOkHttpで直接叩く。
 * 失敗は[DriveException]で投げ、握り潰すかどうかは呼び出し側で決める。
 */
class DriveApi(private val accessToken: String) {

    /** 認可されたアカウント情報。疎通確認用。 */
    suspend fun about(): AboutInfo {
        val url = "$BASE_URL/about".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user(displayName,emailAddress)")
            .build()
        val json = get(url)
        val user = json.optJSONObject("user")
        return AboutInfo(
            displayName = user?.optString("displayName").orEmpty(),
            email = user?.optString("emailAddress").orEmpty(),
        )
    }

    /**
     * [parentId] 直下から名前が [name] のフォルダを探す。
     * 見つからなければnull。同名が複数あれば最初の1つ。
     */
    suspend fun findFolder(name: String, parentId: String): String? {
        val query = buildString {
            append("name = '").append(escapeForQuery(name)).append("'")
            append(" and mimeType = '").append(FOLDER_MIME).append("'")
            append(" and '").append(escapeForQuery(parentId)).append("' in parents")
            append(" and trashed = false")
        }
        val url = "$BASE_URL/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("fields", "files(id,name)")
            .addQueryParameter("pageSize", "10")
            .build()

        val files = get(url).optJSONArray("files") ?: JSONArray()
        if (files.length() == 0) return null
        return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    /**
     * [parentId] 直下のファイル(フォルダ以外)を全件返す。
     *
     * Driveの一覧APIはページングするので、nextPageTokenが尽きるまで回す。
     * inboxに何百件も溜まる想定はないが、打ち切ると「取り込まれない
     * ファイルがある」という分かりにくい不具合になるので全部取る。
     */
    suspend fun listFiles(parentId: String): List<DriveFile> {
        val query = buildString {
            append("'").append(escapeForQuery(parentId)).append("' in parents")
            append(" and mimeType != '").append(FOLDER_MIME).append("'")
            append(" and trashed = false")
        }

        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val builder = "$BASE_URL/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter(
                    "fields",
                    "nextPageToken,files(id,name,mimeType,modifiedTime,size,md5Checksum)",
                )
                .addQueryParameter("pageSize", "100")
                .addQueryParameter("orderBy", "modifiedTime")
            pageToken?.let { builder.addQueryParameter("pageToken", it) }

            val json = get(builder.build())
            val array = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                files += DriveFile(
                    id = id,
                    name = item.optString("name"),
                    mimeType = item.optString("mimeType"),
                    modifiedTime = item.optString("modifiedTime"),
                    size = item.optString("size").toLongOrNull(),
                    md5Checksum = item.optString("md5Checksum").takeIf { it.isNotBlank() },
                )
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        return files
    }

    /** [parentId] 直下にフォルダを作り、そのIDを返す。 */
    suspend fun createFolder(name: String, parentId: String): String {
        val body = JSONObject()
            .put("name", name)
            .put("mimeType", FOLDER_MIME)
            .put("parents", JSONArray().put(parentId))

        val url = "$BASE_URL/files".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id")
            .build()

        val id = post(url, body).optString("id")
        if (id.isBlank()) throw DriveException("フォルダ作成の応答にidが無かった: $name")
        return id
    }

    /**
     * フォルダを探し、無ければ作る。
     * 返り値はIDと、新規作成したかどうか。
     */
    suspend fun ensureFolder(name: String, parentId: String): EnsuredFolder {
        findFolder(name, parentId)?.let { return EnsuredFolder(it, created = false) }
        return EnsuredFolder(createFolder(name, parentId), created = true)
    }

    private suspend fun get(url: HttpUrl): JSONObject = execute(
        Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build(),
    )

    private suspend fun post(url: HttpUrl, body: JSONObject): JSONObject = execute(
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build(),
    )

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        val raw = try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    throw DriveException(
                        "HTTP ${response.code}: ${text.take(300)}",
                        httpCode = response.code,
                    )
                }
                text
            }
        } catch (e: IOException) {
            throw DriveException("通信に失敗: ${e.message}", cause = e)
        }

        try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw DriveException("応答を解釈できなかった: ${raw.take(200)}", cause = e)
        }
    }

    data class AboutInfo(val displayName: String, val email: String)

    data class DriveFile(
        val id: String,
        val name: String,
        val mimeType: String,
        /** RFC3339。同じidでも中身が差し替わると変わる。 */
        val modifiedTime: String,
        val size: Long?,
        val md5Checksum: String?,
    )

    data class EnsuredFolder(val id: String, val created: Boolean)

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/drive/v3"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // OkHttpClientは使い回す前提のオブジェクトなので1つだけ持つ。
        private val client = OkHttpClient()

        /**
         * Driveのクエリ文字列はシングルクォート括り。
         * 名前に ' や \ が入っても壊れないようにエスケープする。
         */
        private fun escapeForQuery(value: String): String =
            value.replace("\\", "\\\\").replace("'", "\\'")
    }
}
