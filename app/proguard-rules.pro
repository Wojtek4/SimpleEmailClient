# Keep JavaMail classes
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class java.awt.** { *; }

# Keep AppAuth
-keep class net.openid.appauth.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
