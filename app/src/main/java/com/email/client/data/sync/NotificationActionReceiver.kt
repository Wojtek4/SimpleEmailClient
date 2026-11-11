package com.email.client.data.sync

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.email.client.data.repository.EmailRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var emailRepository: EmailRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MARK_AS_READ) {
            val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            if (notificationId != -1) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }

            if (messageId > 0) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        emailRepository.setReadStatus(messageId, true)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_MARK_AS_READ = "com.email.client.ACTION_MARK_AS_READ"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
