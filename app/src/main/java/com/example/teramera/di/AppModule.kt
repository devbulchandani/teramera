package com.example.teramera.di

import android.content.Context
import com.example.teramera.data.local.TerameraDao
import com.example.teramera.data.local.TerameraDatabase
import com.example.teramera.data.local.DatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @AppScope
    @Singleton
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
        provider: DatabaseProvider,
    ): TerameraDatabase = provider.provide(context)

    @Provides
    fun dao(db: TerameraDatabase): TerameraDao = db.dao()
}
