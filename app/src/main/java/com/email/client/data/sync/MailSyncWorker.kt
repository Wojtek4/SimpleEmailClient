package com.email.client.data.sync

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.email.client.EmailApplication
import com.email.client.MainActivity
import com.email.client.R
import com.email.client.data.local.dao.AccountDao
import com.email.client.data.local.dao.FolderDao
import com.email.client.data.local.entity.FolderType
import com.email.client.data.repository.EmailRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MailSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val emailRepository: EmailRepository,
    private val accountDao: AccountDao,
    private val folderDao: FolderDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accounts = accountDao.getAllAccountsSync()
        if (accounts.isEmpty()) {
            return Result.success()
        }

        var totalNewMessages = 0

        for (account in accounts) {
            val folderResult = emailRepository.syncFolders(account.id)
            if (folderResult.isSuccess) {
                val inbox = folderDao.getFolderByType(account.id, FolderType.INBOX)
                if (inbox != null) {
                    val syncRes = emailRepository.syncMessages(inbox.id, limit = 20)
                    if (syncRes.isSuccess) {
                        totalNewMessages += syncRes.getOrDefault(0)
                    }
                }
            }
        }

        if (totalNewMessages > 0) {
            showNewEmailNotification(totalNewMessages)
        }

        return Result.success()
    }

    private fun showNewEmailNotification(count: Int) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, EmailApplication.CHANNEL_EMAILS)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle("New Mail")
            .setContentText("You have $count new messages")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "periodic_email_sync_work"
        private const val NOTIFICATION_ID = 1001
    }
}
