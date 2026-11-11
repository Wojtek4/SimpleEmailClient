package com.email.client

import com.email.client.data.local.entity.MessageEntity
import com.email.client.data.repository.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncResultTest {

    @Test
    fun testSyncResultWithNewMessages() {
        val msg1 = MessageEntity(
            id = 1,
            accountId = 1,
            folderId = 1,
            serverUid = 101,
            senderName = "Alice",
            senderAddress = "alice@example.com",
            subject = "Hello",
            previewSnippet = "Meeting at 10",
            isRead = false
        )

        val result = SyncResult(
            totalSynced = 25,
            newMessages = listOf(msg1)
        )

        assertEquals(25, result.totalSynced)
        assertEquals(1, result.newMessages.size)
        assertEquals("Alice", result.newMessages[0].senderName)
        assertTrue(!result.newMessages[0].isRead)
    }

    @Test
    fun testSyncResultEmpty() {
        val result = SyncResult(totalSynced = 0)
        assertEquals(0, result.totalSynced)
        assertTrue(result.newMessages.isEmpty())
    }
}
