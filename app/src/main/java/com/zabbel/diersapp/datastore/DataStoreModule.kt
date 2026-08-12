package com.zabbel.diersapp.datastore

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // SingletonComponent, da DataStore app-weit sein soll
object DataStoreModule {

    @Singleton
    @Provides
    fun provideAppSettingsDataStore(@ApplicationContext context: Context): AppSettingsDataStore {
        return AppSettingsDataStore(context)
    }
}