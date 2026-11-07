package com.email.client.core.auth

import android.net.Uri
import net.openid.appauth.AuthorizationServiceConfiguration

enum class OAuthProviderType {
    GOOGLE,
    MICROSOFT,
    CUSTOM
}

data class OAuthConfig(
    val providerType: OAuthProviderType,
    val clientId: String,
    val redirectUri: Uri,
    val authorizationEndpoint: Uri,
    val tokenEndpoint: Uri,
    val scopes: List<String>
) {
    fun toServiceConfiguration(): AuthorizationServiceConfiguration {
        return AuthorizationServiceConfiguration(authorizationEndpoint, tokenEndpoint)
    }

    companion object {
        fun google(clientId: String): OAuthConfig {
            return OAuthConfig(
                providerType = OAuthProviderType.GOOGLE,
                clientId = clientId,
                redirectUri = Uri.parse("com.email.client:/oauth2redirect"),
                authorizationEndpoint = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
                tokenEndpoint = Uri.parse("https://oauth2.googleapis.com/token"),
                scopes = listOf(
                    "https://mail.google.com/",
                    "email",
                    "profile"
                )
            )
        }

        fun microsoft(clientId: String): OAuthConfig {
            return OAuthConfig(
                providerType = OAuthProviderType.MICROSOFT,
                clientId = clientId,
                redirectUri = Uri.parse("msauth://com.email.client/oauth2redirect"),
                authorizationEndpoint = Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
                tokenEndpoint = Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/token"),
                scopes = listOf(
                    "https://outlook.office.com/IMAP.AccessAsUser.All",
                    "https://outlook.office.com/SMTP.Send",
                    "offline_access",
                    "email",
                    "profile"
                )
            )
        }
    }
}
