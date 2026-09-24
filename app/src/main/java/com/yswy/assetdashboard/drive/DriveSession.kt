package com.yswy.assetdashboard.drive

import android.app.PendingIntent
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * 認可 → Drive操作 → 失敗の仕分け、をまとめて面倒みる。
 *
 * 呼び出し側(UI)が毎回「トークンを取って、401なら取り直して、
 * 圏外なら諦めて…」と書かずに済むようにする。
 *
 * ## トークン切れは1回だけ黙って取り直す
 * アクセストークンの寿命は1時間程度なので、アプリを開きっぱなしにして
 * いると普通に切れる。**ユーザーに見せる前に1回リトライする。**
 * それでも駄目なら同意画面に誘導する。
 *
 * ## オフラインは「失敗」と別に扱う
 * 圏外は直しようがないので、エラーとして騒ぐのではなく
 * 「同期をスキップした」として扱い、キャッシュを見せる
 * (CLAUDE.mdのオフライン時の方針)。
 */
object DriveSession {

    private const val TAG = "DriveSession"

    sealed interface Outcome<out T> {
        data class Success<T>(val value: T) : Outcome<T>

        /** 同意画面を出す必要がある。[pendingIntent]を起動する。 */
        data class ConsentRequired(val pendingIntent: PendingIntent) : Outcome<Nothing>

        /** 通信できない。同期はスキップしてキャッシュを見せる。 */
        data class Offline(val message: String) : Outcome<Nothing>

        /** それ以外の失敗。理由を出す。 */
        data class Failed(val message: String) : Outcome<Nothing>
    }

    suspend fun <T> withDrive(
        context: Context,
        block: suspend (DriveApi) -> T,
    ): Outcome<T> {
        // 認可より先にネットワークを見る。
        // 圏外だとAuthorizationClientが「同意が必要」を返すので、
        // そのまま進むと開けもしない同意画面に誘導してしまう(実機で確認)。
        if (!isOnline(context)) {
            return Outcome.Offline("ネットワークに繋がっていない")
        }

        val token = when (val auth = DriveAuth.authorize(context)) {
            is DriveAuth.Result.Authorized -> auth.accessToken
            is DriveAuth.Result.ConsentRequired -> return Outcome.ConsentRequired(auth.pendingIntent)
            is DriveAuth.Result.Failed -> return Outcome.Failed(auth.message)
        }

        return runBlock(context, token, block, allowRetry = true)
    }

    /**
     * 通信できる見込みがあるか。
     *
     * 「繋がっている」まではここでは分からない(Wi-Fiに繋がっていても
     * その先が死んでいることはある)。そちらは実際のリクエストが
     * [DriveException.isOffline] で拾う。二段構えにしてある。
     */
    private fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun <T> runBlock(
        context: Context,
        token: String,
        block: suspend (DriveApi) -> T,
        allowRetry: Boolean,
    ): Outcome<T> = try {
        Outcome.Success(block(DriveApi(token)))
    } catch (e: DriveException) {
        when {
            e.isOffline -> Outcome.Offline(e.message ?: "通信できない")

            e.isUnauthorized && allowRetry -> {
                // 1時間でトークンが切れるので、開きっぱなしだと普通に起きる。
                // ユーザーに見せる前に黙って取り直す。
                Log.i(TAG, "トークンが切れていたので取り直す")
                when (val retry = DriveAuth.authorize(context)) {
                    is DriveAuth.Result.Authorized ->
                        runBlock(context, retry.accessToken, block, allowRetry = false)
                    is DriveAuth.Result.ConsentRequired ->
                        Outcome.ConsentRequired(retry.pendingIntent)
                    is DriveAuth.Result.Failed ->
                        Outcome.Failed("再認可できない: ${retry.message}")
                }
            }

            e.isUnauthorized -> Outcome.Failed("再認可しても認証が通らない")

            else -> Outcome.Failed(e.message ?: "Driveの操作に失敗")
        }
    } catch (e: Exception) {
        Log.w(TAG, "想定外の失敗", e)
        Outcome.Failed(e.message ?: e::class.java.simpleName)
    }
}
