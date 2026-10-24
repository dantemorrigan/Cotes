package io.github.dantemorrigan.cotes.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Доступ к скрытой папке приложения в Google Drive. Ключей в приложении нет:
 * Google узнаёт Cotes по имени пакета и отпечатку подписи, зарегистрированным в Google Cloud.
 */
object GoogleAuth {
    sealed interface Result {
        data class Token(val token: String) : Result

        /** Нужно согласие пользователя: показать [intent]. */
        data class NeedsConsent(val intent: PendingIntent) : Result

        data class Failed(val error: Exception) : Result
    }

    fun available(context: Context): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Системный выбор Google-аккаунта; в ответе — AccountManager.KEY_ACCOUNT_NAME. */
    fun chooseAccountIntent(): Intent = AccountPicker.newChooseAccountIntent(
        AccountPicker.AccountChooserOptions.Builder()
            .setAllowableAccountsTypes(listOf(GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
            .setAlwaysShowAccountPicker(true)
            .build()
    )

    suspend fun token(context: Context, email: String): Result = try {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveApi.SCOPE)))
            .setAccount(Account(email, GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val pending = result.pendingIntent
        val token = result.accessToken
        when {
            result.hasResolution() && pending != null -> Result.NeedsConsent(pending)
            token != null -> Result.Token(token)
            else -> Result.Failed(IllegalStateException("no token"))
        }
    } catch (e: Exception) {
        Result.Failed(e)
    }

    /** Ответ экрана согласия. */
    fun tokenFromConsent(context: Context, data: Intent?): Result = try {
        val token = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken
        if (token != null) Result.Token(token) else Result.Failed(IllegalStateException("no token"))
    } catch (e: Exception) {
        Result.Failed(e)
    }

    /** Забыть просроченный токен, чтобы следующий запрос получил свежий. */
    suspend fun clearToken(context: Context, token: String) {
        withContext(Dispatchers.IO) { runCatching { GoogleAuthUtil.clearToken(context, token) } }
    }

    /** Ошибка настройки проекта в Google Cloud (пакет или отпечаток подписи не зарегистрированы). */
    fun isSetupError(e: Throwable): Boolean =
        e is ApiException && e.statusCode == CommonStatusCodes.DEVELOPER_ERROR

    fun isNetworkError(e: Throwable): Boolean =
        e is ApiException && e.statusCode == CommonStatusCodes.NETWORK_ERROR

    fun isCanceled(e: Throwable): Boolean =
        e is ApiException && e.statusCode == CommonStatusCodes.CANCELED
}
