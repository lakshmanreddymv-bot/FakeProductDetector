package com.example.fakeproductdetector.di

import android.content.Context
import androidx.room.Room
import com.example.fakeproductdetector.BuildConfig
import com.example.fakeproductdetector.data.api.ClaudeVerificationApi
import com.example.fakeproductdetector.data.api.GeminiVisionApi
import com.example.fakeproductdetector.data.local.ScanDao
import com.example.fakeproductdetector.data.local.ScanDatabase
import com.example.fakeproductdetector.data.repository.ProductRepositoryImpl
import com.example.fakeproductdetector.domain.repository.ProductRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)   // ← was 10s default
            .readTimeout(60, TimeUnit.SECONDS)       // ← Gemini vision needs up to 30s
            .writeTimeout(60, TimeUnit.SECONDS)      // ← large base64 image upload
            .build()

    @Provides
    @Singleton
    @GeminiRetrofit
    fun provideGeminiRetrofit(gson: Gson, okHttpClient: OkHttpClient): Retrofit {
        val geminiClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("key", BuildConfig.GEMINI_API_KEY)
                    .build()
                chain.proceed(original.newBuilder().url(url).build())
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(geminiClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @ClaudeRetrofit
    fun provideClaudeRetrofit(gson: Gson, okHttpClient: OkHttpClient): Retrofit {
        val claudeClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("x-api-key", BuildConfig.ANTHROPIC_API_KEY)
                    .build()
                chain.proceed(request)
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(claudeClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideGeminiVisionApi(@GeminiRetrofit retrofit: Retrofit): GeminiVisionApi =
        retrofit.create(GeminiVisionApi::class.java)

    @Provides
    @Singleton
    fun provideClaudeVerificationApi(@ClaudeRetrofit retrofit: Retrofit): ClaudeVerificationApi =
        retrofit.create(ClaudeVerificationApi::class.java)

    @Provides
    @Singleton
    fun provideScanDatabase(@ApplicationContext context: Context): ScanDatabase =
        Room.databaseBuilder(context, ScanDatabase::class.java, "scan_history.db")
            .build()

    @Provides
    @Singleton
    fun provideScanDao(database: ScanDatabase): ScanDao = database.scanDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository
}