//NetworkModule
package com.example.netpingmonitor.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.ConnectionPool
import okhttp3.logging.HttpLoggingInterceptor

import com.example.netpingmonitor.util.Constants
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideConnectionPool(): ConnectionPool {
        return ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor? {
        // Отключаем логирование в production для производительности
        return null
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        connectionPool: ConnectionPool,
        loggingInterceptor: HttpLoggingInterceptor?
    ): OkHttpClient {
        return OkHttpClient.Builder().apply {
            connectionPool(connectionPool)
            connectTimeout(Constants.Network.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            readTimeout(Constants.Network.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            writeTimeout(Constants.Network.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            retryOnConnectionFailure(true)

            // Добавляем логирование только в debug режиме
            loggingInterceptor?.let { addInterceptor(it) }

            // Оптимизации для производительности
            cache(null) // Отключаем кэш для реального времени данных
        }.build()
    }
}
