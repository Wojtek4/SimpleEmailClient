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
import com.email.client.data.local.entity.MessageEntity
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

        val allNewMessages = mutableListOf<MessageEntity>()

        for (account in accounts) {
            val folderResult = emailRepository.syncFolders(account.id)
            if (folderResult.isSuccess) {
                val inbox = folderDao.getFolderByType(account.id, FolderType.INBOX)
                if (inbox != null) {
                    val syncRes = emailRepository.syncMessages(inbox.id, limit = 50)
                    if (syncRes.isSuccess) {
                        allNewMessages.addAll(syncRes.getOrNull()?.newMessages ?: emptyList())
                    }
                }
            }
        }

        if (allNewMessages.isNotEmpty()) {
            showNewEmailsNotification(allNewMessages)
        }

        return Result.success()
    }

    private fun showNewEmailsNotification(newMessages: List<MessageEntity>) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (newMessages.size == 1) {
            val msg = newMessages[0]
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_MESSAGE_ID, msg.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                msg.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct "Mark as Read" action in notification
            val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_AS_READ
                putExtra(NotificationActionReceiver.EXTRA_MESSAGE_ID, msg.id)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            }
            val markReadPendingIntent = PendingIntent.getBroadcast(
                context,
                msg.id.toInt(),
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sender = msg.senderName.ifBlank { msg.senderAddress }
            val subject = msg.subject.ifBlank { "(No Subject)" }
            val preview = msg.previewSnippet.ifBlank { "" }

            val notification = NotificationCompat.Builder(context, EmailApplication.CHANNEL_EMAILS)
                .setSmallIcon(android.R.drawable.sym_action_email)
                .setContentTitle(sender)
                .setContentText(subject)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(sender)
                        .setSummaryText(msg.senderAddress)
                        .bigText("$subject\n\n$preview".trim())
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.checkbox_on_background,
                    "Mark as Read",
                    markReadPendingIntent
                )
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("${newMessages.size} new emails")

            for (msg in newMessages.take(6)) {
                val sender = msg.senderName.ifBlank { msg.senderAddress }
                val subj = msg.subject.ifBlank { "(No Subject)" }
                inboxStyle.addLine("$sender: $subj")
            }
            if (newMessages.size > 6) {
                inboxStyle.setSummaryText("+${newMessages.size - 6} more")
            }

            val notification = NotificationCompat.Builder(context, EmailApplication.CHANNEL_EMAILS)
                .setSmallIcon(android.R.drawable.sym_action_email)
                .setContentTitle("New Mail (${newMessages.size})")
                .setContentText("${newMessages.size} new messages received")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val WORK_NAME = "periodic_email_sync_work"
        private const val NOTIFICATION_ID = 1001
    }
}
