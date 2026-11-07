package com.email.client.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface SecureStorage {
    fun saveSecret(key: String, value: String)
    fun getSecret(key: String): String?
    fun deleteSecret(key: String)
    fun clearAll()
}

@Singleton
class EncryptedSecureStorage @Inject constructor(
    @ApplicationContext context: Context
) : SecureStorage {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveSecret(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    override fun getSecret(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    override fun deleteSecret(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    override fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "email_secure_vault"
        fun accountPasswordKey(accountId: Long): String = "acc_pwd_$accountId"
        fun accountRefreshTokenKey(accountId: Long): String = "acc_refresh_$accountId"
        fun accountAccessTokenKey(accountId: Long): String = "acc_access_$accountId"
    }
}
