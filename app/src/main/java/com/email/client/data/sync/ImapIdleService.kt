package com.email.client.data.sync

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.email.client.EmailApplication
import com.email.client.R
import com.email.client.data.local.dao.AccountDao
import com.email.client.data.local.dao.FolderDao
import com.email.client.data.local.entity.FolderType
import com.email.client.data.repository.EmailRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ImapIdleService : Service() {

    @Inject
    lateinit var emailRepository: EmailRepository

    @Inject
    lateinit var accountDao: AccountDao

    @Inject
    lateinit var folderDao: FolderDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        startIdleLoop()
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, EmailApplication.CHANNEL_SYNC)
            .setContentTitle("Email Sync")
            .setContentText("Checking for new emails in background...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startIdleLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val accounts = accountDao.getAllAccountsSync()
                    for (acc in accounts) {
                        val inbox = folderDao.getFolderByType(acc.id, FolderType.INBOX)
                        if (inbox != null) {
                            emailRepository.syncMessages(inbox.id, limit = 15)
                        }
                    }
                } catch (_: Exception) {
                    // Backoff delay on error
                }
                delay(60_000) // Poll / Idle refresh cycle
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 2002
    }
}
