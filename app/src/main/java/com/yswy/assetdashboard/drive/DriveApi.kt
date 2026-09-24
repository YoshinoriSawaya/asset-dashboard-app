package com.yswy.assetdashboard.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Drive REST APIの薄いラッパー。
 *
 * google-api-services-driveは依存が重く、Android向けの取り回しも悪いので、
 * 必要なエンドポイントだけOkHttpで直接叩く。E01-01の時点では
 * 「トークンが本当に通るか」を確かめる[about]だけ。
 */
class DriveApi(private val accessToken: String) {

    /**
     * 認可されたアカウント情報を取得する。
     * 認証が通っているかの確認用で、業務上の意味はない。
     */
    suspend fun about(): AboutResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/about?fields=user(displayName,emailAddress),storageQuota(limit,usage)")
            .header("Authorization", "Bearer $accessToken")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    return@withContext AboutResult.Failed(
                        "HTTP ${response.code}: ${body.take(300)}",
                    )
                }
                val user = JSONObject(body).optJSONObject("user")
                AboutResult.Success(
                    displayName = user?.optString("displayName").orEmpty(),
                    email = user?.optString("emailAddress").orEmpty(),
                )
            }
        } catch (e: IOException) {
            AboutResult.Failed("通信に失敗: ${e.message}")
        } catch (e: Exception) {
            AboutResult.Failed("応答を解釈できなかった: ${e.message}")
        }
    }

    sealed interface AboutResult {
        data class Success(val displayName: String, val email: String) : AboutResult
        data class Failed(val message: String) : AboutResult
    }

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/drive/v3"

        // OkHttpClientは使い回す前提のオブジェクトなので1つだけ持つ。
        private val client = OkHttpClient()
    }
}
