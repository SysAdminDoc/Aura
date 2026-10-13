package com.freevibe.di

import com.freevibe.service.AndroidContactRingtoneWriter
import com.freevibe.service.ContactRingtoneSoundResolver
import com.freevibe.service.ContactRingtoneSoundRestorer
import com.freevibe.service.ContactRingtoneWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContactRingtoneBackupModule {
    @Binds
    @Singleton
    abstract fun bindContactRingtoneWriter(
        impl: AndroidContactRingtoneWriter,
    ): ContactRingtoneWriter

    @Binds
    @Singleton
    abstract fun bindContactRingtoneSoundResolver(
        impl: ContactRingtoneSoundRestorer,
    ): ContactRingtoneSoundResolver
}
