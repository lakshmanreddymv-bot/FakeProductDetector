package com.example.fakeproductdetector.di

import android.content.Context
import com.example.fakeproductdetector.ui.tts.TTSManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TTSModule {

    @Provides
    @Singleton
    fun provideTTSManager(@ApplicationContext context: Context): TTSManager =
        TTSManager(context)
}