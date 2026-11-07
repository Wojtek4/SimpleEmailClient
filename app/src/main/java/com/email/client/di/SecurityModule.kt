package com.email.client.di

import com.email.client.core.security.EncryptedSecureStorage
import com.email.client.core.security.SecureStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecureStorage(
        encryptedSecureStorage: EncryptedSecureStorage
    ): SecureStorage
}
