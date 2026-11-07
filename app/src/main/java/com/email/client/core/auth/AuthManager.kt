package com.email.client.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.email.client.core.security.EncryptedSecureStorage
import com.email.client.core.security.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private val authService = AuthorizationService(context)

    fun createAuthorizationIntent(config: OAuthConfig): Intent {
        val authRequest = AuthorizationRequest.Builder(
            config.toServiceConfiguration(),
            config.clientId,
            ResponseTypeValues.CODE,
            config.redirectUri
        )
            .setScopes(config.scopes)
            .build()

        return authService.getAuthorizationRequestIntent(authRequest)
    }

    suspend fun handleAuthorizationResponse(
        accountId: Long,
        response: AuthorizationResponse?,
        exception: AuthorizationException?
    ): Result<TokenResponse> = suspendCancellableCoroutine { continuation ->
        if (exception != null) {
            continuation.resume(Result.failure(exception))
            return@suspendCancellableCoroutine
        }

        if (response == null) {
            continuation.resume(Result.failure(IllegalStateException("Empty authorization response")))
            return@suspendCancellableCoroutine
        }

        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenEx ->
            if (tokenEx != null) {
                continuation.resume(Result.failure(tokenEx))
            } else if (tokenResponse != null) {
                tokenResponse.accessToken?.let { accessToken ->
                    secureStorage.saveSecret(
                        EncryptedSecureStorage.accountAccessTokenKey(accountId),
                        accessToken
                    )
                }
                tokenResponse.refreshToken?.let { refreshToken ->
                    secureStorage.saveSecret(
                        EncryptedSecureStorage.accountRefreshTokenKey(accountId),
                        refreshToken
                    )
                }
                continuation.resume(Result.success(tokenResponse))
            } else {
                continuation.resume(Result.failure(IllegalStateException("No token received")))
            }
        }
    }

    suspend fun getValidAccessToken(
        accountId: Long,
        config: OAuthConfig
    ): Result<String> {
        val cachedToken = secureStorage.getSecret(EncryptedSecureStorage.accountAccessTokenKey(accountId))
        val refreshToken = secureStorage.getSecret(EncryptedSecureStorage.accountRefreshTokenKey(accountId))

        if (refreshToken.isNullOrBlank()) {
            return if (!cachedToken.isNullOrBlank()) {
                Result.success(cachedToken)
            } else {
                Result.failure(IllegalStateException("No refresh token or access token available"))
            }
        }

        return refreshAccessToken(accountId, refreshToken, config)
    }

    private suspend fun refreshAccessToken(
        accountId: Long,
        refreshToken: String,
        config: OAuthConfig
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val refreshRequest = TokenRequest.Builder(
            config.toServiceConfiguration(),
            config.clientId
        )
            .setGrantType("refresh_token")
            .setRefreshToken(refreshToken)
            .build()

        authService.performTokenRequest(refreshRequest) { tokenResponse, ex ->
            if (ex != null) {
                continuation.resume(Result.failure(ex))
            } else if (tokenResponse != null && tokenResponse.accessToken != null) {
                val newAccessToken = tokenResponse.accessToken!!
                secureStorage.saveSecret(
                    EncryptedSecureStorage.accountAccessTokenKey(accountId),
                    newAccessToken
                )
                tokenResponse.refreshToken?.let { newRefresh ->
                    secureStorage.saveSecret(
                        EncryptedSecureStorage.accountRefreshTokenKey(accountId),
                        newRefresh
                    )
                }
                continuation.resume(Result.success(newAccessToken))
            } else {
                continuation.resume(Result.failure(IllegalStateException("Token refresh failed without error")))
            }
        }
    }
}
