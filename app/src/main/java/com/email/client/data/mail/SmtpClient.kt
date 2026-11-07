package com.email.client.data.mail

import com.email.client.data.local.entity.AccountEntity
import com.email.client.data.local.entity.AuthType
import com.email.client.data.mail.model.OutgoingEmail
import com.sun.mail.smtp.SMTPTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

@Singleton
class SmtpClient @Inject constructor() {

    suspend fun sendEmail(
        account: AccountEntity,
        credentialOrToken: String,
        email: OutgoingEmail
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val props = Properties().apply {
                put("mail.transport.protocol", "smtps")
                put("mail.smtps.host", account.smtpHost)
                put("mail.smtps.port", account.smtpPort.toString())
                put("mail.smtps.auth", "true")
                put("mail.smtps.ssl.enable", account.smtpUseSsl.toString())
                put("mail.smtps.timeout", "20000")
                put("mail.smtps.connectiontimeout", "20000")

                if (account.authType == AuthType.OAUTH2) {
                    put("mail.smtps.auth.mechanisms", "XOAUTH2")
                    put("mail.smtps.auth.plain.disable", "true")
                }
            }

            val session = Session.getInstance(props, null)
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.email, email.senderName.ifEmpty { account.displayName }))

                val toAddresses = email.recipientsTo.map { InternetAddress(it.trim()) }.toTypedArray()
                setRecipients(Message.RecipientType.TO, toAddresses)

                if (email.recipientsCc.isNotEmpty()) {
                    val ccAddresses = email.recipientsCc.map { InternetAddress(it.trim()) }.toTypedArray()
                    setRecipients(Message.RecipientType.CC, ccAddresses)
                }

                if (email.recipientsBcc.isNotEmpty()) {
                    val bccAddresses = email.recipientsBcc.map { InternetAddress(it.trim()) }.toTypedArray()
                    setRecipients(Message.RecipientType.BCC, bccAddresses)
                }

                subject = email.subject
                sentDate = Date()
            }

            val multipart = MimeMultipart("mixed")

            // Body Part
            val bodyPart = MimeBodyPart()
            if (email.isHtml) {
                bodyPart.setContent(email.bodyText, "text/html; charset=utf-8")
            } else {
                bodyPart.setText(email.bodyText, "utf-8")
            }
            multipart.addBodyPart(bodyPart)

            // Attachment Parts
            for (path in email.attachmentPaths) {
                val file = File(path)
                if (file.exists()) {
                    val attachPart = MimeBodyPart()
                    val source = FileDataSource(file)
                    attachPart.dataHandler = DataHandler(source)
                    attachPart.fileName = file.name
                    multipart.addBodyPart(attachPart)
                }
            }

            message.setContent(multipart)
            message.saveChanges()

            val transport = session.getTransport("smtps") as SMTPTransport
            try {
                transport.connect(account.smtpHost, account.smtpPort, account.email, credentialOrToken)
                transport.sendMessage(message, message.allRecipients)
            } finally {
                if (transport.isConnected) {
                    transport.close()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
