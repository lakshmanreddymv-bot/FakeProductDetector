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

// D: Dependency Inversion — qualifiers allow injecting two distinct Retrofit instances without ambiguity

/**
 * Qualifier annotation that identifies the [Retrofit] instance configured for the Gemini API.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRetrofit

/**
 * Qualifier annotation that identifies the [Retrofit] instance configured for the Claude API.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeRetrofit

// S: Single Responsibility — provides all app-wide singleton dependencies via Hilt
// D: Dependency Inversion — consumer classes receive abstractions (interfaces/Retrofit) rather than concrete clients
/**
 * Hilt module that provides singleton-scoped infrastructure dependencies for the entire app.
 *
 * Covers JSON serialisation, HTTP networking (separate Retrofit instances for Gemini and Claude),
 * and the Room database with its DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the app-wide [Gson] instance used for JSON serialisation and deserialisation.
     *
     * @return A default [Gson] instance.
     */
    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    /**
     * Provides the [HttpLoggingInterceptor] used to log HTTP traffic in debug builds.
     *
     * Logs full request/response bodies in DEBUG builds and nothing in RELEASE builds.
     *
     * @return A configured [HttpLoggingInterceptor].
     */
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

    /**
     * Provides the shared [OkHttpClient] with extended timeouts to accommodate large image uploads
     * and slower Gemini Vision inference times.
     *
     * @param loggingInterceptor The logging interceptor applied to all requests.
     * @return A configured [OkHttpClient].
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)   // ← was 10s default
            .readTimeout(60, TimeUnit.SECONDS)       // ← Gemini vision needs up to 30s
            .writeTimeout(60, TimeUnit.SECONDS)      // ← large base64 image upload
            .build()

    /**
     * Provides the [Retrofit] instance for the Gemini API, with the API key injected as a
     * query parameter on every request.
     *
     * @param gson Gson converter factory for request/response serialisation.
     * @param okHttpClient Shared base HTTP client.
     * @return A [Retrofit] instance pointed at the Gemini base URL.
     */
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

    /**
     * Provides the [Retrofit] instance for the Claude API, with the API key injected as an
     * `x-api-key` header on every request.
     *
     * @param gson Gson converter factory for request/response serialisation.
     * @param okHttpClient Shared base HTTP client.
     * @return A [Retrofit] instance pointed at the Anthropic base URL.
     */
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

    /**
     * Provides the Retrofit-generated [GeminiVisionApi] implementation.
     *
     * @param retrofit The Gemini-specific [Retrofit] instance.
     * @return The generated [GeminiVisionApi] implementation.
     */
    @Provides
    @Singleton
    fun provideGeminiVisionApi(@GeminiRetrofit retrofit: Retrofit): GeminiVisionApi =
        retrofit.create(GeminiVisionApi::class.java)

    /**
     * Provides the Retrofit-generated [ClaudeVerificationApi] implementation.
     *
     * @param retrofit The Claude-specific [Retrofit] instance.
     * @return The generated [ClaudeVerificationApi] implementation.
     */
    @Provides
    @Singleton
    fun provideClaudeVerificationApi(@ClaudeRetrofit retrofit: Retrofit): ClaudeVerificationApi =
        retrofit.create(ClaudeVerificationApi::class.java)

    /**
     * Provides the Room [ScanDatabase] instance.
     *
     * @param context Application context used by Room to locate the database file.
     * @return The [ScanDatabase] singleton.
     */
    @Provides
    @Singleton
    fun provideScanDatabase(@ApplicationContext context: Context): ScanDatabase =
        Room.databaseBuilder(context, ScanDatabase::class.java, "scan_history.db")
            .build()

    /**
     * Provides the [ScanDao] from the Room database.
     *
     * @param database The [ScanDatabase] instance.
     * @return The [ScanDao] for scan history CRUD operations.
     */
    @Provides
    @Singleton
    fun provideScanDao(database: ScanDatabase): ScanDao = database.scanDao()
}

// D: Dependency Inversion — binds the concrete ProductRepositoryImpl to the ProductRepository interface
/**
 * Hilt module that binds the [ProductRepository] interface to its concrete implementation.
 *
 * Separating bindings from provisions keeps [AppModule] focused on infrastructure
 * and this module focused on abstraction wiring.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds [ProductRepositoryImpl] as the singleton implementation of [ProductRepository].
     *
     * @param impl The concrete repository implementation provided by Hilt.
     * @return The [ProductRepository] abstraction used throughout the app.
     */
    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository
}