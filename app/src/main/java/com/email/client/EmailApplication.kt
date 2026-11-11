package com.email.client

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.email.client.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.activation.CommandMap
import javax.activation.MailcapCommandMap
import javax.inject.Inject

@HiltAndroidApp
class EmailApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupMailcap()
        createNotificationChannels()
        SyncScheduler.schedulePeriodicSync(this)
    }

    private fun setupMailcap() {
        try {
            val mc = CommandMap.getDefaultCommandMap() as? MailcapCommandMap ?: MailcapCommandMap()
            mc.addMailcap("text/html;; x-java-content-handler=com.sun.mail.handlers.text_html")
            mc.addMailcap("text/xml;; x-java-content-handler=com.sun.mail.handlers.text_xml")
            mc.addMailcap("text/plain;; x-java-content-handler=com.sun.mail.handlers.text_plain")
            mc.addMailcap("multipart/*;; x-java-content-handler=com.sun.mail.handlers.multipart_mixed")
            mc.addMailcap("message/rfc822;; x-java-content-handler=com.sun.mail.handlers.message_rfc822")
            CommandMap.setDefaultCommandMap(mc)
        } catch (_: Exception) {
            // Best effort
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val emailChannel = NotificationChannel(
                CHANNEL_EMAILS,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_desc)
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }

            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.sync_channel_desc)
            }

            notificationManager.createNotificationChannel(emailChannel)
            notificationManager.createNotificationChannel(syncChannel)
        }
    }

    companion object {
        const val CHANNEL_EMAILS = "email_messages"
        const val CHANNEL_SYNC = "email_sync"
    }
}
