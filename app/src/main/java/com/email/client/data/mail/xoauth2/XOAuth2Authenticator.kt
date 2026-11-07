package com.email.client.data.mail.xoauth2

import java.nio.charset.StandardCharsets
import java.util.Base64

object XOAuth2Authenticator {

    /**
     * Formats XOAUTH2 initial client response for SASL according to RFC 6749 / Google / Microsoft specs:
     * base64("user=" + user + "\u0001auth=Bearer " + token + "\u0001\u0001")
     */
    fun buildOAuthToken(user: String, accessToken: String): ByteArray {
        val raw = "user=$user\u0001auth=Bearer $accessToken\u0001\u0001"
        return Base64.getEncoder().encode(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun buildOAuthTokenString(user: String, accessToken: String): String {
        return String(buildOAuthToken(user, accessToken), StandardCharsets.UTF_8)
    }
}
