package com.freevibe.di

import com.freevibe.data.repository.CommunityCallableInvoker
import com.freevibe.data.repository.FirebaseCommunityCallableInvoker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityCallableModule {
    @Binds
    @Singleton
    abstract fun bindCommunityCallableInvoker(
        impl: FirebaseCommunityCallableInvoker,
    ): CommunityCallableInvoker
}
