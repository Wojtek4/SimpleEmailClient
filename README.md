# Simple Mail - Free Native Android Email Client

A free, lightweight, privacy-first native Android email client built with **Kotlin** and **Jetpack Compose**. It directly connects to your email server via standard **IMAP** and **SMTP** protocols with zero third-party proxy servers in between.

---

## Features

- **Direct Server Communication**: Connects directly to IMAP/SMTP mail servers over TLS/SSL (no intermediate cloud backend storing your emails).
- **Multiple Accounts**: Support for multiple email accounts (Gmail, Outlook, Yahoo, iCloud, custom Dovecot/Postfix servers).
- **Modern Authentication**:
  - **OAuth2 PKCE** (via AppAuth-Android) for Google and Microsoft Outlook.
  - **App Passwords / Basic Auth** for generic IMAP/SMTP providers.
- **Offline First with Room**:
  - Incremental sync engine caching folders, headers, and messages.
  - **Full-Text Search (FTS4)** virtual tables for instant offline search across subjects, senders, and message bodies.
- **Sandboxed HTML Viewer**:
  - Secure email body rendering with disabled JavaScript and restricted file access to guard against tracking pixels and XSS attacks.
- **Attachment Support**:
  - View, preview, and download message attachments.
  - Multi-file attachment support when composing emails.
- **Background Sync & Notifications**:
  - Battery-efficient periodic synchronization with **WorkManager**.
  - Foreground IMAP IDLE push service for real-time notification alerts.
- **Material 3 Design**:
  - Modern Jetpack Compose UI with Dynamic Color theming, edge-to-edge support, and responsive drawer navigation.

---

## Project Structure & Architecture

```
app/src/main/
├── AndroidManifest.xml
├── java/com/email/client/
│   ├── EmailApplication.kt                  # Hilt & Notification setup
│   ├── MainActivity.kt                      # Jetpack Compose Navigation & App Root
│   ├── core/
│   │   ├── auth/                            # OAuth2 & AppAuth token handling
│   │   └── security/                        # Keystore & EncryptedSharedPreferences
│   ├── data/
│   │   ├── local/                           # Room Database, Entities, & DAOs
│   │   │   ├── dao/                         # AccountDao, FolderDao, MessageDao, AttachmentDao
│   │   │   └── entity/                      # AccountEntity, MessageEntity, MessageFtsEntity, etc.
│   │   ├── mail/                            # JavaMail / Jakarta Mail Client wrapper
│   │   │   ├── ImapClient.kt                # IMAP folder & message fetching, IDLE
│   │   │   ├── SmtpClient.kt                # SMTP dispatch & MIME multipart composer
│   │   │   └── xoauth2/                     # SASL XOAUTH2 token formatters
│   │   ├── repository/                      # EmailRepository (offline-first sync)
│   │   └── sync/                            # MailSyncWorker & ImapIdleService
│   ├── di/                                  # Hilt Dagger Modules (Database, Security)
│   └── presentation/                        # Jetpack Compose UI (Clean Architecture)
│       ├── compose/                         # Compose & Send Screen + ViewModel
│       ├── detail/                          # Message Detail Viewer + Sandboxed WebView
│       ├── mailbox/                         # Inbox list, drawer, search, & swipe actions
│       ├── onboarding/                      # Account connection & server autodiscovery
│       └── theme/                           # Material 3 dynamic themes
└── res/
    ├── values/                              # Strings, colors, styles
    └── xml/file_paths.xml                   # Secure FileProvider configuration
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 35 (Android 15) with minimum SDK 26 (Android 8.0)

### Opening in Android Studio
1. Open Android Studio.
2. Select **Open** and select this directory.
3. Allow Gradle to synchronize dependencies.
4. Run the `app` configuration on an Android Emulator or connected physical device.

---

## How to Build the .APK File

You have two easy ways to generate the `.apk` file to install on your phone:

### Option 1: Automatic Build Script (Windows)

A build script is included that checks for Java and the Android SDK, downloads Gradle dependencies, compiles the app, and outputs `SimpleEmailClient.apk` right into the root folder.

1. Double-click **`build_apk.bat`** (or run `.\build_apk.ps1` in PowerShell).
2. If Java or Android SDK are not yet on your machine, the script will show you the exact `winget` command or prompt:
   - `winget install Microsoft.OpenJDK.17`
   - `winget install Google.AndroidStudio`
3. Once the build completes, your ready-to-install file will be at:
   - **`SimpleEmailClient.apk`** in this folder.

### Option 2: Cloud Build with GitHub Actions (No local SDK needed)

If you don't want to install gigabytes of Android SDK and Java on your PC:
1. Push this project to a GitHub repository (public or private).
2. Go to the **Actions** tab on your GitHub repository.
3. Select **Build Android APK** and click **Run workflow**.
4. When finished (typically 2-3 minutes), click on the completed run.
5. Under **Artifacts**, download **`SimpleEmailClient-APK`**.
6. Extract the zip to get `SimpleEmailClient-debug.apk` directly on your phone or PC!

---

## Installing on Your Android Phone

1. **Send the APK to your phone**:
   - Send it to yourself via email / Telegram / WhatsApp, or
   - Upload it to Google Drive / OneDrive, or
   - Connect your phone via USB cable and copy the file into your phone's `Download` folder.
2. **Open the APK**:
   - On your phone, open the **Files** / **My Files** / **Downloads** app and tap on `SimpleEmailClient.apk`.
3. **Allow Installation**:
   - If Android prompts *"For your security, your phone is not allowed to install unknown apps from this source"*, tap **Settings** and toggle **Allow from this source**.
4. **Install**:
   - Tap **Install** and then tap **Open** to launch Simple Mail!

---

### Supported Ports & Protocols
| Protocol | Port | Security |
|---|---|---|
| IMAP | 993 | SSL/TLS |
| IMAP | 143 | STARTTLS |
| SMTP | 465 | SSL/TLS |
| SMTP | 587 | STARTTLS |
