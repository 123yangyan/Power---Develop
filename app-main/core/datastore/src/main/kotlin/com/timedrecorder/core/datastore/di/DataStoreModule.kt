package com.timedrecorder.core.datastore.di

import com.timedrecorder.core.datastore.PreferencesDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
//    @Provides
//    @Singleton
//    fun providePreferencesDataSource(source: PreferencesDataSource): PreferencesDataSource = source
}
