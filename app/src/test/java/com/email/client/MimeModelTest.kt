package com.email.client

import com.email.client.data.mail.model.OutgoingEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeModelTest {

    @Test
    fun testOutgoingEmailCreation() {
        val email = OutgoingEmail(
            senderAddress = "sender@example.com",
            senderName = "Sender Name",
            recipientsTo = listOf("alice@example.com", "bob@example.com"),
            recipientsCc = listOf("charlie@example.com"),
            recipientsBcc = emptyList(),
            subject = "Project Update",
            bodyText = "Hello team, here is the update.",
            isHtml = false,
            attachmentPaths = listOf("/data/user/0/com.email.client/cache/report.pdf")
        )

        assertEquals("sender@example.com", email.senderAddress)
        assertEquals(2, email.recipientsTo.size)
        assertEquals(1, email.recipientsCc.size)
        assertTrue(email.recipientsBcc.isEmpty())
        assertFalse(email.isHtml)
        assertEquals(1, email.attachmentPaths.size)
    }
}
