package com.nitrogene.obd2.di

import com.nitrogene.obd2.core.obd.transport.TransportManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TransportModule {
    @Provides
    @Singleton
    fun provideTransportManager(): TransportManager = TransportManager()
}
