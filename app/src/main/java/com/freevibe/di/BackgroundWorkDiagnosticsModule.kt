package com.freevibe.di

import com.freevibe.service.AndroidBackgroundWorkDiagnosticsReader
import com.freevibe.service.BackgroundWorkDiagnosticsReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackgroundWorkDiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindBackgroundWorkDiagnosticsReader(
        impl: AndroidBackgroundWorkDiagnosticsReader,
    ): BackgroundWorkDiagnosticsReader
}
