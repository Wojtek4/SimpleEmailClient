package com.email.client

import com.email.client.data.local.entity.FolderType
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderTypeDetectionTest {

    private fun detectFolderType(name: String, fullName: String): FolderType {
        val lower = "${name.lowercase()} ${fullName.lowercase()}"
        return when {
            lower.contains("inbox") -> FolderType.INBOX
            lower.contains("sent") -> FolderType.SENT
            lower.contains("draft") -> FolderType.DRAFTS
            lower.contains("trash") || lower.contains("bin") || lower.contains("deleted") -> FolderType.TRASH
            lower.contains("archive") -> FolderType.ARCHIVE
            lower.contains("spam") || lower.contains("junk") -> FolderType.SPAM
            lower.contains("starred") || lower.contains("flagged") -> FolderType.STARRED
            else -> FolderType.CUSTOM
        }
    }

    @Test
    fun testStandardFolderTypeDetection() {
        assertEquals(FolderType.INBOX, detectFolderType("INBOX", "INBOX"))
        assertEquals(FolderType.SENT, detectFolderType("Sent Items", "[Gmail]/Sent Mail"))
        assertEquals(FolderType.DRAFTS, detectFolderType("Drafts", "[Gmail]/Drafts"))
        assertEquals(FolderType.TRASH, detectFolderType("Bin", "[Gmail]/Bin"))
        assertEquals(FolderType.TRASH, detectFolderType("Deleted Items", "Deleted Items"))
        assertEquals(FolderType.SPAM, detectFolderType("Junk", "Junk Email"))
        assertEquals(FolderType.ARCHIVE, detectFolderType("Archive", "Archive"))
        assertEquals(FolderType.CUSTOM, detectFolderType("Projects", "Work/Projects"))
    }
}
