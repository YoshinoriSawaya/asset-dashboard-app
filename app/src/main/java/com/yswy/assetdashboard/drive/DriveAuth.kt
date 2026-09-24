package com.yswy.assetdashboard.drive

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Drive APIを叩くためのアクセストークンを取得する。
 *
 * サーバーを持たないのでrefresh tokenは扱わない。AuthorizationClientが
 * 端末内でGoogleアカウントの認可状態を持っていて、許可済みなら
 * ユーザー操作なしでアクセストークン(1時間程度で失効)を返してくれる。
 * 失効したらもう一度[authorize]を呼べばよい。
 *
 * アプリ側にクライアントIDを埋め込む必要はない。Google Cloud Console側で
 * 「パッケージ名 + 署名証明書のSHA-1」に紐づくAndroid用OAuthクライアントIDを
 * 作っておけば、Play Servicesが署名を見て照合する。
 */
object DriveAuth {

    /**
     * フルの `drive` スコープを使う。
     *
     * `drive.file` だとアプリ自身が作成したファイルしか見えず、
     * 「Driveの画面からinboxにCSVを放り込むだけ」という運用が成立しない。
     * 制限付きスコープなので、OAuth同意画面は自分のアカウントだけを
     * テストユーザーに入れた状態で使う(E01-01参照)。
     */
    const val SCOPE_DRIVE = "https://www.googleapis.com/auth/drive"

    sealed interface Result {
        /** 認可済み。[accessToken]をAuthorizationヘッダーに載せて使う。 */
        data class Authorized(val accessToken: String) : Result

        /**
         * ユーザーの同意が必要。[pendingIntent]を起動して同意画面を出し、
         * 戻ってきたらもう一度[authorize]を呼ぶ。
         */
        data class ConsentRequired(val pendingIntent: PendingIntent) : Result

        /** 失敗。落とさずに理由を持ち回してUIとログに出す。 */
        data class Failed(val message: String, val cause: Throwable? = null) : Result
    }

    suspend fun authorize(context: Context): Result {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_DRIVE)))
            .build()

        return try {
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            val pendingIntent = result.pendingIntent
            when {
                // hasResolution()がtrueのときは未同意。同意画面を出す必要がある。
                result.hasResolution() && pendingIntent != null ->
                    Result.ConsentRequired(pendingIntent)

                result.hasResolution() ->
                    Result.Failed("同意が必要だが同意画面を開けなかった")

                else -> {
                    val token = result.accessToken
                    if (token.isNullOrBlank()) {
                        Result.Failed("アクセストークンが空だった")
                    } else {
                        Result.Authorized(token)
                    }
                }
            }
        } catch (e: Exception) {
            Result.Failed(e.message ?: e::class.java.simpleName, e)
        }
    }
}
