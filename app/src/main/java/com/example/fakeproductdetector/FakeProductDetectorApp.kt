package com.example.fakeproductdetector

import android.app.Application
import com.example.fakeproductdetector.ui.tts.TTSManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FakeProductDetectorApp : Application() {

    @Inject lateinit var ttsManager: TTSManager

    override fun onTerminate() {
        super.onTerminate()
        ttsManager.shutdown()
    }
}