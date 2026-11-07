package com.email.client

import com.email.client.data.mail.xoauth2.XOAuth2Authenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class XOAuth2AuthenticatorTest {

    @Test
    fun testBuildOAuthTokenFormat() {
        val user = "test@example.com"
        val token = "ya29.sample_oauth_token"

        val tokenBytes = XOAuth2Authenticator.buildOAuthToken(user, token)
        val decoded = Base64.getDecoder().decode(tokenBytes)
        val decodedString = String(decoded, StandardCharsets.UTF_8)

        val expected = "user=$user\u0001auth=Bearer $token\u0001\u0001"
        assertEquals(expected, decodedString)
        assertTrue(decodedString.startsWith("user=test@example.com"))
        assertTrue(decodedString.contains("auth=Bearer ya29.sample_oauth_token"))
    }
}
